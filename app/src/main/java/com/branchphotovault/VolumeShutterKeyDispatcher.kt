package com.branchphotovault

import android.view.KeyEvent

/**
 * Consumes volume keys only while a camera capture callback is registered.
 * An in-flight key release is consumed even if capture navigates away immediately.
 */
internal class VolumeShutterKeyDispatcher {
    private val pressedKeys = mutableSetOf<Int>()

    fun handle(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        capture: (() -> Unit)?
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) return false

        return when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (capture == null && keyCode !in pressedKeys) return false
                if (keyCode !in pressedKeys) {
                    pressedKeys.add(keyCode)
                    if (repeatCount == 0) capture?.invoke()
                }
                true
            }
            KeyEvent.ACTION_UP -> pressedKeys.remove(keyCode)
            else -> keyCode in pressedKeys
        }
    }
}
