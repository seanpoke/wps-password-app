package com.wpspasswordmanager.business

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.*

class FileManager private constructor() {

    companion object {
        private const val TAG = "FileManager"
        private var instance: FileManager? = null

        fun getInstance(): FileManager {
            if (instance == null) {
                instance = FileManager()
            }
            return instance!!
        }
    }

    /**
     * 写入密码到文件备注信息
     */
    fun writePasswordToFileComment(context: Context, filePath: String, password: String): Boolean {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: $filePath")
                return false
            }

            // 在Android 10+上，使用MediaStore API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return writePasswordToMediaStore(context, filePath, password)
            } else {
                // 在Android 9及以下，使用文件属性
                return writePasswordToFileAttributes(file, password)
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入文件备注失败", e)
            return false
        }
    }

    /**
     * 使用MediaStore API写入密码到文件备注
     */
    private fun writePasswordToMediaStore(context: Context, filePath: String, password: String): Boolean {
        try {
            val contentResolver = context.contentResolver
            val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            
            val cursor = contentResolver.query(uri, null, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
                
                val values = android.content.ContentValues()
                values.put("description", password)
                
                val rowsUpdated = contentResolver.update(contentUri, values, null, null)
                cursor.close()
                return rowsUpdated > 0
            }
            cursor?.close()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "使用MediaStore写入备注失败", e)
            return false
        }
    }


    /**
     * 使用文件属性写入密码到文件备注
     */
    private fun writePasswordToFileAttributes(file: File, password: String): Boolean {
        try {
            // 使用文件的元数据或创建一个隐藏文件来存储密码
            val metaFile = File(file.parent, ".${file.name}.password")
            val writer = FileWriter(metaFile)
            writer.write(password)
            writer.close()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "使用文件属性写入备注失败", e)
            return false
        }
    }
}
