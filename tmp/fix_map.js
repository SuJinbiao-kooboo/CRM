/* 修正映射遗漏: HK/SZ、GZ/HK、IT Partner */
const mysql = require('mysql2/promise');
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

async function main() {
  const c = await mysql.createConnection(DB);
  const [a] = await c.query("UPDATE crm_supplier SET country='中国香港/中国深圳' WHERE id=126");
  const [b] = await c.query("UPDATE crm_supplier SET country='中国广州/中国香港' WHERE id=134");
  const [d] = await c.query("UPDATE crm_supplier SET supplier_type='贸易商' WHERE id=104");
  console.log('fixed:', a.affectedRows, b.affectedRows, d.affectedRows);
  await c.end();
}
main().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
