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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
    var drawerOpen by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    var homeItems by remember { mutableStateOf(Workspace.load(context)) }
    var widgets by remember { mutableStateOf(WidgetData.load(context)) }
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

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            apps = apps,
            homeItems = homeItems,
            widgets = widgets,
            favApps = favApps,
            pages = pages,
            rows = rows,
            cols = cols,
            switchIcon = switchIcon,
            host = host,
            awm = awm,
            onOpenDrawer = { drawerOpen = true },
            onLaunch = { launchApp(context, it) },
            onRemoveItem = { item -> saveHome(homeItems - item) },
            onMoveItem = { item, r, c ->
                if (!cellCoveredByWidget(widgets, item.page, r, c)) {
                    saveHome(placeItem(homeItems, item, item.page, r, c))
                }
            },
            onMoveToPage = { item, delta ->
                val target = item.page + delta
                if (target in 0 until pages) {
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
                if (target in 0 until pages) {
                    saveWidgets(widgets.map { if (it == w) it.copy(page = target) else it })
                }
            },
            onHideSwitchIcon = {
                switchIcon = false
                LauncherSettings.setSwitchIcon(context, false)
            }
        )
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            AppDrawer(
                apps = apps,
                favorites = favorites,
                onLaunch = {
                    launchApp(context, it)
                    drawerOpen = false
                },
                onToggleFavorite = { pkg -> toggleFavorite(pkg) },
                onAddToHome = { app ->
                    val cell = firstFreeCell(homeItems, pages, rows, cols, 0)
                    if (cell != null && !cellCoveredByWidget(widgets, cell.first, cell.second, cell.third)) {
                        saveHome(
                            homeItems + HomeItem(
                                cell.first, cell.second, cell.third,
                                app.packageName, app.activityName
                            )
                        )
                        drawerOpen = false
                    }
                },
                onAppInfo = { pkg -> openAppInfo(context, pkg) }
            )
        }
    }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

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

fun openClock(context: Context) {
    val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "時計アプリを開けませんでした", Toast.LENGTH_SHORT).show()
    }
}

fun openCalendar(context: Context) {
    val uri = Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis())
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "カレンダーを開けませんでした", Toast.LENGTH_SHORT).show()
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
    rows: Int,
    cols: Int,
    switchIcon: Boolean,
    host: AppWidgetHost,
    awm: AppWidgetManager,
    onOpenDrawer: () -> Unit,
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
    onHideSwitchIcon: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.JAPAN) }
    val dateFmt = remember { SimpleDateFormat("M月d日 (E)", Locale.JAPAN) }
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultHome(context)
    }
    var homeMenu by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { pages })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -20) onOpenDrawer()
                    if (dragAmount > 20) expandNotifications(context)
                }
            }
            .padding(top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            timeFmt.format(now),
            fontSize = 40.sp,
            color = Color.White,
            modifier = Modifier.clickable { openClock(context) }
        )
        Text(
            dateFmt.format(now),
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.clickable { openCalendar(context) }
        )
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { homeMenu = true })
                }
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
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
                    onWidgetToPage = onWidgetToPage
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
            repeat(pages) { i ->
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
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            favApps.take(5).forEach { app ->
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
    onWidgetToPage: (WidgetItem, Int) -> Unit
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
                            val app = item?.let { resolve(it) }
                            if (item != null && app != null && item != dragItem) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onLaunch(app) }
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
                            if (item != null && app != null) {
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
                                        text = { Text("ホームから削除") },
                                        onClick = {
                                            menuFor = null
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
            if (dragApp != null) {
                Image(
                    bitmap = dragApp.icon,
                    contentDescription = dragApp.label,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragPos.x - 28.dp.toPx()).roundToInt(),
                                (dragPos.y - 28.dp.toPx()).roundToInt()
                            )
                        }
                        .size(56.dp)
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    favorites: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToHome: (AppEntry) -> Unit,
    onAppInfo: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) apps
    else apps.filter { it.label.contains(query, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0101418))
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("アプリを検索") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    val first = filtered.firstOrNull()
                    if (first != null) onLaunch(first)
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray,
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray,
                cursorColor = Color.White
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filtered, key = { it.packageName + "/" + it.activityName }) { app ->
                val isFav = favorites.contains(app.packageName)
                var menu by remember(app.packageName) { mutableStateOf(false) }
                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { menu = true }
                            )
                            .padding(4.dp)
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isFav) app.label + " ★" else app.label,
                            fontSize = 11.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("ホームに追加") },
                            onClick = {
                                menu = false
                                onAddToHome(app)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isFav) "お気に入りから外す" else "お気に入りに追加") },
                            onClick = {
                                menu = false
                                onToggleFavorite(app.packageName)
                            }
                        )
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
