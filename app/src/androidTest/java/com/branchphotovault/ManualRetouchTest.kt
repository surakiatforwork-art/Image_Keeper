package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.Color
import com.branchphotovault.retouch.*
import org.junit.Assert.*
import org.junit.Test

class ManualRetouchTest {
    @Test fun restorePaintStrengthAndFrameNeverChangeInputs() {
        val source=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val ai=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val stroke=RetouchStroke(listOf(RetouchPoint(.5f,.5f)),.15f,true)
        try {
            ManualRetouch.compose(source,ai,listOf(stroke),1f).let {
                assertEquals(Color.BLUE,it.getPixel(80,80))
                assertEquals(Color.RED,it.getPixel(10,10))
                it.recycle()
            }
            ManualRetouch.compose(source,ai,listOf(stroke,stroke.copy(restore=false)),1f).let {
                assertEquals(Color.RED,it.getPixel(80,80)); it.recycle()
            }
            ManualRetouch.compose(source,ai,emptyList(),1f,RetouchBounds(.25f,.25f,.75f,.75f)).let {
                assertEquals(Color.RED,it.getPixel(80,80)); assertEquals(Color.BLUE,it.getPixel(10,10)); it.recycle()
            }
            ManualRetouch.compose(source,ai,emptyList(),0f).let { assertEquals(Color.BLUE,it.getPixel(80,80)); it.recycle() }
            assertEquals(Color.BLUE,source.getPixel(80,80))
            assertEquals(Color.RED,ai.getPixel(80,80))
        } finally { source.recycle(); ai.recycle() }
    }
}
