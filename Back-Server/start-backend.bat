@echo off
echo 启动CRM后端服务...
echo.

REM 设置JAVA_HOME环境变量
set JAVA_HOME=C:\Program Files\Java\jdk-1.8
set PATH=%JAVA_HOME%\bin;%PATH%

echo 当前JAVA_HOME: %JAVA_HOME%
echo 当前Java版本:
java -version
echo.

echo 清理并编译项目...
call mvnw.cmd clean compile -DskipTests
if %errorlevel% neq 0 (
    echo 编译失败，请检查错误信息
    pause
    exit /b 1
)

echo.
echo 启动Spring Boot应用...
call mvnw.cmd spring-boot:run -DskipTests
if %errorlevel% neq 0 (
    echo 启动失败，请检查错误信息
    pause
    exit /b 1
)

pause