# プリンターの仕様と実機確認

バイト列の Fixture と Simulator テストは、実機での印刷成功を証明しない。
以下の実機確認はすべて未実施である。

| 機種 | 紙上の確認 | 重点項目 |
| --- | --- | --- |
| Epson TM-m30 | 未実施 | Partial cut、末尾の文字、外付けブザー設定 |
| Epson TM-T90KP | 未実施 | 余白なしカット、反転時の末尾 |
| SII RP-F10 | 未実施 | Full / Partial、反転、QR |
| Star FVP10 | 未実施 | カット、QR 誤り訂正、ブザー |
| Star TSP650II | 未実施 | カット、反転、QR、外付けブザー |
| Star mC-Print3 | 未実施 | カット、反転、QR、サウンド |

## 確認した仕様

TM-m30 のカット方式は中央一点を残す Partial cut である。
[Epson TM-m30 Technical Reference Guide](https://files.support.epson.com/pdf/pos/bulk/tm-m30_trg_en_revh.pdf) の Product Specifications を根拠とする。
`PrinterProfiles.tmM30` はこの仕様に従い、Full cut を能力に含めない。

その他の能力と命令は `fixtures/commands` の回帰テストで検証するが、メーカー仕様の全項目照合と実機確認は未完了である。

## 紙送りとカットの確認手順

1. 機種、ファームウェア、用紙幅、文字フォント、接続方式、本ライブラリのバージョンとコミットを記録する。
2. 末尾を識別できる文字列「最終行 ABC123」を置き、Feed を置かない入力を Strict と Compatibility で印刷する。
3. 同じ入力に Feed(1)、Feed(3) を加えて印刷する。対応しているカットの種類ごとに繰り返す。
4. 切り取った側の紙に最終行全体が残るか、次の伝票へ文字が残らないか、余白が適切かを記録する。切り取った紙とロール側を撮影する。
5. 通常方向と、対応機種では反転方向も確認する。Feed の必要量は用紙幅や倍率を変えて再確認する。
6. QR は ASCII の URL と日本語データを読み取り、入力の CP932 バイト列と比較する。Compatibility の補正内容と診断も記録する。

ESC/POS の Strict は `GS V 0/1`、Compatibility の通常カットは `GS V 66 0` を使う。
現段階では自動紙送り量を推測で追加しない。
実機で欠けが生じた場合は、上記の条件と命令列を保存して、機種ごとのカット前紙送りを決定する。
