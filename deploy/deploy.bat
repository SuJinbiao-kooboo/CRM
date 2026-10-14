@echo off
chcp 65001 >nul
cd /d "%~dp0"
title RuoYi 一键部署
where node >nul 2>nul
if errorlevel 1 (
  echo [错误] 未检测到 Node.js，请先安装 Node.js 并加入 PATH
  pause
  exit /b 1
)
if not exist node_modules (
  echo [提示] 首次运行，正在安装依赖 ssh2 ...
  call npm install --no-audit --no-fund
  if errorlevel 1 (
    echo [错误] 依赖安装失败
    pause
    exit /b 1
  )
)
echo.
echo ============================================
echo   RuoYi-Vue 一键覆盖部署
echo   参数: [--skip-build] 跳过前端构建 [--backend] 同时覆盖后端jar
echo   密码: 优先读环境变量SSH_PASS，其次config.local.json，否则交互输入
echo ============================================
echo.
node deploy.js %*
pause
