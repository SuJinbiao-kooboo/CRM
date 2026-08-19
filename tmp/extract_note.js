/* 提取 PN_公司名称标记.xlsx 的 同公司分组说明 sheet 为文本, 供用户预览 */
const XLSX = require('xlsx');
const fs = require('fs');
const wb = XLSX.readFile('c:/Project/RuoYi-Vue/tmp/PN_公司名称标记.xlsx');
const note = XLSX.utils.sheet_to_json(wb.Sheets['同公司分组说明'], { header: 1, defval: '' });
const out = note.map(r => r.map(c => String(c === undefined || c === null ? '' : c)).join('\t'));
fs.writeFileSync('c:/Project/RuoYi-Vue/tmp/mark_note_final.txt', out.join('\n'), 'utf8');
console.log('ok, lines=' + out.length);
