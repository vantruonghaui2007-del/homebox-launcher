package vn.homekara.launcher.ui

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import vn.homekara.launcher.R
import vn.homekara.launcher.apps.BoxInfo
import vn.homekara.launcher.data.Bg
import vn.homekara.launcher.data.ConfigRepository

/** Thông tin box (model, API, ABI, RAM, bộ nhớ…) và thử phím remote. */
class InfoActivity : Activity() {

    private lateinit var root: View
    private lateinit var list: LinearLayout
    private lateinit var keyView: TextView
    private var gen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        root = findViewById(R.id.root)
        list = findViewById(R.id.list)
        findViewById<TextView>(R.id.title).text = "Thông tin box"
        keyView = findViewById(R.id.subtitle)
        keyView.visibility = View.VISIBLE
        keyView.text = "Bấm một phím bất kỳ trên remote để xem mã phím · Back để quay lại"
    }

    override fun onStart() {
        super.onStart()
        UiStyle.apply(this, root, ConfigRepository.get(this))
        val g = ++gen
        Bg.run {
            val rows = BoxInfo.collect(this)
            Bg.main.post { if (g == gen && !isFinishing) render(rows) }
        }
    }

    override fun onStop() {
        super.onStop()
        gen++
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        keyView.text = "Phím vừa bấm: ${KeyEvent.keyCodeToString(keyCode)} · mã $keyCode · scan ${event.scanCode}"
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION")
    private fun render(rows: List<Pair<String, String>>) {
        val focused = list.indexOfChild(currentFocus).coerceAtLeast(0)
        list.removeAllViews()
        val dp = resources.displayMetrics.density
        for ((title, value) in rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setBackgroundResource(R.drawable.bg_button)
            row.isFocusable = true // để cuộn bằng remote
            row.setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())

            val t = TextView(this)
            t.text = title
            t.textSize = 17f
            t.setTextColor(resources.getColor(R.color.text_secondary))
            row.addView(t)

            val v = TextView(this)
            v.text = value
            v.textSize = 20f
            v.setTextColor(resources.getColor(R.color.text_primary))
            row.addView(v)

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (8 * dp).toInt()
            list.addView(row, lp)
        }
        UiStyle.applyTextScale(list, ConfigRepository.get(this).largeText)
        list.getChildAt(minOf(focused, list.childCount - 1))?.requestFocus()
    }
}
