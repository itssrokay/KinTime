package app.kintime.family

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("KinTime steps privacy", style = MaterialTheme.typography.headlineMedium)
                    Text("KinTime reads step-count totals from Health Connect to show your daily progress and recent history. It requests read access to steps only; it does not write health records, read your medical records, or collect routes, heart rate, or sleep data.")
                    Text("Health Connect may combine steps from Samsung Health, Fitbit/Google Health, connected watches, and this phone using your source-priority settings. Optional background access lets periodic work refresh steps when KinTime is closed.")
                    Text("Daily step totals, dates, time zone, and reading times are stored in this phone’s private database, excluded from Android backup. Family sharing is off by default. Only when you enable both family sharing and Share steps with family will daily totals and reading times be uploaded to your family’s Firebase space, where joined family members can see them.")
                    Text("No raw health records, source identifiers, or sensor streams are uploaded. There are no advertising or analytics SDKs in this app. Step data is not sold or used for advertising.")
                    Text("You can revoke permission in Health Connect, disconnect in the Steps tab, or delete saved local history in Tracking. Disabling step sharing removes this device’s previously shared step snapshots when the online cleanup succeeds; a failed cleanup is saved and retried on the next sync. Leaving the family removes your shared snapshots. Other devices signed in to the same account can still share if you keep sharing enabled there.")
                    Text("Disconnecting retains saved local readings until you delete local history; deleted local readings can be imported again if you reconnect. Samsung/Fitbit syncing and Android background scheduling can delay totals. Step counts are for general wellbeing.")
                    Button(onClick = { finish() }) { Text("Done") }
                } }
            }
        }
    }
}
