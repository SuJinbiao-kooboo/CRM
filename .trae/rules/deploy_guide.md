# RuoYi-Vue 生产部署指南

> 适用项目：RuoYi-Vue（若依前后端分离版 + CRM 模块）
> 服务器：个人云服务器（CentOS Stream 9）

---

## 1. 服务器信息

| 项目 | 值 |
| --- | --- |
| 公网 IP | `114.132.93.127` |
| SSH 端口 | `22` |
| 登录账号 | `root` |
| 登录密码 | ⚠️ 见 `deploy/config.local.json` 或部署时交互输入（**严禁写入本文件/代码/文档**） |
| 操作系统 | CentOS Stream 9 |
| Java | 1.8.0_202（路径 `/opt/soft/jdk8/jdk1.8.0_202`） |
| 反向代理 | Nginx 1.20.1（systemd 托管，开机自启） |
| 后端 | Spring Boot 3.9.0 分支（端口 8081，systemd 托管） |
| Redis | 本机 6379 |

## 2. 部署目录结构（服务器）

```text
/opt/soft/crm/
├── ruoyi-admin.jar          # 后端可执行 jar
├── ruoyi-ui/
│   ├── dist/                # 前端产物（Nginx 站点根目录）
│   └── dist.bak/            # 上次部署的前端备份（自动生成，用于回滚）
├── nohup.out                # 后端日志（systemd 追加写入）
└── logs/                    # 若依运行日志
```

## 3. 一键部署 / 一键检测（推荐）

项目根目录下 `deploy/` 文件夹内已提供一键脚本（基于 Node.js + ssh2，密码不写死在代码里）：

| 入口 | 说明 |
| --- | --- |
| 双击 `deploy.bat` 或 `node deploy.js` | **一键覆盖部署**：构建前端 → 备份并上传 dist → 写 Nginx 配置 → 安全重启后端 → 自动验证 |
| 双击 `verify.bat` 或 `node verify.js` | **一键检测**：检查进程/端口/systemd/nginx/防火墙/公网访问，输出 PASS/FAIL 汇总 |

### 3.1 常用命令

```bash
# 部署（默认会先构建前端）
node deploy.js

# 跳过本地构建，直接上传现有 ruoyi-ui/dist
node deploy.js --skip-build

# 前端 + 后端 jar 全量覆盖（需先 mvn package 生成 jar）
node deploy.js --backend
# 或组合：node deploy.js --skip-build --backend
```

### 3.2 凭据配置（三选一，按优先级）

1. 环境变量：`set SSH_PASS=你的密码`（当前终端有效）
2. 本机私有配置：`deploy/config.local.json` 的 `password` 字段（已 gitignore，不会提交）
3. 交互输入：运行脚本时按提示输入（输入不回显）

> 服务器地址、端口、路径等公共配置在 `deploy/config.json`，修改服务器后只需改这一个文件。

### 3.3 首次使用

```bash
cd deploy
npm install        # 安装 ssh2 依赖（deploy.bat 检测到缺依赖也会自动执行）
```

### 3.4 部署流程（脚本自动完成）

1. `npm run build:prod` 构建前端（可 `--skip-build` 跳过）
2. 远程 `rm -rf dist.bak && mv dist dist.bak` 备份旧产物
3. SFTP 递归上传新 dist（196 个文件左右，约 7.3MB）
4. 写入 `/etc/nginx/conf.d/crm.conf`，`nginx -t` 校验后 `systemctl reload nginx`
5. **安全重启后端**（解决旧进程残留/端口冲突）：
   - `systemctl stop ruoyi-admin` 先停服务
   - `pkill -f "ruoyi-admin[.]jar"` 清理所有残留 java 进程（含历史 nohup 进程）
   - 确认 8081 端口释放 → `systemctl start ruoyi-admin`
6. 轮询 8081 就绪，校验端口归属 = systemd MainPID
7. 验证：systemd 自启/运行、nginx、防火墙 80、本地 HTTP、公网 HTTP

## 4. Nginx 配置（/etc/nginx/conf.d/crm.conf）

```nginx
server {
    listen       80;
    server_name  114.132.93.127;
    root  /opt/soft/crm/ruoyi-ui/dist;
    index index.html;
    client_max_body_size 100m;
    location / { try_files $uri $uri/ /index.html; }
    location /prod-api/ {
        proxy_pass http://127.0.0.1:8081/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

要点：
- `try_files ... /index.html` 支持 history 路由（刷新页面不 404）
- `/prod-api/` 前缀剥离后转发 8081（对应前端 `VUE_APP_BASE_API=/prod-api`）
- 超时 300s：AI 录入等长耗时接口（DeepSeek 响应可达 130s）
- `client_max_body_size 100m`：批量导入 Excel 不受限

## 5. 后端 systemd 服务（/etc/systemd/system/ruoyi-admin.service）

```ini
[Unit]
Description=RuoYi Admin (CRM) Backend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/soft/crm
ExecStart=/opt/soft/jdk8/jdk1.8.0_202/bin/java -jar /opt/soft/crm/ruoyi-admin.jar
Restart=always
RestartSec=5
StandardOutput=append:/opt/soft/crm/nohup.out
StandardError=append:/opt/soft/crm/nohup.out

[Install]
WantedBy=multi-user.target
```

**永久运行保障**：`Restart=always`（进程挂掉自动拉起）+ `systemctl enable`（开机自启），Nginx 同样已 `enable`。

常用运维命令：

```bash
systemctl status ruoyi-admin          # 查看后端状态
journalctl -u ruoyi-admin -n 100      # 查看后端日志
systemctl restart ruoyi-admin         # 手动重启
tail -f /opt/soft/crm/nohup.out       # 跟踪应用日志
```

## 6. 防火墙

```bash
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --reload
```

已放行端口：`80`（前端）、`22`（SSH）、`8081`（后端，生产可考虑仅保留本机回环）。

## 7. 回滚

```bash
# 前端回滚（恢复到上次部署版本）
cd /opt/soft/crm/ruoyi-ui
rm -rf dist && mv dist.bak dist
systemctl reload nginx

# 后端回滚：恢复旧 jar（需提前备份，部署脚本 --backend 模式建议先 cp ruoyi-admin.jar ruoyi-admin.jar.bak）
systemctl restart ruoyi-admin
```

## 8. 常见问题排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 前端能开但接口 404/502 | 后端未启动或 8081 被旧进程占用 | `verify.bat` 看 8081 归属；重跑 `deploy.js` 安全重启 |
| `ruoyi-admin` 状态 `activating` 反复重启 | 8081 被历史 nohup java 进程占用，新进程绑不上端口 | 部署脚本的"安全重启"流程即为此设计 |
| 公网打不开但服务器本地 200 | 云安全组/防火墙未放行 80 | 检查 `firewall-cmd --list-ports` 与云控制台安全组 |
| 刷新页面 404 | nginx 缺 `try_files` 配置 | 检查 crm.conf 是否被覆盖 |
| AI 接口超时 | 后端响应超过代理超时时间 | 确认 `proxy_read_timeout 300s` 生效 |

## 9. 部署验证清单（verify.bat 自动完成）

- [x] SSH 连通
- [x] 后端 java 进程存在且 8081 归属 systemd MainPID
- [x] `ruoyi-admin` enabled + active（永久运行）
- [x] nginx enabled + active，配置语法正确
- [x] 防火墙 80/tcp 放行
- [x] 前端产物存在
- [x] 本地 HTTP：`/` 与 `/prod-api/captchaImage` 均 200
- [x] 公网 HTTP：`http://114.132.93.127/` 与代理均 200

> 本次部署结果（2026-08-24）：11/11 项全部 PASS，前端地址 `http://114.132.93.127/`。
> 2026-08-24 已用 `deploy.js --backend` 将后端 jar 覆盖部署为最新版（含供应商编码查询等改动），核验：服务器 jar 时间戳 2026-08-24、jar 内 Mapper 含 `supplier_code like` 条件。
