/// Input models corresponding to the public Kotlin receipt-engine DTOs.
library;

/// Direction in which the printer outputs a receipt.
enum PrintDirectionDto {
  normal('Normal'),
  reverse('Reverse');

  const PrintDirectionDto(this.wireValue);
  final String wireValue;
}

/// An optional action to perform before printing the tickets.
enum JobActionDto {
  playSound('PlaySound');

  const JobActionDto(this.wireValue);
  final String wireValue;
}

/// Paper-cut behavior after a ticket.
enum CutModeDto {
  none('None'),
  full('Full'),
  partial('Partial'),
  noMargin('NoMargin');

  const CutModeDto(this.wireValue);
  final String wireValue;
}

/// Horizontal alignment within the current printable width.
enum TextAlignDto {
  left('Left'),
  center('Center'),
  right('Right');

  const TextAlignDto(this.wireValue);
  final String wireValue;
}

/// How text wraps when it exceeds the available columns.
enum WrapModeDto {
  none('None'),
  character('Character'),
  wordOrCharacter('WordOrCharacter');

  const WrapModeDto(this.wireValue);
  final String wireValue;
}

/// How to handle a row whose text exceeds the reserved columns.
enum TwoColumnOverflowDto {
  wrapLeadingThenTrailing('WrapLeadingThenTrailing'),
  trailingOnNextLine('TrailingOnNextLine'),

  /// Reserved; returns INVALID_LAYOUT because no clipping policy is implemented.
  clip('Clip'),
  fail('Fail');

  const TwoColumnOverflowDto(this.wireValue);
  final String wireValue;
}

/// QR error correction level requested from the printer.
enum QrErrorCorrectionDto {
  low('Low'),
  medium('Medium'),
  quartile('Quartile'),
  high('High');

  const QrErrorCorrectionDto(this.wireValue);
  final String wireValue;
}

/// A printer model supported by the native receipt engine.
enum PrinterModelDto {
  starFvp10('StarFvp10'),
  epsonTmT90Kp('EpsonTmT90Kp'),
  epsonTmM30('EpsonTmM30'),
  starTsp650II('StarTsp650II'),
  starMcPrint3('StarMcPrint3'),
  siiRpF10('SiiRpF10');

  const PrinterModelDto(this.wireValue);
  final String wireValue;
}

/// Whether the engine may approximate unsupported layout features.
enum CompatibilityModeDto {
  strict('Strict'),
  compatibility('Compatibility');

  const CompatibilityModeDto(this.wireValue);
  final String wireValue;
}

/// Base type for the nodes that make up a receipt.
sealed class PrintNodeDto {
  const PrintNodeDto();

  /// Encodes this node for the native platform channel.
  Map<String, Object?> toMap();
}

/// A print job containing one or more tickets and optional startup actions.
final class PrintJobDto {
  const PrintJobDto({
    required this.tickets,
    this.direction = PrintDirectionDto.normal,
    this.startActions = const [],
  });

  /// Tickets in the order supplied to the engine.
  final List<TicketDto> tickets;

  /// Requested direction for the whole job.
  final PrintDirectionDto direction;

  /// Actions requested before any ticket is printed.
  final List<JobActionDto> startActions;

  Map<String, Object?> toMap() => {
    'tickets': tickets.map((value) => value.toMap()).toList(),
    'direction': direction.wireValue,
    'startActions': startActions.map((value) => value.wireValue).toList(),
  };
}

/// A sequence of receipt nodes followed by a paper-cut request.
final class TicketDto {
  const TicketDto({required this.children, this.cut = CutModeDto.partial});

  final List<PrintNodeDto> children;

  /// Cut request applied after this ticket.
  final CutModeDto cut;

  Map<String, Object?> toMap() => {
    'children': children.map((value) => value.toMap()).toList(),
    'cut': cut.wireValue,
  };
}

/// A vertical group of child nodes with an optional inherited text style.
final class ColumnDto extends PrintNodeDto {
  /// Groups [children] using [style] as the inherited text style, if provided.
  const ColumnDto({required this.children, this.style});

  final List<PrintNodeDto> children;
  final TextStyleDto? style;

  @override
  Map<String, Object?> toMap() => {
    'type': 'column',
    'children': children.map((value) => value.toMap()).toList(),
    'style': style?.toMap(),
  };
}

/// A block of styled text with explicit wrapping and newline behavior.
final class TextDto extends PrintNodeDto {
  const TextDto({
    required this.value,
    this.style,
    this.wrap = WrapModeDto.wordOrCharacter,
    this.preserveNewLines = true,
  });

  final String value;
  final TextStyleDto? style;

  /// Wrap policy used when text exceeds the printable width.
  final WrapModeDto wrap;

  /// Whether explicit newlines in [value] are preserved.
  final bool preserveNewLines;

  @override
  Map<String, Object?> toMap() => {
    'type': 'text',
    'value': value,
    'style': style?.toMap(),
    'wrap': wrap.wireValue,
    'preserveNewLines': preserveNewLines,
  };
}

/// A row with leading text and an optional trailing value, such as a price.
final class TwoColumnDto extends PrintNodeDto {
  const TwoColumnDto({
    required this.leading,
    this.trailing,
    this.widthColumns,
    this.leadingReservedColumns,
    this.trailingReservedColumns,
    this.overflow = TwoColumnOverflowDto.wrapLeadingThenTrailing,
  });

  /// The text run at the leading edge of the row.
  final TextRunDto leading;

  /// Optional text run at the trailing edge of the row.
  final TextRunDto? trailing;
  final int? widthColumns;
  final int? leadingReservedColumns;
  final int? trailingReservedColumns;

  /// Policy applied when the two text runs do not fit on one line.
  final TwoColumnOverflowDto overflow;

  @override
  Map<String, Object?> toMap() => {
    'type': 'twoColumn',
    'leading': leading.toMap(),
    'trailing': trailing?.toMap(),
    'widthColumns': widthColumns,
    'leadingReservedColumns': leadingReservedColumns,
    'trailingReservedColumns': trailingReservedColumns,
    'overflow': overflow.wireValue,
  };
}

/// A horizontal rule spanning the printable width.
final class RuleDto extends PrintNodeDto {
  const RuleDto({this.thickness = 1});

  final int thickness;

  @override
  Map<String, Object?> toMap() => {'type': 'rule', 'thickness': thickness};
}

/// A QR code with configurable size, error correction, and alignment.
final class QrCodeDto extends PrintNodeDto {
  const QrCodeDto({
    required this.data,
    this.cellSize = 6,
    this.errorCorrection = QrErrorCorrectionDto.medium,
    this.align = TextAlignDto.center,
  });

  final String data;
  final int cellSize;
  final QrErrorCorrectionDto errorCorrection;
  final TextAlignDto align;

  @override
  Map<String, Object?> toMap() => {
    'type': 'qrCode',
    'data': data,
    'cellSize': cellSize,
    'errorCorrection': errorCorrection.wireValue,
    'align': align.wireValue,
  };
}

/// Requests [lines] blank lines of paper feed.
final class FeedDto extends PrintNodeDto {
  const FeedDto({this.lines = 1});

  final int lines;

  @override
  Map<String, Object?> toMap() => {'type': 'feed', 'lines': lines};
}

/// A fragment of text with an optional style override.
final class TextRunDto {
  const TextRunDto({required this.value, this.style});

  final String value;
  final TextStyleDto? style;

  Map<String, Object?> toMap() => {'value': value, 'style': style?.toMap()};
}

/// Text scaling, emphasis, inversion, and alignment for a node or run.
final class TextStyleDto {
  const TextStyleDto({
    this.widthScale = 1,
    this.heightScale = 1,
    this.emphasized = false,
    this.inverted = false,
    this.align = TextAlignDto.left,
  });

  final int widthScale;
  final int heightScale;
  final bool emphasized;
  final bool inverted;
  final TextAlignDto align;

  Map<String, Object?> toMap() => {
    'widthScale': widthScale,
    'heightScale': heightScale,
    'emphasized': emphasized,
    'inverted': inverted,
    'align': align.wireValue,
  };
}

/// Selects the printer and sets layout and compatibility options for a job.
final class PrinterProfileDto {
  const PrinterProfileDto({
    required this.model,
    this.columnsPerLine = 48,
    this.compatibilityMode = CompatibilityModeDto.strict,
    this.soundEnabled = true,
    this.optionalExternalBuzzerEnabled = false,
  });

  final PrinterModelDto model;

  /// Printable width in CP932 columns for the configured paper and printer.
  final int columnsPerLine;

  /// Determines whether the engine may substitute unsupported features.
  final CompatibilityModeDto compatibilityMode;
  final bool soundEnabled;

  /// Whether the optional external buzzer is configured on a TM-m30.
  final bool optionalExternalBuzzerEnabled;

  Map<String, Object?> toMap() => {
    'model': model.wireValue,
    'columnsPerLine': columnsPerLine,
    'compatibilityMode': compatibilityMode.wireValue,
    'soundEnabled': soundEnabled,
    'optionalExternalBuzzerEnabled': optionalExternalBuzzerEnabled,
  };
}

/// Preformatted inline text with no implicit padding around inverted runs.
final class InlineLineDto extends PrintNodeDto {
  const InlineLineDto({required this.runs, this.align = TextAlignDto.left});
  final List<TextRunDto> runs;
  final TextAlignDto align;
  @override
  Map<String, Object?> toMap() => {
    'type': 'inline',
    'runs': runs.map((e) => e.toMap()).toList(),
    'align': align.wireValue,
  };
}
