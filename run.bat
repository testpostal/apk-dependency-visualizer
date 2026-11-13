@echo off
chcp 65001 > nul
if not exist out mkdir out

for /f "delims=" %%i in ('where javac') do set "JAVAC_PATH=%%i"
for %%i in ("%JAVAC_PATH%") do set "JDK_BIN=%%~dpi"

echo Компиляция исходного кода основного приложения...
"%JDK_BIN%javac.exe" -encoding UTF-8 -d out src/*.java
if %errorlevel% neq 0 (
    echo Ошибка компиляции основного приложения!
    pause
    exit /b %errorlevel%
)

echo Компиляция тестов...
"%JDK_BIN%javac.exe" -encoding UTF-8 -cp out -d out tests/*.java
if %errorlevel% neq 0 (
    echo Ошибка компиляции тестов!
    pause
    exit /b %errorlevel%
)

echo ====================================================
echo Запуск автоматических тестов...
echo ====================================================
"%JDK_BIN%java.exe" -Dfile.encoding=UTF-8 -cp out Test
if %errorlevel% neq 0 (
    echo Тесты завершились с ошибкой!
    pause
    exit /b %errorlevel%
)

echo.
echo ====================================================
echo Запуск основного приложения...
echo ====================================================
"%JDK_BIN%java.exe" -Dfile.encoding=UTF-8 -cp out Main
pause
