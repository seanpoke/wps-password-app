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

    var isSearchMode = false


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tree_node_item, parent, false)
        return TreeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        val node = nodes[position]
        val shouldShow = shouldShowNode(node)

        if (shouldShow) {
            holder.itemView.visibility = View.VISIBLE
            val params = holder.itemView.layoutParams
            if (params != null) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                holder.itemView.layoutParams = params
            }
            holder.bind(node, onItemClicked, onAuthStateChanged)
        } else {
            holder.itemView.visibility = View.GONE
            val params = holder.itemView.layoutParams
            if (params != null) {
                params.height = 0
                holder.itemView.layoutParams = params
            }
        }
    }

    override fun getItemCount(): Int {
        val count = nodes.size
        return count
    }

    private fun shouldShowNode(node: TreeNode): Boolean {
        if (isSearchMode) {
            return true
        }
        if (node.parent == null) {
            return true
        }
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
                arrowImageView.visibility = View.INVISIBLE
            }

            // 区分部门和员工的图标
            iconImageView.setImageResource(if (node.type == 0) R.drawable.ic_dept else R.drawable.ic_employee)

            // 半勾选状态显示半勾选图标，其他状态不显示图标
            if (node.type == 0 && node.isIndeterminate) {
                authImageView.visibility = View.VISIBLE
                authImageView.setImageResource(R.drawable.ic_indeterminate_checkbox)
            } else {
                authImageView.visibility = View.GONE
            }

            // 设置勾选状态，避免触发onCheckedChangeListener
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = node.hasAuth
            checkBox.setOnCheckedChangeListener {
                _, isChecked ->
                node.isIndeterminate = false
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
