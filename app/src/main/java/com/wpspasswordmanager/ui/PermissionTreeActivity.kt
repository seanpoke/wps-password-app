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
        val ldapItems = if (ldapItemsJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<LdapItem>>() {}.type
            gson.fromJson<List<LdapItem>>(ldapItemsJson, type)
        } else {
            emptyList<LdapItem>()
        }

        // 构建树形结构
        buildTreeStructure(treeContainer, ldapItems, 0)

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
            val searchText = etSearch.text.toString()
            filterTreeItems(treeContainer, searchText)
        }

        // 搜索框回车事件
        etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val searchText = etSearch.text.toString()
                filterTreeItems(treeContainer, searchText)
                true
            } else {
                false
            }
        }
    }

    private fun buildTreeStructure(container: LinearLayout, ldapItems: List<LdapItem>, level: Int) {
        for (item in ldapItems) {
            // 创建部门节点
            val deptView = createDeptView(item, level)
            container.addView(deptView)

            // 添加员工节点
            for (employee in item.employeeList) {
                val employeeView = createEmployeeView(employee.name, level + 1)
                container.addView(employeeView)
            }

            // 递归处理子部门
            if (item.deptList.isNotEmpty()) {
                buildTreeStructure(container, item.deptList, level + 1)
            }
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

    private fun toggleDeptVisibility(deptView: LinearLayout, dept: LdapItem) {
        // 这里需要实现部门的展开/折叠逻辑
        // 暂时简单实现
        val toggleButton = deptView.getChildAt(0) as Button
        toggleButton.text = if (toggleButton.text == "▼") "▶" else "▼"
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
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                val checkBox = child.getChildAt(1) as CheckBox
                val text = checkBox.text.toString()
                child.visibility = if (text.contains(searchText, ignoreCase = true)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
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