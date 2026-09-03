package com.google.samples.apps.mcp

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice

/**
 * Configure project for Gradle managed devices
 */
internal fun configureGradleManagedDevices(
    commonExtension: CommonExtension,
) {
    val pixel4 = DeviceConfig("Pixel 4", 30, "aosp-atd")
    val pixel6 = DeviceConfig("Pixel 6", 31, "aosp")
    val pixelC = DeviceConfig("Pixel C", 30, "aosp-atd")

    val allDevices = listOf(pixel4, pixel6, pixelC)
    val ciDevices = listOf(pixel4, pixelC)

    val managedDevices = commonExtension.testOptions.managedDevices

    allDevices.forEach { deviceConfig ->
        managedDevices.localDevices.maybeCreate(deviceConfig.taskName).apply {
            device = deviceConfig.device
            apiLevel = deviceConfig.apiLevel
            systemImageSource = deviceConfig.systemImageSource
        }
    }

    managedDevices.groups.maybeCreate("ci").apply {
        ciDevices.forEach { deviceConfig ->
            targetDevices.add(managedDevices.localDevices.getByName(deviceConfig.taskName))
        }
    }
}

private data class DeviceConfig(
    val device: String,
    val apiLevel: Int,
    val systemImageSource: String,
) {
    val taskName = buildString {
        append(device.lowercase().replace(" ", ""))
        append("api")
        append(apiLevel.toString())
        append(systemImageSource.replace("-", ""))
    }
}
