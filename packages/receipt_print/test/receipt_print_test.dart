import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:receipt_print/receipt_print.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('dev.receiptprint/receipt_print');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final engine = ReceiptPrint();
  const job = PrintJobDto(
    tickets: [
      TicketDto(
        children: [
          ColumnDto(
            children: [
              TextDto(value: '日本語', style: TextStyleDto(emphasized: true)),
              TwoColumnDto(
                leading: TextRunDto(value: 'item'),
                trailing: TextRunDto(value: '100'),
              ),
              RuleDto(),
              QrCodeDto(data: 'qr'),
              FeedDto(),
            ],
          ),
        ],
      ),
    ],
    startActions: [JobActionDto.playSound],
  );
  const printer = PrinterProfileDto(model: PrinterModelDto.epsonTmM30);

  test(
    'omitted styles inherit and explicit defaults remain distinguishable',
    () {
      expect(const TextDto(value: 'A').toMap()['style'], isNull);
      expect(const TextRunDto(value: 'A').toMap()['style'], isNull);
      expect(
        const TextDto(value: 'A', style: TextStyleDto()).toMap()['style'],
        const TextStyleDto().toMap(),
      );
      expect(
        const TextRunDto(value: 'A', style: TextStyleDto()).toMap()['style'],
        const TextStyleDto().toMap(),
      );
    },
  );

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test(
    'measurement and local SVG rendering use the existing channel',
    () async {
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        return call.method == 'measureText' ? 6 : '<svg/>';
      });
      expect(await engine.measureText('日本Aｱ'), 6);
      expect(await engine.renderSvg('日本語', columns: 48), '<svg/>');
      expect(calls.map((e) => e.method), ['measureText', 'renderSvg']);
      expect(calls.first.arguments, {'protocolVersion': 1, 'text': '日本Aｱ'});
      expect(calls.last.arguments, {
        'protocolVersion': 1,
        'receiptLine': '日本語',
        'columns': 48,
      });
    },
  );
  test(
    'inline lines keep independently styled runs without adding margins',
    () {
      const node = InlineLineDto(
        runs: [
          TextRunDto(value: '□ ALPHA'),
          TextRunDto(value: ' 1点 ', style: TextStyleDto(inverted: true)),
        ],
      );
      final map = node.toMap();
      expect(map['type'], 'inline');
      expect((map['runs'] as List).last['value'], ' 1点 ');
      expect((map['runs'] as List).last['style']['inverted'], isTrue);
    },
  );
  test(
    'compile sends codec-compatible typed trees and preserves unsigned bytes',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'compile');
        final arguments = call.arguments as Map;
        expect(arguments['protocolVersion'], 1);
        final mappedJob = arguments['job'] as Map;
        expect(mappedJob['startActions'], ['PlaySound']);
        final ticket = (mappedJob['tickets'] as List).single as Map;
        final column = (ticket['children'] as List).single as Map;
        final children = column['children'] as List;
        expect(children.map((value) => (value as Map)['type']), [
          'text',
          'twoColumn',
          'rule',
          'qrCode',
          'feed',
        ]);
        expect(((children.first as Map)['style'] as Map)['emphasized'], isTrue);
        expect(arguments['printer'], printer.toMap());
        return {
          'bytes': Uint8List.fromList([0, 127, 128, 255]),
          'diagnostics': [],
        };
      });
      final response = await engine.compile(job: job, printer: printer);
      expect(response.isSuccess, isTrue);
      expect(response.bytes, [0, 127, 128, 255]);
    },
  );

  test('compile response isolates input and returned command bytes', () {
    final input = Uint8List.fromList([0, 255]);
    final response = CompileResponseDto.fromMap({
      'bytes': input,
      'diagnostics': [],
    });
    input[0] = 42;
    response.bytes![1] = 42;
    expect(response.bytes, [0, 255]);
  });

  test(
    'preview preserves warnings and nullable diagnostic positions',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'preview');
        return {
          'receiptLine': '日本語',
          'diagnostics': [
            {
              'code': 'PREVIEW_APPROXIMATION',
              'severity': 'Warning',
              'message': 'approximation',
              'ticketIndex': null,
              'childIndexes': [],
            },
          ],
        };
      });
      final response = await engine.preview(job: job, printer: printer);
      expect(response.isSuccess, isTrue);
      expect(response.receiptLine, '日本語');
      expect(response.diagnostics.single.ticketIndex, isNull);
      expect(
        response.diagnostics.single.severity,
        DiagnosticSeverityDto.warning,
      );
    },
  );

  test('validation failure preserves the tree path and null output', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => {
        'bytes': null,
        'diagnostics': [
          {
            'code': 'INVALID_VALUE',
            'severity': 'Error',
            'message': 'invalid feed',
            'ticketIndex': 1,
            'childIndexes': [2, 0],
          },
        ],
      },
    );
    final response = await engine.compile(job: job, printer: printer);
    expect(response.isSuccess, isFalse);
    expect(response.bytes, isNull);
    expect(response.diagnostics.single.ticketIndex, 1);
    expect(response.diagnostics.single.childIndexes, [2, 0]);
  });

  test('native errors propagate as PlatformException', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => throw PlatformException(
        code: 'invalid_arguments',
        message: 'invalid request',
      ),
    );
    await expectLater(
      engine.compile(job: job, printer: printer),
      throwsA(isA<PlatformException>()),
    );
  });

  test('malformed null responses fail explicitly', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => null);
    await expectLater(
      engine.compile(job: job, printer: printer),
      throwsA(isA<FormatException>()),
    );
  });
}
