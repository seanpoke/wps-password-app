package com.wpspasswordmanager.business

import android.util.Log
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ZipExtraFieldManager private constructor() {

    companion object {
        private const val TAG = "ZipExtraFieldManager"
        private const val WPS_PASSWORD_SIGNATURE = "WPPM"  // 4字节Magic
        private const val WPS_PASSWORD_VERSION = 1
        private const val METADATA_TYPE_PASSWORD = 1  // 元数据类型：1=密码
        private const val ENCRYPTION_KEY = "wps_password_manager_key"
        private const val ENCRYPTION_IV = "wps_password_iv"
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
     * 写入密码到ZIP文件的Extra Field
     */
    fun writePassword(file: File, password: String): Boolean {
        if (!file.exists() || !file.canWrite()) {
            Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 文件不存在或不可写: ${file.absolutePath}")
            return false
        }

        var retryCount = 0
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 尝试写入密码到文件: ${file.absolutePath}, 密码: '$password'")
                
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
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 未找到旧的WPPM标记或删除失败")
                }

                // 构建Extra Field数据
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 开始构建Extra Field数据")
                val extraFieldData = buildExtraFieldData(password)
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] Extra Field数据构建完成，长度: ${extraFieldData.size} bytes")
                
                // 写入到文件尾部
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 开始写入Extra Field数据到文件尾部")
                RandomAccessFile(file, "rw").use { raf ->
                    val fileLength = raf.length()
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 文件当前长度: $fileLength bytes")
                    raf.seek(fileLength)
                    raf.write(extraFieldData)
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 数据写入完成，文件新长度: ${fileLength + extraFieldData.size} bytes")
                }

                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 密码写入成功")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 写入密码失败", e)
                retryCount++
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.w(TAG, "[时间戳: ${System.currentTimeMillis()}] 重试写入... ($retryCount/$MAX_RETRY_COUNT)")
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
                val tempFile = File.createTempFile("temp", ".tmp")
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
                if (file.delete() && tempFile.renameTo(file)) {
                    Log.d(TAG, "成功删除旧的WPPM标记并替换文件")
                    return true
                } else {
                    Log.e(TAG, "替换文件失败")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除旧WPPM标记失败", e)
            return false
        }
    }
    
    /**
     * 写入密码到ZIP文件的Extra Field（接受Context参数）
     */
    fun writePassword(context: android.content.Context, filePath: String, password: String): Boolean {
        val file = File(filePath)
        return writePassword(file, password)
    }

    /**
     * 使用ParcelFileDescriptor直接操作写入密码（方案5）
     * 按照核心流程文档要求：使用"wa"模式（Write Append）直接在文件末尾追加数据
     */
    fun writePasswordWithParcelFileDescriptor(context: android.content.Context, uri: android.net.Uri, password: String): Boolean {
        var retryCount = 0
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                Log.d(TAG, "尝试使用ParcelFileDescriptor直接操作写入密码: $uri, 重试次数: $retryCount")
                
                // 检查Content URI权限
                try {
                    val contentResolver = context.contentResolver
                    // 尝试获取Content URI的元数据，检查权限
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use { 
                        if (it.moveToFirst()) {
                            Log.d(TAG, "Content URI权限检查成功")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Content URI权限被拒绝", e)
                    // 尝试请求临时权限
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        intent.type = "*/*"
                        intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false)
                        Log.d(TAG, "建议用户通过系统文件选择器授予权限")
                    } catch (innerE: Exception) {
                        Log.e(TAG, "创建权限请求Intent失败", innerE)
                    }
                    return false
                }
                
                // 尝试不同的打开模式
                val modes = arrayOf("wa", "w", "rw")
                var pfd: android.os.ParcelFileDescriptor? = null
                
                for (mode in modes) {
                    try {
                        Log.d(TAG, "尝试以模式 $mode 打开ParcelFileDescriptor")
                        pfd = context.contentResolver.openFileDescriptor(uri, mode)
                        if (pfd != null) {
                            Log.d(TAG, "成功以模式 $mode 打开ParcelFileDescriptor")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "以模式 $mode 打开ParcelFileDescriptor失败", e)
                    }
                }
                
                if (pfd != null) {
                    pfd.use { 
                        // 获取文件长度
                        val fileLength = pfd.statSize
                        Log.d(TAG, "文件长度: $fileLength")
                        
                        // 构建Extra Field数据
                        val extraFieldData = buildExtraFieldData(password)
                        Log.d(TAG, "Extra Field数据长度: ${extraFieldData.size}")
                        
                        // 使用FileOutputStream写入到文件尾部
                        val fos = android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                        fos.use {
                            // 定位到文件末尾
                            fos.channel.position(fileLength)
                            // 写入数据
                            fos.write(extraFieldData)
                            fos.flush()
                        }
                        
                        Log.d(TAG, "使用ParcelFileDescriptor写入密码成功")
                        return true
                    }
                } else {
                    Log.e(TAG, "无法打开ParcelFileDescriptor")
                    retryCount++
                    if (retryCount < MAX_RETRY_COUNT) {
                        Log.w(TAG, "重试打开ParcelFileDescriptor... ($retryCount/$MAX_RETRY_COUNT)")
                        Thread.sleep(RETRY_DELAY_MS.toLong())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "使用ParcelFileDescriptor写入密码失败", e)
                retryCount++
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.w(TAG, "重试写入... ($retryCount/$MAX_RETRY_COUNT)")
                    Thread.sleep(RETRY_DELAY_MS.toLong())
                }
            }
        }
        
        Log.e(TAG, "达到最大重试次数，写入失败")
        return false
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
                    val dataInputStream = ByteArrayInputStream(byteArray, dataPosition, (fileLength - dataPosition).toInt())
                    
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
                    Log.d(TAG, "WPPM后的内容: ${metadataBuffer.sliceArray(0 until bytesRead).joinToString(", ") { it.toString(16).padStart(2, '0') }}")
                    
                    if (type == METADATA_TYPE_PASSWORD.toByte()) {
                        // 找到密码类型，重新创建输入流解析数据
                        val passwordInputStream = ByteArrayInputStream(byteArray, dataPosition, (fileLength - dataPosition).toInt())
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
     * 检查文件是否包含密码
     */
    fun hasPassword(file: File): Boolean {
        return readPassword(file) != null
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
    private fun buildExtraFieldData(password: String): ByteArray {
        try {
            // 直接使用明文密码，按照文档要求Data部分是明文UTF-8
            val passwordBytes = password.toByteArray(Charsets.UTF_8)
            
            // 构建数据结构
            val signature = WPS_PASSWORD_SIGNATURE.toByteArray()
            // 2字节版本号（小端序）
            val version = byteArrayOf(WPS_PASSWORD_VERSION.toByte(), 0)
            val type = byteArrayOf(METADATA_TYPE_PASSWORD.toByte()) // 1字节类型
            // 4字节数据长度（小端序）
            val dataLength = intToByteArrayLittleEndian(passwordBytes.size)
            
            // 计算CRC32校验和（计算范围：Magic到Data部分）
            val checksum = calculateCRC32Checksum(signature, version, type, dataLength, passwordBytes)
            
            // 组合所有数据
            val totalLength = signature.size + version.size + type.size + 
                             dataLength.size + passwordBytes.size + checksum.size
            val result = ByteArray(totalLength)
            
            var offset = 0
            System.arraycopy(signature, 0, result, offset, signature.size)
            offset += signature.size
            
            System.arraycopy(version, 0, result, offset, version.size)
            offset += version.size
            
            System.arraycopy(type, 0, result, offset, type.size)
            offset += type.size
            
            System.arraycopy(dataLength, 0, result, offset, dataLength.size)
            offset += dataLength.size
            
            System.arraycopy(passwordBytes, 0, result, offset, passwordBytes.size)
            offset += passwordBytes.size
            
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
            val calculatedChecksum = calculateCRC32Checksum(magic, versionBytes, byteArrayOf(type), dataLengthBytes, data)
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
            val calculatedChecksum = calculateCRC32Checksum(magic, versionBytes, byteArrayOf(type), dataLengthBytes, data)
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
     * 搜索签名（从前向后）
     */
    private fun findSignature(buffer: ByteArray, length: Int): Int {
        val signatureBytes = WPS_PASSWORD_SIGNATURE.toByteArray()
        val signatureLength = signatureBytes.size
        
        for (i in 0..length - signatureLength) {
            var match = true
            for (j in 0 until signatureLength) {
                if (buffer[i + j] != signatureBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                return i
            }
        }
        return -1
    }

    /**
     * 搜索签名（从后向前）
     * 参考C++实现，从后向前搜索WPPM签名
     */
    private fun findSignatureFromEnd(buffer: ByteArray, length: Int): Int {
        val signatureBytes = WPS_PASSWORD_SIGNATURE.toByteArray()
        val signatureLength = signatureBytes.size
        
        // 从后向前搜索，找到最后一个匹配的签名
        for (i in length - signatureLength downTo 0) {
            var match = true
            for (j in 0 until signatureLength) {
                if (buffer[i + j] != signatureBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                return i
            }
        }
        return -1
    }

    /**
     * 加密密码
     */
    private fun encryptPassword(password: String): ByteArray {
        try {
            val key = generateKey()
            val iv = generateIV()
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)
            
            return cipher.doFinal(password.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "加密密码失败", e)
            throw e
        }
    }

    /**
     * 解密密码
     */
    private fun decryptPassword(encryptedPassword: ByteArray): String {
        try {
            val key = generateKey()
            val iv = generateIV()
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            
            val decrypted = cipher.doFinal(encryptedPassword)
            return String(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "解密密码失败", e)
            throw e
        }
    }

    /**
     * 生成加密密钥
     */
    private fun generateKey(): SecretKeySpec {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(ENCRYPTION_KEY.toByteArray())
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * 生成初始化向量
     */
    private fun generateIV(): IvParameterSpec {
        val ivBytes = MessageDigest.getInstance("MD5").digest(ENCRYPTION_IV.toByteArray())
        return IvParameterSpec(ivBytes)
    }

    /**
     * 计算校验和
     */
    private fun calculateChecksum(data: ByteArray): ByteArray {
        try {
            val checksum = MessageDigest.getInstance("MD5").digest(data)
            val result = ByteArray(4)
            System.arraycopy(checksum, 0, result, 0, minOf(4, checksum.size))
            return result
        } catch (e: Exception) {
            Log.e(TAG, "计算校验和失败", e)
            return ByteArray(4)
        }
    }

    /**
     * Int转ByteArray（大端序）
     */
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
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
