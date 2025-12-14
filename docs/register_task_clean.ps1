$taskName = "PythonAnalysisConsumer"
$description = "开机启动Python分析消费者脚本"
$workdir = "d:\HuaweiMoveData\Users\38929\Desktop\System\System"
$scriptPath = "$workdir\docs\run_consumer.ps1"

Write-Host "[INFO] 开始注册计划任务：$taskName"
Write-Host "[INFO] 脚本路径：$scriptPath"
Write-Host "[INFO] 工作目录：$workdir"

# 检查脚本是否存在
if (-not (Test-Path -Path $scriptPath)) {
    Write-Host "[ERROR] 脚本文件不存在：$scriptPath"
    exit 1
}

# 创建触发器：开机启动
$trigger = New-ScheduledTaskTrigger -AtStartup

# 创建操作：运行PowerShell脚本
$action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File '$scriptPath'" -WorkingDirectory $workdir

# 设置任务设置
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RunOnlyIfNetworkAvailable

# 注册计划任务
try {
    Register-ScheduledTask -TaskName $taskName -Trigger $trigger -Action $action -Settings $settings -Description $description -User "$env:USERNAME" -RunLevel Highest -Force
    Write-Host "[SUCCESS] 计划任务注册成功：$taskName"
    Write-Host "[INFO] 任务将在开机时自动启动"
} catch {
    Write-Host "[ERROR] 计划任务注册失败：$($_.Exception.Message)"
    exit 1
}
