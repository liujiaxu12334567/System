# 简化版运行脚本
$workdir = "d:\HuaweiMoveData\Users\38929\Desktop\System\System"
$python = "C:\Users\38929\AppData\Local\Programs\Python\Python311\python.exe"
$script = "$workdir\docs\python_analysis_consumer.py"
$log = "$workdir\consumer.log"

# 设置环境变量
$env:RABBITMQ_URL = "amqp://guest:guest@127.0.0.1:5672/%2F"
$env:MYSQL_URL = "mysql+pymysql://root:liujiaxu@localhost:3306/system"

# 切换到工作目�?
Set-Location -Path $workdir

# 确保日志文件存在
if (-not (Test-Path -Path $log)) {
    New-Item -Path $log -ItemType File -Force | Out-Null
}

# 输出开始信�?
$startMsg = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 开始执行Python脚本..."
Write-Host $startMsg -ForegroundColor Green
Add-Content -Path $log -Value $startMsg

# 使用管道同时输出到控制台和日�?
# 注意：如果Python脚本是长运行的，这个命令会阻�?
& $python $script 2>&1 | ForEach-Object {
    # 输出到控制台
    Write-Host $_
    # 追加到日志文�?
    Add-Content -Path $log -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $_"
}

# 输出结束信息
$endMsg = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Python脚本执行结束"
Write-Host $endMsg -ForegroundColor Green
Add-Content -Path $log -Value $endMsg
