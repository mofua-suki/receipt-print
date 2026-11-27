import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';

const receiptline = createRequire(import.meta.url)('receiptline');
// 48 columns x 12 dots; QR version 1 is 21 modules x 6 dots.
const alignments = {
  Left: { textPosition: 0, qrAlignment: 0, qrPosition: 0 },
  Center: { textPosition: 22.5, qrAlignment: 1, qrPosition: 225 },
  Right: { textPosition: 45, qrAlignment: 2, qrPosition: 450 },
};
const svg = receiptline.commands.svg;
for (const [alignment, expected] of Object.entries(alignments)) {
  const text = [];
  const qr = [];
  const fixture = new URL(`../../fixtures/previews/alignment-${alignment.toLowerCase()}.receiptline`, import.meta.url);
  const input = readFileSync(fixture, 'utf8').replaceAll('\r\n', '\n').replace(/\n$/, '');
  receiptline.transform(input, {
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
        const qrAlignment = this.lineAlign;
        const result = svg.qrcode.call(this, symbol, encoding);
        const fragment = this.svgContent.slice(start);
        const position = Number(fragment.match(/translate\(([-\d.]+),/)[1]);
        qr.push({ value: symbol.data, alignment: qrAlignment, position });
        return result;
      },
    },
  });
  assert.deepEqual(text, [{ value: 'ABC', position: expected.textPosition }], `${alignment} text position`);
  assert.deepEqual(qr, [{ value: 'A', alignment: expected.qrAlignment, position: expected.qrPosition }], `${alignment} QR SVG position`);
}
