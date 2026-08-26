/* PN.xlsx(新) -> crm_supplier / crm_supplier_contact 数据导入脚本
 * 流程: 1)备份两张表 _bak_060813  2)删除与Excel重名的现有供应商(清单人工核对)  3)空公司行按Code组/邮箱域名挂靠  4)插入公司+联系人  5)日志输出
 * 规则:
 *  - Company 多抬头取第一段为主名, 其余入 remark
 *  - Type 枚举映射到字典 crm_supplier_type
 *  - Country 缩写规范为中文
 *  - Contact "姓名|电话"拆分; 整格电话->姓名"前台"; 整格邮箱->姓名"销售"
 *  - Email 多邮箱: 主邮箱入 email, 其余入 other_contact_first
 *  - Social media: V:/wechat/微信->wechat; WA:/whatsapp->whatsapp; Skype->other_contact_first; 电话->phone(<=20)超长入other_contact_first
 *  - 所有字段按DB上限截断并记警告
 */
const mysql = require('mysql2/promise');
const XLSX = require('xlsx');
const fs = require('fs');

const SRC = 'C:/Users/亦钟人/Desktop/PN.xlsx';
const LOG = 'c:/Project/RuoYi-Vue/tmp/insert_pn_log.txt';
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

/* 人工核对后的删除清单（库中 crm_supplier.id） */
const DELETE_IDS = [2, 3, 4, 5, 9, 11, 12, 15, 16, 18, 20, 22, 24, 25, 27, 28, 29, 31, 32, 33, 38, 40, 41, 46, 47, 55, 57];

/* 字段上限 */
const LIM = {
  company: 200, type: 50, products: 256, country: 100, address: 1024, website: 200,
  remark: 2048, remark2: 1024, bank: 1024, billTo: 1024, shipTo: 1024, code: 50,
  email: 100, phone: 20, whatsapp: 50, wechat: 50, contactName: 100, post: 100, otherContact: 200
};
const cut = (s, n, warn) => { const v = String(s || '').trim(); if (v.length > n) { warn.push(`${v.slice(0, 30)}... 长度${v.length}->截断${n}`); return v.slice(0, n); } return v; };

/* 映射 */
const TYPE_MAP = {
  'IT DIST': '贸易商', 'IT SHOP': '贸易商', 'IT Broker': '贸易商', 'IT PARTNER': '贸易商', 'IT PARTNER ': '贸易商',
  'IT AD/franchise': '授权代理商', 'IT MFG': '贸易商', 'IT MFG+AD': '授权代理商,贸易商', 'IT Stockiest': '库存商',
  'SI-System integrator': '集成商', 'Solution provider': '集成商', 'Others': '贸易商', 'Third-party': '服务商'
};
const COUNTRY_MAP = {
  'CN': '中国', 'CHINA': '中国', '中国': '中国', 'TW': '中国台湾', '台湾': '中国台湾', 'SH': '中国上海', '上海': '中国上海',
  'SZ': '中国深圳', 'Shenzhen': '中国深圳', '深圳': '中国深圳', 'GZ': '中国广州', 'HK': '中国香港', 'Hong Kong': '中国香港',
  'HK/GZ': '中国香港/中国广州', 'HK/TW/SZ': '中国香港/中国台湾/中国深圳', 'SZ, HK': '中国深圳/中国香港', 'SZ, HK ': '中国深圳/中国香港',
  'SG': '新加坡', 'UAE': '阿联酋', 'NL': '荷兰', '荷兰': '荷兰', 'USA': '美国', '美国': '美国', 'United States (West)': '美国',
  'IR': '伊朗', 'IL': '以色列', '以色列': '以色列', 'AT': '奥地利', 'PL': '波兰', 'AT/PL/QT': '奥地利/波兰/卡塔尔',
  'Saudi Arabia': '沙特阿拉伯', 'Australia': '澳大利亚', '欧洲及中东': '欧洲及中东', '北京': '中国北京', '武汉': '中国武汉',
  'SG，dubai, india, indonesia, turkey': '新加坡/迪拜/印度/印度尼西亚/土耳其', '上海/北京/深圳': '中国上海/中国北京/中国深圳',
  'Hong Kong · Singapore · Kuala Lumpur': '中国香港/新加坡/吉隆坡', 'China': '中国', 'Hongkong': '中国香港', 'England': '英国',
  '美国 US': '美国', '中国香港': '中国香港', 'China(上海)': '中国上海', 'Europe': '欧洲', 'Middle East': '中东'
};
const phoneRe = /[\d()+\-－/ ]{5,}/;
const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

async function main() {
  const conn = await mysql.createConnection(DB);
  const log = [];
  const warn = [];
  const push = s => { log.push(s); console.log(s); };

  /* ---------- 1. 读取 Excel ---------- */
  const wb = XLSX.readFile(SRC);
  const ws = wb.Sheets[wb.SheetNames[0]];
  const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
  const H = rows[0].map((h, i) => String(h || '').trim());
  const idx = {};
  H.forEach((h, i) => { if (h) idx[h] = i; });
  const cell = (row, ci) => (ci === undefined ? '' : String((row && row[ci]) || '').trim());
  const dataRows = [];
  for (let r = 1; r < rows.length; r++) {
    const row = rows[r];
    const nonEmpty = (Array.isArray(row) ? row : []).filter(c => String(c || '').trim() !== '').length;
    if (nonEmpty > 0) dataRows.push({ r: r + 1, cells: row });
  }
  push(`[1] 读取Excel完成: 数据行 ${dataRows.length} 行`);

  /* ---------- 2. 解析行 -> 公司聚合 ---------- */
  const companies = {};   // key=主名
  const orphanRows = [];  // 公司为空的行
  const splitComp = v => { const p = String(v || '').split(/[|\n/]/).map(s => s.trim()).filter(Boolean); return { main: p[0] || '', related: p.slice(1) }; };

  dataRows.forEach(({ r, cells }) => {
    const company = cell(cells, idx['Company']);
    const { main, related } = splitComp(company);
    const row = {
      r, code: cell(cells, idx['Code']), bank: cell(cells, idx['Bank info']), compNote: cell(cells, idx['公司情况备注']),
      billTo: cell(cells, idx['Bill to']), shipTo: cell(cells, idx['Ship to']), country: cell(cells, idx['Country/Area']),
      website: cell(cells, idx['Website']), add: cell(cells, idx['ADD']), products: cell(cells, idx['Products']),
      type: cell(cells, idx['Type']), contact: cell(cells, idx['Contact']), position: cell(cells, idx['Position']),
      email: cell(cells, idx['Email']), social: cell(cells, idx['Social media(wechat/whatsapp)']),
      coopDir: cell(cells, idx['对接点合作方向']), related
    };
    if (!main) { orphanRows.push(row); return; }
    if (!companies[main]) companies[main] = { name: main, rows: [], related: [], codes: new Set(), countries: new Set(), types: new Set() };
    const c = companies[main];
    c.rows.push(row);
    related.forEach(x => { if (!c.related.includes(x)) c.related.push(x); });
    if (row.code) c.codes.add(row.code);
    if (row.country) c.countries.add(row.country);
    if (row.type) c.types.add(row.type);
  });
  push(`[2] 公司聚合完成: ${Object.keys(companies).length} 家, 空公司行 ${orphanRows.length} 行`);

  /* ---------- 3. 空公司行挂靠: ①同Code组唯一公司 ②邮箱域名唯一匹配 ---------- */
  const companyRowsMap = {};
  Object.entries(companies).forEach(([k, c]) => { c.rows.forEach(r => { companyRowsMap[r.r] = k; }); });
  const codeGroups = {};
  dataRows.forEach(row => { if (row.code) { (codeGroups[row.code] = codeGroups[row.code] || []).push(row.r); } });
  const attached = [];
  orphanRows.forEach(row => {
    let target = '';
    // ① Code 组内唯一公司
    if (row.code && codeGroups[row.code]) {
      const names = [...new Set(codeGroups[row.code].map(r => companyRowsMap[r]).filter(Boolean))];
      if (names.length === 1) target = names[0];
    }
    // ② 邮箱域名匹配
    if (!target && row.email) {
      const dom = row.email.split(/[|;；\s]+/).find(e => emailRe.test(e.trim()));
      if (dom) {
        const d = dom.trim().split('@')[1].toLowerCase();
        const hit = Object.keys(companies).filter(k => companies[k].rows.some(r => r.email.toLowerCase().includes('@' + d)));
        if (hit.length === 1) target = hit[0];
      }
    }
    if (target) {
      companies[target].rows.push(row);
      attached.push(`行${row.r}(${row.contact || row.email || '无联系人'}) -> 挂靠 ${target}`);
    } else {
      push(`[挂靠失败] 行${row.r} 无公司且无法挂靠: ${(row.contact || row.email || '全空').slice(0, 40)}`);
    }
  });
  if (attached.length) push(`[3] 空公司行挂靠 ${attached.length} 行: ${attached.join('; ')}`);
  else push('[3] 无空公司行可挂靠');

  /* ---------- 4. 备份 ---------- */
  push('[4] 备份表 crm_supplier/crm_supplier_contact -> *_bak_060813 ...');
  for (const t of ['crm_supplier', 'crm_supplier_contact']) {
    await conn.query(`DROP TABLE IF EXISTS ${t}_bak_060813`);
    await conn.query(`CREATE TABLE ${t}_bak_060813 AS SELECT * FROM ${t}`);
    const [cnt] = await conn.query(`SELECT COUNT(*) c FROM ${t}_bak_060813`);
    push(`    备份 ${t}_bak_060813 完成, 行数=${cnt[0].c}`);
  }

  /* ---------- 5. 删除重名（事务） ---------- */
  await conn.beginTransaction();
  try {
    const [del] = await conn.query(`SELECT id, supplier_name, supplier_code FROM crm_supplier WHERE id IN (?)`, [DELETE_IDS]);
    push(`[5] 将删除 ${del.length} 家已存在供应商: ${del.map(d => `${d.id}:${d.supplier_name}(${d.supplier_code || ''})`).join('; ')}`);
    await conn.query(`DELETE FROM crm_supplier_contact WHERE supplier_id IN (?)`, [DELETE_IDS]);
    const [c1] = await conn.query(`DELETE FROM crm_supplier WHERE id IN (?)`, [DELETE_IDS]);
    push(`    删除完成: 供应商 ${c1.affectedRows} 条(含其联系人)`);

    /* ---------- 6. 插入公司 ---------- */
    const seenCodes = new Set();
    let compInserted = 0;
    for (const [key, c] of Object.entries(companies)) {
      const first = c.rows[0];
      const warns = [];
      // 选最完整值: 非空中最长
      const best = sel => c.rows.map(r => r[sel]).filter(Boolean).sort((a, b) => b.length - a.length)[0] || '';
      let code = [...c.codes][0] || '';
      if (!code) code = `PN${1000 + compInserted}`;
      let codeSuffix = 2;
      while (seenCodes.has(code)) { code = code + '-' + codeSuffix++; }
      seenCodes.add(code);
      // remark = 公司情况备注 + 关联抬头
      let remark = best('compNote');
      if (c.related.length) {
        const rel = '关联抬头: ' + c.related.join(' | ');
        remark = remark ? remark + '\n' + rel : rel;
      }
      // country 规范化
      const rawCountries = [...c.countries];
      const country = rawCountries.map(x => COUNTRY_MAP[x] || x).filter(Boolean).join(',');
      // website 补协议
      let website = best('website');
      if (website && !/^https?:\/\//i.test(website)) website = 'http://' + website;
      // type
      const rawType = [...c.types][0] || '';
      const type = TYPE_MAP[rawType] || rawType;
      const products = best('products');

      const sql = `INSERT INTO crm_supplier
        (supplier_code, supplier_name, supplier_type, brands, country, address, website, main_products,
         bank_info, bill_to, ship_to, remark, remark_second, status, create_by, create_time, update_time)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,'admin',NOW(),NOW())`;
      const vals = [
        cut(code, LIM.code, warns), cut(c.name, LIM.company, warns), cut(type, LIM.type, warns), '',
        cut(country, LIM.country, warns), cut(best('add'), LIM.address, warns), cut(website, LIM.website, warns),
        cut(products, LIM.products, warns), cut(best('bank'), LIM.bank, warns), cut(best('billTo'), LIM.billTo, warns),
        cut(best('shipTo'), LIM.shipTo, warns), cut(remark, LIM.remark, warns), cut(best('coopDir'), LIM.remark2, warns)
      ];
      const [res] = await conn.query(sql, vals);
      const supplierId = res.insertId;
      compInserted++;
      if (warns.length) push(`    [截断] 公司"${c.name}": ${warns.join('; ')}`);

      /* ---------- 7. 插入联系人 ---------- */
      for (const r of c.rows) {
        if (!r.contact && !r.email && !r.social) continue; // 无联系方式的行跳过
        const cwarns = [];
        let contactName = '', phone = '', whatsapp = '', wechat = '', email = '', other = '';
        const contact = r.contact;
        if (contact) {
          const parts = contact.split('|').map(s => s.trim()).filter(Boolean);
          let name = parts[0] || '';
          // 整格电话 -> 前台; 整格邮箱 -> 销售
          if (/^[\d\s()+\-－/]{5,}$/.test(name)) { phone = name; name = '前台'; }
          else if (emailRe.test(name)) { email = name; name = '销售'; }
          // 第二段若为电话 -> phone
          if (parts[1] && /^[\d\s()+\-－/]{5,}$/.test(parts[1])) { if (!phone) phone = parts[1]; }
          contactName = name;
        }
        // Email 拆分
        if (r.email) {
          const es = r.email.split(/[|;；\n]+/).map(s => s.trim()).filter(Boolean);
          if (!email && es.length) email = es[0];
          const rest = es.slice(email ? 1 : 0).filter(e => e && e !== email);
          if (rest.length) other = (other ? other + ' | ' : '') + rest.join(' | ');
        }
        // Social media 解析
        if (r.social) {
          const s = r.social;
          const v = s.match(/(?:V:|wechat|微信|WeChat)\s*[:：]?\s*([A-Za-z0-9_.\-@]{1,40})/i);
          const w = s.match(/(?:WA:|whatsapp|WhatsApp)\s*[:：]?\s*([A-Za-z0-9_.\-@+\s]{1,30})/i);
          const sk = s.match(/skype\s*[:：]?\s*([A-Za-z0-9_.\-@]{1,40})/i);
          if (v && v[1]) wechat = v[1];
          if (w && w[1]) whatsapp = w[1].trim();
          if (sk && sk[1]) other = (other ? other + ' | ' : '') + 'Skype:' + sk[1];
          if (!v && !w && !sk) {
            const m = s.match(/[\d()+\-－ ]{5,}/);
            if (m && m[0].trim().length >= 5) { if (!phone) phone = m[0].trim(); }
            else other = (other ? other + ' | ' : '') + s;
          }
        }
        // phone 超长 -> other
        if (phone.length > LIM.phone) { other = (other ? other + ' | ' : '') + phone; phone = ''; }
        const sql2 = `INSERT INTO crm_supplier_contact
          (supplier_id, contact_name, post, position, phone, email, whatsapp, wechat, qq, other_contact_first, other_contact_second,
           remark_first, remark_second, is_primary, create_by, create_time, update_time)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,'admin',NOW(),NOW())`;
        await conn.query(sql2, [
          supplierId, cut(contactName || '未知', LIM.contactName, cwarns), cut(r.position || '', LIM.post, cwarns), '',
          cut(phone, LIM.phone, cwarns), cut(email, LIM.email, cwarns), cut(whatsapp, LIM.whatsapp, cwarns),
          cut(wechat, LIM.wechat, cwarns), '', cut(other, LIM.otherContact, cwarns), '', '', '', 0
        ]);
        if (cwarns.length) push(`    [截断] 联系人"${contactName}"(公司${c.name}): ${cwarns.join('; ')}`);
      }
    }
    push(`[6-7] 插入完成: 公司 ${compInserted} 家`);
    await conn.commit();
  } catch (e) {
    await conn.rollback();
    push('[错误] 事务回滚: ' + e.message);
    throw e;
  }

  /* ---------- 8. 验证 ---------- */
  const [[sup], [ct], [dup]] = await Promise.all([
    conn.query(`SELECT COUNT(*) c FROM crm_supplier`),
    conn.query(`SELECT COUNT(*) c FROM crm_supplier_contact`),
    conn.query(`SELECT supplier_name, COUNT(*) c FROM crm_supplier GROUP BY supplier_name HAVING c>1 LIMIT 20`)
  ]);
  push(`[8] 验证: crm_supplier 现有 ${sup[0].c} 条, crm_supplier_contact 现有 ${ct[0].c} 条`);
  if (dup[0].length) push(`    [警告] 存在重名供应商: ${dup[0].map(d => d.supplier_name + 'x' + d.c).join('; ')}`);
  else push('    无重名供应商 ✓');
  await conn.end();

  fs.writeFileSync(LOG, log.join('\n'), 'utf8');
  console.log('log written:', LOG);
}

main().catch(e => { console.error('FAILED:', e); process.exit(1); });
