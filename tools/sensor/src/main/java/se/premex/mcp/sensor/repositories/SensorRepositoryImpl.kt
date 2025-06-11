package se.premex.mcp.sensor.repositories

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.ConcurrentHashMap

class SensorRepositoryImpl(
    private val context: Context
) : SensorRepository {

    // Cache the latest sensor values
    private val sensorValueCache = ConcurrentHashMap<Int, SensorReading>()

    // SensorEventListener that updates the cache with the latest values
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val valuesCopy = it.values.clone()
                sensorValueCache[it.sensor.type] = SensorReading(
                    values = valuesCopy.toList(),
                    accuracy = it.accuracy,
                    timestamp = it.timestamp
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            sensor?.let {
                val currentReading = sensorValueCache[it.type]
                if (currentReading != null) {
                    sensorValueCache[it.type] = currentReading.copy(accuracy = accuracy)
                }
            }
        }
    }

    // Initialize sensor collection on instantiation
    init {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager != null) {
            val deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            for (sensor in deviceSensors) {
                // Register listeners for all sensors with a normal rate
                sensorManager.registerListener(
                    sensorListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    override fun getStatus(): List<SensorInfo> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()

        // Get the list of all available sensors
        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)

        return deviceSensors.map { sensor ->
            val sensorReading = sensorValueCache[sensor.type]

            SensorInfo(
                name = sensor.name,
                type = sensor.type,
                vendor = sensor.vendor,
                version = sensor.version,
                resolution = sensor.resolution,
                power = sensor.power,
                maxRange = sensor.maximumRange,
                minDelay = sensor.minDelay,
                isWakeUpSensor = sensor.isWakeUpSensor,
                reportingMode = sensor.reportingMode,
                maxDelay = sensor.maxDelay,
                fifoMaxEventCount = sensor.fifoMaxEventCount,
                fifoReservedEventCount = sensor.fifoReservedEventCount,
                stringType = sensor.stringType,
                id = sensor.id,
                values = sensorReading?.values ?: emptyList(),
                accuracy = sensorReading?.accuracy ?: -1,
                timestamp = sensorReading?.timestamp ?: 0,
                valueDescription = formatSensorValues(sensor.type, sensorReading?.values ?: emptyList())
            )
        }
    }

    // Helper class to store sensor readings
    private data class SensorReading(
        val values: List<Float>,
        val accuracy: Int,
        val timestamp: Long
    )

    // Helper function to format sensor values in a human-readable way
    private fun formatSensorValues(sensorType: Int, values: List<Float>): String {
        if (values.isEmpty()) return "No data"

        return when (sensorType) {
            Sensor.TYPE_ACCELEROMETER ->
                "X: ${values[0]} m/s², Y: ${values[1]} m/s², Z: ${values[2]} m/s²"

            Sensor.TYPE_GYROSCOPE ->
                "X: ${values[0]} rad/s, Y: ${values[1]} rad/s, Z: ${values[2]} rad/s"

            Sensor.TYPE_MAGNETIC_FIELD ->
                "X: ${values[0]} μT, Y: ${values[1]} μT, Z: ${values[2]} μT"

            Sensor.TYPE_LIGHT ->
                "${values[0]} lux"

            Sensor.TYPE_PRESSURE ->
                "${values[0]} hPa"

            Sensor.TYPE_PROXIMITY ->
                "${values[0]} cm"

            Sensor.TYPE_GRAVITY ->
                "X: ${values[0]} m/s², Y: ${values[1]} m/s², Z: ${values[2]} m/s²"

            Sensor.TYPE_AMBIENT_TEMPERATURE ->
                "${values[0]} °C"

            Sensor.TYPE_RELATIVE_HUMIDITY ->
                "${values[0]} %"

            Sensor.TYPE_HEART_RATE ->
                "${values[0]} bpm"

            Sensor.TYPE_STEP_COUNTER ->
                "${values[0].toInt()} steps"

            else -> values.joinToString(", ") { it.toString() }
        }
    }
}
