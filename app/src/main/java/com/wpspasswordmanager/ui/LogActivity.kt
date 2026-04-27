package com.wpspasswordmanager.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.utils.LogManager

class LogActivity : AppCompatActivity() {

    private lateinit var logListView: ListView
    private lateinit var searchEditText: EditText
    private lateinit var levelSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var clearButton: Button
    private lateinit var logAdapter: ArrayAdapter<String>
    private var logList: MutableList<String> = mutableListOf()
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // 初始化 UI 元素
        logListView = findViewById(R.id.log_list_view)
        searchEditText = findViewById(R.id.search_edit_text)
        levelSpinner = findViewById(R.id.level_spinner)
        refreshButton = findViewById(R.id.refresh_button)
        pauseButton = findViewById(R.id.pause_button)
        resumeButton = findViewById(R.id.resume_button)
        clearButton = findViewById(R.id.clear_button)

        // 初始化日志适配器
        logAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logList)
        logListView.adapter = logAdapter

        // 初始化日志级别选择器
        val levels = arrayOf("所有", "DEBUG", "INFO", "WARN", "ERROR")
        val levelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, levels)
        levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        levelSpinner.adapter = levelAdapter

        // 加载日志
        loadLogs()

        // 设置搜索功能
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterLogs()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 设置日志级别筛选
        levelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterLogs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 设置刷新按钮
        refreshButton.setOnClickListener {
            loadLogs()
        }

        // 设置清除按钮
        clearButton.setOnClickListener {
            LogManager.clearLogs()
            loadLogs()
        }

        // 设置暂停按钮
        pauseButton.setOnClickListener {
            // 暂停实时刷新
            isPaused = true
            pauseButton.isEnabled = false
            resumeButton.isEnabled = true
            stopRealTimeRefresh()
        }

        // 设置恢复按钮
        resumeButton.setOnClickListener {
            // 恢复实时刷新
            isPaused = false
            pauseButton.isEnabled = true
            resumeButton.isEnabled = false
            startRealTimeRefresh()
        }

        // 设置实时刷新
        startRealTimeRefresh()
    }

    private fun loadLogs() {
        logList.clear()
        val logs = LogManager.getLogs()
        logList.addAll(logs)
        logAdapter.notifyDataSetChanged()
        // 滚动到底部，确保最新的日志在最下面
        if (logList.isNotEmpty()) {
            logListView.setSelection(logList.size - 1)
        }
    }

    private fun filterLogs() {
        val searchText = searchEditText.text.toString().toLowerCase()
        val selectedLevel = levelSpinner.selectedItem.toString()
        
        val filteredLogs = LogManager.getLogs().filter { log ->
            val matchesSearch = log.toLowerCase().contains(searchText)
            val matchesLevel = if (selectedLevel == "所有") {
                true
            } else {
                log.contains(selectedLevel)
            }
            matchesSearch && matchesLevel
        }

        logList.clear()
        logList.addAll(filteredLogs)
        logAdapter.notifyDataSetChanged()
    }

    private fun startRealTimeRefresh() {
        // 先停止之前的刷新，避免重复
        stopRealTimeRefresh()
        
        refreshRunnable = object : Runnable {
            override fun run() {
                loadLogs()
                handler.postDelayed(this, 2000) // 每2秒刷新一次
            }
        }
        handler.postDelayed(refreshRunnable!!, 2000)
    }

    private fun stopRealTimeRefresh() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止实时刷新
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }
}