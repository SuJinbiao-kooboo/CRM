/* 排查: 异常 country / type 对应公司清单 */
const mysql = require('mysql2/promise');
const fs = require('fs');
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

async function main() {
  const c = await mysql.createConnection(DB);
  const out = [];
  const goodC = ['中国','中国上海','中国北京','中国台湾','中国深圳','中国香港','新加坡','阿联酋','荷兰','美国','伊朗','以色列','奥地利','波兰','奥地利/波兰/卡塔尔','沙特阿拉伯','澳大利亚','欧洲及中东','中国上海/中国北京/中国深圳','中国深圳/中国香港','中国香港/中国广州','新加坡/迪拜/印度/印度尼西亚/土耳其','中国香港/新加坡/吉隆坡','中东','欧洲','英国','中国上海/中国深圳','中国深圳/中国广州'];
  const [r] = await c.query('SELECT id,supplier_name,country,LEFT(website,50) web FROM crm_supplier WHERE country<>""');
  r.forEach(x => { if (!goodC.includes(x.country)) out.push('#' + x.id + '|' + x.supplier_name + '|COUNTRY=' + x.country + '|web=' + (x.web || '')); });
  const goodT = ['授权代理商','贸易商','集成商','库存商','终端','服务商','授权代理商,贸易商'];
  const [t] = await c.query('SELECT id,supplier_name,supplier_type FROM crm_supplier');
  t.forEach(x => { if (x.supplier_type && !goodT.includes(x.supplier_type)) out.push('TYPE#' + x.id + '|' + x.supplier_name + '|' + x.supplier_type); });
  fs.writeFileSync('c:/Project/RuoYi-Vue/tmp/fix_check.txt', out.join('\n'), 'utf8');
  console.log('lines:', out.length);
  await c.end();
}
main().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
