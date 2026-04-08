import android.graphics.*
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException


object BitmapUtil {
    fun bitmap2Base64(bitmap: Bitmap): String {
        var result: String = ""
        var baos: ByteArrayOutputStream? = null
        try {
            baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            baos.flush()
            baos.close()
            val bitMapByte = baos.toByteArray()
            result = Base64.encodeToString(bitMapByte, Base64.NO_WRAP)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                if (baos != null) {
                    baos.flush()
                    baos.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return result
    }

    /**
     * 将本地图片转换为Base64
     * @param filePath 图片文件路径
     * @return Base64编码的字符串，如果转换失败返回空字符串
     */
    fun transformImageToBase64(filePath: String): String {
        if (filePath.isEmpty()) {
            Log.w("BitmapUtil", "文件路径为空")
            return ""
        }

        val file = File(filePath)
        if (!file.exists()) {
            Log.w("BitmapUtil", "文件不存在: $filePath")
            return ""
        }

        if (!file.isFile) {
            Log.w("BitmapUtil", "路径不是文件: $filePath")
            return ""
        }

        return try {
            // 解码图片文件为Bitmap
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (bitmap == null) {
                Log.w("BitmapUtil", "无法解码图片文件: $filePath")
                return ""
            }

            // 使用已有的bitmap2Base64方法转换为Base64
            val base64String = bitmap2Base64(bitmap)

            // 回收Bitmap资源
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            Log.d("BitmapUtil", "图片转换Base64成功: $filePath")
            base64String
        } catch (e: Exception) {
            Log.d("BitmapUtil", "转换图片为Base64失败: ${e.message}")
            e.printStackTrace()
            ""
        }
    }


}


fun Bitmap.toHorizontalMirror(): Bitmap {
    val w = width
    val h = height
    val matrix = Matrix()
    matrix.postScale(-1f, 1f) // 水平镜像翻转
    return Bitmap.createBitmap(this, 0, 0, w, h, matrix, true)
}


fun Bitmap.drawRect(block: (canvas: Canvas, paint: Paint) -> Unit): Bitmap {
    var result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(result)
        canvas.drawBitmap(this, 0f, 0f, null)
        val paint = Paint()
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        paint.color = Color.RED

        block(canvas, paint)

        canvas.save()
        canvas.restore()
    } catch (e: Exception) {
//        LogUtils.w(e.message)
        Log.d("bitmapUtils", e.message ?: "")
    }
    return result
}

/**
 * 水平翻转bitmap
 *
 */
fun Bitmap.convertToHorizontalMirror(): Bitmap {
    val matrix = Matrix();
    matrix.preScale(-1f, 1f); // 水平翻转
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, false)
}
