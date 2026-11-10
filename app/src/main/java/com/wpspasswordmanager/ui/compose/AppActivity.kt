package com.wpspasswordmanager.ui.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.wpspasswordmanager.business.WpsManager
import androidx.lifecycle.ViewModelProvider

/**
 * 应用界面入口（Compose）。旧 XML 页面 MainActivity / LogActivity 已删除，
 * 仅 ProxyActivity 作为外部文件打开的无界面转发入口保留。
 */
class AppActivity : ComponentActivity() {

    private val vm: AppViewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }

    private val sessionExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.onSessionExpired()
        }
    }

    /**
     * 重新请求「读取设备应用列表」。用户在系统弹框误点「禁止」后，
     * getInstalledPackages 会一直返回空，需要再次给用户一次选择机会。
     */
    private val requestAppListPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // 授权结果出来后重扫一次：允许时列全量，拒绝时 WpsManager 走已知包名探测兜底
            vm.scanWps()
            if (!granted && !shouldShowRequestPermissionRationale(WpsManager.PERMISSION_GET_INSTALLED_APPS)) {
                vm.showToast("系统未再弹出授权框，可在系统设置中允许「读取应用列表」")
            }
        }

    /** 「重新扫描已安装应用」的统一入口：先兜底扫一次，权限被拒时再唤起系统授权框 */
    private fun rescanWps() {
        vm.scanWps()
        if (!WpsManager.appListPermissionDenied(this)) return
        try {
            requestAppListPermission.launch(WpsManager.PERMISSION_GET_INSTALLED_APPS)
        } catch (e: Exception) {
            vm.showToast("无法唤起系统授权，已按已知包名重新扫描")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.WHITE

        // 调试用场景跳转（仅 debug 包）：adb shell am start ... --es scene overview
        intent?.getStringExtra("scene")?.let { vm.applyDebugScene(it) }

        applyStatusBar(darkIcons = false)

        setContent {
            // 只订阅「是否启动页」这个派生布尔：输入、切 tab、弹窗等状态变化不再让
            // setContent 顶层（含 AppTheme / AppRoot）整棵重组
            val darkIcons by remember { derivedStateOf { vm.s.route !is Route.Boot } }
            LaunchedEffect(darkIcons) {
                // 等窗口挂上后再改，避免被主题里的 windowLightStatusBar 覆盖
                window.decorView.post { applyStatusBar(darkIcons) }
            }
            AppTheme {
                AppRoot(
                    vm = vm,
                    onFixPermission = ::fixPermission,
                    onRescan = ::rescanWps,
                    onCreateDoc = { vm.openCreateDoc() },
                    onOpenDoc = { vm.setTab("docs") },
                    onOpenLogs = { vm.openLogs() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResumeRefresh()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.wpspasswordmanager.ACTION_SESSION_EXPIRED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionExpiredReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(sessionExpiredReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(sessionExpiredReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 状态栏图标颜色：启动页是深色渐变 → 浅色图标；其余白底 → 深色图标。
     * 部分 ROM（如 MagicOS）对 WindowInsetsController 不生效，这里同时写旧版 systemUiVisibility 兜底。 */
    private fun applyStatusBar(darkIcons: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = darkIcons
        @Suppress("DEPRECATION")
        run {
            val flag = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            val vis = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (darkIcons) vis or flag else vis and flag.inv()
        }
    }

    private fun fixPermission(kind: String) {
        try {
            when (kind) {
                "accessibility" -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "overlay" -> startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                "storage" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } else {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                }
            }
        } catch (e: Exception) {
            vm.showToast("无法打开系统设置页")
        }
    }
}
