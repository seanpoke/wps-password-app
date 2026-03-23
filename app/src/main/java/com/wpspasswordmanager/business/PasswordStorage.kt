package com.wpspasswordmanager.business

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.security.*
import java.security.cert.CertificateException
import javax.crypto.*
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PasswordStorage private constructor() {

    companion object {
        private const val TAG = "PasswordStorage"
        private const val KEYSTORE_NAME = "AndroidKeyStore"
        private const val KEY_ALIAS = "wps_password_key"
        private const val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        private const val PREFERENCES_NAME = "wps_passwords"
        private const val HMAC_ALGORITHM = "HmacSHA256"

        private var instance: PasswordStorage? = null

        fun getInstance(): PasswordStorage {
            if (instance == null) {
                instance = PasswordStorage()
            }
            return instance!!
        }
    }

    private var keyStore: KeyStore? = null
    private var hmacKey: SecretKey? = null

    init {
        try {
            keyStore = KeyStore.getInstance(KEYSTORE_NAME)
            keyStore?.load(null)
            hmacKey = generateHmacKey()
        } catch (e: Exception) {
            Log.e(TAG, "初始化KeyStore失败", e)
        }
    }

    /**
     * 存储密码
     */
    fun storePassword(context: Context, key: String, password: String): Boolean {
        try {
            Log.d(TAG, "开始存储密码，文件路径: $key")
            val encryptedPassword = encrypt(password)
            val hmac = generateHmac(encryptedPassword)
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString(key, encryptedPassword)
            editor.putString("${key}_hmac", hmac)
            val success = editor.commit() // 使用commit确保操作同步完成
            
            if (success) {
                Log.d(TAG, "密码存储成功，文件路径: $key")
                return true
            } else {
                Log.e(TAG, "密码存储失败，提交操作未成功")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "存储密码失败", e)
            return false
        }
    }

    /**
     * 读取密码
     */
    fun getPassword(context: Context, key: String): String? {
        try {
            Log.d(TAG, "开始读取密码，文件路径: $key")
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val encryptedPassword = sharedPreferences.getString(key, null)
            val storedHmac = sharedPreferences.getString("${key}_hmac", null)
            
            if (encryptedPassword != null && storedHmac != null) {
                // 验证数据完整性
                val calculatedHmac = generateHmac(encryptedPassword)
                if (calculatedHmac == storedHmac) {
                    Log.d(TAG, "数据完整性验证通过，开始解密密码")
                    val decryptedPassword = decrypt(encryptedPassword)
                    Log.d(TAG, "密码读取成功，文件路径: $key")
                    return decryptedPassword
                } else {
                    Log.e(TAG, "数据完整性验证失败，密码可能被篡改")
                    return null
                }
            }
            Log.d(TAG, "未找到存储的密码，文件路径: $key")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "读取密码失败", e)
            return null
        }
    }

    /**
     * 删除密码
     */
    fun deletePassword(context: Context, key: String): Boolean {
        try {
            Log.d(TAG, "开始删除密码，文件路径: $key")
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.remove(key)
            editor.remove("${key}_hmac")
            val success = editor.commit() // 使用commit确保操作同步完成
            
            if (success) {
                Log.d(TAG, "密码删除成功，文件路径: $key")
                return true
            } else {
                Log.e(TAG, "密码删除失败，提交操作未成功")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除密码失败", e)
            return false
        }
    }

    /**
     * 加密密码
     */
    private fun encrypt(password: String): String {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(password.toByteArray())
            val combined = iv + encrypted
            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "加密失败", e)
            throw e
        }
    }

    /**
     * 解密密码
     */
    private fun decrypt(encryptedPassword: String): String {
        try {
            val secretKey = getOrCreateSecretKey()
            val combined = Base64.decode(encryptedPassword, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, 12) // GCM模式使用12字节IV
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "解密失败", e)
            throw e
        }
    }

    /**
     * 生成HMAC用于数据完整性校验
     */
    private fun generateHmac(data: String): String {
        try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(hmacKey)
            val hmacBytes = mac.doFinal(data.toByteArray())
            return Base64.encodeToString(hmacBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "生成HMAC失败", e)
            throw e
        }
    }

    /**
     * 生成HMAC密钥
     */
    private fun generateHmacKey(): SecretKey {
        try {
            val keyGenerator = KeyGenerator.getInstance(HMAC_ALGORITHM)
            keyGenerator.init(256)
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e(TAG, "生成HMAC密钥失败", e)
            throw e
        }
    }

    /**
     * 获取或创建密钥
     */
    private fun getOrCreateSecretKey(): SecretKey {
        try {
            // 检查密钥是否存在
            if (keyStore?.containsAlias(KEY_ALIAS) == true) {
                val key = keyStore?.getKey(KEY_ALIAS, null) as? SecretKey
                if (key != null) {
                    return key
                }
            }

            // 创建新密钥
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyGenerator = KeyGenerator.getInstance(
                    ENCRYPTION_ALGORITHM,
                    KEYSTORE_NAME
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(ENCRYPTION_BLOCK_MODE)
                    .setEncryptionPaddings(ENCRYPTION_PADDING)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            } else {
                // 兼容旧版本
                val keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM)
                keyGenerator.init(256)
                return keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取或创建密钥失败", e)
            throw e
        }
    }

    /**
     * 检查密码是否存在
     */
    fun hasPassword(context: Context, key: String): Boolean {
        try {
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            return sharedPreferences.contains(key)
        } catch (e: Exception) {
            Log.e(TAG, "检查密码是否存在失败", e)
            return false
        }
    }

    /**
     * 获取所有存储的密码键
     */
    fun getAllKeys(context: Context): Set<String> {
        try {
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val allKeys = sharedPreferences.all.keys
            // 过滤掉HMAC键
            return allKeys.filter { !it.endsWith("_hmac") }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "获取所有密码键失败", e)
            return emptySet()
        }
    }
}
