package com.wpspasswordmanager.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.screens.AboutScreen
import com.wpspasswordmanager.ui.compose.screens.AdvancedScreen
import com.wpspasswordmanager.ui.compose.screens.AssistantPreviewScreen
import com.wpspasswordmanager.ui.compose.screens.ConfirmDialog
import com.wpspasswordmanager.ui.compose.screens.BootScreen
import com.wpspasswordmanager.ui.compose.screens.CreateDocSheet
import com.wpspasswordmanager.ui.compose.screens.DownloadDialog
import com.wpspasswordmanager.ui.compose.screens.ExpiredScreen
import com.wpspasswordmanager.ui.compose.screens.FileExistsDialog
import com.wpspasswordmanager.ui.compose.screens.ForcePasswordScreen
import com.wpspasswordmanager.ui.compose.screens.LogoutDialog
import com.wpspasswordmanager.ui.compose.screens.LogsScreen
import com.wpspasswordmanager.ui.compose.screens.MainScreen
import com.wpspasswordmanager.ui.compose.screens.OpenModeDialog
import com.wpspasswordmanager.ui.compose.screens.PasswordScreen
import com.wpspasswordmanager.ui.compose.screens.PermTreeSheet
import com.wpspasswordmanager.ui.compose.screens.ServerEditScreen
import com.wpspasswordmanager.ui.compose.screens.UpdateDialog
import com.wpspasswordmanager.ui.compose.screens.WizardScreen

private val AppColorScheme = lightColorScheme(
    primary = C.Primary,
    onPrimary = Color.White,
    primaryContainer = C.PrimarySoft,
    background = C.Bg,
    onBackground = C.Ink,
    surface = Color.White,
    onSurface = C.Ink,
    error = C.Err
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = Typography(), content = content)
}

@Composable
fun AppRoot(
    vm: AppViewModel,
    onFixPermission: (String) -> Unit,
    onCreateDoc: () -> Unit,
    onOpenDoc: () -> Unit,
    onOpenLogs: () -> Unit
) {
    // 顶层不读取 UiState：路由屏 / 弹窗层 / 返回键 / toast 各自独立订阅。
    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        RouteHost(vm, onFixPermission, onCreateDoc, onOpenDoc, onOpenLogs)
        OverlayLayer(vm)
    }
    BackHandlerLayer(vm)
}

/** 主界面只关心这些字段。其余（弹层 / toast / 向导 / 改密）变化时，
 *  [mainOnly] 产出的 UiState 与上一次 equals 相同，derivedStateOf 保留同一实例，
 *  Compose 因此能直接跳过整棵 MainScreen 的重组。 */
private fun UiState.mainOnly(): UiState = UiState(
    route = route,
    // tab 刻意不进快照：由 MainScreen 单独订阅，切 tab 不会让这份快照失效
    name = name,
    role = role,
    account = account,
    docs = docs,
    perm = perm,
    tokenOk = tokenOk,
    host = host,
    port = port,
    scanning = scanning,
    wpsApps = wpsApps,
    selectedPkg = selectedPkg,
    refreshing = refreshing
)

/** 弹层快照：tap 切换、输入、toast 都不会改变它，弹窗层保持稳定。 */
private data class OverlaySnapshot(
    val createDoc: CreateDocState?,
    val permTree: PermTreeState?,
    val docMenu: DocItem?,
    val openMode: OpenModeState?,
    val fileExists: FileExistsState?,
    val deleteTarget: DocItem?,
    val logoutConfirm: Boolean,
    val downloading: Int?,
    val downloadError: String?,
    val update: UpdateInfo?
)

private fun UiState.overlayOnly(): OverlaySnapshot = OverlaySnapshot(
    createDoc = createDoc,
    permTree = permTree,
    docMenu = docMenu,
    openMode = openMode,
    fileExists = fileExists,
    deleteTarget = deleteTarget,
    logoutConfirm = logoutConfirm,
    downloading = downloading,
    downloadError = downloadError,
    update = update
)

@Composable
private fun BackHandlerLayer(vm: AppViewModel) {
    // 只订阅一个布尔；返回键分支逻辑下沉到 ViewModel，避免每次状态变化重建 lambda
    val enabled by remember { derivedStateOf { vm.backEnabled() } }
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = vm::handleBack)
}

@Composable
private fun RouteHost(
    vm: AppViewModel,
    onFixPermission: (String) -> Unit,
    onCreateDoc: () -> Unit,
    onOpenDoc: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val s = vm.s
    val mainS by remember { derivedStateOf { vm.s.mainOnly() } }
    when (val r = s.route) {
        is Route.Boot -> BootScreen(s)
        is Route.Wizard -> WizardScreen(s, r.step, vm, onFixPermission)
        is Route.Main -> MainScreen(
            s = mainS,
            vm = vm,
            onFixPermission = onFixPermission,
            onCreateDoc = onCreateDoc,
            onOpenDoc = onOpenDoc,
            onOpenLogs = onOpenLogs
        )
        is Route.Expired -> ExpiredScreen(s, vm)
        is Route.Logs -> LogsScreen(vm)
        is Route.Password -> PasswordScreen(vm, s.account)
        is Route.About -> AboutScreen(vm)
        is Route.ServerEdit -> ServerEditScreen(vm)
        is Route.ForcePassword -> ForcePasswordScreen(vm)
        is Route.Advanced -> AdvancedScreen(vm)
        is Route.Assistant -> AssistantPreviewScreen(vm)
    }
}

/** 抽屉 / 对话框：与路由屏分开订阅，输入与切页不会让弹窗层跟着重组 */
@Composable
private fun BoxScope.OverlayLayer(vm: AppViewModel) {
    val ov by remember { derivedStateOf { vm.s.overlayOnly() } }

    ov.createDoc?.let { CreateDocSheet(it, vm) }
    ov.permTree?.let { PermTreeSheet(it, vm) }
    ov.docMenu?.let { doc ->
        BottomSheet(onDismiss = vm::closeDocMenu) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp)
            ) {
                Text(doc.name, style = T.Title, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${doc.timeText} · ${doc.sizeText}",
                    style = T.Micro.copy(fontSize = 11.5.sp)
                )
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().card()) {
                    MenuItem("打开文档", I.doc) { vm.closeDocMenu(); vm.openDocFlow(doc) }
                    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(C.Divider))
                    MenuItem("删除文档", I.warn, danger = true) { vm.askDeleteDoc(doc) }
                }
                Spacer(Modifier.height(14.dp))
                AppButton(
                    "取消",
                    onClick = vm::closeDocMenu,
                    modifier = Modifier.fillMaxWidth(),
                    style = BtnStyle.Ghost,
                    height = 44.dp,
                    fontSize = 14.sp
                )
            }
        }
    }

    ov.openMode?.let { om ->
        Scrim {
            OpenModeDialog(
                fileName = om.doc.name,
                mode = om.mode,
                onPick = vm::setOpenMode,
                onCancel = vm::cancelOpenMode,
                onConfirm = vm::confirmOpenMode
            )
        }
    }
    ov.fileExists?.let { fe -> Scrim { FileExistsDialog(fe, vm) } }

    ov.deleteTarget?.let { doc ->
        Scrim {
            ConfirmDialog(
                title = "删除「${doc.name}」？",
                body = "文件会从 Documents/WpsManagement 中永久删除，已通过助手填充的密码也会随之丢失。",
                confirmText = "删除",
                danger = true,
                onCancel = vm::cancelDeleteDoc,
                onConfirm = vm::confirmDeleteDoc
            )
        }
    }

    if (ov.logoutConfirm) {
        Scrim { LogoutDialog(onCancel = vm::cancelLogout, onConfirm = vm::doLogout) }
    }

    if (ov.downloading != null || ov.downloadError != null) {
        Scrim {
            DownloadDialog(
                pct = ov.downloading ?: 0,
                error = ov.downloadError,
                onCancel = vm::cancelDownload,
                onRetry = vm::startDownload
            )
        }
    } else {
        ov.update?.let { info ->
            Scrim {
                UpdateDialog(
                    info = info,
                    currentVersion = remember { vm.currentVersion() },
                    onLater = vm::dismissUpdate,
                    onUpdate = vm::startDownload,
                    onExit = { android.os.Process.killProcess(android.os.Process.myPid()) }
                )
            }
        }
    }

    ToastLayer(vm)
}

/** toast 独立于弹窗层：显示/隐藏提示不会重建任何抽屉或对话框 */
@Composable
private fun BoxScope.ToastLayer(vm: AppViewModel) {
    val toast by remember { derivedStateOf { vm.s.toast } }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 84.dp)
    ) {
        toast?.let { ToastBar(it) }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x73110C1E)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun MenuItem(title: String, icon: List<String>, danger: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SvgIcon(icon, size = 16.dp, tint = if (danger) C.Err else C.Ink3, strokeWidth = 1.9f)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = T.Small.copy(
                fontSize = 13.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = if (danger) C.Err else C.Ink
            )
        )
    }
}
