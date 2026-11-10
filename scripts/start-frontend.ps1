# ============================================================
#  学生实训室进组选拔系统 · 一键启动前端
#  由「启动前端.cmd」双击调用
# ============================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$fe = Join-Path $root 'frontend'

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
Write-Host "   学生实训室进组选拔系统  ·  启动前端" -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor DarkCyan
Write-Host ""

# ---------- 1. 检查 node / npm ----------
Step "检查 Node.js ..."
$nodeExe = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if (-not $nodeExe) {
    foreach ($p in @("$env:ProgramFiles\nodejs\node.exe", "${env:ProgramFiles(x86)}\nodejs\node.exe")) {
        if (Test-Path $p) { $nodeExe = $p; break }
    }
}
if (-not $nodeExe) {
    Fail "没找到 Node.js。请先安装 Node 18 以上版本：https://nodejs.org/"
}
Step "node: $(& $nodeExe -v)"

$npmExe = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
if (-not $npmExe) {
    $npmExe = Join-Path (Split-Path -Parent $nodeExe) 'npm.cmd'
}
if (-not (Test-Path $npmExe)) {
    Fail "没找到 npm.cmd（通常和 node.exe 在同一目录）"
}
# npm.cmd 所在目录要进 PATH，否则 vite 内部调用 npm 会找不到
$env:Path = (Split-Path -Parent $nodeExe) + ';' + $env:Path

if (-not (Test-Path $fe)) { Fail "找不到 frontend 目录：$fe" }

# ---------- 2. 装依赖 ----------
Push-Location $fe
if (-not (Test-Path 'node_modules')) {
    Write-Host ""
    Step "首次运行，需要安装依赖（npm install，可能要 1-3 分钟）..."
    & $npmExe install
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        Fail "npm install 失败。`n         国内网络可以先执行：npm config set registry https://registry.npmmirror.com`n         再重新双击本脚本。"
    }
    Step "依赖安装完成"
} else {
    Step "依赖已安装（node_modules 存在）"
}

# ---------- 3. 端口检查 ----------
$busy = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    Pop-Location
    Fail "5173 端口已被占用（可能已经开过一个前端窗口）。先关掉那个窗口再试。"
}

# ---------- 4. 启动 ----------
Write-Host ""
Write-Host "  启动开发服务器 ..." -ForegroundColor Yellow
Write-Host "  访问地址 : http://localhost:5173" -ForegroundColor Green
Write-Host "  后端接口 : 需要后端在 8080 运行（由 Vite 代理 /api）" -ForegroundColor Gray
Write-Host "  停止服务 : 按 Ctrl + C，或直接关掉这个窗口" -ForegroundColor Gray
Write-Host ""
Write-Host "  提示：页面目前用的还是 mock 数据，接后端请参考 src\api\README.md" -ForegroundColor DarkYellow
Write-Host ""

& $npmExe run dev
$exitCode = $LASTEXITCODE
Pop-Location

Write-Host ""
if ($exitCode -ne 0) {
    Write-Host "  前端异常退出（exit=$exitCode）。" -ForegroundColor Red
    Write-Host "  常见原因：端口被占、node_modules 装坏了（删掉 node_modules 重装）" -ForegroundColor DarkGray
}
Read-Host "  按回车退出"
