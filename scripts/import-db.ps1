# ============================================================
#  学生实训室进组选拔系统 · 一键初始化数据库
#  由「初始化数据库.cmd」双击调用
# ============================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$dbDir = Join-Path $root 'framework\3.database'
$localYml = Join-Path $root 'backend\src\main\resources\application-local.yml'

function Step($m) { Write-Host "  $m" -ForegroundColor Cyan }
function Fail($m) {
    Write-Host ""
    Write-Host "  [失败] $m" -ForegroundColor Red
    Write-Host ""
    Read-Host "  按回车退出"
    exit 1
}

Write-Host ""
Write-Host "  ============================================" -ForegroundColor DarkCyan
Write-Host "   学生实训室进组选拔系统  ·  初始化数据库" -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor DarkCyan
Write-Host ""

# ---------- 1. 找 mysql.exe ----------
Step "查找 mysql 客户端 ..."
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
Step "mysql: $mysql"

# ---------- 2. 取密码 ----------
Step "读取数据库密码 ..."
$pw = ''
if (Test-Path $localYml) {
    $yml = Get-Content -Raw -LiteralPath $localYml
    if ($yml -match '(?m)^\s+password:\s*"?([^"\r\n]*)"?\s*$') { $pw = $Matches[1] }
}
if (-not $pw) {
    Write-Host "  没在 application-local.yml 里读到密码（文件可能还没建）。" -ForegroundColor Yellow
    $pw = Read-Host "  请输入 MySQL root 密码（只在本次使用，不保存）"
}
if (-not $pw) { Fail "密码为空，无法连接数据库" }

# ---------- 3. 确认 ----------
Write-Host ""
Write-Host "  即将【重建】数据库 lab_selection，现有数据会被清空。" -ForegroundColor Yellow
$ok = Read-Host "  确认继续请输入 y 回车（其他键取消）"
if ($ok -ne 'y') { Write-Host "  已取消"; exit 0 }

# ---------- 4. 复制 SQL 到英文临时目录 ----------
# 原因：mysql 的 source 命令打不开含中文的路径，会报 Failed to open file
Step "准备 SQL 脚本 ..."
$tmp = Join-Path $env:TEMP ('sel_import_' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Copy-Item -LiteralPath (Join-Path $dbDir 'schema.sql') -Destination (Join-Path $tmp 'schema.sql') -Force
Copy-Item -LiteralPath (Join-Path $dbDir 'seed.sql') -Destination (Join-Path $tmp 'seed.sql') -Force
$fwd = $tmp.Replace('\', '/')

$cnf = Join-Path $env:TEMP ('sel_cnf_' + [guid]::NewGuid().ToString('N').Substring(0, 8) + '.cnf')
"[client]`r`nuser=root`r`npassword=$pw`r`n" | Set-Content -LiteralPath $cnf -Encoding ascii

try {
    Step "建表（schema.sql）..."
    & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 -e "source $fwd/schema.sql"
    if ($LASTEXITCODE -ne 0) { Fail "建表失败。常见原因：密码不对、MySQL 服务没启动。" }

    Step "导入数据（seed.sql）..."
    & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 -e "source $fwd/seed.sql"
    if ($LASTEXITCODE -ne 0) { Fail "导入数据失败（上面有具体报错）。" }

    Step "校验 ..."
    & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 -e @"
USE lab_selection;
SELECT (SELECT COUNT(*) FROM lab)      AS 实训室,
       (SELECT COUNT(*) FROM mentor)   AS 导师,
       (SELECT COUNT(*) FROM sys_user) AS 账号,
       (SELECT COUNT(*) FROM stage)    AS 阶段;
"@

    Write-Host ""
    $demo = Read-Host "  是否把「意向填报 / 面试考核」阶段设成现在开放，方便演示？(y/N)"
    if ($demo -eq 'y') {
        & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 -e @"
USE lab_selection;
UPDATE stage SET start_at = DATE_SUB(NOW(), INTERVAL 1 HOUR),
                 end_at   = DATE_ADD(NOW(), INTERVAL 20 DAY)
 WHERE round_no = 1 AND stage_no IN (3, 4);
SELECT stage_no, name, start_at, end_at FROM stage WHERE round_no = 1 AND stage_no IN (3,4);
"@
        Step "已开放（有效期 20 天）"
    }
}
finally {
    Remove-Item -LiteralPath $cnf -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "  完成！" -ForegroundColor Green
Write-Host "  演示账号：admin / teacher01 / student01    密码都是 123456" -ForegroundColor Green
Write-Host "  下一步：双击根目录的「启动后端.cmd」" -ForegroundColor Gray
Write-Host ""
Read-Host "  按回车退出"
