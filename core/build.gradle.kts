plugins {
    alias(libs.plugins.mcp.jvm.library)
}

dependencies {
    implementation(libs.io.modelcontextprotocol.kotlin.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
