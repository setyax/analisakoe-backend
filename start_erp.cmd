@echo off
title AnalisaKoe ERP - port 8085
cd /d "%~dp0"

set "JAVA=C:\orlansoft_erp\openjdk\bin\java.exe"
set "JAR=%~dp0target\analisakoe-0.1.0-SNAPSHOT.jar"

if not exist "%JAVA%" (
    echo ERROR: Java tidak ditemukan di %JAVA%
    pause
    exit /b 1
)
if not exist "%JAR%" (
    echo ERROR: JAR tidak ditemukan di %JAR%
    echo Jalankan dulu: mvn -q -DskipTests package
    pause
    exit /b 1
)

netstat -ano | findstr ":8085" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo Aplikasi sudah berjalan di port 8085. Tidak perlu start ulang.
    timeout /t 5 >nul
    exit /b 0
)

echo Menjalankan AnalisaKoe ERP...
echo Buka: http://localhost:8085/api/ui/login
echo Tutup jendela ini untuk menghentikan aplikasi.
echo.

"%JAVA%" -Xmx2048m -jar "%JAR%"

echo.
echo Aplikasi berhenti.
pause
