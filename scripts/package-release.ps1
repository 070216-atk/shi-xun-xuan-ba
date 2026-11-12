<#
    打发布包：把前端构建产物塞进后端 jar，输出「一个 jar 就是整个系统」。
    
    产物：
        deploy\选拔系统.jar      ← 双击 启动.cmd 就能跑
        deploy\启动.cmd
        deploy\README.txt
    
    用法（在项目根目录）：
        powershell -ExecutionPolicy Bypass -File scripts\package-release.ps1
#>

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$staticDir = Join-Path $backend 'src\main\resources\static'
$deploy = Join-Path $root 'deploy'

function Resolve-Maven {
    $m = Get-Command mvn -ErrorAction SilentlyContinue
    if ($m) { return $m.Source }
    if ($env:MAVEN_HOME) {
        $p = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
        if (Test-Path $p) { return $p }
    }
    if ($env:M2_HOME) {
        $p = Join-Path $env:M2_HOME 'bin\mvn.cmd'
        if (Test-Path $p) { return $p }
    }
    $hits = @(
        Get-ChildItem "$env:USERPROFILE\apache-maven-*\bin\mvn.cmd" -ErrorAction SilentlyContinue
        Get-ChildItem "$env:USERPROFILE\scoop\apps\maven\current\bin\mvn.cmd" -ErrorAction SilentlyContinue
        Get-ChildItem "C:\Program Files\Apache\maven\bin\mvn.cmd" -ErrorAction SilentlyContinue
        Get-ChildItem "$root\..\*\tools\apache-maven-*\bin\mvn.cmd" -ErrorAction SilentlyContinue
    )
    if ($hits.Count -gt 0) { return $hits[0].FullName }
    throw '找不到 Maven：请把 mvn 加到 PATH，或者设置 MAVEN_HOME 环境变量'
}

$mvn = Resolve-Maven

Write-Host ''
Write-Host '================ 打发布包 ================' -ForegroundColor Cyan

# ---------- 1. 构建前端 ----------
Write-Host '[1/5] 构建前端...' -ForegroundColor Yellow
Push-Location $frontend
try {
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'npm run build 失败' }
} finally {
    Pop-Location
}
$dist = Join-Path $frontend 'dist'
if (-not (Test-Path $dist)) { throw '找不到 frontend\dist' }

# ---------- 2. 复制到后端 static ----------
Write-Host '[2/5] 把前端产物放进后端 static...' -ForegroundColor Yellow
if (Test-Path $staticDir) { Remove-Item -Recurse -Force $staticDir }
New-Item -ItemType Directory -Path $staticDir -Force | Out-Null
Copy-Item -Path (Join-Path $dist '*') -Destination $staticDir -Recurse -Force

# ---------- 3. 打包后端 ----------
Write-Host '[3/5] 打包后端...' -ForegroundColor Yellow
Push-Location $backend
try {
    & $mvn -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw 'mvn package 失败' }
} finally {
    Pop-Location
}

$jar = Get-ChildItem -Path (Join-Path $backend 'target') -Filter 'selection-*.jar' |
       Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw '找不到打出来的 jar' }

# ---------- 4. 输出到 deploy ----------
Write-Host '[4/5] 输出到 deploy\ ...' -ForegroundColor Yellow
if (-not (Test-Path $deploy)) { New-Item -ItemType Directory -Path $deploy -Force | Out-Null }
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $deploy '选拔系统.jar') -Force

$cmd = @'
@echo off
chcp 65001 >nul
title 实训室进组选拔系统
cd /d "%~dp0"
echo 正在启动... 启动后浏览器访问 http://localhost:8080
start "" http://localhost:8080
java -jar "选拔系统.jar"
pause
'@
[IO.File]::WriteAllText((Join-Path $deploy '启动.cmd'), $cmd, (New-Object System.Text.UTF8Encoding($false)))

$readme = @'
实训室进组选拔系统 — 独立运行包
====================================

前提：
  1. 装 JDK 17 或 21
  2. 本机有 MySQL 8，且已导入数据库（把 schema.sql 和 seed.sql 跑一遍）
  3. 在 jar 同目录放一份 application-local.yml（数据库密码、JWT 密钥）

启动：
  双击 启动.cmd，然后浏览器访问 http://localhost:8080

说明：
  这个 jar 已经把前端页面打包进去了，不需要单独启动前端。
  数据和配置不在 jar 里，改数据库密码请改 application-local.yml。
'@
[IO.File]::WriteAllText((Join-Path $deploy 'README.txt'), $readme, (New-Object System.Text.UTF8Encoding($true)))

# ---------- 5. 清理 ----------
Write-Host '[5/5] 清理临时文件...' -ForegroundColor Yellow
Remove-Item -Recurse -Force $staticDir

$size = [math]::Round((Get-Item (Join-Path $deploy '选拔系统.jar')).Length / 1MB, 1)
Write-Host ''
Write-Host '---------------- 完成 ----------------' -ForegroundColor Green
Write-Host "  deploy\选拔系统.jar   $size MB"
Write-Host '  deploy\启动.cmd'
Write-Host '  deploy\README.txt'
Write-Host ''
Write-Host '  这个 jar 已经把前端页面打进去了，双击 启动.cmd 即可，不用再开前端。' -ForegroundColor Cyan
