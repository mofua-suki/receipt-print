# Receipt Print サンプル

[English](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/example/README.md) · [pub.dev](https://pub.dev/packages/receipt_print)

サンプルの伝票を変換し、印刷コマンドのバイト数、ReceiptLine プレビュー、診断を表示します。

## サンプルを起動する

配布 ZIP を使う場合は、このディレクトリで `flutter create --platforms=android,ios .` を実行し、起動用ファイルを生成します。Git から取得した場合は、先にネイティブの配布物を生成します。 [ソースから利用する手順](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.ja.md#ソースから利用する場合)

iOS のファイルを生成した場合は、Runner の最小対応 OS を iOS 15 以上に設定します。CocoaPods を使う場合は Podfile にも `platform :ios, '15.0'` を指定します。Android の最小 SDK は API 24 以上に設定します。 [対応環境](https://github.com/mofua-suki/receipt-print/blob/main/packages/receipt_print/README.ja.md#インストール)

このディレクトリで `flutter pub get` と `flutter run` を実行し、**変換する** を押します。

## サンプルをテストする

`flutter test test/widget_test.dart` でサンプルの変換処理を確認できます。
