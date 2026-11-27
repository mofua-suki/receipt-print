# Receipt Print

[日本語](https://github.com/mofua-suki/receipt-print/blob/main/README.ja.md) · [pub.dev](https://pub.dev/packages/receipt_print)

Receipt Print is a Kotlin Multiplatform library for turning a caller-defined receipt tree into thermal printer commands or a ReceiptLine preview. The caller maps domain data such as orders, kitchen tickets, payments and reprints to `PrintJobDto`. The engine validates the tree, lays it out in CP932 columns and converts it to the selected output. The caller owns printer connections, transmission, retries and queues.

## Public model

```text
PrintJobDto
└── tickets: List<TicketDto>
    ├── children: PrintNodeListDto  // value class
    │   ├── TextDto
    │   ├── InlineLineDto
    │   ├── TwoColumnDto
    │   ├── RuleDto
    │   ├── QrCodeDto
    │   ├── FeedDto
    │   └── ColumnDto
    │       └── children: PrintNodeListDto
    └── cut: CutModeDto
```

`ReceiptEngine.compile(job, printer)` returns printer-specific `bytes` in a `CompileResponseDto`. `ReceiptEngine.preview(job, printer)` returns `receiptLine` in a `PreviewResponseDto`. Failed conversions return null output. Inspect `diagnostics` for errors, approximations and their positions in the tree.

Set the model, columns per line, compatibility mode and sound permission in `PrinterProfileDto`. For sound on a TM-m30, also indicate that the optional external buzzer is configured. The engine resolves the command set and other capabilities from the model.

## Modules

| Module | Purpose |
| --- | --- |
| `receipt-core` | Immutable AST, Kotlin DSL, validation, CP932 layout and compiler |
| `receipt-command-escpos` | Commands for TM-T90KP, TM-m30 and RP-F10 |
| `receipt-command-star` | Commands for FVP10, TSP650II and mC-Print3 |
| `receipt-receiptline` | ReceiptLine preview conversion |
| `receipt-engine` | Public DTOs, model resolution, command and preview facade, XCFramework |
| `samples/kotlinApp` | Kotlin example using the public DTOs |
| `samples/iosApp` | Swift example using ReceiptPrint.xcframework |
| `packages/receipt_print` | Flutter plugin with Dart API and Android and iOS MethodChannel implementations |

The [architecture document](doc/receipt_dsl_kmp_architecture.md) describes boundaries and conversion rules. Its [input and normalization contract](doc/receipt_dsl_kmp_architecture.md#8-入力と補正の契約) covers control characters, QR codes, style inheritance and compatibility adjustments. The [printer verification matrix](doc/printer-verification.md) records hardware verification status.

## Build and verify

Use JDK 17 or later and install Node.js with npm. Gradle uses `npm ci --include=dev` to install the ReceiptLine development dependency. The engine's JVM rendering tests and the `./gradlew engineCheck` SVG golden check use that installation. iOS verification requires macOS, Xcode and an Apple Silicon simulator toolchain.

```shell
./gradlew build
./gradlew engineCheck
# Include Flutter checks
./gradlew fullCheck
```

`engineCheck` verifies the ABI, JVM and iOS simulator tests, printer command fixtures, ReceiptLine SVG output and Kotlin and Swift samples.

Publish Maven artifacts to `build/maven-repository`:

```shell
./gradlew publishAllToBuildRepository
```

Build the Swift framework at `receipt-engine/build/XCFrameworks/debug/ReceiptPrint.xcframework` and verify the Swift sample:

```shell
./gradlew :receipt-engine:assembleReceiptPrintDebugXCFramework
./gradlew swiftSampleCheck
```

### GitHub checks

The required checks for merging into `main` are `engine-and-ios` on the project's MacBook Pro and `android` on NAB-9S. CI runs on pushes to this repository's `main` and `ci/**` branches. For external contributions, a maintainer reviews the diff before pushing it to a `ci/**` branch for execution on those runners.

## Flutter

Run `flutter pub add receipt_print` in your app. The [Flutter package README](packages/receipt_print/README.md) covers installation, native artifacts and platform requirements. Android and iOS apps can display a [receipt preview](packages/receipt_print/README.md#receipt-preview). Kotlin and Swift apps can use `ReceiptEngine` directly.

## Build a versioned distribution

```shell
./gradlew bundleFlutterPlugin
```

Android and KMP share the version in `packages/receipt_print/engine-version.txt`. Bump it when changing the native engine artifacts and rebuild the ZIP. The task writes the Dart API, Android Maven artifacts and iOS XCFramework to `build/distributions/receipt-print-<version>.zip`. GitHub Actions checks the archive, tests the Flutter example and iOS channel against the extracted package, and stores the verified ZIP under an artifact name containing the commit SHA. The pub.dev package name is `receipt_print`.

## License

Receipt Print is licensed under the [Apache License 2.0](LICENSE). The distribution ZIP includes the project license and the licenses for the bundled Unicode and ReceiptLine components.
