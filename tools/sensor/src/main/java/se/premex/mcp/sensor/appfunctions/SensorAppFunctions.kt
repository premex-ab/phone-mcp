package se.premex.mcp.sensor.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import se.premex.mcp.sensor.repositories.SensorInfo
import se.premex.mcp.sensor.repositories.SensorRepository
import javax.inject.Inject

/** Metadata and latest cached values for one Android sensor. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFunctionSensor(
    /** Human-readable sensor name. */
    val name: String,
    /** Android sensor type integer. */
    val type: Int,
    /** Sensor manufacturer. */
    val vendor: String,
    /** Android string type, such as android.sensor.accelerometer. */
    val stringType: String?,
    /** Latest raw values as a comma-separated list; empty when no reading has arrived. */
    val rawValues: String,
    /** Android accuracy constant for the latest reading, or -1 when unavailable. */
    val accuracy: Int,
    /** Latest sensor-event timestamp in nanoseconds since boot, or 0 when unavailable. */
    val timestamp: Long,
    /** Human-readable interpretation of the latest values. */
    val valueDescription: String?,
)

/** Phone actions that return a one-shot snapshot of available sensors. */
class SensorAppFunctions @Inject constructor(
    private val sensorRepository: SensorRepository,
) {
    /**
     * Get the available Android sensors and their latest cached readings.
     *
     * This is a one-shot snapshot, not a continuous stream. Some sensors may have empty values
     * when Android has not delivered a reading yet.
     *
     * @param appFunctionContext The Android execution context for this function invocation.
     * @return Available sensors with their latest cached values.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun getSensorSnapshot(
        appFunctionContext: AppFunctionContext,
    ): List<AppFunctionSensor> = executeGetSensorSnapshot()

    internal fun executeGetSensorSnapshot(): List<AppFunctionSensor> =
        sensorRepository.getStatus().map(SensorInfo::toAppFunctionSensor)
}

private fun SensorInfo.toAppFunctionSensor() = AppFunctionSensor(
    name = name,
    type = type,
    vendor = vendor,
    stringType = stringType,
    rawValues = values.joinToString(", "),
    accuracy = accuracy,
    timestamp = timestamp,
    valueDescription = valueDescription,
)
