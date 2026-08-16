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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
            reload()
        }
    }

    private fun reload() {
        thread {
            val list = loadApps(this)
            runOnUiThread { apps.value = list }
        }
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

@Composable
fun LauncherRoot(
    apps: List<AppEntry>,
    host: AppWidgetHost,
    awm: AppWidgetManager
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var searchOpen by remember { mutableStateOf(false) }
    var libraryOnly by remember { mutableStateOf(LibraryApps.load(context)) }
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    var homeItems by remember { mutableStateOf(Workspace.load(context)) }
    var widgets by remember { mutableStateOf(WidgetData.load(context)) }
    var folders by remember { mutableStateOf(Folders.load(context)) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf(LauncherSettings.pages(context)) }
    var rows by remember { mutableStateOf(LauncherSettings.rows(context)) }
    var cols by remember { mutableStateOf(LauncherSettings.cols(context)) }
    var switchIcon by remember { mutableStateOf(LauncherSettings.switchIcon(context)) }
    var showSettings by remember { mutableStateOf(false) }
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

    LaunchedEffect(apps, libraryOnly, favorites, rows, cols) {
        if (apps.isNotEmpty()) {
            val placed = autoPlace(
                apps, homeItems, folders, widgets, libraryOnly, dockKeys, pages, rows, cols
            )
            if (placed !== homeItems) saveHome(placed)
        }
    }

    val contentPages = pagesNeeded(homeItems, pages)
    val totalPages = contentPages + 1

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            apps = apps,
            homeItems = homeItems,
            widgets = widgets,
            favApps = favApps,
            pages = contentPages,
            totalPages = totalPages,
            rows = rows,
            cols = cols,
            switchIcon = switchIcon,
            libraryOnly = libraryOnly,
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
            onOpenSettings = { showSettings = true },
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
            onHideSwitchIcon = {
                switchIcon = false
                LauncherSettings.setSwitchIcon(context, false)
            }
        )
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
    }
    BackHandler(enabled = searchOpen) { searchOpen = false }

    val openFolder = folders.find { it.id == openFolderId }
    if (openFolder != null) {
        FolderDialog(
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

    if (showSettings) {
        SettingsDialog(
            pages = pages,
            rows = rows,
            cols = cols,
            switchIcon = switchIcon,
            onPages = { pages = it; LauncherSettings.setPages(context, it) },
            onRows = { rows = it; LauncherSettings.setRows(context, it) },
            onCols = { cols = it; LauncherSettings.setCols(context, it) },
            onSwitchIcon = { switchIcon = it; LauncherSettings.setSwitchIcon(context, it) },
            onDismiss = { showSettings = false }
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

fun expandNotifications(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(service)
    }
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
    switchIcon: Boolean,
    libraryOnly: Set<String>,
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
    onAddWidget: () -> Unit,
    onEditWidget: (WidgetItem) -> Unit,
    onDeleteWidget: (WidgetItem) -> Unit,
    onWidgetToPage: (WidgetItem, Int) -> Unit,
    onHideSwitchIcon: () -> Unit,
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
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { homeMenu = true })
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

        if (!isDefault) {
            TextButton(onClick = { requestDefaultHome(context, roleLauncher) }) {
                Text("デフォルトのホームに設定", color = Color.White)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            favApps.take(4).forEach { app ->
                var dockMenu by remember(app.packageName) { mutableStateOf(false) }
                Box {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(app.packageName) {
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
            if (switchIcon) {
                var switchMenu by remember { mutableStateOf(false) }
                Box {
                    Image(
                        painter = painterResource(R.drawable.ic_switch_home),
                        contentDescription = "ランチャー切替",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { openLauncherChooser(context) },
                                    onLongPress = { switchMenu = true }
                                )
                            }
                            .padding(10.dp)
                    )
                    DropdownMenu(
                        expanded = switchMenu,
                        onDismissRequest = { switchMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ホーム設定を開く") },
                            onClick = {
                                switchMenu = false
                                openLauncherChooser(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("このランチャーを既定にする") },
                            onClick = {
                                switchMenu = false
                                requestDefaultHome(context, roleLauncher)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("このアイコンを隠す") },
                            onClick = {
                                switchMenu = false
                                onHideSwitchIcon()
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "△ 上にスワイプでアプリ一覧",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clickable { onOpenDrawer() }
        )
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
                            val folder = if (isFolder) {
                                folders.find { it.id == item!!.activityName }
                            } else null
                            val app = if (isFolder) null else item?.let { resolve(it) }
                            val visible = item != null && item != dragItem &&
                                (app != null || folder != null)
                            if (item != null && visible) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            if (folder != null) {
                                                onOpenFolder(folder.id)
                                            } else if (app != null) {
                                                onLaunch(app)
                                            }
                                        }
                                        .pointerInput(item) {
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
                                    if (folder != null) {
                                        FolderIcon(folder = folder, resolve = resolveKey)
                                        Text(
                                            folder.name,
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (app != null) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            app.label,
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (item != null && (app != null || folder != null)) {
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
                                                if (folder != null) "フォルダを削除"
                                                else "ホームから削除"
                                            )
                                        },
                                        onClick = {
                                            menuFor = null
                                            onRemoveItem(item)
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
                    Image(
                        bitmap = dragApp.icon,
                        contentDescription = dragApp.label,
                        modifier = Modifier.fillMaxSize()
                    )
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
fun SettingsDialog(
    pages: Int,
    rows: Int,
    cols: Int,
    switchIcon: Boolean,
    onPages: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onCols: (Int) -> Unit,
    onSwitchIcon: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        title = { Text("設定") },
        text = {
            Column {
                SettingRow("ページ数", pages, 1, 5, onPages)
                SettingRow("グリッド行数", rows, 3, 8, onRows)
                SettingRow("グリッド列数", cols, 3, 6, onCols)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ランチャー切替アイコン", modifier = Modifier.weight(1f))
                    Switch(checked = switchIcon, onCheckedChange = onSwitchIcon)
                }
            }
        }
    )
}

@Composable
fun SettingRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > min) onChange(value - 1) }) { Text("−") }
        Text(value.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        TextButton(onClick = { if (value < max) onChange(value + 1) }) { Text("＋") }
    }
}

@Composable
fun FolderIcon(
    folder: FolderEntry,
    resolve: (String) -> AppEntry?
) {
    val shown = folder.apps.mapNotNull { resolve(it) }.take(4)
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
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
                                Image(
                                    bitmap = entry.icon,
                                    contentDescription = entry.label,
                                    modifier = Modifier.fillMaxSize()
                                )
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
fun FolderDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        title = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onRename(it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                app.label,
                                fontSize = 10.sp,
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
    )
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
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier.size(44.dp)
                                        )
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
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(40.dp)
                        )
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
