@echo off
chcp 65001 >nul
cd /d "%~dp0"
title RuoYi 部署检测
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
echo   RuoYi-Vue 部署完成度检测
echo ============================================
echo.
node verify.js %*
pause
