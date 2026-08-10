package com.vkaan.runtimeinspector.report

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import java.util.UUID

/**
 * Builds the [SessionContext] once per process, at init.
 *
 * Best-effort throughout: every lookup has a fallback and nothing throws into the host's
 * startup path, because a missing version number must not stop findings being collected.
 */
internal object SessionContextFactory {

    private const val TAG = "RuntimeInspector"
    private const val UNKNOWN = "unknown"

    /** Bumped by hand — the library module generates no BuildConfig. */
    private const val LIBRARY_VERSION = "0.1.0"

    /** Recorded when the build number cannot be read, so it is never confused with build 0. */
    private const val UNKNOWN_VERSION_CODE = -1L

    fun create(context: Context): SessionContext {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read package info — findings will not name a build.", t)
            null
        }

        return SessionContext(
            // A fresh id per process: two cold starts of the same build are two sessions.
            sessionId = UUID.randomUUID().toString(),
            appPackage = context.packageName,
            appVersionName = packageInfo?.versionName ?: UNKNOWN,
            appVersionCode = packageInfo?.versionCodeCompat() ?: UNKNOWN_VERSION_CODE,
            libraryVersion = LIBRARY_VERSION,
            deviceModel = deviceName(Build.MANUFACTURER, Build.MODEL),
            androidSdkInt = Build.VERSION.SDK_INT,
        )
    }

    // versionCode became a Long in API 28 and the Int accessor was deprecated. The fleet is
    // Android 9 but minSdk is 24, so both paths have to stay.
    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else versionCode.toLong()

    /**
     * Joins make and model: `PAX` + `A920` becomes "PAX A920", but a model that already
     * carries its maker is left alone rather than becoming "Google Google Pixel 3".
     */
    fun deviceName(manufacturer: String?, model: String?): String {
        val make = manufacturer?.trim().orEmpty()
        val name = model?.trim().orEmpty()
        return when {
            name.isEmpty() -> make.ifEmpty { UNKNOWN }
            make.isEmpty() || name.startsWith(make, ignoreCase = true) -> name
            else -> "$make $name"
        }
    }
}
