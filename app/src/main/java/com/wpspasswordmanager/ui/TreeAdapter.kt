package com.wpspasswordmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R

class TreeAdapter(
    val nodes: MutableList<TreeNode>,
    private val onItemClicked: (TreeNode) -> Unit,
    private val onAuthStateChanged: (TreeNode, Boolean) -> Unit
) : RecyclerView.Adapter<TreeAdapter.TreeViewHolder>() {

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
        return nodes.size
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
        private val checkBox: CheckBox = itemView.findViewById(R.id.cb_select)

        fun bind(
            node: TreeNode,
            onItemClicked: (TreeNode) -> Unit,
            onAuthStateChanged: (TreeNode, Boolean) -> Unit
        ) {
            val params = itemView.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = node.level * 40
            itemView.layoutParams = params

            nameTextView.text = node.name

            if (node.type == 0 && node.children.isNotEmpty()) {
                arrowImageView.visibility = View.VISIBLE
                arrowImageView.setImageResource(if (node.isExpanded) R.drawable.ic_expanded else R.drawable.ic_collapsed)
            } else {
                arrowImageView.visibility = View.INVISIBLE
            }

            iconImageView.setImageResource(if (node.type == 0) R.drawable.ic_dept else R.drawable.ic_employee)

            checkBox.setOnCheckedChangeListener(null)
            
            if (node.isIndeterminate) {
                checkBox.isChecked = true
                checkBox.isEnabled = false
            } else if (node.isGrayed && !node.hasAuth) {
                checkBox.isChecked = true
                checkBox.isEnabled = false
                checkBox.alpha = 0.5f
            } else {
                checkBox.isChecked = node.hasAuth
                checkBox.isEnabled = true
                checkBox.alpha = 1.0f
            }
            
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                node.isIndeterminate = false
                node.isGrayed = false
                checkBox.isEnabled = true
                checkBox.alpha = 1.0f
                onAuthStateChanged(node, isChecked)
            }

            itemView.setOnClickListener {
                onItemClicked(node)
            }
        }
    }
}