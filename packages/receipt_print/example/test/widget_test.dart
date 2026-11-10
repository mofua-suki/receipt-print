import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:receipt_print_example/main.dart';

void main() {
  testWidgets('converts the sample and renders both results', (tester) async {
    const channel = MethodChannel('dev.receiptprint/receipt_print');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return switch (call.method) {
        'compile' => {
          'bytes': Uint8List.fromList([1, 2, 3]),
          'diagnostics': [],
        },
        'preview' => {'receiptLine': '注文 #12', 'diagnostics': []},
        _ => throw MissingPluginException(call.method),
      };
    });

    await tester.pumpWidget(const ReceiptSampleApp());
    await tester.tap(find.text('変換する'));
    await tester.pumpAndSettle();

    expect(calls, ['compile', 'preview']);
    expect(find.textContaining('3 bytes'), findsOneWidget);
    expect(find.textContaining('注文 #12'), findsOneWidget);
  });
}
