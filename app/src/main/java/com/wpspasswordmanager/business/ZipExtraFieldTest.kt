package com.wpspasswordmanager.business

import android.content.Context
import android.util.Log
import java.io.File

class ZipExtraFieldTest {

    companion object {
        private const val TAG = "ZipExtraFieldTest"

        /**
         * 测试ZIP Extra Field功能
         */
        fun testZipExtraField(context: Context, testFile: File) {
            Log.d(TAG, "开始测试ZIP Extra Field功能")
            Log.d(TAG, "测试文件: ${testFile.absolutePath}")

            // 测试1: 写入密码
            Log.d(TAG, "测试1: 写入密码")
            val testPassword = "TestPassword123"
            val writeResult = ZipExtraFieldManager.getInstance().writePassword(testFile, testPassword)
            Log.d(TAG, "写入结果: $writeResult")

            if (writeResult) {
                // 测试2: 读取密码
                Log.d(TAG, "测试2: 读取密码")
                val readPassword = ZipExtraFieldManager.getInstance().readPassword(testFile)
                Log.d(TAG, "读取到的密码: $readPassword")
                Log.d(TAG, "密码匹配: ${readPassword == testPassword}")

                // 测试3: 检查密码是否存在
                Log.d(TAG, "测试3: 检查密码是否存在")
                val hasPassword = ZipExtraFieldManager.getInstance().hasPassword(testFile)
                Log.d(TAG, "密码存在: $hasPassword")

                // 测试4: 使用PasswordStorage测试
                Log.d(TAG, "测试4: 使用PasswordStorage测试")
                val storageWriteResult = PasswordStorage.getInstance().writePassword(context, testFile.absolutePath, "NewPassword456")
                Log.d(TAG, "PasswordStorage写入结果: $storageWriteResult")

                val storageReadPassword = PasswordStorage.getInstance().getPassword(context, testFile.absolutePath)
                Log.d(TAG, "PasswordStorage读取到的密码: $storageReadPassword")
            } else {
                Log.e(TAG, "写入测试失败，无法继续测试")
            }

            Log.d(TAG, "ZIP Extra Field功能测试完成")
        }

        /**
         * 测试文件锁定处理
         */
        fun testFileLockHandling(context: Context, testFile: File) {
            Log.d(TAG, "开始测试文件锁定处理")

            try {
                // 模拟文件锁定
                val raf = java.io.RandomAccessFile(testFile, "rw")
                Log.d(TAG, "文件已锁定")

                // 尝试写入密码
                val testPassword = "LockedFilePassword"
                val writeResult = ZipExtraFieldManager.getInstance().writePassword(testFile, testPassword)
                Log.d(TAG, "锁定状态下写入结果: $writeResult")

                // 释放文件锁
                raf.close()
                Log.d(TAG, "文件锁已释放")

                // 再次尝试写入
                val writeResultAfterUnlock = ZipExtraFieldManager.getInstance().writePassword(testFile, testPassword)
                Log.d(TAG, "解锁后写入结果: $writeResultAfterUnlock")

                // 读取密码
                val readPassword = ZipExtraFieldManager.getInstance().readPassword(testFile)
                Log.d(TAG, "读取到的密码: $readPassword")
                Log.d(TAG, "密码匹配: ${readPassword == testPassword}")

            } catch (e: Exception) {
                Log.e(TAG, "文件锁定测试失败", e)
            }

            Log.d(TAG, "文件锁定处理测试完成")
        }

        /**
         * 测试性能
         */
        fun testPerformance(testFile: File) {
            Log.d(TAG, "开始测试性能")

            val testPassword = "PerformanceTestPassword"
            val iterations = 10

            // 测试写入性能
            val writeStart = System.currentTimeMillis()
            for (i in 1..iterations) {
                ZipExtraFieldManager.getInstance().writePassword(testFile, "$testPassword$i")
            }
            val writeEnd = System.currentTimeMillis()
            val writeTime = writeEnd - writeStart
            Log.d(TAG, "Write performance: $iterations writes took $writeTime ms")

            // 测试读取性能
            val readStart = System.currentTimeMillis()
            for (i in 1..iterations) {
                ZipExtraFieldManager.getInstance().readPassword(testFile)
            }
            val readEnd = System.currentTimeMillis()
            val readTime = readEnd - readStart
            Log.d(TAG, "Read performance: $iterations reads took $readTime ms")

            Log.d(TAG, "性能测试完成")
        }
    }
}
