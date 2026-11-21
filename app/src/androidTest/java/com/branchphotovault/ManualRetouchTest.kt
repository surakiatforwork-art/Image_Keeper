package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.Color
import com.branchphotovault.retouch.*
import org.junit.Assert.*
import org.junit.Test

class ManualRetouchTest {
    @Test fun preparedLayerMatchesOneShotComposeAtDifferentStrengths() {
        val source=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val ai=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val strokes=listOf(RetouchStroke(listOf(RetouchPoint(.5f,.5f)),.10f,true))
        val bounds=RetouchBounds(.15f,.15f,.85f,.85f)
        val layer=ManualRetouch.prepareLayer(ai,strokes,bounds)
        try {
            for(level in listOf(0f,.18f,.35f,.5f,.83f,1f)) {
                val oneShot=ManualRetouch.compose(source,ai,strokes,level,bounds)
                val fast=ManualRetouch.composeLayer(source,layer,level)
                try {
                    val first=IntArray(120*120)
                    val second=IntArray(120*120)
                    oneShot.getPixels(first,0,120,0,0,120,120)
                    fast.getPixels(second,0,120,0,0,120,120)
                    assertArrayEquals("strength=$level",first,second)
                } finally { oneShot.recycle(); fast.recycle() }
            }
        } finally { source.recycle(); ai.recycle(); layer.recycle() }
    }

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
