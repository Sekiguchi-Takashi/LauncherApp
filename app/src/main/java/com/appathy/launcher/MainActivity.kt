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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var searchOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var settingsAppOpen by remember { mutableStateOf(false) }
    var settingsTileHidden by remember { mutableStateOf(SettingsTile.hidden(context)) }
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

    val favApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    val dockKeys = favApps.map { appKey(it) }.toSet()

    LaunchedEffect(apps, libraryOnly, favorites, rows, cols, settingsTileHidden) {
        if (apps.isNotEmpty()) {
            var placed = autoPlace(
                apps, homeItems, folders, widgets, libraryOnly, dockKeys, pages, rows, cols
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
            apps = apps,
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
            onOpenSearch = { searchOpen = true },
            onLaunch = { launchApp(context, it) },
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
            settingsTileHidden = settingsTileHidden,
            onRestoreSettingsTile = {
                settingsTileHidden = false
                SettingsTile.setHidden(context, false)
            },
            onAddWidget = { showWidgetPicker = true },
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
        )
        }
        AnimatedVisibility(
            visible = searchOpen,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            SpotlightSearch(
                apps = apps,
                onLaunch = {
                    launchApp(context, it)
                    searchOpen = false
                },
                onAppInfo = { pkg -> openAppInfo(context, pkg) },
                onDismiss = { searchOpen = false }
            )
        }

        val openFolder = folders.find { it.id == openFolderId }
        if (openFolder != null) {
            FolderOverlay(
                folder = openFolder,
                apps = apps,
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

    if (settingsAppOpen) {
        SettingsApp(
            pages = pages,
            rows = rows,
            cols = cols,
            iconStyle = iconStyle,
            libraryOnly = libraryOnly,
            settingsTileHidden = settingsTileHidden,
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
            onAddWidget = { showWidgetPicker = true },
            onRequestDefaultHome = { openLauncherChooser(context) },
            onOpenHomeSettings = { openLauncherChooser(context) },
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

fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
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
    onOpenSearch: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    onMoveItem: (HomeItem, Int, Int) -> Unit,
    onMoveToPage: (HomeItem, Int) -> Unit,
    onAppInfo: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSettingsApp: () -> Unit,
    settingsTileHidden: Boolean,
    onRestoreSettingsTile: () -> Unit,
    onAddWidget: () -> Unit,
    onEditWidget: (WidgetItem) -> Unit,
    onDeleteWidget: (WidgetItem) -> Unit,
    onWidgetToPage: (WidgetItem, Int) -> Unit,
    folders: List<FolderEntry>,
    onOpenFolder: (String) -> Unit
) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultHome(context)
    }
    var homeMenu by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { totalPages })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 20) onOpenSearch()
                }
            }
            .padding(top = 48.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(editMode) {
                    detectTapGestures(
                        onTap = { if (editMode) onExitEdit() },
                        onLongPress = { if (!editMode) homeMenu = true }
                    )
                }
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
                    folders = folders,
                    onOpenFolder = onOpenFolder,
                    onMoveToLibrary = onMoveToLibrary,
                    editMode = editMode,
                    onEnterEdit = onEnterEdit,
                    onOpenSettingsApp = onOpenSettingsApp,
                    resolveKey = { key ->
                        val i = key.lastIndexOf('/')
                        if (i <= 0) null else apps.find {
                            it.packageName == key.substring(0, i) &&
                                it.activityName == key.substring(i + 1)
                        }
                    }
                )
            }
            DropdownMenu(expanded = homeMenu, onDismissRequest = { homeMenu = false }) {
                DropdownMenuItem(
                    text = { Text("ホーム画面を編集") },
                    onClick = {
                        homeMenu = false
                        onEnterEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("検索") },
                    onClick = {
                        homeMenu = false
                        onOpenSearch()
                    }
                )
                if (!isDefault) {
                    DropdownMenuItem(
                        text = { Text("デフォルトのホームに設定") },
                        onClick = {
                            homeMenu = false
                            requestDefaultHome(context, roleLauncher)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("ホーム設定を開く") },
                    onClick = {
                        homeMenu = false
                        openLauncherChooser(context)
                    }
                )
                DropdownMenuItem(
                    text = { Text("ウィジェットを追加") },
                    onClick = {
                        homeMenu = false
                        onAddWidget()
                    }
                )
                DropdownMenuItem(
                    text = { Text("設定") },
                    onClick = {
                        homeMenu = false
                        onOpenSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("壁紙を変更") },
                    onClick = {
                        homeMenu = false
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SET_WALLPAPER)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
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
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(30.dp))
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
    }
}

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
    folders: List<FolderEntry>,
    onOpenFolder: (String) -> Unit,
    onMoveToLibrary: (String) -> Unit,
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onOpenSettingsApp: () -> Unit,
    resolveKey: (String) -> AppEntry?
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        val density = LocalDensity.current
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
                                Box {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            rotationZ = if (editMode) wiggleAngle * phase else 0f
                                        }
                                        .clickable(enabled = !editMode) {
                                            if (isSettings) {
                                                onOpenSettingsApp()
                                            } else if (folder != null) {
                                                onOpenFolder(folder.id)
                                            } else if (app != null) {
                                                onLaunch(app)
                                            }
                                        }
                                        .pointerInput(item, editMode) {
                                            if (editMode) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        val start = Offset(
                                                            c * cellW + offset.x,
                                                            r * cellH + offset.y
                                                        )
                                                        dragItem = item
                                                        dragStart = start
                                                        dragPos = start
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragPos += amount
                                                    },
                                                    onDragEnd = {
                                                        val moving = dragItem
                                                        if (moving != null) {
                                                            val tc = (dragPos.x / cellW).toInt()
                                                                .coerceIn(0, cols - 1)
                                                            val tr = (dragPos.y / cellH).toInt()
                                                                .coerceIn(0, rows - 1)
                                                            onMoveItem(moving, tr, tc)
                                                        }
                                                        dragItem = null
                                                    },
                                                    onDragCancel = { dragItem = null }
                                                )
                                                return@pointerInput
                                            }
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { offset ->
                                                    val start = Offset(
                                                        c * cellW + offset.x,
                                                        r * cellH + offset.y
                                                    )
                                                    dragItem = item
                                                    dragStart = start
                                                    dragPos = start
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragPos += amount
                                                },
                                                onDragEnd = {
                                                    val moving = dragItem
                                                    if (moving != null) {
                                                        val dist = (dragPos - dragStart).getDistance()
                                                        if (dist < cellW * 0.2f) {
                                                            menuFor = moving
                                                        } else {
                                                            val tc = (dragPos.x / cellW).toInt()
                                                                .coerceIn(0, cols - 1)
                                                            val tr = (dragPos.y / cellH).toInt()
                                                                .coerceIn(0, rows - 1)
                                                            onMoveItem(moving, tr, tc)
                                                        }
                                                    }
                                                    dragItem = null
                                                },
                                                onDragCancel = { dragItem = null }
                                            )
                                        }
                                ) {
                                    if (isSettings) {
                                        SettingsTileIcon()
                                        Text(
                                            "設定",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    } else if (folder != null) {
                                        FolderIcon(folder = folder, resolve = resolveKey)
                                        Text(
                                            folder.name,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (app != null) {
                                        AppIcon(app = app, size = 60.dp)
                                        Text(
                                            app.label,
                                            fontSize = 11.sp,
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
                                            .background(Color(0xFFE8E8E8))
                                            .clickable {
                                                if (app != null) onMoveToLibrary(appKey(app))
                                                onRemoveItem(item)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "−",
                                            fontSize = 14.sp,
                                            color = Color(0xFF333333)
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
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (w.col * cellW).roundToInt(),
                                    (w.row * cellH).roundToInt()
                                )
                            }
                            .size(
                                width = with(density) { (cellW * w.colSpan).toDp() },
                                height = with(density) { (cellH * w.rowSpan).toDp() }
                            )
                    ) {
                        AndroidView(
                            factory = { ctx -> host.createView(ctx, w.widgetId, info) },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "⋮",
                            color = Color.White,
                            fontSize = 14.sp,
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
        scale.animateTo(1f, animationSpec = tween(durationMillis = 180))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
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
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.14f))
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
                shape = RoundedCornerShape(14.dp),
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
                                fontSize = 10.sp,
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(10.dp)
                ) {
                    Text(name, fontSize = 12.sp, color = Color.White)
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
                            fontSize = 10.sp,
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
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = if (query.isBlank()) emptyList()
    else apps.filter { it.label.contains(query, ignoreCase = true) }.take(24)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60B0D10))
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
                focusedBorderColor = Color.White.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray,
                cursorColor = Color.White
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Text(app.label, fontSize = 15.sp, color = Color.White)
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
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.225f))
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

@Composable
fun SettingsTileIcon(size: Dp = 60.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.235f))
            .background(Color(0xFF6E6E73)),
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
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
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
        Text(label, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(8.dp))
            Text("›", fontSize = 18.sp, color = Color.White.copy(alpha = 0.5f))
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
    apps: List<AppEntry>,
    isDefaultHomeNow: Boolean,
    onPages: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onCols: (Int) -> Unit,
    onIconStyle: (IconStyle) -> Unit,
    onRestoreFromLibrary: (String) -> Unit,
    onRestoreSettingsTile: () -> Unit,
    onEnterEdit: () -> Unit,
    onAddWidget: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onOpenHomeSettings: () -> Unit,
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
            .background(Color(0xF20B0D10))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("設定", fontSize = 28.sp, color = Color.White, modifier = Modifier.weight(1f))
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
                                    color = Color(0xFF7FA6D8),
                                    fontSize = 16.sp
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
                    SettingsRow(label = "ウィジェットを追加", onClick = {
                        onDismiss()
                        onAddWidget()
                    })
                    if (settingsTileHidden) {
                        SettingsRow(label = "設定アイコンをホームに戻す", onClick = onRestoreSettingsTile)
                    }
                }
            }
            item {
                SettingsSection("壁紙とシステム") {
                    SettingsRow(label = "壁紙を変更", onClick = onChangeWallpaper)
                    SettingsRow(
                        label = "デフォルトのホーム",
                        value = if (isDefaultHomeNow) "このアプリ" else "他のアプリ",
                        onClick = if (isDefaultHomeNow) null else onRequestDefaultHome
                    )
                    SettingsRow(label = "ホーム設定を開く", onClick = onOpenHomeSettings)
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
                SettingsSection("情報") {
                    SettingsRow(label = "アプリ数", value = apps.size.toString() + " 件")
                    SettingsRow(label = "バージョン", value = version)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
