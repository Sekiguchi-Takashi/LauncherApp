# CatalogApp rollout.sh への申し送り

## 背景
CatalogApp の rollout.sh が GitHub API 経由で各リポジトリに以下を直接コミットしている。

- `.github/workflows/release.yml`（署名パスワードが平文で直書き）
- `ci/appathy.keystore`（リリース署名鍵そのもの）

LauncherApp 側では v1.5 で Secrets 方式へ切り替えたが、rollout.sh のテンプレートが元のままなので、
**次に rollout.sh が走った時点で平文版の release.yml に戻り、keystore も再びコミットされる**。
`.gitignore` は既にコミット済み・API経由で送られるファイルを止められないため対策にならない。

## 必要な修正（CatalogApp 側）
1. rollout.sh が配布する release.yml を `ci/release.template.yml`（本リポジトリ同梱）に差し替える
   - `--ks-pass pass:...` の平文パスワードを廃止
   - Gradle 側で署名するため apksigner の再署名を廃止し、verify のみ
   - 鍵は `secrets.KEYSTORE_B64` から実行時に復元し、ジョブ終了時に削除
2. rollout.sh から `ci/appathy.keystore` のコミット処理を削除する
3. 各リポジトリに Secrets を登録する処理を追加する
   - `KEYSTORE_B64` / `KEYSTORE_PASSWORD`
   - GitHub の Secrets API は平文登録不可（sealed box 暗号化が必須）
   - LauncherApp の `ci/set_secrets.py` がそのまま流用できる（REPO 定数を引数化すれば汎用になる）
4. 配布先リポジトリの `app/build.gradle.kts` が
   `ci/appathy.keystore` + 環境変数 `APPATHY_STORE_PASS` で署名する形になっている必要がある
   （LauncherApp v1.5 の signingConfigs を参照。鍵が無ければ debug.keystore にフォールバック）

## 未解決事項
- 各リポジトリの git 履歴に旧 keystore と旧パスワードが残っている。
  完全に消すには履歴書き換え、または鍵のローテーションが必要。
- 鍵をローテーションする場合、既に配布済みの APK は上書き更新できなくなる。
- appathy 鍵は key password == store password 前提（旧 release.yml が `--ks-pass` のみで動作していたことから推定）。
