# ============================================================
#  学生实训室进组选拔系统 · 用命令行打开数据库
#  由「打开数据库.cmd」双击调用
#  直接进 mysql 交互界面，退出后自动删掉临时密码文件
# ============================================================
param([string]$Sql)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$localYml = Join-Path $root 'backend\src\main\resources\application-local.yml'

function Fail($m) {
    Write-Host ""
    Write-Host "  [失败] $m" -ForegroundColor Red
    Write-Host ""
    Read-Host "  按回车退出"
    exit 1
}

# ---------- 1. 找 mysql.exe ----------
$mysql = $null
$cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($cmd) { $mysql = $cmd.Source }
if (-not $mysql) {
    $known = @(
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe',
        'C:\xampp\mysql\bin\mysql.exe',
        'D:\xampp\mysql\bin\mysql.exe'
    )
    foreach ($p in $known) { if (Test-Path $p) { $mysql = $p; break } }
}
if (-not $mysql) {
    Fail "没找到 mysql.exe。请确认已安装 MySQL 8，或把 mysql 的 bin 目录加进 PATH。"
}

# ---------- 2. 取密码（优先读本机配置，不写进仓库） ----------
$pw = ''
if (Test-Path $localYml) {
    $yml = Get-Content -Raw -LiteralPath $localYml
    if ($yml -match '(?m)^\s+password:\s*"?([^"\r\n]*)"?\s*$') { $pw = $Matches[1] }
}
if (-not $pw) {
    $pw = Read-Host "  请输入 MySQL root 密码（只在本次使用，不保存）"
}
if (-not $pw) { Fail "密码为空，无法连接数据库" }

# ---------- 3. 临时密码文件（退出即删） ----------
$cnf = Join-Path $env:TEMP ('sel_open_' + [guid]::NewGuid().ToString('N').Substring(0, 8) + '.cnf')
"[client]`r`nuser=root`r`npassword=$pw`r`n" | Set-Content -LiteralPath $cnf -Encoding ascii

try {
    if ($Sql) {
        & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 lab_selection -e $Sql
        exit $LASTEXITCODE
    }

    Write-Host ""
    Write-Host "  ============================================" -ForegroundColor DarkCyan
    Write-Host "   已连接数据库 lab_selection（mysql 命令行）" -ForegroundColor White
    Write-Host "  ============================================" -ForegroundColor DarkCyan
    Write-Host ""
    Write-Host "  常用命令（在 mysql> 提示符后输入，每条以分号结尾）：" -ForegroundColor Cyan
    Write-Host "    show tables;" -ForegroundColor Gray
    Write-Host "    select id,username,code,real_name,class_name from sys_user where role='STUDENT';" -ForegroundColor Gray
    Write-Host "    select * from application where round_no=3;" -ForegroundColor Gray
    Write-Host "    select * from offer where round_no=3;" -ForegroundColor Gray
    Write-Host "    select * from publicity;" -ForegroundColor Gray
    Write-Host "    exit   -- 退出（这条不带分号）" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  提示符是 mysql>，不是 Windows 的 >；SQL 语句要以分号结尾。" -ForegroundColor DarkGray
    Write-Host ""

    & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 lab_selection
    Write-Host ""
    Read-Host "  已退出数据库，按回车关闭窗口"
}
finally {
    Remove-Item -LiteralPath $cnf -Force -ErrorAction SilentlyContinue
}
