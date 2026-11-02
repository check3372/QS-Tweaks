@echo off
rem QSTweaks v3.0：清理临时文件 + 编译
cd /d "%~dp0"

del /f /q "_cleanup.bat" "_cleanup2.bat" "_keep_notes.txt" "_adb_step1.txt" "_extract_problems.bat" "_rebuild.bat" 2>nul

call gradlew.bat assembleDebug --console=plain > build_log.txt 2>&1
echo EXITCODE=%ERRORLEVEL% >> build_log.txt
type build_log.txt | findstr /i /c:"BUILD SUCCESSFUL" /c:"BUILD FAILED" /c:"EXITCODE"