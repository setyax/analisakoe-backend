Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "C:\analisakoe\backend"
WshShell.Run """C:\analisakoe\backend\run_erp.bat""", 0, False
