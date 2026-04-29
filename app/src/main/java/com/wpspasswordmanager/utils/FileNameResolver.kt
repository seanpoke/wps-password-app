package com.wpspasswordmanager.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

object FileNameResolver {

    private const val TAG = "FileNameResolver"

    fun resolveFileName(context: Context, uri: Uri): ResolvedFileName {
        val result = ResolvedFileName()
        result.originalUri = uri.toString()
        result.scheme = uri.scheme
        
        Log.d(TAG, "开始解析文件名，URI: $uri")

        if ("content" == uri.scheme) {
            val resolvedName = resolveFromContentResolver(context, uri)
            if (resolvedName != null) {
                result.fileName = resolvedName
                result.source = "ContentResolver"
                result.success = true
                Log.d(TAG, "通过ContentResolver获取文件名: $resolvedName")
            } else {
                val pathName = resolveFromPath(uri)
                if (pathName.isNotEmpty()) {
                    result.fileName = pathName
                    result.source = "UriPath"
                    result.success = true
                    Log.d(TAG, "通过URI路径获取文件名: $pathName")
                }
            }
        } else if ("file" == uri.scheme) {
            result.source = "FilePath"
            val pathName = resolveFromPath(uri)
            if (pathName.isNotEmpty()) {
                result.fileName = pathName
                result.success = true
                Log.d(TAG, "通过文件路径获取文件名: $pathName")
            }
        } else {
            result.source = "Unknown"
            val pathName = resolveFromPath(uri)
            if (pathName.isNotEmpty()) {
                result.fileName = pathName
                result.success = true
                Log.d(TAG, "通过URI路径获取文件名: $pathName")
            }
        }

        if (!result.success) {
            Log.w(TAG, "无法获取文件名，URI: $uri")
        }

        return result
    }

    private fun resolveFromContentResolver(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    val displayName = cursor.getString(displayNameIndex)
                    if (!displayName.isNullOrEmpty()) {
                        Log.d(TAG, "查询到DISPLAY_NAME: $displayName")
                        return displayName
                    }
                }

                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrEmpty()) {
                        Log.d(TAG, "查询到name字段: $name")
                        return name
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "权限异常: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "查询ContentResolver失败: ${e.message}")
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun resolveFromPath(uri: Uri): String {
        val path = uri.path
        if (!path.isNullOrEmpty()) {
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash != -1 && lastSlash < path.length - 1) {
                return path.substring(lastSlash + 1)
            }
        }
        return ""
    }

    fun isHashFileName(fileName: String): Boolean {
        if (fileName.isEmpty()) return false
        
        val extension = getFileExtension(fileName)
        val nameWithoutExt = if (extension.isNotEmpty()) {
            fileName.substring(0, fileName.length - extension.length - 1)
        } else {
            fileName
        }

        val md5Pattern = Regex("^[a-f0-9]{32}$", RegexOption.IGNORE_CASE)
        val longHashPattern = Regex("^[a-f0-9]{32}_\\d+(_m)?$", RegexOption.IGNORE_CASE)
        
        if (longHashPattern.matches(nameWithoutExt)) {
            Log.d(TAG, "检测到Hash文件名模式: $fileName")
            return true
        }
        
        if (md5Pattern.matches(nameWithoutExt)) {
            Log.d(TAG, "检测到MD5文件名: $fileName")
            return true
        }

        return false
    }

    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot != -1 && lastDot < fileName.length - 1) {
            return fileName.substring(lastDot + 1)
        }
        return ""
    }

    data class ResolvedFileName(
        var fileName: String = "",
        var source: String = "",
        var originalUri: String = "",
        var scheme: String? = null,
        var success: Boolean = false
    )
}
