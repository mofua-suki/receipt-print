# Receipt Print

[English](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.md) · [pub.dev](https://pub.dev/packages/receipt_print)

汎用印刷ツリーからサーマルプリンター向けコマンドと ReceiptLine プレビューを生成する Flutter プラグインです。
変換処理は、このリポジトリの Kotlin Multiplatform `ReceiptEngine` を利用します。
プリンターへの接続、送信、再試行は利用側で実装します。

## インストール

Flutter アプリのルートで実行します。

```shell
flutter pub add receipt_print
```

pub.dev からインストールしたパッケージには Android の Maven 成果物と iOS の XCFramework が同梱されています。

- Flutter 3.44.5 以降、Dart 3.12.2 以上 4.0.0 未満（リポジトリの `.tool-versions` は Flutter 3.44.5）
- Android API 24 以降、JDK 17 以降、Kotlin 2.4.10 以降
- iOS 15 以降、arm64 の実機と Apple Silicon Simulator

Android の検証用 example は AGP 9.0.1 と Kotlin 2.4.10 を使用します。
Flutter の iOS 連携は CocoaPods と Swift Package Manager の定義を含みます。
Web、macOS、Windows、Linux と Intel Mac の iOS Simulator は対象外です。
iOS アプリの Runner ターゲットの Minimum Deployments を 15.0 以上に設定してください。
CocoaPods を使うアプリでは、`Podfile` にも `platform :ios, '15.0'` を指定します。

## ソースから利用する場合

リポジトリのルートで実行します。
iOS 向け配布物の生成には macOS と Xcode が必要です。

```shell
./gradlew prepareFlutterPlugin
```

Android だけを使う場合は `prepareFlutterAndroid`、iOS だけを使う場合は `prepareFlutterIos` を実行できます。
生成する Maven リポジトリと XCFramework は Git 管理対象外です。
KMP のコードを変更した場合は、同じタスクで再生成してください。
Maven のバージョンは `engine-version.txt` で固定し、Android と KMP で共有します。
ネイティブエンジンの配布物を変更するときは、このファイルのバージョンを上げて全成果物を再生成してください。
`./gradlew bundleFlutterPlugin` は両 OS の配布物を含む ZIP を `build/distributions` に生成します。
CI は検証したコミットの ZIP を保存します。
Receipt Print の独自コードは Apache License 2.0 で公開しています。配布 ZIP には本プロジェクトの LICENSE と、同梱する Unicode および ReceiptLine のライセンスを含めています。

利用側の `pubspec.yaml` に追加し、`flutter pub get` を実行します。
パスは利用側プロジェクトからの相対パスです。

```yaml
dependencies:
  receipt_print:
    path: ../receipt-print/packages/receipt_print
```

Git 依存として取得する場合も、取得先にネイティブ配布物を準備する必要があります。

## Android のリポジトリ設定

Android の依存解決でプロジェクト側のリポジトリを禁止している場合は、利用側の `settings.gradle.kts` の `dependencyResolutionManagement.repositories` に、このパッケージの `android/maven` を追加してください。
`receipt-kmp` グループの JVM ライブラリをこの場所から解決します。
`FAIL_ON_PROJECT_REPOS` を使う場合は、利用側の `gradle.properties` に `receiptPrintConfigureRepositories=false` も指定し、プラグインによるリポジトリの自動追加を無効にしてください。

## 使用例

```dart
import 'package:receipt_print/receipt_print.dart';

Future<void> compileReceipt() async {
  final receipt = ReceiptPrint();
  const job = PrintJobDto(tickets: [
    TicketDto(children: [
      TextDto(value: '注文 #12', style: TextStyleDto(emphasized: true)),
      TwoColumnDto(
        leading: TextRunDto(value: 'コーヒー'),
        trailing: TextRunDto(value: '2'),
      ),
      RuleDto(),
      QrCodeDto(data: 'https://example.com/order/12'),
      FeedDto(),
    ]),
  ]);
  const printer = PrinterProfileDto(model: PrinterModelDto.epsonTmM30);

  final compiled = await receipt.compile(job: job, printer: printer);
  if (compiled.isSuccess) {
    final bytes = compiled.bytes!; // Uint8List。利用側でプリンターへ送信する。
    print('コマンド長: ${bytes.length}');
  }
  for (final diagnostic in compiled.diagnostics) {
    print('${diagnostic.code}: ${diagnostic.message}');
  }

  final preview = await receipt.preview(job: job, printer: printer);
  print(preview.receiptLine);
}
```

`ColumnDto` でツリーを入れ子にできます。
公開 DTO のフィールドと既定値は KMP に対応し、子ノードの配列には Dart の `List<PrintNodeDto>` を使います。
Epson TM-m30・TM-T90KP、Star FVP10・TSP650II・mC-Print3、Seiko Instruments RP-F10 に対応します。用紙と機種に合う桁数を `columnsPerLine` に指定します。TM-m30 の `optionalExternalBuzzerEnabled` は対応する外付けブザーを設定済みの場合に指定してください。
Dart のモデルで渡す整数は、Kotlin の `Int` と同じ符号付き 32 ビット整数の範囲です。

QR プレビューには印字可能な ASCII を指定します。データの上限は適用される誤り訂正レベルごとに L が 2953、M が 2331、Q が 1663、H が 1273 バイトです。Compatibility では ESC/POS 機が M、Star 機が Q を使います。上限を超えるとプレビューは `INVALID_LAYOUT` を返します。
`CutModeDto.none` のチケット間に空行を入れるには `FeedDto()` を指定します。

コンパイルが成功すると、`bytes` を読むたびに新しい `Uint8List` を取得できます。送信用に書き換えた後も、元のコマンドを再取得できます。

変換が成功すると `isSuccess` が `true` になります。
警告だけの場合は、出力と診断の両方を返します。
検証エラーの場合は `bytes` または `receiptLine` が `null` になり、`diagnostics` の `ticketIndex` と `childIndexes` から該当ノードを特定できます。
プレビューには ReceiptLine の表現上の近似を示す警告が含まれる場合があります。
Channel 引数の不正など、呼び出し自体の失敗は `PlatformException` として通知します。

## 構成

```text
Flutter → Dart API → MethodChannel → Kotlin / Swift → ReceiptEngine
Android ネイティブ → Kotlin → ReceiptEngine
 iOS ネイティブ    → Swift  → ReceiptEngine
```

Dart は入力モデルと結果モデル、および Channel 呼び出しを担当します。
検証、レイアウト、コマンド生成を Dart に重複実装しません。
Android と iOS のネイティブプロジェクトは、既存の KMP 配布物と XCFramework を直接利用できます。

## 検証

Git チェックアウトの `packages/receipt_print` で Dart の解析とテストを実行します。

```shell
flutter pub get
flutter analyze
flutter test
cd example
flutter test
flutter build apk --debug
cd android
./gradlew :receipt_print:testDebugUnitTest
```

iOS Simulator で実際の MethodChannel と KMP を通すテストを実行できます。
Android 端末またはエミュレーターでも、同じテストを実行できます。次のコマンドはリポジトリのルートから実行します。

```shell
cd packages/receipt_print/example
flutter devices
# DEVICE_ID に対象端末の ID を設定します。
flutter test integration_test/plugin_integration_test.dart -d "$DEVICE_ID"
```

iOS 側の DTO 変換と不正引数のテストは、`example` で次のコマンドを実行します。
結合テストの実行後は `--config-only` で通常のアプリを対象にした設定へ戻してから実行します。

```shell
flutter build ios --simulator --debug --config-only
cd ios
xcodebuild test -project Runner.xcodeproj -scheme Runner \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -parallel-testing-enabled NO -only-testing:RunnerTests
```

[サンプルアプリ](https://github.com/mofua-suki/receipt-print/tree/main/packages/receipt_print/example)はコマンド長、ReceiptLine、診断を表示します。`example` ディレクトリで `flutter run` を実行すると起動します。

## 紙面プレビュー

`receipt.measureText(text)` は Android と iOS で互換モードと同じ CP932 の桁数を返します。CP932 で表現できない文字は数えません。
印刷と同じ `job` と `printer` を使い、Android と iOS で次のように紙面を表示できます。

```dart
import 'package:flutter/widgets.dart';
import 'package:receipt_print/receipt_print.dart';

Future<Widget?> buildPreview(PrintJobDto job, PrinterProfileDto printer) async {
  final receipt = ReceiptPrint();
  final preview = await receipt.preview(job: job, printer: printer);
  final text = preview.receiptLine;
  if (text == null) return null;
  final svg = await receipt.renderSvg(text, columns: printer.columnsPerLine);
  return SizedBox(height: 320, child: ReceiptPreviewView(svg: svg));
}
```

SVG は iOS の JavaScriptCore または Android のオフライン描画用 WebView で生成します。ReceiptLine 4.0.1 の `lib/receiptline.js` は改行を LF にそろえ、付属のライセンスとともに同梱しています。
プレビューではローカルのフォントを使います。表示用 WebView のスクリプト、外部リソース、外部への遷移は無効です。

`renderSvg()` は「塚」などのCJK互換漢字を保持し、結合文字列にはNFCを適用します。外部描画器では正規化の違いで字形が変わる場合があります。

`preview()` は印刷設定を適用した後のQRセル幅を、最大8ドットで出力します。1・2ドットはそのまま出力しますが、標準のReceiptLine 4.0.1描画器では最小3ドットになるため、`PREVIEW_APPROXIMATION` を返します。実効幅が8を超える場合は8に丸め、同じ警告を返します。同梱のSVG描画器は出力されたセル幅で描画し、収まらない場合だけ整数ドット単位で縮小します。行列を生成できない場合や1ドットでも収まらない場合は描画エラーになります。印刷には印刷設定の実効幅を使います。

`InlineLineDto` は、各 `TextRunDto` を暗黙の余白なしで並べる整形済みの一行です。
各区間のスタイルと入力済みの余白を保持し、自動で折り返しません。区間に改行は指定できません。
