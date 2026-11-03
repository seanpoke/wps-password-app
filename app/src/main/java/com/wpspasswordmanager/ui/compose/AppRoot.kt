package com.wpspasswordmanager.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.screens.AboutScreen
import com.wpspasswordmanager.ui.compose.screens.AdvancedScreen
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
    val s = vm.s

    // 返回键：弹层优先关闭，其次抽屉，最后向导回退 / 取消就地重登
    val wizardStep = (s.route as? Route.Wizard)?.step ?: 1
    val dialogOpen = s.logoutConfirm || s.update != null ||
        s.downloading != null || s.downloadError != null ||
        s.fileExists != null || s.openMode != null || s.deleteTarget != null
    val sheetOpen = s.createDoc != null || s.permTree != null || s.docMenu != null
    androidx.activity.compose.BackHandler(
        enabled = dialogOpen || sheetOpen || wizardStep > 1 ||
            s.route is Route.Logs || s.route is Route.Password || s.route is Route.About ||
            s.route is Route.ServerEdit || s.route is Route.ForcePassword || s.route is Route.Advanced ||
            (s.route is Route.Expired && s.expiredForm)
    ) {
        when {
            s.deleteTarget != null -> vm.cancelDeleteDoc()
            s.docMenu != null -> vm.closeDocMenu()
            s.logoutConfirm -> vm.cancelLogout()
            s.downloading != null || s.downloadError != null -> vm.cancelDownload()
            s.update != null -> vm.dismissUpdate()
            s.fileExists != null -> vm.dismissExists()
            s.openMode != null -> vm.cancelOpenMode()
            s.createDoc != null -> vm.closeCreateDoc()
            s.permTree != null -> vm.closePermTree()
            wizardStep > 1 -> vm.wizardBack()
            s.route is Route.Logs -> vm.closeLogs()
            s.route is Route.Password -> vm.closePasswordPage()
            s.route is Route.About -> vm.closeAbout()
            s.route is Route.ServerEdit -> vm.closeServerEdit()
            s.route is Route.ForcePassword -> vm.abortForcePassword()
            s.route is Route.Advanced -> vm.closeAdvanced()
            else -> vm.cancelExpiredForm()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        when (val r = s.route) {
            is Route.Boot -> BootScreen(s)
            is Route.Wizard -> WizardScreen(s, r.step, vm, onFixPermission)
            is Route.Main -> MainScreen(
                s = s,
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
        }

        // 抽屉（新建 / 权限树 / 文档长按菜单）
        s.createDoc?.let { CreateDocSheet(it, vm) }
        s.permTree?.let { PermTreeSheet(it, vm) }
        s.docMenu?.let { doc ->
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

        // 对话框
        s.openMode?.let { om ->
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
        s.fileExists?.let { fe -> Scrim { FileExistsDialog(fe, vm) } }

        s.deleteTarget?.let { doc ->
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


        if (s.logoutConfirm) {
            Scrim { LogoutDialog(onCancel = vm::cancelLogout, onConfirm = vm::doLogout) }
        }

        if (s.downloading != null || s.downloadError != null) {
            Scrim {
                DownloadDialog(
                    pct = s.downloading ?: 0,
                    error = s.downloadError,
                    onCancel = vm::cancelDownload,
                    onRetry = vm::startDownload
                )
            }
        } else {
            s.update?.let { info ->
                Scrim {
                    UpdateDialog(
                        info = info,
                        currentVersion = vm.currentVersion(),
                        onLater = vm::dismissUpdate,
                        onUpdate = vm::startDownload,
                        onExit = { android.os.Process.killProcess(android.os.Process.myPid()) }
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        ) {
            s.toast?.let { ToastBar(it) }
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x73110C1E))
            .imePadding(),
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
