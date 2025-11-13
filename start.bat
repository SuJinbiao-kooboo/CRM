@echo off
echo ========================================
echo    IT分销公司CRM系统启动脚本
echo ========================================
echo.

echo 1. 检查Java环境...
java -version
if %errorlevel% neq 0 (
    echo 错误：未找到Java环境，请先安装Java 8或更高版本
    pause
    exit /b 1
)

echo.
echo 2. 检查Node.js环境...
node -v
if %errorlevel% neq 0 (
    echo 错误：未找到Node.js环境，请先安装Node.js
    pause
    exit /b 1
)

echo.
echo 3. 检查MySQL服务...
sc query mysql >nul 2>&1
if %errorlevel% neq 0 (
    echo 警告：MySQL服务未启动，请确保MySQL服务正在运行
)

echo.
echo 4. 启动后端服务...
cd Back-Server
start "CRM后端服务" cmd /k "mvn spring-boot:run"
cd ..

echo.
echo 5. 等待后端服务启动...
timeout /t 10 /nobreak

echo.
echo 6. 安装前端依赖...
cd Front-Server
if not exist node_modules (
    echo 正在安装前端依赖，请稍候...
    npm install
)

echo.
echo 7. 启动前端服务...
start "CRM前端服务" cmd /k "npm run dev"
cd ..

echo.
echo ========================================
echo    CRM系统启动完成！
echo ========================================
echo 前端访问地址: http://localhost:8080
echo 后端API地址: http://localhost:8081
echo 默认账号: admin
echo 默认密码: 123456
echo ========================================
echo.
echo 按任意键退出...
pause >nul