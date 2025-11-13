@echo off
echo 正在启动CRM后端服务...

set JAVA_HOME=C:\Program Files\Java\jdk-1.8
set PATH=%JAVA_HOME%\bin;%PATH%

echo 编译项目...
javac -cp "src/main/java" -d "target/classes" src/main/java/com/crm/CrmSystemApplication.java

if %errorlevel% neq 0 (
    echo 编译失败，请检查代码
    pause
    exit /b 1
)

echo 启动应用...
java -cp "target/classes" com.crm.CrmSystemApplication

pause