package com.wpspasswordmanager.business

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        private const val DATABASE_NAME = "wps_passwords.db"
        private const val DATABASE_VERSION = 1
        
        // 表名
        const val TABLE_PASSWORD = "passwords"
        
        // 列名
        const val COLUMN_ID = "id"
        const val COLUMN_FILE_NAME = "file_name"
        const val COLUMN_FILE_URI = "file_uri"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_SYNC_STATUS = "sync_status"
        
        // 同步状态
        const val SYNC_STATUS_PENDING = 0
        const val SYNC_STATUS_SYNCED = 1
        
        // 创建表的 SQL 语句
        private const val CREATE_TABLE_PASSWORD = """
            CREATE TABLE $TABLE_PASSWORD (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FILE_NAME TEXT NOT NULL,
                $COLUMN_FILE_URI TEXT,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_SYNC_STATUS INTEGER DEFAULT $SYNC_STATUS_PENDING
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "创建数据库表")
        db.execSQL(CREATE_TABLE_PASSWORD)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "升级数据库，从版本 $oldVersion 到 $newVersion")
        // 这里可以添加数据库升级逻辑
    }
}