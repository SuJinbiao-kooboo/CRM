/* 调试: 行371 的 Company -> 主名 -> norm key -> PREMERGE 匹配检查 */
const XLSX = require('xlsx');
const fs = require('fs');
const SUFFIX = ['limited', 'ltd', 'llc', 'l.l.c', 'inc', 'corp', 'corporation', 'co', 'company', 'holdings', 'holding',
  'group', 'international', 'pte', 'technologies', 'technology', 'electronics', 'systems', 'system', 'digital',
  'enterprises', 'enterprise', 'solutions', 'solution', 'computers', 'computer', 'trading', 'distribution',
  'integrated', 'semi', 'semiconductor', 'memory', 'storage', 'components', 'component', 'bv', 'fzco',
  'gmbh', 'plc', 'llp', 'sarl', 'private', 'company limited',
  '贸易', '公司', '有限公司', '集团', '科技', '电子', '技术', '信息', '数码', '实业', '国际', '股份', '有限'];
const norm = (s, trace) => {
  let k = String(s).toLowerCase().replace(/[.,、，()（）\-_/|&·•\s"'`~!@#$%^*+=?<>:;【】\[\]{}]+/g, '');
  if (trace) trace.push('after clean: [' + k + ']');
  let changed = true;
  let round = 0;
  while (changed && k.length > 4) {
    changed = false;
    round++;
    for (const suf of SUFFIX) {
      if (k.endsWith(suf) && k.length - suf.length >= 3) { k = k.slice(0, k.length - suf.length); changed = true; if (trace) trace.push('round' + round + ' strip[' + suf + '] -> [' + k + ']'); break; }
    }
  }
  return k;
};
const wb = XLSX.readFile('C:/Users/亦钟人/Desktop/PN.xlsx');
const rows = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: '' });
const H = rows[0].map((h, i) => String(h || '').trim());
const ci = H.indexOf('Company');
const out = [];
[370, 371].forEach(r => {
  const comp = String(rows[r - 1][ci] || '');
  const main = (comp.split(/[|\n/]/).map(s => s.trim()).filter(Boolean)[0] || '');
  out.push('row ' + r + ' comp=[' + comp + ']');
  out.push('main=[' + main + ']');
  const trace = [];
  const key = norm(main, trace);
  out.push(...trace);
  out.push('key=[' + key + ']');
  out.push('match=' + (key === 'computersolutionincanchor美国关联' || key === 'anchorinnovativeitfzc'));
});
fs.writeFileSync('c:/Project/RuoYi-Vue/tmp/debug371.txt', out.join('\n'), 'utf8');
console.log('ok');
