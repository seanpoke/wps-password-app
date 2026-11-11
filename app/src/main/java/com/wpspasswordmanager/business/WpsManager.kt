package com.wpspasswordmanager.business

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.wpspasswordmanager.utils.LogManager

object WpsManager {
    private const val TAG = "WpsManager"

    /** 部分 ROM 把「读取设备应用列表」做成可撤销的运行时权限，用户点「禁止」后需重新请求 */
    const val PERMISSION_GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"

    private val KNOWN_WPS_PACKAGES = listOf(
        "cn.wps.moffice_eng", "cn.wps.moffice", "cn.wps.moffice_i18n", "cn.wps.wpsoffice",
        "com.wps.moffice", "com.wps.moffice.lite", "com.kingsoft.moffice",
        "com.xiaomi.wpslauncher", "com.xiaomi.miui.wps", "com.miui.wps"
    )

    private val WPS_KEYWORDS = listOf("wps", "moffice", "kingsoft")
    private val WPS_LABEL_KEYWORDS = listOf("wps", "金山", "office")
    private val WPS_PACKAGE_PATTERNS = listOf(
        "cn.wps",
        "com.wps",
        "com.kingsoft",
        "com.xiaomi.wps",
        "com.miui.wps",
        "com.xiaomi.wpslauncher"
    )

    private val NON_WPS_PACKAGES = setOf(
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.wpspasswordmanager"
    )

    fun scanInstalledWpsApps(context: Context): List<WpsAppInfo> {
        val wpsApps = mutableListOf<WpsAppInfo>()
        
        wpsApps.addAll(scanByPackageEnumeration(context))
        // 全量枚举在「读取应用列表」被拒时会返回空，再用 <queries> 已声明的已知包名显式探测兜底
        wpsApps.addAll(probeKnownWpsPackages(context))
        
        return wpsApps.distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    private fun scanByPackageEnumeration(context: Context): List<WpsAppInfo> {
        val wpsApps = mutableListOf<WpsAppInfo>()
        val pm = context.packageManager
        
        try {
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            
            for (pkgInfo in packages) {
                val pkgName = pkgInfo.packageName
                
                if (NON_WPS_PACKAGES.contains(pkgName)) continue
                
                try {
                    val appInfo = pkgInfo.applicationInfo
                    val label = appInfo.loadLabel(pm).toString().toLowerCase()
                    val pkgNameLower = pkgName.toLowerCase()
                    val processName = appInfo.processName?.toLowerCase() ?: ""
                    
                    if (isWpsApplication(pkgNameLower, label, processName)) {
                        val appLabel = appInfo.loadLabel(pm).toString()
                        val icon = appInfo.loadIcon(pm)
                        val installPath = getInstallPath(appInfo)
                        
                        wpsApps.add(
                            WpsAppInfo(
                                label = appLabel,
                                packageName = pkgInfo.packageName,
                                versionName = pkgInfo.versionName ?: "unknown",
                                versionCode = pkgInfo.versionCode,
                                icon = icon,
                                installPath = installPath
                            )
                        )
                    }
                } catch (e: Exception) {
                    LogManager.log(TAG, "加载应用信息失败: $pkgName, 错误: ${e.message}", "ERROR")
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "枚举已安装应用失败: ${e.message}", "ERROR")
        }
        
        return wpsApps
    }

    private fun isWpsApplication(packageName: String, label: String, processName: String): Boolean {
        if (WPS_PACKAGE_PATTERNS.any { packageName.contains(it) }) return true
        if (WPS_KEYWORDS.any { packageName.contains(it) } || WPS_KEYWORDS.any { processName.contains(it) }) return true
        return WPS_LABEL_KEYWORDS.any { label.contains(it) }
    }

    /**
     * 是否需要重新请求「读取设备应用列表」。标准 Android 没有这个权限定义，
     * getInstalledPackages 不受限制，返回 false；只有定义了该权限且被用户拒绝时才返回 true。
     */
    fun appListPermissionDenied(context: Context): Boolean = try {
        context.packageManager.getPermissionInfo(PERMISSION_GET_INSTALLED_APPS, 0)
        ContextCompat.checkSelfPermission(context, PERMISSION_GET_INSTALLED_APPS) != PackageManager.PERMISSION_GRANTED
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** 按已知包名逐个 getPackageInfo：<queries> 声明过的包不受 GET_INSTALLED_APPS 拒绝影响 */
    private fun probeKnownWpsPackages(context: Context): List<WpsAppInfo> {
        val pm = context.packageManager
        return KNOWN_WPS_PACKAGES.mapNotNull { pkg ->
            try {
                val pi = pm.getPackageInfo(pkg, 0)
                val ai = pi.applicationInfo ?: return@mapNotNull null
                @Suppress("DEPRECATION")
                WpsAppInfo(
                    label = ai.loadLabel(pm).toString(),
                    packageName = pkg,
                    versionName = pi.versionName ?: "unknown",
                    versionCode = pi.versionCode,
                    icon = ai.loadIcon(pm),
                    installPath = getInstallPath(ai)
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getInstallPath(applicationInfo: ApplicationInfo): String {
        return applicationInfo.sourceDir ?: applicationInfo.publicSourceDir ?: "unknown"
    }
}