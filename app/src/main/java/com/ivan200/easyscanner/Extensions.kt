package com.ivan200.easyscanner

import android.app.Activity
import android.content.Context.WINDOW_SERVICE
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.*

/**
 * @author Захаров Иван
 * @since 05.07.2021
 */
fun <T : Activity> T.lockOrientation(view: View) {
    val rotation = view.displayCompat.rotation
    val orientation = when (resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> when (rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        Configuration.ORIENTATION_PORTRAIT -> when (rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    requestedOrientation = orientation
}

fun <T : Activity> T.unlockOrientation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

fun <T : Activity> T.hideSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        window.decorView.postDelayed({
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }, 500L)
    }
}

val View.displayCompat: Display
    get() {
        display?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.let { return it }
        }
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(WindowManager::class.java)
        } else {
            (context.getSystemService(WINDOW_SERVICE) as WindowManager)
        }.defaultDisplay
    }