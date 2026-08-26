/* PN.xlsx(新版本) 数据校验脚本 v2
 * 新表头: Code | Bank info | 公司情况备注 | Bill to | Ship to | Company | Country/Area | Website | ADD | Products | Type | Contact | Position | Email | Social media(wechat/whatsapp) | 对接点合作方向
 * 1. 逐行校验：公司缺失 / 联系人缺失 / 长度超限(按DB字段上限) / 邮箱格式 / 电话识别 / Type枚举 / 重名检测
 * 2. 公司维度聚合（Company 取 |、/、换行 第一段为主名，其余为关联抬头）
 * 3. 与库中现有 55 家供应商重名检测
 * 4. 输出 tmp/pn2_validation.json + tmp/PN_数据校验报告.xlsx
 */
const XLSX = require('xlsx');
const fs = require('fs');

const SRC = 'C:/Users/亦钟人/Desktop/PN.xlsx';
const OUT_JSON = 'c:/Project/RuoYi-Vue/tmp/pn2_validation.json';
const OUT_XLSX = 'c:/Project/RuoYi-Vue/tmp/PN_数据校验报告.xlsx';

/* ---------- 读取 ---------- */
const wb = XLSX.readFile(SRC);
const ws = wb.Sheets[wb.SheetNames[0]];
const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
const H = rows[0].map((h, i) => String(h || '').trim());
const idx = {};
H.forEach((h, i) => { if (h) idx[h] = i; });
console.log('headers:', JSON.stringify(H.slice(0, 16)));

const dataRows = [];
for (let r = 1; r < rows.length; r++) {
  const row = rows[r];
  const nonEmpty = (Array.isArray(row) ? row : []).filter(c => String(c || '').trim() !== '').length;
  if (nonEmpty > 0) dataRows.push({ r: r + 1, cells: row });
}
console.log('dataRows:', dataRows.length);

const cell = (row, ci) => (ci === undefined ? '' : String((row && row[ci]) || '').trim());
const C = {
  code: idx['Code'], bank: idx['Bank info'], compNote: idx['公司情况备注'], billTo: idx['Bill to'], shipTo: idx['Ship to'],
  company: idx['Company'], country: idx['Country/Area'], website: idx['Website'], add: idx['ADD'], products: idx['Products'],
  type: idx['Type'], contact: idx['Contact'], position: idx['Position'], email: idx['Email'], social: idx['Social media(wechat/whatsapp)'], coopDir: idx['对接点合作方向']
};

/* ---------- 规则 ---------- */
const LIMIT = { // crm_supplier / crm_supplier_contact 字段上限
  company: 200, type: 50, products: 256, country: 100, address: 1024, website: 200,
  remark: 2048, remark2: 1024, bank: 1024, billTo: 1024, shipTo: 1024, code: 50,
  email: 100, phone: 20, whatsapp: 50, wechat: 50, contactName: 100, post: 100, otherContact: 200, followUp: 64
};
const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const phoneOnlyRe = /^[\d\s()+\-－/]{5,}$/;      // 整格为电话
const hasDigits6 = /\d{6,}/;                      // 含6位以上数字
// Type 枚举 -> 字典映射建议（crm_supplier_type: 授权代理商/贸易商/集成商/库存商/终端）
const TYPE_MAP = {
  'IT DIST': '贸易商',
  'IT SHOP': '贸易商',
  'IT Broker': '贸易商',
  'IT PARTNER': '贸易商',
  'IT AD/franchise': '授权代理商',
  'IT MFG': '贸易商',
  'IT MFG+AD': '授权代理商,贸易商',
  'IT Stockiest': '库存商',
  'SI-System integrator': '集成商',
  'Solution provider': '集成商',
  'Others': '贸易商',
  'Third-party': '服务商(字典缺失,建议新增)'
};
// 库中现有供应商（重名检测）
const EXISTING = ['ASTREON COMPUTERS L.L.C', 'Star Seed Technologies ME FZE', 'MA-LABS', '诚誉', 'hkhouch', '富强', 'mbuzz', '赞禾', 'B2B', 'DickerData', 'Taurus', 'elena.medved', 'Selma Selmanagić-S2', '彼富', 'Honch', 'summits', 'STES', 'Mempire', '山东怡然/HuaShu', 'Jeffrey', '融科', '鑫浩翔', 'LinkView', '星能', 'SGC', 'E2K', 'NXE', 'CI Gulf', 'Golden Fish', 'xinhao', '新欣', 'VastSystem', 'Doglas', 'CPU Power/星云', '迈驰', 'Servergst', 'Tier', '华储', '熊猫国际', 'techno', '鑫浩(不是鑫浩翔)', '云仲存储', 'Syno smoothroute', '远信', 'Sbs', '环阳', 'B2B Export', 'ECOM Electronic', 'Navy Blue', '迪天嘉业', '芯力量', '宝通', 'Darian', '广达/grandtect', '华泰易'];
const norm = s => String(s || '').toLowerCase().replace(/[\s.。，,()（）\-_/|&]/g, '');
const EX_NORM = EXISTING.map(norm);
function findDup(name) {
  const n = norm(name);
  if (!n || n.length < 2) return '';
  for (let i = 0; i < EX_NORM.length; i++) {
    if (EX_NORM[i].length >= 2 && (n === EX_NORM[i] || n.includes(EX_NORM[i]) || EX_NORM[i].includes(n))) return EXISTING[i];
  }
  return '';
}
// 公司主名提取：| / 换行 切分，第一段为主名，其余为关联抬头
function splitCompanies(v) {
  const parts = String(v || '').split(/[|\n/]/).map(s => s.trim()).filter(Boolean);
  return { main: parts[0] || '', related: parts.slice(1) };
}

/* ---------- 逐行校验 ---------- */
const PROBLEMS = [];
const ROW_RESULTS = [];
const add = (r, company, level, code, field, msg) => PROBLEMS.push({ r, company, level, code, field, msg });

dataRows.forEach(({ r, cells }) => {
  const company = cell(cells, C.company);
  const country = cell(cells, C.country);
  const website = cell(cells, C.website);
  const products = cell(cells, C.products);
  const type = cell(cells, C.type);
  const contact = cell(cells, C.contact);
  const position = cell(cells, C.position);
  const email = cell(cells, C.email);
  const social = cell(cells, C.social);
  const bank = cell(cells, C.bank);
  const billTo = cell(cells, C.billTo);
  const shipTo = cell(cells, C.shipTo);
  const compNote = cell(cells, C.compNote);
  const coopDir = cell(cells, C.coopDir);
  const code = cell(cells, C.code);
  const { main, related } = splitCompanies(company);

  /* 1) Company */
  if (!company) add(r, '', 'ERROR', 'MISSING_COMPANY', 'Company', '公司名为空：仅联系人/邮箱/备注，无法创建供应商记录');
  else {
    if (main.length > LIMIT.company) add(r, main, 'ERROR', 'LEN_COMPANY', 'Company', `主公司名长度 ${main.length}>${LIMIT.company}`);
    if (related.length > 0) add(r, main, 'INFO', 'MULTI_TITLE', 'Company', `同格含 ${related.length} 个关联抬头/别名(如中英文名): ${related.join(' | ').slice(0, 60)}，主名取"${main}"，其余记入备注`);
    const dup = findDup(main);
    if (dup) add(r, main, 'WARN', 'EXIST_IN_DB', 'Company', `与库中现有供应商疑似重名: "${dup}"，建议更新已有记录而非新增`);
  }

  /* 2) 联系人 */
  if (!contact) add(r, main, 'WARN', 'MISSING_CONTACT', 'Contact', '联系人为空：此行走联系人跳过，只建公司记录');
  else {
    const first = contact.split('|')[0].trim();
    if (first.length > LIMIT.contactName) add(r, main, 'ERROR', 'LEN_CONTACT', 'Contact', `姓名长度 ${first.length}>${LIMIT.contactName}: "${first.slice(0, 50)}"`);
    if (phoneOnlyRe.test(first)) add(r, main, 'ERROR', 'CONTACT_IS_PHONE', 'Contact', `联系人位置是电话号码(无姓名): "${first.slice(0, 50)}"，需转存电话字段`);
    if (emailRe.test(first)) add(r, main, 'ERROR', 'CONTACT_HAS_EMAIL', 'Contact', `联系人位置是邮箱: "${first.slice(0, 50)}"`);
    if (contact.includes('|')) add(r, main, 'INFO', 'CONTACT_MULTI', 'Contact', `联系人含 | 分隔（姓名|电话/备注）: "${contact.slice(0, 60)}"，入库时拆分`);
    if (hasDigits6.test(first) && !phoneOnlyRe.test(first)) add(r, main, 'INFO', 'CONTACT_HAS_PHONE', 'Contact', `姓名内疑含电话: "${first.slice(0, 60)}"（如"林玉龙 Colin | 18668035107"属正常拆分场景）`);
  }

  /* 3) Email */
  if (!email) add(r, main, 'INFO', 'MISSING_EMAIL', 'Email', '邮箱为空');
  else {
    const parts = email.split(/[|;；]/).map(s => s.trim()).filter(Boolean);
    if (parts.length > 1) add(r, main, 'INFO', 'EMAIL_MULTI', 'Email', `含 ${parts.length} 个邮箱，主邮箱=${parts[0]}，其余入 other_contact_first`);
    parts.forEach(e => {
      if (e.length > LIMIT.email) add(r, main, 'ERROR', 'LEN_EMAIL', 'Email', `邮箱长度 ${e.length}>${LIMIT.email}: "${e.slice(0, 60)}"`);
      if (!emailRe.test(e)) add(r, main, 'ERROR', 'EMAIL_FORMAT', 'Email', `邮箱格式非法: "${e.slice(0, 60)}"`);
    });
  }

  /* 4) Social media */
  if (social) {
    if (social.length > LIMIT.otherContact) add(r, main, 'ERROR', 'LEN_SOCIAL', 'Social', `社媒/电话长度 ${social.length}>${LIMIT.otherContact}`);
    else if (hasDigits6.test(social)) add(r, main, 'INFO', 'SOCIAL_IS_PHONE', 'Social', `社媒列实际为电话号码/多联系方式: "${social.slice(0, 60)}"，入库时按 whatsaap/wechat/other 归类`);
  }

  /* 5) Position */
  if (position && position.length > LIMIT.post) add(r, main, 'ERROR', 'LEN_POSITION', 'Position', `职位长度 ${position.length}>${LIMIT.post}`);

  /* 6) Type */
  if (!type) add(r, main, 'INFO', 'MISSING_TYPE', 'Type', '类型为空（默认不填，列表可显示空）');
  else {
    const t = type.trim();
    if (!TYPE_MAP[t]) add(r, main, 'WARN', 'TYPE_UNKNOWN', 'Type', `类型 "${t}" 不在已知枚举，需映射`);
    else if (TYPE_MAP[t].includes('字典缺失')) add(r, main, 'WARN', 'TYPE_THIRD_PARTY', 'Type', `类型 "${t}"(财务/仓库/秘书等第三方)，字典 crm_supplier_type 无"服务商"值，需新增字典或映射`);
  }

  /* 7) 长度校验（长文本列） */
  const lenChecks = [
    ['Code', code, LIMIT.code], ['Country/Area', country, LIMIT.country], ['Website', website, LIMIT.website],
    ['ADD', cell(cells, C.add), LIMIT.address], ['Products', products, LIMIT.products],
    ['Bank info', bank, LIMIT.bank], ['Bill to', billTo, LIMIT.billTo], ['Ship to', shipTo, LIMIT.shipTo],
    ['公司情况备注', compNote, LIMIT.remark], ['对接点合作方向', coopDir, LIMIT.remark2]
  ];
  lenChecks.forEach(([f, v, lim]) => {
    if (v && v.length > lim) add(r, main, 'ERROR', `LEN_${f.toUpperCase().replace(/[^A-Z]/g, '')}`, f, `长度 ${v.length}>${lim}，插入会失败或被截断`);
  });

  /* 8) Website 前缀 */
  if (website && !/^https?:\/\//i.test(website)) add(r, main, 'INFO', 'WEBSITE_NO_SCHEME', 'Website', `缺 http(s):// 前缀: "${website.slice(0, 60)}"，前端链接跳转需补全`);

  /* 逐行结果 */
  ROW_RESULTS.push({ r, code, main, related, country, website, products, type, contact, position, email, social, bank, billTo, shipTo, compNote, coopDir });
});

/* ---------- 公司聚合 ---------- */
const COMPANIES = {};
ROW_RESULTS.forEach(row => {
  const key = row.main || '(空公司)';
  if (!COMPANIES[key]) COMPANIES[key] = { name: key, related: [], rows: [], contacts: 0, hasEmail: 0, hasPhone: 0, hasWebsite: 0, hasType: 0, hasCode: 0, types: new Set(), codes: new Set() };
  const c = COMPANIES[key];
  c.rows.push(row.r);
  row.related.forEach(x => { if (!c.related.includes(x)) c.related.push(x); });
  if (row.contact) c.contacts++;
  if (row.email) c.hasEmail++;
  if (row.social || /[\d()+\-]{5,}/.test(row.contact)) c.hasPhone++;
  if (row.website) c.hasWebsite++;
  if (row.type) { c.hasType++; c.types.add(row.type); }
  if (row.code) { c.hasCode++; c.codes.add(row.code); }
});
Object.values(COMPANIES).forEach(c => {
  const miss = [];
  if (!c.hasWebsite) miss.push('官网');
  if (!c.hasEmail) miss.push('邮箱');
  if (!c.hasPhone) miss.push('电话');
  if (!c.hasType) miss.push('类型');
  if (!c.hasCode) miss.push('Code编号');
  if (c.contacts === 0) miss.push('联系人');
  c.missing = miss;
  c.typeList = [...c.types].join(';');
  c.codeList = [...c.codes].join(';');
});

/* ---------- 汇总 ---------- */
const SUMMARY = {};
PROBLEMS.forEach(p => {
  if (!SUMMARY[p.code]) SUMMARY[p.code] = { code: p.code, level: p.level, count: 0, field: p.field, examples: [] };
  SUMMARY[p.code].count++;
  if (SUMMARY[p.code].examples.length < 3) SUMMARY[p.code].examples.push(`行${p.r}: ${String(p.msg).slice(0, 70)}`);
});
const byLevel = { ERROR: 0, WARN: 0, INFO: 0 };
PROBLEMS.forEach(p => byLevel[p.level]++);

const result = {
  generatedAt: new Date().toISOString(), srcFile: SRC,
  totalRows: dataRows.length,
  totalCompanies: Object.keys(COMPANIES).filter(k => k !== '(空公司)').length,
  emptyCompanyRows: (COMPANIES['(空公司)'] || { rows: [] }).rows,
  problems: { ERROR: byLevel.ERROR, WARN: byLevel.WARN, INFO: byLevel.INFO, total: PROBLEMS.length },
  summary: Object.values(SUMMARY).sort((a, b) => b.count - a.count),
  companies: Object.values(COMPANIES).sort((a, b) => b.rows.length - a.rows.length),
  problemsDetail: PROBLEMS
};
fs.writeFileSync(OUT_JSON, JSON.stringify(result, null, 1), 'utf8');
console.log('json done. problems:', PROBLEMS.length, 'ERROR:', byLevel.ERROR, 'WARN:', byLevel.WARN, 'INFO:', byLevel.INFO, '| companies:', result.totalCompanies, '| emptyCompanyRows:', result.emptyCompanyRows.length);

/* ---------- Excel 报告 ---------- */
const out = XLSX.utils.book_new();
const sheetOf = (name, aoa, widths) => {
  const s = XLSX.utils.aoa_to_sheet(aoa);
  s['!cols'] = widths.map(w => ({ wch: w }));
  XLSX.utils.book_append_sheet(out, s, name);
};

sheetOf('校验总览', [
  ['PN.xlsx 数据校验报告（新版本）', '', '', '', '', ''],
  ['生成时间', result.generatedAt, '', '', '', ''],
  ['', '', '', '', '', ''],
  ['数据总行数', result.totalRows, '涉及公司数', result.totalCompanies, '', ''],
  ['问题总数', result.problems.total, 'ERROR(需处理/跳过)', result.problems.ERROR, 'WARN(建议确认)', result.problems.WARN],
  ['公司名为空的行数', result.emptyCompanyRows.length, '（这些行只能跳过或挂靠已有公司）', JSON.stringify(result.emptyCompanyRows), '', ''],
  ['', '', '', '', '', ''],
  ['主要发现与建议', '', '', '', '', ''],
  ['1. 16 列结构与 crm_supplier 表字段高度对应（Code→supplier_code, Bank info→bank_info, Bill to→bill_to, Ship to→ship_to, 公司情况备注→remark, 对接点合作方向→remark_second）', '', '', '', '', ''],
  ['2. 数据库现有字段可完整承载本文件数据，无需新增字段；唯一风险点：联系人 phone varchar(20) 对长号码(如 00852-97600611/00852-31142311)会超限，建议超长号码拆入 other_contact_first 或扩列到 varchar(100)', '', '', '', '', ''],
  ['3. Type 枚举：IT DIST/IT SHOP/IT Broker/IT Partner→贸易商；IT AD/franchise→授权代理商；IT MFG→贸易商；IT MFG+AD→授权代理商,贸易商；IT Stockiest→库存商；SI-System integrator/Solution provider→集成商；Third-party→字典无"服务商"值(前10行财务/仓库/秘书公司)，建议新增字典或映射', '', '', '', '', ''],
  ['4. 部分公司与库中已有供应商重名(Taurus/Star Seed/ASTREON/DickerData/融科/华储/远信/宝通/LinkView/彼富/Honch/STES/VastSystem/新欣/鑫浩/Servergst/芯通等)，插入策略建议：已存在→跳过新增(报告列出清单)，未存在→新增', '', '', '', '', ''],
  ['5. 联系人多格含"姓名|电话"，邮箱多格含多邮箱，入库脚本会拆分；无法拆分的(整格电话/整格邮箱)标 ERROR 需人工确认', '', '', '', '', ''],
  ['6. Company 同格含中英文名/多个关联抬头(如 极光通讯|POLARIS、SANFAN|科创邦达|SANFAN TECH|HK Qianjin)取第一段为主名，其余记入备注避免信息丢失', '', '', '', '', ''],
], [42, 30, 28, 14, 30, 14]);

sheetOf('逐行校验明细', [['Excel行', 'Code', '公司(主)', '关联抬头', '国家', '官网', '产品', 'Type', '联系人', '职位', '邮箱', '社媒/电话', '问题数', '问题明细', '处理建议']].concat(ROW_RESULTS.map(row => {
  const probs = PROBLEMS.filter(p => p.r === row.r);
  const advice = probs.some(p => p.level === 'ERROR') ? '需人工处理或跳过' : (probs.some(p => p.level === 'WARN') ? '确认后入库' : '可直接入库');
  return [row.r, row.code, row.main, row.related.join(' | '), row.country, row.website, row.products, row.type, row.contact, row.position, row.email, row.social, probs.length, probs.map(p => `[${p.level}]${p.msg}`).join('\n'), advice];
})), [7, 12, 30, 24, 10, 20, 22, 14, 20, 12, 24, 18, 6, 70, 16]);

sheetOf('公司数据完整性', [['公司(主)', '关联抬头', '行数', '联系人', '缺官网', '缺邮箱', '缺电话', '缺类型', '缺Code', '类型值', 'Code值', '缺失项', 'Excel行']].concat(Object.values(COMPANIES).sort((a, b) => b.rows.length - a.rows.length).map(c => [
  c.name, c.related.join(' | '), c.rows.length, c.contacts, c.hasWebsite ? '' : '缺', c.hasEmail ? '' : '缺', c.hasPhone ? '' : '缺', c.hasType ? '' : '缺', c.hasCode ? '' : '缺', c.typeList, c.codeList, c.missing.join('、'), c.rows.join(',')
])), [34, 26, 5, 6, 6, 6, 6, 6, 6, 22, 20, 26, 14]);

sheetOf('问题分类汇总', [['问题代码', '级别', '数量', '涉及字段', '示例']].concat(Object.values(SUMMARY).sort((a, b) => b.count - a.count).map(s => [s.code, s.level, s.count, s.field, s.examples.join('\n')])), [30, 8, 6, 14, 90]);

sheetOf('字段映射与DB建议', [
  ['PN.xlsx 列', '目标表.字段', '说明', '是否需要改库'],
  ['Code', 'crm_supplier.supplier_code', '如 202401 等，同公司多行共用；入库时每公司取首个，重复冲突时加序号', '否'],
  ['Bank info', 'crm_supplier.bank_info', '直接入库(≤1024)', '否'],
  ['公司情况备注', 'crm_supplier.remark', '直接入库(≤2048)', '否'],
  ['Bill to', 'crm_supplier.bill_to', '直接入库(≤1024)', '否'],
  ['Ship to', 'crm_supplier.ship_to', '直接入库(≤1024)', '否'],
  ['Company', 'crm_supplier.supplier_name', '多抬头取第一段为主名，其余入 remark 或 remark_second', '否'],
  ['Country/Area', 'crm_supplier.country', '如 CN/SZ/HK/UAE 等缩写，建议规范为中文(中国/中国香港/阿联酋...)便于列表展示', '否'],
  ['Website', 'crm_supplier.website', '缺 http:// 前缀的入库时补全，保证前端链接可跳转', '否'],
  ['ADD', 'crm_supplier.address', '直接入库(≤1024)', '否'],
  ['Products', 'crm_supplier.main_products', '自由文本，无法完全命中产品字典(CPU/GPU/SSD...)，入库保留原文+尽量规范化', '否(可选扩展字典)'],
  ['Type', 'crm_supplier.supplier_type', '按映射表转换(见总览第3条)', '否(Third-party建议新增字典值"服务商")'],
  ['Contact', 'crm_supplier_contact.contact_name', '"姓名|电话"拆分；整格电话需人工确认姓名', '否'],
  ['Position', 'crm_supplier_contact.post', 'post 字段 NOT NULL，空值填默认"销售"或空串', '否'],
  ['Email', 'crm_supplier_contact.email', '多邮箱拆分：主邮箱入 email，其余入 other_contact_first(≤200)', '否'],
  ['Social media', 'crm_supplier_contact.whatsapp/wechat/phone', '含 V:/WeChat→wechat；WA:/WhatsApp→whatsapp；Skype→other_contact_first；纯电话→phone(≤20)超长入 other_contact_first', '否(phone 20偏短，见下)'],
  ['对接点合作方向', 'crm_supplier.remark_second', '直接入库(≤1024)', '否'],
  ['', '', '', ''],
  ['可选DB优化建议', '', '', ''],
  ['ALTER TABLE crm_supplier_contact MODIFY COLUMN phone varchar(100) NULL COMMENT \'手机号\';', '超长电话(含多个号码/分机)不再截断', '可选'],
  ['INSERT INTO sys_dict_type(dict_name,dict_type,status) VALUES(\'供应商类型补充\',\'crm_supplier_type\',\'0\') 后加字典值 \'服务商\'', 'Third-party 财务/仓库/秘书公司展示与筛选', '可选'],
  ['', '', '', ''],
  ['前端展示兼容性结论', '', '', ''],
  ['列表字段全部来自 crm_supplier/crm_supplier_contact 现有列，插入后可直接展示；supplier_type 多值逗号分隔兼容；', '', '', ''],
  ['country 若存缩写(CN/SZ)也能显示但不够友好，建议规范中文；main_products 自由文本可显示，字典筛选不命中(可接受)；', '', '', ''],
  ['联系人 contact_name 填电话号码会显示为号码，建议整格电话行跳过联系人创建或姓名填"前台"。', '', '', ''],
], [22, 34, 70, 16]);

XLSX.writeFile(out, OUT_XLSX);
console.log('xlsx done:', OUT_XLSX);
