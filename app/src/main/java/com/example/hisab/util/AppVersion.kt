package com.example.hisab.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The app's own version, read from the installed package at runtime.
 *
 * Settings used to render the literals `"Hisab v3.1.2"` and `"Build 312"`, which had already drifted
 * two releases behind `versionName`/`versionCode` in `app/build.gradle.kts` — so the About card
 * confidently reported the wrong build to the one person trying to tell us which build misbehaved.
 * Reading the values back from the `PackageManager` makes that class of drift impossible: there is
 * only one place left to change, and it is the place Gradle already writes.
 *
 * `PackageManager` is used rather than `BuildConfig` because the module does not enable the
 * `buildConfig` feature (off by default from AGP 8), and turning it on to fetch two strings would be
 * a build-graph change in service of a label.
 */
object AppVersion {

    /** e.g. `"3.2.0"`. Falls back to `"unknown"` — a wrong number is worse than an honest blank. */
    fun name(context: Context): String = info(context)?.versionName ?: "unknown"

    /** e.g. `320`, or `null` when the package cannot be read. */
    fun code(context: Context): Long? = info(context)?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
        else @Suppress("DEPRECATION") it.versionCode.toLong()
    }

    private fun info(context: Context) = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
