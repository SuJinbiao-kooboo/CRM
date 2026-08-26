/* 验证 PN_公司名称标记.xlsx: 检查行370/371是否已合并为同一公司名称, 并检查说明sheet */
const XLSX = require('xlsx');
const fs = require('fs');
const wb = XLSX.readFile('c:/Project/RuoYi-Vue/tmp/PN_公司名称标记.xlsx');
const ws = wb.Sheets['PN标记'];
const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
const H = rows[0].map((h, i) => String(h || '').trim());
const ci = H.indexOf('Company') >= 0 ? H.indexOf('Company') : 6; // 公司名称列为第0列, 原Company右移一位
const noteWs = wb.Sheets['同公司分组说明'];
const note = XLSX.utils.sheet_to_json(noteWs, { header: 1, defval: '' });
const out = [];
// 行370/371 的公司名称列
[370, 371].forEach(r => {
  out.push('行' + r + ' 公司名称=[' + rows[r - 1][0] + '] 原Company=[' + String(rows[r - 1][ci] || '').slice(0, 60) + ']');
});
// 关键合并点抽查: [Excel行号, 期望公司名称]
const checks = [
  [106, 'HONCKONG HONCH INTERNATIONAL LIMITED香港鴻啟國際有限公司'],
  [110, 'HONCKONG HONCH INTERNATIONAL LIMITED香港鴻啟國際有限公司'],
  [111, 'HONCKONG HONCH INTERNATIONAL LIMITED香港鴻啟國際有限公司'],
  [201, 'HONGKONG NETZONE TECHNOLOGY CO., LIMITED'],
  [207, 'JOINT-HARVEST INTERNATION HOLDINGS LIMITED'],
  [208, 'JOINT-HARVEST INTERNATION HOLDINGS LIMITED'],
  [314, 'AMAX'],
  [345, 'Powerleader Computer System Co., Ltd.'],
  [354, '杭州易成讯创电子科技有限公司'],
  [363, '融核（上海）科技有限公司'],
  [364, '融核（上海）科技有限公司'],
  [367, '芯通科技有限公司']
];
let pass = 0;
checks.forEach(([r, expect]) => {
  const got = String(rows[r - 1][0] || '');
  const ok = got === expect;
  if (ok) pass++;
  out.push((ok ? 'PASS' : 'FAIL') + ' 行' + r + ' 期望=[' + expect + '] 实际=[' + got + ']');
});
out.push('抽查通过: ' + pass + '/' + checks.length);
out.push('输出总行数: ' + rows.length + ' (含空行, 应为372)');
// 说明sheet中 Anchor 相关组
note.forEach(n => {
  if (String(n[0] || '').toLowerCase().includes('anchor') || String(n[3] || '').toLowerCase().includes('anchor')) {
    out.push('说明组: 名称=[' + n[0] + '] 行数=' + n[1] + ' 行号=[' + n[2] + ']');
  }
});
// 说明sheet总组数统计
const groups = note.filter(n => n[0] && String(n[2] || '').includes(',') || (n[0] && /^\d+$/.test(String(n[2] || '')) && n[1] > 1));
out.push('说明sheet含分组的行数: ' + note.filter(n => n[0] && n[2] && String(n[2]).includes(',')).length);
fs.writeFileSync('c:/Project/RuoYi-Vue/tmp/verify_final.txt', out.join('\n'), 'utf8');
console.log('ok');
