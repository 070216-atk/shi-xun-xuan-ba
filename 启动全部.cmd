@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo   正在打开两个窗口：
echo     1. 后端  http://localhost:8080
echo     2. 前端  http://localhost:5173
echo.
echo   注意：第一次使用前，请先双击一次「初始化数据库.cmd」。
echo.

start "选拔系统 - 后端 8080" cmd /k powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-backend.ps1"

echo   等待后端启动 ...
timeout /t 8 /nobreak >nul

start "选拔系统 - 前端 5173" cmd /k powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-frontend.ps1"

echo.
echo   两个窗口已打开，关闭本窗口不影响它们。
timeout /t 3 /nobreak >nul
