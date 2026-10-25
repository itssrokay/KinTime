package app.kintime.family

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kintime.family.data.*
import app.kintime.family.sync.FamilyRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Run with `npm run test:sync` to start local Auth/Firestore emulators. No cloud project needed. */
@RunWith(AndroidJUnit4::class)
class FamilySyncTest {
    @Test fun accountsJoinPublishPauseAndLeave() = runBlocking {
        assumeTrue("Requires npm run test:sync", InstrumentationRegistry.getArguments().getString("firebaseEmulators") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (android.os.Build.VERSION.SDK_INT >= 37) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        val testId = UUID.randomUUID().toString()
        fun app(label: String): FirebaseApp {
            val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApiKey("fake-api-key-for-local-emulator-only").setApplicationId("1:123:android:test")
                .setProjectId("demo-kintime").build(), "$testId-$label")
            FirebaseAuth.getInstance(app).useEmulator("10.0.2.2", 9099)
            FirebaseFirestore.getInstance(app).useEmulator("10.0.2.2", 8080)
            return app
        }
        val aliceApp = app("alice")
        val bobApp = app("bob")
        val database = Room.inMemoryDatabaseBuilder(context, KinDatabase::class.java).build()
        val alicePrefs = Preferences(context, "test-alice-$testId").apply { name("Alice") }
        val bobPrefs = Preferences(context, "test-bob-$testId").apply { name("Bob") }
        val alice = FamilyRepository(context, alicePrefs, database.stats(), aliceApp)
        val bob = FamilyRepository(context, bobPrefs, database.stats(), bobApp)
        try {
            alice.authenticate("alice-$testId@example.com", "test-password-123", true)
            assertNull(alice.state.value.error)
            bob.authenticate("bob-$testId@example.com", "test-password-123", true)
            assertNull(bob.state.value.error)
            alice.createFamily("Test family")
            assertNull(alice.state.value.error)
            assertTrue(alicePrefs.state.value.familyId.isNotBlank())
            alice.createInvite()
            assertNull(alice.state.value.error)
            bob.joinFamily(alice.state.value.invite)
            assertNull(bob.state.value.error)
            assertEquals(alicePrefs.state.value.familyId, bobPrefs.state.value.familyId)
            val today = LocalDate.now().toString()
            database.stats().putDay(UsageDay(today, "Asia/Kolkata", 120_000, System.currentTimeMillis(), true))
            database.stats().addVideo(VideoSample(UUID.randomUUID().toString(), today, "com.google.android.youtube", 2_000, 1, System.currentTimeMillis()))
            alice.changeSharing(true)
            assertNull(alice.state.value.error)
            val uid = FirebaseAuth.getInstance(aliceApp).currentUser!!.uid
            val family = FirebaseFirestore.getInstance(bobApp).collection("families").document(bobPrefs.state.value.familyId)
            val summary = family.collection("summaries").document("${uid}_${alicePrefs.deviceId}_$today")
            assertEquals(120_000L, summary.get(Source.SERVER).await().getLong("screenMillis"))
            assertEquals(1L, summary.get(Source.SERVER).await().getLong("views"))
            alice.sync() // Retrying replaces the same per-device/day record.
            assertEquals(1, family.collection("summaries").get(Source.SERVER).await().size())
            alice.changeSharing(false)
            assertNull(alice.state.value.error)
            assertFalse(summary.get(Source.SERVER).await().exists())
            bob.leaveFamily()
            assertNull(bob.state.value.error)
            assertEquals("", bobPrefs.state.value.familyId)
        } finally {
            alice.close(); bob.close(); database.close()
            FirebaseFirestore.getInstance(aliceApp).terminate().await()
            FirebaseFirestore.getInstance(bobApp).terminate().await()
            aliceApp.delete(); bobApp.delete()
        }
    }
}
