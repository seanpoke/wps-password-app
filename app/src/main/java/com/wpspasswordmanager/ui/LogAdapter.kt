package com.wpspasswordmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logList = mutableListOf<String>()

    fun updateLogs(newLogs: List<String>) {
        logList.clear()
        logList.addAll(newLogs)
        notifyDataSetChanged()
    }

    fun addLogs(newLogs: List<String>) {
        val startPosition = logList.size
        logList.addAll(newLogs)
        notifyItemRangeInserted(startPosition, newLogs.size)
    }

    fun clearLogs() {
        logList.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logList[position])
    }

    override fun getItemCount(): Int {
        return logList.size
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logTextView: TextView = itemView.findViewById(R.id.log_text)

        fun bind(log: String) {
            logTextView.text = log
            
            // 根据日志级别设置颜色（忽略大小写匹配）
            when {
                log.contains("[ERROR]", ignoreCase = true) -> logTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_light))
                log.contains("[WARN]", ignoreCase = true) -> logTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_orange_light))
                log.contains("[INFO]", ignoreCase = true) -> logTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_light))
                log.contains("[DEBUG]", ignoreCase = true) -> logTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_light))
                else -> logTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
            }
        }
    }
}
