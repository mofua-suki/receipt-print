import 'dart:typed_data';

/// Distinguishes recoverable warnings from errors that prevent output.
enum DiagnosticSeverityDto { warning, error }

/// A validation error or warning, optionally tied to a node in the receipt.
final class DiagnosticDto {
  DiagnosticDto.fromMap(Map<Object?, Object?> map)
    : code = map['code'] as String,
      severity = switch (map['severity']) {
        'Warning' => DiagnosticSeverityDto.warning,
        'Error' => DiagnosticSeverityDto.error,
        _ => throw const FormatException('Unknown diagnostic severity'),
      },
      message = map['message'] as String,
      ticketIndex = map['ticketIndex'] as int?,
      childIndexes = List<int>.unmodifiable(
        (map['childIndexes'] as List).cast<int>(),
      );

  /// Machine-readable diagnostic identifier.
  final String code;
  final DiagnosticSeverityDto severity;
  final String message;

  /// Zero-based ticket index, or null for diagnostics affecting the job.
  final int? ticketIndex;

  /// Zero-based path to the node within the selected ticket.
  final List<int> childIndexes;
}

List<DiagnosticDto> _diagnostics(Map<Object?, Object?> map) =>
    List<DiagnosticDto>.unmodifiable(
      (map['diagnostics'] as List).map(
        (value) => DiagnosticDto.fromMap(value as Map<Object?, Object?>),
      ),
    );

/// Native printer commands and diagnostics produced by compilation.
final class CompileResponseDto {
  CompileResponseDto.fromMap(Map<Object?, Object?> map)
    : bytes = map['bytes'] as Uint8List?,
      diagnostics = _diagnostics(map);

  /// Command bytes, or null if the job could not be compiled.
  final Uint8List? bytes;
  final List<DiagnosticDto> diagnostics;
  bool get isSuccess =>
      bytes != null &&
      !diagnostics.any(
        (value) => value.severity == DiagnosticSeverityDto.error,
      );
}

/// ReceiptLine output and diagnostics produced by the preview conversion.
final class PreviewResponseDto {
  PreviewResponseDto.fromMap(Map<Object?, Object?> map)
    : receiptLine = map['receiptLine'] as String?,
      diagnostics = _diagnostics(map);

  /// ReceiptLine text, or null if the job could not be converted.
  final String? receiptLine;
  final List<DiagnosticDto> diagnostics;
  bool get isSuccess =>
      receiptLine != null &&
      !diagnostics.any(
        (value) => value.severity == DiagnosticSeverityDto.error,
      );
}
