#!/usr/bin/env pwsh
# ES2022 Class Test262 分批测试运行脚本
# 根据测试分类清单设计分批分片方案

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("all", "negative", "core", "es6", "elements", "dstr", "gen", "async", "others", "es2023")]
    [string]$Batch = "core",
    
    [Parameter(Mandatory=$false)]
    [int]$Shard = 0,
    
    [Parameter(Mandatory=$false)]
    [switch]$ListOnly,
    
    [Parameter(Mandatory=$false)]
    [int]$Timeout = 30
)

# 环境变量配置
$env:JAVA_HOME = "F:\AIDE\jdk-21.0.9+10"
$env:GRADLE_USER_HOME = "F:\AIDE\.gradle"
$env:TEMP = "F:\AIDE\tmp"
$env:TMP = "F:\AIDE\tmp"
$env:NODE_OPTIONS = "--max-old-space-size=4096"
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Duser.country=CN -Duser.language=zh"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$TestsDir = Join-Path $ProjectRoot "tests"

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Invoke-GradleTest {
    param(
        [string]$TestClass,
        [int]$TimeoutMinutes = 30
    )
    
    $timeoutSeconds = $TimeoutMinutes * 60
    
    Write-Host "Running: .\gradlew.bat :tests:test --tests `"$TestClass`" --no-daemon" -ForegroundColor Yellow
    Write-Host "Timeout: $TimeoutMinutes minutes" -ForegroundColor Yellow
    Write-Host ""
    
    if ($ListOnly) {
        Write-Host "[ListOnly] Would run test: $TestClass" -ForegroundColor Green
        return
    }
    
    Push-Location $ProjectRoot
    try {
        & .\gradlew.bat :tests:test --tests "$TestClass" --no-daemon 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Test PASSED: $TestClass" -ForegroundColor Green
        } else {
            Write-Host "Test FAILED: $TestClass (exit code: $LASTEXITCODE)" -ForegroundColor Red
        }
    } finally {
        Pop-Location
    }
}

function Get-TestCount {
    param(
        [string]$Pattern,
        [string]$Path = "tests\test262\test\language\statements\class"
    )
    
    $fullPath = Join-Path $ProjectRoot $Path
    if (Test-Path $fullPath) {
        $count = (Get-ChildItem -Path $fullPath -Filter $Pattern -Recurse -File | Measure-Object).Count
        return $count
    }
    return 0
}

# 批次定义
$Batches = @{
    "negative" = @{
        Name = "Batch 0: Negative Tests"
        Description = "负面测试 - 期望抛出错误的测试用例 (~460 测试)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassNegativeTest262Test"
        EstimatedTests = 460
        Priority = 0
        Special = $true
    }
    "core" = @{
        Name = "Batch 1: ES2022 Core"
        Description = "ES2022 Class 核心特性测试 (~500 测试)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassCoreTest262Test"
        EstimatedTests = 500
        Priority = 1
    }
    "es6" = @{
        Name = "Batch 2: ES6 Core"
        Description = "ES6 Class 核心特性测试 (subclass, super, definition) (~100 测试)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassES6CoreTest262Test"
        EstimatedTests = 100
        Priority = 2
    }
    "elements" = @{
        Name = "Batch 3: Elements"
        Description = "Class elements 目录测试 (~500 测试)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassElementsTest262Test"
        EstimatedTests = 500
        Priority = 3
    }
    "dstr" = @{
        Name = "Batch 4: Destructuring"
        Description = "解构赋值测试 (1281 测试, 分4片)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassDestructuringTest262Test"
        EstimatedTests = 1281
        Shards = 4
        Priority = 4
    }
    "gen" = @{
        Name = "Batch 5: Generators"
        Description = "生成器测试 (1156 测试, 分4片)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassGeneratorsTest262Test"
        EstimatedTests = 1156
        Shards = 4
        Priority = 5
    }
    "async" = @{
        Name = "Batch 6: Async"
        Description = "异步测试 (~700 测试, 分2片)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassAsyncTest262Test"
        EstimatedTests = 700
        Shards = 2
        Priority = 6
    }
    "others" = @{
        Name = "Batch 7: Others"
        Description = "其他测试 (computed-property-names, scope, etc.) (~500 测试)"
        TestClass = "org.mozilla.javascript.tests.ES2022ClassOthersTest262Test"
        EstimatedTests = 500
        Priority = 7
    }
    "es2023" = @{
        Name = "Batch 8: ES2023+ Features"
        Description = "ES2023+ 特性测试 (装饰器, 自动存取器) (16 测试)"
        TestClass = "org.mozilla.javascript.tests.ES2023ClassTest262Test"
        EstimatedTests = 16
        Priority = 99
    }
}

# 主逻辑
Write-Header "ES2022 Class Test262 分批测试"

if ($Batch -eq "all") {
    Write-Host "运行所有批次测试..." -ForegroundColor Yellow
    Write-Host ""
    
    foreach ($key in @("negative", "core", "es6", "elements", "dstr", "gen", "async", "others")) {
        $batchInfo = $Batches[$key]
        Write-Header $batchInfo.Name
        Write-Host "描述: $($batchInfo.Description)" -ForegroundColor White
        Write-Host "预计测试数: $($batchInfo.EstimatedTests)" -ForegroundColor White
        
        if ($batchInfo.Special) {
            Write-Host "特殊批次: 需要单独分析" -ForegroundColor Yellow
        }
        Write-Host ""
        
        Invoke-GradleTest -TestClass $batchInfo.TestClass -TimeoutMinutes $Timeout
        
        Write-Host ""
        Write-Host "批次 '$key' 完成" -ForegroundColor Green
        Write-Host ""
    }
} elseif ($Batch -eq "negative") {
    # 负面测试特殊处理
    $batchInfo = $Batches["negative"]
    
    Write-Header $batchInfo.Name
    Write-Host "描述: $($batchInfo.Description)" -ForegroundColor White
    Write-Host "预计测试数: $($batchInfo.EstimatedTests)" -ForegroundColor White
    Write-Host ""
    Write-Host "负面测试分类:" -ForegroundColor Yellow
    Write-Host "  - Parse Phase: 解析阶段错误 (词法/语法分析)" -ForegroundColor White
    Write-Host "  - Early Phase: 早期错误 (编译时语义检查)" -ForegroundColor White
    Write-Host "  - Runtime Phase: 运行时错误 (TDZ、类型错误等)" -ForegroundColor White
    Write-Host ""
    
    # 运行核心测试，后续需要分析哪些是负面测试
    Write-Host "运行测试并分析负面测试结果..." -ForegroundColor Yellow
    Invoke-GradleTest -TestClass "org.mozilla.javascript.tests.ES2022ClassCoreTest262Test" -TimeoutMinutes $Timeout
} else {
    $batchInfo = $Batches[$Batch]
    
    if (-not $batchInfo) {
        Write-Host "未知的批次: $Batch" -ForegroundColor Red
        Write-Host "可用批次: all, core, es6, elements, dstr, gen, async, others, es2023" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Header $batchInfo.Name
    Write-Host "描述: $($batchInfo.Description)" -ForegroundColor White
    Write-Host "预计测试数: $($batchInfo.EstimatedTests)" -ForegroundColor White
    
    if ($batchInfo.Shards -and $batchInfo.Shards -gt 1) {
        Write-Host "分片数: $($batchInfo.Shards)" -ForegroundColor White
        if ($Shard -gt 0) {
            Write-Host "运行分片: $Shard / $($batchInfo.Shards)" -ForegroundColor Yellow
        }
    }
    
    Write-Host ""
    
    Invoke-GradleTest -TestClass $batchInfo.TestClass -TimeoutMinutes $Timeout
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " 测试完成" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
