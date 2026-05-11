package com.wpspasswordmanager

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.wpspasswordmanager.utils.LogManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class DirectoryMigrationManager(private val context: Context) {

    interface Callback {
        fun onSuccess()
        fun onError(errorCode: Int, message: String)
    }

    companion object {
        private const val TAG = "DirectoryMigrationManager"
        private const val WPS_MANAGEMENT_DIR = "WpsManagement"
        private const val TEMP_DIR_NAME = "\$_wpsManange"
        private const val TARGET_DIR_NAME = "WpsManagement"

        const val ERROR_CODE_DIR_CHECK_FAILED = 1001
        const val ERROR_CODE_TEMP_DIR_CREATE_FAILED = 1002
        const val ERROR_CODE_COPY_FAILED = 1003
        const val ERROR_CODE_VERIFY_FAILED = 1004
        const val ERROR_CODE_DELETE_FAILED = 1005
        const val ERROR_CODE_RENAME_FAILED = 1006
        const val ERROR_CODE_DISK_SPACE_INSUFFICIENT = 1007
        const val ERROR_CODE_PERMISSION_DENIED = 1008
        const val ERROR_CODE_FILE_LOCKED = 1009

        fun execute(context: Context, callback: Callback? = null) {
            Thread {
                try {
                    DirectoryMigrationManager(context).executeMigration(callback)
                } catch (e: Exception) {
                    LogManager.log(
                        TAG,
                        "目录迁移流程执行失败: ${e.message}, 错误码: 1000",
                        "ERROR"
                    )
                    callback?.let {
                        Handler(Looper.getMainLooper()).post {
                            it.onError(1000, "目录迁移流程执行失败: ${e.message}")
                        }
                    }
                }
            }.start()
        }
    }

    private val documentsDir: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    }

    fun executeMigration(callback: Callback? = null) {
        val mainHandler = Handler(Looper.getMainLooper())
        LogManager.log(TAG, "========== 开始执行目录迁移流程 ==========", "INFO")

        val sourceDir = File(documentsDir, WPS_MANAGEMENT_DIR)
        val tempDir = File(documentsDir, TEMP_DIR_NAME)
        val targetDir = File(documentsDir, TARGET_DIR_NAME)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            migrateWithSAF(sourceDir, tempDir, targetDir, callback, mainHandler)
        } else {
            migrateWithLegacyAPI(sourceDir, tempDir, targetDir, callback, mainHandler)
        }
    }

    private fun migrateWithSAF(sourceDir: File, tempDir: File, targetDir: File, 
                               callback: Callback?, handler: Handler) {
        LogManager.log(TAG, "使用 SAF API 进行迁移", "DEBUG")

        val documentsUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents"
        )

        val documentsDocFile = DocumentFile.fromTreeUri(context, documentsUri)
        if (documentsDocFile == null) {
            LogManager.log(TAG, "[ERROR] 无法获取 Documents 目录访问权限, 错误码: $ERROR_CODE_PERMISSION_DENIED", "ERROR")
            handler.post { callback?.onError(ERROR_CODE_PERMISSION_DENIED, "无法访问 Documents 目录，请先授予访问权限") }
            return
        }

        if (!documentsDocFile.name.equals("Documents", ignoreCase = true)) {
            LogManager.log(TAG, "[ERROR] 选择的目录不是 Documents 目录, 错误码: $ERROR_CODE_DIR_CHECK_FAILED", "ERROR")
            handler.post { callback?.onError(ERROR_CODE_DIR_CHECK_FAILED, "请选择正确的 Documents 目录") }
            return
        }

        val sourceDocFile = documentsDocFile.findFile(WPS_MANAGEMENT_DIR)
        if (sourceDocFile == null) {
            LogManager.log(TAG, "[INFO] 源目录不存在，无需迁移", "INFO")
            handler.post { callback?.onSuccess() }
            return
        }

        if (!sourceDocFile.isDirectory) {
            LogManager.log(TAG, "[ERROR] 源路径不是目录, 错误码: $ERROR_CODE_DIR_CHECK_FAILED", "ERROR")
            handler.post { callback?.onError(ERROR_CODE_DIR_CHECK_FAILED, "源路径不是目录") }
            return
        }

        LogManager.log(TAG, "--- 阶段2: 临时目录创建 ---", "INFO")
        val tempDocFile = documentsDocFile.createDirectory(TEMP_DIR_NAME)
        if (tempDocFile == null) {
            LogManager.log(TAG, "[ERROR] 临时目录创建失败, 错误码: $ERROR_CODE_TEMP_DIR_CREATE_FAILED", "ERROR")
            handler.post { callback?.onError(ERROR_CODE_TEMP_DIR_CREATE_FAILED, "临时目录创建失败") }
            return
        }
        LogManager.log(TAG, "[INFO] 临时目录创建成功：$TEMP_DIR_NAME", "INFO")

        LogManager.log(TAG, "--- 阶段3: 文件内容复制 ---", "INFO")
        val files = sourceDocFile.listFiles()
        var copiedCount = 0
        if (files.isEmpty()) {
            LogManager.log(TAG, "[INFO] 源目录为空，没有需要复制的文件", "INFO")
        } else {
            LogManager.log(TAG, "[INFO] 遍历源目录中的 ${files.size} 个项目", "DEBUG")
            for (file in files) {
                if (file.isFile) {
                    copyDocFile(file, tempDocFile)
                    copiedCount++
                } else {
                    LogManager.log(TAG, "[INFO] 跳过文件夹: ${file.name}", "INFO")
                }
            }
        }
        LogManager.log(TAG, "[INFO] 所有文件复制完成，共复制 $copiedCount 个文件", "INFO")

        LogManager.log(TAG, "--- 阶段4: 复制完整性验证 ---", "INFO")
        val sourceFiles = sourceDocFile.listFiles()
        val tempFiles = tempDocFile.listFiles()
        if (sourceFiles.size == tempFiles.size) {
            LogManager.log(TAG, "[INFO] 复制完整性验证通过，共验证${sourceFiles.size}个文件", "INFO")
        } else {
            LogManager.log(TAG, "[ERROR] 复制完整性验证失败, 错误码: $ERROR_CODE_VERIFY_FAILED", "ERROR")
            executeRollback(tempDocFile)
            handler.post { callback?.onError(ERROR_CODE_VERIFY_FAILED, "复制完整性验证失败") }
            return
        }

        LogManager.log(TAG, "--- 阶段5: 原目录删除 ---", "INFO")
        if (sourceDocFile.delete()) {
            LogManager.log(TAG, "[INFO] 原目录删除成功", "INFO")
        } else {
            LogManager.log(TAG, "[ERROR] 原目录删除失败, 错误码: $ERROR_CODE_DELETE_FAILED", "ERROR")
            executeRollback(tempDocFile)
            handler.post { callback?.onError(ERROR_CODE_DELETE_FAILED, "原目录删除失败") }
            return
        }

        LogManager.log(TAG, "--- 阶段6: 临时目录重命名 ---", "INFO")
        if (tempDocFile.renameTo(TARGET_DIR_NAME)) {
            LogManager.log(TAG, "[INFO] 临时目录重命名成功：$TEMP_DIR_NAME -> $TARGET_DIR_NAME", "INFO")
        } else {
            LogManager.log(TAG, "[ERROR] 临时目录重命名失败, 错误码: $ERROR_CODE_RENAME_FAILED", "ERROR")
            executeRollback(tempDocFile)
            handler.post { callback?.onError(ERROR_CODE_RENAME_FAILED, "临时目录重命名失败") }
            return
        }

        LogManager.log(TAG, "========== 目录迁移流程结束 ==========", "INFO")
        handler.post { callback?.onSuccess() }
    }

    private fun copyDocFile(source: DocumentFile, destDir: DocumentFile) {
        val fileName = source.name ?: return
        if (source.isDirectory) {
            val newDir = destDir.createDirectory(fileName)
            if (newDir != null) {
                val children = source.listFiles()
                for (child in children) {
                    copyDocFile(child, newDir)
                }
            }
        } else {
            LogManager.log(TAG, "[INFO] 正在复制：$fileName", "INFO")
            try {
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    val destFile = destDir.createFile(source.type ?: "*/*", fileName)
                    if (destFile != null) {
                        context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                LogManager.log(TAG, "[INFO] ${source.name}复制成功", "INFO")
            } catch (e: Exception) {
                LogManager.log(TAG, "[ERROR] ${source.name}复制失败: ${e.message}", "ERROR")
            }
        }
    }

    private fun executeRollback(tempDir: DocumentFile) {
        LogManager.log(TAG, "[WARN] 开始执行回滚操作...", "WARN")
        try {
            if (tempDir.exists()) {
                val success = tempDir.delete()
                LogManager.log(
                    TAG,
                    "[INFO] 回滚操作：临时目录删除${if (success) "成功" else "失败"}",
                    if (success) "INFO" else "ERROR"
                )
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "[ERROR] 回滚操作失败: ${e.message}", "ERROR")
        }
    }

    private fun migrateWithLegacyAPI(sourceDir: File, tempDir: File, targetDir: File,
                                     callback: Callback?, handler: Handler) {
        LogManager.log(TAG, "--- 阶段1: 目录存在性检测 ---", "INFO")

        if (!documentsDir.exists() || !documentsDir.isDirectory) {
            LogManager.log(TAG, "[ERROR] Documents目录不可访问, 错误码: $ERROR_CODE_DIR_CHECK_FAILED", "ERROR")
            handler.post { callback?.onError(ERROR_CODE_DIR_CHECK_FAILED, "Documents目录不可访问") }
            return
        }

        if (!sourceDir.exists()) {
            LogManager.log(TAG, "[INFO] 源目录不存在，无需迁移", "INFO")
            handler.post { callback?.onSuccess() }
            return
        }

        LogManager.log(TAG, "[INFO] 检测到需要迁移的目录，准备开始迁移", "INFO")

        LogManager.log(TAG, "--- 阶段2: 临时目录创建 ---", "INFO")
        if (tempDir.exists()) {
            LogManager.log(TAG, "[WARN] 临时目录已存在，尝试删除", "WARN")
            tempDir.deleteRecursively()
        }
        if (!tempDir.mkdirs()) {
            LogManager.log(TAG, "[ERROR] 临时目录创建失败, 错误码: $ERROR_CODE_TEMP_DIR_CREATE_FAILED", "ERROR")
            handler.post { callback?.onError(ERROR_CODE_TEMP_DIR_CREATE_FAILED, "临时目录创建失败") }
            return
        }
        LogManager.log(TAG, "[INFO] 临时目录创建成功：${tempDir.absolutePath}", "INFO")

        LogManager.log(TAG, "--- 阶段3: 文件内容复制 ---", "INFO")
        val sourceFiles = sourceDir.listFiles()
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            LogManager.log(TAG, "[INFO] 源目录为空，没有需要复制的文件", "INFO")
        } else {
            for (file in sourceFiles) {
                copyFileOrDir(file, File(tempDir, file.name))
            }
        }
        LogManager.log(TAG, "[INFO] 所有文件复制完成", "INFO")

        LogManager.log(TAG, "--- 阶段4: 复制完整性验证 ---", "INFO")
        val tempFiles = tempDir.listFiles()
        if (sourceFiles.isNullOrEmpty() || (tempFiles != null && sourceFiles.size == tempFiles.size)) {
            LogManager.log(TAG, "[INFO] 复制完整性验证通过", "INFO")
        } else {
            LogManager.log(TAG, "[ERROR] 复制完整性验证失败, 错误码: $ERROR_CODE_VERIFY_FAILED", "ERROR")
            executeRollbackLegacy(tempDir)
            handler.post { callback?.onError(ERROR_CODE_VERIFY_FAILED, "复制完整性验证失败") }
            return
        }

        LogManager.log(TAG, "--- 阶段5: 原目录删除 ---", "INFO")
        if (sourceDir.deleteRecursively()) {
            LogManager.log(TAG, "[INFO] 原目录删除成功", "INFO")
        } else {
            LogManager.log(TAG, "[ERROR] 原目录删除失败, 错误码: $ERROR_CODE_DELETE_FAILED", "ERROR")
            executeRollbackLegacy(tempDir)
            handler.post { callback?.onError(ERROR_CODE_DELETE_FAILED, "原目录删除失败") }
            return
        }

        LogManager.log(TAG, "--- 阶段6: 临时目录重命名 ---", "INFO")
        if (tempDir.renameTo(targetDir)) {
            LogManager.log(TAG, "[INFO] 临时目录重命名成功：$TEMP_DIR_NAME -> $TARGET_DIR_NAME", "INFO")
        } else {
            LogManager.log(TAG, "[ERROR] 临时目录重命名失败, 错误码: $ERROR_CODE_RENAME_FAILED", "ERROR")
            executeRollbackLegacy(tempDir)
            handler.post { callback?.onError(ERROR_CODE_RENAME_FAILED, "临时目录重命名失败") }
            return
        }

        LogManager.log(TAG, "========== 目录迁移流程结束 ==========", "INFO")
        handler.post { callback?.onSuccess() }
    }

    private fun copyFileOrDir(source: File, dest: File) {
        if (source.isDirectory) {
            dest.mkdirs()
            val children = source.listFiles()
            if (children != null) {
                for (child in children) {
                    copyFileOrDir(child, File(dest, child.name))
                }
            }
        } else {
            try {
                LogManager.log(TAG, "[INFO] 正在复制：${source.name}", "INFO")
                FileInputStream(source).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                dest.setLastModified(source.lastModified())
                LogManager.log(TAG, "[INFO] ${source.name}复制成功", "INFO")
            } catch (e: Exception) {
                LogManager.log(TAG, "[ERROR] ${source.name}复制失败: ${e.message}", "ERROR")
            }
        }
    }

    private fun executeRollbackLegacy(tempDir: File) {
        LogManager.log(TAG, "[WARN] 开始执行回滚操作...", "WARN")
        try {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                LogManager.log(TAG, "[INFO] 回滚操作：临时目录删除成功", "INFO")
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "[ERROR] 回滚操作失败: ${e.message}", "ERROR")
        }
    }
}