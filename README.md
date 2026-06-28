# Rokid Manga Viewer

An Android comic/manga viewer application optimized for Rokid AR Glasses (such as Rokid Max, Rokid Joy, etc.). It allows you to read manga directly from ZIP files stored on your device, control the viewer using touchpad gestures or Bluetooth controllers (like the Rokid R08 Ring), and features custom display adjustments suitable for AR reading environments.

[日本語の案内は以下にあります](#日本語説明)

---

## Features

- **Direct ZIP Loading**: Instantly read `.zip` format books from your device without unpacking.
- **Smart Glasses Navigation**: Fully controlled via touchpad swipes, side taps, and DPAD remote inputs.
  - *Debounced Controls*: Custom debouncing prevents double-triggering or ghost scrolling when you lift your finger.
- **Invert Colors Mode**: High-contrast mode to invert page colors (white-on-black). Perfect for reading at night or in bright AR environments.
- **Read Left-to-Right Mode**: Supports Western comics and artbooks. Reverses swipe directions and swaps left/right page allocations dynamically.
- **Spread View (見開き表示)**: Automatically splits landscape screens to show two pages side-by-side.
- **Auto Save/Restore Progress**: Remembers your last read page and progress percentage for every book.

---

## Installation & Setup

1. **Prerequisite**: Ensure your device allows installation from unknown sources.
2. **Download APK**: Download the pre-built `MangaViewer_debug_v1.0.3.apk` from the Releases page.
3. **Install via ADB**:
   ```bash
   adb install -r MangaViewer_debug_v1.0.3.apk
   ```
4. **Permissions**: The app requires "Files and media" (Storage read) permission to locate your manga.
5. **Manga Folder**: Place your ZIP comic books inside the `Documents/MangaViewer` folder on your device's internal storage. (The folder is automatically created on first launch).

---

## Controls

### List Screen (Book Selector)
- **Swipe Left / Right (or DPAD Left / Right)**: Selects "Invert Colors", "Read Left to Right" checkbox, or navigates to the list.
- **Swipe Up / Down (or DPAD Up / Down)**: Scroll through your books.
- **Tap (DPAD Center / Enter)**: Toggle options, or open the highlighted book.

### Reader Screen
- **Swipe Left / Right (or DPAD Left / Right)**: Turn pages. (Behavior reverses depending on the "Read Left to Right" setting).
- **Single Tap (DPAD Center / Enter)**: Toggle between **Spread (2-page) View** and **Single-page View**.
- **Double Tap (or Back key)**: Exit the reader and return to the selector.

---

<a id="日本語説明"></a>
## 日本語説明

Rokidスマートグラス（Rokid Max、Rokid Joyなど）向けに最適化されたコミック・マンガビューアアプリです。デバイス内のZIPファイルを直接読み込み、グラス側面のスワイプジェスチャーやBluetoothリモコン（Rokid R08 Ring等）でスムーズに操作できます。

### 主な機能
- **ZIP直接読み込み**: `.zip` 形式のファイルを解凍することなく直接読書可能。
- **スマートグラス最適化**: デバウンス処理により、パッドから手を離す瞬間の誤操作や連続めくりを防止。
- **明るさ反転（Invert Colors）**: 夜間やARグラスでの視認性向上のため、白黒反転表示が可能。
- **左開き/右読み切り替え**: 洋書やアメコミに適した左開きモードを搭載。スワイプ方向と見開き位置を動的に反転します。
- **見開き（2ページ）表示**: 横向き画面で自動的に2ページ並べて表示。
- **しおり（進捗保存）機能**: 本ごとの最終閲覧ページと進捗率を自動で記録します。

### 導入手順
1. 本リポジトリの Releases ページ等から APK ファイルをダウンロードします。
2. ADB 等を用いてデバイス（Rokidホストデバイス）にインストールします。
   ```bash
   adb install -r MangaViewer_debug_v1.0.apk
   ```
3. デバイスの内部ストレージ内の `Documents/MangaViewer` フォルダ（初回起動時に自動生成されます）に、読みたいZIPファイルを配置します。

---

## License

This project is open-source. Feel free to modify and adapt it for your own AR glasses setup!
