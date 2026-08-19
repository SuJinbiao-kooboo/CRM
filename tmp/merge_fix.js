/* 合并+错列修复脚本
 * A. 合并 8 组完全同名公司(用户确认): 联系人迁主记录、字段取非空最长、副记录删除
 *    G1 SYNO(#78+#79+#80) G2 DATA JCE(#82+#83) G3 TwinMOS(#101+#102) G4 LINKVIEW(#134+#135)
 *    G5 SANFAN(#140+#142) G6 Ex-Channel(#174+#175) G7 AMAX(#163+#165) G8 NX(#74+#75)
 * B. 错列修复(用户确认的12条清单 + 串列清理):
 *    #81 CENTURY country=新加坡/web=centurytech.sg  #129 华储 country=中国广州/address补/bank清
 *    #131 珑京 country 去地址  #150 Tier country=荷兰  #177 Powerleader ZH=珠海  #185 朴赛规范化
 *    #106 BOSSION type清->remark2/bank清(E2K串列)  #102 TwinMOS country=中国台湾/web/prod 补
 *    NX/DATA JCE products 清"合作:"段  NX country 去重规范化
 */
const mysql = require('mysql2/promise');
const fs = require('fs');
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

const FIELDS = ['supplier_code', 'supplier_name', 'supplier_type', 'brands', 'country', 'address', 'website',
  'main_products', 'bank_info', 'bill_to', 'ship_to', 'remark', 'remark_second'];

/* 组定义: main=主记录, subs=副记录(被删除), rename=主名修正 */
const GROUPS = [
  { main: 78, subs: [79, 80] },
  { main: 82, subs: [83] },
  { main: 102, subs: [101] },
  { main: 134, subs: [135] },
  { main: 140, subs: [142] },
  { main: 174, subs: [175] },
  { main: 165, subs: [163] },
  { main: 74, subs: [75] }
];
/* 合并时清理 products 中"合作:"串列段的组 */
const CLEAN_PROD = new Set([82, 74]);
/* 合并时 country 去重规范化的组 */
const CLEAN_COUNTRY = new Set([74]);

/* 去"合作[：:]xxx"前缀段(第一个换行前), 返回 {prod, coop} */
function splitCoop(v) {
  const s = String(v || '').trim();
  const m = s.match(/^合作\s*[：:]\s*([^\n]*)/);
  if (!m) return { prod: s, coop: '' };
  const rest = s.slice(m[0].length).replace(/^\n+/, '').trim();
  return { prod: rest, coop: m[1].trim() };
}
/* country 去重规范化: 按 / 和 , 拆开后去重 */
function normCountry(v) {
  const seen = [];
  String(v || '').split(/[,\/]/).map(x => x.trim()).filter(Boolean).forEach(x => { if (!seen.includes(x)) seen.push(x); });
  return seen.join('/');
}

async function main() {
  const conn = await mysql.createConnection(DB);
  const log = [];
  const push = s => { log.push(s); console.log(s); };

  await conn.beginTransaction();
  try {
    /* ---------- A. 合并 8 组 ---------- */
    for (const g of GROUPS) {
      const [rows] = await conn.query(`SELECT * FROM crm_supplier WHERE id IN (?)`, [[g.main, ...g.subs]]);
      const map = {};
      rows.forEach(r => map[r.id] = r);
      const main = map[g.main];
      if (!main) { push(`[跳过] 主记录 #${g.main} 不存在`); continue; }
      const merged = {};
      FIELDS.forEach(f => { merged[f] = main[f] || ''; });
      // 副记录: 字段取非空最长 + remark 追加关联抬头
      for (const sid of g.subs) {
        const sub = map[sid];
        if (!sub) continue;
        for (const f of FIELDS) {
          const sv = String(sub[f] || '').trim();
          if (sv && sv.length > String(merged[f] || '').length) merged[f] = sv;
        }
        if (sub.remark && !merged.remark.includes(sub.remark)) merged.remark = [merged.remark, '关联抬头: ' + sub.supplier_name].filter(Boolean).join('\n');
        // 联系人迁移
        const [mv] = await conn.query(`UPDATE crm_supplier_contact SET supplier_id=? WHERE supplier_id=?`, [g.main, sid]);
        push(`[合并] #${sid} ${sub.supplier_name} -> #${g.main} ${main.supplier_name} (联系人迁移 ${mv.affectedRows} 条)`);
      }
      // products 清理"合作:"段
      if (CLEAN_PROD.has(g.main)) {
        const { prod, coop } = splitCoop(merged.main_products);
        merged.main_products = prod;
        if (coop && !merged.remark_second.includes(coop)) merged.remark_second = [merged.remark_second, '合作方向: ' + coop].filter(Boolean).join('\n');
      }
      if (CLEAN_COUNTRY.has(g.main)) merged.country = normCountry(merged.country);
      const upd = {};
      FIELDS.forEach(f => { if (merged[f] !== (main[f] || '')) upd[f] = merged[f]; });
      if (Object.keys(upd).length) {
        await conn.query(`UPDATE crm_supplier SET ? WHERE id=?`, [upd, g.main]);
        push(`[合并] #${g.main} 字段更新: ${Object.keys(upd).join(',')}`);
      }
      for (const sid of g.subs) {
        const [del] = await conn.query(`DELETE FROM crm_supplier WHERE id=?`, [sid]);
        push(`[合并] 删除 #${sid}: ${del.affectedRows} 条`);
      }
    }

    /* ---------- B. 错列修复 ---------- */
    const fixes = [
      { id: 81, set: { country: '新加坡', website: 'http://www.centurytech.sg' }, why: 'Country列为新加坡地址, Website列错填公司名' },
      { id: 129, set: { country: '中国广州', address: '广州市天河区天河路518号地中海2001室', bank_info: '', main_products: '' }, why: 'Country列为地址, Bank信息为YANDE串列, Products为合作方向' },
      { id: 131, set: { country: '中国上海/中国北京/中国深圳' }, why: 'Country列混入地址(地址已并入address)' },
      { id: 150, set: { country: '荷兰' }, why: 'Country列混入地址' },
      { id: 177, set: { country: '中国深圳/中国广州/中国珠海' }, why: 'ZH未映射(实为珠海)' },
      { id: 185, set: { country: '中国上海/中国深圳' }, why: '分隔符规范化' },
      { id: 106, set: { supplier_type: '', bank_info: '' }, why: 'Type为合作方向, Bank为E2K串列' }
    ];
    for (const f of fixes) {
      const [r] = await conn.query(`UPDATE crm_supplier SET ? WHERE id=?`, [f.set, f.id]);
      push(`[修复] #${f.id} ${r.affectedRows}行: ${f.why}`);
    }
    // #106 remark_second 追加合作方向(原type)
    const [b1] = await conn.query(`SELECT remark_second FROM crm_supplier WHERE id=106`);
    const b2 = [b1[0].remark_second, '合作方向: HDD/三星'].filter(Boolean).join('\n');
    await conn.query(`UPDATE crm_supplier SET remark_second=? WHERE id=106`, [b2.slice(0, 1024)]);
    push('[修复] #106 remark_second 追加合作方向');
    // #129 remark_second 追加合作方向(原products)
    const [h1] = await conn.query(`SELECT remark_second FROM crm_supplier WHERE id=129`);
    const h2 = [h1[0].remark_second, '合作方向: hdd'].filter(Boolean).join('\n');
    await conn.query(`UPDATE crm_supplier SET remark_second=? WHERE id=129`, [h2.slice(0, 1024)]);
    push('[修复] #129 remark_second 追加合作方向');
    // #102 TwinMOS 补字段
    await conn.query(`UPDATE crm_supplier SET country='中国台湾', website='https://www.twinmos.com/', main_products='memory modules， SSD' WHERE id=102`);
    push('[修复] #102 TwinMOS country=中国台湾(原TW), web=补twinmos.com, products=补memory modules, SSD');
    // #131 address 并入龙华东环地址
    const [l1] = await conn.query(`SELECT address FROM crm_supplier WHERE id=131`);
    const la = l1[0].address || '';
    if (!la.includes('龙华东环二路')) {
      const na = la ? la + '\n龙华东环二路65号中佳创意园A6栋708' : '龙华东环二路65号中佳创意园A6栋708';
      await conn.query(`UPDATE crm_supplier SET address=? WHERE id=131`, [na.slice(0, 1024)]);
      push('[修复] #131 address 并入龙华东环地址');
    }

    await conn.commit();
    push('事务提交成功 ✓');
  } catch (e) {
    await conn.rollback();
    push('[错误] 回滚: ' + e.message);
    throw e;
  }

  /* ---------- C. 验证 ---------- */
  const [[s], [ct], [dup], [orphan]] = await Promise.all([
    conn.query(`SELECT COUNT(*) c FROM crm_supplier`),
    conn.query(`SELECT COUNT(*) c FROM crm_supplier_contact`),
    conn.query(`SELECT supplier_name, COUNT(*) c FROM crm_supplier GROUP BY supplier_name HAVING c>1 LIMIT 20`),
    conn.query(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE supplier_id NOT IN (SELECT id FROM crm_supplier)`)
  ]);
  push(`[验证] supplier=${s[0].c}, contact=${ct[0].c}, 孤立联系人=${orphan[0].c}`);
  push(dup[0].length ? `[警告] 重名: ${dup[0].map(d => d.supplier_name + 'x' + d.c).join(';')}` : '[验证] 无重名 ✓');
  // 抽验合并组
  const [chk] = await conn.query(`SELECT id, supplier_name, country, website, LEFT(main_products,50) prod, LEFT(remark_second,50) r2 FROM crm_supplier WHERE id IN (74,82,102,134,140,174,165,78,81,129,131,150,177,185,106)`);
  chk.forEach(x => push(`#${x.id}|${x.supplier_name}|c=${x.country}|w=${x.website}|p=${x.prod}|r2=${x.r2}`));
  fs.writeFileSync('c:/Project/RuoYi-Vue/tmp/merge_fix_log.txt', log.join('\n'), 'utf8');
  await conn.end();
}
main().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
