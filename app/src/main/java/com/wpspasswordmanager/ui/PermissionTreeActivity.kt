package com.wpspasswordmanager.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R

class PermissionTreeActivity : Activity() {

    companion object {
        const val EXTRA_LDAP_ITEMS = "ldap_items"
    }

    private lateinit var treeRecyclerView: RecyclerView
    private lateinit var btnClose: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var ldapItems: List<LdapItem>
    private lateinit var treeAdapter: TreeAdapter
    private val flattenedNodes = mutableListOf<TreeNode>()
    private var rootTreeNodes: List<TreeNode> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_permission_tree)

        // 获取视图
        treeRecyclerView = findViewById(R.id.tree_recycler_view)
        btnClose = findViewById(R.id.btn_close)
        btnSelectAll = findViewById(R.id.btn_select_all)
        btnDeselectAll = findViewById(R.id.btn_deselect_all)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        etSearch = findViewById(R.id.et_search)
        btnSearch = findViewById(R.id.btn_search)

        // 获取传递的LdapItems数据
        val ldapItemsJson = intent.getStringExtra(EXTRA_LDAP_ITEMS)
        this.ldapItems = if (ldapItemsJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<LdapItem>>() {}.type
            gson.fromJson<List<LdapItem>>(ldapItemsJson, type)
        } else {
            emptyList<LdapItem>()
        }

        // 转换为TreeNode并扁平化
        rootTreeNodes = convertToTreeNodes(ldapItems, 0)
        flattenTreeNodes(rootTreeNodes, flattenedNodes)

        // 初始化RecyclerView
        treeAdapter = TreeAdapter(
            nodes = flattenedNodes,
            onItemClicked = { node ->
                toggleNodeExpansion(node)
            },
            onAuthStateChanged = { node, isChecked ->
                handleAuthStateChanged(node, isChecked)
            }
        )
        treeRecyclerView.layoutManager = LinearLayoutManager(this)
        treeRecyclerView.adapter = treeAdapter

        // 关闭按钮点击事件
        btnClose.setOnClickListener {
            finish()
        }

        // 全选按钮点击事件
        btnSelectAll.setOnClickListener {
            selectAllItems(true)
        }

        // 反选按钮点击事件
        btnDeselectAll.setOnClickListener {
            selectAllItems(false)
        }

        // 保存按钮点击事件
        btnSave.setOnClickListener {
            // 处理保存逻辑
            val selectedItems = getSelectedItems()
            Toast.makeText(this, "保存选择的权限: ${selectedItems.size}", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 取消按钮点击事件
        btnCancel.setOnClickListener {
            finish()
        }

        // 搜索按钮点击事件
        btnSearch.setOnClickListener {
            performSearch()
        }

        // 搜索框回车事件
        etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // 搜索框文本变化事件，添加防抖
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            private var searchHandler = android.os.Handler()
            private val SEARCH_DELAY = 300L // 300ms延迟

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 移除之前的搜索任务
                searchHandler.removeCallbacksAndMessages(null)
                // 延迟执行搜索
                searchHandler.postDelayed({ performSearch() }, SEARCH_DELAY)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun performSearch() {
        val searchText = etSearch.text.toString()
        android.util.Log.d("PermissionTree", "执行搜索: $searchText")
        if (searchText.isEmpty()) {
            resetAllNodesExpansion(rootTreeNodes)
            flattenedNodes.clear()
            flattenTreeNodes(rootTreeNodes, flattenedNodes)
            treeAdapter.notifyDataSetChanged()
            return
        }

        resetAllNodesExpansion(rootTreeNodes)

        val matchingNodes = mutableListOf<TreeNode>()
        findMatchingNodes(rootTreeNodes, searchText, matchingNodes)

        matchingNodes.forEach { node ->
            expandAncestors(node)
        }

        flattenedNodes.clear()
        flattenTreeNodes(rootTreeNodes, flattenedNodes)
        treeAdapter.notifyDataSetChanged()
    }

    private fun resetAllNodesExpansion(nodes: List<TreeNode>) {
        for (node in nodes) {
            node.isExpanded = false
            if (node.children.isNotEmpty()) {
                resetAllNodesExpansion(node.children)
            }
        }
    }

    private fun expandAncestors(node: TreeNode) {
        var current = node.parent
        while (current != null) {
            current.isExpanded = true
            current = current.parent
        }
    }

    private fun convertToTreeNodes(ldapItems: List<LdapItem>, level: Int): List<TreeNode> {
        val nodes = mutableListOf<TreeNode>()
        for (item in ldapItems) {
            // 创建部门节点
            val deptNode = TreeNode(
                dn = item.fullPath,
                name = item.name,
                account = null,
                type = 0, // 0: 部门
                hasAuth = false, // 初始状态为未勾选
                level = level,
                isExpanded = false
            )
            nodes.add(deptNode)

            // 添加员工节点
            for (employee in item.employeeList) {
                val empNode = TreeNode(
                    dn = employee.fullPath,
                    name = employee.name,
                    account = employee.name, // 使用name作为account
                    type = 1, // 1: 员工
                    hasAuth = false, // 初始状态为未勾选
                    level = level + 1,
                    parent = deptNode
                )
                deptNode.children.add(empNode)
            }

            // 递归处理子部门
            if (item.deptList.isNotEmpty()) {
                val childDeptNodes = convertToTreeNodes(item.deptList, level + 1)
                childDeptNodes.forEach { it.parent = deptNode }
                deptNode.children.addAll(childDeptNodes)
            }
        }
        return nodes
    }

    private fun flattenTreeNodes(nodes: List<TreeNode>, result: MutableList<TreeNode>) {
        for (node in nodes) {
            result.add(node)
            // 总是添加所有子节点，不管是否展开
            if (node.children.isNotEmpty()) {
                flattenTreeNodes(node.children, result)
            }
        }
    }

    private fun toggleNodeExpansion(node: TreeNode) {
        if (node.type == 0 && node.children.isNotEmpty()) {
            node.toggleExpansion()
            // 直接更新列表，不重新创建节点
            treeAdapter.notifyDataSetChanged()
        }
    }

    private fun selectAllItems(select: Boolean) {
        flattenedNodes.forEach { node ->
            if (node.type == 0) {
                node.setAuthState(select)
                node.updateChildrenAuthState(select)
            } else {
                node.setAuthState(select)
            }
        }
        treeAdapter.notifyDataSetChanged()
    }

    private fun handleAuthStateChanged(node: TreeNode, isChecked: Boolean) {
        // 更新当前节点状态
        node.setAuthState(isChecked)
        
        // 如果是部门节点，递归更新子节点
        if (node.type == 0) {
            node.updateChildrenAuthState(isChecked)
        }
        
        // 更新父节点状态
        updateParentNodeState(node.parent)
        
        // 通知适配器更新
        treeAdapter.notifyDataSetChanged()
    }

    private fun updateParentNodeState(parent: TreeNode?) {
        if (parent == null) return
        
        val allSelected = parent.areAllChildrenSelected()
        val anySelected = parent.hasSelectedChildren()
        
        if (allSelected) {
            parent.setAuthState(true)
        } else if (anySelected) {
            // 保持父节点的当前状态，这里可以根据需求调整
        } else {
            parent.setAuthState(false)
        }
        
        // 递归更新上一级父节点
        updateParentNodeState(parent.parent)
    }

    private fun getSelectedItems(): List<String> {
        return flattenedNodes.filter { it.hasAuth }.map { it.name }
    }

    private fun findMatchingNodes(nodes: List<TreeNode>, searchText: String, result: MutableList<TreeNode>) {
        for (node in nodes) {
            if (node.name.contains(searchText, ignoreCase = true)) {
                result.add(node)
            }
            if (node.children.isNotEmpty()) {
                findMatchingNodes(node.children, searchText, result)
            }
        }
    }

    // LdapItem数据类
    data class LdapItem(
        val name: String,
        val fullPath: String,
        val deptList: List<LdapItem>,
        val employeeList: List<LdapItem>
    )
}