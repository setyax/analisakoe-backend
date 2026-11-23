@echo off
cd /d "C:\analisakoe\backend"
echo === ERP STARTUP LOG === >> "C:\analisakoe\backend\erp-startup.log"
echo Started at %DATE% %TIME% >> "C:\analisakoe\backend\erp-startup.log"
"C:\orlansoft_erp\openjdk\bin\java.exe" -Xmx2048m -jar "C:\analisakoe\backend\target\analisakoe-0.1.0-SNAPSHOT.jar" >> "C:\analisakoe\backend\erp-startup.log" 2>&1