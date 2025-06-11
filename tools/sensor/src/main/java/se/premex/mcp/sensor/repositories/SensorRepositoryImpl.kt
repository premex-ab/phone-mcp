package se.premex.mcp.sensor.repositories

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

class SensorRepositoryImpl(
    private val context: Context
) : SensorRepository {
    override fun getStatus(): List<SensorInfo> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()

        // Get the list of all available sensors
        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)

        return deviceSensors.map { sensor ->
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
                id = sensor.id
            )
        }
    }
}
