# Open Lynx (com.open.lynx) — Alpha2 SDK rebuilt for alpha2services_base 3.002

呢個 repo 係 Alpha2 機械人嘅開源 SDK + 一個簡化測試面板，AIDL 介面同實作**完全對照
`com.ubtechinc.alpha2services_base.3.002.apk`（package: `com.ubtechinc.alpha.serverlibutil.aidl`）
反編譯結果重新寫過**，同呢個 repo 之前嘅版本（對應 v1.1.7.3、`com.ubtechinc.alpha2serverlib.aidlinterface`
package）**完全唔相容** —— 兩個係唔同韌體版本、唔同 AIDL 介面。

## 呢次改咗咩

舊版 SDK 對應嘅機械人韌體有 chest/head 原始 serial byte 協定、XMPP 自訂訊息、
5-mic LED 硬件直通等子系統；新版 APK（3.002）完全冇呢啲嘢，改用一個統一嘅
`IServiceFetcher` binder broker + 5 個語義化子服務（action/motor/led/speech/sysinfo）。
因此：

- **舊 `sdk-module` 全部代碼已刪除**（`alpha2serverlib` package、`developer/*`、
  chest/header serial 協定常數、XMPP util）
- **21 個 AIDL interface + 6 個 Parcelable + 3 個 LED enum** 已經根據反編譯結果重寫，
  放喺 `com.ubtechinc.alpha.serverlibutil.aidl` / `com.ubtechinc.alpha.sdk.led` package
- **新 `ServiceFetcher`/`Alpha2RobotApi` facade** 完全重寫，method 名跟返新 AIDL
  （`action_`/`motor_`/`led_`/`speech_`/`sys_` 前綴）
- **`app` module 嘅舊 1421 行 HTTP/WebSocket/相機/錄音測試面板已整個換走**，改用一個
  簡單、原生 View 寫嘅 test panel（一個 AIDL call 一粒掣 + 一個 log）

## 反編譯方法論（透明度聲明）

**呢個唔係乾淨室開發** —— AIDL 介面、method 順序、Parcelable field layout，全部
係用 `androguard`（Python，內建 DAD decompiler）對 `com_ubtechinc_alpha2services_base_3_002.apk`
逐個 class 反編譯確認：

1. 搵晒 `com.ubtechinc.alpha.serverlibutil.aidl` package 底下所有 class（20 個 interface
   演化到 21 個、6 個 Parcelable），逐個攞 `$Stub` 嘅 `TRANSACTION_xxx` 常數 + `onTransact()`
   嘅 `switch` case，確認每個 method 嘅**聲明順序**（呢個直接決定 Binder transaction id，
   一錯就會 call 錯 method）。
2. Parcelable（`ActionInfo`/`AlarmInfo`/`LedInfo`/`MotorAngle`/`MotorInfo`/`SpeechVoice`）
   逐個攞返 `writeToParcel`/建構子/`readFromParcel` 嘅**讀寫順序**同 field 型別。
3. `IServiceFetcher` 嘅實際 binder 攞取方式（`content://alpha2.service.BinderProvider`
   + method `"@"` + Bundle key `"fetchBinder"`），同每個子服務嘅 `getService()` string
   key（`"action"`/`"motor"`/`"led"`/`"sysinfo"`/`"speech"`），係反編譯 APK 內部
   obfuscated helper class（`ru`/`rt`/`rv`/`rw`/`rx`）確認 —— 呢啲 key 冇公開文檔，
   純粹靠反編譯搵到。

如果你哋自己有官方 SDK 文檔/原始碼，麻煩對照一下上面呢幾個 service key 同
transaction 順序，如果發現有出入請話俾我知。

## 檔案結構

```
OpenLynx/
├── docs/
│   └── AIDL_GUIDE.md                       — 全部 21 個 AIDL interface 逐個 method 詳解
├── sdk-module/ubtechalpha2robot/
│   └── src/main/
│       ├── aidl/com/ubtechinc/alpha/serverlibutil/aidl/
│       │   ├── I*.aidl              — 21 個 AIDL interface
│       │   └── *.aidl               — 6 個 parcelable 聲明
│       └── java/com/ubtechinc/
│           ├── alpha/serverlibutil/aidl/   — 6 個 Parcelable .java 實作
│           ├── alpha/sdk/led/              — Led/LedColor/LedEffect enum
│           ├── alpha2robot/
│           │   ├── ServiceFetcher.java     — IServiceFetcher binder broker
│           │   ├── Alpha2RobotApi.java     — 主 facade，65 個 method
│           │   ├── SampleUsage.java        — 獨立範例 class，逐個子系統示範用法
│           │   └── constant/UbxErrorCode.java
│           └── constant/                   — ActionType/CustomLanguage/LanguageType
├── app/
│   ├── build.gradle
│   ├── debug.keystore
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/open/lynx/MainActivity.java  — 簡化測試面板
```

想睇每個 AIDL method 嘅詳細參數/回調解釋，請睇 [`docs/AIDL_GUIDE.md`](docs/AIDL_GUIDE.md)；
想睇實際 code 點寫，請睇 `SampleUsage.java`。

## 服務對照表

| Service key | AIDL interface | 主要功能 |
|---|---|---|
| `"action"` | `IActionService` | 播放/停止預錄動作、攞動作列表 |
| `"motor"` | `IMotorInterface` | 伺服角度控制（絕對/相對）、省電模式 |
| `"led"` | `ILedInterface` | 眼/頭/嘴/wifi/胸口 LED 開關同特效 |
| `"speech"` | `ISpeechInterface` | TTS 播放、ASR、喚醒詞、PCM 串流 |
| `"sysinfo"` | `ISysService` | 版本查詢、電量、鬧鐘、PIR 感應器 |

（`IServiceFetcher` 本身唔included 喺 facade 入面，因為佢係內部 binder broker，
由 `ServiceFetcher.java` 內部處理。）

## Build 方法

呢個沙盒環境冇 Android SDK/aapt2/d8，所以冇辦法喺度直接砌出 .apk。有兩條路：

### 方法 A：GitHub Actions（推薦）

1. 將呢個 repo push 上 GitHub：
   ```bash
   cd OpenLynx
   git init
   git add .
   git commit -m "Alpha2 SDK rebuilt for base3.002"
   git branch -M main
   git remote add origin https://github.com/<你的帳號>/<repo名>.git
   git push -u origin main
   ```
2. 如果 repo 未有 `.github/workflows/build-apk.yml`，需要你自己加一個標準嘅
   Android Gradle build workflow（`actions/setup-java` + `./gradlew assembleDebug`）。
3. Build 完成後喺 Actions run 嘅 Artifacts 度攞 `app-debug.apk`。

### 方法 B：你自己有 Android SDK 嘅機器

```bash
cd OpenLynx
./gradlew assembleDebug
```

輸出喺 `app/build/outputs/apk/debug/app-debug.apk`。已經包含 `app/debug.keystore`
（標準 debug key，密碼 `android`，alias `androiddebugkey`）。

## 驗證狀態

已經做咗：
1. 逐個 AIDL interface 對照反編譯結果核實 method 順序、參數型別、transaction id。
2. 逐個 Parcelable 對照反編譯結果核實 field 順序同 read/write 邏輯（包括
   `LedInfo` 嘅自訂 `Set<LedColor>`/`Set<LedEffect>` marshalling 同其原本嘅
   "loop bound off-by-one" 行為，特登保留咗以確保 wire-compatible）。
3. 用 Python 檢查 `Alpha2RobotApi.java` 嘅大括號/括號平衡，同確認 facade 入面
   實際調用咗嘅 65 個 `svc.xxx()` method，同 5 個主要 interface 嘅全部 method
   一一對應，冇缺漏。
4. 全面掃描確認冇殘留任何舊 package/class（`alpha2serverlib`、`developer.*`、
   `mic5.LedControl`、`StaticValue` 等）嘅 reference。

**未驗證**：真正嘅 Android 編譯（`aapt2`/`d8`/manifest merge）、喺真實機器人上嘅
實機測試（尤其係 `IServiceFetcher` 嘅 `ContentProvider` 綁定方式、5 個 service
key 係咪喺呢個確切韌體版本準確）。呢幾步需要你喺有 Android SDK 嘅環境度
`./gradlew assembleDebug`，再裝落機械人先可以確認。

## 已知限制

- 冇自訂語音詞彙（機器人用寫死嘅語法，SDK 呢層 call 唔到 —— 呢個係新舊版本
  共通嘅限制）。
- 測試面板只覆蓋每個子系統嘅代表性 method，唔係全部 65 個都有對應按鈕（但
  `Alpha2RobotApi` 本身已經 100% 覆蓋咗全部 AIDL method，缺嘅淨係 UI 按鈕，
  你可以照樣直接調用）。
- `ServiceFetcher` 嘅 `ContentProvider` binder 攞取方式假設咗
  `alpha2.service.BinderProvider` 呢個 authority、`"@"` method、`"fetchBinder"`
  bundle key 喺呢個韌體版本準確 —— 呢啲全部係反編譯確認，但冇實機驗證過。

## 覆核記錄

第二次逐個 AIDL 檔案對照反編譯結果覆核時搵到並修正咗一個問題：

- **`ISpeechInterface.getSpeechVoices()` 返回型別**：原本寫成 `List<SpeechVoice>`
  （typed list，會生成 `writeTypedList`/`createTypedArrayList`），但反編譯
  `$Stub`/`$Stub$Proxy` 發現機械人實際用嘅係**冇 generic 參數嘅 raw `List`**
  （`writeList`/`readArrayList`）—— AIDL 舊版編譯器容許 `List` 唔帶 type
  parameter，兩種寫法生成嘅 wire format 唔相容。已改正 `.aidl` 做
  `List getSpeechVoices();`，並喺 `Alpha2RobotApi.speech_getSpeechVoices()`
  入面加咗 unchecked cast，令 facade 對外嘅型別（`List<SpeechVoice>`）保持唔變。

其餘 20 個 interface + 6 個 Parcelable + 3 個 enum，逐一同反編譯結果比對
method 順序、transaction id、參數方向（`in`/`out`）、field 讀寫順序，全部
確認一致，冇再搵到第二個問題。
