# LauncherApp HANDOFF

## 概要
Androidホームアプリ（ランチャー）。Nova風機能をComposeでゼロから再現する方針。
本ファイルは新チャット開始時の引き継ぎ用。

## 構成
- パッケージ: com.appathy.launcher
- Kotlin 2.0.20 / AGP 8.5.2 / compileSdk 34 / minSdk 26 / Compose (BOM 2024.09.00)
- app/debug.keystore 固定（上書きインストール可）
- ビルド: GitHub Actions (build.yml, gradle 8.7, assembleDebug → artifact "app-debug")
- リポジトリ: Sekiguchi-Takashi/LauncherApp (private)

## ファイル
- AppModel.kt: AppEntry / loadApps()（LAUNCHERインテント照会・自分自身は除外）/ Favorites（SharedPreferences永続化）
- MainActivity.kt: LauncherRoot（ホーム＋ドロワー切替）/ HomeScreen（時計・日付・Dock・上スワイプ）/ AppDrawer（検索＋4列グリッド、長押しでお気に入り切替）/ デフォルトホーム設定（RoleManager、API29未満はHOME設定画面）

## v1.0 実装済み
- HOMEインテント（singleTask / stateNotNeeded / clearTaskOnLaunch / excludeFromRecents）
- 壁紙透過テーマ（windowShowWallpaper）
- アプリ一覧（別スレッド読込、PACKAGE_ADDED/REMOVED/CHANGEDで自動更新）
- ドロワー: 検索フィルタ、タップ起動、長押しでお気に入り（★表示、Dock最大5個）
- 戻るボタンでドロワーを閉じる

## 未実装（Nova風の拡張候補）
- ワークスペースのページング・グリッド配置
- ウィジェット（AppWidgetHost）
- ジェスチャー割当、icon pack対応、フォルダ、バックアップ/復元
- アイコンの遅延読込（現状は起動時に全件読込）

## 設計原則
- 状態とロジックはAppModel系に分離し、UIにルールを書かない（KingStackと同方針）
