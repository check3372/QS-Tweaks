@echo off
rem 在 Windows 上一键编译（需先装 JDK17 + Android SDK，并在 PATH 里有 gradle 或已有 gradlew.bat）
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem 清空废弃文件（注释占位文件，留着不影响编译，但清掉更干净）
for %%f in (ReflectiveTile TileFactory XposedActive ModuleBridge NoTitleBarActivity) do (
  if exist "app\src\main\java\com\firefly\qstweaks\%%f.java" (
    for %%s in ("app\src\main\java\com\firefly\qstweaks\%%f.java") do (
      findstr /b /c:"//" "%%~s" >nul
      if not errorlevel 1 (
        del "app\src\main\java\com\firefly\qstweaks\%%f.java"
        echo removed deprecated: %%f.java
      )
    )
  )
)

if not exist "local.properties" (
  echo local.properties 不存在，正在尝试用 ANDROID_HOME 生成...
  if defined ANDROID_HOME (
    echo sdk.dir=!ANDROID_HOME:\=\\!> local.properties
  ) else (
    echo [错误] 请先设置 ANDROID_HOME 或手动创建 local.properties，内容: sdk.dir=C:\\Users\\你\\AppData\\Local\\Android\\Sdk
    pause & exit /b 1
  )
)

if not exist "gradlew.bat" (
  echo 首次运行，生成 gradle wrapper...
  call gradle wrapper --gradle-version 8.9 || (echo gradle wrapper 失败 & pause & exit /b 1)
)

call gradlew.bat assembleDebug
if errorlevel 1 (echo 编译失败 & pause & exit /b 1)

echo.
echo 编译完成:
echo   %cd%\app\build\outputs\apk\debug\app-debug.apk
pause