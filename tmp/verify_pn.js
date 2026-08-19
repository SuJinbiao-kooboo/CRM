/* 插入结果验证脚本: 抽查关键公司、联系人拆分、备份表 */
const mysql = require('mysql2/promise');
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

async function main() {
  const conn = await mysql.createConnection(DB);
  const q = async (sql, p = []) => { const [r] = await conn.query(sql, p); return r; };

  console.log('=== 1. 总量 ===');
  console.log('crm_supplier:', (await q('SELECT COUNT(*) c FROM crm_supplier'))[0].c);
  console.log('crm_supplier_contact:', (await q('SELECT COUNT(*) c FROM crm_supplier_contact'))[0].c);
  console.log('备份表 supplier:', (await q('SELECT COUNT(*) c FROM crm_supplier_bak_060813'))[0].c);
  console.log('备份表 contact:', (await q('SELECT COUNT(*) c FROM crm_supplier_contact_bak_060813'))[0].c);

  console.log('\n=== 2. 关键公司抽查(company/country/website/type/address/remark) ===');
  const names = ['深圳市极光通讯科技有限公司', 'CI Gulf Distribution FZCO', 'TOP GREAT TECHNOLOGY (HK) CO., LIMITED', 'Taurus', '远信', '宝通'];
  for (const n of names) {
    const rows = await q('SELECT id, supplier_code, supplier_name, supplier_type, country, website, LEFT(address,60) addr, LEFT(remark,80) remark FROM crm_supplier WHERE supplier_name LIKE ?', ['%' + n + '%']);
    rows.forEach(r => console.log(`#${r.id} [${r.supplier_code}] ${r.supplier_name} | type=${r.supplier_type} | country=${r.country} | web=${r.website} | addr=${r.addr} | remark=${r.remark}`));
  }

  console.log('\n=== 3. 联系人拆分规则验证 ===');
  console.log('name=前台(纯电话行):', (await q(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE contact_name='前台'`))[0].c);
  console.log('name=销售(纯邮箱行):', (await q(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE contact_name='销售'`))[0].c);
  console.log('wechat非空:', (await q(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE wechat<>''`))[0].c);
  console.log('whatsapp非空:', (await q(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE whatsapp<>''`))[0].c);
  console.log('other_contact_first非空:', (await q(`SELECT COUNT(*) c FROM crm_supplier_contact WHERE other_contact_first<>''`))[0].c);

  console.log('\n=== 4. 极光通讯联系人 ===');
  const pj = await q(`SELECT id FROM crm_supplier WHERE supplier_name LIKE '深圳市极光通讯%'`);
  if (pj.length) {
    const cs = await q(`SELECT contact_name, post, phone, email, whatsapp, wechat, other_contact_first FROM crm_supplier_contact WHERE supplier_id=?`, [pj[0].id]);
    cs.forEach(c => console.log(`${c.contact_name} | ${c.post} | ${c.phone} | ${c.email} | wa=${c.whatsapp} | wc=${c.wechat} | other=${c.other_contact_first}`));
  }

  console.log('\n=== 5. 挂靠成功公司联系人 ===');
  const gk = await q(`SELECT id, supplier_name FROM crm_supplier WHERE supplier_name IN ('CI Gulf Distribution FZCO','TOP GREAT TECHNOLOGY (HK) CO., LIMITED')`);
  for (const g of gk) {
    const cs = await q(`SELECT contact_name, email, other_contact_first FROM crm_supplier_contact WHERE supplier_id=?`, [g.id]);
    cs.forEach(c => console.log(`${g.supplier_name} -> ${c.contact_name} | ${c.email} | ${c.other_contact_first}`));
  }

  console.log('\n=== 6. 无company/无联系人检查 ===');
  console.log('supplier_name为空:', (await q(`SELECT COUNT(*) c FROM crm_supplier WHERE supplier_name=''`))[0].c);
  console.log('无联系人的供应商:', (await q(`SELECT COUNT(*) c FROM crm_supplier s WHERE NOT EXISTS (SELECT 1 FROM crm_supplier_contact c WHERE c.supplier_id=s.id)`))[0].c);

  console.log('\n=== 7. 新Code抽查 ===');
  const codes = await q(`SELECT supplier_code, supplier_name FROM crm_supplier WHERE supplier_code IN ('202401','202501','202403','PN1001')`);
  codes.forEach(r => console.log(`${r.supplier_code} -> ${r.supplier_name}`));

  await conn.end();
}
main().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
