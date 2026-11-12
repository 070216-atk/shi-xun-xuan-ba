<#
    并发压测：验证「名额行锁」在超卖场景下真的能挡住。
    
    思路：
      1. 把某个导师的名额临时改成小数字（默认 2）
      2. 直接往库里塞入比名额更多的「拟录取」意向（模拟异常/脏数据，正常流程走不出来）
      3. 让这些学生「同时」点确认
      4. 断言：最终确认数 == 名额，多出来的必须被拒绝
    
    用法（在项目根目录）：
        powershell -ExecutionPolicy Bypass -File scripts\load-test.ps1
        powershell -ExecutionPolicy Bypass -File scripts\load-test.ps1 -Quota 1 -Students 5
#>

param(
    [int]$Quota = 2,
    [int]$Students = 3,
    [int]$MentorId = 1,
    [int]$Round = 1,
    [int]$BasePort = 8080
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$localYml = Join-Path $root 'backend\src\main\resources\application-local.yml'
$api = "http://localhost:$BasePort/api"

if (-not (Test-Path $localYml)) {
    Write-Host "找不到 $localYml" -ForegroundColor Red
    exit 1
}

function Get-DbPassword {
    $txt = Get-Content -Raw -LiteralPath $localYml
    if ($txt -match '(?m)^\s+password:\s*"?([^"\r\n]*)"?\s*$') { return $Matches[1] }
    throw 'application-local.yml 里没有读到数据库密码'
}

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path $mysql)) { $mysql = 'mysql' }

$cnf = Join-Path $env:TEMP ("selql_{0}.cnf" -f ([guid]::NewGuid().ToString('N')))
("[client]`r`nuser=root`r`npassword=" + (Get-DbPassword) + "`r`n") |
    Set-Content -LiteralPath $cnf -Encoding ascii

function Sql([string]$q) {
    & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4 -N -B lab_selection -e $q
}

function Api([string]$method, [string]$url, $body, [string]$token) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    $p = @{ Uri = $url; Method = $method; UseBasicParsing = $true; Headers = $h; TimeoutSec = 30 }
    if ($body) {
        $p['ContentType'] = 'application/json; charset=utf-8'
        $p['Body'] = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress -Depth 6))
    }
    $r = Invoke-WebRequest @p
    return ([Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json)
}

function Login([string]$u, [string]$portal) {
    $r = Api 'POST' "$api/auth/login" @{ username = $u; password = '123456'; portal = $portal } $null
    if ($r.code -ne 0) { throw "登录失败: $u -> $($r.message)" }
    return $r.data.token
}

Write-Host ''
Write-Host '================ 并发压测：名额行锁 ================' -ForegroundColor Cyan
Write-Host "  导师 ID      : $MentorId"
Write-Host "  临时名额     : $Quota"
Write-Host "  并发确认人数 : $Students"
Write-Host ''

# ---------- 0. 记录原始名额，便于还原 ----------
$origQuota = (Sql "SELECT quota FROM mentor WHERE id=$MentorId").Trim()
$labId = (Sql "SELECT lab_id FROM mentor WHERE id=$MentorId").Trim()
$mentorName = (Sql "SELECT name FROM mentor WHERE id=$MentorId").Trim()
Write-Host "  导师：$mentorName（实训室 $labId），原名额 $origQuota"

# ---------- 1. 准备学生 ----------
$studentIds = @(Sql "SELECT id FROM sys_user WHERE role='STUDENT' ORDER BY id LIMIT $Students")
$studentIds = @($studentIds | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
if ($studentIds.Count -lt $Students) {
    Write-Host "  学生不够：需要 $Students 个，只有 $($studentIds.Count) 个" -ForegroundColor Red
    Remove-Item -LiteralPath $cnf -Force -ErrorAction SilentlyContinue
    exit 1
}

$usernames = @()
foreach ($sid in $studentIds) {
    $usernames += (Sql "SELECT username FROM sys_user WHERE id=$sid").Trim()
}

# ---------- 2. 构造超卖场景 ----------
Sql "UPDATE mentor SET quota=$Quota WHERE id=$MentorId" | Out-Null
Sql "DELETE FROM offer WHERE round_no=$Round" | Out-Null
Sql "UPDATE stage SET start_at = DATE_SUB(NOW(), INTERVAL 2 DAY), end_at = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE round_no=$Round" | Out-Null
Sql "UPDATE stage SET start_at = DATE_SUB(NOW(), INTERVAL 1 HOUR), end_at = DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE round_no=$Round AND stage_no=5" | Out-Null

$vals = @()
foreach ($sid in $studentIds) {
    $vals += "($sid,$MentorId,$labId,$Round,'ADMITTED','ISSUED',DATE_ADD(NOW(), INTERVAL 1 DAY))"
}
Sql ("INSERT INTO offer (student_id, mentor_id, lab_id, round_no, type, status, confirm_deadline) VALUES " + ($vals -join ',')) | Out-Null

$offers = @(Sql "SELECT id, student_id FROM offer WHERE round_no=$Round AND mentor_id=$MentorId ORDER BY id")
$offerIds = @(); $offerOwner = @()
foreach ($line in $offers) {
    $parts = $line -split "`t"
    $offerIds += $parts[0].Trim()
    $offerOwner += $parts[1].Trim()
}
Write-Host "  已构造 $($offerIds.Count) 条拟录取意向，名额只有 $Quota —— 必然超卖" -ForegroundColor Yellow

# ---------- 3. 每个学生登录拿 token ----------
$tokens = @{}
for ($i = 0; $i -lt $offerIds.Count; $i++) {
    $uname = $usernames[$i]
    $tokens[$i] = Login $uname 'web'
}
Write-Host "  已登录 $($tokens.Count) 个学生"

# ---------- 4. 同时点确认 ----------
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)

$tasks = @()
for ($i = 0; $i -lt $offerIds.Count; $i++) {
    $req = New-Object System.Net.Http.HttpRequestMessage('POST', "$api/student/offers/$($offerIds[$i])/confirm")
    $req.Headers.Add('Authorization', "Bearer $($tokens[$i])")
    $req.Content = New-Object System.Net.Http.StringContent('{}', [Text.Encoding]::UTF8, 'application/json')
    $tasks += $client.SendAsync($req)
}
[System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)

$ok = 0; $fail = 0; $detail = @()
for ($i = 0; $i -lt $tasks.Count; $i++) {
    $json = $tasks[$i].Result.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    if ($json.code -eq 0) { $ok++ } else { $fail++ }
    $detail += ("    student {0} -> code={1} {2}" -f $offerOwner[$i], $json.code, $json.message)
}
$client.Dispose()
$detail | ForEach-Object { Write-Host $_ }

# ---------- 5. 断言 ----------
$confirmed = [int](Sql "SELECT COUNT(*) FROM offer WHERE round_no=$Round AND mentor_id=$MentorId AND status='CONFIRMED'").Trim()

Write-Host ''
Write-Host '---------------- 结果 ----------------' -ForegroundColor Cyan
Write-Host ("  确认成功      : {0}" -f $ok)
Write-Host ("  被拒绝        : {0}" -f $fail)
Write-Host ("  库里 CONFIRMED: {0}   名额: {1}" -f $confirmed, $Quota)

$pass = $true
if ($confirmed -gt $Quota) { Write-Host '  [失败] 超录了！行锁没挡住' -ForegroundColor Red; $pass = $false }
if ($confirmed -ne $ok) { Write-Host '  [失败] 返回成功数与库里不一致（可能有重复写入）' -ForegroundColor Red; $pass = $false }
if ($ok -ne $Quota) { Write-Host "  [注意] 成功数 $ok 不等于名额 $Quota（可能并发时机不同）" -ForegroundColor Yellow }
if ($pass) { Write-Host '  [通过] 名额没有被突破，行锁生效' -ForegroundColor Green }

# ---------- 6. 还原 ----------
Sql "UPDATE mentor SET quota=$origQuota WHERE id=$MentorId" | Out-Null
Sql "DELETE FROM offer WHERE round_no=$Round" | Out-Null
Sql "UPDATE stage SET start_at = DATE_SUB(NOW(), INTERVAL 2 DAY), end_at = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE round_no=$Round" | Out-Null
Sql "UPDATE stage SET start_at = DATE_SUB(NOW(), INTERVAL 1 HOUR), end_at = DATE_ADD(NOW(), INTERVAL 20 DAY) WHERE round_no=$Round AND stage_no=3" | Out-Null
Remove-Item -LiteralPath $cnf -Force -ErrorAction SilentlyContinue
Write-Host ''
Write-Host "  已还原：导师名额 $origQuota，测试数据已清理，阶段回到「意向填报」" -ForegroundColor DarkGray
