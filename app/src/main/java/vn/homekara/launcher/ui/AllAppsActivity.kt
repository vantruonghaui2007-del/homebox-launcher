package vn.homekara.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import vn.homekara.launcher.R
import vn.homekara.launcher.apps.AppCatalog
import vn.homekara.launcher.apps.AppLauncher
import vn.homekara.launcher.apps.IconCache
import vn.homekara.launcher.apps.LaunchResult
import vn.homekara.launcher.apps.SystemSettings
import vn.homekara.launcher.core.AppEntry
import vn.homekara.launcher.core.Favorites
import vn.homekara.launcher.core.LauncherConfig
import vn.homekara.launcher.data.Bg
import vn.homekara.launcher.data.ConfigRepository

/** Danh sách mọi ứng dụng. Có chế độ "chọn" để gán ứng dụng cho một ô trên Home. */
class AllAppsActivity : Activity() {

    companion object {
        const val EXTRA_PICK_TITLE = "vn.homekara.launcher.PICK_TITLE"
        const val EXTRA_TARGET = "vn.homekara.launcher.TARGET"

        fun pickIntent(ctx: Context, title: String): Intent =
            Intent(ctx, AllAppsActivity::class.java).putExtra(EXTRA_PICK_TITLE, title)
    }

    private lateinit var root: View
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var count: TextView

    private var pickTitle: String? = null
    private var apps: List<AppEntry> = emptyList()
    private var loaded = false
    private var largeText = false
    private var gen = 0
    private var receiverOn = false
    private var dialog: Dialog? = null
    private val adapter = AppsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        root = findViewById(R.id.root)
        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        count = findViewById(R.id.count)

        pickTitle = intent.getStringExtra(EXTRA_PICK_TITLE)
        val title = findViewById<TextView>(R.id.title)
        val hint = findViewById<TextView>(R.id.hint)
        if (pickTitle != null) {
            title.text = pickTitle
            hint.text = "Chọn ứng dụng rồi bấm OK · Back để huỷ"
        }

        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> onChoose(pos) }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            appMenu(pos)
            true
        }
    }

    override fun onStart() {
        super.onStart()
        val cfg = ConfigRepository.get(this)
        largeText = cfg.largeText
        UiStyle.apply(this, root, cfg)
        registerPackageReceiver()
        if (loaded) adapter.notifyDataSetChanged() // nạp lại icon đã nhả khi rời màn hình
        load()
    }

    override fun onStop() {
        super.onStop()
        unregisterPackageReceiver()
        dialog?.dismiss()
        dialog = null
        gen++
        for (i in 0 until grid.childCount) {
            (grid.getChildAt(i).tag as? Holder)?.let { IconCache.release(it.icon) }
        }
        IconCache.clear()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.repeatCount == 0) {
                val pos = grid.selectedItemPosition
                if (pos >= 0) appMenu(pos)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun load() {
        val g = ++gen
        if (!loaded) {
            empty.text = "Đang tải danh sách ứng dụng…"
            empty.visibility = View.VISIBLE
        }
        val app = applicationContext
        Bg.run {
            val list = AppCatalog.listApps(app)
            Bg.main.post { if (g == gen && !isFinishing) setApps(list) }
        }
    }

    private fun setApps(list: List<AppEntry>) {
        val keep = apps.getOrNull(grid.selectedItemPosition)?.target
        val first = !loaded
        apps = list
        loaded = true
        adapter.notifyDataSetChanged()
        count.text = "${list.size} ứng dụng"
        if (list.isEmpty()) {
            empty.text = "Không tìm thấy ứng dụng nào"
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }
        val idx = list.indexOfFirst { it.target == keep }.takeIf { it >= 0 } ?: 0
        if (first) grid.requestFocus()
        if (list.isNotEmpty()) grid.setSelection(idx)
    }

    private fun onChoose(pos: Int) {
        val e = apps.getOrNull(pos) ?: return
        if (pickTitle != null) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_TARGET, e.target.encode()))
            finish()
            return
        }
        val result = AppLauncher.open(this, e.target)
        UiStyle.toast(this, AppLauncher.message(result))
        if (result != LaunchResult.OK && result != LaunchResult.IGNORED) load()
    }

    private fun appMenu(pos: Int) {
        val e = apps.getOrNull(pos) ?: return
        if (pickTitle != null) {
            onChoose(pos)
            return
        }
        val cfg = ConfigRepository.get(this)
        val items = ArrayList<ActionDialog.Item>()
        items += ActionDialog.Item("Mở", R.drawable.ic_play_small) { onChoose(pos) }
        val favIndex = cfg.favorites.indexOf(e.target)
        if (favIndex >= 0) {
            items += ActionDialog.Item("Bỏ khỏi yêu thích", R.drawable.ic_remove) {
                ConfigRepository.update(this) { it.copy(favorites = Favorites.remove(it.favorites, favIndex)) }
                Toast.makeText(this, "Đã bỏ ${e.label} khỏi yêu thích", Toast.LENGTH_SHORT).show()
            }
        } else {
            items += ActionDialog.Item("Thêm vào yêu thích", R.drawable.ic_add_small) {
                if (cfg.favorites.size >= LauncherConfig.MAX_FAVORITES) {
                    UiStyle.toast(this, "Yêu thích đã đủ ${LauncherConfig.MAX_FAVORITES} ô. Hãy bỏ bớt một ô ở Home.")
                } else {
                    ConfigRepository.update(this) { it.copy(favorites = Favorites.add(it.favorites, e.target)) }
                    Toast.makeText(this, "Đã thêm ${e.label} vào yêu thích", Toast.LENGTH_SHORT).show()
                }
            }
        }
        items += ActionDialog.Item("Đặt cho ô YouTube", R.drawable.ic_play_small) {
            ConfigRepository.update(this) { it.copy(youtube = e.target) }
            Toast.makeText(this, "Ô YouTube sẽ mở ${e.label}", Toast.LENGTH_SHORT).show()
        }
        items += ActionDialog.Item("Đặt cho ô Karaoke", R.drawable.ic_mic_small) {
            ConfigRepository.update(this) { it.copy(karaoke = e.target) }
            Toast.makeText(this, "Ô Karaoke sẽ mở ${e.label}", Toast.LENGTH_SHORT).show()
        }
        items += ActionDialog.Item("Thông tin ứng dụng", R.drawable.ic_info) { SystemSettings.appDetails(this, e.target.packageName) }
        dialog?.dismiss()
        dialog = ActionDialog.show(this, e.label, null, items)
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = load()
    }

    private fun registerPackageReceiver() {
        if (receiverOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, f)
        receiverOn = true
    }

    private fun unregisterPackageReceiver() {
        if (!receiverOn) return
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // đã huỷ
        }
        receiverOn = false
    }

    private class Holder(val icon: ImageView, val label: TextView)

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): Any = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false).also {
                it.tag = Holder(it.findViewById(R.id.icon), it.findViewById(R.id.label))
            }
            UiStyle.applyTextScale(v, largeText)
            val h = v.tag as Holder
            val e = apps[position]
            h.label.text = e.label
            IconCache.load(h.icon, e.target)
            return v
        }
    }
}
