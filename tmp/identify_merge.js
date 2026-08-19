/* 同公司识别脚本: 主要词语一样 => 同一家
 * 规则: 名称归一化(小写+去标点空格) -> 剥公司后缀词(limited/科技/电子等) -> 得核心词
 *       核心词完全相同 或 一个包含另一个(短词>=4) => 视为同一家, 输出分组供确认
 */
const mysql = require('mysql2/promise');
const fs = require('fs');
const DB = { host: '42.194.240.191', port: 3306, user: 'root', password: 'eke123', database: 'crm_me', charset: 'utf8mb4' };

/* 归一化: 小写 -> 去全半角标点/空格 -> 剥公司后缀词(循环) */
const SUFFIX = ['limited', 'ltd', 'llc', 'l.l.c', 'inc', 'corp', 'corporation', 'co', 'company', 'holdings', 'holding',
  'group', 'international', 'pte', 'technologies', 'technology', 'electronics', 'systems', 'system', 'digital',
  'enterprises', 'enterprise', 'solutions', 'solution', 'computers', 'computer', 'trading', 'distribution',
  'integrated', 'semi', 'semiconductor', 'memory', 'storage', 'components', 'component', 'bv', 'fzco',
  'gmbh', 'plc', 'llp', 'sarl', 'private', 'company limited',
  '贸易', '公司', '有限公司', '集团', '科技', '电子', '技术', '信息', '数码', '实业', '国际', '股份', '有限'];
const norm = s => {
  let k = String(s || '').toLowerCase().replace(/[.,、，()（）\-_/|&·•\s"'`~!@#$%^*+=?<>:;【】\[\]{}]+/g, '');
  let changed = true;
  while (changed && k.length > 4) {
    changed = false;
    for (const suf of SUFFIX) {
      if (k.endsWith(suf) && k.length - suf.length >= 3) { k = k.slice(0, k.length - suf.length); changed = true; break; }
    }
  }
  return k;
};

async function main() {
  const c = await mysql.createConnection(DB);
  const [rows] = await c.query(`SELECT s.id, s.supplier_name, s.supplier_code, s.country, s.website, s.supplier_type,
    (SELECT COUNT(*) FROM crm_supplier_contact ct WHERE ct.supplier_id = s.id) contacts
    FROM crm_supplier s ORDER BY s.id`);
  // 分组: key 完全相同
  const groups = {};   // key -> [{id,name,...}]
  const order = [];
  rows.forEach(r => {
    const key = norm(r.supplier_name);
    if (!groups[key]) { groups[key] = []; order.push(key); }
    groups[key].push({ id: r.id, name: r.supplier_name, code: r.supplier_code, contacts: r.contacts });
  });
  const out = ['=== 完全同名(key相同)分组 ==='];
  order.forEach(key => {
    if (groups[key].length > 1) out.push(`KEY[${key}]: ` + groups[key].map(g => `#${g.id} ${g.name}(${g.code},联系人${g.contacts})`).join(' || '));
  });
  // 包含关系: 核心词 A 包含 B(短词>=4) 且差<=25 => 主要词语相同, 疑似同一家
  out.push('\n=== 主要词语相同(疑似同一家) ===');
  const keys = order.filter(k => groups[k][0] && groups[k][0].contacts !== undefined);
  const pairs = [];
  for (let i = 0; i < keys.length; i++) {
    for (let j = i + 1; j < keys.length; j++) {
      const a = keys[i], b = keys[j];
      const [long, short] = a.length >= b.length ? [a, b] : [b, a];
      if (long !== short && short.length >= 4 && long.includes(short) && long.length - short.length <= 25) {
        pairs.push(`${long} 含 ${short}: ` + groups[a].map(g => `#${g.id} ${g.name}(联系人${g.contacts})`).join(';') + ' vs ' + groups[b].map(g => `#${g.id} ${g.name}(联系人${g.contacts})`).join(';'));
      }
    }
  }
  pairs.sort((x, y) => x.length - y.length);
  out.push(...pairs);
  fs.writeFileSync('c:/Project/RuoYi-Vue/tmp/merge_identify.txt', out.join('\n'), 'utf8');
  console.log(out.join('\n'));
  await c.end();
}
main().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
