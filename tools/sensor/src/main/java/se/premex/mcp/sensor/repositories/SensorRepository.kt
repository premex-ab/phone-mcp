package se.premex.mcp.sensor.repositories

interface SensorRepository {
     fun getStatus(): List<SensorInfo>
}

