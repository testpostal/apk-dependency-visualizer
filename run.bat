@echo off
chcp 65001 > nul
echo Компиляция исходного кода основного приложения и тестов...

javac --release 8 -Xlint:-options -encoding UTF-8 -d out src/Main.java

javac --release 8 -Xlint:-options -encoding UTF-8 -d out tests/Test.java

if %errorlevel% neq 0 (
    echo Ошибка компиляции!
    pause
    exit /b %errorlevel%
)

echo ====================================================
echo Запуск автоматических тестов...
echo ====================================================

java -Dfile.encoding=UTF-8 -cp out Test

echo.
echo ====================================================
echo Запуск основного приложения...
echo ====================================================

java -Dfile.encoding=UTF-8 -cp out Main
pause
