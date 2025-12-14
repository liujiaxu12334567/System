@echo off
setlocal enabledelayedexpansion

echo ===============================================
echo 注册Python消费者计划任务
echo ===============================================

REM 设置变量
set "TASK_NAME=PythonAnalysisConsumer"
set "SCRIPT_PATH=d:\HuaweiMoveData\Users\38929\Desktop\System\System\docs\run_consumer.ps1"
set "WORK_DIR=d:\HuaweiMoveData\Users\38929\Desktop\System\System"
set "USERNAME=%USERNAME%"

echo [INFO] 任务名称: %TASK_NAME%
echo [INFO] 脚本路径: %SCRIPT_PATH%
echo [INFO] 工作目录: %WORK_DIR%
echo [INFO] 当前用户: %USERNAME%
echo.

REM 检查脚本是否存在
if not exist "%SCRIPT_PATH%" (
    echo [ERROR] 脚本文件不存在: %SCRIPT_PATH%
    pause
    exit /b 1
)

REM 注册计划任务
echo [INFO] 开始注册计划任务...
schtasks /CREATE /TN "%TASK_NAME%" /TR "powershell.exe -ExecutionPolicy Bypass -File \"%SCRIPT_PATH%\"" /SC ONSTART /RL HIGHEST /F /RU "%USERNAME%"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] 计划任务注册成功！
    echo [INFO] 任务将在开机时自动启动
    echo [INFO] 可以在 "任务计划程序" 中查看和管理任务
) else (
    echo.
    echo [ERROR] 计划任务注册失败！
    echo [INFO] 请检查命令语法或手动注册
)

echo.
echo ===============================================
echo 注册完成
echo ===============================================
pause
