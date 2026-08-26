/**
 * 部署/检测公共工具：配置加载、密码解析、SSH 连接、远程命令、目录上传
 * 说明：
 *  - 服务器密码不写死在代码里，按 环境变量 SSH_PASS > config.local.json 的 password 字段 > 交互输入 依次取值
 *  - config.local.json 已被 .gitignore 忽略，可放心存放本机私有密码
 */
const fs = require('fs');
const path = require('path');
const { Client } = require('ssh2');

const ROOT = path.join(__dirname, '..');

/** 深合并两个对象（local 覆盖 base） */
function deepMerge(base, local) {
  const out = Array.isArray(base) ? base.slice() : { ...base };
  for (const k of Object.keys(local || {})) {
    out[k] = base[k] && typeof base[k] === 'object' && !Array.isArray(base[k])
      ? deepMerge(base[k], local[k])
      : local[k];
  }
  return out;
}

/** 加载部署配置：config.json（公共配置）+ config.local.json（本机私有配置） */
function loadConfig() {
  const base = JSON.parse(fs.readFileSync(path.join(ROOT, 'config.json'), 'utf8'));
  const localFile = path.join(ROOT, 'config.local.json');
  let local = {};
  if (fs.existsSync(localFile)) {
    try { local = JSON.parse(fs.readFileSync(localFile, 'utf8')); } catch (e) { /* 忽略损坏的本地配置 */ }
  }
  return deepMerge(base, local);
}

/** 隐藏输入提示密码（输入不回显） */
function promptPassword(question = '请输入服务器密码: ') {
  return new Promise((resolve) => {
    const readline = require('readline');
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    const origWrite = rl._writeToOutput.bind(rl);
    process.stdout.write(question);
    rl._writeToOutput = () => origWrite('*');
    rl.question('', (answer) => {
      process.stdout.write('\n');
      rl.close();
      resolve(answer);
    });
  });
}

/** 解析密码：环境变量 SSH_PASS > config.local.json > 交互输入 */
async function resolvePassword(cfg) {
  if (process.env.SSH_PASS) return process.env.SSH_PASS;
  if (cfg.password) return String(cfg.password);
  return promptPassword();
}

/** 建立 SSH 连接 */
function connect(cfg) {
  return new Promise((resolve, reject) => {
    const conn = new Client();
    conn.on('ready', () => resolve(conn))
      .on('error', (e) => reject(new Error('SSH 连接失败: ' + e.message)));
    conn.connect({
      host: cfg.server.host,
      port: Number(cfg.server.port || 22),
      username: cfg.server.user,
      password: cfg.password,
      readyTimeout: 20000,
    });
  });
}

/** 执行远程命令，返回 { code, out }（stdout+stderr 合并） */
function exec(conn, cmd) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return reject(err);
      let out = '';
      stream.on('close', (code) => resolve({ code, out: out.trim() }));
      stream.on('data', (d) => { out += d.toString(); });
      stream.stderr.on('data', (d) => { out += d.toString(); });
    });
  });
}

/** 递归上传本地目录到远程目录（自动创建目录、覆盖同名文件），返回上传文件数 */
async function uploadDir(sftp, localDir, remoteDir) {
  const items = fs.readdirSync(localDir, { withFileTypes: true });
  let count = 0;
  for (const it of items) {
    const lp = path.join(localDir, it.name);
    const rp = remoteDir + '/' + it.name;
    if (it.isDirectory()) {
      await new Promise((res, rej) => sftp.mkdir(rp, { recursive: true }, (e) => (e ? rej(new Error('创建目录失败 ' + rp + ': ' + e.message)) : res())));
      count += await uploadDir(sftp, lp, rp);
    } else if (it.isFile()) {
      await new Promise((res, rej) => sftp.fastPut(lp, rp, (e) => (e ? rej(new Error('上传失败 ' + lp + ' → ' + rp + ': ' + e.message)) : res())));
      count++;
    }
  }
  return count;
}

/** 从本机请求公网地址，返回 HTTP 状态码（失败返回 0） */
function httpCheck(url, timeoutMs = 10000) {
  return new Promise((resolve) => {
    const mod = url.startsWith('https') ? require('https') : require('http');
    const req = mod.get(url, { timeout: timeoutMs }, (res) => {
      res.resume();
      resolve(res.statusCode || 0);
    });
    req.on('error', () => resolve(0));
    req.on('timeout', () => { req.destroy(); resolve(0); });
  });
}

module.exports = { loadConfig, resolvePassword, connect, exec, uploadDir, httpCheck, ROOT };
