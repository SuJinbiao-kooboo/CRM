@echo off
rem ============================================================
rem Database Schema Inspector - one-click build & run script
rem Usage: run.bat [options...]
rem   Examples:
rem     run.bat                          inspect all tables
rem     run.bat -table crm_supplier      inspect one table only
rem     run.bat -db crm_me -out c:/tmp/db.txt
rem Result is written to db_schema.txt (UTF-8) in this directory
rem ============================================================

setlocal
rem Switch to UTF-8 codepage for better Chinese output display
chcp 65001 >nul
rem Switch to script directory so output file lands here
cd /d "%~dp0"

set JDBC_JAR=

rem Locate mysql connector jar in local Maven repository (by version priority)
set "M2_REPO=%USERPROFILE%\.m2\repository"
for %%J in (
    "%M2_REPO%\com\mysql\mysql-connector-j\8.0.33\mysql-connector-j-8.0.33.jar"
    "%M2_REPO%\mysql\mysql-connector-java\8.0.28\mysql-connector-java-8.0.28.jar"
    "%M2_REPO%\mysql\mysql-connector-java\8.0.27\mysql-connector-java-8.0.27.jar"
) do (
    if exist %%J set "JDBC_JAR=%%J"
)

if "%JDBC_JAR%"=="" (
    echo [ERROR] mysql-connector driver jar not found. Run "mvn clean package -DskipTests" first.
    exit /b 1
)

rem Locate javac: JAVA_HOME first, then PATH, then common JDK path
set "JAVAC=%JAVA_HOME%\bin\javac.exe"
if not exist "%JAVAC%" set "JAVAC=javac"
if not exist "%JAVAC%" (
    if exist "C:\Program Files\Java\jdk-1.8\bin\javac.exe" set "JAVAC=C:\Program Files\Java\jdk-1.8\bin\javac.exe"
)
if not exist "%JAVAC%" (
    echo [ERROR] javac not found. Please install JDK and set JAVA_HOME.
    exit /b 1
)

rem Compile into classes subdirectory to keep tool directory clean
if not exist "classes" mkdir classes
echo Compiling DbInspector.java ...
"%JAVAC%" -encoding UTF-8 -cp "%JDBC_JAR%" -d "classes" "DbInspector.java"
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    exit /b 1
)

rem Run
echo Connecting to database and inspecting schema ...
java -cp "classes;%JDBC_JAR%" DbInspector %*
if errorlevel 1 (
    echo [ERROR] Execution failed. Check network and database configuration.
    exit /b 1
)

endlocal
