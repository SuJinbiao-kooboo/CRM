<#
 微信网关接口 PowerShell 测试脚本（用真实 curl.exe，非 IWR 别名）
 运行:  powershell -ExecutionPolicy Bypass -File test-wechat.ps1
 前置:  ① ruoyi-admin 已启动、能登录; ② 微信 4.1.15.13 已登录、主窗口打开。
>
# 改这里
$BASE   = "http://localhost:8081"
$USER   = "admin"
$PASS   = "admin123"

# ---- 1) 登录拿 token -----------------------------------------------
Write-Host "== 登录 $USER ..." -ForegroundColor Cyan
$login = & curl.exe -s -H "Content-Type: application/json" -d "{`"username`":`"$USER`",`"password`":`"$PASS`"}" "$BASE/login"
$loginObj = $login | ConvertFrom-Json
$token = $loginObj.token
if (-not $token) {
    Write-Host "登录失败，可能需要验证码或账号密码不对。返回：" -ForegroundColor Yellow
    Write-Host $login
    exit 1
}
Write-Host ("token: " + $token.Substring(0, [Math]::Min(24, $token.Length)) + "..." ) -ForegroundColor Green
$AUTH = "Authorization: Bearer $token"

# ---- 2) 主动拉取：某时间之后的所有会话 -------------------------------
Write-Host "`n== 主动拉取(时间之后,全部会话) ..." -ForegroundColor Cyan
curl.exe -s -G -H $AUTH `
    --data-urlencode "since=2026-10-05 12:00:00" `
    --data-urlencode "media=true" `
    "$BASE/wechat/messages"

# ---- 3) 主动拉取：指定会话 --------------------------------------------
Write-Host "`n== 主动拉取(指定会话:文件传输助手) ..." -ForegroundColor Cyan
curl.exe -s -G -H $AUTH `
    --data-urlencode "since=2026-10-05 00:00:00" `
    --data-urlencode "chat=文件传输助手" `
    "$BASE/wechat/messages"

# ---- 4) 启动实时监听 -------------------------------------------------
Write-Host "`n== 启动监听(带媒体下载) ..." -ForegroundColor Cyan
curl.exe -s -X POST -H $AUTH -H "Content-Type: application/json" `
    -d "media=true" "$BASE/wechat/listen/start"

# ---- 5) 查询监听状态 -------------------------------------------------
Write-Host "`n== 监听状态 ..." -ForegroundColor Cyan
curl.exe -s -H $AUTH "$BASE/wechat/listen/status"

Write-Host "`n(等待几秒，真实微信里发条消息后再次查询状态可看到 processed 增长)"
Start-Sleep -Seconds 3
Write-Host "`n== 监听状态(3秒后) ..." -ForegroundColor Cyan
curl.exe -s -H $AUTH "$BASE/wechat/listen/status"

# ---- 6) 停止监听 ------------------------------------------------------
Write-Host "`n== 停止监听 ..." -ForegroundColor Cyan
curl.exe -s -X POST -H $AUTH -H "Content-Type: application/json" `
    -d "{}" "$BASE/wechat/listen/stop"

Write-Host "`n完成。" -ForegroundColor Green
