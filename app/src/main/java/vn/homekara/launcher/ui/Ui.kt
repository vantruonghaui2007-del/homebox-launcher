package vn.homekara.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import vn.homekara.launcher.R
import vn.homekara.launcher.core.LauncherConfig

/** Ba nền tối tĩnh, cỡ chữ, lề an toàn. */
object UiStyle {
    class Palette(val name: String, val top: Int, val bottom: Int, val accent: Int)

    val PALETTES = listOf(
        Palette("Xanh đêm", 0xFF0B1424.toInt(), 0xFF16294A.toInt(), 0xFF64B5F6.toInt()),
        Palette("Than chì", 0xFF101010.toInt(), 0xFF262626.toInt(), 0xFFFFB74D.toInt()),
        Palette("Tím than", 0xFF140B22.toInt(), 0xFF2B1A47.toInt(), 0xFFCE93D8.toInt()),
    )

    const val LARGE_SCALE = 1.2f

    fun palette(cfg: LauncherConfig) = PALETTES[cfg.theme.coerceIn(0, PALETTES.size - 1)]

    /** Áp màu nền, cỡ chữ và lề an toàn cho cả màn hình. */
    fun apply(activity: Activity, root: View, cfg: LauncherConfig) {
        val p = palette(cfg)
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(p.top, p.bottom))
        activity.window.setBackgroundDrawable(bg)
        applySafeMargin(root, cfg.safeMargin)
        applyTextScale(root, cfg.largeText)
    }

    fun applySafeMargin(root: View, percent: Int) {
        val dm = root.resources.displayMetrics
        val minGap = (12 * dm.density).toInt()
        val h = maxOf(minGap, dm.widthPixels * percent / 100)
        val v = maxOf(minGap, dm.heightPixels * percent / 100)
        root.setPadding(h, v, h, v)
    }

    /** Phóng to/thu nhỏ mọi TextView theo cỡ gốc trong layout (không cộng dồn khi gọi lại). */
    fun applyTextScale(view: View, large: Boolean) {
        if (view is TextView) {
            val base = (view.getTag(R.id.tag_base_text) as? Float) ?: view.textSize.also {
                view.setTag(R.id.tag_base_text, it)
            }
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (large) base * LARGE_SCALE else base)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyTextScale(view.getChildAt(i), large)
        }
    }

    fun toast(activity: Activity, text: String?) {
        if (text != null) Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }
}

/** Hộp thoại nút lớn, dùng tốt bằng remote. Back để đóng. */
object ActionDialog {
    class Item(val label: String, val iconRes: Int = 0, val action: () -> Unit)

    fun show(
        activity: Activity,
        title: String,
        message: String? = null,
        items: List<Item>,
        focusIndex: Int = 0,
    ): Dialog? {
        if (activity.isFinishing) return null
        val dialog = Dialog(activity, R.style.HomeDialog)
        val root = LayoutInflater.from(activity).inflate(R.layout.dialog_actions, null)
        (root.findViewById<View>(R.id.title) as TextView).text = title
        val msg = root.findViewById<View>(R.id.message) as TextView
        if (message.isNullOrEmpty()) msg.visibility = View.GONE else msg.text = message

        val container = root.findViewById<View>(R.id.buttons) as LinearLayout
        val dp = activity.resources.displayMetrics.density
        val buttons = ArrayList<View>()
        for ((i, item) in items.withIndex()) {
            val b = activity.layoutInflater.inflate(R.layout.item_dialog_button, container, false) as TextView
            b.text = item.label
            if (item.iconRes != 0) b.setCompoundDrawablesWithIntrinsicBounds(item.iconRes, 0, 0, 0)
            b.setOnClickListener {
                dialog.dismiss()
                item.action()
            }
            val lp = b.layoutParams as LinearLayout.LayoutParams
            if (i > 0) lp.topMargin = (10 * dp).toInt()
            container.addView(b, lp)
            buttons.add(b)
        }
        val cfg = vn.homekara.launcher.data.ConfigRepository.get(activity)
        UiStyle.applyTextScale(root, cfg.largeText)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)

        val dm = activity.resources.displayMetrics
        val width = minOf((640 * dp).toInt(), (dm.widthPixels * 0.92f).toInt())
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
        buttons.getOrNull(focusIndex.coerceIn(0, maxOf(0, buttons.size - 1)))?.requestFocus()
        return dialog
    }
}
