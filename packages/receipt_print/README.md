# Receipt Print

[日本語](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.ja.md) · [pub.dev](https://pub.dev/packages/receipt_print)

Receipt Print converts a structured receipt into printer commands or a ReceiptLine preview. It runs the Kotlin Multiplatform receipt engine on Android and iOS. Your app supplies the receipt data and handles printer connections, sending bytes, and retries.

## Installation

```shell
flutter pub add receipt_print
```

The pub.dev package includes the Maven artifacts for Android and an XCFramework for iOS.

| Platform | Requirements |
| --- | --- |
| Android | API 24 or later, JDK 17 or later, and Kotlin 2.4.10 or later |
| iOS | iOS 15 or later on arm64 devices or Apple Silicon simulators |

Use Flutter 3.44.5 or later with Dart `>=3.12.2 <4.0.0`. For iOS, set the app's minimum deployment target to 15.0. If using CocoaPods, also set `platform :ios, '15.0'` in your Podfile.

The example uses Flutter 3.44.5, AGP 9.0.1 and Kotlin 2.4.10. iOS integration supports CocoaPods and Swift Package Manager. Web, desktop and Intel iOS simulators are unsupported.

## Generate commands and a preview

```dart
import 'package:receipt_print/receipt_print.dart';

Future<void> compileReceipt() async {
  final receipt = ReceiptPrint();
  const job = PrintJobDto(
    tickets: [
      TicketDto(children: [
        TextDto(value: 'Order #12', style: TextStyleDto(emphasized: true)),
        TwoColumnDto(
          leading: TextRunDto(value: 'Coffee'),
          trailing: TextRunDto(value: '2'),
        ),
        RuleDto(),
        QrCodeDto(data: 'https://example.com/order/12'),
        FeedDto(),
      ]),
    ],
  );
  const printer = PrinterProfileDto(model: PrinterModelDto.epsonTmM30);

  final result = await receipt.compile(job: job, printer: printer);
  if (result.isSuccess) {
    final bytes = result.bytes!;
    print('Command length: ${bytes.length} bytes');
  }
  for (final diagnostic in result.diagnostics) {
    print('${diagnostic.code}: ${diagnostic.message}');
  }

  final preview = await receipt.preview(job: job, printer: printer);
  print(preview.receiptLine);
}
```

Use `ColumnDto` to nest nodes. Child lists use Dart's `List<PrintNodeDto>`. DTO fields and defaults correspond to KMP, including the signed 32-bit range for integer inputs. `InlineLineDto` joins styled runs on one preformatted line. It preserves explicit spacing, adds no padding, does not wrap and disallows newlines within runs.

For a QR preview, use printable ASCII. The maximum payload in bytes depends on the effective error correction level (L 2953, M 2331, Q 1663, H 1273). Compatibility mode uses M for ESC/POS models and Q for Star models. An oversized preview returns `INVALID_LAYOUT`.
Use `FeedDto()` for a blank line between tickets with `CutModeDto.none`.

Each read of a successful compile result’s `bytes` returns a fresh `Uint8List`. You can modify it before sending and read the original commands again.

Both calls can return output alongside warnings. If receipt validation fails, the output is null. Use `ticketIndex` and `childIndexes` to find the affected node. Invalid platform-channel calls can throw a `PlatformException`.

The supported models are Epson TM-m30 and TM-T90KP, Star FVP10, TSP650II and mC-Print3, and Seiko Instruments RP-F10. Set `columnsPerLine` to the width for the chosen printer and paper. The engine uses CP932 column widths. On a TM-m30, set `optionalExternalBuzzerEnabled` only if a compatible external buzzer is configured.

The Dart API sends models over a MethodChannel to Kotlin or Swift. The native engine handles validation, layout and conversion. Native apps can use the KMP artifacts or XCFramework directly.

## Receipt preview

`receipt.measureText(text)` measures CP932 column widths on both platforms, skipping unencodable characters. `preview()` returns ReceiptLine text on Android and iOS. On either platform, pass that text to `renderSvg()` and display the returned SVG with `ReceiptPreviewView`. Use the same column count as in the printer profile.

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

Both platforms render with the bundled ReceiptLine 4.0.1 script. iOS uses JavaScriptCore and Android uses a separate offline WebView. The visible preview uses local fonts and disables scripts, external resources and navigation.

`renderSvg()` preserves CJK compatibility ideographs such as 塚 while composing combining sequences. External renderers may normalize text differently.

`preview()` exports the effective QR cell size after printer settings are applied, up to 8 dots. At 1 or 2 dots it preserves the size and reports `PREVIEW_APPROXIMATION` because standard ReceiptLine 4.0.1 renderers use a minimum of 3. Effective sizes above 8 are exported as 8 with the same warning. The bundled SVG renderer uses the exported size, reducing it only as needed to fit a whole number of dots per module. Rendering fails if the matrix cannot be generated or a one-dot module cannot fit. Printing uses the printer’s effective size.

## Run the example or build from source

See the [example](https://github.com/mofua-suki/receipt-print/tree/main/packages/receipt_print/example) for a Flutter app that compiles a receipt and displays its command length, ReceiptLine output, and diagnostics.

When using a Git checkout, run `./gradlew prepareFlutterPlugin` from the repository root to build the native artifacts. iOS builds need macOS and Xcode. Use `prepareFlutterAndroid` or `prepareFlutterIos` to build only one platform. The generated Maven repository and XCFramework are excluded from Git. Rebuild them after changing KMP code. Android and KMP share the version in `engine-version.txt`. Bump it when changing native engine artifacts. Use `./gradlew bundleFlutterPlugin` for a ZIP in `build/distributions`. CI retains the ZIP for the verified commit.

Add the path relative to the consuming app's `pubspec.yaml`, then run `flutter pub get`. Git dependencies also require native artifacts in the checkout.

```yaml
dependencies:
  receipt_print:
    path: ../receipt-print/packages/receipt_print
```

If your Android project uses `FAIL_ON_PROJECT_REPOS`, add the plugin's `android/maven` directory to `dependencyResolutionManagement.repositories` in `settings.gradle.kts`. Set `receiptPrintConfigureRepositories=false` in the app's `gradle.properties` to disable automatic registration. This repository supplies the `receipt-kmp` group.

## Verify a source checkout

Prepare the native artifacts, then run the package and example checks from `packages/receipt_print`.

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

Run the MethodChannel integration test from the repository root on an iOS simulator, Android device or Android emulator.

```shell
cd packages/receipt_print/example
flutter devices
# Set DEVICE_ID to the device identifier shown above.
flutter test integration_test/plugin_integration_test.dart -d "$DEVICE_ID"
```

From the example directory, restore the normal app configuration after the integration test and run the iOS DTO and invalid-argument tests.

```shell
flutter build ios --simulator --debug --config-only
cd ios
xcodebuild test -project Runner.xcodeproj -scheme Runner \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -parallel-testing-enabled NO -only-testing:RunnerTests
```

Run `flutter run` from the example directory to launch the app.

## License

Receipt Print is licensed under Apache 2.0. The package includes the licenses for the bundled Unicode and ReceiptLine components.
