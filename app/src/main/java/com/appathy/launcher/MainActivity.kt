package com.appathy.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

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
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        setContent {
            LauncherRoot(apps.value)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}

@Composable
fun LauncherRoot(apps: List<AppEntry>) {
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    val favApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            favApps = favApps,
            onOpenDrawer = { drawerOpen = true },
            onLaunch = { launchApp(context, it) }
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
                onToggleFavorite = { pkg ->
                    favorites = if (favorites.contains(pkg)) favorites - pkg else favorites + pkg
                    Favorites.save(context, favorites)
                }
            )
        }
    }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
}

fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
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
    favApps: List<AppEntry>,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppEntry) -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -20) onOpenDrawer()
                }
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(timeFmt.format(now), fontSize = 64.sp, color = Color.White)
        Text(dateFmt.format(now), fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.weight(1f))
        if (!isDefault) {
            TextButton(onClick = { requestDefaultHome(context, roleLauncher) }) {
                Text("デフォルトのホームに設定", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            favApps.take(5).forEach { app ->
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(56.dp)
                        .clickable { onLaunch(app) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "△ 上にスワイプでアプリ一覧",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clickable { onOpenDrawer() }
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    favorites: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { onToggleFavorite(app.packageName) }
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
            }
        }
    }
}
