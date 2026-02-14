# LifeLog
最大８時間の無充電連続稼働可能な THINKLET向けのカメラ撮影，録音アプリです．  
ある程度Activityにベタ書きもできるように設計しています．
## 開発時
- Android Studio Ladybug Feature Drop | 2024.2.2
# インタフェース
- 起動時の引数として以下をサポートしています．

| パラメータ        | 型                | デフォルト | 説明                     |
| ----------------- | ----------------- | ---------- | ------------------------ |
| `longSide`        | String or Int     | 640        | 撮影する画像の長辺       |
| `shortSide`       | String or Int     | 480        | 撮影する画像の短辺       |
| `intervalSeconds` | String or Int     | 300        | 撮影間隔の秒数．最小10． |
| `enabledMic`      | String or Boolean | false      | 録音を有効化します       |

> [!NOTE]
> `longSide` と，`shortSide` はなるべくその大きさになるように指定するものです．サポートされていない組み合わせの場合，最も近い解像度に変更されます．

# 機能
## 定期撮影
- 定期的に撮影し，Gifファイルに書き出します．
- 書き出しファイルは，最大で600枚の画像撮影ごとに別ファイルに切り替えます．
## （オプション）録音
- 5chマイクを用いて録音し，2chの48kHzのRawファイル形式で保存します．
- 使用すると，消費電力が増加します．
- 書き出しファイルは，最大で1GByteごとに別ファイルに切り替えます．
## MTP公開
- 利用を簡便化するために，保存したデータをMTPモードで公開できるようにしています．
  - `/内部ストレージ/DCIM/lifelog/20241122/20241122_085125.gif` のようなPathに保存されます．
> [!TIP]
> THINKLETでは，以下のコマンドを実行することで，MTPモードへ一時的に切り替えることができます．  
> `adb shell svc usb setFunction mtp true`
# 開発
- `local.properties` に以下を追記します．GithubPackagesにあるライブラリを取得するために使います．
    ```diff
    # GitHub Packages経由でライブラリを取得します．下記を参考にアクセストークンを発行ください．
    # https://docs.github.com/ja/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token
    # read:packages の権限が必須です
    + TOKEN=<github token>
    + USERNAME=<github username>
    ```
- 画像などをアップロードしたいときは，`MicRecordUseCase.kt`, `SnapshotUseCase.kt` の下記の部分で，ファイル保存が完了したときのイベントが記述できます．ここに追記や拡張することで実現できます．
    ```kotlin
    gifEncoderRepository.savedEvent {
        Log.i(TAG, "savedEvent gif: ${it.absoluteFile}")
        fileSelectorRepository.deploy(it)
    }
    ```

    ```kotlin
    audioCaptureRepository.savedEvent {
        Log.i(TAG, "savedEvent mic: ${it.absoluteFile}")
        fileSelectorRepository.deploy(it)
    }
    ```
- インストールは，AndroidStudio あるいは，`./gradlew installDebug` 等で行います．
- 実行前に，[scrcpy](https://github.com/Genymobile/scrcpy) を用いて，設定アプリからすべてのPermissionを許可します．
# KeyConfig
- [開発者ポータルのKeyConfig](https://fairydevicesrd.github.io/thinklet.app.developer/docs/keyConfig/) を使ったキーコンフィグ設定例です．
- `adb push 1440_1080.json /sdcard/Android/data/ai.fd.thinklet.app.launcher/files/key_config.json && adb reboot` で設定可能です．
  - [1440x1080 マイク無し](./keyConfigs/1440_1080.json)
  - [2592x1944 マイクあり](./keyConfigs/2592_1944_withMic.json)

# メモ
- pcから画像やテキストを一括取得するコマンド→'adb pull /storage/emulated/0/DCIM/lifelog/ .'