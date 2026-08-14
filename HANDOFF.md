# LauncherApp HANDOFF

## 概要
Androidホームアプリ（ランチャー）。Nova風機能をComposeでゼロから再現する方針。
機能は基本から高度なものまで充実させ、不要になったら外す。
本ファイルは新チャット開始時の引き継ぎ用。

## 構成
- パッケージ: com.appathy.launcher
- Kotlin 2.0.20 / AGP 8.5.2 / compileSdk 34 / minSdk 26 / Compose (BOM 2024.09.00)
- app/debug.keystore 固定（上書きインストール可）
- ビルド: GitHub Actions (build.yml, gradle 8.7, assembleDebug → artifact "app-debug")
- リポジトリ: Sekiguchi-Takashi/LauncherApp (private)

## ファイル
- AppModel.kt: AppEntry / HomeItem / loadApps() / Favorites / Workspace（ホーム配置の永続化、"page|row|col|pkg|activity" を ";" 連結）/ LauncherSettings（pages/rows/cols）/ firstFreeCell()
- MainActivity.kt: LauncherRoot / HomeScreen（時計＋HorizontalPagerワークスペース＋ページドット＋Dock）/ WorkspacePage（rows×colsグリッド）/ AppDrawer / SettingsDialog

## v1.0 実装済み
- HOMEインテント（singleTask / stateNotNeeded / clearTaskOnLaunch / excludeFromRecents）
- 壁紙透過テーマ、時計・日付、Dock（お気に入り最大5）
- ドロワー: 検索、タップ起動、パッケージ増減で自動更新
- デフォルトホーム設定（RoleManager）、戻るでドロワーを閉じる

## v1.1 実装済み（続きはv1.2の節）
- ワークスペース: HorizontalPagerでページング（既定3ページ）、rows×colsグリッド配置（既定5×4）、ページインジケーター
- ドロワー長押しメニュー: ホームに追加（最初の空きセルへ）/ お気に入り追加・解除 / アプリ情報
- ホームアイコン長押しメニュー: ホームから削除 / アプリ情報
- ホーム空き領域長押し: 設定（ページ数・行数・列数の±調整）/ 壁紙変更
- ジェスチャー: 下スワイプで通知シェード展開（reflection、失敗時は無害に無視。Pixel系の新しいOSでは動かない可能性あり）

## 未実装（拡張候補）
- アイコンのドラッグ&ドロップ移動（現状は削除→追加で配置し直し）
- ウィジェット（AppWidgetHost）
- ジェスチャー割当のカスタマイズ、icon pack、フォルダ、バックアップ/復元
- アイコンの遅延読込（現状は起動時に全件読込）

## 設計原則
- 状態とロジックはAppModel系に分離し、UIにルールを書かない（KingStackと同方針）
- 配置データの互換性: Workspaceのエンコード形式を変える場合はマイグレーションを書く

## v1.2 実装済み
- ワークスペースのドラッグ&ドロップ: 長押し→ドラッグでアイコン移動（同一ページ内）、移動先が埋まっていれば位置を交換、指に浮いてついてくる表示（offsetラムダ内でだけ座標を読むKingStack方式）
- 長押しして動かさず離すと従来のメニュー表示（移動距離がセル幅20%未満ならメニュー扱い）
- ページ間移動はメニューから「左/右のページへ移動」（移動先ページの最初の空きセルへ）
- Dockアイコンの長押しメニュー: お気に入りから外す / アプリ情報
- placeItem()（移動と位置交換）/ freeCellOnPage() をAppModelに追加

## v1.3 実装済み（ウィジェット）
- AppWidgetHost(hostId=1024) をMainActivityで保持、onStart/onStopでstartListening/stopListening
- 追加フロー: 空き領域長押し→「ウィジェットを追加」→自前ピッカー（installedProvidersのラベル一覧）→allocate→bindAppWidgetIdIfAllowed、不可ならACTION_APPWIDGET_BINDで許可ダイアログ→configureありならACTION_APPWIDGET_CONFIGURE起動→配置
- 配置: minWidth/minHeightからスパンを概算（列=画面幅dp/列数で割る、行=96dp/行の目安）、freeRegion()でアプリ・既存ウィジェットと重ならない最初の領域へ。空きがなければToastで拒否しID解放
- WidgetItem(page|row|col|rowSpan|colSpan|widgetId) を "widget_items" キーで永続化
- 表示: WorkspacePage上にAndroidView(host.createView)をoffset/sizeで重ねる。provider消滅時(getAppWidgetInfo==null)は非表示
- 操作: ウィジェット右上の「⋮」→ 位置とサイズを編集（上下左右移動・スパン±のダイアログ）/ 左右ページへ移動 / 削除（deleteAppWidgetId）
- アプリ側の配置判定はウィジェット占有セルを回避（cellCoveredByWidget）
- 既知の注意: 一部ウィジェットのconfigure直接起動はSecurityExceptionの可能性→runCatchingで失敗時は設定なしで配置。ウィジェット自体のドラッグ移動は未対応（AndroidViewがタッチを消費するため⋮ボタン方式）

## v1.4 実装済み（ランチャー切替アイコン）
- Dock右端に「ランチャー切替」アイコン（drawable/ic_switch_home.xml）。タップでホーム設定（ランチャー選択画面）を起動
- openLauncherChooser(): ACTION_HOME_SETTINGS → MANAGE_DEFAULT_APPS_SETTINGS → ACTION_SETTINGS の順にフォールバック、全滅ならToast
- 長押しメニュー: ホーム設定を開く / このランチャーを既定にする（RoleManager） / このアイコンを隠す
- 設定ダイアログにSwitch「ランチャー切替アイコン」を追加（LauncherSettings.switchIcon、既定ON）
- 注意: 端末がすでに本アプリを既定ホームにしている場合、RoleManagerの要求は何も起きないため、切替にはホーム設定画面を使う設計にしている
