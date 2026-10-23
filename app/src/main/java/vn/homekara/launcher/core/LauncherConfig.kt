package vn.homekara.launcher.core

/** Cấu hình phiên bản 1 của launcher. Mọi giá trị đã được kiểm tra hợp lệ. */
data class LauncherConfig(
    /** Ứng dụng của ô YouTube do người dùng chọn; null = tự nhận diện. */
    val youtube: AppTarget? = null,
    /** Ứng dụng của ô Karaoke; null = HomeKara (vn.homekara.box). */
    val karaoke: AppTarget? = null,
    val favorites: List<AppTarget> = emptyList(),
    val theme: Int = 0,
    val largeText: Boolean = false,
    val safeMargin: Int = DEFAULT_MARGIN,
    val lastFocus: String = FocusKeys.KARAOKE,
) {
    companion object {
        const val VERSION = 1
        const val MAX_FAVORITES = 4
        const val THEME_COUNT = 3
        const val MIN_MARGIN = 0
        const val MAX_MARGIN = 10
        const val DEFAULT_MARGIN = 5
    }
}

/** Chuyển cấu hình qua lại dạng chuỗi để lưu trong SharedPreferences. */
object ConfigCodec {
    const val K_VERSION = "v"
    const val K_YOUTUBE = "youtube"
    const val K_KARAOKE = "karaoke"
    const val K_FAVORITES = "favorites"
    const val K_THEME = "theme"
    const val K_LARGE_TEXT = "largeText"
    const val K_MARGIN = "margin"
    const val K_FOCUS = "focus"
    private const val SEP = '|'

    fun encode(c: LauncherConfig): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m[K_VERSION] = LauncherConfig.VERSION.toString()
        c.youtube?.let { m[K_YOUTUBE] = it.encode() }
        c.karaoke?.let { m[K_KARAOKE] = it.encode() }
        m[K_FAVORITES] = c.favorites.joinToString(SEP.toString()) { it.encode() }
        m[K_THEME] = c.theme.toString()
        m[K_LARGE_TEXT] = c.largeText.toString()
        m[K_MARGIN] = c.safeMargin.toString()
        m[K_FOCUS] = c.lastFocus
        return m
    }

    /** Đọc từng mục độc lập: mục hỏng hoặc sai kiểu được đưa về mặc định an toàn. */
    fun decode(raw: Map<String, *>): LauncherConfig {
        val d = LauncherConfig()
        fun str(key: String): String? = raw[key]?.toString()
        fun int(key: String, min: Int, max: Int, def: Int): Int {
            val v = str(key)?.trim()?.toIntOrNull() ?: return def
            return if (v in min..max) v else def
        }

        val favorites = str(K_FAVORITES)
            ?.split(SEP)
            ?.mapNotNull { AppTarget.decode(it) }
            ?.distinct()
            ?.take(LauncherConfig.MAX_FAVORITES)
            ?: d.favorites

        val large = when (str(K_LARGE_TEXT)?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> d.largeText
        }

        val focus = str(K_FOCUS)?.takeIf { FocusKeys.isKnown(it) } ?: d.lastFocus

        return LauncherConfig(
            youtube = AppTarget.decode(str(K_YOUTUBE)),
            karaoke = AppTarget.decode(str(K_KARAOKE)),
            favorites = favorites,
            theme = int(K_THEME, 0, LauncherConfig.THEME_COUNT - 1, d.theme),
            largeText = large,
            safeMargin = int(K_MARGIN, LauncherConfig.MIN_MARGIN, LauncherConfig.MAX_MARGIN, d.safeMargin),
            lastFocus = focus,
        )
    }
}
