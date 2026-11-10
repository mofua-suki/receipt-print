import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderReceiptLineSvg } from './receiptline-svg.mjs';

const [inputArgument, goldenArgument, columnsArgument = '48'] = process.argv.slice(2);
if (!inputArgument || !goldenArgument) {
  throw new Error('usage: node verify-svg.mjs <input.receiptline> <golden.svg> [columns]');
}

const [input, golden] = await Promise.all([
  readFile(resolve(inputArgument), 'utf8'),
  readFile(resolve(goldenArgument), 'utf8'),
]);
const actual = renderReceiptLineSvg(input, Number(columnsArgument));
if (actual !== golden) {
  throw new Error('ReceiptLine SVG golden differs; run the explicit render command to inspect it');
}

const receiptline = createRequire(import.meta.url)('receiptline');
const seen = [];
const svg = receiptline.commands.svg;
receiptline.transform(String.raw`{text:nowrap}
\x20A\x20 |
| {code:\x20A\x20; option:qrcode,6,m} |
{text:wrap}`, {
  cpl: 48,
  encoding: 'cp932',
  command: {
    ...svg,
    text(text, encoding) {
      seen.push(['text', text]);
      return svg.text.call(this, text, encoding);
    },
    qrcode(symbol, encoding) {
      seen.push(['qr', symbol.data]);
      return svg.qrcode.call(this, symbol, encoding);
    },
  },
});
assert.deepEqual(seen, [['text', ' A '], ['qr', ' A ']], 'ReceiptLine must preserve text and QR boundary spaces');
