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
)
