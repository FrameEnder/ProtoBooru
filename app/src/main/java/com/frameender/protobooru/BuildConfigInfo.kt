package com.frameender.protobooru

import android.content.Context

/** Reads version info from the package manager (BuildConfig generation is off by default in AGP 8). */
object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "dev"
}
