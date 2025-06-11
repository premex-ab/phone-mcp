package se.premex.mcp.sensor.repositories

data class SensorInfo(
    val name: String,
    val type: Int,
    val vendor: String,
    val version: Int,
    val resolution: Float,
    val power: Float,
    val maxRange: Float,
    val minDelay: Int,
    val isWakeUpSensor: Boolean,
    val reportingMode: Int,
    val maxDelay: Int,
    val fifoMaxEventCount: Int,
    val fifoReservedEventCount: Int,
    val stringType: String?,
    val id: Int,
    val values: List<Float> = emptyList(),
    val accuracy: Int = -1,
    val timestamp: Long = 0,
    val valueDescription: String? = null
)
