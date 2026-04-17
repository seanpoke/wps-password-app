package com.wpspasswordmanager.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.wpspasswordmanager.R

class PermissionTreeActivity : Activity() {

    companion object {
        const val EXTRA_LDAP_ITEMS = "ldap_items"
    }

    private lateinit var treeContainer: LinearLayout
    private lateinit var btnClose: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var ldapItems: List<LdapItem>

    // 存储部门与其子项的映射关系
    private val deptChildrenMap = mutableMapOf<LinearLayout, MutableList<LinearLayout>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_permission_tree)

        // 获取视图
        treeContainer = findViewById(R.id.tree_container)
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

        // 构建树形结构
        buildTreeStructure(treeContainer, this.ldapItems, 0)

        // 关闭按钮点击事件
        btnClose.setOnClickListener {
            finish()
        }

        // 全选按钮点击事件
        btnSelectAll.setOnClickListener {
            selectAllItems(treeContainer, true)
        }

        // 反选按钮点击事件
        btnDeselectAll.setOnClickListener {
            selectAllItems(treeContainer, false)
        }

        // 保存按钮点击事件
        btnSave.setOnClickListener {
            // 处理保存逻辑
            val selectedItems = getSelectedItems(treeContainer)
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
        filterTreeItems(treeContainer, searchText)
    }

    private fun buildTreeStructure(container: LinearLayout, ldapItems: List<LdapItem>, level: Int) {
        android.util.Log.d("PermissionTree", "开始构建树形结构，项目数量: ${ldapItems.size}")
        for (item in ldapItems) {
            // 创建部门节点
            val deptView = createDeptView(item, level)
            container.addView(deptView)

            // 存储部门的子项
            val children = mutableListOf<LinearLayout>()

            // 添加员工节点
            for (employee in item.employeeList) {
                val employeeView = createEmployeeView(employee.name, level + 1)
                container.addView(employeeView)
                children.add(employeeView)
                android.util.Log.d("PermissionTree", "添加员工: ${employee.name} 到部门: ${item.name}")
            }

            // 递归处理子部门
            if (item.deptList.isNotEmpty()) {
                val childDepts = mutableListOf<LinearLayout>()
                buildTreeStructureWithChildren(childDepts, container, item.deptList, level + 1)
                children.addAll(childDepts)
                android.util.Log.d("PermissionTree", "添加子部门到部门: ${item.name}")
            }

            deptChildrenMap[deptView] = children
            android.util.Log.d("PermissionTree", "部门 ${item.name} 的子项数量: ${children.size}")
            // 默认折叠部门
            toggleDeptVisibility(deptView, item, true)
        }
        android.util.Log.d("PermissionTree", "构建完成，deptChildrenMap大小: ${deptChildrenMap.size}")
    }

    private fun buildTreeStructureWithChildren(childDepts: MutableList<LinearLayout>, container: LinearLayout, ldapItems: List<LdapItem>, level: Int) {
        android.util.Log.d("PermissionTree", "开始构建子部门树形结构，项目数量: ${ldapItems.size}")
        for (item in ldapItems) {
            // 创建部门节点
            val deptView = createDeptView(item, level)
            container.addView(deptView)
            childDepts.add(deptView)

            // 存储部门的子项
            val children = mutableListOf<LinearLayout>()

            // 添加员工节点
            for (employee in item.employeeList) {
                val employeeView = createEmployeeView(employee.name, level + 1)
                container.addView(employeeView)
                children.add(employeeView)
                android.util.Log.d("PermissionTree", "添加员工: ${employee.name} 到部门: ${item.name}")
            }

            // 递归处理子部门
            if (item.deptList.isNotEmpty()) {
                val subChildDepts = mutableListOf<LinearLayout>()
                buildTreeStructureWithChildren(subChildDepts, container, item.deptList, level + 1)
                children.addAll(subChildDepts)
                childDepts.addAll(subChildDepts)
                android.util.Log.d("PermissionTree", "添加子部门到部门: ${item.name}")
            }

            deptChildrenMap[deptView] = children
            android.util.Log.d("PermissionTree", "部门 ${item.name} 的子项数量: ${children.size}")
            // 默认折叠部门
            toggleDeptVisibility(deptView, item, true)
        }
    }

    private fun createDeptView(dept: LdapItem, level: Int): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.setPadding(level * 40, 8, 8, 8)

        // 展开/折叠按钮
        val toggleButton = Button(this)
        toggleButton.text = if (dept.deptList.isNotEmpty()) "▼" else ""
        toggleButton.setPadding(4, 0, 4, 0)
        toggleButton.minWidth = 40
        toggleButton.setOnClickListener {
            toggleDeptVisibility(layout, dept)
        }

        // 勾选框
        val checkBox = CheckBox(this)
        checkBox.text = dept.name
        checkBox.textSize = 16f

        // 添加到布局
        layout.addView(toggleButton)
        layout.addView(checkBox)

        return layout
    }

    private fun createEmployeeView(account: String, level: Int): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.setPadding(level * 40, 4, 8, 4)

        // 占位视图
        val space = android.widget.Space(this)
        space.minimumWidth = 40

        // 勾选框
        val checkBox = CheckBox(this)
        checkBox.text = account
        checkBox.textSize = 14f

        // 添加到布局
        layout.addView(space)
        layout.addView(checkBox)

        return layout
    }

    private fun toggleDeptVisibility(deptView: LinearLayout, dept: LdapItem, initialCollapse: Boolean = false) {
        val toggleButton = deptView.getChildAt(0) as Button
        val isExpanded = toggleButton.text == "▼"
        val targetExpanded = if (initialCollapse) false else !isExpanded
        
        toggleButton.text = if (targetExpanded) "▼" else "▶"
        
        // 显示或隐藏子项
        val children = deptChildrenMap[deptView]
        children?.forEach { child ->
            child.visibility = if (targetExpanded) View.VISIBLE else View.GONE
        }
    }

    private fun selectAllItems(container: LinearLayout, select: Boolean) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                val checkBox = child.getChildAt(1) as CheckBox
                checkBox.isChecked = select
            }
        }
    }

    private fun getSelectedItems(container: LinearLayout): List<String> {
        val selectedItems = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                val checkBox = child.getChildAt(1) as CheckBox
                if (checkBox.isChecked) {
                    selectedItems.add(checkBox.text.toString())
                }
            }
        }
        return selectedItems
    }

    private fun filterTreeItems(container: LinearLayout, searchText: String) {
        android.util.Log.d("PermissionTree", "开始搜索: $searchText")
        if (searchText.isEmpty()) {
            // 如果搜索文本为空，恢复默认状态（折叠所有部门）
            deptChildrenMap.forEach { (deptView, _) ->
                val toggleButton = deptView.getChildAt(0) as Button
                if (toggleButton.text == "▼") {
                    val deptName = (deptView.getChildAt(1) as CheckBox).text.toString()
                    // 找到对应的LdapItem
                    val ldapItem = findLdapItemByName(deptName)
                    if (ldapItem != null) {
                        toggleDeptVisibility(deptView, ldapItem, true)
                    }
                }
            }
            android.util.Log.d("PermissionTree", "搜索文本为空，恢复默认状态")
            return
        }
        
        // 搜索所有项目
        val matchingItems = mutableListOf<LinearLayout>()
        android.util.Log.d("PermissionTree", "deptChildrenMap大小: ${deptChildrenMap.size}")
        
        // 遍历所有部门视图
        deptChildrenMap.forEach { (deptView, children) ->
            val deptCheckBox = deptView.getChildAt(1) as CheckBox
            val deptText = deptCheckBox.text.toString()
            android.util.Log.d("PermissionTree", "检查部门: $deptText")
            if (deptText.contains(searchText, ignoreCase = true)) {
                matchingItems.add(deptView)
                android.util.Log.d("PermissionTree", "匹配部门: $deptText")
            }
            
            // 遍历部门的所有子项，包括被折叠的员工
            android.util.Log.d("PermissionTree", "部门 $deptText 的子项数量: ${children.size}")
            children.forEach { child ->
                val childCheckBox = child.getChildAt(1) as CheckBox
                val childText = childCheckBox.text.toString()
                android.util.Log.d("PermissionTree", "检查员工: $childText")
                if (childText.contains(searchText, ignoreCase = true)) {
                    matchingItems.add(child)
                    android.util.Log.d("PermissionTree", "匹配员工: $childText")
                }
            }
        }
        
        // 遍历所有直接子项，确保没有遗漏
        android.util.Log.d("PermissionTree", "container子项数量: ${container.childCount}")
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                val checkBox = child.getChildAt(1) as CheckBox
                val text = checkBox.text.toString()
                android.util.Log.d("PermissionTree", "检查直接子项: $text")
                if (text.contains(searchText, ignoreCase = true)) {
                    if (!matchingItems.contains(child)) {
                        matchingItems.add(child)
                        android.util.Log.d("PermissionTree", "匹配直接子项: $text")
                    }
                }
            }
        }
        
        android.util.Log.d("PermissionTree", "匹配项目数量: ${matchingItems.size}")
        if (matchingItems.isEmpty()) {
            // 如果没有匹配项，隐藏所有项目
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is LinearLayout) {
                    child.visibility = View.GONE
                }
            }
            android.util.Log.d("PermissionTree", "没有匹配项，隐藏所有项目")
            return
        }
        
        // 隐藏所有项目
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                child.visibility = View.GONE
            }
        }
        android.util.Log.d("PermissionTree", "隐藏所有项目")
        
        // 显示匹配的项目及其所有父部门
        matchingItems.forEach { item ->
            val checkBox = item.getChildAt(1) as CheckBox
            val text = checkBox.text.toString()
            item.visibility = View.VISIBLE
            android.util.Log.d("PermissionTree", "显示匹配项目: $text")
            // 找到并显示所有父部门
            showParentDepts(item)
        }
    }
    
    private fun resetAllItemsVisibility(container: LinearLayout) {
        // 重置所有直接子视图的可见性
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                child.visibility = View.VISIBLE
            }
        }
    }
    
    private fun showParentDepts(item: LinearLayout) {
        // 找到包含该项目的部门
        deptChildrenMap.forEach { (deptView, children) ->
            if (children.contains(item)) {
                deptView.visibility = View.VISIBLE
                // 展开该部门
                val toggleButton = deptView.getChildAt(0) as Button
                if (toggleButton.text == "▶") {
                    val deptName = (deptView.getChildAt(1) as CheckBox).text.toString()
                    val ldapItem = findLdapItemByName(deptName)
                    if (ldapItem != null) {
                        toggleDeptVisibility(deptView, ldapItem)
                    }
                }
                // 递归显示父部门
                showParentDepts(deptView)
            }
        }
    }
    
    private fun findLdapItemByName(name: String): LdapItem? {
        return findLdapItemByNameRecursive(ldapItems, name)
    }
    
    private fun findLdapItemByNameRecursive(items: List<LdapItem>, name: String): LdapItem? {
        for (item in items) {
            if (item.name == name) {
                return item
            }
            val found = findLdapItemByNameRecursive(item.deptList, name)
            if (found != null) {
                return found
            }
            val employeeFound = findLdapItemByNameRecursive(item.employeeList, name)
            if (employeeFound != null) {
                return employeeFound
            }
        }
        return null
    }

    // LdapItem数据类
    data class LdapItem(
        val name: String,
        val fullPath: String,
        val deptList: List<LdapItem>,
        val employeeList: List<LdapItem>
    )
}