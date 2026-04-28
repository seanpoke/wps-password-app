package com.wpspasswordmanager.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.WpsAppInfo

class WpsAppAdapter(
    context: Context,
    private val wpsApps: List<WpsAppInfo>,
    private val selectedPackage: String?,
    private val onItemSelected: (WpsAppInfo) -> Unit
) : ArrayAdapter<WpsAppInfo>(context, 0, wpsApps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.wps_app_item, parent, false)

        val wpsApp = wpsApps[position]

        val iconView: ImageView = view.findViewById(R.id.wps_app_icon)
        val nameView: TextView = view.findViewById(R.id.wps_app_name)
        val packageView: TextView = view.findViewById(R.id.wps_app_package)
        val versionView: TextView = view.findViewById(R.id.wps_app_version)
        val radioButton: RadioButton = view.findViewById(R.id.wps_select_radio)

        iconView.setImageDrawable(wpsApp.icon)
        nameView.text = wpsApp.label
        packageView.text = "包名: ${wpsApp.packageName}"
        versionView.text = "版本: ${wpsApp.versionName} (${wpsApp.versionCode})"
        
        radioButton.isChecked = wpsApp.packageName == selectedPackage

        view.setOnClickListener {
            onItemSelected(wpsApp)
        }

        return view
    }
}