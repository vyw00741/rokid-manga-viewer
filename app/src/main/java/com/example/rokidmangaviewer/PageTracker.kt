package com.example.rokidmangaviewer

import android.content.Context
import android.content.SharedPreferences

class PageTracker(context: Context) {

    companion object {
        private const val PREFS_NAME = "MangaViewerPrefs"
        private const val KEY_PREFIX_PAGE = "page_"
        private const val KEY_PREFIX_TOTAL = "total_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 最後に読んだページインデックス（0開始）と総ページ数を保存する
     */
    fun saveLastReadPage(bookName: String, pageIndex: Int, totalPages: Int) {
        prefs.edit().apply {
            putInt(KEY_PREFIX_PAGE + bookName, pageIndex)
            putInt(KEY_PREFIX_TOTAL + bookName, totalPages)
            apply()
        }
    }

    /**
     * 最後に読んだページインデックスを取得する（保存されていなければ0）
     */
    fun getLastReadPage(bookName: String): Int {
        return prefs.getInt(KEY_PREFIX_PAGE + bookName, 0)
    }

    /**
     * 保存されている総ページ数を取得する
     */
    fun getTotalPages(bookName: String): Int {
        return prefs.getInt(KEY_PREFIX_TOTAL + bookName, 0)
    }

    /**
     * 本の進捗状況のテキスト表現を取得する
     * 例: "Page 12 / 100 (12%)" または未読なら "Not read yet"
     */
    fun getProgressText(bookName: String, totalPages: Int): String {
        val lastPage = getLastReadPage(bookName)
        if (totalPages <= 0) return "No pages"
        
        // 表示用に1始まりにする
        val displayPage = lastPage + 1
        val percent = (displayPage.toFloat() / totalPages.toFloat() * 100).toInt()
        
        return "Progress: $displayPage / $totalPages ($percent%)"
    }

    /**
     * 特定の本の読書進行情報をクリアする（任意）
     */
    fun clearProgress(bookName: String) {
        prefs.edit().apply {
            remove(KEY_PREFIX_PAGE + bookName)
            remove(KEY_PREFIX_TOTAL + bookName)
            apply()
        }
    }

    /**
     * 明るさ反転（ネガポジ反転）設定が有効か取得する
     */
    fun isColorInverted(): Boolean {
        return prefs.getBoolean("is_color_inverted", false)
    }

    /**
     * 明るさ反転（ネガポジ反転）設定を保存する
     */
    fun setColorInverted(inverted: Boolean) {
        prefs.edit().putBoolean("is_color_inverted", inverted).apply()
    }

    /**
     * 左から右へ読み進めるモード（洋書スタイル等）が有効か取得する
     */
    fun isLeftToRight(): Boolean {
        return prefs.getBoolean("is_left_to_right", false)
    }

    /**
     * 左から右へ読み進めるモードを保存する
     */
    fun setLeftToRight(enabled: Boolean) {
        prefs.edit().putBoolean("is_left_to_right", enabled).apply()
    }
}
