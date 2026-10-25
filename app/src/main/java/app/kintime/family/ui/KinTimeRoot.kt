package app.kintime.family.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kintime.family.DashboardModel
import app.kintime.family.data.*
import app.kintime.family.sync.*
import java.time.*
import java.time.format.DateTimeFormatter

private val Paper = Color(0xFFF7F5F0)
private val Ink = Color(0xFF243B36)
private val Muted = Color(0xFF6C7A73)
private val Coral = Color(0xFFBD543B)
private val Peach = Color(0xFFF5E1D7)
private val Sage = Color(0xFFE3EAE1)
private val Gold = Color(0xFFD3AD65)

fun duration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return when { minutes == 0L -> "${ms.coerceAtLeast(0) / 1_000}s"; minutes < 60 -> "${minutes}m"; else -> "${minutes / 60}h ${minutes % 60}m" }
}
private fun ago(at: Long): String = when {
    at == 0L -> "Not synced yet"
    System.currentTimeMillis() - at < 60_000 -> "Updated just now"
    else -> "Updated ${duration(System.currentTimeMillis() - at)} ago"
}

@Composable
fun KinTimeRoot(model: DashboardModel, openSettings: (String) -> Unit) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val family by model.family.collectAsStateWithLifecycle()
    val hasAccess by model.hasAccess.collectAsStateWithLifecycle()
    val day by model.day.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val videos by model.videos.collectAsStateWithLifecycle()
    val week by model.week.collectAsStateWithLifecycle()
    val selectedDay by model.selectedDay.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    val localError by model.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var disclosure by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Coral, onPrimary = Color.White, secondary = Ink,
        background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink, outline = Color(0xFFD8DDD4))) {
        Scaffold(containerColor = Paper, bottomBar = {
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                listOf("Today" to Icons.Rounded.DonutLarge, "Log" to Icons.Rounded.Timer, "Family" to Icons.Rounded.PeopleOutline, "Tracking" to Icons.Rounded.Tune).forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, null) }, label = { Text(item.first) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Sage, selectedTextColor = Ink, unselectedIconColor = Muted))
                }
            }
        }) { padding ->
            val scrollState = remember(tab) { ScrollState(0) }
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Ink), contentAlignment = Alignment.Center) { Text("k", color = Paper, fontWeight = FontWeight.Bold, fontSize = 25.sp) }
                        Text("kintime", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                    }
                    Surface(color = Sage, shape = CircleShape) { Text("FAMILY, IN BALANCE", Modifier.padding(horizontal = 11.dp, vertical = 8.dp), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }
                }
                if (localError != null || family.error != null) {
                    Notice(localError ?: family.error.orEmpty(), Icons.Rounded.Info) {
                        model.error.value = null; model.container.family.dismissError()
                    }
                }
                when (tab) {
                    0 -> TodayScreen(prefs, hasAccess, day, apps, videos, week, selectedDay, refreshing,
                        { model.selectedDay.value = it }, { model.refresh() }, { tab = 3 }, { model.label(it) })
                    1 -> TimeLogScreen(model)
                    2 -> FamilyScreen(model, prefs, family)
                    else -> TrackingScreen(model, prefs, hasAccess, family.configured, openSettings) { disclosure = true }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, icon = { Icon(Icons.Rounded.Visibility, null) },
            title = { Text("Enable short-video estimates?") },
            text = { Text("KinTime will inspect accessible interface elements in Instagram and YouTube to recognise Reels and Shorts and estimate video appearances and time.\n\nInterface information is processed in memory on this phone. Only counts and durations are saved. Those summaries are shared with your family only if you enable sharing.\n\nCounts can be incomplete or inaccurate after app updates. No screenshot capture or automated taps are used. You can disable this at any time in Tracking or Android Settings.") },
            confirmButton = { TextButton(onClick = { model.container.preferences.videoConsent(true); disclosure = false; openSettings("accessibility") }) { Text("Agree & open Settings") } },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } })
    }
}

@Composable
private fun Title(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), color = Coral, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(title, fontFamily = FontFamily.Serif, fontSize = 38.sp, lineHeight = 41.sp, letterSpacing = (-1).sp)
        Text(subtitle, fontSize = 14.sp, color = Muted, lineHeight = 21.sp)
    }
}

@Composable
private fun TodayScreen(prefs: PreferencesState, hasAccess: Boolean, day: UsageDay?, apps: List<AppUsage>, videos: List<VideoTotal>,
                        week: List<UsageDay>, selected: String, refreshing: Boolean, select: (String) -> Unit, refresh: () -> Unit,
                        tracking: () -> Unit, label: (String) -> String) {
    val today = LocalDate.now()
    Title(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), "A little less scrolling.\nA little more living.", "Your day, with a little perspective.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (6L downTo 0L).forEach { offset ->
            val date = today.minusDays(offset)
            val chosen = selected == date.toString()
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (chosen) Ink else Color.Transparent)
                .clickable { select(date.toString()) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEEE")), color = if (chosen) Paper.copy(alpha = .65f) else Muted, fontSize = 11.sp)
                Text(date.dayOfMonth.toString(), color = if (chosen) Paper else Ink, fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(4.dp).background(if (week.any { it.day == date.toString() }) Gold else Color.Transparent, CircleShape))
            }
        }
    }
    if (!hasAccess) Notice("Your phone, your permission. Enable Usage Access to see real screen-time statistics.", Icons.Rounded.LockOpen, tracking)
    Surface(shape = RoundedCornerShape(28.dp), color = Ink) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SCREEN TIME", color = Sage, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.WbSunny, null, tint = Gold, modifier = Modifier.size(22.dp))
            }
            Text(if (day != null) duration(day.screenMillis) else "—", fontSize = 56.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Light, color = Paper)
            LinearProgressIndicator(progress = { ((day?.screenMillis ?: 0).toFloat() / (prefs.goalMinutes * 60_000f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = Gold, trackColor = Color.White.copy(alpha = .13f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Personal goal · ${duration(prefs.goalMinutes * 60_000L)}", color = Sage, fontSize = 12.sp)
                Text("${if (day == null) "No data yet" else "Screen on & unlocked"}", color = Sage.copy(alpha = .65f), fontSize = 10.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric("SHORT VIDEOS", if (videos.isEmpty()) "—" else videos.sumOf { it.views }.toString(), "Estimated appearances", Icons.Rounded.PlayCircleOutline, Peach, Modifier.weight(1f))
        Metric("SHORT-VIDEO TIME", if (videos.isEmpty()) "—" else duration(videos.sumOf { it.millis }), "Recognised viewers only", Icons.Rounded.HourglassEmpty, Sage, Modifier.weight(1f))
    }
    if (videos.isEmpty()) Text("Short-video estimates start after you enable optional tracking. An empty count does not mean zero videos watched.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Where your time went", fontFamily = FontFamily.Serif, fontSize = 24.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = refresh, enabled = !refreshing) { if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, "Refresh statistics", tint = Muted) }
    }
    if (apps.isEmpty()) EmptyCard(Icons.Rounded.BarChart, "A fresh start", "Your app breakdown will appear after Usage Access is enabled and you use your phone.")
    else Panel {
        apps.take(10).forEachIndexed { i, app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val color = when (app.packageName) { "com.instagram.android" -> Peach; "com.google.android.youtube" -> Color(0xFFF1DBD5); else -> Sage }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text(label(app.packageName).take(1), color = Coral, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(label(app.packageName), fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { (app.millis.toFloat() / maxOf(1L, apps.maxOf { it.millis })).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Coral.copy(alpha = .6f), trackColor = Sage)
                }
                Text(duration(app.millis), color = Muted, fontSize = 13.sp)
            }
            if (i != minOf(9, apps.lastIndex)) HorizontalDivider(color = Paper)
        }
    }
    Text(if (day != null) "${ago(day.collectedAt)} · totals may differ from Digital Wellbeing. Split-screen app times can overlap." else "Statistics stay on this phone until you choose to share them.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
}

@Composable private fun Metric(title: String, value: String, caption: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = color) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(23.dp))
            Text(title, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 33.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
            Text(caption, fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable private fun FamilyScreen(model: DashboardModel, prefs: PreferencesState, state: FamilyState) {
    val repo = model.container.family
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(true) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    var leave by remember { mutableStateOf(false) }
    Title("Better, together", state.familyName.ifBlank { "Make room for\nfamily time." }, "A shared view of your habits. A little encouragement for everyone.")
    if (!state.configured) {
        EmptyCard(Icons.Rounded.PeopleOutline, "Your family space is ready", "Local tracking works now. Connect a Firebase project to enable accounts, invitations, and shared statistics.")
        Panel {
            Text("One-time setup", fontWeight = FontWeight.SemiBold)
            Text("1. Create a Firebase project and Android app.\n2. Enable Email/Password Authentication.\n3. Deploy the included Firestore rules.\n4. Add firebase.properties and rebuild.", fontSize = 13.sp, color = Muted, lineHeight = 24.sp)
            Text("The complete steps are in docs/FIREBASE_SETUP.md in the project.", fontSize = 12.sp, color = Coral)
        }
        return
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral)
    if (state.uid == null) {
        Panel {
            Text(if (register) "Create your account" else "Welcome back", fontFamily = FontFamily.Serif, fontSize = 25.sp)
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Button(onClick = { val submittedPassword = password; val submittedEmail = email; val createAccount = register; model.runAction { repo.authenticate(submittedEmail, submittedPassword, createAccount) }; password = "" }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Create account" else "Sign in") }
            TextButton(onClick = { register = !register }) { Text(if (register) "Already have an account? Sign in" else "New here? Create an account") }
            TextButton(onClick = { model.runAction { repo.resetPassword(email) } }, enabled = email.isNotBlank() && !state.busy) { Text("Send password reset email") }
        }
    } else if (prefs.familyId.isBlank()) {
        Panel {
            Text("Start your circle", fontFamily = FontFamily.Serif, fontSize = 25.sp)
            OutlinedTextField(familyName, { familyName = it }, label = { Text("Family name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { model.runAction { repo.createFamily(familyName) } }, enabled = !state.busy && familyName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create a family") }
        }
        Panel {
            Text("Have an invitation?", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(invite, { invite = it }, label = { Text("Invitation code") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { model.runAction { repo.joinFamily(invite) } }, enabled = !state.busy && invite.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Join family") }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY’S FAMILY SNAPSHOT", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            IconButton(onClick = { model.refresh() }) { Icon(Icons.Rounded.Refresh, "Refresh family") }
        }
        state.members.forEach { member ->
            var expanded by remember(member.uid) { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(42.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) {
                            Text(member.name + if (member.uid == state.uid) " (you)" else "", fontWeight = FontWeight.SemiBold)
                            Text(ago(member.updatedAt), fontSize = 11.sp, color = Muted)
                        }
                        Text(if (member.devices == 0) "—" else duration(member.screenMillis), fontSize = 24.sp, fontFamily = FontFamily.Serif)
                    }
                    Text(if (member.devices == 0) "No shared data for today" else "${member.views} estimated short videos · ${duration(member.shortMillis)} · ${member.devices} device(s)", fontSize = 12.sp, color = Muted)
                    if (member.apps.isNotEmpty()) {
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide app breakdown" else "View app breakdown") }
                        if (expanded) member.apps.entries.sortedByDescending { it.value }.take(10).forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(model.label(entry.key), fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                                Text(duration(entry.value), fontSize = 12.sp, color = Ink)
                            }
                        }
                    }
                }
            }
        }
        if (state.members.isEmpty()) Text("Loading your family…", color = Muted)
        Panel {
            ToggleRow("Share my daily totals", "Screen time and short-video estimates", prefs.sharing, !state.busy) { model.runAction { repo.changeSharing(it) } }
            Text("Turning sharing off also removes your previously shared statistics when the request completes online.", fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
            OutlinedButton(onClick = { model.runAction { repo.createInvite() } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PersonAddAlt, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Invite a family member") }
            if (state.invite.isNotBlank()) {
                Text("Invitation valid for 24 hours. Share only with people you want in this family.", fontSize = 12.sp, color = Muted)
                OutlinedButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Join our KinTime family. Invitation code (valid 24 hours): ${state.invite}") }, "Share invitation"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Share invitation code") }
                TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("KinTime invitation", state.invite)) }) { Text("Copy code") }
            }
            TextButton(onClick = { leave = true }, enabled = !state.busy) { Text("Leave family & remove my shared stats") }
        }
        Text("${ago(prefs.lastSync)} · background uploads are periodic and may be delayed. Multiple-device totals are added together.", fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
    }
    if (state.uid != null) TextButton(onClick = { model.runAction { repo.signOut() } }, enabled = !state.busy) { Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Sign out · ${state.email}") }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("Leave this family?") }, text = { Text("Your shared statistics will be removed online. Your local history stays on this phone. You will need a new invitation to rejoin.") },
        confirmButton = { TextButton(onClick = { leave = false; model.runAction { repo.leaveFamily() } }) { Text("Leave family") } }, dismissButton = { TextButton(onClick = { leave = false }) { Text("Stay") } })
}

@Composable private fun TrackingScreen(model: DashboardModel, prefs: PreferencesState, hasAccess: Boolean, cloudConfigured: Boolean, settings: (String) -> Unit, consent: () -> Unit) {
    var name by remember(prefs.displayName) { mutableStateOf(prefs.displayName) }
    var goal by remember(prefs.goalMinutes) { mutableStateOf(prefs.goalMinutes.toString()) }
    var clear by remember { mutableStateOf(false) }
    Title("You’re in control", "Good habits start\nwith a clear picture.", "Choose what to track, and what to share.")
    Panel {
        Text("Your profile", fontFamily = FontFamily.Serif, fontSize = 24.sp)
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(goal, { goal = it.filter(Char::isDigit).take(4) }, label = { Text("Daily screen-time goal (minutes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { model.container.preferences.name(name); goal.toIntOrNull()?.let { model.container.preferences.goal(it) } }) { Text("Save profile") }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.QueryStats, null, tint = Coral)
            Text("Screen-time tracking", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (hasAccess) "ON" else "OFF", fontSize = 10.sp, color = if (hasAccess) Ink else Coral, fontWeight = FontWeight.Bold)
        }
        Text(prefs.collectorMessage, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Button(onClick = { settings("usage") }, modifier = Modifier.fillMaxWidth()) { Text(if (hasAccess) "Manage Usage Access" else "Enable Usage Access"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp)) }
    }
    Panel {
        ToggleRow("Reels & Shorts estimates", "Optional accessibility-based recognition", prefs.videoConsent, true) {
            if (it) consent() else model.container.preferences.videoConsent(false)
        }
        Text(prefs.detectorMessage, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        if (prefs.videoConsent) OutlinedButton(onClick = { settings("accessibility") }, modifier = Modifier.fillMaxWidth()) { Text("Open Accessibility Settings") }
        Text("A detected appearance lasting 2+ seconds counts as one estimated view. Loops do not add views; revisits can. App updates and unsupported screens can cause gaps.", fontSize = 11.sp, color = Muted, lineHeight = 18.sp)
    }
    Panel {
        Text("Keep tracking healthy", fontWeight = FontWeight.SemiBold)
        Text("On Samsung: Battery → Background usage limits → Never sleeping apps. Add KinTime if collection or syncing is delayed. A sideloaded build may also need App info → Allow restricted settings before Accessibility can be enabled.", fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
        TextButton(onClick = { settings("app") }) { Text("Open app settings") }
    }
    Panel {
        Text("Privacy, by choice", fontFamily = FontFamily.Serif, fontSize = 24.sp)
        Text("Your usage history stays in this phone’s private database. Family sharing is off by default. Sharing uploads daily totals and short-video estimates to your family’s private Firebase space.", fontSize = 13.sp, color = Muted, lineHeight = 21.sp)
        ToggleRow("Share app breakdowns too", "Optional · package names and durations", prefs.shareApps, cloudConfigured && prefs.familyId.isNotBlank()) { model.runAction { model.container.family.changeAppSharing(it) } }
        Text("Phone owners can revoke access or uninstall the app. Missing or stale statistics do not mean zero usage. Secure Folder, work profiles, and browser short-video feeds are outside this first version.", fontSize = 11.sp, color = Muted, lineHeight = 18.sp)
        TextButton(onClick = { clear = true }) { Text("Delete local history") }
    }
    Text("KINTIME 0.2 · MADE FOR YOUR FAMILY", fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Delete local history?") }, text = { Text("Deletes saved statistics and activity logs on this phone, including any running timer. Android may restore recent screen-time events on the next refresh. Previously shared cloud records are separate; turn off sharing to remove those.") },
        confirmButton = { TextButton(onClick = { clear = false; model.runAction { model.container.dao.clearAll() } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
}
@Composable private fun EmptyCard(icon: ImageVector, title: String, body: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = Sage.copy(alpha = .65f)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(30.dp))
            Text(title, fontFamily = FontFamily.Serif, fontSize = 24.sp)
            Text(body, fontSize = 13.sp, color = Muted, lineHeight = 21.sp)
        }
    }
}
@Composable private fun Notice(text: String, icon: ImageVector, action: () -> Unit) {
    Surface(onClick = action, shape = RoundedCornerShape(18.dp), color = Peach) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Coral, modifier = Modifier.size(20.dp))
            Text(text, modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Coral, modifier = Modifier.size(16.dp))
        }
    }
}
@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(checked, change, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Ink))
    }
}
