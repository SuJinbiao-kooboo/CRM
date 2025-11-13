@echo off
echo Starting CRM Backend Service...
echo.

REM Set JAVA_HOME if not set
if not defined JAVA_HOME (
    set JAVA_HOME=C:\Program Files\Java\jdk-1.8
)

set PATH=%JAVA_HOME%\bin;%PATH%

echo Current JAVA_HOME: %JAVA_HOME%
echo Current Java Version:
java -version
echo.

echo Starting Spring Boot Application...
REM 支持通过第一个参数传入端口，如：start-simple.bat 7090
set PORT=%1
if "%PORT%"=="" set PORT=7082
echo Using server.port=%PORT%

REM 准备日志目录与文件
if not exist logs mkdir logs
set LOGFILE=logs\boot-run-%PORT%.log
echo Logging to %LOGFILE%

REM 组装应用参数，禁用devtools重启避免端口重复绑定
set ARGS=--server.port=%PORT% --spring.devtools.restart.enabled=false

REM 使用 Spring Boot Maven 插件参数传递端口与配置文件，并将输出重定向到日志
.\mvnw.cmd spring-boot:run -DskipTests -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="%ARGS%" >> "%LOGFILE%" 2>&1

if %errorlevel% neq 0 (
    echo Failed to start application, please check error messages
    pause
    exit /b 1
)

pause