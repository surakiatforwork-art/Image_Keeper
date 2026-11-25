package com.branchphotovault

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeShutterKeyDispatcherTest {
    @Test
    fun upDownTriggerExactlyOnceAndConsumeRepeats() {
        val dispatcher = VolumeShutterKeyDispatcher()
        var shots = 0
        val onCapture = { shots += 1 }
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, onCapture))
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 1, onCapture))
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 2, onCapture))
        assertEquals(1, shots)
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, onCapture))
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, onCapture))
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, 0, onCapture))
        assertEquals(2, shots)
    }

    @Test
    fun outsideCameraKeepsNormalVolumeKeysAndOtherKeys() {
        val dispatcher = VolumeShutterKeyDispatcher()
        assertFalse(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, null))
        assertFalse(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, 0, null))
        assertFalse(dispatcher.handle(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0) {})
        assertFalse(dispatcher.handle(KeyEvent.KEYCODE_CAMERA, KeyEvent.ACTION_DOWN, 0) {})
    }

    @Test
    fun releaseAfterNavigationDoesNotChangeVolume() {
        val dispatcher = VolumeShutterKeyDispatcher()
        var shots = 0
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0) { shots++ })
        assertTrue(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, null))
        assertEquals(1, shots)
        assertFalse(dispatcher.handle(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, null))
    }
}
