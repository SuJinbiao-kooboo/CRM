/**
 * RuoYi-Vue 部署完成度一键检测脚本
 * 用法（在 deploy 目录下）：node verify.js   或直接双击 verify.bat
 * 检查项：SSH 连通 → java 进程/8081 端口归属 → systemd 服务状态 → nginx 状态与配置
 *        → 防火墙 80 → 服务器本地 HTTP → 公网 HTTP，最后输出 PASS/FAIL 汇总
 */
const { loadConfig, resolvePassword, connect, exec, httpCheck } = require('./lib/common');

const cfg = loadConfig();
const S = cfg.server, P = cfg.paths, W = cfg.web;

async function main() {
  cfg.password = await resolvePassword(cfg);
  const conn = await connect(cfg);
  console.log('=== SSH 连接成功: ' + S.user + '@' + S.host + ':' + S.port + ' ===\n');

  // 后端进程与端口归属
  let r = await exec(conn, 'ps -eo pid,lstart,cmd | grep "ruoyi-admin[.]jar" | grep -v grep');
  console.log('### java 进程\n' + (r.out || '(无 java 进程!)') + '\n');
  r = await exec(conn, 'MP=$(systemctl show -p MainPID --value ' + P.serviceName + ' 2>/dev/null); echo "MainPID=$MP"; ss -tlnp | grep ":' + W.backendPort + ' " || echo PORT_EMPTY');
  console.log('### 8081 端口归属\n' + r.out + '\n');

  // systemd / nginx / 防火墙 / 配置
  r = await exec(conn, 'echo "nginx: $(systemctl is-enabled nginx 2>/dev/null)/$(systemctl is-active nginx 2>/dev/null)"; echo "' + P.serviceName + ': $(systemctl is-enabled ' + P.serviceName + ' 2>/dev/null)/$(systemctl is-active ' + P.serviceName + ' 2>/dev/null)"; echo "firewall80: $(firewall-cmd --list-ports 2>/dev/null | grep -o "80/tcp" | head -1 || echo 未放行)"');
  console.log('### 服务状态\n' + r.out + '\n');
  r = await exec(conn, 'nginx -t 2>&1; echo "---"; ls -l ' + P.remoteDist + '/index.html 2>/dev/null || echo "dist缺失"');
  console.log('### nginx 配置与前端产物\n' + r.out + '\n');

  // 检查项汇总
  console.log('### 检查结果汇总');
  const checks = [
    ['后端进程存在', () => exec(conn, 'pgrep -f "ruoyi-admin[.]jar" > /dev/null && echo OK || echo FAIL')],
    ['8081 归属 systemd MainPID', async () => {
      const rr = await exec(conn, 'MP=$(systemctl show -p MainPID --value ' + P.serviceName + ' 2>/dev/null); ss -tlnp | grep ":' + W.backendPort + ' " | grep -q "pid=$MP" && echo OK || echo FAIL');
      return rr;
    }],
    [P.serviceName + ' 已启用', () => exec(conn, 'systemctl is-enabled ' + P.serviceName + ' 2>/dev/null | grep -q enabled && echo OK || echo FAIL')],
    [P.serviceName + ' 运行中', () => exec(conn, 'systemctl is-active ' + P.serviceName + ' | grep -q active && echo OK || echo FAIL')],
    ['nginx 运行中', () => exec(conn, 'systemctl is-active nginx | grep -q active && echo OK || echo FAIL')],
    ['防火墙 ' + W.port + ' 放行', () => exec(conn, 'firewall-cmd --list-ports | tr " " "\n" | grep -q "^' + W.port + '/tcp$" && echo OK || echo FAIL')],
    ['前端产物存在', () => exec(conn, 'test -f ' + P.remoteDist + '/index.html && echo OK || echo FAIL')],
    ['本地前端 HTTP', () => exec(conn, 'curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:' + W.port + '/')],
    ['本地代理 HTTP', () => exec(conn, 'curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:' + W.port + W.basePath + '/captchaImage')],
  ];
  const results = [];
  for (const [title, fn] of checks) {
    try {
      const rr = await fn();
      results.push([title, rr.out && rr.out !== 'FAIL' ? rr.out : 'FAIL']);
    } catch (e) {
      results.push([title, 'FAIL: ' + e.message]);
    }
  }

  // 公网访问检测（从本机发起）
  const base = 'http://' + S.host + ':' + W.port;
  const ext1 = await httpCheck(base + '/');
  const ext2 = await httpCheck(base + W.basePath + '/captchaImage');
  results.push(['公网前端 ' + base + '/', ext1 === 200 ? 'HTTP ' + ext1 : 'FAIL (HTTP ' + ext1 + ')']);
  results.push(['公网代理 /prod-api', ext2 === 200 ? 'HTTP ' + ext2 : 'FAIL (HTTP ' + ext2 + ')']);

  let allOk = true;
  for (const [title, val] of results) {
    const ok = val === 'OK' || val === '200' || /^HTTP 200/.test(val);
    if (!ok) allOk = false;
    console.log('  [' + (ok ? 'PASS' : 'FAIL') + '] ' + title + ' → ' + val);
  }

  conn.end();
  console.log('\n=== ' + (allOk ? '全部检查通过，部署正常 ✓' : '存在未通过项，请检查上方 FAIL 明细') + ' ===');
  process.exit(allOk ? 0 : 1);
}

main().catch((e) => {
  console.error('\n检测失败:', e.message);
  process.exit(1);
});
