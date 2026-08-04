package com.vkaan.runtimeinspector.report

/**
 * Everything a finding needs to be attributable to a specific run of a specific build.
 *
 * Deliberately free of Android types so it stays unit-testable; the Android-side builder that
 * fills it from PackageManager and Build lands separately.
 *
 * @param sessionId identifies one process lifetime — a new one on every cold start.
 * @param appVersionCode the build under test. Without this a finding says nothing about
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
