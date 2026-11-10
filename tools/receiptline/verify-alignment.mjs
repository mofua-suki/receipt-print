import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';

const receiptline = createRequire(import.meta.url)('receiptline');
const alignment = process.argv[2];
// 48 columns x 12 dots; QR version 1 is 21 modules x 6 dots.
const expected = {
  Left: { textPosition: 0, qrAlignment: 0, qrPosition: 0 },
  Center: { textPosition: 22.5, qrAlignment: 1, qrPosition: 225 },
  Right: { textPosition: 45, qrAlignment: 2, qrPosition: 450 },
}[alignment];
assert.ok(expected, `Unknown alignment: ${alignment}`);
const text = [];
const qr = [];
const svg = receiptline.commands.svg;
receiptline.transform(readFileSync(0, 'utf8'), {
  cpl: 48,
  encoding: 'multilingual',
  command: {
    ...svg,
    text(value, encoding) {
      if (value.trim()) text.push({ value, position: this.textPosition });
      return svg.text.call(this, value, encoding);
    },
    qrcode(symbol, encoding) {
      const start = this.svgContent.length;
      const alignment = this.lineAlign;
      const result = svg.qrcode.call(this, symbol, encoding);
      const fragment = this.svgContent.slice(start);
      const position = Number(fragment.match(/translate\(([-\d.]+),/)[1]);
      qr.push({ value: symbol.data, alignment, position });
      return result;
    },
  },
});
assert.deepEqual(text, [{ value: 'ABC', position: expected.textPosition }], `${alignment} text position`);
assert.deepEqual(qr, [{ value: 'A', alignment: expected.qrAlignment, position: expected.qrPosition }], `${alignment} QR SVG position`);
