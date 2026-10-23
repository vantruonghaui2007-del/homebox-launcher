package vn.homekara.launcher.core

/** Một Activity có thể mở: gói + tên lớp đầy đủ. Lưu dạng "gói/lớp". */
data class AppTarget(val packageName: String, val activityName: String) {

    fun encode(): String = "$packageName/$activityName"

    companion object {
        private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val CLASS = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")

        /** Tạo đích hợp lệ; tên lớp dạng ".Main" được ghép với gói. Sai định dạng → null. */
        fun of(packageName: String?, activityName: String?): AppTarget? {
            val pkg = packageName?.trim() ?: return null
            var cls = activityName?.trim() ?: return null
            if (!PACKAGE.matches(pkg) || cls.isEmpty()) return null
            if (cls.startsWith(".")) cls = pkg + cls
            if (!CLASS.matches(cls)) return null
            return AppTarget(pkg, cls)
        }

        fun decode(value: String?): AppTarget? {
            if (value == null) return null
            val slash = value.indexOf('/')
            if (slash <= 0 || slash == value.length - 1) return null
            return of(value.substring(0, slash), value.substring(slash + 1))
        }
    }
}

/** Một mục trong danh sách ứng dụng. */
data class AppEntry(val target: AppTarget, val label: String)

object AppEntries {
    /** Loại trùng theo Activity (giữ mục đầu tiên) rồi sắp theo tên hiển thị. */
    fun dedupeAndSort(entries: List<AppEntry>, labelOrder: Comparator<String>): List<AppEntry> {
        val seen = HashSet<AppTarget>()
        val unique = ArrayList<AppEntry>(entries.size)
        for (e in entries) if (seen.add(e.target)) unique.add(e)
        return unique.sortedWith(Comparator { a, b ->
            val byLabel = labelOrder.compare(a.label, b.label)
            if (byLabel != 0) byLabel else a.target.encode().compareTo(b.target.encode())
        })
    }
}
