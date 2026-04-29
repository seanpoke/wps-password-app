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
        
        LogManager.log(TAG, "========== 扫描完成: 找到 ${result.size} 个 WPS 应用 ==========", "DEBUG")
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
                        LogManager.log(TAG, "  ✓ 识别到WPS应用: $appLabel ($pkgName)", "DEBUG")
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
        if (WPS_KEYWORDS.any { packageName.contains(it) } || WPS_KEYWORDS.any { processName.contains(it) }) {
            return true
        }
        
        if (WPS_LABEL_KEYWORDS.any { label.contains(it) }) {
            return true
        }
        
        if (packageName.contains("cn.wps") || packageName.contains("com.wps")) {
            return true
        }
        
        return false
    }

    private fun getInstallPath(applicationInfo: ApplicationInfo): String {
        return applicationInfo.sourceDir ?: applicationInfo.publicSourceDir ?: "unknown"
    }

    fun launchWpsWithPassword(context: Context, file: java.io.File, password: String): Boolean {
        val configStorage = com.wpspasswordmanager.storage.ConfigStorage.getInstance(context)
        val targetPkg = configStorage.getTargetWpsPackage()
        
        if (targetPkg == null) {
            return false
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, 
            context.packageName + ".fileprovider", 
            file
        )

        val mimeType = getMimeType(file.name)
        intent.setDataAndType(uri, mimeType)
        intent.setPackage(targetPkg)
        intent.putExtra("Password", password)

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").toLowerCase()
        return when (extension) {
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    fun isWpsInstalled(context: Context): Boolean {
        return scanInstalledWpsApps(context).isNotEmpty()
    }
}