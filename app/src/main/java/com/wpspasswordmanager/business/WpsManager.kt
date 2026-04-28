package com.wpspasswordmanager.business

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.wpspasswordmanager.utils.LogManager

object WpsManager {
    private const val TAG = "WpsManager"
    private val WPS_KEYWORDS = listOf("wps", "moffice", "kingsoft")
    
    private val MIME_TYPES = listOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint"
    )
    
    private val KNOWN_WPS_PACKAGES = listOf(
        "cn.wps.moffice_eng",
        "cn.wps.moffice",
        "cn.wps.moffice_eng.cn",
        "cn.wps.moffice.beta",
        "cn.wps.wpsoffice",
        "com.wps.moffice",
        "com.wps.moffice_eng",
        "com.kingsoft.wps",
        "com.kingsoft.wps_office"
    )

    fun scanInstalledWpsApps(context: Context): List<WpsAppInfo> {
        LogManager.log(TAG, "========== 开始扫描 WPS 应用 ==========", "DEBUG")
        LogManager.log(TAG, "当前进程ID: ${android.os.Process.myPid()}", "DEBUG")
        LogManager.log(TAG, "当前线程: ${Thread.currentThread().name}", "DEBUG")
        
        val wpsApps = mutableListOf<WpsAppInfo>()
        val pm = context.packageManager
        val selfPackageName = context.packageName
        
        LogManager.log(TAG, "自身包名: $selfPackageName", "DEBUG")
        LogManager.log(TAG, "搜索关键词: $WPS_KEYWORDS", "DEBUG")

        try {
            wpsApps.addAll(scanUsingIntentResolution(context))
            LogManager.log(TAG, "通过 Intent 解析找到 ${wpsApps.size} 个应用", "DEBUG")
        } catch (e: Exception) {
            LogManager.log(TAG, "Intent 解析扫描失败: ${e.message}", "ERROR")
        }
        
        try {
            val directScanApps = scanKnownWpsPackages(context)
            LogManager.log(TAG, "通过直接检查已知包名找到 ${directScanApps.size} 个应用", "DEBUG")
            wpsApps.addAll(directScanApps)
        } catch (e: Exception) {
            LogManager.log(TAG, "直接检查已知包名失败: ${e.message}", "ERROR")
        }
        
        if (wpsApps.isEmpty()) {
            try {
                wpsApps.addAll(scanUsingPackageManager(context))
                LogManager.log(TAG, "回退到 PackageManager 扫描，找到 ${wpsApps.size} 个应用", "DEBUG")
            } catch (e: Exception) {
                LogManager.log(TAG, "PackageManager 扫描失败: ${e.message}", "ERROR")
            }
        }

        val result = wpsApps.distinctBy { it.packageName }.filter { it.packageName != selfPackageName }
        LogManager.log(TAG, "去重并排除自身后结果数量: ${result.size}", "DEBUG")
        
        if (result.isEmpty()) {
            LogManager.log(TAG, "========== 扫描完成: 未找到 WPS 应用 ==========", "WARN")
        } else {
            LogManager.log(TAG, "========== 扫描完成: 找到 ${result.size} 个 WPS 应用 ==========", "DEBUG")
            result.forEach { LogManager.log(TAG, "  - ${it.label} (${it.packageName})", "DEBUG") }
        }
        
        return result.sortedBy { it.label }
    }

    private fun scanKnownWpsPackages(context: Context): List<WpsAppInfo> {
        LogManager.log(TAG, "--- 直接检查已知 WPS 包名 ---", "DEBUG")
        
        val wpsApps = mutableListOf<WpsAppInfo>()
        val pm = context.packageManager

        for (packageName in KNOWN_WPS_PACKAGES) {
            try {
                LogManager.log(TAG, "  检查包名: $packageName", "DEBUG")
                val pkgInfo = pm.getPackageInfo(packageName, 0)
                
                val label = pkgInfo.applicationInfo.loadLabel(pm).toString()
                val icon = pkgInfo.applicationInfo.loadIcon(pm)
                val installPath = getInstallPath(pkgInfo.applicationInfo)
                
                wpsApps.add(
                    WpsAppInfo(
                        label = label,
                        packageName = pkgInfo.packageName,
                        versionName = pkgInfo.versionName ?: "unknown",
                        versionCode = pkgInfo.versionCode,
                        icon = icon,
                        installPath = installPath
                    )
                )
                LogManager.log(TAG, "  ✓ 找到: $label ($packageName)", "DEBUG")
                
            } catch (e: PackageManager.NameNotFoundException) {
                LogManager.log(TAG, "  ✗ 未安装: $packageName", "DEBUG")
            } catch (e: Exception) {
                LogManager.log(TAG, "  ✗ 检查失败: $packageName, 错误: ${e.message}", "ERROR")
            }
        }

        LogManager.log(TAG, "--- 直接检查完成: 找到 ${wpsApps.size} 个应用 ---", "DEBUG")
        return wpsApps
    }

    private fun scanUsingIntentResolution(context: Context): List<WpsAppInfo> {
        LogManager.log(TAG, "--- 使用 Intent 解析方式扫描 ---", "DEBUG")
        
        val wpsApps = mutableListOf<WpsAppInfo>()
        val pm = context.packageManager
        val selfPackageName = context.packageName
        val foundPackages = mutableSetOf<String>()

        for (mimeType in MIME_TYPES) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.setType(mimeType)
                
                LogManager.log(TAG, "查询 MIME 类型: $mimeType", "DEBUG")
                val resolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                
                LogManager.log(TAG, "  找到 ${resolveInfoList.size} 个应用", "DEBUG")
                
                for (resolveInfo in resolveInfoList) {
                    val pkgName = resolveInfo.activityInfo.packageName
                    
                    if (pkgName == selfPackageName) {
                        LogManager.log(TAG, "  跳过自身应用", "DEBUG")
                        continue
                    }
                    
                    if (foundPackages.contains(pkgName)) {
                        LogManager.log(TAG, "  已处理过: $pkgName", "DEBUG")
                        continue
                    }
                    
                    foundPackages.add(pkgName)
                    LogManager.log(TAG, "  新应用: $pkgName", "DEBUG")
                    
                    try {
                        val pkgInfo = pm.getPackageInfo(pkgName, 0)
                        val pkgNameLower = pkgName.toLowerCase()
                        
                        if (WPS_KEYWORDS.any { pkgNameLower.contains(it) }) {
                            val label = pkgInfo.applicationInfo.loadLabel(pm).toString()
                            val icon = pkgInfo.applicationInfo.loadIcon(pm)
                            val installPath = getInstallPath(pkgInfo.applicationInfo)
                            
                            wpsApps.add(
                                WpsAppInfo(
                                    label = label,
                                    packageName = pkgInfo.packageName,
                                    versionName = pkgInfo.versionName ?: "unknown",
                                    versionCode = pkgInfo.versionCode,
                                    icon = icon,
                                    installPath = installPath
                                )
                            )
                            LogManager.log(TAG, "  ✓ 添加 WPS 应用: $label ($pkgName)", "DEBUG")
                        } else {
                            LogManager.log(TAG, "  跳过非 WPS 应用: $pkgName", "DEBUG")
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "  加载应用信息失败: $pkgName, 错误: ${e.message}", "ERROR")
                    }
                }
            } catch (e: Exception) {
                LogManager.log(TAG, "  查询失败: $mimeType, 错误: ${e.message}", "ERROR")
            }
        }

        LogManager.log(TAG, "--- Intent 解析扫描完成: 找到 ${wpsApps.size} 个应用 ---", "DEBUG")
        return wpsApps
    }

    private fun scanUsingPackageManager(context: Context): List<WpsAppInfo> {
        LogManager.log(TAG, "--- 使用 PackageManager 方式扫描 ---", "DEBUG")
        
        val wpsApps = mutableListOf<WpsAppInfo>()
        val pm = context.packageManager

        try {
            var flags = PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
                LogManager.log(TAG, "Android 13+, 使用 GET_SIGNING_CERTIFICATES 标志", "DEBUG")
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
                LogManager.log(TAG, "Android 9+, 使用 GET_SIGNING_CERTIFICATES 标志", "DEBUG")
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                flags = flags or PackageManager.MATCH_ALL
                LogManager.log(TAG, "Android 11+, 添加 MATCH_ALL 标志", "DEBUG")
            }

            LogManager.log(TAG, "调用 getInstalledPackages(), flags=$flags", "DEBUG")
            val packages = pm.getInstalledPackages(flags)
            
            LogManager.log(TAG, "已安装应用总数: ${packages.size}", "DEBUG")
            
            for (pkg in packages) {
                val pkgNameLower = pkg.packageName.toLowerCase()
                
                if (WPS_KEYWORDS.any { pkgNameLower.contains(it) }) {
                    LogManager.log(TAG, "匹配到关键词: ${pkg.packageName}", "DEBUG")
                    
                    try {
                        val label = pkg.applicationInfo.loadLabel(pm).toString()
                        val icon = pkg.applicationInfo.loadIcon(pm)
                        val installPath = getInstallPath(pkg.applicationInfo)
                        
                        wpsApps.add(
                            WpsAppInfo(
                                label = label,
                                packageName = pkg.packageName,
                                versionName = pkg.versionName ?: "unknown",
                                versionCode = pkg.versionCode,
                                icon = icon,
                                installPath = installPath
                            )
                        )
                        LogManager.log(TAG, "成功添加: $label (${pkg.packageName})", "DEBUG")
                    } catch (e: Exception) {
                        LogManager.log(TAG, "加载应用信息失败: ${pkg.packageName}, 错误: ${e.message}", "ERROR")
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "PackageManager 扫描失败: ${e.message}", "ERROR")
        }

        LogManager.log(TAG, "--- PackageManager 扫描完成: 找到 ${wpsApps.size} 个应用 ---", "DEBUG")
        return wpsApps
    }

    private fun getInstallPath(applicationInfo: ApplicationInfo): String {
        return applicationInfo.sourceDir ?: applicationInfo.publicSourceDir ?: "unknown"
    }

    fun launchWpsWithPassword(context: Context, file: java.io.File, password: String): Boolean {
        LogManager.log(TAG, "准备启动 WPS 打开文件", "DEBUG")
        
        val configStorage = com.wpspasswordmanager.storage.ConfigStorage.getInstance(context)
        val targetPkg = configStorage.getTargetWpsPackage()
        
        LogManager.log(TAG, "目标包名: $targetPkg", "DEBUG")
        
        if (targetPkg == null) {
            LogManager.log(TAG, "未设置目标 WPS 包名", "WARN")
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
            LogManager.log(TAG, "启动 WPS: $targetPkg, 文件: ${file.name}", "DEBUG")
            context.startActivity(intent)
            LogManager.log(TAG, "启动成功", "DEBUG")
            true
        } catch (e: Exception) {
            LogManager.log(TAG, "启动失败: ${e.message}", "ERROR")
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
        val apps = scanInstalledWpsApps(context)
        return apps.isNotEmpty()
    }
}