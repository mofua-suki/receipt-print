import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:receipt_print/receipt_print.dart';
import 'package:receipt_print_example/main.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  final engine = ReceiptPrint();

  testWidgets('all node kinds cross the real native channel', (tester) async {
    final compiled = await engine.compile(
      job: sampleJob,
      printer: samplePrinter,
    );
    final preview = await engine.preview(
      job: sampleJob,
      printer: samplePrinter,
    );
    expect(
      compiled.isSuccess,
      isTrue,
      reason: compiled.diagnostics.map((d) => d.message).join(),
    );
    expect(compiled.bytes, isNotEmpty);
    expect(compiled.bytes!.any((byte) => byte > 127), isTrue);
    expect(preview.isSuccess, isTrue);
    expect(preview.receiptLine, contains('注文 #12'));
    expect(preview.receiptLine, contains('コーヒー${' ' * 39}2'));
    expect(preview.receiptLine, contains('{code:'));
  });

  testWidgets('native measurement and paper preview keep inline runs', (
    tester,
  ) async {
    expect(await engine.measureText('日本Aｱ😀'), 6);
    const job = PrintJobDto(
      tickets: [
        TicketDto(
          children: [
            InlineLineDto(
              runs: [
                TextRunDto(value: '日本 <script>'),
                TextRunDto(value: ' 1点 ', style: TextStyleDto(inverted: true)),
              ],
            ),
            QrCodeDto(data: 'order-42'),
          ],
          cut: CutModeDto.none,
        ),
      ],
    );
    final compiled = await engine.compile(job: job, printer: samplePrinter);
    expect(compiled.isSuccess, isTrue);
    final preview = await engine.preview(job: job, printer: samplePrinter);
    expect(preview.isSuccess, isTrue);
    final receiptLine = preview.receiptLine!;
    expect(receiptLine, contains('日本 <script>'));
    expect(receiptLine, contains('`\\x201点\\x20'));
    expect(receiptLine, contains('{code:order-42;'));

    if (defaultTargetPlatform == TargetPlatform.android) {
      await expectLater(
        engine.renderSvg(receiptLine, columns: samplePrinter.columnsPerLine),
        throwsA(
          isA<PlatformException>().having(
            (error) => error.code,
            'code',
            'unsupported_platform',
          ),
        ),
      );
      return;
    }
    final svg = await engine.renderSvg(
      receiptLine,
      columns: samplePrinter.columnsPerLine,
    );
    expect(svg, contains('<svg'));
    expect(svg, contains('lang="ja"'));
    expect(svg, contains('<path'));
    expect(svg, isNot(contains('<script>')));
    expect(svg, isNot(contains('@import')));
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: SizedBox(height: 320, child: ReceiptPreviewView(svg: svg)),
      ),
    );
    expect(find.byType(UiKitView), findsOneWidget);
  });

  testWidgets('name forms and QR cell sizes survive preview export', (
    tester,
  ) async {
    const printer = PrinterProfileDto(
      model: PrinterModelDto.epsonTmM30,
      columnsPerLine: 32,
    );
    final data = 'A' * 300;
    final job = PrintJobDto(
      tickets: [
        TicketDto(
          children: [
            QrCodeDto(data: data),
            const TextDto(value: '塚田'),
          ],
          cut: CutModeDto.none,
        ),
      ],
    );
    final printed = await engine.compile(job: job, printer: printer);
    final hex = printed.bytes!.map((byte) {
      return byte.toRadixString(16).padLeft(2, '0');
    }).join();
    expect(hex, contains('1d286b0300314306')); // ESC/POS cell size 6.
    expect(hex, contains('fa9c9363')); // Engine CP932 mapping for 塚田.
    final preview = await engine.preview(job: job, printer: printer);
    final text = preview.receiptLine!;
    expect(text, contains('| {code:$data; option:qrcode,6,m} |'));
    expect(text, contains('塚田'));

    if (defaultTargetPlatform != TargetPlatform.iOS) return;
    final svg = await engine.renderSvg(text, columns: 32);
    expect(svg, contains('>塚</tspan>'));
    expect(svg, contains('>田</tspan>'));
    // 69 modules at 5 dots occupy 345 of 384 dots, centered at x=19.
    expect(svg, contains('translate(19,0)"><path d="M0,0l5,0'));
    final wide = await engine.renderSvg(text, columns: 48);
    expect(wide, contains('translate(81,0)"><path d="M0,0l6,0'));

    final combined = await engine.renderSvg('塚か\u3099e\u0301 |', columns: 32);
    expect(combined, contains('>塚</tspan>'));
    expect(combined, contains('>が</tspan>'));
    expect(combined, contains('>é</tspan>'));
    await expectLater(
      engine.renderSvg(text, columns: 1),
      throwsA(isA<PlatformException>()),
    );
    final oversized = 'A' * 2332;
    await expectLater(
      engine.renderSvg('{code:$oversized; option:qrcode,6,m}', columns: 48),
      throwsA(isA<PlatformException>()),
    );
  });

  testWidgets('small QR cells survive compilation, export and iOS rendering', (
    tester,
  ) async {
    const printer = PrinterProfileDto(model: PrinterModelDto.epsonTmM30);
    for (final cell in [1, 2]) {
      final job = PrintJobDto(
        tickets: [
          TicketDto(
            children: [QrCodeDto(data: 'A', cellSize: cell)],
            cut: CutModeDto.none,
          ),
        ],
      );
      final printed = await engine.compile(job: job, printer: printer);
      final hex = printed.bytes!
          .map((byte) => byte.toRadixString(16).padLeft(2, '0'))
          .join();
      expect(hex, contains('1d286b030031430$cell'));
      final preview = await engine.preview(job: job, printer: printer);
      final text = preview.receiptLine!;
      expect(text, contains('option:qrcode,$cell,m'));
      expect(
        preview.diagnostics.map((d) => d.code),
        contains('PREVIEW_APPROXIMATION'),
      );
      if (defaultTargetPlatform == TargetPlatform.iOS) {
        final svg = await engine.renderSvg(text, columns: 48);
        expect(svg, contains('<path d="M0,0l$cell,0 0,$cell'));
      }
    }
  });

  testWidgets('the native channel preserves all six printer models', (
    tester,
  ) async {
    const job = PrintJobDto(
      startActions: [JobActionDto.playSound],
      tickets: [
        TicketDto(
          children: [TextDto(value: 'A')],
          cut: CutModeDto.noMargin,
        ),
      ],
    );
    // Session, sound, style reset, text, and cut from fixtures/commands.
    // The TM-T90KP and RP-F10 share sound bytes; their no-margin cuts differ.
    const escPosInit = '1b401b21001b52081c261c2e001c4301';
    const escPosText = '1b61001d21001b45001d4200410a';
    const starTextAndCut = '1b1d61001b6900001b461b35410a1b6402';
    const expected = {
      PrinterModelDto.epsonTmT90Kp:
          '${escPosInit}1014030300010100${escPosText}1d56620a0a',
      PrinterModelDto.epsonTmM30:
          '${escPosInit}1b28410300610101${escPosText}1d564200',
      PrinterModelDto.siiRpF10:
          '${escPosInit}1014030300010100${escPosText}1d564200',
      PrinterModelDto.starFvp10:
          '1b401b7300001b1d734f000103010000000000$starTextAndCut',
      PrinterModelDto.starTsp650II:
          '1b401b7300001b1d19110132001b1d1912010100$starTextAndCut',
      PrinterModelDto.starMcPrint3: '1b401b401b73000007$starTextAndCut',
    };
    expect(expected.keys, unorderedEquals(PrinterModelDto.values));
    expect(expected.values.toSet(), hasLength(PrinterModelDto.values.length));
    for (final model in PrinterModelDto.values) {
      final response = await engine.compile(
        job: job,
        printer: PrinterProfileDto(
          model: model,
          compatibilityMode: CompatibilityModeDto.compatibility,
          optionalExternalBuzzerEnabled: model == PrinterModelDto.epsonTmM30,
        ),
      );
      expect(response.isSuccess, isTrue, reason: model.name);
      final bytes = response.bytes!.map((byte) {
        return byte.toRadixString(16).padLeft(2, '0');
      }).join();
      expect(bytes, expected[model], reason: model.name);
    }
  });

  testWidgets('nested validation errors preserve their positions', (
    tester,
  ) async {
    const job = PrintJobDto(
      tickets: [
        TicketDto(
          children: [
            TextDto(value: 'valid'),
            ColumnDto(children: [FeedDto(lines: 0)]),
          ],
        ),
      ],
    );
    final compiled = await engine.compile(job: job, printer: samplePrinter);
    final preview = await engine.preview(job: job, printer: samplePrinter);
    expect(compiled.isSuccess, isFalse);
    expect(compiled.bytes, isNull);
    expect(preview.receiptLine, isNull);
    for (final diagnostic in [
      compiled.diagnostics.single,
      preview.diagnostics.single,
    ]) {
      expect(diagnostic.severity, DiagnosticSeverityDto.error);
      expect(diagnostic.ticketIndex, 0);
      expect(diagnostic.childIndexes, [1, 0]);
    }
  });

  testWidgets('start actions and printer flags reach the engine', (
    tester,
  ) async {
    const job = PrintJobDto(
      tickets: [
        TicketDto(
          children: [TextDto(value: 'sound')],
          cut: CutModeDto.none,
        ),
      ],
      startActions: [JobActionDto.playSound],
    );
    final unconfigured = await engine.compile(job: job, printer: samplePrinter);
    expect(unconfigured.isSuccess, isFalse);
    final configured = await engine.compile(
      job: job,
      printer: const PrinterProfileDto(
        model: PrinterModelDto.epsonTmM30,
        optionalExternalBuzzerEnabled: true,
      ),
    );
    expect(configured.isSuccess, isTrue);
    final disabled = await engine.compile(
      job: job,
      printer: const PrinterProfileDto(
        model: PrinterModelDto.epsonTmM30,
        soundEnabled: false,
      ),
    );
    expect(disabled.isSuccess, isTrue);
    expect(disabled.diagnostics.single.severity, DiagnosticSeverityDto.warning);
  });

  testWidgets('unsupported glyphs are returned as diagnostics', (tester) async {
    final response = await engine.compile(
      job: const PrintJobDto(
        tickets: [
          TicketDto(
            children: [TextDto(value: 'A🙂B')],
            cut: CutModeDto.none,
          ),
        ],
      ),
      printer: samplePrinter,
    );
    expect(response.bytes, isNull);
    expect(response.diagnostics.single.code, 'UNSUPPORTED_GLYPH');
  });

  testWidgets('native output agrees on exact bytes and normalized preview', (
    tester,
  ) async {
    const input = PrintJobDto(
      tickets: [
        TicketDto(children: [TextDto(value: 'A')]),
      ],
    );
    const printer = PrinterProfileDto(model: PrinterModelDto.epsonTmM30);
    final output = await engine.compile(job: input, printer: printer);
    expect(output.diagnostics, isEmpty);
    expect(output.bytes, [
      27,
      64,
      27,
      33,
      0,
      27,
      82,
      8,
      28,
      38,
      28,
      46,
      0,
      28,
      67,
      1,
      27,
      97,
      0,
      29,
      33,
      0,
      27,
      69,
      0,
      29,
      66,
      0,
      65,
      10,
      29,
      86,
      1,
    ]);
    final preview = await engine.preview(job: input, printer: printer);
    expect(preview.receiptLine, '{text:nowrap}\nA |\n=\n{text:wrap}');
    const inherited = PrintJobDto(
      tickets: [
        TicketDto(
          children: [
            ColumnDto(
              style: TextStyleDto(widthScale: 2),
              children: [
                TextDto(value: 'AB'),
                TextDto(value: 'CD', style: TextStyleDto()),
              ],
            ),
          ],
          cut: CutModeDto.none,
        ),
      ],
    );
    final styled = await engine.preview(job: inherited, printer: printer);
    expect(styled.receiptLine, '{text:nowrap}\n^AB |\nCD |\n{text:wrap}');
    for (final node in <PrintNodeDto>[
      const TextDto(value: 'A\u001b@B'),
      const TwoColumnDto(leading: TextRunDto(value: 'A\nB')),
      const QrCodeDto(data: 'A🙂B'),
    ]) {
      final job = PrintJobDto(
        tickets: [
          TicketDto(children: [node]),
        ],
      );
      expect((await engine.compile(job: job, printer: printer)).bytes, isNull);
      expect(
        (await engine.preview(job: job, printer: printer)).receiptLine,
        isNull,
      );
    }
  });

  testWidgets('malformed requests and unknown methods fail explicitly', (
    tester,
  ) async {
    const channel = MethodChannel('dev.receiptprint/receipt_print');
    await expectLater(
      channel.invokeMethod<void>('compile', {'protocolVersion': 99}),
      throwsA(
        isA<PlatformException>().having(
          (e) => e.code,
          'code',
          'invalid_arguments',
        ),
      ),
    );
    await expectLater(
      channel.invokeMethod<void>('unknown'),
      throwsA(isA<MissingPluginException>()),
    );
  });
}
