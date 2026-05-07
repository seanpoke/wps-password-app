package com.wpspasswordmanager.business

/**
 * 自定义有序不重复集合，满足以下要求：
 * 1. 保持元素插入倒序（最新插入的元素在最前）
 * 2. 确保集合内不存在重复元素
 * 3. 当插入重复元素时，将其移动至集合首位
 */
class OrderedSet<T> {
    private val linkedHashMap = LinkedHashMap<T, Unit>()
    
    /**
     * 添加元素到集合
     * 如果元素已存在，将其移动至集合首位
     */
    fun add(element: T) {
        linkedHashMap.remove(element) // 先移除（如果存在）
        linkedHashMap[element] = Unit // 再添加到末尾
        // 限制集合大小不超过5个
        if (linkedHashMap.size > 5) {
            // 移除最旧的元素（第一个元素）
            val oldestElement = linkedHashMap.keys.iterator().next()
            linkedHashMap.remove(oldestElement)
        }
    }
    
    /**
     * 移除元素
     */
    fun remove(element: T) {
        linkedHashMap.remove(element)
    }
    
    /**
     * 检查集合是否包含元素
     */
    fun contains(element: T): Boolean {
        return linkedHashMap.containsKey(element)
    }
    
    /**
     * 获取集合大小
     */
    val size: Int
        get() = linkedHashMap.size
    
    /**
     * 检查集合是否为空
     */
    fun isEmpty(): Boolean {
        return linkedHashMap.isEmpty()
    }
    
    /**
     * 清空集合
     */
    fun clear() {
        linkedHashMap.clear()
    }
    
    /**
     * 获取倒序遍历的迭代器（最新插入的元素在前）
     */
    operator fun iterator(): Iterator<T> {
        return linkedHashMap.keys.reversed().iterator()
    }
    
    /**
     * 获取倒序列表
     */
    fun toList(): List<T> {
        return linkedHashMap.keys.reversed()
    }
    
    /**
     * 获取第一个元素（最新插入的元素）
     */
    fun firstOrNull(): T? {
        return linkedHashMap.keys.lastOrNull()
    }
}

data class FileMeta(
    // --- 基础信息 ---
    val filePath: String,            // 文件绝对路径 (作为唯一标识)
    
    // --- 元数据信息 ---
    var uid: String?,    // 文件权限标识
    var currentPassword: String?,    // 旧密码：当前已确认生效的密码
    var pendingPasswordList: OrderedSet<String>? = null, // 待定密码：无障碍服务捕获到的新密码集合
    var currentKeyVersion: String = "default",  // 密钥版本，默认值为"default"
    
    // --- 权限信息 ---
    var ownerAccount: String? = null, // 文档所属账号
    var ownerName: String? = null,    // 文档所属名称
    var readAuth: Boolean = false,    // 读权限
    var writeAuth: Boolean = false,   // 写权限
)