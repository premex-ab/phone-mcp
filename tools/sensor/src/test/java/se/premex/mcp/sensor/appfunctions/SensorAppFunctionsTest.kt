package se.premex.mcp.sensor.appfunctions

import org.junit.Assert.assertEquals
import org.junit.Test
import se.premex.mcp.sensor.repositories.SensorInfo
import se.premex.mcp.sensor.repositories.SensorRepository

class SensorAppFunctionsTest {
    @Test
    fun executeGetSensorSnapshot_mapsLatestValues() {
        val repository = object : SensorRepository {
            override fun getStatus() = listOf(
                SensorInfo(
                    name = "Accelerometer",
                    type = 1,
                    vendor = "Example",
                    version = 1,
                    resolution = 0.1f,
                    power = 0.2f,
                    maxRange = 20f,
                    minDelay = 10,
                    isWakeUpSensor = false,
                    reportingMode = 0,
                    maxDelay = 100,
                    fifoMaxEventCount = 0,
                    fifoReservedEventCount = 0,
                    stringType = "android.sensor.accelerometer",
                    id = 7,
                    values = listOf(1f, 2f, 3f),
                    accuracy = 3,
                    timestamp = 99,
                    valueDescription = "X: 1, Y: 2, Z: 3",
                ),
            )
        }

        val result = SensorAppFunctions(repository).executeGetSensorSnapshot().single()

        assertEquals("Accelerometer", result.name)
        assertEquals("1.0, 2.0, 3.0", result.rawValues)
        assertEquals(3, result.accuracy)
        assertEquals(99, result.timestamp)
    }
}
