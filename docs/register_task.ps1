# 计划任务注册脚本
# 用于注册开机启动的Python消费者脚本

$taskName = "PythonAnalysisConsumer"
$description = "开机启动Python分析消费者脚本"
$workdir = "d:\HuaweiMoveData\Users\38929\Desktop\System\System"
$scriptPath = "$workdir\docs\run_consumer.ps1"
$pythonPath = "C:\Users\38929\AppData\Local\Programs\Python\Python311\python.exe"

Write-Host "[INFO] 开始注册计划任务：$taskName" -ForegroundColor Cyan
Write-Host "[INFO] 脚本路径：$scriptPath" -ForegroundColor Cyan
Write-Host "[INFO] 工作目录：$workdir" -ForegroundColor Cyan

# 检查脚本是否存在
if (-not (Test-Path -Path $scriptPath)) {
    Write-Host "[ERROR] 脚本文件不存在：$scriptPath" -ForegroundColor Red
    exit 1
}

# 创建触发器：开机启动
$trigger = New-ScheduledTaskTrigger -AtStartup

# 创建操作：运行PowerShell脚本
$action = New-ScheduledTaskAction `
    -Execute "PowerShell.exe" `
    -Argument "-ExecutionPolicy Bypass -File '$scriptPath'" `
    -WorkingDirectory $workdir

# 设置任务设置
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RunOnlyIfNetworkAvailable `
    -RestartInterval (New-TimeSpan -Minutes 5) `
    -RestartCount 3

# 注册计划任务
# -User "$env:USERNAME"：使用当前用户
# -RunLevel Highest：以最高权限运行
try {
    Register-ScheduledTask `
        -TaskName $taskName `
        -Trigger $trigger `
        -Action $action `
        -Settings $settings `
        -Description $description `
        -User "$env:USERNAME" `
        -RunLevel Highest `
        -Force
    
    Write-Host "[SUCCESS] 计划任务注册成功：$taskName" -ForegroundColor Green
    Write-Host "[INFO] 任务将在开机时自动启动" -ForegroundColor Green
    
    # 显示任务信息
    Write-Host "[INFO] 任务信息：" -ForegroundColor Cyan
    Get-ScheduledTask -TaskName $taskName | Format-List
    
} catch {
    Write-Host "[ERROR] 计划任务注册失败：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
