package com.appathy.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val category: Int
)

data class HomeItem(
    val page: Int,
    val row: Int,
    val col: Int,
    val packageName: String,
    val activityName: String
)

fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            AppEntry(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                activityName = it.activityInfo.name,
                category = it.activityInfo.applicationInfo.category
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun categoryLabel(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "ゲーム"
    ApplicationInfo.CATEGORY_AUDIO -> "ミュージック"
    ApplicationInfo.CATEGORY_VIDEO -> "ビデオ"
    ApplicationInfo.CATEGORY_IMAGE -> "写真"
    ApplicationInfo.CATEGORY_SOCIAL -> "ソーシャル"
    ApplicationInfo.CATEGORY_NEWS -> "ニュース"
    ApplicationInfo.CATEGORY_MAPS -> "マップ"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "仕事効率化"
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> "ユーティリティ"
    else -> "その他"
}

object LibraryApps {
    private const val PREF = "launcher_prefs"
    private const val KEY = "library_only"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")!!.split(";").filter { it.isNotBlank() }.toSet()

    fun save(context: Context, keys: Set<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, keys.joinToString(";")).apply()
    }
}

fun ensureSettingsTile(
    items: List<HomeItem>,
    widgets: List<WidgetItem>,
    pages: Int,
    rows: Int,
    cols: Int
): List<HomeItem> {
    if (items.any { it.packageName == SETTINGS_PKG }) return items
    for (p in 0 until pages) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val taken = items.any { it.page == p && it.row == r && it.col == c } ||
                    cellCoveredByWidget(widgets, p, r, c)
                if (!taken) return items + HomeItem(p, r, c, SETTINGS_PKG, "settings")
            }
        }
    }
    return items
}

object SettingsTile {
    private const val PREF = "launcher_prefs"
    private const val KEY = "settings_tile_hidden"

    fun hidden(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setHidden(context: Context, v: Boolean) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, v).apply()
}

fun pagesNeeded(items: List<HomeItem>, minPages: Int): Int {
    val maxPage = items.maxOfOrNull { it.page } ?: 0
    return maxOf(minPages, maxPage + 1)
}

fun autoPlace(
    apps: List<AppEntry>,
    items: List<HomeItem>,
    folders: List<FolderEntry>,
    widgets: List<WidgetItem>,
    libraryOnly: Set<String>,
    dockKeys: Set<String>,
    minPages: Int,
    rows: Int,
    cols: Int
): List<HomeItem> {
    val inFolders = folders.flatMap { it.apps }.toSet()
    val placed = items
        .filter { it.packageName != FOLDER_PKG && it.packageName != SETTINGS_PKG }
        .map { appKey(it) }.toSet()
    val missing = apps
        .map { appKey(it) }
        .filter { it !in placed && it !in inFolders && it !in libraryOnly && it !in dockKeys }

    if (missing.isEmpty()) return items

    var result = items
    var page = 0
    var row = 0
    var col = 0
    for (key in missing) {
        var found = false
        while (!found) {
            val taken = result.any { it.page == page && it.row == row && it.col == col } ||
                cellCoveredByWidget(widgets, page, row, col)
            if (!taken) {
                val placedItem = keyToItem(key, page, row, col)
                if (placedItem != null) result = result + placedItem
                found = true
            }
            col += 1
            if (col >= cols) {
                col = 0
                row += 1
            }
            if (row >= rows) {
                row = 0
                page += 1
            }
        }
    }
    return result
}

object Favorites {
    private const val PREF = "launcher_prefs"
    private const val KEY = "favorites"

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")!!.split(",").filter { it.isNotBlank() }

    fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, list.joinToString(",")).apply()
    }
}

object Workspace {
    private const val PREF = "launcher_prefs"
    private const val KEY = "home_items"

    fun load(context: Context): List<HomeItem> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")!!.split(";").filter { it.isNotBlank() }.mapNotNull {
                val f = it.split("|")
                if (f.size == 5) {
                    val page = f[0].toIntOrNull()
                    val row = f[1].toIntOrNull()
                    val col = f[2].toIntOrNull()
                    if (page != null && row != null && col != null) {
                        HomeItem(page, row, col, f[3], f[4])
                    } else null
                } else null
            }

    fun save(context: Context, list: List<HomeItem>) {
        val encoded = list.joinToString(";") {
            listOf(it.page, it.row, it.col, it.packageName, it.activityName).joinToString("|")
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, encoded).apply()
    }
}

object LauncherSettings {
    private const val PREF = "launcher_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun pages(context: Context): Int = prefs(context).getInt("pages", 3)
    fun rows(context: Context): Int = prefs(context).getInt("rows", 5)
    fun cols(context: Context): Int = prefs(context).getInt("cols", 4)
    fun switchIcon(context: Context): Boolean = prefs(context).getBoolean("switch_icon", true)
    fun iconStyle(context: Context): IconStyle =
        IconStyle.from(prefs(context).getString("icon_style", null))

    fun setPages(context: Context, v: Int) = prefs(context).edit().putInt("pages", v).apply()
    fun setRows(context: Context, v: Int) = prefs(context).edit().putInt("rows", v).apply()
    fun setCols(context: Context, v: Int) = prefs(context).edit().putInt("cols", v).apply()
    fun setSwitchIcon(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean("switch_icon", v).apply()
    fun setIconStyle(context: Context, v: IconStyle) =
        prefs(context).edit().putString("icon_style", v.name).apply()
}

data class WidgetItem(
    val page: Int,
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val widgetId: Int
)

object WidgetData {
    private const val PREF = "launcher_prefs"
    private const val KEY = "widget_items"

    fun load(context: Context): List<WidgetItem> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")!!.split(";").filter { it.isNotBlank() }.mapNotNull {
                val f = it.split("|").mapNotNull { s -> s.toIntOrNull() }
                if (f.size == 6) WidgetItem(f[0], f[1], f[2], f[3], f[4], f[5]) else null
            }

    fun save(context: Context, list: List<WidgetItem>) {
        val encoded = list.joinToString(";") {
            listOf(it.page, it.row, it.col, it.rowSpan, it.colSpan, it.widgetId).joinToString("|")
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, encoded).apply()
    }
}

fun cellCoveredByWidget(widgets: List<WidgetItem>, page: Int, row: Int, col: Int): Boolean =
    widgets.any {
        it.page == page &&
            row >= it.row && row < it.row + it.rowSpan &&
            col >= it.col && col < it.col + it.colSpan
    }

fun freeRegion(
    items: List<HomeItem>,
    widgets: List<WidgetItem>,
    pages: Int,
    rows: Int,
    cols: Int,
    rowSpan: Int,
    colSpan: Int
): Triple<Int, Int, Int>? {
    for (p in 0 until pages) {
        for (r in 0..(rows - rowSpan)) {
            for (c in 0..(cols - colSpan)) {
                var free = true
                for (rr in r until r + rowSpan) {
                    for (cc in c until c + colSpan) {
                        if (items.any { it.page == p && it.row == rr && it.col == cc } ||
                            cellCoveredByWidget(widgets, p, rr, cc)
                        ) {
                            free = false
                        }
                    }
                }
                if (free) return Triple(p, r, c)
            }
        }
    }
    return null
}

const val FOLDER_PKG = "__folder__"
const val SETTINGS_PKG = "__settings__"

data class FolderEntry(
    val id: String,
    val name: String,
    val apps: List<String>
)

object Folders {
    private const val PREF = "launcher_prefs"
    private const val KEY = "folders"

    fun load(context: Context): List<FolderEntry> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")!!.split(";").filter { it.isNotBlank() }.mapNotNull {
                val f = it.split("|")
                if (f.size == 3) {
                    FolderEntry(f[0], f[1], f[2].split(",").filter { s -> s.isNotBlank() })
                } else null
            }

    fun save(context: Context, list: List<FolderEntry>) {
        val encoded = list.joinToString(";") {
            it.id + "|" + sanitize(it.name) + "|" + it.apps.joinToString(",")
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, encoded).apply()
    }

    fun sanitize(name: String): String {
        val cleaned = name.replace("|", "-").replace(";", "-").replace(",", "-").trim()
        return if (cleaned.isBlank()) "フォルダ" else cleaned
    }

    fun newId(): String = "f" + System.currentTimeMillis()
}

fun appKey(app: AppEntry): String = app.packageName + "/" + app.activityName

fun appKey(item: HomeItem): String = item.packageName + "/" + item.activityName

fun keyToItem(key: String, page: Int, row: Int, col: Int): HomeItem? {
    val i = key.lastIndexOf('/')
    if (i <= 0) return null
    return HomeItem(page, row, col, key.substring(0, i), key.substring(i + 1))
}

data class DropResult(
    val items: List<HomeItem>,
    val folders: List<FolderEntry>
)

fun dropOnto(
    items: List<HomeItem>,
    folders: List<FolderEntry>,
    source: HomeItem,
    row: Int,
    col: Int
): DropResult {
    if (source.row == row && source.col == col) return DropResult(items, folders)
    val target = items.find { it.page == source.page && it.row == row && it.col == col }

    if (target == null) {
        return DropResult(placeItem(items, source, source.page, row, col), folders)
    }

    val sourceIsFolder = source.packageName == FOLDER_PKG
    val targetIsFolder = target.packageName == FOLDER_PKG

    if (sourceIsFolder && targetIsFolder) {
        val sf = folders.find { it.id == source.activityName }
        val tf = folders.find { it.id == target.activityName }
        if (sf == null || tf == null) return DropResult(items, folders)
        val merged = tf.copy(apps = (tf.apps + sf.apps).distinct())
        return DropResult(
            items - source,
            folders.filter { it.id != sf.id }.map { if (it.id == tf.id) merged else it }
        )
    }

    if (targetIsFolder) {
        val tf = folders.find { it.id == target.activityName } ?: return DropResult(items, folders)
        val updated = tf.copy(apps = (tf.apps + appKey(source)).distinct())
        return DropResult(
            items - source,
            folders.map { if (it.id == tf.id) updated else it }
        )
    }

    if (sourceIsFolder) {
        val sf = folders.find { it.id == source.activityName } ?: return DropResult(items, folders)
        val updated = sf.copy(apps = (sf.apps + appKey(target)).distinct())
        return DropResult(
            (items - target - source) + source.copy(row = row, col = col),
            folders.map { if (it.id == sf.id) updated else it }
        )
    }

    val folder = FolderEntry(
        id = Folders.newId(),
        name = "フォルダ",
        apps = listOf(appKey(target), appKey(source))
    )
    return DropResult(
        (items - target - source) + HomeItem(source.page, row, col, FOLDER_PKG, folder.id),
        folders + folder
    )
}

fun removeFromFolder(
    items: List<HomeItem>,
    folders: List<FolderEntry>,
    folderId: String,
    key: String,
    pages: Int,
    rows: Int,
    cols: Int,
    toHome: Boolean
): DropResult {
    val folder = folders.find { it.id == folderId } ?: return DropResult(items, folders)
    val remaining = folder.apps.filter { it != key }
    val holder = items.find { it.packageName == FOLDER_PKG && it.activityName == folderId }

    var newItems = items
    if (toHome) {
        val cell = firstFreeCell(newItems, pages, rows, cols, holder?.page ?: 0)
        val placed = cell?.let { keyToItem(key, it.first, it.second, it.third) }
        if (placed != null) newItems = newItems + placed
    }

    if (remaining.size <= 1 && holder != null) {
        newItems = newItems - holder
        val last = remaining.firstOrNull()
        if (last != null) {
            val restored = keyToItem(last, holder.page, holder.row, holder.col)
            if (restored != null) newItems = newItems + restored
        }
        return DropResult(newItems, folders.filter { it.id != folderId })
    }

    return DropResult(
        newItems,
        folders.map { if (it.id == folderId) it.copy(apps = remaining) else it }
    )
}

fun placeItem(
    items: List<HomeItem>,
    item: HomeItem,
    page: Int,
    row: Int,
    col: Int
): List<HomeItem> {
    if (item.page == page && item.row == row && item.col == col) return items
    val target = items.find { it.page == page && it.row == row && it.col == col }
    return items.map {
        when (it) {
            item -> item.copy(page = page, row = row, col = col)
            target -> target.copy(page = item.page, row = item.row, col = item.col)
            else -> it
        }
    }
}

fun freeCellOnPage(
    items: List<HomeItem>,
    page: Int,
    rows: Int,
    cols: Int
): Pair<Int, Int>? {
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (items.none { it.page == page && it.row == r && it.col == c }) {
                return Pair(r, c)
            }
        }
    }
    return null
}

fun firstFreeCell(
    items: List<HomeItem>,
    pages: Int,
    rows: Int,
    cols: Int,
    preferredPage: Int
): Triple<Int, Int, Int>? {
    val order = (listOf(preferredPage) + (0 until pages)).distinct().filter { it in 0 until pages }
    for (p in order) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (items.none { it.page == p && it.row == r && it.col == c }) {
                    return Triple(p, r, c)
                }
            }
        }
    }
    return null
}
