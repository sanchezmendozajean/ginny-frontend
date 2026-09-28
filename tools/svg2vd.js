// Convierte los SVG de Illustrator (estilos por clase CSS, rect/circle/polygon/path) a VectorDrawable.
// Uso (desde la raíz del proyecto): node tools/svg2vd.js "../Iconos del sistema" app/src/main/res/drawable
const fs = require('fs');
const path = require('path');
const [, , srcDir, outDir] = process.argv;

const attr = (s, n) => { const m = s.match(new RegExp(`\\s${n}="([^"]*)"`)); return m ? m[1] : null; };
const num = (s, n, d = 0) => { const v = attr(s, n); return v == null ? d : parseFloat(v); };
const f = (x) => +x.toFixed(4);

function parseStyles(svg) {
  const styles = {};
  const block = (svg.match(/<style>([\s\S]*?)<\/style>/) || [])[1] || '';
  for (const [, sel, body] of block.matchAll(/([^{}]+)\{([^}]*)\}/g)) {
    const props = Object.fromEntries(body.split(';').filter(Boolean).map(p => p.split(':').map(t => t.trim())));
    for (const s of sel.split(',')) {
      const cls = s.trim().replace(/^\./, '');
      styles[cls] = { ...(styles[cls] || {}), ...props };
    }
  }
  return styles;
}

function rectPath(x, y, w, h, rx) {
  if (!rx) return `M${f(x)},${f(y)}h${f(w)}v${f(h)}h${f(-w)}z`;
  rx = Math.min(rx, w / 2, h / 2);
  return `M${f(x + rx)},${f(y)}h${f(w - 2 * rx)}a${f(rx)},${f(rx)} 0 0 1 ${f(rx)},${f(rx)}` +
    `v${f(h - 2 * rx)}a${f(rx)},${f(rx)} 0 0 1 ${f(-rx)},${f(rx)}h${f(-(w - 2 * rx))}` +
    `a${f(rx)},${f(rx)} 0 0 1 ${f(-rx)},${f(-rx)}v${f(-(h - 2 * rx))}a${f(rx)},${f(rx)} 0 0 1 ${f(rx)},${f(-rx)}z`;
}
const circlePath = (cx, cy, r) =>
  `M${f(cx - r)},${f(cy)}a${f(r)},${f(r)} 0 1 0 ${f(2 * r)},0a${f(r)},${f(r)} 0 1 0 ${f(-2 * r)},0z`;
const polyPath = (pts) => {
  const n = pts.trim().split(/[\s,]+/).map(Number);
  let d = `M${n[0]},${n[1]}`;
  for (let i = 2; i < n.length; i += 2) d += `L${n[i]},${n[i + 1]}`;
  return d + 'z';
};

function convert(svg) {
  const styles = parseStyles(svg);
  const [, , , vw, vh] = (attr(svg, 'viewBox') || '0 0 100 100').split(/\s+/).map(Number).reduce((a, v) => [...a, v], [null]);
  const out = [];
  for (const [, tag, rest] of svg.matchAll(/<(path|rect|circle|polygon)\b([^>]*)\/?>/g)) {
    let d;
    if (tag === 'path') d = attr(rest, 'd');
    if (tag === 'rect') d = rectPath(num(rest, 'x'), num(rest, 'y'), num(rest, 'width'), num(rest, 'height'), num(rest, 'rx', num(rest, 'ry')));
    if (tag === 'circle') d = circlePath(num(rest, 'cx'), num(rest, 'cy'), num(rest, 'r'));
    if (tag === 'polygon') d = polyPath(attr(rest, 'points'));
    const st = {};
    for (const c of (attr(rest, 'class') || '').split(/\s+/).filter(Boolean)) Object.assign(st, styles[c] || {});
    const a = [`android:pathData="${d}"`];
    // El color real lo pone el tint desde Compose; aquí negro opaco.
    if (st.fill !== 'none') a.push('android:fillColor="#FF000000"');
    if (st.stroke && st.stroke !== 'none') {
      a.push('android:strokeColor="#FF000000"');
      a.push(`android:strokeWidth="${parseFloat(st['stroke-width'] || '1')}"`);
      if (st['stroke-linecap']) a.push(`android:strokeLineCap="${st['stroke-linecap']}"`);
      if (st['stroke-linejoin']) a.push(`android:strokeLineJoin="${st['stroke-linejoin']}"`);
      if (st['stroke-miterlimit']) a.push(`android:strokeMiterLimit="${st['stroke-miterlimit']}"`);
    }
    out.push(`    <path\n        ${a.join('\n        ')} />`);
  }
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Generado desde "Iconos del sistema/" (SVG de Illustrator). Para cambiarlo, editar el SVG y regenerar. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${vw}"
    android:viewportHeight="${vh}">
${out.join('\n')}
</vector>
`;
}

for (const file of fs.readdirSync(srcDir).filter(n => n.endsWith('.svg'))) {
  const xml = convert(fs.readFileSync(path.join(srcDir, file), 'utf8'));
  fs.writeFileSync(path.join(outDir, file.replace(/\.svg$/, '.xml')), xml);
  console.log(file, '->', (xml.match(/<path/g) || []).length, 'paths');
}
