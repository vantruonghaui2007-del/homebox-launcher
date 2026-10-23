@file:Suppress("DEPRECATION")

package vn.homekara.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import vn.homekara.launcher.BuildConfig
import vn.homekara.launcher.R
import vn.homekara.launcher.apps.AppCatalog
import vn.homekara.launcher.apps.HomeApps
import vn.homekara.launcher.apps.SystemSettings
import vn.homekara.launcher.core.AppTarget
import vn.homekara.launcher.core.LauncherConfig
import vn.homekara.launcher.data.ConfigRepository

class SettingsActivity : Activity() {

    private lateinit var root: View
    private lateinit var list: LinearLayout
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        root = findViewById(R.id.root)
        list = findViewById(R.id.list)
        findViewById<TextView>(R.id.title).text = "Cài đặt"
        findViewById<TextView>(R.id.subtitle).apply {
            text = "HomeBox Lite ${BuildConfig.VERSION_NAME} · Back để về Home"
            visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        render()
    }

    override fun onStop() {
        super.onStop()
        dialog?.dismiss()
        dialog = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val t = AppTarget.decode(data?.getStringExtra(AllAppsActivity.EXTRA_TARGET)) ?: return
        when (requestCode) {
            REQ_KARAOKE -> ConfigRepository.update(this) { it.copy(karaoke = t) }
            REQ_YOUTUBE -> ConfigRepository.update(this) { it.copy(youtube = t) }
        }
        render()
    }

    // ---------------------------------------------------------------- dựng danh sách

    private fun render() {
        val focusedTag = currentFocus?.tag as? String
        val cfg = ConfigRepository.get(this)
        list.removeAllViews()

        section("Giao diện")
        row("theme", "Màu nền", UiStyle.palette(cfg).name) {
            update { it.copy(theme = (it.theme + 1) % LauncherConfig.THEME_COUNT) }
        }
        row("text", "Cỡ chữ", if (cfg.largeText) "Lớn" else "Thường") {
            update { it.copy(largeText = !it.largeText) }
        }
        stepper("margin", "Lề an toàn (chống mất viền trên TV)", "${cfg.safeMargin}%",
            onMinus = { update { it.copy(safeMargin = (it.safeMargin - 1).coerceAtLeast(LauncherConfig.MIN_MARGIN)) } },
            onPlus = { update { it.copy(safeMargin = (it.safeMargin + 1).coerceAtMost(LauncherConfig.MAX_MARGIN)) } },
        )

        section("Hai ô chính")
        row("karaoke", "Ô Karaoke", cfg.karaoke?.let { labelOf(it) } ?: "HomeKara (mặc định)") { karaokeMenu(cfg) }
        row("youtube", "Ô YouTube", cfg.youtube?.let { labelOf(it) } ?: "Tự nhận diện") { youtubeMenu(cfg) }

        section("Cài đặt hệ thống")
        row("sys_wifi", "Wi-Fi", null) { SystemSettings.open(this, Settings.ACTION_WIFI_SETTINGS) }
        row("sys_lan", "Mạng dây (LAN) và mạng khác", null) {
            SystemSettings.open(this, SystemSettings.ACTION_ETHERNET, Settings.ACTION_WIRELESS_SETTINGS)
        }
        row("sys_date", "Ngày và giờ", null) { SystemSettings.open(this, Settings.ACTION_DATE_SETTINGS) }
        row("sys_sound", "Âm thanh", null) { SystemSettings.open(this, Settings.ACTION_SOUND_SETTINGS) }
        row("sys_display", "Màn hình", null) { SystemSettings.open(this, Settings.ACTION_DISPLAY_SETTINGS) }
        row("sys_apps", "Ứng dụng đã cài", null) {
            SystemSettings.open(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_APPLICATION_SETTINGS)
        }
        row("sys_all", "Toàn bộ cài đặt của box", null) { SystemSettings.open(this, Settings.ACTION_SETTINGS) }

        section("Màn hình chính")
        row("home", "Đặt HomeBox làm màn hình chính", homeStatus()) { homeMenu() }
        row("revert", "Quay về launcher gốc của VNPT", "Hướng dẫn") { revertMenu() }

        section("Box")
        row("info", "Thông tin box", "Model, RAM, bộ nhớ, phím remote") {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        UiStyle.apply(this, root, cfg)
        val target = focusedTag?.let { list.findViewWithTag<View>(it) }
        (target ?: firstFocusable())?.requestFocus()
    }

    private fun update(change: (LauncherConfig) -> LauncherConfig) {
        ConfigRepository.update(this, change)
        render()
    }

    private fun labelOf(t: AppTarget): String = AppCatalog.label(this, t) ?: "${t.packageName} (đã gỡ)"

    private fun firstFocusable(): View? {
        for (i in 0 until list.childCount) {
            val c = list.getChildAt(i)
            if (c.isFocusable) return c
        }
        return null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun section(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(resources.getColor(R.color.text_secondary))
        tv.textSize = 17f
        tv.setPadding(dp(4), dp(if (list.childCount == 0) 0 else 22), 0, dp(8))
        list.addView(tv)
    }

    private fun row(tag: String, title: String, value: String?, onClick: () -> Unit) {
        val row = LinearLayout(this)
        row.tag = tag
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.bg_button)
        row.isFocusable = true
        row.isClickable = true
        row.minimumHeight = dp(62)
        row.setPadding(dp(20), dp(8), dp(20), dp(8))
        row.setOnClickListener { onClick() }

        val t = TextView(this)
        t.text = title
        t.textSize = 20f
        t.setTextColor(resources.getColor(R.color.text_primary))
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (value != null) {
            val v = TextView(this)
            v.text = value
            v.textSize = 20f
            v.gravity = Gravity.RIGHT
            v.maxLines = 2
            v.setTextColor(resources.getColor(R.color.text_secondary))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = dp(16)
            row.addView(v, lp)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        list.addView(row, lp)
    }

    /** Hàng có nút − và +; trái/phải trên remote chuyển giữa hai nút. */
    private fun stepper(tag: String, title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = dp(62)
        row.setPadding(dp(20), 0, 0, 0)

        val t = TextView(this)
        t.text = title
        t.textSize = 20f
        t.setTextColor(resources.getColor(R.color.text_primary))
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        fun button(label: String, btnTag: String, action: () -> Unit): TextView {
            val b = layoutInflater.inflate(R.layout.item_dialog_button, row, false) as TextView
            b.text = label
            b.tag = btnTag
            b.textSize = 26f
            b.gravity = Gravity.CENTER
            b.setOnClickListener { action() }
            return b
        }
        val size = dp(72)
        row.addView(button("−", "${tag}_minus", onMinus), LinearLayout.LayoutParams(size, dp(60)))
        val v = TextView(this)
        v.text = value
        v.textSize = 22f
        v.gravity = Gravity.CENTER
        v.setTextColor(resources.getColor(R.color.text_primary))
        row.addView(v, LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(button("+", "${tag}_plus", onPlus), LinearLayout.LayoutParams(size, dp(60)))

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        list.addView(row, lp)
    }

    // ---------------------------------------------------------------- hộp thoại

    private fun show(title: String, message: String?, items: List<ActionDialog.Item>) {
        dialog?.dismiss()
        dialog = ActionDialog.show(this, title, message, items)
    }

    private fun karaokeMenu(cfg: LauncherConfig) {
        val items = arrayListOf(
            ActionDialog.Item("Chọn ứng dụng cho ô Karaoke", R.drawable.ic_apps) {
                startActivityForResult(AllAppsActivity.pickIntent(this, "Chọn ứng dụng cho ô Karaoke"), REQ_KARAOKE)
            },
        )
        if (cfg.karaoke != null) {
            items += ActionDialog.Item("Dùng HomeKara mặc định", R.drawable.ic_refresh) { update { it.copy(karaoke = null) } }
        }
        val installed = AppCatalog.isInstalled(this, AppCatalog.KARAOKE_PACKAGE)
        show(
            "Ô Karaoke",
            "Mặc định mở HomeKara (${AppCatalog.KARAOKE_PACKAGE}) — " + if (installed) "đã cài trên máy này." else "chưa cài trên máy này.",
            items,
        )
    }

    private fun youtubeMenu(cfg: LauncherConfig) {
        val items = arrayListOf(
            ActionDialog.Item("Chọn ứng dụng cho ô YouTube", R.drawable.ic_apps) {
                startActivityForResult(AllAppsActivity.pickIntent(this, "Chọn ứng dụng cho ô YouTube"), REQ_YOUTUBE)
            },
        )
        if (cfg.youtube != null) {
            items += ActionDialog.Item("Tự nhận diện lại", R.drawable.ic_refresh) { update { it.copy(youtube = null) } }
        }
        show(
            "Ô YouTube",
            "Khi chưa chọn, launcher tự tìm SmartTube, YouTube TV, YouTube hoặc NewPipe nếu có trên máy. " +
                "Launcher chỉ mở ứng dụng; việc xem được YouTube phụ thuộc ứng dụng đó.",
            items,
        )
    }

    private fun homeStatus(): String {
        val def = HomeApps.currentDefault(this)
        return when {
            def == null -> "Chưa chọn"
            def.packageName == packageName -> "Đang dùng"
            else -> "Đang dùng launcher khác"
        }
    }

    private fun homeMenu() {
        val def = HomeApps.currentDefault(this)
        when {
            def?.packageName == packageName -> show(
                "HomeBox đang là màn hình chính",
                "Bấm Home trên remote sẽ về HomeBox. Muốn quay lại launcher của VNPT, xem hướng dẫn bên dưới.",
                listOf(ActionDialog.Item("Hướng dẫn quay về launcher gốc", R.drawable.ic_info) { revertMenu() }),
            )
            def == null -> show(
                "Đặt HomeBox làm màn hình chính",
                "1. Bấm nút bên dưới (hoặc bấm Home trên remote).\n" +
                    "2. Chọn HomeBox Lite, rồi chọn \"Chỉ một lần\" để thử.\n" +
                    "3. Dùng thử thấy ổn thì làm lại và chọn \"Luôn luôn\".",
                listOf(ActionDialog.Item("Hiện bảng chọn màn hình chính", R.drawable.ic_tv) { HomeApps.showChooser(this) }),
            )
            else -> {
                val name = AppCatalog.label(this, def) ?: def.packageName
                show(
                    "Box đang dùng $name",
                    "Vì $name đang được chọn \"Luôn luôn\", hệ thống sẽ không hỏi lại.\n" +
                        "1. Mở thông tin $name và bấm \"Xoá mặc định\".\n" +
                        "2. Bấm Home trên remote, chọn HomeBox Lite → \"Chỉ một lần\" để thử.\n" +
                        "3. Thấy ổn thì làm lại và chọn \"Luôn luôn\".",
                    listOf(
                        ActionDialog.Item("Mở thông tin $name", R.drawable.ic_info) { SystemSettings.appDetails(this, def.packageName) },
                        ActionDialog.Item("Hiện bảng chọn màn hình chính", R.drawable.ic_tv) { HomeApps.showChooser(this) },
                    ),
                )
            }
        }
    }

    private fun revertMenu() {
        val items = ArrayList<ActionDialog.Item>()
        items += ActionDialog.Item("Mở thông tin HomeBox Lite (Xoá mặc định)", R.drawable.ic_info) {
            SystemSettings.appDetails(this, packageName)
        }
        for ((t, label) in HomeApps.all(this)) {
            if (t.packageName == packageName) continue
            items += ActionDialog.Item("Mở $label ngay", R.drawable.ic_tv) { HomeApps.openHome(this, t) }
        }
        items += ActionDialog.Item("Gỡ HomeBox Lite", R.drawable.ic_remove) { SystemSettings.uninstall(this, packageName) }
        show(
            "Quay về launcher gốc",
            "Cách 1: Mở thông tin HomeBox Lite, bấm \"Xoá mặc định\". Sau đó bấm Home trên remote, chọn launcher của VNPT → \"Luôn luôn\".\n" +
                "Cách 2: Gỡ HomeBox Lite — box tự dùng lại launcher gốc.\n" +
                "Nếu trước đó chỉ chọn \"Chỉ một lần\", chỉ cần bấm Home và chọn launcher gốc.",
            items,
        )
    }

    companion object {
        private const val REQ_KARAOKE = 201
        private const val REQ_YOUTUBE = 202
    }
}
