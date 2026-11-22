# Receipt Print

[Japanese documentation](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.ja.md)

Receipt Print converts a structured receipt into printer commands or a ReceiptLine preview. It runs the Kotlin Multiplatform receipt engine on Android and iOS. Your app supplies the receipt data and handles printer connections, sending bytes, and retries.

## Install

```shell
flutter pub add receipt_print
```

The pub.dev package includes the Maven artifacts for Android and an XCFramework for iOS.

| Platform | Requirements |
| --- | --- |
| Android | API 24 or later, JDK 17 or later, and Kotlin 2.4.10 or later |
| iOS | iOS 15 or later on arm64 devices or Apple Silicon simulators |

Use Flutter 3.44.5 or later with Dart 3.12.2 or later. For iOS, set the app's minimum deployment target to 15.0. If using CocoaPods, also set `platform :ios, '15.0'` in your Podfile.

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

Both calls can return output alongside warnings. If receipt validation fails, the output is null. Use `ticketIndex` and `childIndexes` to find the affected node. Invalid platform-channel calls can throw a `PlatformException`.

The supported models are Epson TM-m30 and TM-T90KP, Star FVP10, TSP650II and mC-Print3, and Seiko Instruments RP-F10. Set `columnsPerLine` to the width for the chosen printer and paper. The engine uses CP932 column widths. On a TM-m30, set `optionalExternalBuzzerEnabled` only if a compatible external buzzer is configured.

## Receipt preview

`preview()` returns ReceiptLine text on Android and iOS. On iOS, pass that text to `renderSvg()` and display the returned SVG with `ReceiptPreviewView`. Use the same column count as in the printer profile.

```dart
final text = preview.receiptLine;
if (text != null) {
  final svg = await receipt.renderSvg(text, columns: printer.columnsPerLine);
  final view = ReceiptPreviewView(svg: svg);
  // Return view from your widget or insert it in your layout.
}
```

## Run the example or build from source

See the [example](https://github.com/mofua-suki/receipt-print/tree/main/packages/receipt_print/example) for a Flutter app that compiles a receipt and displays its command length, ReceiptLine output, and diagnostics.

When using a Git checkout, run `./gradlew prepareFlutterPlugin` from the repository root to build the native artifacts. iOS builds need macOS and Xcode. Use `prepareFlutterAndroid` or `prepareFlutterIos` to build only one platform. Add `packages/receipt_print` as a Flutter path dependency.

If your Android project uses `FAIL_ON_PROJECT_REPOS`, add the plugin's `android/maven` directory to `dependencyResolutionManagement.repositories` in `settings.gradle.kts`. Set `receiptPrintConfigureRepositories=false` in the app's `gradle.properties` to disable automatic registration.

## License

Receipt Print is licensed under Apache 2.0. The package includes the licenses for the bundled Unicode and ReceiptLine components.
