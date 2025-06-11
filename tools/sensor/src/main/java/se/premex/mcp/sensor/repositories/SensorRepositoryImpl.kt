package se.premex.mcp.sensor.repositories

import android.content.Context

class SensorRepositoryImpl(
    private val context: Context
): SensorRepository {
    override fun getStatus(): List<SensorInfo> {
        TODO("Not yet implemented")
    }

}

