package com.wpspasswordmanager.business

import android.util.Log
import com.wpspasswordmanager.WpsPasswordManagerApplication
import java.io.*
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.X509EncodedKeySpec
import java.nio.ByteBuffer
import java.util.Base64

object EccEncryptor {
    
    private const val ALGORITHM = "EC"
    private const val CURVE_NAME = "secp256r1"
    private const val PROVIDER = "BC"
    private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
    // 固定的服务器公钥
    private const val SERVER_PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuY2/Hz7c7gM0O8P/8VYjDasWhdW4jyS99+Xwyghe+CVFko7KPeamzaOsUffIHQz0VAA8RH9MV1BYyuZAJ7X05Q=="
    
    init {
        // 添加BouncyCastle Provider
        try {
            val provider = org.bouncycastle.jce.provider.BouncyCastleProvider()
            Security.addProvider(provider)
            Log.d("EccEncryptor", "添加BouncyCastle Provider成功")
        } catch (e: Exception) {
            Log.e("EccEncryptor", "添加BouncyCastle Provider失败", e)
        }
    }
    
    /**
     * 使用服务器公钥加密密码
     * @param password 原始密码字符串
     * @return Base64编码的加密数据，失败返回null
     */
    fun encryptPassword(password: String): String? {
        try {
            // 1. 解析服务器公钥
            val publicKeyBytes = Base64.getDecoder().decode(SERVER_PUBLIC_KEY)
            
            // 尝试使用BouncyCastle Provider
            var keyFactory: KeyFactory
            var keyPairGenerator: KeyPairGenerator
            var keyAgreement: KeyAgreement
            
            try {
                keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER)
                keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER)
                keyAgreement = KeyAgreement.getInstance("ECDH", PROVIDER)
                Log.d("EccEncryptor", "使用BouncyCastle Provider成功")
            } catch (e: Exception) {
                // 如果BouncyCastle失败，尝试使用默认Provider
                Log.w("EccEncryptor", "BouncyCastle Provider失败，尝试使用默认Provider: ${e.message}")
                keyFactory = KeyFactory.getInstance(ALGORITHM)
                keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
                keyAgreement = KeyAgreement.getInstance("ECDH")
                Log.d("EccEncryptor", "使用默认Provider成功")
            }
            
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            
            // 2. 生成临时密钥对
            val ecSpec = java.security.spec.ECGenParameterSpec(CURVE_NAME)
            keyPairGenerator.initialize(ecSpec, SecureRandom())
            val tempKeyPair = keyPairGenerator.generateKeyPair()
            
            // 3. ECDH 密钥协商
            keyAgreement.init(tempKeyPair.private)
            keyAgreement.doPhase(publicKey, true)
            val sharedSecret = keyAgreement.generateSecret()
            
            // 4. 密钥派生 (SHA-256)
            val sha256 = MessageDigest.getInstance("SHA-256")
            val aesKeyBytes = sha256.digest(sharedSecret)
            
            // 5. 生成随机 IV
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            
            // 6. AES-CBC 加密
            val aesCipher = Cipher.getInstance(AES_ALGORITHM)
            val aesKeySpec = SecretKeySpec(aesKeyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, ivSpec)
            val ciphertext = aesCipher.doFinal(password.toByteArray(Charsets.UTF_8))
            
            // 7. 组合数据
            val tempPubKeyBytes = tempKeyPair.public.encoded
            val totalLength = 4 + tempPubKeyBytes.size + iv.size + ciphertext.size
            val result = ByteArray(totalLength)
            
            // 写入临时公钥长度（大端序）
            result[0] = (tempPubKeyBytes.size shr 24).toByte()
            result[1] = (tempPubKeyBytes.size shr 16).toByte()
            result[2] = (tempPubKeyBytes.size shr 8).toByte()
            result[3] = tempPubKeyBytes.size.toByte()
            
            // 写入临时公钥
            System.arraycopy(tempPubKeyBytes, 0, result, 4, tempPubKeyBytes.size)
            
            // 写入 IV
            System.arraycopy(iv, 0, result, 4 + tempPubKeyBytes.size, iv.size)
            
            // 写入密文
            System.arraycopy(ciphertext, 0, result, 4 + tempPubKeyBytes.size + iv.size, ciphertext.size)
            
            // 8. Base64 编码
            val encryptedData = Base64.getEncoder().encodeToString(result)
            // 确保Base64编码格式正确，移除可能的换行符和空格
            val cleanEncryptedData = encryptedData.replace("\n", "").replace("\r", "").trim()
            Log.d("EccEncryptor", "加密成功，密文长度: ${cleanEncryptedData.length}")
            Log.d("EccEncryptor", "加密后的密码$cleanEncryptedData")
            return cleanEncryptedData
            
        } catch (e: Exception) {
            Log.e("EccEncryptor", "ECC加密失败: ${e.message}", e)
            return null
        }
    }
}

class ZipExtraFieldManager private constructor() {

    companion object {
        private const val TAG = "ZipExtraFieldManager"
        private const val WPS_PASSWORD_SIGNATURE = "WPPM"  // 4字节Magic
        private const val WPS_PASSWORD_VERSION = 1
        private const val METADATA_TYPE_PASSWORD = 1  // 元数据类型：1=密码
        private const val METADATA_TYPE_UID = 2  // 元数据类型：2=uid
        private const val MAX_RETRY_COUNT = 5
        private const val RETRY_DELAY_MS = 1000

        private var instance: ZipExtraFieldManager? = null

        fun getInstance(): ZipExtraFieldManager {
            if (instance == null) {
                instance = ZipExtraFieldManager()
            }
            return instance!!
        }
    }

    /**
     * 追加元数据到文件尾部
     */
    fun appendMetaDataToFileEnd(filePath: String, uid: String?, password: String?): Boolean {
        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) {
            Log.e(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] 文件不存在或不可写: ${file.absolutePath}"
            )
            return false
        }

        var retryCount = 0
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                Log.d(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] 尝试写入元数据到文件: ${file.absolutePath}, UID: '$uid', 密码: '$password'"
            )

                // 检测文件是否被锁定
                if (isFileLocked(file)) {
                    Log.w(TAG, "[时间戳: ${System.currentTimeMillis()}] 文件被锁定，等待重试...")
                    Thread.sleep(RETRY_DELAY_MS.toLong())
                    retryCount++
                    continue
                }

                // 先删除旧的WPPM标记
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 开始删除旧的WPPM标记")
                if (removeOldWppmMarkers(file)) {
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 成功删除旧的WPPM标记")
                } else {
                    Log.d(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 未找到旧的WPPM标记或删除失败"
                    )
                }

                // 构建Extra Field数据
            Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 开始构建Extra Field数据")
            
            // 先构建uid数据（如果存在）
            val uidData = if (!uid.isNullOrEmpty()) {
                val uidBytes = buildExtraFieldData(METADATA_TYPE_UID, uid)
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] UID数据构建完成，长度: ${uidBytes.size} bytes"
                )
                uidBytes
            } else {
                ByteArray(0)
            }
            
            // 构建密码数据（如果存在）
            val passwordData = if (!password.isNullOrEmpty()) {
                // 对密码进行ECC加密
                val encryptedPassword = EccEncryptor.encryptPassword(password)
                if (encryptedPassword != null) {
                    Log.d(
                        TAG,
                        "密码加密成，加密后的密码${encryptedPassword} "
                    )
                    val passwordBytes = buildExtraFieldData(METADATA_TYPE_PASSWORD, encryptedPassword)
                    Log.d(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 密码数据构建完成，长度: ${passwordBytes.size} bytes"
                    )
                    passwordBytes
                } else {
                    Log.e(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 密码加密失败，无法写入密码数据"
                    )
                    ByteArray(0)
                }
            } else {
                ByteArray(0)
            }

            // 写入到文件尾部
            Log.d(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] 开始写入Extra Field数据到文件尾部"
            )
            RandomAccessFile(file, "rw").use { raf ->
                val fileLength = raf.length()
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 文件当前长度: $fileLength bytes"
                )
                raf.seek(fileLength)
                
                // 先写入uid数据
                if (uidData.isNotEmpty()) {
                    raf.write(uidData)
                    Log.d(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] UID数据写入完成"
                    )
                }
                
                // 再写入密码数据
                if (passwordData.isNotEmpty()) {
                    raf.write(passwordData)
                    Log.d(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 密码数据写入完成"
                    )
                }
                
                val newFileLength = fileLength + uidData.size + passwordData.size
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 数据写入完成，文件新长度: $newFileLength bytes"
                )
            }

                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 元数据写入成功")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 写入元数据失败", e)
                retryCount++
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.w(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 重试写入... ($retryCount/$MAX_RETRY_COUNT)"
                    )
                    Thread.sleep(RETRY_DELAY_MS.toLong())
                }
            }
        }

        Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 达到最大重试次数，写入失败")
        return false
    }

    /**
     * 删除文件中旧的WPPM标记
     */
    private fun removeOldWppmMarkers(file: File): Boolean {
        // 同时设置插件操作标志
        if (WpsPasswordManagerApplication::class.java.declaredFields.isNotEmpty()) {
            try {
                WpsPasswordManagerApplication.instance.setPluginOperation(true)
            } catch (e: Exception) {
                Log.d(TAG, "无法设置插件操作标志: ${e.message}")
            }
        }
        try {
            Log.d(TAG, "尝试删除旧的WPPM标记")

            RandomAccessFile(file, "rw").use { raf ->
                val fileLength = raf.length()
                if (fileLength < 20) {
                    Log.d(TAG, "文件太小，无需删除WPPM标记")
                    return true
                }

                // 从文件尾部读取1KB数据来查找WPPM标记
                val bufferSize = 1024
                val startPosition = maxOf(0, fileLength - bufferSize)
                val readSize = (fileLength - startPosition).toInt()
                val buffer = ByteArray(bufferSize)

                raf.seek(startPosition)
                raf.readFully(buffer, 0, readSize)

                // 查找所有WPPM标记
                val signatureBytes = WPS_PASSWORD_SIGNATURE.toByteArray()
                val signatureLength = signatureBytes.size
                val markers = mutableListOf<Long>()

                // 从后向前搜索所有WPPM标记
                for (i in readSize - signatureLength downTo 0) {
                    var match = true
                    for (j in 0 until signatureLength) {
                        if (buffer[i + j] != signatureBytes[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        val markerPosition = startPosition + i
                        markers.add(markerPosition)
                        Log.d(TAG, "找到WPPM标记，位置: $markerPosition")
                    }
                }

                if (markers.isEmpty()) {
                    Log.d(TAG, "未找到WPPM标记")
                    return true
                }

                // 无论有多少个标记，都删除所有旧的WPPM标记
                // 这样可以确保每次写入时都只保留最新的密码标记
                Log.d(TAG, "找到${markers.size}个WPPM标记，全部删除")

                // 删除所有WPPM标记：创建新文件，复制除WPPM标记外的所有内容
                // 在原文件所在目录创建临时文件，确保在同一文件系统
                val tempFile = File(file.parent, "temp_${System.currentTimeMillis()}.tmp")
                tempFile.deleteOnExit()

                RandomAccessFile(tempFile, "rw").use { tempRaf ->
                    // 复制文件内容，跳过WPPM标记
                    raf.seek(0)
                    var currentPosition: Long = 0

                    while (currentPosition < fileLength) {
                        // 检查当前位置是否是WPPM标记
                        val isMarker = markers.any { it == currentPosition }
                        if (isMarker) {
                            // 跳过WPPM标记及其后续数据
                            // 读取标记类型
                            raf.seek(currentPosition + 6) // 跳过Magic(4)和Version(2)
                            val type = raf.readByte()

                            // 读取数据长度
                            val dataLengthBytes = ByteArray(4)
                            raf.readFully(dataLengthBytes)
                            val dataLength = byteArrayToInt(dataLengthBytes)

                            // 计算标记总长度：Magic(4) + Version(2) + Type(1) + DataLength(4) + Data(dataLength) + Checksum(4)
                            val markerTotalLength = 4 + 2 + 1 + 4 + dataLength + 4

                            // 跳过整个标记
                            currentPosition += markerTotalLength
                            raf.seek(currentPosition)
                            Log.d(TAG, "跳过WPPM标记，长度: $markerTotalLength")
                        } else {
                            // 复制一个字节
                            val byte = raf.readByte()
                            tempRaf.writeByte(byte.toInt())
                            currentPosition++
                        }
                    }
                }

                // 用临时文件替换原文件
                // 先尝试直接重命名（如果目标文件不存在）
                if (file.exists()) {
                    // 先备份原文件
                    val backupFile = File(file.parent, "${file.name}.bak")
                    if (file.renameTo(backupFile)) {
                        // 重命名临时文件为原文件名
                        if (tempFile.renameTo(file)) {
                            // 删除备份文件
                            backupFile.delete()
                            Log.d(TAG, "成功删除旧的WPPM标记并替换文件")
                            return true
                        } else {
                            // 重命名失败，恢复原文件
                            backupFile.renameTo(file)
                            Log.e(TAG, "替换文件失败，已恢复原文件")
                            return false
                        }
                    } else {
                        Log.e(TAG, "备份原文件失败")
                        return false
                    }
                } else {
                    // 目标文件不存在，直接重命名
                    if (tempFile.renameTo(file)) {
                        Log.d(TAG, "成功删除旧的WPPM标记并替换文件")
                        return true
                    } else {
                        Log.e(TAG, "替换文件失败")
                        return false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除旧WPPM标记失败", e)
            return false
        }
    }


    /**
     * 从ZIP文件的Extra Field读取密码
     * 按照读数据.md文档要求：从文件尾部读取1KB数据来查找元数据块
     */
    fun readPassword(file: File): String? {
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "文件不存在或不可读: ${file.absolutePath}")
            return null
        }

        try {
            Log.d(TAG, "尝试从文件读取密码: ${file.absolutePath}")

            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                if (fileLength < 20) { // 最小Extra Field大小
                    Log.d(TAG, "文件太小，无法包含密码数据")
                    return null
                }

                // 按照读数据.md文档要求：从文件尾部读取1KB数据
                val bufferSize = 1024
                val startPosition = maxOf(0, fileLength - bufferSize)
                val readSize = (fileLength - startPosition).toInt()
                val buffer = ByteArray(bufferSize)

                raf.seek(startPosition)
                raf.readFully(buffer, 0, readSize)

                // 按照C++实现，从后向前搜索WPPM签名
                // 查找所有WPPM签名，找到类型为1的密码元数据
                val signatureBytes = WPS_PASSWORD_SIGNATURE.toByteArray()
                val signatureLength = signatureBytes.size

                // 从后向前搜索，找到最后一个类型为1的密码元数据
                for (i in readSize - signatureLength downTo 0) {
                    var match = true
                    for (j in 0 until signatureLength) {
                        if (buffer[i + j] != signatureBytes[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        // 计算实际数据位置
                        val dataPosition = startPosition + i

                        // 检查剩余文件长度是否足够
                        if (fileLength - dataPosition < 15) { // Magic(4) + Version(2) + Type(1) + DataLength(4) + Checksum(4) = 15
                            continue
                        }

                        // 读取元数据块头部信息
                        raf.seek(dataPosition)

                        // 读取Magic（4字节）
                        val magic = ByteArray(4)
                        raf.readFully(magic)

                        // 读取Version（2字节）
                        val versionBytes = ByteArray(2)
                        raf.readFully(versionBytes)

                        // 读取Type（1字节）
                        val type = raf.readByte()

                        // 重置位置
                        raf.seek(dataPosition)

                        if (type == METADATA_TYPE_PASSWORD.toByte()) {
                            // 找到密码类型，解析数据
                            val password = parseExtraFieldData(raf)
                            if (password != null) {
                                Log.d(TAG, "找到WPPM签名，位置: ${startPosition + i}")
                                Log.d(TAG, "成功读取密码: $password")
                                return password
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "未找到WPPM密码数据")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "读取密码失败", e)
            return null
        }
    }

    /**
     * 从输入流读取ZIP Extra Field中的密码（直接流读取模式）
     * 按照读数据.md文档要求：从文件尾部读取1KB数据来查找元数据块
     */
    fun readPasswordFromInputStream(inputStream: InputStream): String? {
        try {
            Log.d(TAG, "尝试从输入流读取密码")

            // 将输入流转换为字节数组以支持从尾部搜索
            val byteArray = inputStream.readBytes()
            val fileLength = byteArray.size.toLong()

            if (fileLength < 20) { // 最小Extra Field大小
                Log.d(TAG, "文件太小，无法包含密码数据")
                return null
            }

            // 按照读数据.md文档要求：从文件尾部读取1KB数据
            val bufferSize = 1024
            val startPosition = maxOf(0, fileLength - bufferSize).toInt()
            val readSize = (fileLength - startPosition).toInt()
            val buffer = ByteArray(bufferSize)

            // 从字节数组中复制数据到缓冲区
            System.arraycopy(byteArray, startPosition, buffer, 0, readSize)

            // 按照C++实现，从后向前搜索WPPM签名
            // 查找所有WPPM签名，找到类型为1的密码元数据
            val signatureBytes = WPS_PASSWORD_SIGNATURE.toByteArray()
            val signatureLength = signatureBytes.size

            // 从后向前搜索，找到最后一个类型为1的密码元数据
            for (i in readSize - signatureLength downTo 0) {
                var match = true
                for (j in 0 until signatureLength) {
                    if (buffer[i + j] != signatureBytes[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    Log.d(TAG, "找到WPPM签名，位置: ${startPosition + i}")

                    // 计算实际数据位置
                    val dataPosition = startPosition + i

                    // 检查剩余数据长度是否足够
                    if (fileLength - dataPosition < 15) { // Magic(4) + Version(2) + Type(1) + DataLength(4) + Checksum(4) = 15
                        Log.w(TAG, "数据不足，无法解析")
                        continue
                    }

                    // 读取元数据块头部信息
                    val dataInputStream = ByteArrayInputStream(
                        byteArray,
                        dataPosition,
                        (fileLength - dataPosition).toInt()
                    )

                    // 读取Magic（4字节）
                    val magic = ByteArray(4)
                    dataInputStream.read(magic)

                    // 读取Version（2字节）
                    val versionBytes = ByteArray(2)
                    dataInputStream.read(versionBytes)
                    val version = byteArrayToShort(versionBytes)

                    // 读取Type（1字节）
                    val type = dataInputStream.read().toByte()
                    Log.d(TAG, "元数据类型: $type, 版本: $version")

                    // 打印WPPM后的内容，方便排查问题
                    val metadataBuffer = ByteArray(50) // 读取足够的字节来查看内容
                    val bytesRead = dataInputStream.read(metadataBuffer)
                    Log.d(
                        TAG,
                        "WPPM后的内容: ${
                            metadataBuffer.sliceArray(0 until bytesRead)
                                .joinToString(", ") { it.toString(16).padStart(2, '0') }
                        }"
                    )

                    if (type == METADATA_TYPE_PASSWORD.toByte()) {
                        // 找到密码类型，重新创建输入流解析数据
                        val passwordInputStream = ByteArrayInputStream(
                            byteArray,
                            dataPosition,
                            (fileLength - dataPosition).toInt()
                        )
                        val password = parseExtraFieldData(passwordInputStream)
                        if (password != null) {
                            Log.d(TAG, "成功读取密码: $password")
                            return password
                        }
                    } else {
                        Log.w(TAG, "跳过非密码类型的元数据: $type")
                    }
                }
            }

            Log.d(TAG, "未找到WPPM密码数据")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从输入流读取密码失败", e)
            return null
        }
    }

    /**
     * 从输入流读取ZIP Extra Field中的uid（直接流读取模式）
     * 按照读数据.md文档要求：从文件尾部读取1KB数据来查找元数据块
     */
    fun readUidFromInputStream(inputStream: InputStream): String? {
        try {
            Log.d(TAG, "尝试从输入流读取uid")

            // 将输入流转换为字节数组以支持从尾部搜索
            val byteArray = inputStream.readBytes()
            val fileLength = byteArray.size.toLong()

            if (fileLength < 20) { // 最小Extra Field大小
                Log.d(TAG, "文件太小，无法包含uid数据")
                return null
            }

            // 按照读数据.md文档要求：从文件尾部读取1KB数据
            val bufferSize = 1024
            val startPosition = maxOf(0, fileLength - bufferSize).toInt()
            val readSize = (fileLength - startPosition).toInt()
            val buffer = ByteArray(bufferSize)

            // 从字节数组中复制数据到缓冲区
            System.arraycopy(byteArray, startPosition, buffer, 0, readSize)

            // 按照C++实现，从后向前搜索WPPM签名
            // 查找所有WPPM签名，找到类型为2的uid元数据
            val signatureBytes = WPS_PASSWORD_SIGNATURE.toByteArray()
            val signatureLength = signatureBytes.size

            // 从后向前搜索，找到最后一个类型为2的uid元数据
            for (i in readSize - signatureLength downTo 0) {
                var match = true
                for (j in 0 until signatureLength) {
                    if (buffer[i + j] != signatureBytes[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    Log.d(TAG, "找到WPPM签名，位置: ${startPosition + i}")

                    // 计算实际数据位置
                    val dataPosition = startPosition + i

                    // 检查剩余数据长度是否足够
                    if (fileLength - dataPosition < 15) { // Magic(4) + Version(2) + Type(1) + DataLength(4) + Checksum(4) = 15
                        Log.w(TAG, "数据不足，无法解析")
                        continue
                    }

                    // 读取元数据块头部信息
                    val dataInputStream = ByteArrayInputStream(
                        byteArray,
                        dataPosition,
                        (fileLength - dataPosition).toInt()
                    )

                    // 读取Magic（4字节）
                    val magic = ByteArray(4)
                    dataInputStream.read(magic)

                    // 读取Version（2字节）
                    val versionBytes = ByteArray(2)
                    dataInputStream.read(versionBytes)
                    val version = byteArrayToShort(versionBytes)

                    // 读取Type（1字节）
                    val type = dataInputStream.read().toByte()
                    Log.d(TAG, "元数据类型: $type, 版本: $version")

                    if (type == METADATA_TYPE_UID.toByte()) {
                        // 找到uid类型，重新创建输入流解析数据
                        val uidInputStream = ByteArrayInputStream(
                            byteArray,
                            dataPosition,
                            (fileLength - dataPosition).toInt()
                        )
                        val uid = parseExtraFieldDataForUid(uidInputStream)
                        if (uid != null) {
                            Log.d(TAG, "成功读取uid: $uid")
                            return uid
                        }
                    } else {
                        Log.w(TAG, "跳过非uid类型的元数据: $type")
                    }
                }
            }

            Log.d(TAG, "未找到WPPM uid数据")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从输入流读取uid失败", e)
            return null
        }
    }

    /**
     * 检测文件是否被锁定
     */
    private fun isFileLocked(file: File): Boolean {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                // 尝试写入一个字节并回退
                val position = raf.length()
                raf.seek(position)
                raf.writeByte(0)
                raf.seek(position)
                raf.writeByte(0)
                return false
            }
        } catch (e: Exception) {
            return true
        }
    }

    /**
     * 构建Extra Field数据
     * 按照读数据.md文档格式：Magic(4) + Version(2) + Type(1) + DataLength(4) + Data(N) + Checksum(4)
     */
    private fun buildExtraFieldData(type: Int, data: String): ByteArray {
        try {
            // 直接使用明文数据，按照文档要求Data部分是明文UTF-8
            val dataBytes = data.toByteArray(Charsets.UTF_8)

            // 构建数据结构
            val signature = WPS_PASSWORD_SIGNATURE.toByteArray()
            // 2字节版本号（小端序）
            val version = byteArrayOf(WPS_PASSWORD_VERSION.toByte(), 0)
            val typeBytes = byteArrayOf(type.toByte()) // 1字节类型
            // 4字节数据长度（小端序）
            val dataLength = intToByteArrayLittleEndian(dataBytes.size)

            // 计算CRC32校验和（计算范围：Magic到Data部分）
            val checksum = calculateCRC32Checksum(signature, version, typeBytes, dataLength, dataBytes)

            // 组合所有数据
            val totalLength = signature.size + version.size + typeBytes.size +
                    dataLength.size + dataBytes.size + checksum.size
            val result = ByteArray(totalLength)

            var offset = 0
            System.arraycopy(signature, 0, result, offset, signature.size)
            offset += signature.size

            System.arraycopy(version, 0, result, offset, version.size)
            offset += version.size

            System.arraycopy(typeBytes, 0, result, offset, typeBytes.size)
            offset += typeBytes.size

            System.arraycopy(dataLength, 0, result, offset, dataLength.size)
            offset += dataLength.size

            System.arraycopy(dataBytes, 0, result, offset, dataBytes.size)
            offset += dataBytes.size

            System.arraycopy(checksum, 0, result, offset, checksum.size)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "构建Extra Field数据失败", e)
            throw e
        }
    }

    /**
     * 解析Extra Field数据（从RandomAccessFile读取）
     * 按照读数据.md文档格式：Magic(4) + Version(2) + Type(1) + DataLength(4) + Data(N) + Checksum(4)
     */
    private fun parseExtraFieldData(raf: RandomAccessFile): String? {
        try {
            // 读取Magic（4字节）
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (!String(magic).equals(WPS_PASSWORD_SIGNATURE)) {
                Log.d(TAG, "Magic不匹配")
                return null
            }

            // 读取Version（2字节）
            val versionBytes = ByteArray(2)
            raf.readFully(versionBytes)
            val version = byteArrayToShort(versionBytes)
            if (version != WPS_PASSWORD_VERSION.toShort()) {
                Log.w(TAG, "版本不匹配: $version")
                // 可以添加版本兼容性处理
            }

            // 读取Type（1字节）
            val type = raf.readByte()
            if (type != METADATA_TYPE_PASSWORD.toByte()) {
                Log.w(TAG, "类型不是密码: $type")
                return null
            }

            // 读取Data Length（4字节）
            val dataLengthBytes = ByteArray(4)
            raf.readFully(dataLengthBytes)
            val dataLength = byteArrayToInt(dataLengthBytes)
            Log.d(TAG, "Data Length: $dataLength")

            // 检查文件剩余长度是否足够
            val currentPosition = raf.filePointer
            val remainingLength = raf.length() - currentPosition
            Log.d(TAG, "当前位置: $currentPosition, 剩余长度: $remainingLength")

            if (remainingLength < dataLength + 4) { // Data + Checksum
                Log.w(TAG, "文件剩余长度不足，无法读取完整数据")
                return null
            }

            // 读取Data（密码数据，UTF-8编码）
            val data = ByteArray(dataLength)
            raf.readFully(data)

            // 读取Checksum（4字节，CRC32）
            val checksum = ByteArray(4)
            raf.readFully(checksum)

            // 验证CRC32校验和（计算范围：Magic到Data部分）
            val calculatedChecksum = calculateCRC32Checksum(
                magic,
                versionBytes,
                byteArrayOf(type),
                dataLengthBytes,
                data
            )
            if (!checksum.contentEquals(calculatedChecksum)) {
                Log.e(TAG, "CRC32校验和不匹配，数据可能已损坏")
                return null
            }

            // 直接返回UTF-8编码的密码（文档中Data部分是明文UTF-8）
            return String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "解析Extra Field数据失败", e)
            return null
        }
    }

    /**
     * 解析Extra Field数据（从InputStream读取）
     * 按照读数据.md文档格式：Magic(4) + Version(2) + Type(1) + DataLength(4) + Data(N) + Checksum(4)
     */
    private fun parseExtraFieldData(inputStream: InputStream): String? {
        try {
            // 读取Magic（4字节）
            val magic = ByteArray(4)
            inputStream.read(magic)
            if (!String(magic).equals(WPS_PASSWORD_SIGNATURE)) {
                Log.d(TAG, "Magic不匹配")
                return null
            }

            // 读取Version（2字节）
            val versionBytes = ByteArray(2)
            inputStream.read(versionBytes)
            val version = byteArrayToShort(versionBytes)
            if (version != WPS_PASSWORD_VERSION.toShort()) {
                Log.w(TAG, "版本不匹配: $version")
                // 可以添加版本兼容性处理
            }

            // 读取Type（1字节）
            val type = inputStream.read().toByte()
            if (type != METADATA_TYPE_PASSWORD.toByte()) {
                Log.w(TAG, "类型不是密码: $type")
                return null
            }

            // 读取Data Length（4字节）
            val dataLengthBytes = ByteArray(4)
            inputStream.read(dataLengthBytes)
            val dataLength = byteArrayToInt(dataLengthBytes)
            Log.d(TAG, "Data Length: $dataLength")

            // 检查输入流是否有足够的数据
            if (dataLength > 10000) { // 合理的密码长度上限
                Log.w(TAG, "Data Length异常: $dataLength")
                return null
            }

            // 读取Data（密码数据，UTF-8编码）
            val data = ByteArray(dataLength)
            val bytesRead = inputStream.read(data)
            if (bytesRead != dataLength) {
                Log.w(TAG, "读取Data失败，期望: $dataLength, 实际: $bytesRead")
                return null
            }

            // 读取Checksum（4字节，CRC32）
            val checksum = ByteArray(4)
            val checksumRead = inputStream.read(checksum)
            if (checksumRead != 4) {
                Log.w(TAG, "读取Checksum失败，期望: 4, 实际: $checksumRead")
                return null
            }

            // 验证CRC32校验和（计算范围：Magic到Data部分）
            val calculatedChecksum = calculateCRC32Checksum(
                magic,
                versionBytes,
                byteArrayOf(type),
                dataLengthBytes,
                data
            )
            if (!checksum.contentEquals(calculatedChecksum)) {
                Log.e(TAG, "CRC32校验和不匹配，数据可能已损坏")
                return null
            }

            // 直接返回UTF-8编码的密码（文档中Data部分是明文UTF-8）
            return String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "解析Extra Field数据失败", e)
            return null
        }
    }

    /**
     * 解析Extra Field数据（从InputStream读取）用于uid
     * 按照读数据.md文档格式：Magic(4) + Version(2) + Type(1) + DataLength(4) + Data(N) + Checksum(4)
     */
    private fun parseExtraFieldDataForUid(inputStream: InputStream): String? {
        try {
            // 读取Magic（4字节）
            val magic = ByteArray(4)
            inputStream.read(magic)
            if (!String(magic).equals(WPS_PASSWORD_SIGNATURE)) {
                Log.d(TAG, "Magic不匹配")
                return null
            }

            // 读取Version（2字节）
            val versionBytes = ByteArray(2)
            inputStream.read(versionBytes)
            val version = byteArrayToShort(versionBytes)
            if (version != WPS_PASSWORD_VERSION.toShort()) {
                Log.w(TAG, "版本不匹配: $version")
                // 可以添加版本兼容性处理
            }

            // 读取Type（1字节）
            val type = inputStream.read().toByte()
            if (type != METADATA_TYPE_UID.toByte()) {
                Log.w(TAG, "类型不是uid: $type")
                return null
            }

            // 读取Data Length（4字节）
            val dataLengthBytes = ByteArray(4)
            inputStream.read(dataLengthBytes)
            val dataLength = byteArrayToInt(dataLengthBytes)
            Log.d(TAG, "Data Length: $dataLength")

            // 检查输入流是否有足够的数据
            if (dataLength > 10000) { // 合理的uid长度上限
                Log.w(TAG, "Data Length异常: $dataLength")
                return null
            }

            // 读取Data（uid数据，UTF-8编码）
            val data = ByteArray(dataLength)
            val bytesRead = inputStream.read(data)
            if (bytesRead != dataLength) {
                Log.w(TAG, "读取Data失败，期望: $dataLength, 实际: $bytesRead")
                return null
            }

            // 读取Checksum（4字节，CRC32）
            val checksum = ByteArray(4)
            val checksumRead = inputStream.read(checksum)
            if (checksumRead != 4) {
                Log.w(TAG, "读取Checksum失败，期望: 4, 实际: $checksumRead")
                return null
            }

            // 验证CRC32校验和（计算范围：Magic到Data部分）
            val calculatedChecksum = calculateCRC32Checksum(
                magic,
                versionBytes,
                byteArrayOf(type),
                dataLengthBytes,
                data
            )
            if (!checksum.contentEquals(calculatedChecksum)) {
                Log.e(TAG, "CRC32校验和不匹配，数据可能已损坏")
                return null
            }

            // 直接返回UTF-8编码的uid（文档中Data部分是明文UTF-8）
            return String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "解析Extra Field数据失败", e)
            return null
        }
    }

    /**
     * Int转ByteArray（小端序）
     */
    private fun intToByteArrayLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }

    /**
     * ByteArray转Int（小端序）
     */
    private fun byteArrayToInt(bytes: ByteArray): Int {
        // 小端序：低字节在前，高字节在后
        return (bytes[3].toInt() and 0xFF shl 24) or
                (bytes[2].toInt() and 0xFF shl 16) or
                (bytes[1].toInt() and 0xFF shl 8) or
                (bytes[0].toInt() and 0xFF)
    }

    /**
     * ByteArray转Short（小端序）
     */
    private fun byteArrayToShort(bytes: ByteArray): Short {
        // 小端序：低字节在前，高字节在后
        return ((bytes[1].toInt() and 0xFF shl 8) or (bytes[0].toInt() and 0xFF)).toShort()
    }

    /**
     * 计算CRC32校验和
     * 计算范围：Magic到Data部分
     * 输出小端序的CRC32值
     */
    private fun calculateCRC32Checksum(vararg dataArrays: ByteArray): ByteArray {
        try {
            val crc32 = java.util.zip.CRC32()
            for (data in dataArrays) {
                crc32.update(data)
            }
            val value = crc32.value.toInt()
            // 小端序：低字节在前，高字节在后
            return byteArrayOf(
                value.toByte(),
                (value shr 8).toByte(),
                (value shr 16).toByte(),
                (value shr 24).toByte()
            )
        } catch (e: Exception) {
            Log.e(TAG, "计算CRC32校验和失败", e)
            return ByteArray(4)
        }
    }
}
