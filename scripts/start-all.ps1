# ============================================================
#  学生实训室进组选拔系统 · 一键启动整个系统
#  由「启动全部.cmd」双击调用
#
#  做四件事：
#    1. 检查 8080 / 5173 端口
#    2. 后台窗口启动后端，等 /api/labs 真的返回 200
#    3. 后台窗口启动前端，等 5173 真的可访问
#    4. 自动用默认浏览器打开页面
# ============================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$scripts = $PSScriptRoot
$backendScript = Join-Path $scripts 'start-backend.ps1'
$frontendScript = Join-Path $scripts 'start-frontend.ps1'

function Step($m) { Write-Host "  $m" -ForegroundColor Cyan }
function Ok($m) { Write-Host "  $m" -ForegroundColor Green }
function Fail($m) {
    Write-Host ""
    Write-Host "  [失败] $m" -ForegroundColor Red
    Write-Host ""
    Read-Host "  按回车退出"
    exit 1
}

function Wait-Http($url, $seconds) {
    for ($i = 1; $i -le $seconds; $i++) {
        Start-Sleep -Seconds 1
        try {
            $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400) { return $true }
        } catch { }
        if ($i % 10 -eq 0) {
            Write-Host "    还在启动 ... 已等 $i 秒" -ForegroundColor DarkGray
        }
    }
    return $false
}

Write-Host ""
Write-Host "  ============================================" -ForegroundColor DarkCyan
Write-Host "   学生实训室进组选拔系统  ·  一键启动" -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor DarkCyan
Write-Host ""

# ---------- 1. 端口检查 ----------
Step "检查端口 ..."
foreach ($port in @(8080, 5173)) {
    $busy = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($busy) {
        Fail "$port 端口已被占用，可能已经开过一个窗口。`n         先把它关掉（或重启电脑）再来。"
    }
}
Ok "8080 和 5173 都空闲"

# ---------- 2. 启动后端 ----------
Write-Host ""
Step "打开后端窗口（8080）..."
Start-Process -FilePath 'powershell.exe' `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $backendScript) | Out-Null

Step "等待后端就绪（最多 90 秒，首次启动要打包会慢一点）..."
if (-not (Wait-Http 'http://localhost:8080/api/labs' 90)) {
    Fail "后端 90 秒内没有就绪。`n         去看那个「后端」窗口里的报错（多半是 MySQL 没启动、或没导库）。"
}
Ok "后端已就绪：http://localhost:8080/api/labs"

# ---------- 3. 启动前端 ----------
Write-Host ""
Step "打开前端窗口（5173）..."
Start-Process -FilePath 'powershell.exe' `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $frontendScript) | Out-Null

Step "等待前端就绪（最多 90 秒，首次会自动 npm install）..."
if (-not (Wait-Http 'http://localhost:5173/' 90)) {
    Fail "前端 90 秒内没有就绪。`n         去看那个「前端」窗口里的报错（多半是 node_modules 没装好）。"
}
Ok "前端已就绪：http://localhost:5173"

# ---------- 4. 打开浏览器 ----------
Write-Host ""
Step "打开浏览器 ..."
try {
    Start-Process 'http://localhost:5173' | Out-Null
    Ok "已用默认浏览器打开"
} catch {
    Write-Host "  自动打开失败，请手动访问 http://localhost:5173" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "   全部就绪，浏览器已打开" -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "   页面地址 : http://localhost:5173" -ForegroundColor White
Write-Host "   接口地址 : http://localhost:8080/api/labs" -ForegroundColor Gray
Write-Host "   演示账号 : admin / teacher01 / student01   密码 123456" -ForegroundColor Gray
Write-Host ""
Write-Host "   怎么停止 : 关掉「后端」「前端」那两个新开的窗口即可" -ForegroundColor Yellow
Write-Host "   本窗口   : 可以直接关掉，不影响服务运行" -ForegroundColor DarkGray
Write-Host ""
Read-Host "  按回车关闭本窗口"
