@echo off
REM 杀掉占用 9299 端口的进程（本机启动 Spring 前若提示端口占用可运行此脚本）
setlocal
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9299 ^| findstr LISTENING') do (
  echo Killing PID %%a listening on 9299...
  taskkill /PID %%a /F
  goto :done
)
echo No process found listening on port 9299.
:done
endlocal
