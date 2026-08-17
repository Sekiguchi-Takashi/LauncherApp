# LauncherApp HANDOFF

## 概要
Androidホームアプリ（ランチャー）。Nova風機能をComposeでゼロから再現する方針。
機能は基本から高度なものまで充実させ、不要になったら外す。
本ファイルは新チャット開始時の引き継ぎ用。

## 構成
- パッケージ: com.appathy.launcher
- Kotlin 2.0.20 / AGP 8.5.2 / compileSdk 34 / minSdk 26 / Compose (BOM 2024.09.00)
- app/debug.keystore 固定（上書きインストール可）
- ビルド: GitHub Actions の release.yml のみ。タグ push で起動し Release に APK を添付する。build.yml は同梱しない
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

## v1.5 実装済み（CI・署名の整理）
- 署名を1系統に統一: app/build.gradle.kts の debug signingConfig が ci/appathy.keystore（alias appathy、パスワードは環境変数 APPATHY_STORE_PASS）を優先し、無ければ app/debug.keystore にフォールバック
- release.yml の apksigner 再署名を廃止（Gradleが署名済みのため）。代わりに apksigner verify --print-certs で署名者を確認
- 鍵とパスワードを GitHub Secrets へ: KEYSTORE_B64（keystoreのbase64）と KEYSTORE_PASSWORD。両ワークフローが実行時に ci/appathy.keystore へ復元し、最後に必ず削除。Secretが未設定ならエラーで停止
- ci/appathy.keystore を .gitignore に追加、deploy.sh が git rm --cached で追跡解除
- deploy.sh に push 前の fetch + pull --rebase を追加（リモート先行時の reject 対策）
- 注意1: 署名鍵が debug.keystore から appathy 鍵に変わるため、v1.4 以前のインストール済みAPKは一度アンインストールが必要
- 注意2: git履歴には ci/appathy.keystore と旧パスワードが残る。完全に消すには履歴書き換えか鍵のローテーションが必要（未実施）
- 注意3: appathy 鍵は key password == store password 前提（旧 release.yml が --ks-pass のみで動作していたことから推定）

## v1.5.1
- ci/set_secrets.py 追加: GitHub Actions の Secrets（KEYSTORE_B64 / KEYSTORE_PASSWORD）を端末から登録するスクリプト
  - 依存: PyNaCl（pkg install -y python libsodium → SODIUM_INSTALL=system pip install pynacl）
  - 使い方: python ci/set_secrets.py <keystoreのパスワード>
  - ci/appathy.keystore をbase64化し、リポジトリの公開鍵でsealed box暗号化してPUT。最後に登録済みSecret名を表示
  - GitHubのSecrets APIは平文登録を受け付けないため、curlのみでは登録不可

## v1.5.2
- ci/release.template.yml 追加: rollout.sh が配布すべき Secrets 版 release.yml のテンプレート
- ci/CATALOGAPP_NOTES.md 追加: CatalogApp 側で必要な修正の申し送り
- 重要: CatalogApp の rollout.sh が GitHub API 経由で release.yml と ci/appathy.keystore を各リポジトリへ直接コミットしているため、LauncherApp 側の v1.5 対策は rollout.sh を直すまで恒久的ではない（次の配布で平文版に戻る）

## v1.6 実装済み
- 時計タップで時計アプリ（AlarmClock.ACTION_SHOW_ALARMS）、日付タップでカレンダー（content://com.android.calendar/time/<millis>）を起動。失敗時はToast
- ドロワーの検索欄をImeAction.Searchにし、キーボードの検索キーで先頭候補を起動

## v1.6.1（deploy.sh 恒久仕様へ更新）
- deploy.sh を指定仕様に統一: shebang は Termux の bash 絶対パス、set -e なし
  - `git add -A` → `git commit` → `git pull --rebase origin main` → `git push -u origin main`
  - push 後に GitHub API で最新リリースのタグを取得し、末尾を +1 した次タグを算出（取得できなければ v1.0.0）
  - heads/main の SHA を取得して git/refs にタグを POST → タグ push で release.yml が起動し、Release が自作アプリストアに更新として現れる
- pull --rebase が必須な理由: CatalogApp が API 経由で release.yml と ci/appathy.keystore を直接コミットするため、無いと push が rejected になる
- ci/ と .github/workflows/release.yml は配布ビルドに必要なため削除しない。旧 deploy.sh にあった `git rm --cached ci/appathy.keystore` と .gitignore の該当行は撤去済み
- release.sh は deploy.sh がタグ発行を内包したため廃止
- 注意: タグは「直近リリースの末尾+1」で決まり、app/build.gradle.kts の versionName とは連動しない。両者がずれるとストア表示とアプリ内バージョンが食い違う

## v1.7 実装済み（フォルダ）
- データ構造: FolderEntry(id, name, apps) を "id|name|pkg/act,pkg/act" 形式で "folders" キーに永続化（Folders）
  - ホーム上のフォルダは HomeItem の packageName = FOLDER_PKG（"__folder__"）、activityName = フォルダID で表現。既存の配置データとは互換
  - 名前に | ; , が入ると壊れるため Folders.sanitize() で置換。空名は「フォルダ」
- 作成: アイコンを別のアイコンへドロップするとフォルダ化（Nova と同じ挙動）。従来の「位置の入れ替え」は廃止し、空きセルへのドロップのみ移動
  - フォルダへドロップ = フォルダに追加、フォルダ同士のドロップ = 統合（dropOnto がすべて処理）
- 表示: FolderIcon が中身の先頭4件を2×2で描画。タップで FolderDialog を開く
- FolderDialog: 上部のテキスト欄で名前変更（入力即保存）、アプリをタップで起動、長押しで「ホームに出す」/「フォルダから削除」
- 自動解散: 中身が1件以下になったらフォルダを削除し、残った1件を元のセルへ戻す（removeFromFolder）
- セル長押しメニュー: フォルダの場合は「フォルダを削除」「フォルダを開く」に切り替わる
- ドラッグ中の浮遊表示もフォルダに対応

## v1.7.1
- build.yml から actions/upload-artifact ステップを削除
  - 理由: Actions の Artifacts ストレージ無料枠（0.5GB）が枯渇し "Artifact storage quota has been hit" でビルドが失敗するため
  - APK は Release から配布するので Artifacts は不要。build.yml はコンパイル確認と署名検証のみを担う
  - 恒久ルール: 今後 build.yml に upload-artifact を入れない

## v1.8 実装済み（iOS風 A案：ドロワー廃止）
- 構造転換: アプリドロワーを廃止。インストール済み全アプリをホームのページへ自動配置（autoPlace）。新規インストールは末尾の空きセルへ自動追加
  - ページ数は固定ではなく contentPages = max(設定ページ数, 実配置の最大ページ+1)。その右に App Library ページを1枚足した totalPages で Pager を構成
  - Dock（お気に入り）に入れたアプリとフォルダ内のアプリは自動配置の対象外
- App Library（最終ページ）: ApplicationInfo.category で自動分類（ゲーム/ミュージック/ビデオ/写真/ソーシャル/ニュース/マップ/仕事効率化/ユーティリティ/その他）。カテゴリごとに角丸カードで最大8件表示、超過分は件数のみ表示。長押しで「ホーム画面に追加」「アプリ情報」
  - ホームから外したアプリは LibraryApps（"library_only" キー）に記録され、App Library にのみ残る
- アイコンを squircle に統一（IconUtil.kt）。AdaptiveIconDrawable は前景・背景を合成、非対応アイコンは白地に縮小配置してから角丸マスク
- Dock: 半透明の角丸パネルに変更、4枠（iOS準拠）
- 時計・日付の大表示を廃止（iOS のホームには無いため）。openClock/openCalendar も削除
- ジェスチャー変更: 下スワイプ = Spotlight風検索（全画面）。通知シェードは画面最上端からのシステムジェスチャーに任せる。上スワイプのドロワー起動は廃止
- セル長押しメニューに「App Libraryへ移動」を追加
- 注意: 旧 AppDrawer と expandNotifications 経路は削除済み。設定の「ページ数」は最小ページ数として機能する

## v1.8.1（ビルド修正）
- v1.8 のコンパイルエラーを修正: ページ下部のヒント文言に `onOpenDrawer()` の参照が残っていた（HomeScreen のパラメータは onOpenSearch に改名済みだった）。ドロワー廃止に伴いヒント文言ごと削除
- 併せて未使用になった expandNotifications を削除（下スワイプは Spotlight 検索に割り当て済み）

## v1.8.2（ビルド修正）
- v1.8.1 で openLauncherChooser() を巻き添え削除していたため復元。未使用の expandNotifications を削除した際、直後に定義されていた openLauncherChooser まで削除範囲に入っていた
- 教訓: 関数を削除するときは「次の関数定義まで」で範囲を切らず、対象関数の本体だけを消す

## v1.9 実装済み（ジグルモード／ホーム画面編集）
- 編集モード（iOS のジグル）を追加。入り口は2つ: ホーム空き領域の長押しメニュー →「ホーム画面を編集」、アイコン長押しメニュー →「ホーム画面を編集」
- 編集モード中の挙動:
  - アイコンが ±2.5度で揺れる（rememberInfiniteTransition + graphicsLayer の rotationZ、隣接セルで位相を反転させて自然に見せる）
  - 左上に「−」バッジ。タップでそのアプリを App Library へ移動しホームから消す（フォルダの場合はフォルダを削除）
  - ドラッグは長押し不要で即座に開始（通常時は従来どおり長押し→ドラッグ）
  - タップ起動は無効（clickable(enabled = !editMode)）
- 終了: 空き領域をタップ、または戻るボタン
- 実装メモ: セルの中身を Box でラップし、Column（アイコン＋ラベル）の上にバッジを重ねている。pointerInput のキーに editMode を含めてモード切替時にジェスチャーを貼り直す

## v2.0 実装済み（フォルダ全画面＋ブラー、検索ピル）
- FolderDialog を廃止し FolderOverlay（全画面オーバーレイ）へ置き換え
  - 背景は半透明の暗幕、カードは角丸28dpの半透明パネル。開くと 0.85 → 1.0 に 180ms でスケールイン（Animatable + tween）
  - 名前欄は枠なしのテキストフィールド、アプリは4列グリッド（52dpアイコン）。長押しで「ホームに出す」「フォルダから削除」
  - 暗幕タップまたは戻るボタンで閉じる。カード内のタップは吸収して誤クローズを防ぐ
- フォルダを開くとワークスペース側に Modifier.blur(20.dp) を適用（HomeScreen だけを内側 Box で包み、オーバーレイはその外に置く）
  - blur は Android 12 以上でのみ効き、それ未満は無視される（暗幕があるので見た目は破綻しない）。壁紙はシステム側の描画なのでぼけない
- Dock の上に iOS 風の「検索」ピルを追加。タップで Spotlight 風検索を開く（下スワイプと同じ動作）
- BackHandler は 3系統: 検索 → 編集モード → フォルダ

## v2.0.1（ビルド修正・CI整理）
- v2.0 のコンパイルエラーを修正: AppLibraryPage の `@OptIn(ExperimentalFoundationApi::class)` が消えていた（FolderDialog を FolderOverlay に置換した際、直後にあった OptIn 行まで削除範囲に入っていた）
- build.yml を `on: workflow_dispatch:` のみに変更。push では走らせず、配布は release.yml のタグ起動に一本化
- 恒久ルール: build.yml は push トリガーを持たない。upload-artifact も入れない

## v2.0.2（CI・deploy.sh の信頼性向上）
- build.yml をリポジトリから削除。CI は release.yml のタグ起動のみ。以後の納品物にも build.yml は含めない
- deploy.sh のタグ対象 SHA を API 取得（`git/ref/heads/main`）から `git rev-parse HEAD` に変更
  - 理由: push 直後に API を叩くと GitHub 側の反映待ちで「ひとつ前のコミット」にタグが付くことがあり、実際に v1.8.4 が修正前のコミットを指してビルドが落ちた
  - ローカルの HEAD を使えば、push した内容と必ず一致する
- FolderOverlay の LazyVerticalGrid に `heightIn(max = 420.dp)` を追加
  - 補足: 親の Column はスクロール可能ではないため高さは無限にならず、クラッシュはしない。中身の多いフォルダでカードが画面外へ伸びるのを防ぐための上限

## v2.0.3 実装済み（アイコン遅延読込）
- AppEntry から icon フィールドを削除。loadApps() はラベル・パッケージ名・アクティビティ名・カテゴリのみを取得する軽い処理になり、起動時に全アイコンを生成しなくなった
- IconCache（IconUtil.kt）: ConcurrentHashMap のメモリキャッシュ。load() は Dispatchers.IO で ComponentName から ActivityInfo を引き、squircle 化して格納。取得失敗時は null
- AppIcon コンポーザブル: キャッシュにあれば即描画、無ければ LaunchedEffect で読み込む。読み込み中は角丸の半透明プレースホルダを表示（レイアウトが跳ねない）
- 全描画箇所を AppIcon に統一（ワークスペース48dp / ドラッグ中56dp / Dock56dp / フォルダミニ18dp / フォルダ展開52dp / App Library44dp / 検索40dp）
- アプリの追加・削除・更新のブロードキャスト受信時は IconCache.clear() してから再読込（古いアイコンが残らない）

## v2.1 実装済み（iOS 26 / Liquid Glass 寄せ）
- アイコンの外観スタイルを5種類から選択可能に（IconStyle）: デフォルト / ダーク / 色合い調整 / クリア（ライト）/ クリア（ダーク）
  - デフォルト: アダプティブアイコンの背景＋前景をそのまま合成
  - ダーク: 背景を #1C1C1E に置き換え、前景のみ描画
  - 色合い調整: グレースケール化した前景を指定色で着色し、暗い背景に載せる
  - クリア: 半透明の地（白 25% / 黒 25%）に、明度を上げたグレースケール前景。さらに縁に細いハイライトを描いてガラス感を出す
  - 設定ダイアログの「アイコンの外観」から選択。LauncherSettings の "icon_style" キーに保存
- IconCache のキーにスタイル名を含めるため、切り替え時に再生成され、元のスタイルもキャッシュに残る
- 角丸半径を 0.225 → 0.235 に拡大（iOS 26 のやや丸いアイコン形状に合わせる）
- Dock と検索ピルに 1dp のハイライト縁を追加（Liquid Glass のエッジ表現の簡易版）
- 注意: 実機の Liquid Glass は背景の屈折・鏡面反射を伴うが、Compose では再現できないため、半透明＋縁ハイライト＋ブラーで近似している

## v2.1.1 実装済み（画面下部の整理・アイコン拡大）
- 画面下部から次を削除: 「デフォルトのホームに設定」ボタン、「検索」ピル、ランチャー切替アイコン
  - いずれもホーム空き領域の長押しメニューへ移設（検索 / デフォルトのホームに設定（未設定時のみ）/ ホーム設定を開く）
  - 設定ダイアログの「ランチャー切替アイコン」スイッチと LauncherSettings の switchIcon 参照も撤去（AppModel の関数自体は残置）
- Dock はお気に入りが1つも無いときは描画しない（空のガラスパネルが残らない）
- アイコンサイズを iPhone 相当に拡大: ワークスペース 48→60dp、Dock 56→60dp、ドラッグ中 56→68dp、フォルダのタイル 48→60dp（内部ミニアイコン 18→23dp）、ラベル 10→11sp
- フォルダ作成は従来どおりドラッグ＆ドロップ（アイコンを別のアイコンに重ねる）。編集モード中は長押し不要で即ドラッグできるため、まとめる操作はそちらが確実

## v2.2 実装済み（設定アプリ）
- iOS の「設定」に似た全画面の設定アプリを追加し、ホーム上に「設定」タイルとして配置
  - タイルは HomeItem の packageName = SETTINGS_PKG（"__settings__"）で表現。ensureSettingsTile() が最初の空きセルに1つだけ作る
  - 編集モードの「−」バッジまたは長押しメニュー「設定アイコンを隠す」で消せる。SettingsTile("settings_tile_hidden") に記録し、設定画面から戻せる
  - アイコンは res/drawable/ic_settings_app.xml（歯車）をグレーの角丸タイルに載せて描画
- 設定画面の構成（このランチャーで実際に変更できる項目のみ）
  - アイコンの外観: IconStyle 5種をチェックマーク付きで選択
  - ホーム画面: 最小ページ数 / グリッド行数 / グリッド列数のステッパー、ホーム画面を編集、ウィジェットを追加、（隠している場合）設定アイコンをホームに戻す
  - 壁紙とシステム: 壁紙を変更、デフォルトのホーム（現状表示つき）、ホーム設定を開く
  - App Library のみに置いたアプリ: 一覧から個別に「ホームに戻す」
  - 情報: アプリ数、バージョン（PackageManager から取得）
- 旧 SettingsDialog / SettingRow は削除し、ホーム長押しメニューの「設定」もこの画面を開く
- BackHandler は4系統: 検索 → 編集モード → フォルダ → 設定アプリ

## v2.3 実装済み（オントロジーの「着手可能」を6件）
- アプリ起動アニメーション: アイコンの位置から拡大して開く
  - セルの Column に onGloballyPositioned を付けて boundsInWindow を記録し、タップ時に LaunchSource.rect へ格納
  - launchApp() が ActivityOptions.makeScaleUpAnimation(rootView, rect...) を作って startActivity に渡す。rect は使用後 null に戻す（古い位置から開かないように）
- ShortcutManager 対応（iOS の Quick Action 相当）: アイコン長押しメニューの先頭に、そのアプリのショートカットを最大4件表示。LauncherApps.getShortcuts は既定ランチャーでないと SecurityException になるため runCatching で握りつぶし、取れなければ何も出さない
- Spotlight に Web 検索を追加: 入力があるとき先頭に「「〜」をWebで検索」行。ACTION_WEB_SEARCH、失敗時は Google の URL にフォールバック
- ウィジェットのサイズプリセット: 編集ダイアログに 小2×2 / 中2×列数 / 大4×列数 を追加（iOS の Small / Medium / Large 相当）
- フォルダ展開を spring アニメーションに変更（DampingRatioMediumBouncy）
- アイコンに 6dp の落ち影を追加（clip = false で形状に沿わせる）

## v2.4 実装済み（設定 > アプリ一覧：非表示と削除）
- HiddenApps（"hidden_apps" キー）を新設。libraryOnly（App Library のみに置く）とは別概念で、こちらは完全に隠す
- 設定アプリ > ホーム画面 > 「アプリ一覧」から AppListScreen を開く。件数バッジに非表示数を表示
- AppListScreen の機能
  - アプリ名での絞り込み、「非表示だけを見る」トグル
  - 各行に アイコン / 名前 / パッケージ名（非表示中は「非表示中」表示）と、非表示⇄表示 の切替、削除、情報 のボタン
  - 削除は ACTION_DELETE でシステムのアンインストール確認へ遷移。完了後は PACKAGE_REMOVED を受けて一覧が更新される
- 非表示にしたとき: ホームの配置から除去、フォルダの中身からも除去、Dock（お気に入り）からも除去。表示に戻すと autoPlace が空きセルへ再配置する
- LauncherRoot は visibleApps（apps から hiddenApps を除いたもの）をホーム・検索・フォルダ・自動配置へ渡す。設定とアプリ一覧だけが全件の apps を受け取る

## v2.5 実装済み（コントロールセンター）
- SystemControl.kt を新設。Android で実際に操作できるものだけを扱う
  - 明るさ: Settings.System.SCREEN_BRIGHTNESS。WRITE_SETTINGS 権限が要るため Settings.System.canWrite() で判定し、未許可なら「許可する」ボタンから ACTION_MANAGE_WRITE_SETTINGS へ誘導。変更時は自動調整を手動モードへ切り替える
  - 音量: AudioManager の STREAM_MUSIC。権限不要
  - メディア操作: AudioManager.dispatchMediaKeyEvent で 前の曲 / 再生・停止 / 次の曲。通知アクセス権限を取らずに再生中アプリを操作できる
  - Wi-Fi / Bluetooth / 機内モード / 画面: Android 10 以降トグル操作が禁止されているため、設定パネル（Settings.Panel.ACTION_WIFI など）を開く導線のみ
- ControlCenter コンポーザブル: 暗幕＋ガラスタイル。タイル2列、明るさと音量のスライダー、メディア操作3ボタン
- 開き方は2つ: 画面右寄り（幅の60%より右）から下スワイプ、またはホーム長押しメニューの「コントロール」
  - 左寄りの下スワイプは従来どおり Spotlight 検索。detectVerticalDragGestures の onDragStart で開始 X 座標を見て振り分ける
- AndroidManifest に WRITE_SETTINGS を追加

## v2.6 実装済み（ホームアプリ切替）
- 症状: アプリから Android のホームボタンを押すと Nova Launcher に戻る。原因は端末の既定ホームが Nova のままで、本アプリは「アプリ一覧から起動されたときだけ表示される」状態だったこと
- SystemControl.kt に HomeApps を追加
  - currentPackage / currentLabel: HOME インテントを MATCH_DEFAULT_ONLY で解決して現在の既定を取得（"android" が返る場合は「未設定（毎回選択）」）
  - list: インストール済みホームアプリを列挙（AndroidManifest の <queries> に HOME インテントを追加済み）
  - open: 指定したホームアプリを一度だけ起動する
- LauncherSwitchScreen: 設定アプリ > 壁紙とシステム > 「ホームアプリ」から開く
  - 現在の既定を名前で表示、既定でなければ「このランチャーを既定にする」（RoleManager のダイアログ）
  - 「システムのホーム設定を開く」
  - インストール済みホームアプリ一覧。各行の「開く」でそのランチャーを一度だけ起動できる
  - 注意書き: Android では他アプリを既定に設定する操作をアプリ側から行えないため、既定変更は上記2ボタン経由になる
- ホーム画面上部に、既定でないときだけ案内バナーを表示（タップで RoleManager のダイアログ）

## v2.7 実装済み（通知センター・再生中カード）
- NotificationCenter.kt を新設
  - LauncherNotificationService : NotificationListenerService。activeNotifications を NotifItem に変換して companion の mutableStateListOf に保持し、投稿・削除のたびに更新
  - dismiss(key) / dismissAll() で個別・一括消去。isEnabled() は Settings.Secure の enabled_notification_listeners を見る。openSettings() で許可画面へ
  - MediaInfo.current(): MediaSessionManager.getActiveSessions に本サービスの ComponentName を渡して再生中の曲名・アーティスト・アプリ名・再生状態を取得（通知アクセス権限が前提）
- AndroidManifest に BIND_NOTIFICATION_LISTENER_SERVICE のサービスを登録
- NotificationCenterScreen: 未許可なら説明と「許可する」、許可済みなら通知カード一覧（アプリ名 / タイトル / 本文、消去可能なものに ×、上部に「すべて消去」）
- コントロールセンターの上部に NowPlayingCard を追加（再生中のときだけ表示、曲名・アーティスト・前後送りと再生停止）
- 下スワイプのジェスチャーを3分割に変更: 左33%未満=通知、中央=Spotlight検索、右66%超=コントロール。長押しメニューにも「通知」を追加
- 設定 > 壁紙とシステム に「通知へのアクセス」（許可済み / 未許可）を追加

## v2.8 実装済み（ページ間ドラッグ移動・バックアップ/復元）
- ドラッグでのページ間移動: アイコンをページの左右端（幅の 8% 以内）で離すと、隣のページへ移動しつつ Pager もそのページへスクロールする
  - 通常モード・編集モードの両方の onDragEnd に判定を追加。編集モードは長押し不要なので操作が速い
  - WorkspacePage に onScrollToPage を追加し、HomeScreen 側で rememberCoroutineScope + pagerState.animateScrollToPage を呼ぶ
- バックアップ/復元: AppModel に BackupData を追加
  - serialize(): SharedPreferences の対象キー（favorites / home_items / widget_items / folders / library_only / hidden_apps / icon_style / pages / rows / cols / settings_tile_hidden / switch_icon）を "型\tキー\t値" のタブ区切りテキストにする。1行目は識別子 launcherapp-backup-v1
  - restore(): 識別子が一致しないファイルは拒否して false を返す
  - 設定アプリに「バックアップ」セクションを追加。書き出しは CreateDocument、復元は OpenDocument（SAF なのでストレージ権限は不要）
  - 復元後は IconCache.clear() のうえ Activity.recreate() で状態を読み直す

## v2.9 実装済み（長押し→揺れ→移動）
- 長押しから指を動かした瞬間に、全アイコンが揺れ始めるようにした
  - graphicsLayer の条件を editMode から `editMode || dragItem != null` に変更。ドラッグ中は常にジグルが走る
  - 実装上の注意: pointerInput のキーに editMode を含めているため、ドラッグ中に editMode を切り替えるとジェスチャーが貼り直されて移動が中断する。そのため揺れは dragItem を見て表示し、editMode への移行はドラッグ完了後に行う
- 通常モードでドラッグ移動が成立したら（移動距離がセル幅の 20% 以上）そのまま編集モードへ入る。以降は長押し不要で連続して並べ替えできる
- 編集モード中でも、アイコンを押して動かさずに離すと長押しメニューが出るようにした（判定はセル幅の 15% 未満）。ショートカットやアプリ情報に編集モードのままアクセスできる

## v2.9.1（バグ修正：長押しで揺れない・移動できない）
- 原因: ホーム空き領域用の長押し検出（detectTapGestures の onLongPress）を、ページ全体を覆う Box に付けていた
  - Compose ではポインタイベントが子から親へ伝播するため、アイコンを長押しすると子のドラッグ検出と親のタップ検出が同時に走り、先に親の onLongPress が発火してホームメニューが開く
  - メニューが開くとドラッグが打ち切られ、「揺れない・移動できない」ように見えていた
- 修正: 空き領域用の検出を WorkspacePage の中へ移し、グリッドの背後に敷いた専用の Box に付けた
  - Compose のヒットテストは最前面の枝だけを拾うため、アイコンに触れたときは背景レイヤに届かない。空きセルにはポインタ入力が無いので背景レイヤが拾う
  - HomeScreen 側の Box からは pointerInput を外し、ホームメニューの DropdownMenu だけを残した
- WorkspacePage に onExitEdit / onEmptyLongPress を追加
- 既知の副作用: 縦スワイプ（検索・通知・コントロール）の検出は従来どおり画面全体に掛かっているため、アイコンの上から素早く下に払うと長押しが成立せずスワイプ側が動く。長押しは指を止めてから動かす操作になる

## v3.0 実装済み（ジェスチャー競合の解消・クイックパネル）
- 画面全体に掛かっていたポインタ処理をすべて撤去した。これが長押しが効かなかった根本原因
  - ホーム空き領域の長押しメニュー（DropdownMenu と homeMenu 状態）を削除
  - 画面全体の縦ドラッグ検出（下スワイプで検索 / 通知 / コントロール）を削除
  - これでアイコンの pointerInput と競合するものが無くなり、長押し→ドラッグが素直に動く
- 旧ホームメニューの項目は分散した
  - クイックパネル（新設）: ウィジェットを追加 / ホーム画面を編集 / 検索 / 通知 / コントロール / 設定 / 壁紙を変更
  - 設定アプリ > ホーム画面: 検索を開く / 通知を開く / コントロールを開く を追加
- クイックパネル: 画面最下部のハンドル（幅120dp の横棒）を上にスワイプ、またはタップで開く
  - 「ウィジェットを追加」を押すと同じ画面内がウィジェット一覧に切り替わり、そこから選んでそのまま配置できる（従来の WidgetPickerDialog を経由しない）
  - 一覧から「← メニューに戻る」で戻れる
- 編集モード中はページドットの上に「完了」ボタンを表示（空き領域タップでの終了が無くなったため）
- ハンドル以外の場所にはジェスチャーが無いので、アイコンの長押し・ドラッグが最優先で処理される

## v3.0.1（長押しの実装方式を変更）
- v2.9.1 と v3.0 の修正では直らなかったため、ジェスチャーの実装方式そのものを差し替えた
- 旧: セルに `clickable` と `pointerInput { detectDragGesturesAfterLongPress }` を重ねる独自実装
- 新: モードごとに Modifier を出し分ける
  - 通常モード: `combinedClickable(onClick = 起動/フォルダ/設定, onLongClick = onEnterEdit)`
    - 長押しした時点で編集モードに入り、アイコンが揺れ始める
  - 編集モード: `pointerInput { detectDragGestures }` で長押し不要の即ドラッグ。動かさず離すと長押しメニュー（判定はセル幅の 15% 未満）
- 根拠: このアプリで長押しが確実に動いている箇所（App Library / Spotlight 検索 / フォルダ展開）はすべて combinedClickable を使っており、ホーム画面のセルだけが独自実装だった
- WorkspacePage に @OptIn(ExperimentalFoundationApi::class) を付与、未使用になった detectDragGesturesAfterLongPress の import を削除
- 操作の流れ: アイコンを長押し → 全体が揺れる → そのままでも指を離してからでも、ドラッグで移動 → 「完了」ボタンで終了
