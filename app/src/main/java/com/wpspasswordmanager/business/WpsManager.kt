package com.wpspasswordmanager.business

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.wpspasswordmanager.utils.LogManager

object WpsManager {
    private const val TAG = "WpsManager"

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
        "com.hihonor.fileservice",
        "com.huawei.fileservice",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.wpspasswordmanager"
    )

    fun scanInstalledWpsApps(context: Context): List<WpsAppInfo> {
        LogManager.log(TAG, "========== 开始扫描 WPS 应用 ==========", "DEBUG")
        LogManager.log(TAG, "当前进程ID: ${android.os.Process.myPid()}", "DEBUG")
        
        val wpsApps = mutableListOf<WpsAppInfo>()
        
        wpsApps.addAll(scanByPackageEnumeration(context))
        
        val result = wpsApps.distinctBy { it.packageName }
            .sortedBy { it.label }
        
        LogManager.log(TAG, "========== 扫描完成: 找到 ${result.size} 个应用 ==========", "DEBUG")
        result.forEach { LogManager.log(TAG, "  - ${it.label} (${it.packageName}) [${it.versionName}]", "DEBUG") }
        
        return result
    }

    private fun scanByPackageEnumeration(context: Context): List<WpsAppInfo> {
        LogManager.log(TAG, "--- 扫描: 枚举所有已安装应用 ---", "DEBUG")
        
        val wpsApps = mutableListOf<WpsAppInfo>()
        val pm = context.packageManager
        
        try {
            val flags = PackageManager.GET_META_DATA
            val packages = pm.getInstalledPackages(flags)
            LogManager.log(TAG, "  找到 ${packages.size} 个已安装应用", "DEBUG")
            
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
                        LogManager.log(TAG, "  ✓ 识别到应用: $appLabel ($pkgName)", "DEBUG")
                    }
                } catch (e: Exception) {
                    LogManager.log(TAG, "  ✗ 加载失败: $pkgName, 错误: ${e.message}", "ERROR")
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "  ✗ 枚举失败: ${e.message}", "ERROR")
        }
        
        LogManager.log(TAG, "--- 扫描完成: 找到 ${wpsApps.size} 个应用 ---", "DEBUG")
        return wpsApps
    }

    private fun isWpsApplication(packageName: String, label: String, processName: String): Boolean {
        LogManager.log(TAG, "  检查WPS应用: pkg=$packageName, label=$label, process=$processName", "DEBUG")
        
        val patternMatch = WPS_PACKAGE_PATTERNS.any { packageName.contains(it) }
        if (patternMatch) {
            LogManager.log(TAG, "  ✓ 包名模式匹配", "DEBUG")
            return true
        }
        
        val keywordMatch = WPS_KEYWORDS.any { packageName.contains(it) } || WPS_KEYWORDS.any { processName.contains(it) }
        if (keywordMatch) {
            LogManager.log(TAG, "  ✓ 关键字匹配", "DEBUG")
            return true
        }
        
        val labelMatch = WPS_LABEL_KEYWORDS.any { label.contains(it) }
        if (labelMatch) {
            LogManager.log(TAG, "  ✓ 标签匹配", "DEBUG")
            return true
        }
        
        LogManager.log(TAG, "  ✗ 不匹配", "DEBUG")
        return false
    }

    private fun getInstallPath(applicationInfo: ApplicationInfo): String {
        return applicationInfo.sourceDir ?: applicationInfo.publicSourceDir ?: "unknown"
    }
}