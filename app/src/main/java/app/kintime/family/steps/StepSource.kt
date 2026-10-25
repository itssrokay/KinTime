package app.kintime.family.steps

import android.content.Context
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

data class StepAccess(val available: Boolean, val readAllowed: Boolean = false,
                      val backgroundSupported: Boolean = false, val backgroundAllowed: Boolean = false,
                      val phoneCountingSupported: Boolean = false)

interface StepSource {
    suspend fun access(): StepAccess
    suspend fun total(start: Instant, end: Instant): Long?
    suspend fun revoke()
}

class HealthConnectStepSource(private val context: Context) : StepSource {
    private val client get() = HealthConnectClient.getOrCreate(context)
    override suspend fun access(): StepAccess {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return StepAccess(false)
        val granted = client.permissionController.getGrantedPermissions()
        val background = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return StepAccess(true, READ_STEPS in granted, background, background && BACKGROUND in granted,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 20)
    }
    override suspend fun total(start: Instant, end: Instant): Long? {
        // No data-origin filter: Health Connect applies source priority and includes native phone steps.
        return client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL]
    }
    override suspend fun revoke() { if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) client.permissionController.revokeAllPermissions() }
    companion object {
        val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}
