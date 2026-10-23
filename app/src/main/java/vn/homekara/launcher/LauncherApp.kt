package vn.homekara.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import vn.homekara.launcher.apps.IconCache

class LauncherApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Khi launcher bị ẩn hoặc hệ thống thiếu RAM: nhả toàn bộ ảnh biểu tượng.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            IconCache.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        IconCache.clear()
    }
}
