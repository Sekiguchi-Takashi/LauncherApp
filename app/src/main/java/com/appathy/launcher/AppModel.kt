package com.appathy.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap
)

data class HomeItem(
    val page: Int,
    val row: Int,
    val col: Int,
    val packageName: String,
    val activityName: String
)

fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bmp.asImageBitmap()
}

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
                icon = it.loadIcon(pm).toImageBitmap(128)
            )
        }
        .sortedBy { it.label.lowercase() }
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

    fun setPages(context: Context, v: Int) = prefs(context).edit().putInt("pages", v).apply()
    fun setRows(context: Context, v: Int) = prefs(context).edit().putInt("rows", v).apply()
    fun setCols(context: Context, v: Int) = prefs(context).edit().putInt("cols", v).apply()
    fun setSwitchIcon(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean("switch_icon", v).apply()
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
