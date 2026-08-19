/* 插入后修复脚本:
 * 1. 极光通讯 country 修正为"中国深圳"(Excel Country/Area列误填地址,已截断无用)
 * 2. Taurus 合并: #72 TAURUS EUROPE B.V. -> #71 Taurus Europe BV (同一家荷兰公司)
 *    - #71 website 修正为 http://www.tauruseu.com (best()误选货代站 lnafreight.nl)
 *    - #72 联系人改挂 #71, 官网信息并入 remark, 删除 #72
 */
const mysql = require('mysql2/promise');
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

async function main() {
  const conn = await mysql.createConnection(DB);

  /* 1. 极光通讯 country */
  const [r1] = await conn.query(`UPDATE crm_supplier SET country='中国深圳' WHERE id=69 AND supplier_name LIKE '深圳市极光通讯%'`);
  console.log('[1] 极光通讯 country 修正:', r1.affectedRows, '行');

  /* 2. Taurus 合并 */
  const [t] = await conn.query(`SELECT id, supplier_name, remark FROM crm_supplier WHERE id IN (71,72)`);
  const map = {};
  t.forEach(x => map[x.id] = x);
  if (map[71] && map[72]) {
    // 2.1 #72 联系人改挂 #71
    const [r2] = await conn.query(`UPDATE crm_supplier_contact SET supplier_id=71 WHERE supplier_id=72`);
    console.log('[2.1] 联系人迁移 #72->#71:', r2.affectedRows, '条');
    // 2.2 #71 website 修正 + remark 并入官网/抬头
    const newRemark = [map[71].remark, '官网: http://www.taurus.eu.com (三星原厂官网)', '关联抬头: TAURUS EUROPE B.V.'].filter(Boolean).join('\n');
    const [r3] = await conn.query(`UPDATE crm_supplier SET website='http://www.tauruseu.com', remark=? WHERE id=71`, [newRemark.slice(0, 2048)]);
    console.log('[2.2] #71 修正 website/remark:', r3.affectedRows, '行');
    // 2.3 删除 #72
    const [r4] = await conn.query(`DELETE FROM crm_supplier WHERE id=72`);
    console.log('[2.3] 删除 #72 TAURUS EUROPE B.V.:', r4.affectedRows, '行');
  } else {
    console.log('[2] 警告: Taurus 71/72 未找到, 跳过合并');
  }

  /* 3. 终检: 总量 + country/type 取值 */
  const [[s], [c], [dct], [dty]] = await Promise.all([
    conn.query(`SELECT COUNT(*) c FROM crm_supplier`),
    conn.query(`SELECT COUNT(*) c FROM crm_supplier_contact`),
    conn.query(`SELECT DISTINCT country FROM crm_supplier ORDER BY country`),
    conn.query(`SELECT DISTINCT supplier_type FROM crm_supplier ORDER BY supplier_type`)
  ]);
  console.log('[3] 终检: supplier =', s[0].c, ', contact =', c[0].c);
  console.log('    DISTINCT country:', dct.map(x => x.country || '(空)').join(' | '));
  console.log('    DISTINCT type:', dty.map(x => x.supplier_type || '(空)').join(' | '));

  /* 4. 联系人归属完整性 */
  const [orphan] = await conn.query(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE supplier_id NOT IN (SELECT id FROM crm_supplier)`);
  console.log('[4] 孤立联系人(无主供应商):', orphan[0].c);
  await conn.end();
}
main().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
