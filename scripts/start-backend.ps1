# ============================================================
#  学生实训室进组选拔系统 · 一键启动后端
#  由「启动后端.cmd」双击调用，也可以直接：
#    powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$jar = Join-Path $backend 'target\selection-0.1.0.jar'

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
Write-Host "   学生实训室进组选拔系统  ·  启动后端" -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor DarkCyan
Write-Host ""

# ---------- 1. 找一个能用的 JDK（17 ~ 24，优先 21）----------
Step "查找 JDK ..."
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
foreach ($pattern in @(
            'C:\Program Files\Java\jdk-*',
            'C:\Program Files\Zulu\zulu-*',
            'C:\Program Files\Eclipse Adoptium\jdk-*',
            'C:\Program Files\Microsoft\jdk-*')) {
    Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { $candidates += (Join-Path $_.FullName 'bin\java.exe') }
}
$cmdJava = Get-Command java -ErrorAction SilentlyContinue
if ($cmdJava) { $candidates += $cmdJava.Source }

function Get-Major($exe) {
    try {
        $out = & $exe --version 2>&1 | Out-String
        if ($out -match '(\d+)\.') { return [int]$Matches[1] }
    } catch { }
    return 0
}

$found = @()
foreach ($c in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
    if (-not (Test-Path $c)) { continue }
    $m = Get-Major $c
    if ($m -ge 17 -and $m -le 24) {
        $found += [pscustomobject]@{ Exe = $c; Major = $m }
    }
}
if ($found.Count -eq 0) {
    Fail "没找到可用的 JDK（需要 17 到 24 之间的版本）。`n         请安装 JDK 21，或设置环境变量 JAVA_HOME 指向它。"
}
$pick = $found |
    Sort-Object @{ Expression = { if ($_.Major -eq 21) { 0 } elseif ($_.Major -eq 17) { 1 } else { 2 } } },
                @{ Expression = { $_.Major }; Descending = $true } |
    Select-Object -First 1
$javaExe = $pick.Exe
$javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;" + $env:Path
Step "使用 JDK $($pick.Major)：$javaHome"

# ---------- 2. 找 Maven（有就重新打包，没有就用已有 jar）----------
Step "查找 Maven ..."
$mvnPath = $null
$mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mvnCmd) {
    $mvnPath = $mvnCmd.Source
} else {
    foreach ($pattern in @('C:\Program Files\apache-maven-*', 'C:\apache-maven-*', 'D:\apache-maven-*')) {
        Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $p = Join-Path $_.FullName 'bin\mvn.cmd'
            if ((Test-Path $p) -and -not $mvnPath) { $mvnPath = $p }
        }
    }
}
if ($mvnPath) { Step "Maven: $mvnPath" } else { Step "没有 Maven（如果已有编译好的 jar 也能跑）" }

# ---------- 3. 打包 ----------
if ($mvnPath) {
    Step "编译打包（首次或改了代码会花几秒）..."
    Push-Location $backend
    $log = & $mvnPath -o -DskipTests package 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Step "离线编译失败，尝试联网重来 ..."
        $log = & $mvnPath -DskipTests package 2>&1
        $code = $LASTEXITCODE
    }
    Pop-Location
    if ($code -ne 0) {
        $log | Select-Object -Last 25 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        Fail "编译失败（上面是最后的报错）。常见原因：依赖没下载完、JDK 版本不对。"
    }
    Step "打包完成"
} else {
    if (-not (Test-Path $jar)) {
        Fail "既没有 Maven，也没有已编译的 jar。`n         请安装 Maven，或让同学把 backend\target 目录打包发给你。"
    }
    Step "使用已有的 jar"
}

# ---------- 4. 端口检查 ----------
$busy = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    Fail "8080 端口已经被占用（可能已经开过一个窗口）。`n         先关掉那个窗口，或把 backend\src\main\resources\application.yml 里的 server.port 改成别的。"
}

# ---------- 5. 启动 ----------
Write-Host ""
Write-Host "  启动中 ... 看到 Started SelectionApplication 就成功了" -ForegroundColor Yellow
Write-Host "  接口地址 : http://localhost:8080/api/labs" -ForegroundColor Green
Write-Host "  演示账号 : admin / teacher01 / student01   密码都是 123456" -ForegroundColor Green
Write-Host "  停止服务 : 按 Ctrl + C，或直接关掉这个窗口" -ForegroundColor Gray
Write-Host ""

& $javaExe -jar $jar
$exitCode = $LASTEXITCODE

Write-Host ""
if ($exitCode -ne 0) {
    Write-Host "  服务异常退出（exit=$exitCode）。常见原因：" -ForegroundColor Red
    Write-Host "    · MySQL 没启动 / 密码不对   → 检查 application-local.yml" -ForegroundColor DarkGray
    Write-Host "    · 数据库没导入               → 见 framework/3.database/README.md" -ForegroundColor DarkGray
}
Read-Host "  按回车退出"
