package vn.homekara.launcher.core

/** Thao tác trên danh sách yêu thích (tối đa 4). Luôn trả danh sách mới. */
object Favorites {
    fun add(list: List<AppTarget>, t: AppTarget): List<AppTarget> =
        if (list.contains(t) || list.size >= LauncherConfig.MAX_FAVORITES) list else list + t

    fun remove(list: List<AppTarget>, index: Int): List<AppTarget> =
        if (index !in list.indices) list else list.filterIndexed { i, _ -> i != index }

    /** Đặt ứng dụng vào ô [index]; nếu đã có ở ô khác thì dời về ô này. Ô trống → thêm cuối. */
    fun put(list: List<AppTarget>, index: Int, t: AppTarget): List<AppTarget> {
        if (index !in list.indices) return add(list, t)
        val m = list.toMutableList()
        m[index] = t
        val dup = m.indices.firstOrNull { it != index && m[it] == t }
        if (dup != null) m.removeAt(dup)
        return m
    }

    /** Di chuyển ô [from] sang [to]; trả về danh sách và vị trí mới. */
    fun move(list: List<AppTarget>, from: Int, to: Int): Pair<List<AppTarget>, Int> {
        if (from !in list.indices) return list to from
        val dest = to.coerceIn(0, list.size - 1)
        if (dest == from) return list to from
        val m = list.toMutableList()
        val item = m.removeAt(from)
        m.add(dest, item)
        return m to dest
    }
}

/** Chặn mở ứng dụng lặp lại khi giữ hoặc bấm OK liên tục. */
class LaunchGuard(private val clock: () -> Long, private val windowMs: Long = 1200) {
    private var last = Long.MIN_VALUE

    fun tryAcquire(): Boolean {
        val now = clock()
        if (last != Long.MIN_VALUE && now - last in 0 until windowMs) return false
        last = now
        return true
    }
}

/** Khoá focus trên Home, dùng để lưu và khôi phục ô vừa chọn. */
object FocusKeys {
    const val KARAOKE = "karaoke"
    const val YOUTUBE = "youtube"
    const val APPS = "apps"
    const val NETWORK = "network"
    const val SETTINGS = "settings"
    const val EDIT = "edit"
    fun fav(i: Int) = "fav$i"

    private val ALL: Set<String> = setOf(KARAOKE, YOUTUBE, APPS, NETWORK, SETTINGS, EDIT) +
        (0 until LauncherConfig.MAX_FAVORITES).map { fav(it) }

    fun isKnown(key: String) = key in ALL

    /** Ô đã lưu nếu còn hiển thị; nếu không thì về Karaoke. */
    fun restore(saved: String?, available: Set<String>): String = when {
        saved != null && saved in available -> saved
        KARAOKE in available -> KARAOKE
        else -> available.firstOrNull() ?: KARAOKE
    }
}

/** Các ứng dụng xem YouTube hay gặp, theo thứ tự ưu tiên khi tự nhận diện. */
object YouTubeApps {
    val KNOWN = listOf(
        "com.liskovsoft.smarttubetv.beta",
        "com.liskovsoft.smarttubetv",
        "com.google.android.youtube.tv",
        "com.google.android.youtube.googletv",
        "com.teamsmart.videomanager.tv",
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "com.vanced.android.youtube",
        "org.schabi.newpipe",
    )

    fun pick(installed: Collection<String>): String? = KNOWN.firstOrNull { it in installed }
}

/** Định dạng ngày giờ tiếng Việt, không phụ thuộc ngôn ngữ hệ thống. */
object ViText {
    private val DAYS = arrayOf("Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")

    fun time(hour: Int, minute: Int): String = pad(hour) + ":" + pad(minute)

    /** [dayOfWeek] theo Calendar: 1 = Chủ nhật … 7 = Thứ Bảy; [month] từ 1. */
    fun date(dayOfWeek: Int, day: Int, month: Int, year: Int): String {
        val name = DAYS[((dayOfWeek - 1) % 7 + 7) % 7]
        return "$name, ${pad(day)}/${pad(month)}/$year"
    }

    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> "Chào buổi sáng"
        in 11..12 -> "Chào buổi trưa"
        in 13..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }

    fun bytes(value: Long): String {
        if (value < 0) return "không rõ"
        val mb = value / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(java.util.Locale.US, "%.2f GB", mb / 1024) else String.format(java.util.Locale.US, "%.0f MB", mb)
    }

    private fun pad(v: Int) = if (v < 10) "0$v" else v.toString()
}
