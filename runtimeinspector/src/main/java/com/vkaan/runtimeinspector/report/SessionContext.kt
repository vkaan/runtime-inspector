package com.vkaan.runtimeinspector.report

/**
 * Everything a finding needs to name the run and the build it came from.
 *
 * Free of Android types so it stays unit-testable; [SessionContextFactory] fills it.
 *
 * @param sessionId one process lifetime — a new one on every cold start.
 * @param appVersionCode the build under test; without it a finding says nothing about
 *   whether a given bank build passes.
 */
internal data class SessionContext(
    val sessionId: String,
    val appPackage: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val libraryVersion: String,
    val deviceModel: String,
    val androidSdkInt: Int,
)
