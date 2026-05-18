package com.wpspasswordmanager.ui

/**
 * 统一的树节点模型
 * 
 * @param dn 唯一标识路径
 * @param name 显示名称
 * @param account 账号（员工节点使用）
 * @param type 类型：0-部门，1-员工
 * @param hasAuth 实际权限状态（后端返回，仅直接勾选的节点为true）
 * @param level 当前层级（用于缩进）
 * @param isExpanded 是否展开
 * @param parent 父节点引用（用于向上查找）
 * @param children 子节点列表
 * @param isIndeterminate 是否半勾选状态
 */
data class TreeNode(
    val dn: String,
    val name: String,
    val account: String?,
    val type: Int, // 0: 部门, 1: 员工
    var hasAuth: Boolean,
    
    // 以下是辅助展示的字段，不需要后台返回
    var level: Int = 0,
    var isExpanded: Boolean = false,
    var parent: TreeNode? = null,
    val children: MutableList<TreeNode> = mutableListOf(),
    var isIndeterminate: Boolean = false,
    var isGrayed: Boolean = false
) {
    /**
     * 切换节点的展开/折叠状态
     */
    fun toggleExpansion() {
        isExpanded = !isExpanded
    }
    
    /**
     * 设置节点的实际权限状态
     */
    fun setAuthState(auth: Boolean) {
        hasAuth = auth
    }
    
    /**
     * 设置节点的置灰状态
     */
    fun setGrayedState(grayed: Boolean) {
        isGrayed = grayed
    }
    
    /**
     * 递归设置所有子节点的置灰状态（包含循环子节点）
     */
    fun updateChildrenGrayedState(grayed: Boolean) {
        children.forEach { child ->
            child.isGrayed = grayed
            if (child.type == 0) {
                child.updateChildrenGrayedState(grayed)
            }
        }
    }
    
    /**
     * 递归清除所有子节点的状态（hasAuth和isGrayed）
     */
    fun clearChildrenState() {
        children.forEach { child ->
            child.hasAuth = false
            child.isGrayed = false
            child.isIndeterminate = false
            if (child.type == 0) {
                child.clearChildrenState()
            }
        }
    }
    
    /**
     * 获取所有子节点（包含所有层级）
     */
    fun getAllChildren(): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        children.forEach { child ->
            result.add(child)
            if (child.type == 0) {
                result.addAll(child.getAllChildren())
            }
        }
        return result
    }
    
    /**
     * 计算虚拟勾选状态（考虑父节点影响）
     * 当父节点hasAuth为true时，当前节点UI应显示为勾选状态
     */
    fun getVirtualHasAuth(): Boolean {
        if (hasAuth) {
            return true
        }
        // 检查父节点是否有权限
        var current = parent
        while (current != null) {
            if (current.hasAuth) {
                return true
            }
            current = current.parent
        }
        return false
    }
    
    /**
     * 递归更新所有子节点的权限状态
     * @param auth 是否勾选
     * @param updateAll 是否强制更新所有子节点（包括已手动勾选的）
     */
    fun updateChildrenAuthState(auth: Boolean, updateAll: Boolean = true) {
        children.forEach { child ->
            if (updateAll || !child.hasAuth) {
                child.isIndeterminate = false
                child.setAuthState(auth)
                if (child.type == 0) {
                    child.updateChildrenAuthState(auth, updateAll)
                }
            }
        }
    }
    
    /**
     * 检查是否所有子节点都已选中（基于实际hasAuth状态）
     */
    fun areAllChildrenSelected(): Boolean {
        if (children.isEmpty()) return true
        return children.all { it.hasAuth && (it.type == 1 || it.areAllChildrenSelected()) }
    }
    
    /**
     * 检查是否有子节点被选中（基于实际hasAuth状态）
     */
    fun hasSelectedChildren(): Boolean {
        if (children.isEmpty()) return false
        return children.any { it.hasAuth || (it.type == 0 && it.hasSelectedChildren()) }
    }
    
    /**
     * 检查是否所有子节点都被虚拟选中
     */
    fun areAllChildrenVirtualSelected(): Boolean {
        if (children.isEmpty()) return true
        return children.all { it.getVirtualHasAuth() && (it.type == 1 || it.areAllChildrenVirtualSelected()) }
    }
    
    /**
     * 检查是否有子节点被虚拟选中
     */
    fun hasVirtualSelectedChildren(): Boolean {
        if (children.isEmpty()) return false
        return children.any { it.getVirtualHasAuth() || (it.type == 0 && it.hasVirtualSelectedChildren()) }
    }
    
    /**
     * 获取所有直接勾选的子节点数量（不包括继承父节点权限的）
     */
    fun getDirectSelectedChildrenCount(): Int {
        return children.count { it.hasAuth }
    }
    
    /**
     * 判断是否需要显示半选状态
     */
    fun shouldShowIndeterminate(): Boolean {
        if (children.isEmpty()) return false
        val selectedCount = getDirectSelectedChildrenCount()
        return selectedCount > 0 && selectedCount < children.size
    }
    
    /**
     * 获取所有实际选中的节点（包括当前节点和所有子节点中hasAuth为true的）
     */
    fun getAllSelectedNodes(): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        if (hasAuth) {
            result.add(this)
        }
        children.forEach {
            if (it.type == 0) {
                result.addAll(it.getAllSelectedNodes())
            } else if (it.hasAuth) {
                result.add(it)
            }
        }
        return result
    }
    
    /**
     * 获取所有实际选中的部门节点DN
     */
    fun getAllSelectedDeptDns(): List<String> {
        val result = mutableListOf<String>()
        if (type == 0 && hasAuth) {
            result.add(dn)
        }
        children.forEach {
            if (it.type == 0) {
                result.addAll(it.getAllSelectedDeptDns())
            }
        }
        return result
    }
}