package com.example.rokidmangaviewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.rokidmangaviewer.databinding.ActivityMainBinding
import com.example.rokidmangaviewer.databinding.ItemBookBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
        // HUD表示用の最大デコードサイズ
        private const val MAX_IMAGE_DIMENSION = 1280
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var storageManager: MangaStorageManager
    private lateinit var pageTracker: PageTracker
    
    // 現在開いている本とその状態
    private var currentBookFile: File? = null
    private var pageEntries: List<String> = emptyList()
    private var currentPageIndex: Int = 0
    private var isSpreadMode: Boolean = false
    
    // 非同期ロード用ジョブ
    private var imageLoadJob: Job? = null

    // 全体的なキー入力デバウンス（チャタリング、離す瞬間の誤検知防止）用
    private var lastInputTime = 0L
    private val GLOBAL_DEBOUNCE_MS = 400L

    // ダブルタップとタッチスワイプ検知用のデテクター
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storageManager = MangaStorageManager(this)
        pageTracker = PageTracker(this)

        setupGestures()
        
        // 明るさ反転チェックボックスのセットアップ
        binding.cbInvertColors.isChecked = pageTracker.isColorInverted()
        binding.cbInvertColors.setOnCheckedChangeListener { _, isChecked ->
            pageTracker.setColorInverted(isChecked)
            applyColorTheme()
        }

        // 左から右へ読むモードチェックボックスのセットアップ
        binding.cbReadLeftToRight.isChecked = pageTracker.isLeftToRight()
        binding.cbReadLeftToRight.setOnCheckedChangeListener { _, isChecked ->
            pageTracker.setLeftToRight(isChecked)
            val msg = if (isChecked) "Read Left to Right: ON" else "Read Left to Right: OFF"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        applyColorTheme() // 初回テーマ適用
        
        checkPermissionsAndLoad()
    }

    /**
     * ジェスチャー（ダブルタップ、タッチパネルでのスワイプ）のセットアップ
     */
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            // タッチジェスチャーの開始をシステムに通知し、イベントを追跡可能にする
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            // ダブルタップで本を閉じる
            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.d(TAG, "Double tap detected. Returning to list.")
                closeBook()
                return true
            }

            // シングルタップで見開き（2ページ）と1ページ表示をトグルする
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleSpreadMode()
                return true
            }
        })

        // 各ビューに対してタッチイベントをバインド
        val touchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        
        binding.ivMangaPage.setOnTouchListener(touchListener)
        binding.ivMangaPageLeft.setOnTouchListener(touchListener)
        binding.ivMangaPageRight.setOnTouchListener(touchListener)
        binding.layoutSpread.setOnTouchListener(touchListener)
    }

    /**
     * 明るさ反転（ネガポジ反転）設定に基づいてアプリ全体の色テーマを切り替える
     */
    private fun applyColorTheme() {
        val isInverted = pageTracker.isColorInverted()
        
        // 背景色と主要色の決定
        val bgColor = if (isInverted) Color.WHITE else Color.BLACK
        val primaryColor = if (isInverted) Color.BLACK else Color.parseColor("#00FF66")
        
        binding.layoutBookSelect.setBackgroundColor(bgColor)
        binding.layoutViewer.setBackgroundColor(bgColor)
        
        binding.tvListTitle.setTextColor(primaryColor)
        
        binding.cbInvertColors.setTextColor(primaryColor)
        binding.cbInvertColors.buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        
        binding.cbReadLeftToRight.setTextColor(primaryColor)
        binding.cbReadLeftToRight.buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        
        if (isInverted) {
            binding.cbInvertColors.setBackgroundResource(R.drawable.item_background_selector_inverted)
            binding.cbReadLeftToRight.setBackgroundResource(R.drawable.item_background_selector_inverted)
        } else {
            binding.cbInvertColors.setBackgroundResource(R.drawable.item_background_selector)
            binding.cbReadLeftToRight.setBackgroundResource(R.drawable.item_background_selector)
        }
        
        binding.tvEmptyMessage.setTextColor(if (isInverted) Color.GRAY else Color.WHITE)
        binding.tvPageIndicator.setTextColor(primaryColor)
        
        // 漫画表示用のネガ反転フィルタの適用/解除
        if (isInverted) {
            val negativeMatrix = ColorMatrix(floatArrayOf(
                -1.0f,  0.0f,  0.0f, 0.0f, 255f, // R
                 0.0f, -1.0f,  0.0f, 0.0f, 255f, // G
                 0.0f,  0.0f, -1.0f, 0.0f, 255f, // B
                 0.0f,  0.0f,  0.0f, 1.0f,   0f  // A
            ))
            val filter = ColorMatrixColorFilter(negativeMatrix)
            binding.ivMangaPage.colorFilter = filter
            binding.ivMangaPageLeft.colorFilter = filter
            binding.ivMangaPageRight.colorFilter = filter
        } else {
            binding.ivMangaPage.colorFilter = null
            binding.ivMangaPageLeft.colorFilter = null
            binding.ivMangaPageRight.colorFilter = null
        }
        
        // リストデータを再描画してリスト項目の色を反映
        (binding.lvBooks.adapter as? BookAdapter)?.notifyDataSetChanged()
    }

    /**
     * 表示モードに応じてレイアウトの可視性をアップデートする
     */
    private fun updateViewerModeLayout() {
        if (isSpreadMode) {
            binding.ivMangaPage.visibility = View.GONE
            binding.layoutSpread.visibility = View.VISIBLE
        } else {
            binding.layoutSpread.visibility = View.GONE
            binding.ivMangaPage.visibility = View.VISIBLE
        }
    }

    /**
     * 見開きモードと1ページモードをトグル切り替えする
     */
    private fun toggleSpreadMode() {
        isSpreadMode = !isSpreadMode
        Log.d(TAG, "Toggle spread mode: $isSpreadMode")

        updateViewerModeLayout()
        loadPage(currentPageIndex, 0)

        val modeText = if (isSpreadMode) "Spread View Mode (2 Pages)" else "Single Page Mode"
        Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
    }

    /**
     * 権限を確認し、漫画リストを読み込む
     */
    private fun checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        } else {
            loadBooks()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadBooks()
            } else {
                binding.tvEmptyMessage.text = getString(R.string.permission_required)
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.lvBooks.visibility = View.GONE
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 指定フォルダからZIPファイルリストを読み込んでListViewにバインド
     */
    private fun loadBooks() {
        val books = storageManager.getBookList()
        if (books.isEmpty()) {
            // 格納先フォルダパスを提示してユーザーに知らせる
            val primaryDir = storageManager.getPrimaryMangaDirectory()
            val secondaryDir = storageManager.getSecondaryMangaDirectory()
            binding.tvEmptyMessage.text = "${getString(R.string.no_books_found)}\n\n" +
                    "Option 1 (Recommended - No Permission Required):\n${primaryDir.absolutePath}\n\n" +
                    "Option 2 (Legacy Documents - Permission Required):\n${secondaryDir.absolutePath}"
            binding.tvEmptyMessage.visibility = View.VISIBLE
            binding.lvBooks.visibility = View.GONE
        } else {
            binding.tvEmptyMessage.visibility = View.GONE
            binding.lvBooks.visibility = View.VISIBLE
            
            val adapter = BookAdapter(this, books)
            binding.lvBooks.adapter = adapter
            
            binding.lvBooks.setOnItemClickListener { _, _, position, _ ->
                openBook(books[position])
            }
            
            // 初回フォーカスをリストの先頭に設定
            binding.lvBooks.requestFocus()
        }
    }

    /**
     * 選択した本（ZIP）を開き、読書画面へ遷移する
     */
    private fun openBook(file: File) {
        currentBookFile = file
        
        // ZIP内の全画像エントリーを取得
        pageEntries = storageManager.getPageEntries(file)
        if (pageEntries.isEmpty()) {
            Toast.makeText(this, "No support images found in this ZIP.", Toast.LENGTH_SHORT).show()
            return
        }

        // レジューム位置（最後に読んだページ）を取得
        val savedPage = pageTracker.getLastReadPage(file.name)
        currentPageIndex = if (savedPage in pageEntries.indices) savedPage else 0

        // UIの切り替え
        binding.layoutBookSelect.visibility = View.GONE
        binding.layoutViewer.visibility = View.VISIBLE
        updateViewerModeLayout()

        // ページの表示 (初回はアニメーションなし: 0)
        loadPage(currentPageIndex, 0)
    }

    /**
     * 漫画の閲覧画面を閉じ、本選択リストに戻る
     */
    private fun closeBook() {
        // 現在の読み込みジョブをキャンセル
        imageLoadJob?.cancel()
        
        // 進捗を保存
        currentBookFile?.let { file ->
            pageTracker.saveLastReadPage(file.name, currentPageIndex, pageEntries.size)
        }

        // UI切り替え
        binding.layoutViewer.visibility = View.GONE
        binding.layoutBookSelect.visibility = View.VISIBLE
        
        // リストデータを再ロードして進捗表示を更新
        loadBooks()
    }

    /**
     * 指定されたインデックスの画像を非同期でデコードし、表示する
     * @param animateDirection 1: 次へ（左から右にスライドイン）, -1: 前へ（右から左にスライドイン）, 0: アニメーションなし
     */
    private fun loadPage(index: Int, animateDirection: Int = 0) {
        val bookFile = currentBookFile ?: return
        if (index !in pageEntries.indices) return

        currentPageIndex = index

        // 前回の読み込みが動いていればキャンセル
        imageLoadJob?.cancel()

        // プレースホルダー（黒画面）を一旦見せる
        binding.ivMangaPage.setImageBitmap(null)
        binding.ivMangaPageLeft.setImageBitmap(null)
        binding.ivMangaPageRight.setImageBitmap(null)

        // コルーチンによる非同期デコード
        imageLoadJob = lifecycleScope.launch {
            if (isSpreadMode) {
                // 見開きモード
                val entry1 = pageEntries[index] // 現在のページ
                val entry2 = if (index + 1 in pageEntries.indices) pageEntries[index + 1] else null // 次のページ

                val bitmap1 = withContext(Dispatchers.IO) {
                    storageManager.loadPageBitmap(bookFile, entry1, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                }

                val bitmap2 = if (entry2 != null) {
                    withContext(Dispatchers.IO) {
                        storageManager.loadPageBitmap(bookFile, entry2, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                    }
                } else null

                // 読み進める方向に応じて、左右のImageViewへ割り当てる
                if (pageTracker.isLeftToRight()) {
                    // 洋書・アメコミ（左開き）: 左に現在のページ、右に次のページ
                    binding.ivMangaPageLeft.setImageBitmap(bitmap1)
                    binding.ivMangaPageRight.setImageBitmap(bitmap2)
                } else {
                    // 日本の漫画（右開き）: 右に現在のページ、左に次のページ
                    binding.ivMangaPageRight.setImageBitmap(bitmap1)
                    binding.ivMangaPageLeft.setImageBitmap(bitmap2)
                }

                // インジケーター表示の更新
                if (entry2 != null) {
                    binding.tvPageIndicator.text = "${index + 1} - ${index + 2} / ${pageEntries.size}"
                } else {
                    binding.tvPageIndicator.text = "${index + 1} / ${pageEntries.size}"
                }
            } else {
                // シングルページモード
                val entryName = pageEntries[index]
                val bitmap = withContext(Dispatchers.IO) {
                    storageManager.loadPageBitmap(bookFile, entryName, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                }
                binding.ivMangaPage.setImageBitmap(bitmap)
                binding.tvPageIndicator.text = "${index + 1} / ${pageEntries.size}"
            }

            // 進捗を随時保存
            pageTracker.saveLastReadPage(bookFile.name, currentPageIndex, pageEntries.size)

            // アニメーションの適用
            val animRes = if (pageTracker.isLeftToRight()) {
                when (animateDirection) {
                    1 -> R.anim.slide_in_right_to_left // L2Rで進む場合：右から新しいページが来る
                    -1 -> R.anim.slide_in_left_to_right // L2Rで戻る場合：左から古いページが来る
                    else -> 0
                }
            } else {
                when (animateDirection) {
                    1 -> R.anim.slide_in_left_to_right  // R2Lで進む場合：左から新しいページが来る
                    -1 -> R.anim.slide_in_right_to_left // R2Lで戻る場合：右から古いページが来る
                    else -> 0
                }
            }
            if (animRes != 0) {
                val animation = android.view.animation.AnimationUtils.loadAnimation(this@MainActivity, animRes)
                val targetView = if (isSpreadMode) binding.layoutSpread else binding.ivMangaPage
                targetView.startAnimation(animation)
            }
        }
    }

    /**
     * 次のページを表示
     */
    private fun showNextPage() {
        // 画像ロード中のチャタリング（連打）による二重遷移を防止
        if (imageLoadJob?.isActive == true) return
        
        val step = if (isSpreadMode) 2 else 1
        if (currentPageIndex + step < pageEntries.size) {
            loadPage(currentPageIndex + step, 1)
        } else {
            Toast.makeText(this, "End of Book", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 前のページを表示
     */
    private fun showPreviousPage() {
        // 画像ロード中のチャタリング（連打）による二重遷移を防止
        if (imageLoadJob?.isActive == true) return
        
        val step = if (isSpreadMode) 2 else 1
        if (currentPageIndex - step >= 0) {
            loadPage(currentPageIndex - step, -1)
        } else {
            Toast.makeText(this, "First Page", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * リストスクロールのキーイベントをデバウンスとフォーカスルーティング付きでディスパッチする
     */
    private fun dispatchListScroll(event: KeyEvent, targetKeyCode: Int): Boolean {
        // cbInvertColors（明るさ反転チェックボックス）にフォーカスがある場合
        if (binding.cbInvertColors.hasFocus()) {
            if (targetKeyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 下キーの場合はcbReadLeftToRightへ移動
                binding.cbReadLeftToRight.requestFocus()
                return true
            } else if (targetKeyCode == KeyEvent.KEYCODE_DPAD_CENTER || targetKeyCode == KeyEvent.KEYCODE_ENTER) {
                // チェックボックスをトグル
                binding.cbInvertColors.toggle()
                return true
            } else {
                return true // 上キーなどはこれ以上上はないので無視して消費
            }
        }
        
        // cbReadLeftToRight にフォーカスがある場合
        if (binding.cbReadLeftToRight.hasFocus()) {
            if (targetKeyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 下キーの場合はリストへ移動
                binding.lvBooks.requestFocus()
                return true
            } else if (targetKeyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // 上キーの場合はcbInvertColorsへ移動
                binding.cbInvertColors.requestFocus()
                return true
            } else if (targetKeyCode == KeyEvent.KEYCODE_DPAD_CENTER || targetKeyCode == KeyEvent.KEYCODE_ENTER) {
                // チェックボックスをトグル
                binding.cbReadLeftToRight.toggle()
                return true
            } else {
                return true
            }
        }

        // ListView 内にフォーカスがある場合
        if (targetKeyCode == KeyEvent.KEYCODE_DPAD_UP) {
            // リストの先頭アイテムが選択されている、もしくは一番上までスクロールされている状態で上（左）を押した場合
            val selectedItemPosition = binding.lvBooks.selectedItemPosition
            if (selectedItemPosition == 0 || (binding.lvBooks.firstVisiblePosition == 0 && binding.lvBooks.getChildAt(0)?.hasFocus() == true)) {
                binding.cbReadLeftToRight.requestFocus()
                return true
            }
        }

        // リスト内部のスクロールディスパッチ
        val newEvent = KeyEvent(event.action, targetKeyCode)
        return binding.lvBooks.dispatchKeyEvent(newEvent)
    }

    /**
     * キーイベント（Rokid Glassesのサイドタッチセンサー、R08リング、キーボード等）の制御
     * 子ビューにフォーカスが奪われても確実にイベントをキャッチするため、dispatchKeyEventをオーバーライドします。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        
        // --- ゴースト入力・ノイズ対策の要 ---
        // 1. 方向キーや決定キーの ACTION_UP イベントは、システムへ流さずにここで消費（握り潰す）。
        // これにより、パッドから指を離した瞬間にシステム側で意図しないUIフォーカス移動が走るのを防ぐ。
        val isNavKey = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                       keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                       keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                       keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
                       keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                       keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                       
        if (event.action == KeyEvent.ACTION_UP && isNavKey) {
            return true
        }

        // 2. キーが押されたタイミング (ACTION_DOWN かつ 初回検知) のみ処理する
        // repeatCount > 0 の自動連打（長押し）イベントを弾くことで、過敏なスクロールや連続めくりを防止
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            
            // 全体的な入力デバウンス（クールタイム400ms）
            // これにより、指を離す瞬間の遅延ノイズや逆方向へのブレ入力を完全にカットする。
            if (isNavKey) {
                val now = System.currentTimeMillis()
                if (now - lastInputTime < GLOBAL_DEBOUNCE_MS) {
                    Log.d(TAG, "Key event ignored due to global debounce")
                    return true
                }
                lastInputTime = now
            }
            
            // ビューアー（読書画面）がアクティブな場合
            if (binding.layoutViewer.visibility == View.VISIBLE) {
                when (keyCode) {
                    // 次のページへ (Left-to-Rightモードなら前へ)
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, 
                    KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (pageTracker.isLeftToRight()) showPreviousPage() else showNextPage()
                        return true
                    }
                    // 前のページへ (Left-to-Rightモードなら次へ)
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, 
                    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (pageTracker.isLeftToRight()) showNextPage() else showPreviousPage()
                        return true
                    }
                    // 見開きトグル
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        toggleSpreadMode()
                        return true
                    }
                    // 閉じる
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        closeBook()
                        return true
                    }
                }
            } else {
                // 本選択画面（リスト表示）の場合
                when (keyCode) {
                    // 右矢印キー または MEDIA_NEXT -> 下矢印キー（次の本）に変換してディスパッチ
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        return dispatchListScroll(event, KeyEvent.KEYCODE_DPAD_DOWN)
                    }
                    // 左矢印キー または MEDIA_PREVIOUS -> 上矢印キー（前の本）に変換してディスパッチ
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        return dispatchListScroll(event, KeyEvent.KEYCODE_DPAD_UP)
                    }
                    // リスト画面でのタップ -> チェックボックスフォーカス時はトグル、それ以外は手動で本を開く
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (binding.cbInvertColors.hasFocus()) {
                            binding.cbInvertColors.toggle()
                            return true
                        } else if (binding.cbReadLeftToRight.hasFocus()) {
                            binding.cbReadLeftToRight.toggle()
                            return true
                        } else {
                            val pos = binding.lvBooks.selectedItemPosition
                            if (pos != android.widget.AdapterView.INVALID_POSITION) {
                                val adapter = binding.lvBooks.adapter as BookAdapter
                                val book = adapter.getItem(pos)
                                if (book != null) {
                                    openBook(book)
                                }
                            }
                            return true
                        }
                    }
                    // 戻るキー -> アプリ終了
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        finish()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        imageLoadJob?.cancel()
    }

    /**
     * 本リストのカスタムアダプター
     */
    private inner class BookAdapter(
        context: Context,
        private val books: List<File>
    ) : ArrayAdapter<File>(context, 0, books) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val bindingItem: ItemBookBinding
            val view: View

            if (convertView == null) {
                bindingItem = ItemBookBinding.inflate(LayoutInflater.from(context), parent, false)
                view = bindingItem.root
                view.tag = bindingItem
            } else {
                view = convertView
                bindingItem = view.tag as ItemBookBinding
            }

            val bookFile = getItem(position)
            if (bookFile != null) {
                // 拡張子を除去して表示
                bindingItem.tvBookName.text = bookFile.nameWithoutExtension
                
                // 読書進捗を表示
                val total = pageTracker.getTotalPages(bookFile.name)
                bindingItem.tvBookProgress.text = pageTracker.getProgressText(bookFile.name, total)
            }

            // 明るさ反転テーマに応じた色の動的適用
            val isInverted = pageTracker.isColorInverted()
            if (isInverted) {
                view.setBackgroundResource(R.drawable.item_background_selector_inverted) // 反転用セレクター
                bindingItem.tvBookName.setTextColor(Color.BLACK)
                bindingItem.tvBookProgress.setTextColor(Color.parseColor("#444444")) // 濃い灰色
            } else {
                view.setBackgroundResource(R.drawable.item_background_selector) // 標準のセレクター
                bindingItem.tvBookName.setTextColor(Color.WHITE)
                bindingItem.tvBookProgress.setTextColor(Color.parseColor("#00FF66")) // サイバーグリーン
            }

            return view
        }
    }
}
