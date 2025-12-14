# 极简计划任务注册脚本
# 使用最基本的语法，避免复杂转义

# 任务名称
$taskName = "PythonAnalysisConsumer"

# 脚本路径
$scriptPath = "d:\HuaweiMoveData\Users\38929\Desktop\System\System\docs\run_consumer.ps1"

Write-Host "注册计划任务: $taskName"
Write-Host "脚本路径: $scriptPath"

# 简单的任务注册
# 使用默认设置，开机启动
Register-ScheduledTask -TaskName $taskName -Trigger (New-ScheduledTaskTrigger -AtStartup) -Action (New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-ExecutionPolicy Bypass -File '$scriptPath'" -WorkingDirectory "d:\HuaweiMoveData\Users\38929\Desktop\System\System") -RunLevel Highest -Force

Write-Host "注册完成！"
