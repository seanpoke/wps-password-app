package com.wpspasswordmanager.ui

/**
 * 统一的树节点模型
 */
data class TreeNode(
    val dn: String,
    val name: String,
    val account: String?,
    val type: Int, // 0: 部门, 1: 员工
    var hasAuth: Boolean,
    
    // 以下是辅助展示的字段，不需要后台返回
    var level: Int = 0,          // 当前层级（用于缩进）
    var isExpanded: Boolean = false, // 是否展开
    var parent: TreeNode? = null,    // 父节点引用（可选，用于向上查找）
    val children: MutableList<TreeNode> = mutableListOf() // 统一的子节点列表
) {
    /**
     * 切换节点的展开/折叠状态
     */
    fun toggleExpansion() {
        isExpanded = !isExpanded
    }
    
    /**
     * 设置节点的权限状态
     */
    fun setAuthState(auth: Boolean) {
        hasAuth = auth
    }
    
    /**
     * 递归更新所有子节点的权限状态
     */
    fun updateChildrenAuthState(auth: Boolean) {
        children.forEach {
            it.setAuthState(auth)
            if (it.type == 0) {
                it.updateChildrenAuthState(auth)
            }
        }
    }
    
    /**
     * 检查是否所有子节点都已选中
     */
    fun areAllChildrenSelected(): Boolean {
        if (children.isEmpty()) return true
        return children.all { it.hasAuth && (it.type == 1 || it.areAllChildrenSelected()) }
    }
    
    /**
     * 检查是否有子节点被选中
     */
    fun hasSelectedChildren(): Boolean {
        if (children.isEmpty()) return false
        return children.any { it.hasAuth || (it.type == 0 && it.hasSelectedChildren()) }
    }
}
