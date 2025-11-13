@echo off
echo ========================================
echo CRM系统后端服务启动脚本 (本地环境)
echo ========================================
echo.

echo 正在检查Java环境...
java -version
if %errorlevel% neq 0 (
    echo 错误：未找到Java环境，请先安装JDK 8或更高版本
    pause
    exit /b 1
)

echo.
echo 正在检查MySQL服务...
sc query mysql >nul 2>&1
if %errorlevel% neq 0 (
    echo 警告：MySQL服务未启动，请先启动MySQL服务
    echo 可以使用以下命令启动：net start mysql
    pause
)

echo.
echo 正在启动CRM后端服务...
echo 使用配置文件：application-local.yml
echo 数据库：localhost:3306/crm_system
echo.

./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

echo.
echo 服务已停止
pause