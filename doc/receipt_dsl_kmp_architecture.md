# receipt-print アーキテクチャ

## 1. 責務

`receipt-print` は、汎用印刷ツリーを検証・レイアウトし、次のどちらかへ決定的に変換するライブラリである。

- 対応サーマルプリンターへ渡すコマンドバイト列
- 画面表示や Golden 検証に使う ReceiptLine

業務データから印刷ツリーを作る処理は呼び出し元が担う。このライブラリは注文伝票やキッチン伝票といった帳票種別、商品、注文イベント、店舗設定を知らない。また、接続、送信、状態監視、再試行、キュー、履歴、再印刷、送信先の振り分けも対象外とする。

```text
呼び出し元の業務データ
        │ 呼び出し元が変換
        ▼
    PrintJobDto
        │
        ├── ReceiptEngine.preview ──→ ReceiptLine + DiagnosticDto
        │
        └── ReceiptEngine.compile ──→ Printer Command ByteArray + DiagnosticDto
                                      │
                                      └── 呼び出し元が送信
```

## 2. 公開構文木

公開境界は特定の帳票形式に依存しない。

```kotlin
PrintJobDto(
    tickets = listOf(
        TicketDto(
            children = PrintNodeListDto(
                listOf(
                    TextDto("title"),
                    TwoColumnDto(TextRunDto("item"), TextRunDto("100")),
                    ColumnDto(
                        PrintNodeListDto(
                            listOf(RuleDto(), QrCodeDto("payload")),
                        ),
                    ),
                ),
            ),
        ),
    ),
)
```

`PrintNodeListDto` は `List<PrintNodeDto>` を直接公開せずに意味のある境界として扱う value class である。`TicketDto` と `ColumnDto` の子要素は常にこの型で受け取る。

`PrintNodeDto` は次のノードを持つ。

- `TextDto`: 文字列、スタイル、折り返し、新行保持
- `InlineLineDto`: 暗黙の余白を追加しない、複数スタイルの整形済み1行
- `TwoColumnDto`: 左右の文字列と予約幅、溢れ方針
- `RuleDto`: 罫線
- `QrCodeDto`: データ、セルサイズ、誤り訂正、配置
- `FeedDto`: 紙送り
- `ColumnDto`: スタイルを継承できる再帰コンテナ

`TicketDto` は同じ印刷ジョブ内でカット単位を表す。`header`、`body`、`footer` のような意味は持たず、必要なら呼び出し元が `ColumnDto` の並びとして表現する。

## 3. プリンタープロファイル

公開 `PrinterProfileDto` は次の入力だけを受ける。

- `model`: 対応する 6 機種の列挙値
- `columnsPerLine`: 論理桁数
- `compatibilityMode`: `Strict` または `Compatibility`
- `soundEnabled`: 開始アクションのサウンドを許可するか
- `optionalExternalBuzzerEnabled`: TM-m30 の外付けブザーを設定済みか

コマンドセット、対応カット、最大文字倍率、QR 対応、反転方式、サウンド方式などの capability は `model` と外付けブザー設定から内部で解決する。呼び出し元が矛盾した capability を組み立てることはできない。TM-m30 の外付けブザー設定は既定で無効とし、有効な場合だけ `PlaySound` をブザー専用コマンドへ変換する。

`Strict` は表現できない指定をエラーにする標準モードである。`Compatibility` は古いプリンターコマンドの制約に合わせ、丸めや代替を警告付きで許可するための明示的モードである。

## 4. 変換パイプライン

```text
Interop DTO
  → immutable core PrintJob
  → profile validation
  → CP932 encoding and logical-column layout
  → compatibility normalization (effective text, style, QR options and cut)
  → ESC/POS / Star command encoder or ReceiptLine exporter
```

プレビューは印刷と同じ `LaidOutJob` の実効文字列、スタイル、折り返し、二列のパディング、QR 設定、カットを ReceiptLine へ変換する。
元の AST の再解釈は行わない。
ReceiptLine が正確に表現できない反転印刷、カット形状、非対称倍率、QR セルサイズなどは `PREVIEW_APPROXIMATION` 警告を返す。

コマンド変換にエラーがあれば `bytes` は `null`、プレビュー変換にエラーがあれば `receiptLine` は `null` になる。警告だけなら値と警告の両方を返す。`DiagnosticDto` は `ticketIndex` と再帰的な `childIndexes` を持ち、呼び出し元が問題のノードを特定できる。

## 5. 内部モジュール境界

```text
receipt-engine
├── receipt-core
├── receipt-command-escpos ──→ receipt-core
├── receipt-command-star ────→ receipt-core
└── receipt-receiptline ─────→ receipt-core
```

`receipt-engine` だけが機種からエンコーダを選択し、DTO と core AST を相互に隔離する。個別コマンドモジュールと ReceiptLine モジュールは業務語彙を持たない。

## 6. 不変条件

- 同じ `PrintJobDto` と `PrinterProfileDto` は同じ結果を返す。
- 入力 DTO から変換した core AST は不変である。
- 返却する `ByteArray` は防御的コピーとし、呼び出し側の変更が後続取得値へ影響しない。
- 文字幅は CP932 の実エンコード結果に基づいて計算する。
- 全 Diagnostic は安定した code、severity、message、可能ならノード位置を持つ。
- プリンターコマンド Fixture は外部リポジトリや外部ランタイムなしで検証できる。

## 7. 配布境界

Kotlin 利用者には各 KMP publication を提供する。Swift 利用者には `receipt-engine` が静的 `ReceiptPrint.xcframework` を提供する。どちらの配布物にも TCP クライアントや業務帳票モデルは含めない。

Flutter 利用者には `packages/receipt_print` を提供する。
このパッケージの Dart API は印刷ツリーとプリンタープロファイルを MethodChannel に渡す。
Android 側は JVM 配布物、iOS 側は同じ `ReceiptPrint.xcframework` の DTO を構築して `ReceiptEngine` を呼び出す。
Kotlin と Swift の直接利用に Flutter は必要ない。

```text
Flutter → Dart API → MethodChannel → Kotlin / Swift のアダプター → ReceiptEngine
Android → Kotlin API → ReceiptEngine
iOS     → Swift API  → ReceiptEngine
```

Channel は `dev.receiptprint/receipt_print`、メソッドは `compile` と `preview` とする。
引数には `protocolVersion: 1`、`job`、`printer` を渡す。
印刷ノードは `type` で識別し、列挙値には Kotlin の列挙名を使う。
Dart の `PrintNodeListDto` 相当は `List<PrintNodeDto>` として公開する。
Kotlin の `Int` に対応する入力は符号付き 32 ビット整数の範囲とする。

戻り値は `compile` が `bytes` と `diagnostics`、`preview` が `receiptLine` と `diagnostics` を持つ Map とする。
バイト列は StandardMessageCodec のバイト配列として返し、Dart では `Uint8List` として受け取る。
検証エラーは KMP の診断として返し、不正な Channel 引数は `invalid_arguments` の `PlatformException` とする。
変換処理は Android と iOS のそれぞれで直列のバックグラウンドキューに載せる。

ネイティブ配布物の配置と生成手順は[Flutter パッケージの README](../packages/receipt_print/README.md)を参照する。

## 8. 入力と補正の契約

- `TextDto` / `TextBlock` は CR、LF、CRLF を改行として扱う。`preserveNewLines=false` では空白へ変換する。それ以外の C0、DEL、C1 制御文字（タブ、ESC、GS、FS を含む）は、両モードで `INVALID_LAYOUT` とする。
- `TextRunDto` / `TextRun` は改行も含めて C0、DEL、C1 制御文字を拒否する。行や紙送りには対応するノードを使う。
- QR データはテキスト命令と分けて長さ付きペイロードとして扱う。非対応文字は Compatibility でも `UNSUPPORTED_GLYPH` エラーにし、欠落させない。QR 内の制御文字は印刷データとして保持する。
- ReceiptLine の SVG QR は UTF-8、印刷 QR は CP932 である。同一のバイト列を表現できない QR と制御文字入り QR は、プレビューを `INVALID_LAYOUT` とする。日本語 QR の印刷は可能だが、ReceiptLine プレビューは返さない。
- `WrapMode.None` の行が指定幅を超えた場合、ReceiptLine は内容を切り捨てるためプレビューを `INVALID_LAYOUT` とする。
- スタイル省略（`null`）は親を継承する。`TextStyle()` / `TextStyleDto()` は初期値を明示し、親の拡大や強調をリセットする。フィールドごとの部分継承は行わない。二列の行揃えは親 Column が決める。
- `TwoColumnOverflow.Clip` / `TwoColumnOverflowDto.Clip` は予約値であり、常に `INVALID_LAYOUT` となる。金額などを暗黙に切り捨てる方針は導入しない。右列が `null` または空文字の場合もこの契約を適用し、`Fail` は左列だけでも幅超過を拒否する。
- ReceiptLine のテキストと QR の配置には列内配置の構文を使う。左は `内容 |`、中央は `| 内容 |`、右は `| 内容` とし、実際の renderer が解釈した配置と座標を JVM テストで検証する。
- TM-m30 は Partial cut のみ対応する。Strict の Full 指定はエラー、Compatibility は Partial への変更を警告する。
- Compatibility の QR セルサイズは機種設定で確定する。誤り訂正は ESC/POS で Medium、Star で Quartile を機種設定として明示する。設定変更には `VALUE_CLAMPED` または `VALUE_SUBSTITUTED` を返す。
- encoder へ直接渡した `LaidOutJob` にも同じ補正を適用する。補正は冪等であり、通常の compile では二度目の変更は生じない。

## 9. 検証と配布

`engineCheck` は KMP 単独の検証を維持する。
`fullCheck` はこれに Flutter の解析、単体テスト、example の Widget テストを加える。
GitHub Actions は Android と iOS の実際の Channel を通す同じテストを実行し、固定したバイト列、診断、スタイル継承を照合する。

配布バージョン、ZIP の生成手順、CI の成果物は[ルート README の配布手順](../README.md#固定バージョンの配布物)を参照する。

機種仕様の根拠と紙上の確認手順は[実機確認表](printer-verification.md)を参照する。
