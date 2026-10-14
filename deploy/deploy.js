/**
 * RuoYi-Vue 一键覆盖部署脚本（前端 dist + 可选后端 jar）
 * 用法（在 deploy 目录下）：
 *   node deploy.js                # 构建前端 + 上传 + 写 nginx 配置 + 安全重启后端 + 自动验证
 *   node deploy.js --skip-build   # 跳过本地前端构建（直接使用现有 ruoyi-ui/dist）
 *   node deploy.js --backend      # 额外上传本地 ruoyi-admin.jar（前端+后端全量覆盖）
 * 也可以直接双击 deploy.bat 一键运行。
 *
 * 安全重启策略（解决旧 nohup 进程残留导致端口冲突、systemd 反复重启的问题）：
 *   1. systemctl stop ruoyi-admin     —— 先停掉 systemd 服务（停止状态不会被自动拉起）
 *   2. pkill 清理所有残留 java 进程     —— 用 "ruoyi-admin[.]jar" 正则避免误杀执行命令的 shell 自身
 *   3. 确认 8081 端口已释放
 *   4. systemctl start ruoyi-admin    —— 由 systemd 拉起（Restart=always + enable，永久运行）
 *   5. 轮询 8081 就绪，并校验端口归属 == systemd MainPID
 */
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const { loadConfig, resolvePassword, connect, exec, uploadDir, httpCheck, ROOT } = require('./lib/common');

const cfg = loadConfig();
const ARGS = process.argv.slice(2);
const SKIP_BUILD = ARGS.includes('--skip-build');
const WITH_BACKEND = ARGS.includes('--backend');
const UI_DIR = path.join(ROOT, '..', 'ruoyi-ui');
const DIST_DIR = path.join(UI_DIR, 'dist');
const JAR_FILE = path.join(ROOT, '..', 'ruoyi-admin', 'target', 'ruoyi-admin.jar');

const S = cfg.server, P = cfg.paths, W = cfg.web;

/** [1] 本地构建前端（可 --skip-build 跳过） */
async function buildFrontend() {
  if (SKIP_BUILD) {
    console.log('[跳过] 已指定 --skip-build，直接使用现有 dist');
    return;
  }
  console.log('[1/7] 构建前端 (npm run build:prod) ...');
  await new Promise((resolve, reject) => {
    const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
    // Windows 下 .cmd 文件必须经 shell 启动（否则 spawn EINVAL）
    const p = spawn(npm, ['run', 'build:prod'], { cwd: UI_DIR, stdio: 'inherit', shell: process.platform === 'win32' });
    p.on('close', (code) => (code === 0 ? resolve() : reject(new Error('前端构建失败，退出码 ' + code))));
  });
  console.log('      构建完成: ' + DIST_DIR);
}

/** 生成 nginx 站点配置（路径/端口全部取自 config.json，可配置化） */
function nginxConf() {
  return [
    'server {',
    '    listen       ' + W.port + ';',
    '    server_name  ' + S.host + ';',
    '    root  ' + P.remoteDist + ';',
    '    index index.html;',
    '    client_max_body_size 100m;',
    '    location / { try_files $uri $uri/ /index.html; }',
    '    location ' + W.basePath + '/ {',
    '        proxy_pass http://127.0.0.1:' + W.backendPort + '/;',
    '        proxy_set_header Host $host;',
    '        proxy_set_header X-Real-IP $remote_addr;',
    '        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;',
    '        proxy_set_header X-Forwarded-Proto $scheme;',
    '        proxy_connect_timeout 60s;',
    // 500s：需大于AI接口最长耗时（后端sys.ai.timeout.ms默认8分钟=480s、前端axios 480s），避免代理先断开
    '        proxy_read_timeout 500s;',
    '        proxy_send_timeout 500s;',
    '    }',
    '}',
    '',
  ].join('\n');
}

async function main() {
  // 前置检查
  if (!fs.existsSync(DIST_DIR)) {
    console.error('缺少前端产物: ' + DIST_DIR + '，请先构建（去掉 --skip-build）');
    process.exit(1);
  }
  if (WITH_BACKEND && !fs.existsSync(JAR_FILE)) {
    console.error('缺少后端 jar: ' + JAR_FILE + '，请先在项目根目录执行 mvn package -DskipTests');
    process.exit(1);
  }

  await buildFrontend();

  // 连接服务器
  cfg.password = await resolvePassword(cfg);
  const conn = await connect(cfg);
  console.log('=== SSH 连接成功: ' + S.user + '@' + S.host + ':' + S.port + ' ===\n');

  // [2] 备份远程 dist 并上传新产物（覆盖部署）
  console.log('[2/7] 备份远程 dist → dist.bak，上传新前端产物');
  let r = await exec(conn, 'rm -rf ' + P.remoteDist + '.bak && mv ' + P.remoteDist + ' ' + P.remoteDist + '.bak');
  if (r.code !== 0) console.log('      (远程无旧 dist 可备份，首次部署)');
  const sftp = await new Promise((res, rej) => conn.sftp((e, s) => (e ? rej(e) : res(s))));
  // 先创建远程根目录（否则直接上传根下的文件会报 No such file）
  await new Promise((res, rej) => sftp.mkdir(P.remoteDist, { recursive: true }, (e) => (e ? rej(new Error('创建远程目录失败 ' + P.remoteDist + ': ' + e.message)) : res())));
  const n = await uploadDir(sftp, DIST_DIR, P.remoteDist);
  console.log('      已上传 ' + n + ' 个文件 → ' + P.remoteDist);

  // [3] 写入 nginx 配置并校验、重载
  console.log('\n[3/7] 写入 nginx 配置: ' + P.nginxConf);
  r = await exec(conn, "cat > " + P.nginxConf + " <<'NGINX_EOF'\n" + nginxConf() + "NGINX_EOF\n");
  if (r.code !== 0) throw new Error('写入 nginx 配置失败: ' + r.out);
  r = await exec(conn, 'nginx -t');
  if (r.code !== 0) throw new Error('nginx 配置校验失败:\n' + r.out);
  console.log('      nginx -t: OK');
  r = await exec(conn, 'systemctl reload nginx && echo "      nginx reload: OK"');
  console.log(r.out);

  // [4] 可选：上传后端 jar
  if (WITH_BACKEND) {
    console.log('\n[4/7] 上传后端 jar → ' + P.remoteJar);
    await new Promise((res, rej) => sftp.fastPut(JAR_FILE, P.remoteJar, (e) => (e ? rej(e) : res())));
    console.log('      jar 已上传 (' + (fs.statSync(JAR_FILE).size / 1024 / 1024).toFixed(1) + ' MB)');
  } else {
    console.log('\n[4/7] 跳过后端 jar（如需覆盖后端请加 --backend）');
  }

  // [5] 安全重启后端：先停服务 → 清理残留 → 确认端口释放 → 再启动
  console.log('\n[5/7] 安全重启后端服务 ' + P.serviceName);
  r = await exec(conn, 'systemctl stop ' + P.serviceName + ' 2>/dev/null; sleep 1; '
    + 'pkill -f "ruoyi-admin[.]jar" 2>/dev/null; sleep 2; '
    + 'pkill -9 -f "ruoyi-admin[.]jar" 2>/dev/null; '
    + 'ss -tlnp | grep ":' + W.backendPort + ' " || echo PORT_FREE');
  if (!r.out.includes('PORT_FREE')) throw new Error('端口 ' + W.backendPort + ' 仍被占用:\n' + r.out);
  console.log('      旧进程已清理，端口 ' + W.backendPort + ' 已释放');
  r = await exec(conn, 'systemctl start ' + P.serviceName + ' && echo started');
  if (r.code !== 0) throw new Error('启动服务失败: ' + r.out);

  // [6] 等待后端就绪（最长 120 秒）
  console.log('\n[6/7] 等待后端就绪（最长 120 秒）...');
  let up = false;
  for (let i = 0; i < 60; i++) {
    r = await exec(conn, 'curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 http://127.0.0.1:' + W.backendPort + '/captchaImage');
    if (r.out && r.out !== '000') { up = true; console.log('      后端就绪，耗时约 ' + (i * 2) + ' 秒'); break; }
    await new Promise((res) => setTimeout(res, 2000));
  }
  if (!up) throw new Error('后端 120 秒内未就绪，请检查: journalctl -u ' + P.serviceName);

  // 校验 8081 归属 systemd MainPID
  r = await exec(conn, 'MP=$(systemctl show -p MainPID --value ' + P.serviceName + '); ss -tlnp | grep ":' + W.backendPort + ' "; echo "MainPID=$MP"');
  const mp = (r.out.match(/MainPID=(\d+)/) || [])[1];
  const owner = (r.out.match(/pid=(\d+)/) || [])[1];
  console.log('      systemd MainPID=' + mp + ', 8081 归属 pid=' + owner + (mp === owner ? ' ✓ 端口独占正确' : ' ✗ 端口归属异常!'));

  // [7] 部署验证（服务器本地 + 公网）
  console.log('\n[7/7] 部署验证');
  const checks = [
    ['systemd 服务自启', 'systemctl is-enabled ' + P.serviceName],
    ['systemd 服务运行', 'systemctl is-active ' + P.serviceName],
    ['nginx 运行', 'systemctl is-active nginx'],
    ['防火墙 ' + W.port + ' 放行', 'firewall-cmd --list-ports | tr " " "\n" | grep -q "^' + W.port + '/tcp$" && echo OK || echo FAIL'],
    ['本地前端 HTTP', 'curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:' + W.port + '/'],
    ['本地代理 HTTP', 'curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:' + W.port + W.basePath + '/captchaImage'],
  ];
  let allOk = true;
  for (const [title, cmd] of checks) {
    r = await exec(conn, cmd);
    const ok = r.code === 0 && r.out && !r.out.includes('FAIL');
    if (!ok) allOk = false;
    console.log('  [' + (ok ? 'PASS' : 'FAIL') + '] ' + title + ' → ' + (r.out || '(无输出)'));
  }
  const base = 'http://' + S.host + ':' + W.port;
  const ext1 = await httpCheck(base + '/');
  const ext2 = await httpCheck(base + W.basePath + '/captchaImage');
  console.log('  [' + (ext1 === 200 ? 'PASS' : 'FAIL') + '] 公网前端 ' + base + '/ → HTTP ' + ext1);
  console.log('  [' + (ext2 === 200 ? 'PASS' : 'FAIL') + '] 公网代理 ' + base + W.basePath + '/captchaImage → HTTP ' + ext2);
  if (ext1 !== 200 || ext2 !== 200) allOk = false;

  conn.end();
  console.log('\n=== ' + (allOk ? '部署完成，全部检查通过 ✓' : '部署完成，但有检查项未通过，请查看上方 FAIL 项') + ' ===');
  process.exit(allOk ? 0 : 1);
}

main().catch((e) => {
  console.error('\n部署失败:', e.message);
  process.exit(1);
});
