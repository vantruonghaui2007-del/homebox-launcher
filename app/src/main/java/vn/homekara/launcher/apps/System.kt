package vn.homekara.launcher.apps

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import vn.homekara.launcher.BuildConfig
import vn.homekara.launcher.core.AppTarget
import vn.homekara.launcher.core.ViText
import vn.homekara.launcher.core.YouTubeApps

/** Mở trang cài đặt hệ thống; trang nào ROM lược bỏ thì thử trang kế tiếp, cuối cùng là Cài đặt chung. */
object SystemSettings {
    const val ACTION_ETHERNET = "android.settings.ETHERNET_SETTINGS"

    fun open(activity: Activity, vararg actions: String): Boolean {
        val candidates = actions.map { Intent(it) } + Intent(Settings.ACTION_SETTINGS)
        for (intent in candidates) {
            if (start(activity, intent)) return true
        }
        Toast.makeText(activity, "Box không có trang cài đặt này", Toast.LENGTH_LONG).show()
        return false
    }

    fun appDetails(activity: Activity, packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        if (start(activity, intent)) return true
        return open(activity, Settings.ACTION_APPLICATION_SETTINGS)
    }

    fun uninstall(activity: Activity, packageName: String): Boolean =
        start(activity, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))

    fun start(activity: Activity, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(activity.packageManager) == null) return false
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** Trạng thái kết nối cục bộ. Chỉ biết đã nối Wi-Fi/LAN, không khẳng định có Internet. */
data class NetState(val kind: Kind, val text: String) {
    enum class Kind { WIFI, LAN, MOBILE, OTHER, NONE }
}

object NetworkStatus {
    @Suppress("DEPRECATION")
    fun current(ctx: Context): NetState {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetState(NetState.Kind.NONE, "Không rõ mạng")
        val info = try {
            cm.activeNetworkInfo
        } catch (e: Exception) {
            null
        }
        if (info == null || !info.isConnected) return NetState(NetState.Kind.NONE, "Chưa kết nối mạng")
        return when (info.type) {
            ConnectivityManager.TYPE_WIFI -> NetState(NetState.Kind.WIFI, "Đã kết nối Wi-Fi")
            ConnectivityManager.TYPE_ETHERNET -> NetState(NetState.Kind.LAN, "Đã kết nối LAN")
            ConnectivityManager.TYPE_MOBILE -> NetState(NetState.Kind.MOBILE, "Dữ liệu di động")
            else -> NetState(NetState.Kind.OTHER, "Đã kết nối " + (info.typeName ?: "mạng"))
        }
    }
}

/** Màn hình chính (Home) hiện có trên máy. */
object HomeApps {
    fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Ứng dụng đang được chọn "Luôn luôn" làm Home; null = chưa chọn (hệ thống sẽ hỏi). */
    fun currentDefault(ctx: Context): AppTarget? = try {
        val ri = ctx.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        val ai = ri?.activityInfo
        if (ai == null || ai.packageName == "android") null else AppTarget.of(ai.packageName, ai.name)
    } catch (e: Exception) {
        null
    }

    /** Mọi Activity khai báo HOME, trừ hộp chọn của hệ thống. */
    fun all(ctx: Context): List<Pair<AppTarget, String>> = try {
        val pm = ctx.packageManager
        pm.queryIntentActivities(homeIntent(), 0).mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == "android") return@mapNotNull null
            val t = AppTarget.of(ai.packageName, ai.name) ?: return@mapNotNull null
            t to (ri.loadLabel(pm)?.toString() ?: ai.packageName)
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun openHome(activity: Activity, t: AppTarget): Boolean =
        SystemSettings.start(activity, homeIntent().setComponent(ComponentName(t.packageName, t.activityName)))

    /** Hiện bảng chọn Home của hệ thống (chỉ hiện khi chưa có mặc định). */
    fun showChooser(activity: Activity): Boolean = SystemSettings.start(activity, homeIntent())
}

/** Thông tin box cho trang "Thông tin box". Gọi ngoài luồng UI. */
object BoxInfo {
    @Suppress("DEPRECATION")
    fun collect(activity: Activity): List<Pair<String, String>> {
        val ctx = activity.applicationContext
        val rows = ArrayList<Pair<String, String>>()
        rows += "Thiết bị" to "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        rows += "Mã máy / sản phẩm" to "${Build.DEVICE} / ${Build.PRODUCT}"
        rows += "Phần cứng" to "${Build.HARDWARE} · ${Runtime.getRuntime().availableProcessors()} nhân"
        rows += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        rows += "Kernel" to (System.getProperty("os.version") ?: "không rõ")
        val abis = if (Build.VERSION.SDK_INT >= 21) {
            Build.SUPPORTED_ABIS.joinToString(", ")
        } else {
            listOf(Build.CPU_ABI, Build.CPU_ABI2).filter { !it.isNullOrBlank() }.joinToString(", ")
        }
        rows += "ABI" to abis

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            rows += "RAM hệ thống nhận" to "${ViText.bytes(mi.totalMem)} (còn trống ${ViText.bytes(mi.availMem)})"
            rows += "Giới hạn RAM mỗi ứng dụng" to "${am.memoryClass} MB" + if (am.isLowRamDevice) " · chế độ RAM thấp" else ""
        }
        try {
            val fs = StatFs(Environment.getDataDirectory().path)
            rows += "Bộ nhớ trong" to "trống ${ViText.bytes(fs.availableBytes)} / ${ViText.bytes(fs.totalBytes)}"
        } catch (e: Exception) {
            rows += "Bộ nhớ trong" to "không đọc được"
        }

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            val dpW = (dm.widthPixels / dm.density).toInt()
            val dpH = (dm.heightPixels / dm.density).toInt()
            rows += "Màn hình" to "${dm.widthPixels}×${dm.heightPixels} px · ${dm.densityDpi} dpi · ${dpW}×${dpH} dp"
        }
        rows += "Mạng" to NetworkStatus.current(ctx).text

        val def = HomeApps.currentDefault(ctx)
        rows += "Home mặc định" to when {
            def == null -> "Chưa chọn (hệ thống sẽ hỏi)"
            def.packageName == ctx.packageName -> "HomeBox Lite"
            else -> (AppCatalog.label(ctx, def) ?: def.packageName) + " (${def.packageName})"
        }
        rows += "Các màn hình Home" to HomeApps.all(ctx).joinToString("\n") { "${it.second} — ${it.first.packageName}" }.ifEmpty { "không có" }

        rows += "HomeKara" to (version(ctx, AppCatalog.KARAOKE_PACKAGE) ?: "chưa cài")
        val yt = YouTubeApps.KNOWN.mapNotNull { pkg -> version(ctx, pkg)?.let { "$pkg $it" } }
        rows += "Ứng dụng YouTube" to yt.joinToString("\n").ifEmpty { "không tìm thấy ứng dụng quen thuộc" }
        rows += "HomeBox Lite" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        return rows
    }

    private fun version(ctx: Context, pkg: String): String? = try {
        val pi = ctx.packageManager.getPackageInfo(pkg, 0)
        val enabled = pi.applicationInfo?.enabled != false
        (pi.versionName ?: "?") + if (enabled) "" else " (đang tắt)"
    } catch (e: Exception) {
        null
    }
}
