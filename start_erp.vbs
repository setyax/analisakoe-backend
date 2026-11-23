Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "C:\analisakoe\backend"
WshShell.Run "cmd.exe /c ""C:\orlansoft_erp\openjdk\bin\java.exe" -Xmx2048m -jar "C:\analisakoe\backend\target\analisakoe-0.1.0-SNAPSHOT.jar" > "C:\analisakoe\backend\app-startup.log" 2>&1"", 0, False