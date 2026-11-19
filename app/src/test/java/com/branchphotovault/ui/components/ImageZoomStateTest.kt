package com.branchphotovault.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class ImageZoomStateTest {
    private fun state() = ImageZoomState().apply { viewport=IntSize(400,400); imageSize=IntSize(400,400) }
    @Test fun resetRestoresFit() {
        val state=state()
        state.transform(2f,Offset(20f,30f),Offset(200f,200f))
        assertTrue(state.isZoomed)
        state.reset()
        assertEquals(1f,state.scale,0f)
        assertEquals(Offset.Zero,state.offset)
    }
    @Test fun clampsZoomAndPan() {
        val state=state()
        state.transform(100f,Offset(99999f,-99999f),Offset(200f,200f))
        assertEquals(6f,state.scale,0f)
        assertEquals(Offset(1000f,-1000f),state.offset)
        state.transform(.001f,Offset.Zero,Offset(200f,200f))
        assertEquals(Offset.Zero,state.offset)
    }
    @Test fun fitLetterboxingDoesNotPermitBlankHorizontalPanning() {
        val state=state().apply { imageSize=IntSize(100,400) }
        state.transform(2f,Offset(100f,150f),Offset(200f,200f))
        assertEquals(Offset(0f,150f),state.offset)
    }
    @Test fun zoomKeepsTouchedImagePointUnderFingers() {
        val state=state()
        state.transform(2f,Offset.Zero,Offset(300f,200f))
        assertEquals(Offset(-100f,0f),state.offset)
    }
    @Test fun invalidGestureCannotCorruptState() {
        val state=state()
        state.transform(Float.NaN,Offset.Zero,Offset.Zero)
        state.transform(2f,Offset.Zero,Offset(Float.NaN,0f))
        assertEquals(1f,state.scale,0f)
    }
}
