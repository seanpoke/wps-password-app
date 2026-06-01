package com.wpspasswordmanager.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wpspasswordmanager.R
import com.wpspasswordmanager.utils.LogManager

class LogActivity : AppCompatActivity() {

    private lateinit var logRecyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var levelSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var clearButton: Button
    private lateinit var logAdapter: LogAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var isPaused = false
    private var lastLogVersion = -1 // 记住上次的日志版本号
    private var lastLogListSize = 0 // 记住上次的列表大小

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // 初始化 UI 元素
        logRecyclerView = findViewById(R.id.log_recycler_view)
        searchEditText = findViewById(R.id.search_edit_text)
        levelSpinner = findViewById(R.id.level_spinner)
        refreshButton = findViewById(R.id.refresh_button)
        pauseButton = findViewById(R.id.pause_button)
        resumeButton = findViewById(R.id.resume_button)
        clearButton = findViewById(R.id.clear_button)

        // 初始化日志适配器和 RecyclerView
        logAdapter = LogAdapter()
        logRecyclerView.adapter = logAdapter
        logRecyclerView.layoutManager = LinearLayoutManager(this)

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
        val logs = LogManager.getLogs()
        logAdapter.updateLogs(logs)
        lastLogVersion = LogManager.getLogVersion()
        lastLogListSize = logs.size
        // 滚动到底部，确保最新的日志在最下面
        if (logs.isNotEmpty()) {
            logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
        }
    }
    
    /**
     * 增量刷新：只加载新日志
     */
    private fun refreshLogsIncremental() {
        val currentVersion = LogManager.getLogVersion()
        if (currentVersion <= lastLogVersion) {
            return // 版本号没变，没有新日志，不刷新
        }
        
        // 获取新日志
        val newLogs = LogManager.getNewLogs(lastLogListSize)
        val allLogs = LogManager.getLogs()
        
        // 检查队列是否被替换（数量变少了）
        val hasQueueReplaced = allLogs.size < lastLogListSize
        if (hasQueueReplaced) {
            // 队列被替换了，需要全量刷新
            loadLogs()
        } else if (newLogs.isNotEmpty()) {
            // 正常追加新日志
            lastLogVersion = currentVersion
            lastLogListSize = allLogs.size
            
            // 如果有筛选条件，需要重新筛选
            val searchText = searchEditText.text.toString()
            val selectedLevel = levelSpinner.selectedItem.toString()
            if (searchText.isNotEmpty() || selectedLevel != "所有") {
                filterLogs() // 有筛选条件时还是需要重新筛选
            } else {
                // 没有筛选条件，使用增量更新
                logAdapter.addLogs(newLogs)
                logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
            }
        } else {
            // 没有新日志但版本号变了，说明队列内容替换了，全量刷新
            loadLogs()
        }
    }

    private fun filterLogs() {
        val searchText = searchEditText.text.toString()
        val selectedLevel = levelSpinner.selectedItem.toString()
        
        // 使用 LogManager 提供的筛选方法
        val filteredLogs = LogManager.getFilteredLogs(selectedLevel, searchText)

        logAdapter.updateLogs(filteredLogs)
    }

    private fun startRealTimeRefresh() {
        // 先停止之前的刷新，避免重复
        stopRealTimeRefresh()
        
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshLogsIncremental() // 改用增量刷新
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