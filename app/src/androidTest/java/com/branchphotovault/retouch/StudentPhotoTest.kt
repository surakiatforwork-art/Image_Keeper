package com.branchphotovault.retouch

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

class StudentPhotoTest {
    @Test fun fullPhotoWithRealDetection() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val ctx=inst.targetContext
        assumeTrue(ctx.assets.list("retouch")?.contains(ChinStudent.asset)==true)
        assumeTrue(inst.context.assets.list("")?.contains("portrait_fixture.jpg")==true)
        val src=inst.context.assets.open("portrait_fixture.jpg").use { BitmapFactory.decodeStream(it) }
        val face=MoustacheRetoucher(ctx).faces(src).first()
        val guard=LipSafeRasterGuard.fromFace(face,src.width,src.height)
        val out=ChinStudent.apply(ctx,src,src,face)
        try {
            var changed=0
            for(y in 0 until src.height) for(x in 0 until src.width) {
                if(src.getPixel(x,y)!=out.getPixel(x,y)) {
                    check(!guard.at(x,y))
                    changed++
                }
            }
            check(changed>100)
        } finally { out.recycle(); src.recycle() }
    }
}
