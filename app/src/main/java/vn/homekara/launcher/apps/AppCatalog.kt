package vn.homekara.launcher.apps

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.SystemClock
import vn.homekara.launcher.core.AppEntries
import vn.homekara.launcher.core.AppEntry
import vn.homekara.launcher.core.AppTarget
import vn.homekara.launcher.core.LaunchGuard
import java.text.Collator
import java.util.Locale

enum class TargetState { OK, MISSING, DISABLED }

/** Kết quả tra một ô: ứng dụng nào, tên gì, còn mở được không. */
data class Resolved(
    val packageName: String?,
    val target: AppTarget?,
    val label: String?,
    val state: TargetState,
    /** true khi ô đang dùng lựa chọn tự động (chưa được người dùng gán). */
    val auto: Boolean = false,
)

object AppCatalog {
    /** Hằng số API 21; dùng chuỗi trực tiếp để chạy được trên Android 4.4. */
    const val CATEGORY_LEANBACK = "android.intent.category.LEANBACK_LAUNCHER"
    const val KARAOKE_PACKAGE = "vn.homekara.box"

    /** Danh sách Activity mở được (launcher thường + TV), đã loại trùng và sắp xếp. Gọi ngoài luồng UI. */
    fun listApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val raw = ArrayList<AppEntry>()
        for (category in arrayOf(Intent.CATEGORY_LAUNCHER, CATEGORY_LEANBACK)) {
            for (ri in query(pm, Intent(Intent.ACTION_MAIN).addCategory(category))) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                val t = AppTarget.of(ai.packageName, ai.name) ?: continue
                raw.add(AppEntry(t, labelOf(ri, pm, ai.packageName)))
            }
        }
        val collator = Collator.getInstance(Locale("vi", "VN")).apply { strength = Collator.PRIMARY }
        return AppEntries.dedupeAndSort(raw, Comparator { a, b -> collator.compare(a, b) })
    }

    /** Activity mở được đầu tiên của một gói (ưu tiên mục launcher thường, sau đó mục TV). */
    fun findLaunchTarget(ctx: Context, packageName: String): AppTarget? {
        val pm = ctx.packageManager
        for (category in arrayOf(Intent.CATEGORY_LAUNCHER, CATEGORY_LEANBACK)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(packageName)
            for (ri in query(pm, intent)) {
                val ai = ri.activityInfo ?: continue
                AppTarget.of(ai.packageName, ai.name)?.let { return it }
            }
        }
        return null
    }

    fun isInstalled(ctx: Context, packageName: String): Boolean = appEnabled(ctx, packageName) != null

    /** null = chưa cài; true/false = đã cài, đang bật/tắt. */
    @Suppress("DEPRECATION")
    fun appEnabled(ctx: Context, packageName: String): Boolean? = try {
        ctx.packageManager.getApplicationInfo(packageName, PackageManager.GET_DISABLED_COMPONENTS).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    fun state(ctx: Context, t: AppTarget): TargetState {
        val pm = ctx.packageManager
        val cn = ComponentName(t.packageName, t.activityName)
        val ai = try {
            pm.getActivityInfo(cn, PackageManager.GET_DISABLED_COMPONENTS)
        } catch (e: PackageManager.NameNotFoundException) {
            return TargetState.MISSING
        } catch (e: Exception) {
            return TargetState.MISSING
        }
        if (ai.applicationInfo?.enabled == false) return TargetState.DISABLED
        val setting = try {
            pm.getComponentEnabledSetting(cn)
        } catch (e: Exception) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        val enabled = when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> ai.enabled
            else -> false
        }
        if (!enabled) return TargetState.DISABLED
        if (!ai.exported) return TargetState.MISSING
        return TargetState.OK
    }

    fun label(ctx: Context, t: AppTarget): String? = try {
        val pm = ctx.packageManager
        pm.getActivityInfo(ComponentName(t.packageName, t.activityName), 0).loadLabel(pm)?.toString()
    } catch (e: Exception) {
        null
    }

    fun appLabel(ctx: Context, packageName: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm)?.toString()
    } catch (e: Exception) {
        null
    }

    /**
     * Tra một đích đã lưu. Nếu Activity đổi tên sau khi cập nhật nhưng gói vẫn còn,
     * tự chuyển sang Activity mở được mới của gói đó.
     */
    fun resolveStored(ctx: Context, t: AppTarget): Resolved {
        return when (state(ctx, t)) {
            TargetState.OK -> Resolved(t.packageName, t, label(ctx, t) ?: t.packageName, TargetState.OK)
            TargetState.DISABLED -> Resolved(t.packageName, t, appLabel(ctx, t.packageName) ?: t.packageName, TargetState.DISABLED)
            TargetState.MISSING -> resolvePackage(ctx, t.packageName, auto = false).let {
                if (it.state == TargetState.MISSING) it.copy(target = t) else it
            }
        }
    }

    /** Tra theo tên gói (dùng cho ô Karaoke mặc định và YouTube tự nhận diện). */
    fun resolvePackage(ctx: Context, packageName: String, auto: Boolean): Resolved {
        val enabled = appEnabled(ctx, packageName)
            ?: return Resolved(packageName, null, null, TargetState.MISSING, auto)
        val label = appLabel(ctx, packageName) ?: packageName
        if (!enabled) return Resolved(packageName, null, label, TargetState.DISABLED, auto)
        val t = findLaunchTarget(ctx, packageName)
            ?: return Resolved(packageName, null, label, TargetState.MISSING, auto)
        return Resolved(packageName, t, this.label(ctx, t) ?: label, TargetState.OK, auto)
    }

    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> = try {
        pm.queryIntentActivities(intent, 0) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun labelOf(ri: ResolveInfo, pm: PackageManager, fallback: String): String = try {
        ri.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
    } catch (e: Exception) {
        fallback
    }
}

enum class LaunchResult { OK, NOT_INSTALLED, DISABLED, BLOCKED, IGNORED }

/** Mở ứng dụng giống launcher gốc: đưa task có sẵn lên trước, không tạo bản mới. */
object AppLauncher {
    private val guard = LaunchGuard({ SystemClock.elapsedRealtime() })

    fun open(activity: Activity, target: AppTarget?): LaunchResult {
        if (target == null) return LaunchResult.NOT_INSTALLED
        when (AppCatalog.state(activity, target)) {
            TargetState.MISSING -> return LaunchResult.NOT_INSTALLED
            TargetState.DISABLED -> return LaunchResult.DISABLED
            TargetState.OK -> Unit
        }
        if (!guard.tryAcquire()) return LaunchResult.IGNORED
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(target.packageName, target.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            activity.startActivity(intent)
            LaunchResult.OK
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NOT_INSTALLED
        } catch (e: SecurityException) {
            LaunchResult.BLOCKED
        } catch (e: Exception) {
            LaunchResult.BLOCKED
        }
    }

    fun message(result: LaunchResult): String? = when (result) {
        LaunchResult.OK, LaunchResult.IGNORED -> null
        LaunchResult.NOT_INSTALLED -> "Không tìm thấy ứng dụng. Có thể ứng dụng đã bị gỡ."
        LaunchResult.DISABLED -> "Ứng dụng đang bị tắt trong Cài đặt."
        LaunchResult.BLOCKED -> "Hệ thống không cho mở ứng dụng này."
    }
}
