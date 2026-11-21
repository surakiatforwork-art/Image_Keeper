package com.branchphotovault.retouch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Beta Cloud Beard Removal:
 * - only a padded portrait crop is sent to AILabTools on explicit consent;
 * - response is checked against face geometry;
 * - ONLY the lower facial-hair region is blended into a copy of the source;
 * - lips, nostrils, chin silhouette, neck, glasses and background stay source.
 * Quality must be reviewed against Meitu; this is an opt-in beta, not a
 * validated Meitu-equivalent model.
 */
internal class CloudBeardRetoucher(private val context: Context) {
    data class Prepared(val crop: Rect, val upload: Bitmap)

    suspend fun process(
        source: Bitmap,
        faceIndex: Int,
        region: RetouchRegion,
        key: String,
        progress: (String) -> Unit
    ): Bitmap {
        val landmarks = withContext(Dispatchers.Default) {
            MoustacheRetoucher(context).faces(source).getOrNull(faceIndex)
        } ?: error("ไม่พบใบหน้าที่เลือก")
        val prepared = prepareCrop(source, landmarks)
        val remote: Bitmap
        try {
            remote = CloudBeardClient().remove(prepared.upload, key, progress)
        } finally {
            prepared.upload.recycle()
        }
        return try {
            withContext(Dispatchers.Default) {
                progress("กำลังตรวจรูปหน้าและป้องกันขอบคาง…")
                val normalized = normalizeAndVerify(source,remote,prepared.crop,landmarks)
                try { composite(source,normalized,prepared.crop,landmarks,region) }
                finally { normalized.recycle() }
            }
        } finally { remote.recycle() }
    }

    private fun prepareCrop(source: Bitmap, face: List<NormalizedLandmark>): Prepared {
        val w = source.width
        val h = source.height
        fun px(i: Int) = face[i].x()*w
        fun py(i: Int) = face[i].y()*h
        val fw = max(72f,abs(px(454)-px(234)))
        val fh = max(96f,abs(py(152)-py(10)))
        val cx = (px(234)+px(454))/2f
        val top = min(py(10),py(151))
        val left = max(0,(cx-fw*1.0f).roundToInt())
        val right = min(w,(cx+fw*1.0f).roundToInt())
        val y0 = max(0,(top-fh*.39f).roundToInt())
        val y1 = min(h,(py(152)+fh*.56f).roundToInt())
        val rect = Rect(left,y0,right,y1)
        require(rect.width()>=140 && rect.height()>=200) { "ภาพครอปใบหน้าเล็กเกินไป" }
        val crop = Bitmap.createBitmap(source,rect.left,rect.top,rect.width(),rect.height())
        val maxSide=1500f
        val scale=min(1f,maxSide/max(rect.width(),rect.height()))
        val upload = if(scale<1f) {
            val downsized = Bitmap.createScaledBitmap(crop,
                (crop.width*scale).roundToInt().coerceAtLeast(1),
                (crop.height*scale).roundToInt().coerceAtLeast(1),true)
            crop.recycle()
            downsized
        } else crop
        return Prepared(rect,upload)
    }

    private fun normalizeAndVerify(
        source: Bitmap,
        remote:Bitmap,
        crop:Rect,
        face:List<NormalizedLandmark>
    ): Bitmap {
        val wantedRatio=crop.width().toFloat()/crop.height()
        val returnedRatio=remote.width.toFloat()/remote.height
        require(abs(returnedRatio/wantedRatio-1f)<.035f) {
            "Cloud เปลี่ยนสัดส่วนภาพมากเกินไป จึงไม่สามารถเกลี่ยกลับได้อย่างปลอดภัย"
        }
        val corrected = Bitmap.createScaledBitmap(remote,crop.width(),crop.height(),true)
        try {
            val reported = MoustacheRetoucher(context).faces(corrected).firstOrNull()
                ?: throw IllegalStateException("Cloud เปลี่ยนภาพจนตรวจใบหน้าไม่ได้")
            val faceWidth = max(50f,abs((face[454].x()-face[234].x())*source.width))
            fun dx(i:Int)= abs((reported[i].x()*crop.width()+crop.left) - face[i].x()*source.width)
            fun dy(i:Int)= abs((reported[i].y()*crop.height()+crop.top) - face[i].y()*source.height)
            // Guard identity geometry. The cloud may remove hair but may not
            // move eyes, nose or lip center or recompose the photograph.
            val anchors=intArrayOf(33,263,1,61,291)
            val displacement=anchors.map { hypot(dx(it),dy(it)) }.sorted()
            val median=displacement[displacement.size/2]
            val worst=displacement.last()
            require(median<faceWidth*.045f && worst<faceWidth*.11f) {
                "Cloud เปลี่ยนตำแหน่งใบหน้าหรือปากมากเกินไป ระบบไม่ยอมใช้ภาพนี้"
            }
            return corrected
        } catch (e:Exception) {
            if(corrected !== remote) corrected.recycle()
            throw e
        }
    }

    private fun composite(
        source:Bitmap,
        corrected:Bitmap,
        rect:Rect,
        face:List<NormalizedLandmark>,
        region:RetouchRegion
    ):Bitmap {
        val w=source.width
        val h=source.height
        fun p(i:Int)=android.graphics.PointF(face[i].x()*w,face[i].y()*h)
        val jawIndices=intArrayOf(
            234,93,132,58,172,136,150,149,176,148,152,
            377,400,378,379,365,397,288,361,323,454
        )
        val jaw=jawIndices.map {p(it)}
        val left=p(234);val right=p(454)
        val mouthL=p(61);val mouthR=p(291)
        val mouthWidth=hypot(mouthR.x-mouthL.x,mouthR.y-mouthL.y).coerceAtLeast(14f)
        val mouthCenterY=(p(13).y+p(14).y)*.5f
        val topY=min(p(2).y,mouthCenterY-mouthWidth*.42f)
        val path=Path().apply {
            moveTo(jaw.first().x,jaw.first().y)
            for(i in 1 until jaw.size) lineTo(jaw[i].x,jaw[i].y)
            lineTo(right.x,topY)
            lineTo(left.x,topY)
            close()
        }
        val mask=Bitmap.createBitmap(rect.width(),rect.height(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(mask)
        canvas.drawColor(Color.BLACK)
        canvas.translate(-rect.left.toFloat(),-rect.top.toFloat())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;style=Paint.Style.FILL}
        canvas.drawPath(path,paint)
        val exclusion=Paint(Paint.ANTI_ALIAS_FLAG).apply{
            color=Color.BLACK
            style=Paint.Style.FILL
        }
        // Original lips and nostrils must be restored exactly with a safety
        // margin; do not let cloud-generated replacements modify anatomy.
        val lipPath=Path().apply {
            val lip=intArrayOf(61,40,37,0,267,270,291,321,314,17,84,91)
            moveTo(p(lip[0]).x,p(lip[0]).y)
            lip.drop(1).forEach { lineTo(p(it).x,p(it).y) }
            close()
        }
        canvas.drawPath(lipPath,exclusion)
        exclusion.style=Paint.Style.STROKE
        exclusion.strokeWidth=mouthWidth*.08f
        canvas.drawPath(lipPath,exclusion)
        exclusion.style=Paint.Style.FILL
        val noseL=p(98);val noseR=p(327)
        val radius=mouthWidth*.15f
        canvas.drawCircle(noseL.x,noseL.y,radius,exclusion)
        canvas.drawCircle(noseR.x,noseR.y,radius,exclusion)
        val maskPixels=IntArray(rect.width()*rect.height())
        mask.getPixels(maskPixels,0,rect.width(),0,0,rect.width(),rect.height())
        mask.recycle()

        val output=source.copy(Bitmap.Config.ARGB_8888,true)
        val changed=IntArray(w*h)
        source.getPixels(changed,0,w,0,0,w,h)
        val remotePixels=IntArray(rect.width()*rect.height())
        corrected.getPixels(remotePixels,0,rect.width(),0,0,rect.width(),rect.height())
        val jawLineByX=FloatArray(rect.width()) {i->
            jawY((i+rect.left).toFloat(),jaw)
        }
        val margin=mouthWidth*.035f
        val feather=(mouthWidth*.080f).coerceIn(4f,26f)
        var modified=0
        for(y in rect.top until rect.bottom) {
            for(x in rect.left until rect.right) {
                val idx=y*w+x
                val cropIdx=(y-rect.top)*rect.width()+(x-rect.left)
                if(Color.red(maskPixels[cropIdx])<250) continue
                // Face regions use the original landmark positions rather
                // than the Cloud-modified output for protection boundaries.
                val isMoustache=y<mouthCenterY+mouthWidth*.15f
                if (region==RetouchRegion.MOUSTACHE && !isMoustache) continue
                if (region==RetouchRegion.BEARD && isMoustache) continue
                val jawLine=jawLineByX[x-rect.left]
                val fromJaw=jawLine-y
                // Keep original silhouette and natural cast shadows, blending
                // gradually only inside the lower face, never below jawline.
                val jawAlpha=((fromJaw-margin)/feather).coerceIn(0f,1f)
                val upperAlpha=((y-topY)/feather).coerceIn(0f,1f)
                val alpha=min(jawAlpha,upperAlpha)
                if(alpha<=0f) continue
                val old=changed[idx]
                val new=remotePixels[cropIdx]
                fun lerp(c0:Int,c1:Int):Int=(c0+(c1-c0)*alpha).roundToInt().coerceIn(0,255)
                changed[idx]=Color.argb(Color.alpha(old),
                    lerp(Color.red(old),Color.red(new)),
                    lerp(Color.green(old),Color.green(new)),
                    lerp(Color.blue(old),Color.blue(new)))
                if(changed[idx]!=old) modified++
            }
        }
        require(modified>64) { "Cloud ไม่ได้แก้ไขบริเวณหนวดเคราที่ปลอดภัย" }
        output.setPixels(changed,0,w,0,0,w,h)
        Log.i("KeeperCloudBeard","Cloud result composited within lower face; image size=${w}x$h")
        return output
    }

    private fun jawY(x:Float,jaw:List<android.graphics.PointF>):Float {
        if(x<jaw.first().x || x>jaw.last().x) return -100000f
        for(i in 0 until jaw.lastIndex) {
            val a=jaw[i];val b=jaw[i+1]
            if(x>=min(a.x,b.x) && x<=max(a.x,b.x) && abs(b.x-a.x)>.5f) {
                val t=(x-a.x)/(b.x-a.x)
                return a.y+(b.y-a.y)*t
            }
        }
        return -100000f
    }
}
