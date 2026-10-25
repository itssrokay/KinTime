package app.kintime.family.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kintime.family.DashboardModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.NumberFormat

@Composable
fun StepsScreen(model: DashboardModel, openSettings: (String) -> Unit) {
    val state by model.stepsState.collectAsStateWithLifecycle()
    val days by model.stepsWeek.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var disclosure by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    var goal by remember(prefs.stepGoal) { mutableStateOf(prefs.stepGoal.toString()) }
    val reading = days.firstOrNull { it.day == date }
    val ink = Color(0xFF243B36)
    val sage = Color(0xFFE3EAE1)
    val muted = Color(0xFF6C7A73)
    Text("A little more movement.", fontFamily = FontFamily.Serif, fontSize = 32.sp)
    Text("Steps from your phone and connected health apps, brought together with Health Connect.", color = muted, fontSize = 14.sp, lineHeight = 21.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (6L downTo 0L).forEach { offset ->
            val day = LocalDate.now().minusDays(offset)
            FilterChip(selected = date == day.toString(), onClick = { date = day.toString() },
                label = { Text(day.dayOfMonth.toString(), fontSize = 11.sp) }, modifier = Modifier.weight(1f))
        }
    }
    Surface(shape = RoundedCornerShape(28.dp), color = ink) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (date == LocalDate.now().toString()) "STEPS TODAY" else LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, d MMM")).uppercase(),
                    fontSize = 11.sp, color = sage, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.DirectionsWalk, null, tint = Color(0xFFEFC685))
            }
            Text(reading?.count?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—", fontSize = 58.sp,
                color = Color.White, fontWeight = FontWeight.Light)
            LinearProgressIndicator(progress = { ((reading?.count ?: 0L).toFloat() / prefs.stepGoal).coerceIn(0f, 1f) },
                color = Color(0xFFEFC685), trackColor = Color.White.copy(alpha = .15f), modifier = Modifier.fillMaxWidth())
            Text("Daily goal · ${NumberFormat.getIntegerInstance().format(prefs.stepGoal)} steps", fontSize = 12.sp, color = sage)
            Text(if (reading == null) "No reading for this day · this does not mean zero steps" else
                "Health Connect · read ${java.time.Instant.ofEpochMilli(reading.readAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",
                fontSize = 11.sp, color = sage.copy(alpha = .8f))
        }
    }
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(state.message, color = muted, fontSize = 13.sp, lineHeight = 20.sp)
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    if (prefs.stepCleanupPending && prefs.familyId.isNotBlank()) {
        Text("Shared steps cleanup is pending. KinTime will retry when connected, even with sharing off.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
    if (!prefs.stepsEnabled || !state.access.readAllowed) {
        Button(onClick = { disclosure = true }, enabled = state.access.available, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Connect step data")
        }
    } else {
        OutlinedButton(onClick = { model.refresh() }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Refresh steps") }
        if (state.access.backgroundSupported && !state.access.backgroundAllowed) {
            OutlinedButton(onClick = { openSettings("steps-background") }, modifier = Modifier.fillMaxWidth()) { Text("Allow background step refresh") }
            Text("Optional. Without this permission, open KinTime to refresh. Android can delay background updates.", fontSize = 11.sp, color = muted)
        }
    }
    StepPanel {
        Text("Your step goal", fontFamily = FontFamily.Serif, fontSize = 24.sp)
        OutlinedTextField(goal, { goal = it.filter(Char::isDigit).take(6) }, label = { Text("Steps per day") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { goal.toIntOrNull()?.let { model.container.preferences.stepGoal(it) } }) { Text("Save step goal") }
    }
    StepPanel {
        Text("Choose where steps come from", fontFamily = FontFamily.Serif, fontSize = 24.sp)
        Text("Samsung Health / Galaxy Watch", fontWeight = FontWeight.SemiBold)
        Text("In Samsung Health: Settings → Health Connect. Allow Samsung Health to write Steps, then sync your watch and Samsung account. Allow KinTime to read Steps.", color = muted, fontSize = 13.sp, lineHeight = 20.sp)
        Text("Fitbit / Google Health / Google Fit", fontWeight = FontWeight.SemiBold)
        Text("Sync your tracker to its health app. Connect that app to Health Connect and allow it to write Steps. Only apps that support sharing steps to Health Connect can provide data here.", color = muted, fontSize = 13.sp, lineHeight = 20.sp)
        Text("This phone", fontWeight = FontWeight.SemiBold)
        Text(if (state.access.phoneCountingSupported) "This phone's Health Connect version supports built-in step counting after you grant steps access. Keep your Google Play system and health apps updated."
            else "Phone-only counting needs Android 14+ with Health Connect SDK extension 20 or newer. Update the Google Play system, or connect Samsung Health / another step-writing app.", color = muted, fontSize = 13.sp, lineHeight = 20.sp)
        Text("Health Connect applies data-source priority to overlapping phone/watch records. KinTime reads that combined total rather than adding the sources again.", fontSize = 11.sp, color = muted, lineHeight = 18.sp)
        TextButton(onClick = { openSettings("health") }) { Text("Open Health Connect settings") }
    }
    StepPanel {
        Text("Step privacy", fontFamily = FontFamily.Serif, fontSize = 24.sp)
        Text("Reading steps and sharing them with family are separate choices. Only daily totals are saved locally. Enable family sharing in Family, then separately allow Share steps with family.", color = muted, fontSize = 13.sp, lineHeight = 20.sp)
        TextButton(onClick = { openSettings("health-privacy") }) { Text("Read steps privacy policy") }
        if (prefs.stepsEnabled) TextButton(onClick = { disconnect = true }) { Text("Disconnect steps") }
    }
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text("Connect Health Connect?") },
        text = { Text("KinTime will read aggregated step counts to show daily progress and seven-day history. Steps may come from your phone, Samsung Health, Fitbit/Google Health, or other connected apps.\n\nDaily totals are stored on this phone. Sharing with family stays off until you enable Share steps with family separately. You can disconnect or revoke permission at any time.") },
        confirmButton = { TextButton(onClick = { disclosure = false; openSettings("steps-read") }) { Text("Agree & allow steps") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect step data?") },
        text = { Text("Stops reading new step totals and revokes KinTime's Health Connect permissions. Saved local readings remain until you delete local history. Shared step snapshots from this device are removed when online cleanup succeeds.") },
        confirmButton = { TextButton(onClick = {
            disconnect = false
            model.runAction { model.container.family.disconnectSteps { model.container.steps.disconnect() } }
        }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

@Composable private fun StepPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
