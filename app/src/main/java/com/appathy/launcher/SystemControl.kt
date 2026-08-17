package com.appathy.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent

object SystemControl {

    fun canWriteSettings(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun requestWriteSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun brightness(context: Context): Float {
        val value = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        return (value / 255f).coerceIn(0f, 1f)
    }

    fun setBrightness(context: Context, ratio: Float) {
        if (!canWriteSettings(context)) return
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (ratio.coerceIn(0.02f, 1f) * 255).toInt()
            )
        }
    }

    private fun audio(context: Context): AudioManager =
        context.getSystemService(AudioManager::class.java)

    fun volume(context: Context): Float {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(context: Context, ratio: Float) {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching {
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (ratio.coerceIn(0f, 1f) * max).toInt(),
                0
            )
        }
    }

    private fun mediaKey(context: Context, code: Int) {
        val am = audio(context)
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    fun playPause(context: Context) = mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(context: Context) = mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous(context: Context) = mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun openWifi(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openBluetooth(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAirplaneMode(context: Context) {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openDisplay(context: Context) {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

data class HomeAppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isCurrent: Boolean
)

object HomeApps {

    private fun homeIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun currentPackage(context: Context): String? =
        runCatching {
            context.packageManager
                .resolveActivity(homeIntent(), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()

    fun currentLabel(context: Context): String {
        val pkg = currentPackage(context) ?: return "未設定"
        if (pkg == "android") return "未設定（毎回選択）"
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    fun list(context: Context): List<HomeAppInfo> {
        val pm = context.packageManager
        val current = currentPackage(context)
        return runCatching {
            pm.queryIntentActivities(homeIntent(), 0)
                .filter { it.activityInfo.packageName != "android" }
                .map {
                    HomeAppInfo(
                        label = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        activityName = it.activityInfo.name,
                        isCurrent = it.activityInfo.packageName == current
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun open(context: Context, app: HomeAppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
