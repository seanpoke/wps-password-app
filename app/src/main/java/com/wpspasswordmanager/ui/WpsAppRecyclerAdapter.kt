package com.wpspasswordmanager.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.WpsAppInfo

class WpsAppRecyclerAdapter(
    private val context: Context,
    private val selectedPackage: String?,
    private val onItemSelected: (WpsAppInfo) -> Unit
) : ListAdapter<WpsAppInfo, WpsAppRecyclerAdapter.ViewHolder>(WpsAppDiffCallback()) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.wps_app_icon)
        val nameView: TextView = itemView.findViewById(R.id.wps_app_name)
        val packageView: TextView = itemView.findViewById(R.id.wps_app_package)
        val versionView: TextView = itemView.findViewById(R.id.wps_app_version)
        val radioButton: RadioButton = itemView.findViewById(R.id.wps_select_radio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.wps_app_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wpsApp = getItem(position)
        
        holder.iconView.setImageDrawable(wpsApp.icon)
        holder.nameView.text = wpsApp.label
        holder.packageView.text = "包名: ${wpsApp.packageName}"
        holder.versionView.text = "版本: ${wpsApp.versionName} (${wpsApp.versionCode})"
        holder.radioButton.isChecked = wpsApp.packageName == selectedPackage

        holder.itemView.setOnClickListener {
            onItemSelected(wpsApp)
        }
    }

    class WpsAppDiffCallback : DiffUtil.ItemCallback<WpsAppInfo>() {
        override fun areItemsTheSame(oldItem: WpsAppInfo, newItem: WpsAppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: WpsAppInfo, newItem: WpsAppInfo): Boolean {
            return oldItem == newItem
        }
    }
}