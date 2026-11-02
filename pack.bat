@echo off
rem QSTweaks 全量源码打包（不含废弃类），输出到 build\qstweaks-src.zip
setlocal
cd /d "%~dp0"
set OUT=build\qstweaks-src.zip
if not exist build mkdir build
if exist "%OUT%" del "%OUT%"

powershell -NoProfile -Command "Compress-Archive -Path 'app','build.gradle','settings.gradle','gradle.properties','README.md','TESTING.md','DESIGN.md','build.bat','pack.bat' -DestinationPath '%OUT%' -Force"
if errorlevel 1 (echo 打包失败 & pause & exit /b 1)
echo 已生成: %cd%\%OUT%
pause