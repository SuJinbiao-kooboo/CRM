// 临时脚本：将 PN.xlsx 数据转成紧凑 CSV，便于分析全貌
const XLSX = require('xlsx');
const fs = require('fs');

const file = 'C:\\Users\\亦钟人\\Desktop\\PN.xlsx';
const wb = XLSX.readFile(file);
const ws = wb.Sheets['Sheet1'];
const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: null, raw: true });

// 表头在第 1 行（索引 0）
const header = rows[0];
const dataRows = rows.slice(1).filter(r => r.some(c => c !== null && String(c).trim() !== ''));

// 输出每行的紧凑形式：编号 | 公司(前20字) | AR | 产品 | 类型 | 联系人 | 职位 | 邮箱 | 电话 | 优先级 | 来源 | 备注
const out = [];
out.push('NO\tCompany\tAR\tWebsite\tProducts\tTYPE\tContact\tPosition\tEmail\tSocial\tRemark\tPriority\tFrom\tNote');
dataRows.forEach((r, idx) => {
    const get = i => (r[i] === null || r[i] === undefined) ? '' : String(r[i]).replace(/\n/g, '|');
    out.push([
        idx + 2,
        get(0).slice(0, 30),
        get(1), get(2), get(4).slice(0, 20), get(5),
        get(6).slice(0, 20), get(7), get(8), get(9).slice(0, 15),
        get(10).slice(0, 15), get(11), get(12), get(13)
    ].join('\t'));
});

fs.writeFileSync('pn_compact.tsv', out.join('\n'), 'utf-8');
console.log('total data rows:', dataRows.length);
console.log('written to pn_compact.tsv');
