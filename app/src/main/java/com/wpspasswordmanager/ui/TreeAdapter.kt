package com.wpspasswordmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R

class TreeAdapter(private val onItemClicked: (TreeNode) -> Unit) : ListAdapter<TreeNode, TreeAdapter.TreeViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TreeNode>() {
            override fun areItemsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean {
                return oldItem.dn == newItem.dn
            }

            override fun areContentsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tree_node_item, parent, false)
        return TreeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        val node = currentList[position]
        holder.bind(node, onItemClicked)
    }

    inner class TreeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val arrowImageView: ImageView = itemView.findViewById(R.id.iv_arrow)
        private val iconImageView: ImageView = itemView.findViewById(R.id.iv_icon)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_name)
        private val authImageView: ImageView = itemView.findViewById(R.id.iv_auth)
        private val checkBox: CheckBox = itemView.findViewById(R.id.cb_select)

        fun bind(node: TreeNode, onItemClicked: (TreeNode) -> Unit) {
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

            // 设置勾选状态
            checkBox.isChecked = node.hasAuth

            // 点击事件
            itemView.setOnClickListener {
                onItemClicked(node)
            }

            // 勾选框点击事件
            checkBox.setOnCheckedChangeListener {
                _, isChecked ->
                node.hasAuth = isChecked
            }
        }
    }
}
