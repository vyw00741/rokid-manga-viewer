package com.example.rokidmangaviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class MangaStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "MangaStorageManager"
        private const val DIRECTORY_NAME = "MangaViewer"
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }

    /**
     * プライマリ（推奨）の本の格納先ディレクトリを取得する (アプリ専用外部ストレージ)
     * Android 11以上の制限を受けず、パーミッション不要で確実にアクセス可能
     */
    fun getPrimaryMangaDirectory(): File {
        val mangaDir = File(context.getExternalFilesDir(null), DIRECTORY_NAME)
        if (!mangaDir.exists()) {
            val created = mangaDir.mkdirs()
            Log.d(TAG, "Primary Manga directory created: $created, Path: ${mangaDir.absolutePath}")
        }
        return mangaDir
    }

    /**
     * セカンダリ（従来の共有Documents）の格納先ディレクトリを取得する
     */
    fun getSecondaryMangaDirectory(): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documentsDir, DIRECTORY_NAME)
    }

    /**
     * 両方のディレクトリからZIPファイル一覧を取得し、マージする
     */
    fun getBookList(): List<File> {
        val books = mutableListOf<File>()
        
        // 1. アプリ専用外部ストレージから取得（確実に読める）
        val primaryDir = getPrimaryMangaDirectory()
        Log.d(TAG, "Scanning primary dir: ${primaryDir.absolutePath} (exists: ${primaryDir.exists()}, isDir: ${primaryDir.isDirectory})")
        val primaryFiles = primaryDir.listFiles()
        Log.d(TAG, "Primary dir contains ${primaryFiles?.size ?: "null"} files/folders")
        
        primaryFiles?.forEach { file ->
            if (file.isFile && file.extension.lowercase() == "zip") {
                books.add(file)
                Log.d(TAG, "Found ZIP in primary: ${file.name}")
            }
        }

        // 2. 共有Documentsから取得（パーミッションがあり、OSが許可していれば読める）
        try {
            val secondaryDir = getSecondaryMangaDirectory()
            Log.d(TAG, "Scanning secondary dir: ${secondaryDir.absolutePath} (exists: ${secondaryDir.exists()}, isDir: ${secondaryDir.isDirectory})")
            if (secondaryDir.exists()) {
                val secondaryFiles = secondaryDir.listFiles()
                Log.d(TAG, "Secondary dir contains ${secondaryFiles?.size ?: "null"} files/folders")
                
                secondaryFiles?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() == "zip") {
                        if (books.none { it.name == file.name }) {
                            books.add(file)
                            Log.d(TAG, "Found ZIP in secondary: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access secondary documents directory due to security settings", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files from secondary directory", e)
        }

        Log.d(TAG, "Total ZIP books loaded: ${books.size}")
        return books.sortedBy { it.name.lowercase() }
    }

    /**
     * ZIPファイル内の画像エントリー名（ページ）のリストを取得する
     * Mac特有の __MACOSX などの一時ファイルや隠しファイルは除外する
     */
    fun getPageEntries(zipFile: File): List<String> {
        val pages = mutableListOf<String>()
        var zip: ZipFile? = null
        try {
            zip = ZipFile(zipFile)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                // 隠しファイルやシステムフォルダ、非画像ファイルを除外
                if (!entry.isDirectory && 
                    !name.contains("__MACOSX") && 
                    !name.substringAfterLast('/').startsWith(".") &&
                    SUPPORTED_EXTENSIONS.contains(File(name).extension.lowercase())) {
                    pages.add(name)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading zip entries", e)
        } finally {
            zip?.close()
        }

        // 自然順（Natural Order）でソート
        return pages.sortedWith { o1, o2 -> naturalCompare(o1, o2) }
    }

    /**
     * ZIPファイルから特定のページの画像をBitmapとして読み込む
     * メモリ節約（OOM対策）のため、HUD解像度に合わせてリサイズしてロードする
     */
    fun loadPageBitmap(zipFile: File, entryName: String, maxWidth: Int = 1024, maxHeight: Int = 1024): Bitmap? {
        var zip: ZipFile? = null
        var inputStream: InputStream? = null
        try {
            zip = ZipFile(zipFile)
            val entry = zip.getEntry(entryName) ?: return null
            
            // 1. 画像のサイズ情報のみを取得
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = zip.getInputStream(entry)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // 2. inSampleSize を計算
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            // 3. 実際にデコードしてロード
            inputStream = zip.getInputStream(entry)
            return BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from zip: $entryName", e)
            return null
        } finally {
            inputStream?.close()
            zip?.close()
        }
    }

    /**
     * 画像の縮小比率を計算する
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 自然順ソートのための比較関数
     * "1.jpg", "2.jpg", "10.jpg" を正しく順序付ける
     */
    private fun naturalCompare(s1: String, s2: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val chunks1 = regex.findAll(s1).map { it.value }.toList()
        val chunks2 = regex.findAll(s2).map { it.value }.toList()

        val size = minOf(chunks1.size, chunks2.size)
        for (i in 0 until size) {
            val c1 = chunks1[i]
            val c2 = chunks2[i]

            val isDigit1 = c1[0].isDigit()
            val isDigit2 = c2[0].isDigit()

            if (isDigit1 && isDigit2) {
                val n1 = c1.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                val n2 = c2.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                val cmp = n1.compareTo(n2)
                if (cmp != 0) return cmp
            } else {
                val cmp = c1.compareTo(c2, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return chunks1.size.compareTo(chunks2.size)
    }
}
