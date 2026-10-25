package app.kintime.family.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.kintime.family.DashboardModel
import app.kintime.family.core.TimeLogMath
import app.kintime.family.data.TimeEntry
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val LogInk = Color(0xFF243B36)
private val LogMuted = Color(0xFF6C7A73)
private val LogSage = Color(0xFFE3EAE1)
private val LogGold = Color(0xFFEFC685)
private data class HistorySlice(val entry: TimeEntry, val day: LocalDate, val start: Long, val end: Long)

@Composable
fun TimeLogScreen(model: DashboardModel) {
    val focusManager = LocalFocusManager.current
    val entries by model.timeEntries.collectAsStateWithLifecycle()
    val running = entries.firstOrNull { it.activeSlot == 1 }
    var description by rememberSaveable { mutableStateOf("") }
    var project by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editor by remember { mutableStateOf<TimeEntry?>(null) }
    var addManual by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TimeEntry?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(running?.id, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(if (running == null) 30_000 else 1_000) }
        }
    }
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
    val todayTotal = entries.sumOf { TimeLogMath.overlap(it.startedAt, it.endedAt ?: now, dayStart, now) }
    val weekTotal = entries.sumOf { TimeLogMath.overlap(it.startedAt, it.endedAt ?: now, weekStart, now) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MAKE TIME FOR WHAT MATTERS", fontSize = 10.sp, letterSpacing = 1.5.sp, color = LogMuted, fontWeight = FontWeight.Bold)
        Text("Your time log", fontSize = 32.sp, fontFamily = FontFamily.Serif)
        Text("Pick an activity. Press play. Keep a little record of your day.", color = LogMuted, fontSize = 13.sp, lineHeight = 20.sp)
    }
    Surface(shape = RoundedCornerShape(26.dp), color = LogInk) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val fieldColors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = LogGold, unfocusedBorderColor = Color.White.copy(alpha = .3f),
                focusedLabelColor = LogGold, unfocusedLabelColor = LogSage, cursorColor = LogGold)
            OutlinedTextField(running?.description ?: description, { description = it.take(160) },
                modifier = Modifier.fillMaxWidth().testTag("timer-description"), label = { Text("What are you working on?") },
                readOnly = running != null, singleLine = true, colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
            OutlinedTextField(running?.project ?: project, { project = it.take(60) },
                modifier = Modifier.fillMaxWidth().testTag("timer-project"), label = { Text("Project or category (optional)") },
                readOnly = running != null, singleLine = true, colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (running == null) "READY WHEN YOU ARE" else "TIMER RUNNING", color = LogGold, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(TimeLogMath.clock(if (running == null) 0 else now - running.startedAt), color = Color.White,
                        fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, modifier = Modifier.testTag("running-clock"))
                }
                FilledIconButton(onClick = {
                    focusManager.clearFocus(force = true)
                    if (running == null) model.startTimer(description, project) else model.stopTimer()
                }, modifier = Modifier.size(64.dp).testTag("timer-toggle"), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = LogGold, contentColor = LogInk)) {
                    Icon(if (running == null) Icons.Rounded.PlayArrow else Icons.Rounded.Stop,
                        if (running == null) "Start timer" else "Stop timer", Modifier.size(34.dp))
                }
            }
            Text(if (running == null) "Works offline. No special permissions needed." else "Your timer keeps its start time when you leave the app. Return here to stop it.",
                color = LogSage.copy(alpha = .8f), fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LogTotal("TODAY", todayTotal, Modifier.weight(1f))
        LogTotal("THIS WEEK", weekTotal, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Activity history", fontFamily = FontFamily.Serif, fontSize = 24.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { addManual = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(17.dp)); Text("Log time") }
    }
    val history = entries.filter { it.endedAt != null }.take(100).flatMap { entry ->
        TimeLogMath.splitByDay(entry.startedAt, entry.endedAt!!, zone).map { HistorySlice(entry, it.day, it.start, it.end) }
    }
    if (history.isEmpty()) Surface(shape = RoundedCornerShape(22.dp), color = LogSage) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.History, null, tint = LogInk)
            Text("Your first entry starts here", fontFamily = FontFamily.Serif, fontSize = 23.sp)
            Text("Stop a timer to save an entry, or use Log time to add something you’ve already done.", fontSize = 13.sp, color = LogMuted, lineHeight = 20.sp)
        }
    }
    val weeks = history.groupBy { it.day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap(compareByDescending { it })
    weeks.forEach { (startOfWeek, slices) ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${startOfWeek.format(DateTimeFormatter.ofPattern("dd MMM"))} – ${startOfWeek.plusDays(6).format(DateTimeFormatter.ofPattern("dd MMM"))}".uppercase(), fontSize = 11.sp, color = LogMuted, fontWeight = FontWeight.Bold)
                Text(TimeLogMath.clock(slices.sumOf { it.end - it.start }), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            slices.groupBy { it.day }.toSortedMap(compareByDescending { it }).forEach { (date, daySlices) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (date == today) "TODAY" else date.format(DateTimeFormatter.ofPattern("EEE, dd MMM")).uppercase(), fontSize = 10.sp, color = LogMuted, letterSpacing = 1.sp)
                    Text(TimeLogMath.clock(daySlices.sumOf { it.end - it.start }), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = LogMuted)
                }
                daySlices.sortedByDescending { it.start }.forEach { slice ->
                    var menu by remember(slice.entry.id, slice.day) { mutableStateOf(false) }
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(slice.entry.description.ifBlank { "No description" }, fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(TimeLogMath.clock(slice.end - slice.start), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "Entry options", Modifier.size(20.dp)) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Continue activity") }, enabled = running == null, onClick = { menu = false; model.startTimer(slice.entry.description, slice.entry.project) })
                                        DropdownMenuItem(text = { Text("Edit entry") }, onClick = { menu = false; editor = slice.entry })
                                        DropdownMenuItem(text = { Text("Delete entry") }, onClick = { menu = false; deleting = slice.entry })
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("●  ${slice.entry.project.ifBlank { "No project" }}", color = LogMuted, fontSize = 11.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${timeLabel(slice.start, zone)} – ${timeLabel(slice.end, zone)}", color = LogMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    Text("Activity logs stay on this phone and are separate from automatic screen time. Entries spanning midnight are split across days. History shows your 100 most recent finished entries.", color = LogMuted, fontSize = 11.sp, lineHeight = 18.sp)
    if (addManual || editor != null) EntryEditor(editor, onDismiss = { addManual = false; editor = null }) { id, title, category, start, end ->
        model.saveEntry(id, title, category, start, end); addManual = false; editor = null
    }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this time entry?") },
        text = { Text("This removes the full activity entry from this phone.") },
        confirmButton = { TextButton(onClick = { model.deleteEntry(entry.id); deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep entry") } }) }
}

@Composable private fun LogTotal(label: String, millis: Long, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = LogSage.copy(alpha = .65f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(label, color = LogMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(TimeLogMath.clock(millis), fontFamily = FontFamily.Monospace, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun timeLabel(epoch: Long, zone: ZoneId): String = Instant.ofEpochMilli(epoch).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable private fun EntryEditor(entry: TimeEntry?, onDismiss: () -> Unit, save: (String?, String, String, Long, Long) -> Unit) {
    var description by remember { mutableStateOf(entry?.description.orEmpty()) }
    var project by remember { mutableStateOf(entry?.project.orEmpty()) }
    val zone = ZoneId.systemDefault()
    var start by remember { mutableLongStateOf(entry?.startedAt ?: System.currentTimeMillis() - 30 * 60_000) }
    var end by remember { mutableLongStateOf(entry?.endedAt ?: System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (entry == null) "Log an activity" else "Edit activity") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(description, { description = it.take(160) }, label = { Text("Activity description") }, singleLine = true, modifier = Modifier.testTag("entry-description"))
            OutlinedTextField(project, { project = it.take(60) }, label = { Text("Project (optional)") }, singleLine = true)
            DateTimeField("Start", start, zone) { start = it }
            DateTimeField("End", end, zone) { end = it }
            Text("Duration · ${TimeLogMath.clock(end - start)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { TimeLogMath.validate(start, end, System.currentTimeMillis()) }
                .onSuccess { save(entry?.id, description, project, start, end) }.onFailure { error = it.message }
        }) { Text("Save entry") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun DateTimeField(label: String, epoch: Long, zone: ZoneId, change: (Long) -> Unit) {
    val context = LocalContext.current
    val value = Instant.ofEpochMilli(epoch).atZone(zone)
    Column {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, year, month, day ->
                    change(value.withYear(year).withDayOfMonth(1).withMonth(month + 1).withDayOfMonth(day).toInstant().toEpochMilli())
                }, value.year, value.monthValue - 1, value.dayOfMonth).show()
            }) { Text(value.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), fontSize = 11.sp) }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute -> change(value.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()) }, value.hour, value.minute, true).show()
            }) { Text(value.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 11.sp) }
        }
    }
}
