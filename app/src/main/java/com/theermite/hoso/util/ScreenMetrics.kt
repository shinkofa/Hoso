package com.theermite.hoso.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowManager

/**
 * Real (full) screen size, resolved on every supported API level.
 *
 * [WindowManager.getMaximumWindowMetrics] only exists from API 30. minSdk is
 * 29, so calling it directly crashes on Android 10 at launch (it runs from
 * StreamConfig, built in MainActivity.onCreate). This helper guards on the
 * API level and falls back to the legacy real DisplayMetrics on API 29.
 */
object ScreenMetrics {

    fun realScreenSize(context: Context): Size {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also {
                wm.defaultDisplay.getRealMetrics(it)
            }
            Size(metrics.widthPixels, metrics.heightPixels)
        }
    }
}
