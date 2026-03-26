package com.wpspasswordmanager.business

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PasswordStorageTest {

    companion object {
        private const val TAG = "PasswordStorageTest"

        /**
         * 测试读取密码功能
         */
        fun testReadPassword(context: Context, filePath: String) {
            try {
                Log.d(TAG, "开始测试读取密码，文件路径: $filePath")
                
                val password = PasswordStorage.getInstance().getPassword(context, filePath)
                if (password != null) {
                    Log.d(TAG, "测试成功: 读取到密码: $password")
                } else {
                    Log.d(TAG, "测试结果: 未找到密码")
                }
            } catch (e: Exception) {
                Log.e(TAG, "测试失败", e)
            }
        }

        /**
         * 测试读取Content URI密码功能
         */
        fun testReadPasswordFromUri(context: Context, uriString: String) {
            try {
                Log.d(TAG, "开始测试读取Content URI密码，URI: $uriString")
                
                val password = PasswordStorage.getInstance().getPassword(context, uriString)
                if (password != null) {
                    Log.d(TAG, "测试成功: 读取到密码: $password")
                } else {
                    Log.d(TAG, "测试结果: 未找到密码")
                }
            } catch (e: Exception) {
                Log.e(TAG, "测试失败", e)
            }
        }
    }
}
