# Receipt Print example

[日本語](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/example/README.ja.md) · [pub.dev](https://pub.dev/packages/receipt_print)

This example compiles a sample receipt and shows the command size, the ReceiptLine preview, and diagnostics.

## Run the example

If this directory came from the distribution archive, run `flutter create --platforms=android,ios .` here to generate the platform files. If it came from a Git checkout, prepare the native package artifacts first. [Build from source](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.md#run-the-example-or-build-from-source)

For generated iOS files, set the Runner deployment target to iOS 15 or later. If the app uses CocoaPods, set `platform :ios, '15.0'` in its Podfile. Set the Android minimum SDK to API 24 or later. [Platform requirements](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.md#installation)

Run `flutter pub get` and `flutter run` in this directory, then press **変換する**.

## Test the example

Run `flutter test test/widget_test.dart` to check the example's conversion flow.
