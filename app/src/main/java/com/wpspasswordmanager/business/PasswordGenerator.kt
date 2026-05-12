package com.wpspasswordmanager.business

import java.security.SecureRandom

class PasswordGenerator private constructor() {

    companion object {
        private const val TAG = "PasswordGenerator"
        private var instance: PasswordGenerator? = null

        fun getInstance(): PasswordGenerator {
            if (instance == null) {
                instance = PasswordGenerator()
            }
            return instance!!
        }
    }

    private val secureRandom = SecureRandom()
    private val uppercaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val lowercaseLetters = "abcdefghijklmnopqrstuvwxyz"
    private val digits = "0123456789"
    private val specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private val allChars = uppercaseLetters + lowercaseLetters + digits + specialChars

    /**
     * 生成10位随机密码
     * 包含大小写字母、数字及特殊符号
     */
    fun generatePassword(): String {
        val password = StringBuilder(10)

        // 确保包含至少一个大写字母
        password.append(uppercaseLetters[secureRandom.nextInt(uppercaseLetters.length)])
        
        // 确保包含至少一个小写字母
        password.append(lowercaseLetters[secureRandom.nextInt(lowercaseLetters.length)])
        
        // 确保包含至少一个数字
        password.append(digits[secureRandom.nextInt(digits.length)])
        
        // 确保包含至少一个特殊符号
        password.append(specialChars[secureRandom.nextInt(specialChars.length)])
        
        // 填充剩余的字符
        for (i in 4 until 10) {
            password.append(allChars[secureRandom.nextInt(allChars.length)])
        }
        
        // 打乱密码顺序
        return shufflePassword(password.toString())
    }

    /**
     * 打乱密码字符顺序
     */
    private fun shufflePassword(password: String): String {
        val charArray = password.toCharArray()
        for (i in charArray.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = charArray[i]
            charArray[i] = charArray[j]
            charArray[j] = temp
        }
        return String(charArray)
    }

    /**
     * 生成指定长度的随机密码
     */
    fun generatePassword(length: Int): String {
        require(length >= 4) { "密码长度至少为4位" }
        
        val password = StringBuilder(length)

        // 确保包含至少一个大写字母
        password.append(uppercaseLetters[secureRandom.nextInt(uppercaseLetters.length)])
        
        // 确保包含至少一个小写字母
        password.append(lowercaseLetters[secureRandom.nextInt(lowercaseLetters.length)])
        
        // 确保包含至少一个数字
        password.append(digits[secureRandom.nextInt(digits.length)])
        
        // 确保包含至少一个特殊符号
        password.append(specialChars[secureRandom.nextInt(specialChars.length)])
        
        // 填充剩余的字符
        for (i in 4 until length) {
            password.append(allChars[secureRandom.nextInt(allChars.length)])
        }
        
        // 打乱密码顺序
        return shufflePassword(password.toString())
    }
}
