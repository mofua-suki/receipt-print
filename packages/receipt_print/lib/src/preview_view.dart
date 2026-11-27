import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Renders the generated SVG with native SVG text/filter support and local scrolling.
/// The view does not load scripts or remote resources.
class ReceiptPreviewView extends StatelessWidget {
  /// SVG produced by the receipt renderer.
  final String svg;

  /// Displays [svg] in a scrollable native view on Android or iOS.
  const ReceiptPreviewView({super.key, required this.svg});
  @override
  Widget build(BuildContext context) {
    if (!kIsWeb) {
      final gestures = {
        Factory<OneSequenceGestureRecognizer>(() => EagerGestureRecognizer()),
      };
      final params = {'svg': svg};
      switch (defaultTargetPlatform) {
        case TargetPlatform.android:
          return AndroidView(
            key: ValueKey(svg),
            viewType: 'receipt_print/preview',
            gestureRecognizers: gestures,
            creationParams: params,
            creationParamsCodec: const StandardMessageCodec(),
          );
        case TargetPlatform.iOS:
          return UiKitView(
            key: ValueKey(svg),
            viewType: 'receipt_print/preview',
            gestureRecognizers: gestures,
            creationParams: params,
            creationParamsCodec: const StandardMessageCodec(),
          );
        default:
          break;
      }
    }
    return const Center(
      child: Text('Receipt preview requires Android or iOS.'),
    );
  }
}
