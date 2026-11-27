# Receipt Print

[English](https://github.com/mofua-suki/receipt-print/blob/main/README.md) · [pub.dev](https://pub.dev/packages/receipt_print)

`receipt-print` は、呼び出し元が構築した汎用印刷ツリーをサーマルプリンター向けコマンド、または ReceiptLine プレビューへ変換する Kotlin Multiplatform ライブラリです。

注文、キッチン伝票、会計、再印刷などの業務概念は持ちません。呼び出し元が業務データを `PrintJobDto` へ変換し、このライブラリはツリーの検証、CP932 レイアウト、出力形式への変換だけを担当します。プリンターとの接続、送信、再試行、キュー管理も呼び出し元の責務です。

## 公開モデル

```text
PrintJobDto
└── tickets: List<TicketDto>
    ├── children: PrintNodeListDto  // value class
    │   ├── TextDto
    │   ├── InlineLineDto
    │   ├── TwoColumnDto
    │   ├── RuleDto
    │   ├── QrCodeDto
    │   ├── FeedDto
    │   └── ColumnDto
    │       └── children: PrintNodeListDto
    └── cut: CutModeDto
```

`ReceiptEngine.compile(job, printer)` は `CompileResponseDto` を返します。変換した機種別コマンドは `bytes` から取得します。
`ReceiptEngine.preview(job, printer)` は `PreviewResponseDto` を返します。ReceiptLine の出力は `receiptLine` から取得します。
変換に失敗したときは出力が `null` になります。エラーや近似表現は、ツリー上の位置を含む `diagnostics` で確認できます。

`PrinterProfileDto` で指定するのは機種、1 行の桁数、互換モード、サウンドの許可です。TM-m30 でサウンドを使う場合は、外付けブザーを設定済みであることも明示します。対応コマンドセットとその他のプリンター能力は選択した機種からライブラリ内で解決します。

## モジュール

- `receipt-core`: 不変 AST、Kotlin DSL、検証、CP932 レイアウト、コンパイラ
- `receipt-command-escpos`: TM-T90KP、TM-m30、RP-F10 のコマンド変換
- `receipt-command-star`: FVP10、TSP650II、mC-Print3 のコマンド変換
- `receipt-receiptline`: ReceiptLine プレビュー変換
- `receipt-engine`: 汎用 DTO、機種解決、コマンド・プレビュー Facade、XCFramework
- `samples/kotlinApp`: Kotlin から汎用 DTO を利用する例
- `samples/iosApp`: `ReceiptPrint.xcframework` を Swift から利用する例
- `packages/receipt_print`: Dart API と Android / iOS の MethodChannel 実装を持つ Flutter プラグイン

制御文字、QR、スタイル継承、Compatibility の補正規則は[入力と補正の契約](doc/receipt_dsl_kmp_architecture.md#8-入力と補正の契約)に記載しています。
実機確認状況は[プリンター確認表](doc/printer-verification.md)を参照してください。

詳しい境界と変換規則は[アーキテクチャ文書](doc/receipt_dsl_kmp_architecture.md)を参照してください。

## ビルドと検証

JDK 17 以降が必要です。iOS の検証には macOS、Xcode、Apple Silicon Simulator toolchain が必要です。

```shell
./gradlew build
./gradlew engineCheck
# Flutter も含めて検証する場合
./gradlew fullCheck
```

`engineCheck` は ABI、JVM / iOS Simulator テスト、プリンターコマンド Fixture、ReceiptLine の整列入力、Kotlin / Swift サンプルを検証します。

固定バージョンの JavaScript レンダラーによる SVG 出力と整列の確認は、Node.js を使って別に実行します。

```shell
npm --prefix tools/receiptline ci --include=dev --ignore-scripts
npm --prefix tools/receiptline run verify
```

Maven publication は次のコマンドで `build/maven-repository` へ生成できます。

```shell
./gradlew publishAllToBuildRepository
```

Swift 向け XCFramework は次のコマンドで `receipt-engine/build/XCFrameworks/debug/ReceiptPrint.xcframework` へ生成されます。

```shell
./gradlew :receipt-engine:assembleReceiptPrintDebugXCFramework
./gradlew swiftSampleCheck
```

### GitHub の必須チェック

`main` へのマージには `engine-and-ios` と `android` の成功が必要です。iOS は自前の MacBook Pro、Android は自前の NAB-9Sで検証します。CI はこのリポジトリの `main` と `ci/**` への push で実行されます。外部 PR を受け取った場合、メンテナーが差分をレビューし、自前環境での実行を判断したうえで、検証対象をこのリポジトリの `ci/**` ブランチに push します。

## Flutter からの利用

Flutter アプリでは `flutter pub add receipt_print` でインストールできます。導入、ネイティブ配布物の生成、対応環境は[Flutter パッケージの日本語 README](packages/receipt_print/README.ja.md)を参照してください。Android と iOS では印刷ツリーから[紙面プレビュー](packages/receipt_print/README.ja.md#紙面プレビュー)を表示できます。Kotlin と Swift からは `ReceiptEngine` を直接使えます。

## 固定バージョンの配布物

```shell
./gradlew bundleFlutterPlugin
```

`packages/receipt_print/engine-version.txt` は KMP と Android 依存の共通バージョンです。ネイティブエンジンの配布物を変更する際は、この値を更新してから ZIP を作り直してください。
コマンドは Dart API、Android Maven 成果物、iOS XCFramework を含む `build/distributions/receipt-print-<version>.zip` を生成します。
GitHub Actions は ZIP を展開して同梱物を確認し、展開したパッケージを使って Flutter サンプルと iOS Channel を検証します。検証に使った ZIP を、コミット SHA を含む成果物名で保存します。
Flutter プラグインの pub.dev パッケージ名は `receipt_print` です。

## ライセンス

Receipt Print の独自コードは [Apache License 2.0](LICENSE) で公開しています。配布 ZIP には本プロジェクトの LICENSE と、同梱する Unicode および ReceiptLine のライセンスを含めています。
