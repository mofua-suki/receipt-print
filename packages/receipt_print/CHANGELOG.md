## 0.1.6

- Ship the example widget test and verify the distributed example after generating its platform files.

## 0.1.5

- Return an independent command buffer on each read of a Flutter compile result.
- Preserve one- and two-dot QR cells in ReceiptLine exports and bundled iOS SVG previews. Keep the preview warning for external renderers.

## 0.1.4

- Preserve CJK compatibility ideographs in iOS SVG text while continuing to compose combining sequences.
- Fit QR modules to the available width in iOS SVGs. ReceiptLine exports and printer QR commands retain their effective cell sizes.

## 0.1.3

- Return `INVALID_LAYOUT` when a preview QR payload exceeds the effective correction level's byte capacity.
- Preserve explicit feeds between uncut tickets in previews without adding an extra blank line.

## 0.1.2

- Align English and Japanese installation, preview and verification examples.

## 0.1.1

- Provide an English package README and retain the Japanese documentation.
- Document the public receipt builders, compiler, previews, and diagnostics.
- Include the Flutter example source in the distribution archive.

## 0.1.0

- Add typed Dart receipt models and compile/preview calls for Android and iOS.
