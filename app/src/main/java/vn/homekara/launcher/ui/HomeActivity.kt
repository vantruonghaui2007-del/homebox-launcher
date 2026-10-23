@file:Suppress("DEPRECATION")

package vn.homekara.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import vn.homekara.launcher.R
import vn.homekara.launcher.apps.AppCatalog
import vn.homekara.launcher.apps.AppLauncher
import vn.homekara.launcher.apps.IconCache
import vn.homekara.launcher.apps.LaunchResult
import vn.homekara.launcher.apps.NetState
import vn.homekara.launcher.apps.NetworkStatus
import vn.homekara.launcher.apps.Resolved
import vn.homekara.launcher.apps.SystemSettings
import vn.homekara.launcher.apps.TargetState
import vn.homekara.launcher.core.AppTarget
import vn.homekara.launcher.core.Favorites
import vn.homekara.launcher.core.FocusKeys
import vn.homekara.launcher.core.LauncherConfig
import vn.homekara.launcher.core.ViText
import vn.homekara.launcher.core.YouTubeApps
import vn.homekara.launcher.data.Bg
import vn.homekara.launcher.data.ConfigRepository
import java.util.Calendar

class HomeActivity : Activity() {

    private lateinit var scroll: ScrollView
    private lateinit var root: View
    private lateinit var tileKaraoke: View
    private lateinit var tileYoutube: View
    private lateinit var karaokeSub: TextView
    private lateinit var youtubeSub: TextView
    private lateinit var favHeader: TextView
    private lateinit var favRow: View
    private lateinit var favViews: List<View>
    private lateinit var btnApps: View
    private lateinit var btnNetwork: View
    private lateinit var btnSettings: View
    private lateinit var btnEdit: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var greeting: TextView
    private lateinit var netText: TextView
    private lateinit var netIcon: ImageView

    private var editMode = false
    private var receiversOn = false
    private var refreshGen = 0
    private var focusRestored = false
    private var pendingFocus: String? = null
    private var dialog: Dialog? = null

    private var karaoke: Resolved? = null
    private var youtube: Resolved? = null
    private var favorites: List<Resolved> = emptyList()

    // ---------------------------------------------------------------- vòng đời

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        scroll = findViewById(R.id.scroll)
        root = findViewById(R.id.root)
        tileKaraoke = findViewById(R.id.tile_karaoke)
        tileYoutube = findViewById(R.id.tile_youtube)
        karaokeSub = findViewById(R.id.karaoke_sub)
        youtubeSub = findViewById(R.id.youtube_sub)
        favHeader = findViewById(R.id.fav_header)
        favRow = findViewById(R.id.fav_row)
        favViews = listOf<View>(findViewById(R.id.fav0), findViewById(R.id.fav1), findViewById(R.id.fav2), findViewById(R.id.fav3))
        btnApps = findViewById(R.id.btn_apps)
        btnNetwork = findViewById(R.id.btn_network)
        btnSettings = findViewById(R.id.btn_settings)
        btnEdit = findViewById(R.id.btn_edit)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        greeting = findViewById(R.id.greeting)
        netText = findViewById(R.id.net_text)
        netIcon = findViewById(R.id.net_icon)

        (favViews.last().layoutParams as? LinearLayout.LayoutParams)?.rightMargin = 0

        tileKaraoke.setOnClickListener { onKaraoke() }
        tileYoutube.setOnClickListener { onYoutube() }
        tileKaraoke.setOnLongClickListener { tileMenu(karaokeTile = true); true }
        tileYoutube.setOnLongClickListener { tileMenu(karaokeTile = false); true }
        for ((i, v) in favViews.withIndex()) {
            v.setOnClickListener { onFavorite(i) }
            v.setOnLongClickListener { favoriteMenu(i); true }
        }
        btnApps.setOnClickListener { startActivity(Intent(this, AllAppsActivity::class.java)) }
        btnNetwork.setOnClickListener { networkMenu() }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnEdit.setOnClickListener { setEditMode(!editMode) }

        renderFavorites()
    }

    override fun onStart() {
        super.onStart()
        val cfg = ConfigRepository.get(this)
        UiStyle.apply(this, root, cfg)
        favHeader.setTextColor(if (editMode) UiStyle.palette(cfg).accent else resources.getColor(R.color.text_secondary))
        registerReceivers()
        updateClock()
        updateNetwork()
        if (!focusRestored && currentFocus == null) tileKaraoke.requestFocus()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        val key = keyForView(currentFocus)
        if (key != null) ConfigRepository.update(this) { it.copy(lastFocus = key) }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceivers()
        dialog?.dismiss()
        dialog = null
        // Rời Home: không cập nhật giao diện nữa và nhả ảnh để dành RAM cho ứng dụng đang dùng.
        for (v in favViews) IconCache.release(v.findViewById(R.id.icon))
        IconCache.clear()
        refreshGen++
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Bấm Home khi đang ở Home: đóng hộp thoại, thoát chế độ sửa, cuộn lên đầu.
        dialog?.dismiss()
        if (editMode) setEditMode(false)
        scroll.smoothScrollTo(0, 0)
    }

    override fun onBackPressed() {
        // Ở Home thì giữ nguyên màn hình; chỉ thoát chế độ sửa.
        if (editMode) setEditMode(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.repeatCount == 0) onMenuKey()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val t = AppTarget.decode(data?.getStringExtra(AllAppsActivity.EXTRA_TARGET)) ?: return
        when (requestCode) {
            REQ_KARAOKE -> {
                ConfigRepository.update(this) { it.copy(karaoke = t) }
                pendingFocus = FocusKeys.KARAOKE
            }
            REQ_YOUTUBE -> {
                ConfigRepository.update(this) { it.copy(youtube = t) }
                pendingFocus = FocusKeys.YOUTUBE
            }
            in REQ_FAV_BASE until REQ_FAV_BASE + LauncherConfig.MAX_FAVORITES -> {
                val slot = requestCode - REQ_FAV_BASE
                val cfg = ConfigRepository.update(this) { it.copy(favorites = Favorites.put(it.favorites, slot, t)) }
                pendingFocus = FocusKeys.fav(cfg.favorites.indexOf(t).coerceAtLeast(0))
            }
        }
        refresh()
    }

    // ---------------------------------------------------------------- dữ liệu

    private class Snapshot(
        val cfg: LauncherConfig,
        val karaoke: Resolved,
        val youtube: Resolved,
        val favorites: List<Resolved>,
        val migrated: LauncherConfig,
    )

    /** Tra lại các ô ngoài luồng UI; kết quả cũ bị bỏ nếu đã có lần làm mới mới hơn. */
    private fun refresh() {
        val gen = ++refreshGen
        val cfg = ConfigRepository.get(this)
        val app = applicationContext
        Bg.run {
            val snap = buildSnapshot(app, cfg)
            Bg.main.post {
                if (gen == refreshGen && !isFinishing) applySnapshot(snap)
            }
        }
    }

    private fun buildSnapshot(ctx: Context, cfg: LauncherConfig): Snapshot {
        val k = cfg.karaoke?.let { AppCatalog.resolveStored(ctx, it) }
            ?: AppCatalog.resolvePackage(ctx, AppCatalog.KARAOKE_PACKAGE, auto = true)
        val y = cfg.youtube?.let { AppCatalog.resolveStored(ctx, it) } ?: run {
            val installed = YouTubeApps.KNOWN.filter { AppCatalog.appEnabled(ctx, it) == true }
            val pkg = YouTubeApps.pick(installed)
            if (pkg != null) AppCatalog.resolvePackage(ctx, pkg, auto = true)
            else Resolved(null, null, null, TargetState.MISSING, auto = true)
        }
        val favs = cfg.favorites.map { AppCatalog.resolveStored(ctx, it) }

        // Activity đổi tên sau khi cập nhật ứng dụng → lưu đích mới.
        fun fix(stored: AppTarget?, r: Resolved): AppTarget? =
            if (stored != null && r.state == TargetState.OK && r.target != null && r.target != stored) r.target else stored
        val migrated = cfg.copy(
            karaoke = fix(cfg.karaoke, k),
            youtube = fix(cfg.youtube, y),
            favorites = cfg.favorites.mapIndexed { i, t -> fix(t, favs[i]) ?: t }.distinct(),
        )
        return Snapshot(cfg, k, y, favs, migrated)
    }

    private fun applySnapshot(s: Snapshot) {
        karaoke = s.karaoke
        youtube = s.youtube
        favorites = s.favorites
        karaokeSub.text = karaokeSubtitle(s.karaoke)
        youtubeSub.text = youtubeSubtitle(s.youtube)
        renderFavorites()

        if (s.migrated != s.cfg && ConfigRepository.get(this) == s.cfg) {
            ConfigRepository.update(this) { s.migrated }
        }

        val available = LinkedHashSet<String>()
        available.addAll(listOf(FocusKeys.KARAOKE, FocusKeys.YOUTUBE, FocusKeys.APPS, FocusKeys.NETWORK, FocusKeys.SETTINGS, FocusKeys.EDIT))
        for (i in favViews.indices) {
            if (favRow.visibility == View.VISIBLE && favViews[i].visibility == View.VISIBLE) available.add(FocusKeys.fav(i))
        }
        val wanted = pendingFocus ?: if (!focusRestored) s.cfg.lastFocus else null
        if (wanted != null) {
            viewForKey(FocusKeys.restore(wanted, available))?.requestFocus()
        } else {
            val cur = currentFocus
            if (cur == null || cur.visibility != View.VISIBLE || (cur in favViews && favRow.visibility != View.VISIBLE)) {
                tileKaraoke.requestFocus()
            }
        }
        pendingFocus = null
        focusRestored = true
    }

    private fun karaokeSubtitle(r: Resolved): String = when (r.state) {
        TargetState.OK -> r.label ?: "HomeKara"
        TargetState.DISABLED -> "${r.label ?: "Ứng dụng"} đang bị tắt"
        TargetState.MISSING -> if (r.auto) "Chưa cài HomeKara" else "Ứng dụng đã bị gỡ — bấm để chọn lại"
    }

    private fun youtubeSubtitle(r: Resolved): String = when (r.state) {
        TargetState.OK -> r.label ?: "YouTube"
        TargetState.DISABLED -> "${r.label ?: "Ứng dụng"} đang bị tắt"
        TargetState.MISSING -> if (r.auto) "Chưa có ứng dụng — bấm để chọn" else "Ứng dụng đã bị gỡ — bấm để chọn lại"
    }

    // ---------------------------------------------------------------- ô yêu thích

    private fun renderFavorites() {
        val show = editMode || favorites.isNotEmpty()
        favHeader.visibility = if (show) View.VISIBLE else View.GONE
        favRow.visibility = if (show) View.VISIBLE else View.GONE
        favHeader.text = if (editMode) "Sửa yêu thích — chọn một ô để đổi, di chuyển hoặc bỏ. Back để xong." else "Ứng dụng yêu thích"
        for ((i, v) in favViews.withIndex()) {
            val icon = v.findViewById<ImageView>(R.id.icon)
            val label = v.findViewById<TextView>(R.id.label)
            val r = favorites.getOrNull(i)
            when {
                r != null -> {
                    v.visibility = View.VISIBLE
                    v.setBackgroundResource(R.drawable.bg_button)
                    label.text = when (r.state) {
                        TargetState.OK -> r.label ?: r.packageName
                        TargetState.DISABLED -> "${r.label ?: r.packageName} (tắt)"
                        TargetState.MISSING -> "Đã gỡ"
                    }
                    v.alpha = if (r.state == TargetState.OK) 1f else 0.6f
                    val t = r.target
                    if (t != null && r.state != TargetState.MISSING) IconCache.load(icon, t)
                    else {
                        IconCache.release(icon)
                        icon.setImageResource(R.drawable.ic_alert)
                    }
                }
                editMode && i == favorites.size -> {
                    v.visibility = View.VISIBLE
                    v.alpha = 1f
                    v.setBackgroundResource(R.drawable.bg_slot_empty)
                    IconCache.release(icon)
                    icon.setImageResource(R.drawable.ic_add)
                    label.text = "Thêm"
                }
                else -> {
                    v.visibility = View.INVISIBLE
                    IconCache.release(icon)
                }
            }
        }
    }

    private fun setEditMode(on: Boolean) {
        editMode = on
        val cfg = ConfigRepository.get(this)
        btnEdit.text = if (on) "Xong" else "Sửa yêu thích"
        btnEdit.setCompoundDrawablesWithIntrinsicBounds(if (on) R.drawable.ic_check else R.drawable.ic_edit, 0, 0, 0)
        favHeader.setTextColor(if (on) UiStyle.palette(cfg).accent else resources.getColor(R.color.text_secondary))
        renderFavorites()
        UiStyle.applyTextScale(root, cfg.largeText)
        if (on) favViews.firstOrNull { it.visibility == View.VISIBLE }?.requestFocus()
        else if (currentFocus == null || currentFocus?.visibility != View.VISIBLE) btnEdit.requestFocus()
    }

    private fun onFavorite(i: Int) {
        val r = favorites.getOrNull(i)
        when {
            r == null -> pickFavorite(i)
            editMode -> favoriteMenu(i)
            r.state == TargetState.OK -> launch(r.target)
            else -> favoriteMenu(i)
        }
    }

    private fun favoriteMenu(i: Int) {
        val r = favorites.getOrNull(i) ?: run { pickFavorite(i); return }
        val items = ArrayList<ActionDialog.Item>()
        if (r.state == TargetState.OK) items += ActionDialog.Item("Mở", R.drawable.ic_play_small) { launch(r.target) }
        if (i > 0) items += ActionDialog.Item("Chuyển sang trái", R.drawable.ic_left) { moveFavorite(i, i - 1) }
        if (i < favorites.size - 1) items += ActionDialog.Item("Chuyển sang phải", R.drawable.ic_right) { moveFavorite(i, i + 1) }
        items += ActionDialog.Item("Đổi sang ứng dụng khác", R.drawable.ic_edit) { pickFavorite(i) }
        items += ActionDialog.Item("Bỏ khỏi yêu thích", R.drawable.ic_remove) { removeFavorite(i) }
        val message = when (r.state) {
            TargetState.OK -> null
            TargetState.DISABLED -> "Ứng dụng đang bị tắt trong Cài đặt."
            TargetState.MISSING -> "Ứng dụng này đã bị gỡ khỏi box."
        }
        showDialog(r.label ?: r.packageName ?: "Ô yêu thích", message, items)
    }

    private fun moveFavorite(from: Int, to: Int) {
        var pos = from
        ConfigRepository.update(this) { c ->
            val (list, p) = Favorites.move(c.favorites, from, to)
            pos = p
            c.copy(favorites = list)
        }
        pendingFocus = FocusKeys.fav(pos)
        refresh()
    }

    private fun removeFavorite(i: Int) {
        val cfg = ConfigRepository.update(this) { it.copy(favorites = Favorites.remove(it.favorites, i)) }
        pendingFocus = when {
            cfg.favorites.isNotEmpty() -> FocusKeys.fav(minOf(i, cfg.favorites.size - 1))
            editMode -> FocusKeys.fav(0)
            else -> FocusKeys.EDIT
        }
        refresh()
    }

    private fun pickFavorite(i: Int) {
        val slot = minOf(i, favorites.size)
        startActivityForResult(AllAppsActivity.pickIntent(this, "Chọn ứng dụng cho ô yêu thích ${slot + 1}"), REQ_FAV_BASE + slot)
    }

    // ---------------------------------------------------------------- hai ô chính

    private fun onKaraoke() {
        val r = karaoke ?: return
        when (r.state) {
            TargetState.OK -> launch(r.target)
            TargetState.DISABLED -> disabledMenu(r, karaokeTile = true)
            TargetState.MISSING -> showDialog(
                "Chưa tìm thấy HomeKara",
                if (r.auto) "Ô Karaoke mở ứng dụng có mã gói ${AppCatalog.KARAOKE_PACKAGE}. Hãy cài HomeKara, hoặc chọn một ứng dụng karaoke khác cho ô này."
                else "Ứng dụng đã gán cho ô Karaoke không còn trên box.",
                listOf(
                    ActionDialog.Item("Chọn ứng dụng khác", R.drawable.ic_apps) { pick(REQ_KARAOKE, "Chọn ứng dụng cho ô Karaoke") },
                ) + if (!r.auto) listOf(ActionDialog.Item("Dùng HomeKara mặc định", R.drawable.ic_mic_small) { resetTile(karaokeTile = true) }) else emptyList<ActionDialog.Item>(),
            )
        }
    }

    private fun onYoutube() {
        val r = youtube ?: return
        when (r.state) {
            TargetState.OK -> launch(r.target)
            TargetState.DISABLED -> disabledMenu(r, karaokeTile = false)
            TargetState.MISSING -> pick(REQ_YOUTUBE, "Chọn ứng dụng cho ô YouTube")
        }
    }

    private fun disabledMenu(r: Resolved, karaokeTile: Boolean) {
        val items = ArrayList<ActionDialog.Item>()
        r.packageName?.let { pkg -> items += ActionDialog.Item("Mở thông tin ứng dụng để bật lại", R.drawable.ic_info) { SystemSettings.appDetails(this, pkg) } }
        items += ActionDialog.Item("Chọn ứng dụng khác", R.drawable.ic_apps) {
            if (karaokeTile) pick(REQ_KARAOKE, "Chọn ứng dụng cho ô Karaoke") else pick(REQ_YOUTUBE, "Chọn ứng dụng cho ô YouTube")
        }
        showDialog("${r.label ?: "Ứng dụng"} đang bị tắt", null, items)
    }

    private fun tileMenu(karaokeTile: Boolean) {
        val r = (if (karaokeTile) karaoke else youtube) ?: return
        val name = if (karaokeTile) "Karaoke" else "YouTube"
        val items = ArrayList<ActionDialog.Item>()
        if (r.state == TargetState.OK) items += ActionDialog.Item("Mở ${r.label ?: name}", R.drawable.ic_play_small) { launch(r.target) }
        items += ActionDialog.Item("Đổi ứng dụng cho ô $name", R.drawable.ic_apps) {
            pick(if (karaokeTile) REQ_KARAOKE else REQ_YOUTUBE, "Chọn ứng dụng cho ô $name")
        }
        if (!r.auto) {
            items += ActionDialog.Item(if (karaokeTile) "Dùng HomeKara mặc định" else "Tự nhận diện ứng dụng YouTube", R.drawable.ic_refresh) {
                resetTile(karaokeTile)
            }
        }
        r.packageName?.let { pkg ->
            if (r.state != TargetState.MISSING) items += ActionDialog.Item("Thông tin ứng dụng", R.drawable.ic_info) { SystemSettings.appDetails(this, pkg) }
        }
        val msg = if (r.auto && r.state == TargetState.OK && !karaokeTile) "Đang tự nhận diện: ${r.label}. Bạn có thể gán ứng dụng khác." else null
        showDialog("Ô $name", msg, items)
    }

    private fun resetTile(karaokeTile: Boolean) {
        ConfigRepository.update(this) { if (karaokeTile) it.copy(karaoke = null) else it.copy(youtube = null) }
        pendingFocus = if (karaokeTile) FocusKeys.KARAOKE else FocusKeys.YOUTUBE
        refresh()
    }

    private fun pick(requestCode: Int, title: String) {
        startActivityForResult(AllAppsActivity.pickIntent(this, title), requestCode)
    }

    private fun launch(target: AppTarget?) {
        val result = AppLauncher.open(this, target)
        UiStyle.toast(this, AppLauncher.message(result))
        if (result != LaunchResult.OK && result != LaunchResult.IGNORED) refresh()
    }

    // ---------------------------------------------------------------- mạng, phím Menu

    private fun networkMenu() {
        val s = NetworkStatus.current(this)
        showDialog(
            "Mạng",
            s.text + "\nLauncher chỉ báo trạng thái kết nối của box, không tự kiểm tra Internet hay YouTube.",
            listOf(
                ActionDialog.Item("Cài đặt Wi-Fi", R.drawable.ic_wifi) { SystemSettings.open(this, Settings.ACTION_WIFI_SETTINGS) },
                ActionDialog.Item("Mạng dây (LAN) và mạng khác", R.drawable.ic_lan) {
                    SystemSettings.open(this, SystemSettings.ACTION_ETHERNET, Settings.ACTION_WIRELESS_SETTINGS)
                },
            ),
        )
    }

    private fun onMenuKey() {
        val f = currentFocus
        val favIndex = if (f == null) -1 else favViews.indexOf(f)
        when {
            f === tileKaraoke -> tileMenu(karaokeTile = true)
            f === tileYoutube -> tileMenu(karaokeTile = false)
            favIndex >= 0 -> favoriteMenu(favIndex)
            else -> setEditMode(!editMode)
        }
    }

    private fun showDialog(title: String, message: String?, items: List<ActionDialog.Item>) {
        dialog?.dismiss()
        dialog = ActionDialog.show(this, title, message, items)
    }

    // ---------------------------------------------------------------- focus

    private fun viewForKey(key: String): View? = when (key) {
        FocusKeys.KARAOKE -> tileKaraoke
        FocusKeys.YOUTUBE -> tileYoutube
        FocusKeys.APPS -> btnApps
        FocusKeys.NETWORK -> btnNetwork
        FocusKeys.SETTINGS -> btnSettings
        FocusKeys.EDIT -> btnEdit
        else -> key.removePrefix("fav").toIntOrNull()?.let { favViews.getOrNull(it) }
    }

    private fun keyForView(v: View?): String? {
        if (v == null) return null
        val favIndex = favViews.indexOf(v)
        return when {
            v === tileKaraoke -> FocusKeys.KARAOKE
            v === tileYoutube -> FocusKeys.YOUTUBE
            v === btnApps -> FocusKeys.APPS
            v === btnNetwork -> FocusKeys.NETWORK
            v === btnSettings -> FocusKeys.SETTINGS
            v === btnEdit -> FocusKeys.EDIT
            favIndex >= 0 -> FocusKeys.fav(favIndex)
            else -> null
        }
    }

    // ---------------------------------------------------------------- đồng hồ, mạng

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> updateNetwork()
                else -> updateClock()
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    private fun registerReceivers() {
        if (receiversOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(systemReceiver, f)
        val p = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, p)
        receiversOn = true
    }

    private fun unregisterReceivers() {
        if (!receiversOn) return
        try {
            unregisterReceiver(systemReceiver)
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // đã huỷ
        }
        receiversOn = false
    }

    private fun updateClock() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        clock.text = ViText.time(hour, c.get(Calendar.MINUTE))
        date.text = ViText.date(c.get(Calendar.DAY_OF_WEEK), c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
        greeting.text = ViText.greeting(hour)
    }

    private fun updateNetwork() {
        val s = NetworkStatus.current(this)
        netText.text = s.text
        netIcon.setImageResource(
            when (s.kind) {
                NetState.Kind.WIFI, NetState.Kind.MOBILE -> R.drawable.ic_wifi
                NetState.Kind.LAN, NetState.Kind.OTHER -> R.drawable.ic_lan
                NetState.Kind.NONE -> R.drawable.ic_alert
            },
        )
        netIcon.setColorFilter(resources.getColor(if (s.kind == NetState.Kind.NONE) R.color.warn else R.color.ok))
    }

    companion object {
        private const val REQ_KARAOKE = 101
        private const val REQ_YOUTUBE = 102
        private const val REQ_FAV_BASE = 110
    }
}
