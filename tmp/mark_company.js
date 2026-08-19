/* PN.xlsx 同公司标记脚本 v2
 * 规则: 公司主名(取 | / 换行 第一段) -> 繁简转简体 -> 小写去标点 -> 剥公司后缀词 -> 核心词
 *       核心词相同或互为包含(短词>=4) => 同一家 => 每行最前面加"公司名称"列, 写统一标记名
 * 附加: PREMERGE 人工别名映射(中英文对照/抬头/域名/同Code佐证), OVERRIDE_NAME 标记名覆盖,
 *       KNOWN_ORPHAN 空公司行按佐证挂靠
 * 输出: tmp/PN_公司名称标记.xlsx (Sheet1=标记后数据, Sheet2=同公司分组说明)
 */
const XLSX = require('xlsx');

const SRC = 'C:/Users/亦钟人/Desktop/PN.xlsx';
const OUT = 'c:/Project/RuoYi-Vue/tmp/PN_公司名称标记.xlsx';

/* ---------- 繁简转换(公司名常用字) ---------- */
const TS = { '訊':'讯','創':'创','業':'业','實':'实','達':'达','廣':'广','電':'电','龍':'龙','灣':'湾','門':'门','團':'团','儲':'储','豐':'丰','鴻':'鸿','啟':'启','陽':'阳','華':'华','國':'国','億':'亿','萬':'万','眾':'众','優':'优','恆':'恒','傑':'杰','強':'强','導':'导','興':'兴','學':'学','審':'审','計':'计','術':'术','務':'务','聞':'闻','聯':'联','聖':'圣','風':'风','飛':'飞','臺':'台','觀':'观','騰':'腾','匯':'汇','誠':'诚','譽':'誉','數':'数','據':'据','網':'网','質':'质','銀':'银','錢':'钱','產':'产','長':'长','東':'东','兩':'两','師':'师','發':'发','羅':'罗','維':'维','榮':'荣','輝':'辉','漢':'汉','澤':'泽','靈':'灵','領':'领','額':'额','顯':'显','體':'体','為':'为','於':'于','後':'后','從':'从','應':'应','壓':'压','環':'环','現':'现','圖':'图','圓':'圆','場':'场','報':'报','專':'专','層':'层','屬':'属','屆':'届','島':'岛','幣':'币','庫':'库','廠':'厂','廳':'厅','衛':'卫','複':'复','復':'复','種':'种','稱':'称','積':'积','穩':'稳','係':'系','繫':'系','監':'监','覽':'览','規':'规','視':'视','覺':'觉','設':'设','話':'话','語':'语','認':'认','課':'课','調':'调','譜':'谱','購':'购','費':'费','賓':'宾','資':'资','贊':'赞','贈':'赠','賣':'卖','買':'买','貨':'货','責':'责','貴':'贵','賢':'贤','賽':'赛','贏':'赢','亞':'亚','機':'机','權':'权','溫':'温','準':'准','飾':'饰','驅':'驱','驗':'验','麗':'丽','舉':'举','與':'与','舊':'旧','蓋':'盖','適':'适','選':'选','遠':'远','遷':'迁','邏':'逻','遜':'逊','遞':'递','嶼':'屿','嶺':'岭','巒':'峦','巖':'岩','寶':'宝','貿':'贸','運':'运','進':'进','邊':'边','週':'周','禮':'礼','稅':'税','檔':'档','橋':'桥','樓':'楼','橫':'横','樹':'树','檢':'检','標':'标','欄':'栏','點':'点','齊':'齐','齡':'龄','齒':'齿','龔':'龚','龕':'龛','嚴':'严','儀':'仪' };
const toSimp = s => String(s).split('').map(ch => TS[ch] || ch).join('');

/* ---------- 公司后缀词(剥除后为核心词) ---------- */
const SUFFIX = ['limited', 'ltd', 'llc', 'l.l.c', 'inc', 'corp', 'corporation', 'co', 'company', 'holdings', 'holding',
  'group', 'international', 'pte', 'technologies', 'technology', 'electronics', 'systems', 'system', 'digital',
  'enterprises', 'enterprise', 'solutions', 'solution', 'computers', 'computer', 'trading', 'distribution',
  'integrated', 'semi', 'semiconductor', 'memory', 'storage', 'components', 'component', 'bv', 'fzco',
  'gmbh', 'plc', 'llp', 'sarl', 'private', 'company limited',
  '贸易', '公司', '有限公司', '集团', '科技', '电子', '技术', '信息', '数码', '实业', '国际', '股份', '有限'];
const norm = s => {
  let k = toSimp(s).toLowerCase().replace(/[.,、，()（）\-_/|&·•\s"'`~!@#$%^*+=?<>:;【】\[\]{}]+/g, '');
  let changed = true;
  while (changed && k.length > 4) {
    changed = false;
    for (const suf of SUFFIX) {
      if (k.endsWith(suf) && k.length - suf.length >= 3) { k = k.slice(0, k.length - suf.length); changed = true; break; }
    }
  }
  return k;
};

/* 人工别名映射(归一化key -> 目标key), 佐证来源见注释
 * 注意: 中英混合名中英文后缀若卡在中文前无法剥除, key 需保留该英文部分(如行371的 inc) */
const PREMERGE = {
  'honckonghonchinternationallimited香港鸿广州云嘉': 'honckonghonchinternationallimited香港鸿启', // 行110错别字变体+同邮箱域名hkhonch.com
  '苏州超集': 'amaxchina',                                        // 苏州超集信息科技 = AMAX(行314英文抬头AMAX Information Technologies (Suzhou))
  '易成讯创香港': '杭州易成讯创',                                   // 香港易成 = 杭州易成(同Code 202628)
  '广达实业香港': '融核上海',                                      // 廣達實業(香港)=Grandtec=融核(银行户名+superphi域名)
  'computersolutionincanchor美国关联': 'anchorinnovativeitsolutionsfzc', // Computer Solution INC = Anchor美国关联(行371抬头明示; 目标键为行370实际键: 'llc'剥离后以'fzc'结尾, 阻塞'solutions'剥离)
  '鑫昊翔': 'hongkongnetzone',                                    // 鑫昊翔 = HONGKONG NETZONE(行201斜杠别名)
  '广东宝德自强计算机': 'powerleader',                             // 广东宝德自强=宝德/Powerleader(行345抬头powerleader+联系人李科同电话)
  '上海赞禾英泰': 'jointharvestinternation',                       // 赞禾英泰 = JOINT-HARVEST(行208邮箱域名joint-harvest.com)
};
/* 组标记名覆盖(组root key -> 统一标记名) */
const OVERRIDE_NAME = {
  '芯通': '芯通科技有限公司',
  'twinmos': 'TwinMOS Technologies',
  'amax': 'AMAX',
};
/* 空公司行按佐证挂靠(行号 -> 标记名) */
const KNOWN_ORPHAN = {
  28: '深圳市极光通讯科技有限公司',            // Rannie 极光关联(极光组内)
  111: 'HONCKONG HONCH INTERNATIONAL LIMITED香港鴻啟國際有限公司', // 吴美欣 同Code 202511
  112: 'HONCKONG HONCH INTERNATIONAL LIMITED香港鴻啟國際有限公司', // 邱美玉老板 同Code 202511
  196: 'CI Gulf Distribution FZCO',          // finance@cigulf.com 域名佐证
  224: 'INFINIARC (Virtual Reality Solutions Inc.旗下)', // meshal@infiniarc.com 域名佐证
  225: 'INFINIARC (Virtual Reality Solutions Inc.旗下)', // lokesh.sivakumar@infiniarc.com
  226: 'INFINIARC (Virtual Reality Solutions Inc.旗下)', // ahmed.ragab@infiniarc.com
  227: 'INFINIARC (Virtual Reality Solutions Inc.旗下)', // Mohammed.alamer@infiniarc.com
  228: 'INFINIARC (Virtual Reality Solutions Inc.旗下)', // m.alobaydullah@infiniarc.com
  234: 'INFINIARC (Virtual Reality Solutions Inc.旗下)', // abdulaziz.alzarah@infiniarc.com
  304: 'TOP GREAT TECHNOLOGY (HK) CO., LIMITED', // Sunny Huang 挂靠TOP GREAT
  364: '融核（上海）科技有限公司',              // Tom tom@grandtec.net.cn 域名佐证(广达体系)
};

/* ---------- 读取 ---------- */
const wb = XLSX.readFile(SRC);
const ws = wb.Sheets[wb.SheetNames[0]];
const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
const H = rows[0].map((h, i) => String(h || '').trim());
const idx = {};
H.forEach((h, i) => { if (h) idx[h] = i; });
function cell(row, ci) { return ci === undefined ? '' : String((row && row[ci]) || '').trim(); }

const dataRows = [];
for (let r = 1; r < rows.length; r++) {
  const row = rows[r] || [];
  const nonEmpty = row.filter(c => String(c || '').trim() !== '').length;
  if (nonEmpty > 0) dataRows.push({ r: r + 1, cells: row, company: cell(row, idx['Company']) });
}
console.log('数据行:', dataRows.length);

/* ---------- 主名提取 + 归一化分组 ---------- */
const groups = new Map();   // key -> {name, rows:[], members:[], manual:bool, weak:bool}
dataRows.forEach(d => {
  const parts = d.company.split(/[|\n/]/).map(s => s.trim()).filter(Boolean);
  d.main = parts[0] || '';
  if (!d.main) return; // 空公司行
  let key = norm(d.main);
  d.manual = !!PREMERGE[key];
  if (d.manual) key = PREMERGE[key];
  d.key = key;
  if (!groups.has(key)) groups.set(key, { name: '', rows: [], members: [], manual: false, weak: false });
  const g = groups.get(key);
  g.rows.push(d.r);
  if (!g.members.includes(d.main)) g.members.push(d.main);
  if (d.manual) g.manual = true;
});

/* 弱组合并: key 互为包含(短>=4, 差<=25) => 合并(标记 weak) */
const keys = [...groups.keys()];
const parent = {};
keys.forEach(k => parent[k] = k);
const find = k => parent[k] === k ? k : (parent[k] = find(parent[k]));
for (let i = 0; i < keys.length; i++) {
  for (let j = i + 1; j < keys.length; j++) {
    const a = keys[i], b = keys[j];
    const [long, short] = a.length >= b.length ? [a, b] : [b, a];
    if (long !== short && short.length >= 4 && long.includes(short) && long.length - short.length <= 25) {
      groups.get(long).weak = true;
      parent[find(a)] = find(b);
    }
  }
}
/* 重新聚合 */
const finalGroups = new Map();
keys.forEach(k => {
  const root = find(k);
  if (!finalGroups.has(root)) finalGroups.set(root, { name: '', rows: [], members: [], manual: false, weak: false });
  const fg = finalGroups.get(root);
  const g = groups.get(k);
  fg.rows.push(...g.rows);
  fg.members.push(...g.members);
  if (g.manual) fg.manual = true;
  if (g.weak) fg.weak = true;
});
/* 标记名: OVERRIDE 优先, 否则组内最长主名(等长取先出现) */
finalGroups.forEach((g, root) => {
  if (OVERRIDE_NAME[root]) { g.name = OVERRIDE_NAME[root]; return; }
  const uniq = [...new Set(g.members)];
  uniq.sort((a, b) => b.length - a.length || 0);
  g.name = uniq[0] || '';
  g.rows.sort((a, b) => a - b);
});

/* 行 -> 标记名 */
const rowName = new Map();
finalGroups.forEach(g => g.rows.forEach(r => rowName.set(r, g.name)));
Object.entries(KNOWN_ORPHAN).forEach(([r, n]) => rowName.set(Number(r), n));

/* ---------- 输出 Sheet1: 按源行号写入(保留空行位置, 输出行号=源Excel行号), 最前加"公司名称"列 ---------- */
const outWs = XLSX.utils.aoa_to_sheet([]);
XLSX.utils.sheet_add_aoa(outWs, [['公司名称', ...H]], { origin: 'A1' });
let marked = 0;
dataRows.forEach(d => {
  const name = rowName.get(d.r) || '';
  if (name) marked++;
  XLSX.utils.sheet_add_aoa(outWs, [[name, ...(Array.isArray(d.cells) ? d.cells : [])]], { origin: 'A' + d.r });
});
outWs['!cols'] = [{ wch: 48 }, ...H.map(() => ({ wch: 18 }))];

/* ---------- 输出 Sheet2: 同公司分组说明 ---------- */
const basisOf = g => g.manual ? '人工识别(中英文对照/抬头/域名/同Code佐证)'
  : (g.weak ? '主要词语相同(疑似同名,建议核实)' : '主要词语相同');
const noteRows = [['标记公司名称', '行数', 'Excel行号', '原公司名(主名列表)', '识别依据']];
let multi = 0;
finalGroups.forEach(g => {
  if (g.rows.length <= 1) return;
  multi++;
  noteRows.push([g.name, g.rows.length, g.rows.join(','), g.members.join(' | '), basisOf(g)]);
});
noteRows.push([]);
noteRows.push(['【空公司行按佐证挂靠】', '', '', '', '']);
Object.entries(KNOWN_ORPHAN).forEach(([r, n]) => noteRows.push([n, 1, r, '(无公司名)', '邮箱域名/同Code佐证']));
const noteWs = XLSX.utils.aoa_to_sheet(noteRows);
noteWs['!cols'] = [{ wch: 48 }, { wch: 6 }, { wch: 42 }, { wch: 75 }, { wch: 34 }];

const outWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(outWb, outWs, 'PN标记');
XLSX.utils.book_append_sheet(outWb, noteWs, '同公司分组说明');
XLSX.writeFile(outWb, OUT);
console.log('输出:', OUT);
console.log('标记行数:', marked, '/', dataRows.length);
console.log('多行分组数:', multi);
