package com.appathy.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    lateinit var appWidgetHost: AppWidgetHost
    lateinit var appWidgetManager: AppWidgetManager

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadAfterPackageChange()
        }
    }

    private fun reload() {
        thread {
            val list = loadApps(this)
            runOnUiThread { apps.value = list }
        }
    }

    private fun reloadAfterPackageChange() {
        IconCache.clear()
        reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetHost = AppWidgetHost(this, 1024)
        appWidgetManager = AppWidgetManager.getInstance(this)
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        setContent {
            LauncherRoot(apps.value, appWidgetHost, appWidgetManager)
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
    }

    override fun onStop() {
        runCatching { appWidgetHost.stopListening() }
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}

val LocalIconStyle = staticCompositionLocalOf { IconStyle.DEFAULT }

@Composable
fun LauncherRoot(
    apps: List<AppEntry>,
    host: AppWidgetHost,
    awm: AppWidgetManager
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val homeRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(BackupData.serialize(context).toByteArray())
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "書き出しました" else "書き出しに失敗しました",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            val ok = text != null && BackupData.restore(context, text)
            Toast.makeText(
                context,
                if (ok) "復元しました。再起動します" else "このファイルは読み込めません",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) {
                IconCache.clear()
                (context as? android.app.Activity)?.recreate()
            }
        }
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var searchOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var settingsAppOpen by remember { mutableStateOf(false) }
    var settingsTileHidden by remember { mutableStateOf(SettingsTile.hidden(context)) }
    var hiddenApps by remember { mutableStateOf(HiddenApps.load(context)) }
    var appListOpen by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    var launcherSwitchOpen by remember { mutableStateOf(false) }
    var notificationsOpen by remember { mutableStateOf(false) }
    var quickPanelOpen by remember { mutableStateOf(false) }
    var homePrompt by remember { mutableStateOf(LauncherSettings.homePrompt(context)) }
    var askHome by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (homePrompt && !isDefaultHome(context)) askHome = true
    }
    var libraryOnly by remember { mutableStateOf(LibraryApps.load(context)) }
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    var homeItems by remember { mutableStateOf(Workspace.load(context)) }
    var widgets by remember { mutableStateOf(WidgetData.load(context)) }
    var folders by remember { mutableStateOf(Folders.load(context)) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf(LauncherSettings.pages(context)) }
    var rows by remember { mutableStateOf(LauncherSettings.rows(context)) }
    var cols by remember { mutableStateOf(LauncherSettings.cols(context)) }
    var iconStyle by remember { mutableStateOf(LauncherSettings.iconStyle(context)) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var editWidgetId by remember { mutableStateOf<Int?>(null) }
    var pendingWidgetId by remember { mutableStateOf(-1) }
    var pendingProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    fun saveHome(list: List<HomeItem>) {
        homeItems = list
        Workspace.save(context, list)
    }

    fun saveWidgets(list: List<WidgetItem>) {
        widgets = list
        WidgetData.save(context, list)
    }

    fun saveLibrary(keys: Set<String>) {
        libraryOnly = keys
        LibraryApps.save(context, keys)
    }

    fun saveFolders(list: List<FolderEntry>) {
        folders = list
        Folders.save(context, list)
    }

    fun applyDrop(result: DropResult) {
        saveHome(result.items)
        saveFolders(result.folders)
    }

    fun toggleFavorite(pkg: String) {
        favorites = if (favorites.contains(pkg)) favorites - pkg else favorites + pkg
        Favorites.save(context, favorites)
    }

    fun addWidgetToWorkspace(id: Int, provider: AppWidgetProviderInfo) {
        val cellWdp = screenWidthDp.toFloat() / cols
        val colSpan = ceil(provider.minWidth / cellWdp).toInt().coerceIn(1, cols)
        val rowSpan = ceil(provider.minHeight / 96f).toInt().coerceIn(1, rows)
        val region = freeRegion(homeItems, widgets, pages, rows, cols, rowSpan, colSpan)
        if (region != null) {
            saveWidgets(
                widgets + WidgetItem(
                    region.first, region.second, region.third,
                    rowSpan, colSpan, id
                )
            )
        } else {
            Toast.makeText(context, "空きスペースがありません", Toast.LENGTH_SHORT).show()
            host.deleteAppWidgetId(id)
        }
    }

    val configLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        val provider = pendingProvider
        if (id != -1 && provider != null) {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                addWidgetToWorkspace(id, provider)
            } else {
                host.deleteAppWidgetId(id)
            }
        }
        pendingWidgetId = -1
        pendingProvider = null
    }

    fun configureOrAdd(id: Int, provider: AppWidgetProviderInfo) {
        if (provider.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(provider.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val launched = runCatching { configLauncher.launch(intent) }.isSuccess
            if (!launched) {
                addWidgetToWorkspace(id, provider)
                pendingWidgetId = -1
                pendingProvider = null
            }
        } else {
            addWidgetToWorkspace(id, provider)
            pendingWidgetId = -1
            pendingProvider = null
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        val provider = pendingProvider
        if (id != -1 && provider != null) {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                configureOrAdd(id, provider)
            } else {
                host.deleteAppWidgetId(id)
                pendingWidgetId = -1
                pendingProvider = null
            }
        }
    }

    fun startAddWidget(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingWidgetId = id
        pendingProvider = provider
        val bound = runCatching {
            awm.bindAppWidgetIdIfAllowed(id, provider.provider)
        }.getOrDefault(false)
        if (bound) {
            configureOrAdd(id, provider)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            bindLauncher.launch(intent)
        }
    }

    val visibleApps = apps.filter { appKey(it) !in hiddenApps }
    val favApps = favorites.mapNotNull { pkg -> visibleApps.find { it.packageName == pkg } }
    val dockKeys = favApps.map { appKey(it) }.toSet()

    fun hideApp(key: String) {
        hiddenApps = hiddenApps + key
        HiddenApps.save(context, hiddenApps)
        saveHome(homeItems.filter { it.packageName == FOLDER_PKG ||
            it.packageName == SETTINGS_PKG || appKey(it) != key })
        val cleaned = folders.map { f -> f.copy(apps = f.apps.filter { it != key }) }
        if (cleaned != folders) saveFolders(cleaned)
        val pkg = key.substringBeforeLast('/')
        if (favorites.contains(pkg)) {
            favorites = favorites - pkg
            Favorites.save(context, favorites)
        }
    }

    fun unhideApp(key: String) {
        hiddenApps = hiddenApps - key
        HiddenApps.save(context, hiddenApps)
    }

    LaunchedEffect(visibleApps, libraryOnly, favorites, rows, cols, settingsTileHidden) {
        if (visibleApps.isNotEmpty()) {
            var placed = autoPlace(
                visibleApps, homeItems, folders, widgets, libraryOnly, dockKeys, pages, rows, cols
            )
            if (!settingsTileHidden) {
                placed = ensureSettingsTile(
                    placed, widgets, pagesNeeded(placed, pages), rows, cols
                )
            }
            if (placed !== homeItems) saveHome(placed)
        }
    }

    val contentPages = pagesNeeded(homeItems, pages)
    val totalPages = contentPages + 1

    val folderOpen = openFolderId != null

    CompositionLocalProvider(LocalIconStyle provides iconStyle) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (folderOpen) Modifier.blur(20.dp) else Modifier)
        ) {
        HomeScreen(
            apps = visibleApps,
            homeItems = homeItems,
            widgets = widgets,
            favApps = favApps,
            pages = contentPages,
            totalPages = totalPages,
            rows = rows,
            cols = cols,
            libraryOnly = libraryOnly,
            editMode = editMode,
            onEnterEdit = { editMode = true },
            onExitEdit = { editMode = false },
            onMoveToLibrary = { key -> saveLibrary(libraryOnly + key) },
            onRestoreFromLibrary = { key -> saveLibrary(libraryOnly - key) },
            host = host,
            awm = awm,
            onOpenQuickPanel = { quickPanelOpen = true },
            onLaunch = { launchApp(context, it, rootView) },
            onRemoveItem = { item ->
                saveHome(homeItems - item)
                if (item.packageName == FOLDER_PKG) {
                    saveFolders(folders.filter { it.id != item.activityName })
                }
                if (item.packageName == SETTINGS_PKG) {
                    settingsTileHidden = true
                    SettingsTile.setHidden(context, true)
                }
            },
            onMoveItem = { item, r, c ->
                if (!cellCoveredByWidget(widgets, item.page, r, c)) {
                    applyDrop(dropOnto(homeItems, folders, item, r, c))
                }
            },
            folders = folders,
            onOpenFolder = { openFolderId = it },
            onMoveToPage = { item, delta ->
                val target = item.page + delta
                if (target in 0 until contentPages) {
                    val cell = freeCellOnPage(homeItems, target, rows, cols)
                    if (cell != null && !cellCoveredByWidget(widgets, target, cell.first, cell.second)) {
                        saveHome(placeItem(homeItems, item, target, cell.first, cell.second))
                    }
                }
            },
            onAppInfo = { pkg -> openAppInfo(context, pkg) },
            onToggleFavorite = { pkg -> toggleFavorite(pkg) },
            onOpenSettings = { settingsAppOpen = true },
            onOpenSettingsApp = { settingsAppOpen = true },
            onEditWidget = { editWidgetId = it.widgetId },
            onDeleteWidget = { w ->
                runCatching { host.deleteAppWidgetId(w.widgetId) }
                saveWidgets(widgets - w)
            },
            onWidgetToPage = { w, delta ->
                val target = w.page + delta
                if (target in 0 until contentPages) {
                    saveWidgets(widgets.map { if (it == w) it.copy(page = target) else it })
                }
            },
            onMoveWidget = { w, r, c ->
                saveWidgets(widgets.map { if (it == w) it.copy(row = r, col = c) else it })
            },
            onResizeWidget = { w, rs, cs ->
                saveWidgets(
                    widgets.map {
                        if (it == w) it.copy(
                            rowSpan = rs.coerceIn(1, rows - it.row),
                            colSpan = cs.coerceIn(1, cols - it.col)
                        ) else it
                    }
                )
            },
        )
        }
        AnimatedVisibility(
            visible = searchOpen,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            SpotlightSearch(
                apps = visibleApps,
                onLaunch = {
                    launchApp(context, it, rootView)
                    searchOpen = false
                },
                onAppInfo = { pkg -> openAppInfo(context, pkg) },
                onWebSearch = { q ->
                    searchOpen = false
                    webSearch(context, q)
                },
                onDismiss = { searchOpen = false }
            )
        }

        val openFolder = folders.find { it.id == openFolderId }
        if (openFolder != null) {
            FolderOverlay(
                folder = openFolder,
                apps = visibleApps,
                onLaunch = {
                    launchApp(context, it)
                    openFolderId = null
                },
                onRename = { name ->
                    saveFolders(
                        folders.map {
                            if (it.id == openFolder.id) it.copy(name = Folders.sanitize(name)) else it
                        }
                    )
                },
                onTakeOut = { key ->
                    val result = removeFromFolder(
                        homeItems, folders, openFolder.id, key, pages, rows, cols, true
                    )
                    applyDrop(result)
                    if (result.folders.none { it.id == openFolder.id }) openFolderId = null
                },
                onRemove = { key ->
                    val result = removeFromFolder(
                        homeItems, folders, openFolder.id, key, pages, rows, cols, false
                    )
                    applyDrop(result)
                    if (result.folders.none { it.id == openFolder.id }) openFolderId = null
                },
                onDismiss = { openFolderId = null }
            )
        }
    }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = editMode && !searchOpen) { editMode = false }
    BackHandler(enabled = folderOpen) { openFolderId = null }
    BackHandler(enabled = settingsAppOpen) { settingsAppOpen = false }
    BackHandler(enabled = appListOpen) { appListOpen = false }
    BackHandler(enabled = controlOpen) { controlOpen = false }
    BackHandler(enabled = launcherSwitchOpen) { launcherSwitchOpen = false }
    BackHandler(enabled = notificationsOpen) { notificationsOpen = false }
    BackHandler(enabled = quickPanelOpen) { quickPanelOpen = false }

    if (settingsAppOpen) {
        SettingsApp(
            pages = pages,
            rows = rows,
            cols = cols,
            iconStyle = iconStyle,
            libraryOnly = libraryOnly,
            settingsTileHidden = settingsTileHidden,
            hiddenCount = hiddenApps.size,
            onOpenAppList = {
                settingsAppOpen = false
                appListOpen = true
            },
            apps = apps,
            isDefaultHomeNow = isDefaultHome(context),
            onPages = { pages = it; LauncherSettings.setPages(context, it) },
            onRows = { rows = it; LauncherSettings.setRows(context, it) },
            onCols = { cols = it; LauncherSettings.setCols(context, it) },
            onIconStyle = {
                iconStyle = it
                LauncherSettings.setIconStyle(context, it)
            },
            onRestoreFromLibrary = { key -> saveLibrary(libraryOnly - key) },
            onRestoreSettingsTile = {
                settingsTileHidden = false
                SettingsTile.setHidden(context, false)
            },
            onEnterEdit = { editMode = true },
            onOpenSearch = { searchOpen = true },
            onOpenNotifications = { notificationsOpen = true },
            onOpenControlCenter = { controlOpen = true },
            onAddWidget = { quickPanelOpen = true },
            onRequestDefaultHome = { openLauncherChooser(context) },
            onOpenHomeSettings = { openLauncherChooser(context) },
            onOpenLauncherSwitch = {
                settingsAppOpen = false
                launcherSwitchOpen = true
            },
            currentHomeLabel = HomeApps.currentLabel(context),
            notificationAccess = LauncherNotificationService.isEnabled(context),
            onOpenNotificationAccess = { LauncherNotificationService.openSettings(context) },
            homePromptOn = homePrompt,
            onToggleHomePrompt = {
                homePrompt = !homePrompt
                LauncherSettings.setHomePrompt(context, homePrompt)
            },
            onExport = { exportLauncher.launch("launcher-backup.txt") },
            onImport = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
            onChangeWallpaper = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SET_WALLPAPER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onDismiss = { settingsAppOpen = false }
        )
    }

    if (launcherSwitchOpen) {
        LauncherSwitchScreen(
            isDefaultNow = isDefaultHome(context),
            onRequestDefault = { requestDefaultHome(context, homeRoleLauncher) },
            onOpenHomeSettings = { openLauncherChooser(context) },
            onDismiss = { launcherSwitchOpen = false }
        )
    }

    if (askHome) {
        AlertDialog(
            onDismissRequest = { askHome = false },
            title = { Text("ホームアプリ") },
            text = {
                Text("このランチャーを既定のホームアプリにしますか。ホームボタンでこの画面が開くようになります。")
            },
            confirmButton = {
                TextButton(onClick = {
                    askHome = false
                    requestDefaultHome(context, homeRoleLauncher)
                }) { Text("既定にする") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askHome = false
                        homePrompt = false
                        LauncherSettings.setHomePrompt(context, false)
                    }) { Text("今後聞かない") }
                    TextButton(onClick = { askHome = false }) { Text("あとで") }
                }
            }
        )
    }

    if (quickPanelOpen) {
        QuickPanel(
            awm = awm,
            onSelectWidget = { provider -> startAddWidget(provider) },
            onOpenSearch = { searchOpen = true },
            onOpenNotifications = { notificationsOpen = true },
            onOpenControlCenter = { controlOpen = true },
            onOpenSettingsApp = { settingsAppOpen = true },
            onEnterEdit = { editMode = true },
            onDismiss = { quickPanelOpen = false }
        )
    }

    if (notificationsOpen) {
        NotificationCenterScreen(onDismiss = { notificationsOpen = false })
    }

    if (controlOpen) {
        ControlCenter(onDismiss = { controlOpen = false })
    }

    if (appListOpen) {
        AppListScreen(
            apps = apps,
            hiddenApps = hiddenApps,
            onHide = { hideApp(it) },
            onUnhide = { unhideApp(it) },
            onUninstall = { uninstallApp(context, it) },
            onAppInfo = { openAppInfo(context, it) },
            onDismiss = { appListOpen = false }
        )
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(
            awm = awm,
            onSelect = { provider ->
                showWidgetPicker = false
                startAddWidget(provider)
            },
            onDismiss = { showWidgetPicker = false }
        )
    }

    val editing = widgets.find { it.widgetId == editWidgetId }
    if (editing != null) {
        WidgetEditDialog(
            widget = editing,
            rows = rows,
            cols = cols,
            onMove = { dr, dc ->
                saveWidgets(widgets.map {
                    if (it == editing) it.copy(
                        row = (it.row + dr).coerceIn(0, rows - it.rowSpan),
                        col = (it.col + dc).coerceIn(0, cols - it.colSpan)
                    ) else it
                })
            },
            onPreset = { rs, cs ->
                saveWidgets(widgets.map {
                    if (it == editing) it.copy(
                        rowSpan = rs.coerceIn(1, rows),
                        colSpan = cs.coerceIn(1, cols),
                        row = it.row.coerceIn(0, (rows - rs).coerceAtLeast(0)),
                        col = it.col.coerceIn(0, (cols - cs).coerceAtLeast(0))
                    ) else it
                })
            },
            onResize = { drs, dcs ->
                saveWidgets(widgets.map {
                    if (it == editing) it.copy(
                        rowSpan = (it.rowSpan + drs).coerceIn(1, rows - it.row),
                        colSpan = (it.colSpan + dcs).coerceIn(1, cols - it.col)
                    ) else it
                })
            },
            onDismiss = { editWidgetId = null }
        )
    }
}
}

object LaunchSource {
    var rect: android.graphics.Rect? = null
}

fun launchApp(context: Context, app: AppEntry, sourceView: android.view.View? = null) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val rect = LaunchSource.rect
    LaunchSource.rect = null
    val options = if (sourceView != null && rect != null) {
        runCatching {
            android.app.ActivityOptions.makeScaleUpAnimation(
                sourceView, rect.left, rect.top, rect.width(), rect.height()
            ).toBundle()
        }.getOrNull()
    } else null
    runCatching { context.startActivity(intent, options) }
}

fun launchShortcut(context: Context, packageName: String, shortcutId: String) {
    runCatching {
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        launcherApps.startShortcut(
            packageName, shortcutId, null, null, android.os.Process.myUserHandle()
        )
    }
}

fun shortcutsFor(context: Context, packageName: String): List<Pair<String, String>> {
    return runCatching {
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        val query = android.content.pm.LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
            .orEmpty()
            .take(4)
            .map { it.id to (it.shortLabel ?: it.longLabel ?: it.id).toString() }
    }.getOrDefault(emptyList())
}

fun uninstallApp(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

fun webSearch(context: Context, query: String) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
        .putExtra(android.app.SearchManager.QUERY, query)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) {
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + packageName)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

fun openLauncherChooser(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    Toast.makeText(context, "ホーム設定を開けませんでした", Toast.LENGTH_SHORT).show()
}

fun isDefaultHome(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)
    } else {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
    }
}

fun requestDefaultHome(context: Context, launcher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    } else {
        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }
}

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    homeItems: List<HomeItem>,
    widgets: List<WidgetItem>,
    favApps: List<AppEntry>,
    pages: Int,
    totalPages: Int,
    rows: Int,
    cols: Int,
    libraryOnly: Set<String>,
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    onMoveToLibrary: (String) -> Unit,
    onRestoreFromLibrary: (String) -> Unit,
    host: AppWidgetHost,
    awm: AppWidgetManager,
    onOpenQuickPanel: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    onMoveItem: (HomeItem, Int, Int) -> Unit,
    onMoveToPage: (HomeItem, Int) -> Unit,
    onAppInfo: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSettingsApp: () -> Unit,
    onEditWidget: (WidgetItem) -> Unit,
    onDeleteWidget: (WidgetItem) -> Unit,
    onWidgetToPage: (WidgetItem, Int) -> Unit,
    onMoveWidget: (WidgetItem, Int, Int) -> Unit,
    onResizeWidget: (WidgetItem, Int, Int) -> Unit,
    folders: List<FolderEntry>,
    onOpenFolder: (String) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val pagerScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page >= pages) {
                    AppLibraryPage(
                        apps = apps,
                        onLaunch = onLaunch,
                        onAppInfo = onAppInfo,
                        libraryOnly = libraryOnly,
                        onRestoreFromLibrary = onRestoreFromLibrary
                    )
                    return@HorizontalPager
                }
                WorkspacePage(
                    pageIndex = page,
                    pages = pages,
                    items = homeItems.filter { it.page == page },
                    pageWidgets = widgets.filter { it.page == page },
                    rows = rows,
                    cols = cols,
                    host = host,
                    awm = awm,
                    resolve = { item ->
                        apps.find {
                            it.packageName == item.packageName && it.activityName == item.activityName
                        }
                    },
                    onLaunch = onLaunch,
                    onRemoveItem = onRemoveItem,
                    onMoveItem = onMoveItem,
                    onMoveToPage = onMoveToPage,
                    onAppInfo = onAppInfo,
                    onEditWidget = onEditWidget,
                    onDeleteWidget = onDeleteWidget,
                    onWidgetToPage = onWidgetToPage,
                    onMoveWidget = onMoveWidget,
                    onResizeWidget = onResizeWidget,
                    folders = folders,
                    onOpenFolder = onOpenFolder,
                    onMoveToLibrary = onMoveToLibrary,
                    editMode = editMode,
                    onEnterEdit = onEnterEdit,
                    onOpenSettingsApp = onOpenSettingsApp,
                    onScrollToPage = { target ->
                        pagerScope.launch { pagerState.animateScrollToPage(target) }
                    },
                    resolveKey = { key ->
                        val i = key.lastIndexOf('/')
                        if (i <= 0) null else apps.find {
                            it.packageName == key.substring(0, i) &&
                                it.activityName == key.substring(i + 1)
                        }
                    }
                )
            }
        }

        if (editMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(LauncherShape.card)
                    .background(LauncherColors.edgeStrong)
                    .clickable { onExitEdit() }
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text("完了", fontSize = LauncherType.label, color = Color.White)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(totalPages) { i ->
                Box(
                    Modifier
                        .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i) Color.White
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (favApps.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(LauncherShape.dock)
                .background(LauncherColors.glassStrong)
                .border(1.dp, LauncherColors.edgeStrong, LauncherShape.dock)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            favApps.take(4).forEach { app ->
                var dockMenu by remember(app.packageName) { mutableStateOf(false) }
                Box {
                    AppIcon(
                        app = app,
                        size = 60.dp,
                        modifier = Modifier.pointerInput(app.packageName) {
                            detectTapGestures(
                                onTap = { onLaunch(app) },
                                onLongPress = { dockMenu = true }
                            )
                        }
                    )
                    DropdownMenu(expanded = dockMenu, onDismissRequest = { dockMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("お気に入りから外す") },
                            onClick = {
                                dockMenu = false
                                onToggleFavorite(app.packageName)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("アプリ情報") },
                            onClick = {
                                dockMenu = false
                                onAppInfo(app.packageName)
                            }
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -12) onOpenQuickPanel()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpenQuickPanel() })
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(120.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(LauncherColors.textDim)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspacePage(
    pageIndex: Int,
    pages: Int,
    items: List<HomeItem>,
    pageWidgets: List<WidgetItem>,
    rows: Int,
    cols: Int,
    host: AppWidgetHost,
    awm: AppWidgetManager,
    resolve: (HomeItem) -> AppEntry?,
    onLaunch: (AppEntry) -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    onMoveItem: (HomeItem, Int, Int) -> Unit,
    onMoveToPage: (HomeItem, Int) -> Unit,
    onAppInfo: (String) -> Unit,
    onEditWidget: (WidgetItem) -> Unit,
    onDeleteWidget: (WidgetItem) -> Unit,
    onWidgetToPage: (WidgetItem, Int) -> Unit,
    onMoveWidget: (WidgetItem, Int, Int) -> Unit,
    onResizeWidget: (WidgetItem, Int, Int) -> Unit,
    folders: List<FolderEntry>,
    onOpenFolder: (String) -> Unit,
    onMoveToLibrary: (String) -> Unit,
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onOpenSettingsApp: () -> Unit,
    onScrollToPage: (Int) -> Unit,
    resolveKey: (String) -> AppEntry?
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        val density = LocalDensity.current
        val contextForMenu = LocalContext.current
        val cellW = constraints.maxWidth.toFloat() / cols
        val cellH = constraints.maxHeight.toFloat() / rows
        var dragItem by remember { mutableStateOf<HomeItem?>(null) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        var dragStart by remember { mutableStateOf(Offset.Zero) }
        var menuFor by remember { mutableStateOf<HomeItem?>(null) }
        val wiggle = rememberInfiniteTransition(label = "wiggle")
        val wiggleAngle by wiggle.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 160, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wiggleAngle"
        )

        Column(Modifier.fillMaxSize()) {
            repeat(rows) { r ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    repeat(cols) { c ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            val item = items.find { it.row == r && it.col == c }
                            val isFolder = item != null && item.packageName == FOLDER_PKG
                            val isSettings = item != null && item.packageName == SETTINGS_PKG
                            val folder = if (isFolder) {
                                folders.find { it.id == item!!.activityName }
                            } else null
                            val app = if (isFolder || isSettings) null else item?.let { resolve(it) }
                            val visible = item != null && item != dragItem &&
                                (app != null || folder != null || isSettings)
                            if (item != null && visible) {
                                val phase = if ((r + c) % 2 == 0) 1f else -1f
                                var iconBounds by remember(item) {
                                    mutableStateOf<android.graphics.Rect?>(null)
                                }
                                Box {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            val b = coords.boundsInWindow()
                                            iconBounds = android.graphics.Rect(
                                                b.left.toInt(), b.top.toInt(),
                                                b.right.toInt(), b.bottom.toInt()
                                            )
                                        }
                                        .graphicsLayer {
                                            rotationZ =
                                                if (editMode || dragItem != null) {
                                                    wiggleAngle * phase
                                                } else 0f
                                        }
                                        .then(
                                            if (editMode) {
                                                Modifier.pointerInput(item) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            dragItem = item
                                                            dragStart = Offset(
                                                                c * cellW + offset.x,
                                                                r * cellH + offset.y
                                                            )
                                                            dragPos = dragStart
                                                        },
                                                        onDrag = { change, amount ->
                                                            change.consume()
                                                            dragPos += amount
                                                        },
                                                        onDragEnd = {
                                                            val moving = dragItem
                                                            if (moving != null) {
                                                                val dist = (dragPos - dragStart)
                                                                    .getDistance()
                                                                val w = cellW * cols
                                                                val edge = w * 0.08f
                                                                if (dist < cellW * 0.15f) {
                                                                    menuFor = moving
                                                                } else if (
                                                                    dragPos.x < edge &&
                                                                    pageIndex > 0
                                                                ) {
                                                                    onMoveToPage(moving, -1)
                                                                    onScrollToPage(pageIndex - 1)
                                                                } else if (
                                                                    dragPos.x > w - edge &&
                                                                    pageIndex < pages - 1
                                                                ) {
                                                                    onMoveToPage(moving, 1)
                                                                    onScrollToPage(pageIndex + 1)
                                                                } else {
                                                                    val tc = (dragPos.x / cellW)
                                                                        .toInt()
                                                                        .coerceIn(0, cols - 1)
                                                                    val tr = (dragPos.y / cellH)
                                                                        .toInt()
                                                                        .coerceIn(0, rows - 1)
                                                                    onMoveItem(moving, tr, tc)
                                                                }
                                                            }
                                                            dragItem = null
                                                        },
                                                        onDragCancel = { dragItem = null }
                                                    )
                                                }
                                            } else {
                                                Modifier.combinedClickable(
                                                    onClick = {
                                                        if (isSettings) {
                                                            onOpenSettingsApp()
                                                        } else if (folder != null) {
                                                            onOpenFolder(folder.id)
                                                        } else if (app != null) {
                                                            LaunchSource.rect = iconBounds
                                                            onLaunch(app)
                                                        }
                                                    },
                                                    onLongClick = { onEnterEdit() }
                                                )
                                            }
                                        )
                                ) {
                                    if (isSettings) {
                                        SettingsTileIcon()
                                        Text(
                                            "設定",
                                            fontSize = LauncherType.iconLabel,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    } else if (folder != null) {
                                        FolderIcon(folder = folder, resolve = resolveKey)
                                        Text(
                                            folder.name,
                                            fontSize = LauncherType.iconLabel,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (app != null) {
                                        AppIcon(app = app, size = 60.dp)
                                        Text(
                                            app.label,
                                            fontSize = LauncherType.iconLabel,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (editMode) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (-4).dp, y = (-4).dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(LauncherColors.badge)
                                            .clickable {
                                                if (app != null) onMoveToLibrary(appKey(app))
                                                onRemoveItem(item)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "−",
                                            fontSize = LauncherType.bodySmall,
                                            color = LauncherColors.badgeInk
                                        )
                                    }
                                }
                                }
                            }
                            if (item != null && (app != null || folder != null || isSettings)) {
                                DropdownMenu(
                                    expanded = menuFor == item,
                                    onDismissRequest = { menuFor = null }
                                ) {
                                    if (pageIndex > 0) {
                                        DropdownMenuItem(
                                            text = { Text("左のページへ移動") },
                                            onClick = {
                                                menuFor = null
                                                onMoveToPage(item, -1)
                                            }
                                        )
                                    }
                                    if (pageIndex < pages - 1) {
                                        DropdownMenuItem(
                                            text = { Text("右のページへ移動") },
                                            onClick = {
                                                menuFor = null
                                                onMoveToPage(item, 1)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when {
                                                    folder != null -> "フォルダを削除"
                                                    isSettings -> "設定アイコンを隠す"
                                                    else -> "ホームから削除"
                                                }
                                            )
                                        },
                                        onClick = {
                                            menuFor = null
                                            onRemoveItem(item)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("ホーム画面を編集") },
                                        onClick = {
                                            menuFor = null
                                            onEnterEdit()
                                        }
                                    )
                                    if (folder != null) {
                                        DropdownMenuItem(
                                            text = { Text("フォルダを開く") },
                                            onClick = {
                                                menuFor = null
                                                onOpenFolder(folder.id)
                                            }
                                        )
                                    } else if (app != null) {
                                        val shortcuts = remember(app.packageName, menuFor) {
                                            if (menuFor == item) {
                                                shortcutsFor(contextForMenu, app.packageName)
                                            } else emptyList()
                                        }
                                        shortcuts.forEach { entry ->
                                            DropdownMenuItem(
                                                text = { Text(entry.second) },
                                                onClick = {
                                                    menuFor = null
                                                    launchShortcut(
                                                        contextForMenu, app.packageName, entry.first
                                                    )
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("App Libraryへ移動") },
                                            onClick = {
                                                menuFor = null
                                                onMoveToLibrary(appKey(app))
                                                onRemoveItem(item)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("アプリ情報") },
                                            onClick = {
                                                menuFor = null
                                                onAppInfo(app.packageName)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        pageWidgets.forEach { w ->
            key(w.widgetId) {
                val info = awm.getAppWidgetInfo(w.widgetId)
                if (info != null) {
                    var wMenu by remember { mutableStateOf(false) }
                    var wDrag by remember(w) { mutableStateOf(Offset.Zero) }
                    var wResize by remember(w) { mutableStateOf(Offset.Zero) }
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (w.col * cellW + wDrag.x).roundToInt(),
                                    (w.row * cellH + wDrag.y).roundToInt()
                                )
                            }
                            .size(
                                width = with(density) {
                                    (cellW * w.colSpan + wResize.x).coerceAtLeast(cellW).toDp()
                                },
                                height = with(density) {
                                    (cellH * w.rowSpan + wResize.y).coerceAtLeast(cellH).toDp()
                                }
                            )
                            .graphicsLayer {
                                rotationZ = if (editMode) wiggleAngle * 0.4f else 0f
                            }
                    ) {
                        AndroidView(
                            factory = { ctx -> host.createView(ctx, w.widgetId, info) },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (editMode) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(LauncherColors.glass)
                                    .pointerInput(w) {
                                        detectDragGestures(
                                            onDrag = { change, amount ->
                                                change.consume()
                                                wDrag += amount
                                            },
                                            onDragEnd = {
                                                val nc = (w.col + wDrag.x / cellW)
                                                    .roundToInt()
                                                    .coerceIn(0, cols - w.colSpan)
                                                val nr = (w.row + wDrag.y / cellH)
                                                    .roundToInt()
                                                    .coerceIn(0, rows - w.rowSpan)
                                                wDrag = Offset.Zero
                                                onMoveWidget(w, nr, nc)
                                            },
                                            onDragCancel = { wDrag = Offset.Zero }
                                        )
                                    }
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCCFFFFFF))
                                    .pointerInput(w) {
                                        detectDragGestures(
                                            onDrag = { change, amount ->
                                                change.consume()
                                                wResize += amount
                                            },
                                            onDragEnd = {
                                                val ncs = (w.colSpan + wResize.x / cellW)
                                                    .roundToInt()
                                                    .coerceIn(1, cols - w.col)
                                                val nrs = (w.rowSpan + wResize.y / cellH)
                                                    .roundToInt()
                                                    .coerceIn(1, rows - w.row)
                                                wResize = Offset.Zero
                                                onResizeWidget(w, nrs, ncs)
                                            },
                                            onDragCancel = { wResize = Offset.Zero }
                                        )
                                    }
                            )
                        }
                        Text(
                            "⋮",
                            color = Color.White,
                            fontSize = LauncherType.bodySmall,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                                .clickable { wMenu = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        DropdownMenu(expanded = wMenu, onDismissRequest = { wMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("位置とサイズを編集") },
                                onClick = {
                                    wMenu = false
                                    onEditWidget(w)
                                }
                            )
                            if (pageIndex > 0) {
                                DropdownMenuItem(
                                    text = { Text("左のページへ移動") },
                                    onClick = {
                                        wMenu = false
                                        onWidgetToPage(w, -1)
                                    }
                                )
                            }
                            if (pageIndex < pages - 1) {
                                DropdownMenuItem(
                                    text = { Text("右のページへ移動") },
                                    onClick = {
                                        wMenu = false
                                        onWidgetToPage(w, 1)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("ウィジェットを削除") },
                                onClick = {
                                    wMenu = false
                                    onDeleteWidget(w)
                                }
                            )
                        }
                    }
                }
            }
        }

        val dragging = dragItem
        if (dragging != null) {
            val dragApp = resolve(dragging)
            val dragFolder = if (dragging.packageName == FOLDER_PKG) {
                folders.find { it.id == dragging.activityName }
            } else null
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (dragPos.x - 28.dp.toPx()).roundToInt(),
                            (dragPos.y - 28.dp.toPx()).roundToInt()
                        )
                    }
                    .size(56.dp)
            ) {
                if (dragFolder != null) {
                    FolderIcon(folder = dragFolder, resolve = resolveKey)
                } else if (dragApp != null) {
                    AppIcon(app = dragApp, size = 68.dp)
                }
            }
        }
    }
}

@Composable
fun WidgetPickerDialog(
    awm: AppWidgetManager,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val providers = remember {
        awm.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
        title = { Text("ウィジェットを選択") },
        text = {
            LazyColumn {
                items(providers.size) { i ->
                    val p = providers[i]
                    Text(
                        p.loadLabel(context.packageManager),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p) }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun WidgetEditDialog(
    widget: WidgetItem,
    rows: Int,
    cols: Int,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onPreset: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        title = { Text("ウィジェット編集") },
        text = {
            Column {
                Text("位置: " + (widget.row + 1) + "行 " + (widget.col + 1) + "列")
                Row {
                    TextButton(onClick = { onMove(-1, 0) }) { Text("上へ") }
                    TextButton(onClick = { onMove(1, 0) }) { Text("下へ") }
                    TextButton(onClick = { onMove(0, -1) }) { Text("左へ") }
                    TextButton(onClick = { onMove(0, 1) }) { Text("右へ") }
                }
                Spacer(Modifier.height(8.dp))
                Text("サイズ: " + widget.rowSpan + "行 × " + widget.colSpan + "列")
                Row {
                    TextButton(onClick = { onResize(-1, 0) }) { Text("低く") }
                    TextButton(onClick = { onResize(1, 0) }) { Text("高く") }
                    TextButton(onClick = { onResize(0, -1) }) { Text("狭く") }
                    TextButton(onClick = { onResize(0, 1) }) { Text("広く") }
                }
                Spacer(Modifier.height(8.dp))
                Text("プリセット")
                Row {
                    TextButton(onClick = { onPreset(2, 2) }) { Text("小 2×2") }
                    TextButton(onClick = { onPreset(2, cols) }) { Text("中 2×" + cols) }
                    TextButton(onClick = { onPreset(4, cols) }) { Text("大 4×" + cols) }
                }
            }
        }
    )
}

@Composable
fun FolderIcon(
    folder: FolderEntry,
    resolve: (String) -> AppEntry?
) {
    val shown = folder.apps.mapNotNull { resolve(it) }.take(4)
    Box(
        Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            repeat(2) { r ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    repeat(2) { c ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            val entry = shown.getOrNull(r * 2 + c)
                            if (entry != null) {
                                AppIcon(app = entry, size = 23.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderOverlay(
    folder: FolderEntry,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onTakeOut: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val entries = folder.apps.mapNotNull { key ->
        val i = key.lastIndexOf('/')
        if (i <= 0) null else {
            val found = apps.find {
                it.packageName == key.substring(0, i) && it.activityName == key.substring(i + 1)
            }
            if (found == null) null else Pair(key, found)
        }
    }

    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(folder.id) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrimDim)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(LauncherShape.overlay)
                .background(LauncherColors.edge)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { })
                }
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onRename(it)
                },
                singleLine = true,
                shape = LauncherShape.card,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White
                )
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(entries.size) { i ->
                    val key = entries[i].first
                    val app = entries[i].second
                    var menu by remember(key) { mutableStateOf(false) }
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { menu = true }
                            )
                        ) {
                            AppIcon(app = app, size = 52.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                app.label,
                                fontSize = LauncherType.micro,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("ホームに出す") },
                                onClick = {
                                    menu = false
                                    onTakeOut(key)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("フォルダから削除") },
                                onClick = {
                                    menu = false
                                    onRemove(key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibraryPage(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (String) -> Unit,
    libraryOnly: Set<String>,
    onRestoreFromLibrary: (String) -> Unit
) {
    val groups = remember(apps) {
        apps.groupBy { categoryLabel(it.category) }
            .toList()
            .sortedWith(compareBy({ it.first == "その他" }, { it.first }))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            "App Library",
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups.size) { gi ->
                val name = groups[gi].first
                val list = groups[gi].second
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(LauncherShape.sheet)
                        .background(LauncherColors.glassRaised)
                        .padding(10.dp)
                ) {
                    Text(name, fontSize = LauncherType.caption, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    val rowsOfApps = list.chunked(4)
                    rowsOfApps.take(2).forEach { chunk ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            chunk.forEach { app ->
                                var menu by remember(app.packageName) { mutableStateOf(false) }
                                Box {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(64.dp)
                                            .combinedClickable(
                                                onClick = { onLaunch(app) },
                                                onLongClick = { menu = true }
                                            )
                                    ) {
                                        AppIcon(app = app, size = 44.dp)
                                        Text(
                                            app.label,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menu,
                                        onDismissRequest = { menu = false }
                                    ) {
                                        if (libraryOnly.contains(appKey(app))) {
                                            DropdownMenuItem(
                                                text = { Text("ホーム画面に追加") },
                                                onClick = {
                                                    menu = false
                                                    onRestoreFromLibrary(appKey(app))
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("アプリ情報") },
                                            onClick = {
                                                menu = false
                                                onAppInfo(app.packageName)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (list.size > 8) {
                        Text(
                            "ほか " + (list.size - 8) + " 件",
                            fontSize = LauncherType.micro,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotlightSearch(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (String) -> Unit,
    onWebSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = if (query.isBlank()) emptyList()
    else apps.filter { it.label.contains(query, ignoreCase = true) }.take(24)

    Column(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrim)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("検索") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    val first = results.firstOrNull()
                    if (first != null) onLaunch(first)
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = LauncherColors.textDim,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray,
                cursorColor = Color.White
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (query.isNotBlank()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onWebSearch(query) }
                            .padding(vertical = 10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LauncherColors.edge),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("W", fontSize = LauncherType.sectionValue, color = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "「" + query + "」をWebで検索",
                            fontSize = LauncherType.body,
                            color = Color.White
                        )
                    }
                }
            }
            items(results.size) { i ->
                val app = results[i]
                var menu by remember(app.packageName) { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { menu = true }
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        AppIcon(app = app, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, fontSize = LauncherType.body, color = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("アプリ情報") },
                            onClick = {
                                menu = false
                                onAppInfo(app.packageName)
                            }
                        )
                    }
                }
            }
        }
        TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
    }
}

@Composable
fun AppIcon(
    app: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val style = LocalIconStyle.current
    val key = IconCache.cacheKey(app, style)
    var bitmap by remember(key) { mutableStateOf(IconCache.peek(key)) }
    LaunchedEffect(key) {
        if (bitmap == null) bitmap = IconCache.load(context, app, style)
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = app.label,
            modifier = modifier
                .size(size)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(size * IconCorner),
                    clip = false
                )
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * IconCorner))
                .background(LauncherColors.glassRaised)
        )
    }
}

@Composable
fun SettingsTileIcon(size: Dp = 60.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * IconCorner))
            .background(LauncherColors.settingsTile),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings_app),
            contentDescription = "設定",
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        fontSize = LauncherType.caption,
        color = LauncherColors.textMuted,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(LauncherShape.panel)
            .background(LauncherColors.glass)
            .border(1.dp, LauncherColors.edge, LauncherShape.panel)
    ) {
        content()
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
fun SettingsRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(label, fontSize = LauncherType.body, color = Color.White, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, fontSize = LauncherType.bodySmall, color = LauncherColors.textMuted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(8.dp))
            Text("›", fontSize = 18.sp, color = LauncherColors.textDim)
        }
    }
}

@Composable
fun SettingsStepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    SettingsRow(
        label = label,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (value > min) onChange(value - 1) }) {
                    Text("−", color = Color.White, fontSize = 18.sp)
                }
                Text(
                    value.toString(),
                    color = Color.White,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { if (value < max) onChange(value + 1) }) {
                    Text("＋", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    )
}

@Composable
fun SettingsApp(
    pages: Int,
    rows: Int,
    cols: Int,
    iconStyle: IconStyle,
    libraryOnly: Set<String>,
    settingsTileHidden: Boolean,
    hiddenCount: Int,
    onOpenAppList: () -> Unit,
    apps: List<AppEntry>,
    isDefaultHomeNow: Boolean,
    onPages: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onCols: (Int) -> Unit,
    onIconStyle: (IconStyle) -> Unit,
    onRestoreFromLibrary: (String) -> Unit,
    onRestoreSettingsTile: () -> Unit,
    onEnterEdit: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onAddWidget: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onOpenLauncherSwitch: () -> Unit,
    currentHomeLabel: String,
    notificationAccess: Boolean,
    onOpenNotificationAccess: () -> Unit,
    homePromptOn: Boolean,
    onToggleHomePrompt: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }
    val hiddenApps = apps.filter { libraryOnly.contains(appKey(it)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrimOpaque)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("設定", fontSize = LauncherType.screenTitle, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            item {
                SettingsSection("アイコンの外観") {
                    IconStyle.entries.forEach { style ->
                        SettingsRow(
                            label = iconStyleLabel(style),
                            trailing = {
                                Text(
                                    if (style == iconStyle) "✓" else "",
                                    color = LauncherColors.accent,
                                    fontSize = LauncherType.sectionValue
                                )
                            },
                            onClick = { onIconStyle(style) }
                        )
                    }
                }
            }
            item {
                SettingsSection("ホーム画面") {
                    SettingsStepperRow("最小ページ数", pages, 1, 5, onPages)
                    SettingsStepperRow("グリッド行数", rows, 3, 8, onRows)
                    SettingsStepperRow("グリッド列数", cols, 3, 6, onCols)
                    SettingsRow(label = "ホーム画面を編集", onClick = {
                        onDismiss()
                        onEnterEdit()
                    })
                    SettingsRow(label = "検索を開く", onClick = {
                        onDismiss()
                        onOpenSearch()
                    })
                    SettingsRow(label = "通知を開く", onClick = {
                        onDismiss()
                        onOpenNotifications()
                    })
                    SettingsRow(label = "コントロールを開く", onClick = {
                        onDismiss()
                        onOpenControlCenter()
                    })
                    SettingsRow(label = "ウィジェットを追加", onClick = {
                        onDismiss()
                        onAddWidget()
                    })
                    SettingsRow(
                        label = "アプリ一覧",
                        value = hiddenCount.toString() + " 件を非表示",
                        onClick = onOpenAppList
                    )
                    if (settingsTileHidden) {
                        SettingsRow(label = "設定アイコンをホームに戻す", onClick = onRestoreSettingsTile)
                    }
                }
            }
            item {
                SettingsSection("壁紙とシステム") {
                    SettingsRow(label = "壁紙を変更", onClick = onChangeWallpaper)
                    SettingsRow(
                        label = "ホームアプリ",
                        value = if (isDefaultHomeNow) "このアプリ" else currentHomeLabel,
                        onClick = onOpenLauncherSwitch
                    )
                    SettingsRow(label = "ホーム設定を開く", onClick = onOpenHomeSettings)
                    SettingsRow(
                        label = "起動時に既定のホームを確認",
                        value = if (homePromptOn) "する" else "しない",
                        onClick = onToggleHomePrompt
                    )
                    SettingsRow(
                        label = "通知へのアクセス",
                        value = if (notificationAccess) "許可済み" else "未許可",
                        onClick = onOpenNotificationAccess
                    )
                }
            }
            item {
                SettingsSection("App Library のみに置いたアプリ") {
                    if (hiddenApps.isEmpty()) {
                        SettingsRow(label = "なし", value = "0 件")
                    } else {
                        hiddenApps.forEach { app ->
                            SettingsRow(
                                label = app.label,
                                value = "ホームに戻す",
                                onClick = { onRestoreFromLibrary(appKey(app)) }
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection("バックアップ") {
                    SettingsRow(label = "設定をファイルに書き出す", onClick = onExport)
                    SettingsRow(label = "ファイルから復元", onClick = onImport)
                }
            }
            item {
                SettingsSection("情報") {
                    SettingsRow(label = "アプリ数", value = apps.size.toString() + " 件")
                    SettingsRow(label = "バージョン", value = version)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AppListScreen(
    apps: List<AppEntry>,
    hiddenApps: Set<String>,
    onHide: (String) -> Unit,
    onUnhide: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showHiddenOnly by remember { mutableStateOf(false) }
    val listed = apps
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .filter { !showHiddenOnly || hiddenApps.contains(appKey(it)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrimOpaque)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("アプリ一覧", fontSize = LauncherType.screenTitle, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
        }
        Text(
            "全 " + apps.size + " 件 / 非表示 " + hiddenApps.size + " 件",
            fontSize = LauncherType.caption,
            color = LauncherColors.textMuted
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("アプリ名で絞り込み") },
            singleLine = true,
            shape = LauncherShape.card,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = LauncherColors.textDim,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray,
                cursorColor = Color.White
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(LauncherShape.chip)
                .background(
                    if (showHiddenOnly) Color.White.copy(alpha = 0.18f)
                    else Color.White.copy(alpha = 0.07f)
                )
                .clickable { showHiddenOnly = !showHiddenOnly }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                if (showHiddenOnly) "非表示のみ表示中" else "非表示だけを見る",
                fontSize = LauncherType.label,
                color = Color.White
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listed.size) { i ->
                val app = listed[i]
                val key = appKey(app)
                val isHidden = hiddenApps.contains(key)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LauncherShape.card)
                        .background(LauncherColors.glassSoft)
                        .padding(10.dp)
                ) {
                    AppIcon(app = app, size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            fontSize = LauncherType.bodySmall,
                            color = if (isHidden) LauncherColors.textDim else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (isHidden) "非表示中" else app.packageName,
                            fontSize = LauncherType.micro,
                            color = LauncherColors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { if (isHidden) onUnhide(key) else onHide(key) }) {
                        Text(
                            if (isHidden) "表示" else "非表示",
                            fontSize = LauncherType.label,
                            color = if (isHidden) LauncherColors.positive else Color.White
                        )
                    }
                    TextButton(onClick = { onUninstall(app.packageName) }) {
                        Text("削除", fontSize = LauncherType.label, color = LauncherColors.danger)
                    }
                    TextButton(onClick = { onAppInfo(app.packageName) }) {
                        Text("情報", fontSize = LauncherType.label, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ControlTile(
    label: String,
    sub: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(LauncherShape.sheet)
            .background(LauncherColors.glassRaised)
            .border(1.dp, LauncherColors.glassStrong, LauncherShape.sheet)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Text(label, fontSize = LauncherType.bodySmall, color = Color.White)
        if (sub != null) {
            Text(sub, fontSize = LauncherType.iconLabel, color = LauncherColors.textMuted)
        }
    }
}

@Composable
fun ControlCenter(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var canWrite by remember { mutableStateOf(SystemControl.canWriteSettings(context)) }
    var brightness by remember { mutableStateOf(SystemControl.brightness(context)) }
    var volume by remember { mutableStateOf(SystemControl.volume(context)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrimControl)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
        ) {
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "コントロール",
                    fontSize = LauncherType.panelTitle,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile(
                    label = "Wi-Fi",
                    sub = "設定を開く",
                    onClick = { SystemControl.openWifi(context) },
                    modifier = Modifier.weight(1f)
                )
                ControlTile(
                    label = "Bluetooth",
                    sub = "設定を開く",
                    onClick = { SystemControl.openBluetooth(context) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile(
                    label = "機内モード",
                    sub = "設定を開く",
                    onClick = { SystemControl.openAirplaneMode(context) },
                    modifier = Modifier.weight(1f)
                )
                ControlTile(
                    label = "画面",
                    sub = "表示設定",
                    onClick = { SystemControl.openDisplay(context) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(LauncherShape.sheet)
                    .background(LauncherColors.glass)
                    .border(1.dp, LauncherColors.edge, LauncherShape.sheet)
                    .padding(14.dp)
            ) {
                Text("明るさ", fontSize = LauncherType.label, color = Color.White)
                if (canWrite) {
                    Slider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                            SystemControl.setBrightness(context, it)
                        }
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "変更するには設定の書き込み許可が必要です",
                        fontSize = LauncherType.iconLabel,
                        color = LauncherColors.textMuted
                    )
                    TextButton(onClick = {
                        SystemControl.requestWriteSettings(context)
                        canWrite = SystemControl.canWriteSettings(context)
                    }) {
                        Text("許可する", color = LauncherColors.accent)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("音量", fontSize = LauncherType.label, color = Color.White)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        SystemControl.setVolume(context, it)
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            val nowPlaying = remember { MediaInfo.current(context) }
            if (nowPlaying != null) {
                NowPlayingCard(nowPlaying = nowPlaying)
                Spacer(Modifier.height(12.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ControlTile(
                    label = "前の曲",
                    onClick = { SystemControl.previous(context) },
                    modifier = Modifier.weight(1f)
                )
                ControlTile(
                    label = "再生 / 停止",
                    onClick = { SystemControl.playPause(context) },
                    modifier = Modifier.weight(1f)
                )
                ControlTile(
                    label = "次の曲",
                    onClick = { SystemControl.next(context) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun LauncherSwitchScreen(
    isDefaultNow: Boolean,
    onRequestDefault: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var reloadKey by remember { mutableStateOf(0) }
    val currentLabel = remember(reloadKey) { HomeApps.currentLabel(context) }
    val homeApps = remember(reloadKey) { HomeApps.list(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrimOpaque)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ホームアプリ",
                fontSize = LauncherType.screenTitle,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(LauncherShape.panel)
                .background(LauncherColors.glass)
                .border(1.dp, LauncherColors.edge, LauncherShape.panel)
                .padding(14.dp)
        ) {
            Text("現在の既定", fontSize = LauncherType.caption, color = LauncherColors.textMuted)
            Text(currentLabel, fontSize = LauncherType.sectionValue, color = Color.White)
            if (!isDefaultNow) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "ホームボタンを押すと上のアプリが開きます。このランチャーを使うには既定を変更してください。",
                    fontSize = LauncherType.caption,
                    color = LauncherColors.textMuted
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        if (!isDefaultNow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LauncherShape.card)
                    .background(LauncherColors.action)
                    .clickable {
                        onRequestDefault()
                        reloadKey += 1
                    }
                    .padding(14.dp)
            ) {
                Text("このランチャーを既定にする", fontSize = LauncherType.body, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(LauncherShape.card)
                .background(LauncherColors.glass)
                .clickable {
                    onOpenHomeSettings()
                    reloadKey += 1
                }
                .padding(14.dp)
        ) {
            Text("システムのホーム設定を開く", fontSize = LauncherType.body, color = Color.White)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "インストール済みのホームアプリ",
            fontSize = LauncherType.caption,
            color = LauncherColors.textMuted
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(homeApps.size) { i ->
                val app = homeApps[i]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LauncherShape.card)
                        .background(LauncherColors.glassSoft)
                        .padding(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = LauncherType.bodySmall, color = Color.White)
                        Text(
                            if (app.isCurrent) "既定" else app.packageName,
                            fontSize = LauncherType.micro,
                            color = if (app.isCurrent) LauncherColors.positive
                            else LauncherColors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { HomeApps.open(context, app) }) {
                        Text("開く", fontSize = LauncherType.label, color = Color.White)
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "「開く」はそのホームアプリを一度だけ起動します。既定そのものを変えるには上の2つのボタンを使ってください。Android では他アプリを既定に設定する操作をアプリ側から行えないためです。",
                    fontSize = LauncherType.iconLabel,
                    color = LauncherColors.textDim
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun NotificationCenterScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(LauncherNotificationService.isEnabled(context)) }
    val items = LauncherNotificationService.items

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrim)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
        ) {
            Spacer(Modifier.height(44.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("通知", fontSize = LauncherType.screenTitle, color = Color.White, modifier = Modifier.weight(1f))
                if (enabled && items.isNotEmpty()) {
                    TextButton(onClick = { LauncherNotificationService.dismissAll() }) {
                        Text("すべて消去", color = Color.White.copy(alpha = 0.8f), fontSize = LauncherType.label)
                    }
                }
                TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
            }
            Spacer(Modifier.height(10.dp))

            if (!enabled) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(LauncherShape.panel)
                        .background(LauncherColors.glass)
                        .border(1.dp, LauncherColors.edge, LauncherShape.panel)
                        .padding(14.dp)
                ) {
                    Text("通知の読み取りが許可されていません", fontSize = LauncherType.bodySmall, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "通知へのアクセスを許可すると、ここに通知が並び、再生中の曲も表示できます。",
                        fontSize = LauncherType.caption,
                        color = LauncherColors.textMuted
                    )
                    TextButton(onClick = {
                        LauncherNotificationService.openSettings(context)
                        enabled = LauncherNotificationService.isEnabled(context)
                    }) {
                        Text("許可する", color = LauncherColors.accent)
                    }
                }
            } else if (items.isEmpty()) {
                Text(
                    "通知はありません",
                    fontSize = LauncherType.bodySmall,
                    color = LauncherColors.textDim
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items.size) { i ->
                        val n = items[i]
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(LauncherShape.panel)
                                .background(LauncherColors.glassRaised)
                                .border(
                                    1.dp,
                                    LauncherColors.edge,
                                    LauncherShape.panel
                                )
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    n.appLabel,
                                    fontSize = LauncherType.iconLabel,
                                    color = LauncherColors.textMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                if (n.clearable) {
                                    Text(
                                        "×",
                                        fontSize = LauncherType.sectionValue,
                                        color = LauncherColors.textMuted,
                                        modifier = Modifier.clickable {
                                            LauncherNotificationService.dismiss(n.key)
                                        }
                                    )
                                }
                            }
                            if (n.title.isNotBlank()) {
                                Text(n.title, fontSize = LauncherType.bodySmall, color = Color.White)
                            }
                            if (n.text.isNotBlank()) {
                                Text(
                                    n.text,
                                    fontSize = LauncherType.caption,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun NowPlayingCard(nowPlaying: NowPlaying) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(LauncherShape.sheet)
            .background(LauncherColors.glassRaised)
            .border(1.dp, LauncherColors.glassStrong, LauncherShape.sheet)
            .padding(14.dp)
    ) {
        Text(
            nowPlaying.appLabel + (if (nowPlaying.isPlaying) " · 再生中" else " · 一時停止"),
            fontSize = LauncherType.iconLabel,
            color = LauncherColors.textMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            nowPlaying.title,
            fontSize = LauncherType.sectionValue,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (nowPlaying.artist.isNotBlank()) {
            Text(
                nowPlaying.artist,
                fontSize = LauncherType.caption,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { SystemControl.previous(context) }) {
                Text("前の曲", color = Color.White, fontSize = LauncherType.label)
            }
            TextButton(onClick = { SystemControl.playPause(context) }) {
                Text("再生 / 停止", color = Color.White, fontSize = LauncherType.label)
            }
            TextButton(onClick = { SystemControl.next(context) }) {
                Text("次の曲", color = Color.White, fontSize = LauncherType.label)
            }
        }
    }
}

@Composable
fun QuickPanel(
    awm: AppWidgetManager,
    onSelectWidget: (AppWidgetProviderInfo) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onOpenSettingsApp: () -> Unit,
    onEnterEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showWidgets by remember { mutableStateOf(false) }
    val providers = remember {
        awm.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.scrim)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
        ) {
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showWidgets) "ウィジェットを選ぶ" else "メニュー",
                    fontSize = LauncherType.panelTitle,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
            }
            Spacer(Modifier.height(12.dp))

            if (!showWidgets) {
                QuickAction("ウィジェットを追加", "ホームに置くウィジェットを選ぶ") {
                    showWidgets = true
                }
                QuickAction("ホーム画面を編集", "アイコンを揺らして並べ替える") {
                    onDismiss()
                    onEnterEdit()
                }
                QuickAction("検索", "アプリと Web を検索") {
                    onDismiss()
                    onOpenSearch()
                }
                QuickAction("通知", "通知を確認する") {
                    onDismiss()
                    onOpenNotifications()
                }
                QuickAction("コントロール", "音量・明るさ・再生操作") {
                    onDismiss()
                    onOpenControlCenter()
                }
                QuickAction("設定", "このランチャーの設定") {
                    onDismiss()
                    onOpenSettingsApp()
                }
                QuickAction("壁紙を変更", "システムの壁紙設定を開く") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SET_WALLPAPER)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            } else {
                TextButton(onClick = { showWidgets = false }) {
                    Text("← メニューに戻る", color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(providers.size) { i ->
                        val provider = providers[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(LauncherShape.card)
                                .background(LauncherColors.glassSoft)
                                .clickable {
                                    onDismiss()
                                    onSelectWidget(provider)
                                }
                                .padding(14.dp)
                        ) {
                            Text(
                                provider.loadLabel(context.packageManager),
                                fontSize = LauncherType.bodySmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun QuickAction(title: String, sub: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(LauncherShape.panel)
            .background(LauncherColors.glass)
            .border(1.dp, LauncherColors.edge, LauncherShape.panel)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = LauncherType.body, color = Color.White)
            Text(sub, fontSize = LauncherType.iconLabel, color = LauncherColors.textMuted)
        }
        Text("›", fontSize = 18.sp, color = LauncherColors.textDim)
    }
}
