package com.wpspasswordmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R

class TreeAdapter(val nodes: MutableList<TreeNode>, private val onItemClicked: (TreeNode) -> Unit, private val onAuthStateChanged: (TreeNode, Boolean) -> Unit) : RecyclerView.Adapter<TreeAdapter.TreeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tree_node_item, parent, false)
        return TreeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        val node = nodes[position]
        // 检查节点是否应该显示
        val shouldShow = shouldShowNode(node)
        if (shouldShow) {
            holder.itemView.visibility = View.VISIBLE
            // 恢复布局高度
            val params = holder.itemView.layoutParams
            if (params != null) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                holder.itemView.layoutParams = params
            }
            holder.bind(node, onItemClicked, onAuthStateChanged)
        } else {
            holder.itemView.visibility = View.GONE
            // 设置最小高度为0，避免占位
            val params = holder.itemView.layoutParams
            if (params != null) {
                params.height = 0
                holder.itemView.layoutParams = params
            }
        }
    }

    override fun getItemCount(): Int {
        return nodes.size
    }

    private fun shouldShowNode(node: TreeNode): Boolean {
        // 如果是根节点，直接显示
        if (node.parent == null) {
            return true
        }
        // 检查所有父节点是否都已展开
        var current = node.parent
        while (current != null) {
            if (!current.isExpanded) {
                return false
            }
            current = current.parent
        }
        return true
    }

    inner class TreeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val arrowImageView: ImageView = itemView.findViewById(R.id.iv_arrow)
        private val iconImageView: ImageView = itemView.findViewById(R.id.iv_icon)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_name)
        private val authImageView: ImageView = itemView.findViewById(R.id.iv_auth)
        private val checkBox: CheckBox = itemView.findViewById(R.id.cb_select)

        fun bind(node: TreeNode, onItemClicked: (TreeNode) -> Unit, onAuthStateChanged: (TreeNode, Boolean) -> Unit) {
            // 根据层级设置左侧缩进
            val params = itemView.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = node.level * 40 // 每层缩进40dp
            itemView.layoutParams = params

            // 设置名称
            nameTextView.text = node.name

            // 处理展开/折叠图标
            if (node.type == 0 && node.children.isNotEmpty()) {
                arrowImageView.visibility = View.VISIBLE
                arrowImageView.setImageResource(if (node.isExpanded) R.drawable.ic_expanded else R.drawable.ic_collapsed)
            } else {
                arrowImageView.visibility = View.GONE
            }

            // 区分部门和员工的图标
            iconImageView.setImageResource(if (node.type == 0) R.drawable.ic_dept else R.drawable.ic_employee)

            // 根据hasAuth处理权限标识
            authImageView.visibility = if (node.hasAuth) View.VISIBLE else View.GONE

            // 设置勾选状态，避免触发onCheckedChangeListener
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = node.hasAuth
            checkBox.setOnCheckedChangeListener {
                _, isChecked ->
                node.setAuthState(isChecked)
                onAuthStateChanged(node, isChecked)
            }

            // 点击事件
            itemView.setOnClickListener {
                onItemClicked(node)
            }
        }
    }
}
