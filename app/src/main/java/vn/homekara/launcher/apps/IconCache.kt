package vn.homekara.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import vn.homekara.launcher.R
import vn.homekara.launcher.core.AppTarget
import vn.homekara.launcher.data.Bg

/** Cache biểu tượng tối đa 4 MiB, mỗi ảnh tối đa 128×128; nạp ngoài luồng giao diện. */
object IconCache {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_SIZE_PX = 128

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clear() = cache.evictAll()

    fun load(view: ImageView, target: AppTarget) {
        val key = target.encode()
        view.setTag(R.id.tag_icon_key, key)
        val hit = cache.get(key)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        val app = view.context.applicationContext
        val size = minOf(MAX_SIZE_PX, view.layoutParams?.width?.takeIf { it > 0 } ?: MAX_SIZE_PX)
        Bg.run {
            val bmp = render(app, target, size)
            Bg.main.post {
                if (bmp != null) cache.put(key, bmp)
                if (view.getTag(R.id.tag_icon_key) == key) view.setImageBitmap(bmp)
            }
        }
    }

    /** Bỏ ảnh khỏi ImageView để bitmap được thu hồi khi launcher bị ẩn. */
    fun release(view: ImageView) {
        view.setTag(R.id.tag_icon_key, null)
        view.setImageDrawable(null)
    }

    private fun render(ctx: Context, t: AppTarget, size: Int): Bitmap? {
        val pm = ctx.packageManager
        var d: Drawable? = null
        try {
            d = pm.getActivityIcon(ComponentName(t.packageName, t.activityName))
        } catch (e: Exception) {
            try {
                d = pm.getApplicationIcon(t.packageName)
            } catch (e2: Exception) {
                // không có biểu tượng
            }
        }
        if (d == null) return null
        return try {
            toBitmap(d, size)
        } catch (e: Throwable) {
            null
        }
    }

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        if (d is BitmapDrawable) {
            val src = d.bitmap
            if (src != null && src.width <= size && src.height <= size) return src
        }
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: size
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = minOf(size.toFloat() / iw, size.toFloat() / ih)
        val w = maxOf(1, (iw * scale).toInt())
        val h = maxOf(1, (ih * scale).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val oldBounds = d.copyBounds()
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        d.bounds = oldBounds
        return bmp
    }
}
