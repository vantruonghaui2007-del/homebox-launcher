package vn.homekara.launcher.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import vn.homekara.launcher.core.ConfigCodec
import vn.homekara.launcher.core.LauncherConfig
import java.util.concurrent.Executors

/** Một luồng nền ưu tiên thấp cho việc đọc PackageManager, nạp icon, ghi cấu hình. */
object Bg {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "homebox-bg").apply { isDaemon = true }
    }
    val main = Handler(Looper.getMainLooper())

    fun run(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (t: Throwable) {
                android.util.Log.w("HomeBox", "Lỗi tác vụ nền", t)
            }
        }
    }
}

/** Cấu hình v1 lưu trong SharedPreferences, giữ bản sao trong bộ nhớ. */
object ConfigRepository {
    private const val PREFS = "homebox_config_v1"

    @Volatile private var cached: LauncherConfig? = null

    fun get(ctx: Context): LauncherConfig {
        cached?.let { return it }
        val cfg = try {
            ConfigCodec.decode(prefs(ctx).all)
        } catch (e: Exception) {
            LauncherConfig()
        }
        cached = cfg
        return cfg
    }

    fun update(ctx: Context, change: (LauncherConfig) -> LauncherConfig): LauncherConfig {
        val old = get(ctx)
        val new = change(old)
        if (new == old) return old
        cached = new
        val values = ConfigCodec.encode(new)
        val editor = prefs(ctx).edit().clear()
        for ((k, v) in values) editor.putString(k, v)
        editor.apply() // ghi xuống đĩa ở luồng nền của hệ thống
        return new
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
