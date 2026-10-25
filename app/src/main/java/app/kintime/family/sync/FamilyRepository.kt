package app.kintime.family.sync

import android.content.Context
import app.kintime.family.BuildConfig
import app.kintime.family.data.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Date
import java.util.UUID

data class FamilyMember(val uid: String, val name: String, val screenMillis: Long = 0,
                        val views: Long = 0, val shortMillis: Long = 0, val updatedAt: Long = 0,
                        val devices: Int = 0, val apps: Map<String, Long> = emptyMap(),
                        val steps: Long? = null, val stepsReadAt: Long = 0)
data class FamilyState(val configured: Boolean = false, val uid: String? = null, val email: String = "",
                       val familyName: String = "", val members: List<FamilyMember> = emptyList(),
                       val invite: String = "", val busy: Boolean = false, val error: String? = null)

class FamilyRepository(context: Context, private val preferences: Preferences, private val dao: StatsDao,
                       firebaseAppOverride: FirebaseApp? = null, private val stepsPermission: suspend () -> Boolean = { false }) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth: FirebaseAuth?
    private val store: FirebaseFirestore?
    private val mutable = MutableStateFlow(FamilyState())
    val state = mutable.asStateFlow()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val syncMutex = Mutex()
    private var memberDocs = emptyList<DocumentSnapshot>()
    private var summaries = emptyList<DocumentSnapshot>()
    private var stepSummaries = emptyList<DocumentSnapshot>()
    private var listenedDay = ""
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        mutable.update { it.copy(uid = firebaseAuth.currentUser?.uid, email = firebaseAuth.currentUser?.email.orEmpty()) }
        attach()
    }

    init {
        val configured = firebaseAppOverride != null || (BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank())
        val app = firebaseAppOverride ?: if (configured) FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_API_KEY).setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build()) else null
        auth = app?.let { FirebaseAuth.getInstance(it) }
        store = app?.let { FirebaseFirestore.getInstance(it) }
        // Family data is not persisted to disk: a departed member should not retain a local cloud cache.
        store?.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        mutable.value = FamilyState(configured = configured)
        auth?.addAuthStateListener(authListener)
        scope.launch { preferences.state.map { it.familyId }.distinctUntilChanged().collect { attach() } }
    }

    private fun attach() {
        synchronized(listeners) {
            listeners.forEach { it.remove() }; listeners.clear()
            memberDocs = emptyList(); summaries = emptyList(); stepSummaries = emptyList()
            mutable.update { it.copy(members = emptyList(), familyName = "", invite = "") }
            val id = preferences.state.value.familyId
            if (id.isBlank() || auth?.currentUser == null || store == null) return
            listenedDay = LocalDate.now().toString()
            val family = store.collection("families").document(id)
            listeners += family.addSnapshotListener { doc, error ->
                if (error != null) mutable.update { it.copy(error = "Family access unavailable. Check membership, connection, and deployed rules.") }
                else mutable.update { it.copy(familyName = doc?.getString("name").orEmpty()) }
            }
            listeners += family.collection("members").addSnapshotListener { docs, error ->
                if (error == null) { memberDocs = docs?.documents.orEmpty(); publishMembers() }
            }
            listeners += family.collection("summaries").whereEqualTo("day", listenedDay).addSnapshotListener { docs, error ->
                if (error == null) { summaries = docs?.documents.orEmpty(); publishMembers() }
                else mutable.update { it.copy(error = "Could not read family statistics. Check deployed Firestore rules.") }
            }
            listeners += family.collection("steps").whereEqualTo("day", listenedDay).addSnapshotListener { docs, error ->
                if (error == null) { stepSummaries = docs?.documents.orEmpty(); publishMembers() }
                else mutable.update { it.copy(error = "Step sharing needs the updated Firestore rules. See the Firebase setup guide.") }
            }
        }
    }
    private fun publishMembers() {
        mutable.update { state -> state.copy(members = memberDocs.map { member ->
            val stats = summaries.filter { it.getString("userId") == member.id }
            val step = stepSummaries.firstOrNull { it.getString("userId") == member.id }
            FamilyMember(member.id, member.getString("name") ?: "Family member",
                stats.sumOf { it.getLong("screenMillis") ?: 0 }, stats.sumOf { it.getLong("views") ?: 0 },
                stats.sumOf { it.getLong("shortMillis") ?: 0 }, stats.maxOfOrNull { it.getTimestamp("updatedAt")?.toDate()?.time ?: 0 } ?: 0, stats.size,
                buildMap { stats.forEach { doc -> (doc.get("apps") as? Map<*, *>)?.forEach { (key, value) -> if (key is String && value is Number) put(key, (get(key) ?: 0L) + value.toLong()) } } },
                step?.getLong("count"), step?.getTimestamp("readAt")?.toDate()?.time ?: 0)
        }.sortedBy { it.name.lowercase() }) }
    }
    suspend fun authenticate(email: String, password: String, register: Boolean) = action {
        require(email.isNotBlank() && password.length >= 6) { "Enter an email and a password of at least 6 characters." }
        if (register) auth!!.createUserWithEmailAndPassword(email.trim(), password).await()
        else auth!!.signInWithEmailAndPassword(email.trim(), password).await()
    }
    suspend fun resetPassword(email: String) = action {
        require(email.isNotBlank()) { "Enter your email first." }
        auth!!.sendPasswordResetEmail(email.trim()).await()
    }
    suspend fun createFamily(name: String) = action {
        require(preferences.state.value.familyId.isBlank()) { "Leave your current family first." }
        require(name.trim().length in 2..60) { "Use a family name between 2 and 60 characters." }
        val uid = requireNotNull(auth?.currentUser?.uid) { "Sign in first." }
        val family = store!!.collection("families").document()
        store.batch().apply {
            set(family, mapOf("name" to name.trim(), "ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            set(family.collection("members").document(uid), mapOf("name" to preferences.state.value.displayName, "joinedAt" to FieldValue.serverTimestamp(), "inviteCode" to "owner"))
        }.commit().await()
        preferences.family(family.id)
    }
    suspend fun createInvite() = action {
        val family = requireNotNull(store).collection("families").document(preferences.state.value.familyId)
        val code = UUID.randomUUID().toString().replace("-", "")
        store.collection("invites").document(code).set(mapOf("familyId" to family.id, "createdBy" to auth!!.currentUser!!.uid,
            "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1_000)))).await()
        mutable.update { it.copy(invite = code) }
    }
    suspend fun joinFamily(code: String) = action {
        require(preferences.state.value.familyId.isBlank()) { "Leave your current family first." }
        val clean = code.trim().lowercase()
        require(clean.matches(Regex("[a-f0-9]{32}"))) { "Paste the complete invitation code." }
        val invite = store!!.collection("invites").document(clean).get(Source.SERVER).await()
        require(invite.exists() && (invite.getTimestamp("expiresAt")?.toDate()?.time ?: 0) > System.currentTimeMillis()) { "This invitation has expired or does not exist." }
        val familyId = requireNotNull(invite.getString("familyId"))
        val uid = requireNotNull(auth?.currentUser?.uid) { "Sign in first." }
        val membership = store.collection("families").document(familyId).collection("members").document(uid)
        if (membership.get(Source.SERVER).await().exists()) membership.update("name", preferences.state.value.displayName).await()
        else membership.set(mapOf("name" to preferences.state.value.displayName,
            "joinedAt" to FieldValue.serverTimestamp(), "inviteCode" to clean)).await()
        preferences.family(familyId)
    }
    suspend fun changeSharing(enabled: Boolean) = action {
        require(preferences.state.value.familyId.isNotBlank()) { "Join a family first." }
        preferences.sharing(enabled)
        if (enabled) sync() else syncMutex.withLock { deleteSharedStats() }
    }
    suspend fun changeAppSharing(enabled: Boolean) = action {
        preferences.shareApps(enabled)
        // Replace previously published records too, so disabling this removes app breakdowns.
        if (!enabled) {
            val id = preferences.state.value.familyId
            val uid = auth?.currentUser?.uid
            if (id.isNotBlank() && uid != null) {
                syncMutex.withLock {
                    val docs = store!!.collection("families").document(id).collection("summaries").whereEqualTo("userId", uid).get(Source.SERVER).await()
                    docs.documents.chunked(400).forEach { chunk -> store.batch().apply { chunk.forEach { update(it.reference, "apps", emptyMap<String, Long>()) } }.commit().await() }
                }
            }
        }
        if (preferences.state.value.sharing) sync()
    }
    suspend fun changeStepSharing(enabled: Boolean) = action {
        require(preferences.state.value.familyId.isNotBlank()) { "Join a family first." }
        if (enabled) require(stepsPermission()) { "Connect Health Connect and allow steps access first." }
        preferences.shareSteps(enabled)
        if (!enabled) preferences.stepCleanupPending(true)
        if (enabled && preferences.state.value.sharing) sync()
        else if (!enabled) syncMutex.withLock { deleteSharedSteps(onlyThisDevice = true) }
    }
    suspend fun disconnectSteps(disconnect: suspend () -> Unit) {
        preferences.shareSteps(false)
        preferences.stepCleanupPending(true)
        // Local disconnection must still work in builds without Firebase.
        if (store != null && auth?.currentUser != null && preferences.state.value.familyId.isNotBlank()) {
            action { syncMutex.withLock { deleteSharedSteps(onlyThisDevice = true) } }
        }
        disconnect()
    }
    private suspend fun deleteSharedSteps(onlyThisDevice: Boolean = false) {
        val id = preferences.state.value.familyId
        val uid = auth?.currentUser?.uid ?: return
        if (id.isBlank() || store == null) return
        val docs = store.collection("families").document(id).collection("steps").whereEqualTo("userId", uid).get(Source.SERVER).await()
        if (onlyThisDevice) removeDeviceStepSnapshots(docs.documents)
        else docs.documents.chunked(400).forEach { chunk -> store.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await() }
        preferences.stepCleanupPending(false)
    }
    private suspend fun removeDeviceStepSnapshots(docs: List<DocumentSnapshot>) {
        for (doc in docs.filter { it.getString("deviceId") == preferences.deviceId }) {
            // The canonical snapshot may have moved to another device since the query.
            store!!.runTransaction { transaction ->
                val current = transaction.get(doc.reference)
                if (current.getString("deviceId") == preferences.deviceId) transaction.delete(doc.reference)
            }.await()
        }
    }
    private suspend fun deleteSharedStats() {
        val id = preferences.state.value.familyId
        val uid = auth?.currentUser?.uid ?: return
        if (id.isBlank()) return
        val docs = store!!.collection("families").document(id).collection("summaries").whereEqualTo("userId", uid).get(Source.SERVER).await()
        docs.documents.chunked(400).forEach { chunk -> store.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await() }
        deleteSharedSteps()
    }
    suspend fun leaveFamily() = action {
        preferences.sharing(false)
        syncMutex.withLock { deleteSharedStats() }
        val id = preferences.state.value.familyId
        store!!.collection("families").document(id).collection("members").document(auth!!.currentUser!!.uid).delete().await()
        preferences.family("")
    }
    suspend fun signOut() = action {
        preferences.sharing(false)
        syncMutex.withLock { deleteSharedStats() }
        preferences.family("")
        auth?.signOut()
    }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun close() {
        auth?.removeAuthStateListener(authListener)
        synchronized(listeners) { listeners.forEach { it.remove() }; listeners.clear() }
        scope.cancel()
    }
    suspend fun refresh() { if (listenedDay != LocalDate.now().toString()) attach(); sync() }
    suspend fun sync() = syncMutex.withLock {
        val prefs = preferences.state.value
        val uid = auth?.currentUser?.uid ?: return@withLock
        if (prefs.familyId.isBlank() || store == null) return@withLock
        // Retry an offline opt-out even while all sharing is paused, including after an app restart.
        if (prefs.stepCleanupPending) deleteSharedSteps(onlyThisDevice = true)
        if (!prefs.sharing) return@withLock
        val family = store.collection("families").document(prefs.familyId)
        val days = dao.days(LocalDate.now().minusDays(6).toString())
        val batch = store.batch()
        batch.update(family.collection("members").document(uid), "name", prefs.displayName)
        for (day in days) {
            val videos = dao.videos(day.day)
            val data = mapOf("userId" to uid, "deviceId" to preferences.deviceId, "day" to day.day, "zoneId" to day.zoneId,
                "screenMillis" to day.screenMillis, "views" to videos.sumOf { it.views }, "shortMillis" to videos.sumOf { it.millis },
                "collectedAt" to Timestamp(Date(day.collectedAt)), "updatedAt" to FieldValue.serverTimestamp(),
                "apps" to if (prefs.shareApps) dao.apps(day.day).take(40).associate { it.packageName to it.millis } else emptyMap<String, Long>(),
                "detectorVersion" to 1)
            batch.set(family.collection("summaries").document("${uid}_${preferences.deviceId}_${day.day}"), data)
        }
        batch.commit().await()
        if (prefs.shareSteps) {
            if (!stepsPermission()) {
                preferences.shareSteps(false)
                preferences.stepCleanupPending(true)
                deleteSharedSteps(onlyThisDevice = true)
            } else {
                val steps = dao.stepDays(LocalDate.now().minusDays(6).toString())
                for (day in steps) {
                    val ref = family.collection("steps").document("${uid}_${day.day}")
                    // One document per person/day, not per device. New readings may legitimately decrease.
                    store.runTransaction { transaction ->
                        val previous = transaction.get(ref)
                        val previousRead = previous.getTimestamp("readAt")?.toDate()?.time ?: 0
                        if (day.readAt >= previousRead) transaction.set(ref, mapOf(
                            "userId" to uid, "deviceId" to preferences.deviceId, "day" to day.day, "zoneId" to day.zoneId,
                            "count" to day.count, "readAt" to Timestamp(Date(day.readAt)), "updatedAt" to FieldValue.serverTimestamp()))
                    }.await()
                }
                // Deletions/corrections in Health Connect must remove previously shared readings too.
                val validDays = steps.map { it.day }.toSet()
                val old = family.collection("steps").whereEqualTo("userId", uid).get(Source.SERVER).await()
                removeDeviceStepSnapshots(old.documents.filter { it.getString("day") !in validDays })
            }
        }
        preferences.synced(System.currentTimeMillis())
    }
    private suspend fun action(block: suspend () -> Unit) {
        mutable.update { it.copy(busy = true, error = null) }
        try { withTimeout(30_000) { require(store != null) { "Firebase is not configured in this build." }; block() } }
        catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e; mutable.update { it.copy(error = "Request timed out. Check your connection and retry.") } }
        catch (e: Exception) { mutable.update { it.copy(error = e.localizedMessage ?: "The request failed. Please retry.") } }
        finally { mutable.update { it.copy(busy = false) } }
    }
}
