package vn.homekara.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTargetTest {
    @Test fun roundTrip() {
        val t = AppTarget.of("vn.homekara.box", "vn.homekara.box.ui.HomeActivity")!!
        assertEquals(t, AppTarget.decode(t.encode()))
    }

    @Test fun relativeActivityIsExpanded() {
        assertEquals("com.a.b.Main", AppTarget.of("com.a.b", ".Main")!!.activityName)
    }

    @Test fun innerClassAllowed() {
        assertEquals("com.a.b.X\$Y", AppTarget.of("com.a.b", "com.a.b.X\$Y")!!.activityName)
    }

    @Test fun invalidValuesRejected() {
        listOf(null, "", "/", "abc", "/x.Y", "com.a/", "nopackage/x.Y", "com.a/b c", "com.a/1bad", "com..a/x.Y")
            .forEach { assertNull("'$it'", AppTarget.decode(it)) }
    }
}

class AppEntriesTest {
    private val a = AppTarget("com.a", "com.a.Main")
    private val b = AppTarget("com.b", "com.b.Main")

    @Test fun dedupesByActivityAndSorts() {
        val out = AppEntries.dedupeAndSort(
            listOf(AppEntry(b, "Zeta"), AppEntry(a, "Alpha"), AppEntry(b, "Zeta TV")),
            String.CASE_INSENSITIVE_ORDER,
        )
        assertEquals(listOf("Alpha", "Zeta"), out.map { it.label })
    }
}

class ConfigCodecTest {
    private val yt = AppTarget("com.google.android.youtube.tv", "com.google.android.apps.youtube.tv.activity.ShellActivity")
    private val f1 = AppTarget("com.a", "com.a.Main")
    private val f2 = AppTarget("com.b", "com.b.Main")

    @Test fun emptyGivesDefaults() {
        assertEquals(LauncherConfig(), ConfigCodec.decode(emptyMap<String, Any>()))
    }

    @Test fun roundTrip() {
        val c = LauncherConfig(youtube = yt, favorites = listOf(f1, f2), theme = 2, largeText = true, safeMargin = 8, lastFocus = "fav1")
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun corruptValuesFallBackPerField() {
        val raw = mapOf(
            ConfigCodec.K_YOUTUBE to "rubbish",
            ConfigCodec.K_FAVORITES to "${f1.encode()}|bad|${f1.encode()}|${f2.encode()}",
            ConfigCodec.K_THEME to "99",
            ConfigCodec.K_LARGE_TEXT to 42,
            ConfigCodec.K_MARGIN to "-3",
            ConfigCodec.K_FOCUS to "nowhere",
        )
        val c = ConfigCodec.decode(raw)
        assertNull(c.youtube)
        assertEquals(listOf(f1, f2), c.favorites)
        assertEquals(0, c.theme)
        assertFalse(c.largeText)
        assertEquals(LauncherConfig.DEFAULT_MARGIN, c.safeMargin)
        assertEquals(FocusKeys.KARAOKE, c.lastFocus)
    }

    @Test fun wrongTypesAreTolerated() {
        val c = ConfigCodec.decode(mapOf(ConfigCodec.K_MARGIN to 7, ConfigCodec.K_THEME to 1L, ConfigCodec.K_LARGE_TEXT to true))
        assertEquals(7, c.safeMargin)
        assertEquals(1, c.theme)
        assertTrue(c.largeText)
    }

    @Test fun favoritesCappedAtFour() {
        val many = (1..6).joinToString("|") { "com.p$it/com.p$it.Main" }
        assertEquals(4, ConfigCodec.decode(mapOf(ConfigCodec.K_FAVORITES to many)).favorites.size)
    }

    @Test fun marginBoundsAccepted() {
        assertEquals(0, ConfigCodec.decode(mapOf(ConfigCodec.K_MARGIN to "0")).safeMargin)
        assertEquals(10, ConfigCodec.decode(mapOf(ConfigCodec.K_MARGIN to "10")).safeMargin)
        assertEquals(5, ConfigCodec.decode(mapOf(ConfigCodec.K_MARGIN to "11")).safeMargin)
    }
}

class FavoritesTest {
    private val t = (0..4).map { AppTarget("com.p$it", "com.p$it.Main") }

    @Test fun addSkipsDuplicatesAndFull() {
        var l = emptyList<AppTarget>()
        for (i in 0..4) l = Favorites.add(l, t[i])
        assertEquals(t.take(4), l)
        assertEquals(l, Favorites.add(l, t[0]))
    }

    @Test fun removeOutOfRangeIsNoop() {
        val l = listOf(t[0], t[1])
        assertEquals(l, Favorites.remove(l, 5))
        assertEquals(listOf(t[1]), Favorites.remove(l, 0))
    }

    @Test fun moveClamps() {
        val l = listOf(t[0], t[1], t[2])
        assertEquals(listOf(t[1], t[0], t[2]) to 1, Favorites.move(l, 0, 1))
        assertEquals(l to 0, Favorites.move(l, 0, -1))
        assertEquals(listOf(t[0], t[2], t[1]) to 2, Favorites.move(l, 1, 9))
    }

    @Test fun putReplacesAndRemovesDuplicate() {
        val l = listOf(t[0], t[1], t[2])
        assertEquals(listOf(t[3], t[1], t[2]), Favorites.put(l, 0, t[3]))
        assertEquals(listOf(t[2], t[1]), Favorites.put(l, 0, t[2]))
        assertEquals(listOf(t[0], t[1], t[2], t[3]), Favorites.put(l, 3, t[3]))
    }
}

class LaunchGuardTest {
    @Test fun blocksRepeatsInsideWindow() {
        var now = 1000L
        val g = LaunchGuard({ now }, 1200)
        assertTrue(g.tryAcquire())
        now += 100; assertFalse(g.tryAcquire())
        now += 1099; assertFalse(g.tryAcquire())
        now += 1; assertTrue(g.tryAcquire())
    }

    @Test fun clockGoingBackwardsDoesNotLock() {
        var now = 5000L
        val g = LaunchGuard({ now }, 1200)
        assertTrue(g.tryAcquire())
        now = 10; assertTrue(g.tryAcquire())
    }
}

class FocusKeysTest {
    @Test fun restoresSavedWhenAvailable() {
        assertEquals("fav2", FocusKeys.restore("fav2", setOf("karaoke", "fav2")))
    }

    @Test fun fallsBackToKaraoke() {
        assertEquals(FocusKeys.KARAOKE, FocusKeys.restore("fav3", setOf("karaoke", "youtube")))
        assertEquals(FocusKeys.KARAOKE, FocusKeys.restore(null, setOf("youtube", "karaoke")))
    }

    @Test fun knownKeys() {
        assertTrue(FocusKeys.isKnown("fav0")); assertFalse(FocusKeys.isKnown("fav4"))
    }
}

class YouTubeAppsTest {
    @Test fun prefersTvApps() {
        assertEquals("com.liskovsoft.smarttubetv", YouTubeApps.pick(setOf("com.google.android.youtube", "com.liskovsoft.smarttubetv")))
        assertNull(YouTubeApps.pick(setOf("com.other")))
    }
}

class ViTextTest {
    @Test fun formats() {
        assertEquals("07:05", ViText.time(7, 5))
        assertEquals("Thứ Bảy, 24/10/2026", ViText.date(7, 24, 10, 2026))
        assertEquals("Chủ nhật, 01/02/2026", ViText.date(1, 1, 2, 2026))
        assertEquals("Chào buổi tối", ViText.greeting(23))
        assertEquals("512 MB", ViText.bytes(512L * 1024 * 1024))
        assertEquals("1.50 GB", ViText.bytes(1536L * 1024 * 1024))
    }
}
