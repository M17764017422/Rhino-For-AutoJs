#!/usr/bin/env pwsh
# ES2022 Class Test Failure Analysis Script
# Analyzes test failure reports and categorizes them

param(
    [Parameter(Mandatory=$false)]
    [string]$Batch = "core",
    
    [Parameter(Mandatory=$false)]
    [string]$ReportDir = "",
    
    [Parameter(Mandatory=$false)]
    [int]$MaxAnalyze = 500
)

$ProjectRoot = Split-Path -Parent $PSScriptRoot

# Map batch names to test class names
$BatchToTestClass = @{
    "negative" = "ES2022ClassNegativeTest262Test"
    "core" = "ES2022ClassCoreTest262Test"
    "es6" = "ES2022ClassES6CoreTest262Test"
    "elements" = "ES2022ClassElementsTest262Test"
    "dstr" = "ES2022ClassDestructuringTest262Test"
    "gen" = "ES2022ClassGeneratorsTest262Test"
    "async" = "ES2022ClassAsyncTest262Test"
    "others" = "ES2022ClassOthersTest262Test"
    "es2023" = "ES2023ClassTest262Test"
    "full" = "ES2022ClassTest262Test"
}

# Determine test class
$testClass = $BatchToTestClass[$Batch]
if (-not $testClass) {
    Write-Host "Unknown batch: $Batch" -ForegroundColor Red
    Write-Host "Available batches: $($BatchToTestClass.Keys -join ', ')" -ForegroundColor Yellow
    exit 1
}

# Determine report directory
if ($ReportDir -eq "") {
    $ReportDir = Join-Path $ProjectRoot "tests\build\reports\tests\test\org.mozilla.javascript.tests.$testClass\test262ClassCase(String,-TestMode,-boolean,-Test262Case)"
}

# Check if report directory exists
if (-not (Test-Path $ReportDir)) {
    Write-Host "Report directory not found: $ReportDir" -ForegroundColor Red
    Write-Host "Run the test first to generate the report." -ForegroundColor Yellow
    exit 1
}

Write-Host "Analyzing test failures for batch: $Batch" -ForegroundColor Cyan
Write-Host "Test class: $testClass" -ForegroundColor Cyan
Write-Host "Report directory: $ReportDir" -ForegroundColor Cyan
Write-Host ""

# Categories
$categories = @{
    "NEGATIVE_TEST" = @()
    "SYNTAX_ERROR" = @()
    "RUNTIME_ERROR" = @()
    "BYTECODE_ERROR" = @()
    "CODEGEN_ERROR" = @()
    "EXPECTED_FEATURE" = @()
    "OTHER" = @()
}

# Negative test patterns (tests that expect errors)
$negativePatterns = @(
    "expected-early-error",
    "invalid-syntax", 
    "syntax-error",
    "invalid",
    "not-valid",
    "forbidden",
    "unexpected",
    "duplicate",
    "redeclaration",
    "not-strict-mode",
    "no-annex-b",
    "negative"
)

$files = Get-ChildItem -Path $ReportDir -Filter "*.html" | Where-Object { $_.Name -match '\[\d+\]\.html$' }

$totalAnalyzed = 0

foreach ($file in $files | Select-Object -First $MaxAnalyze) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $totalAnalyzed++
    
    # Extract test name from h1 tag
    $testFile = "unknown"
    $testMode = "UNKNOWN"
    
    if ($content -match '<h1>\[\d+\]\s+([^,]+\.js)\s*,\s*(\w+)') {
        $testFile = $matches[1].Trim()
        $testMode = $matches[2]
    }
    
    # Extract error message - handle multiple exception types
    $errorMsg = ""
    
    # Try to match AssertionFailedError
    $preMatch = [regex]::Match($content, '<pre[^>]*>(org\.opentest4j\.AssertionFailedError:.+?)</pre>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if ($preMatch.Success) {
        $errorMsg = $preMatch.Value
    } else {
        # Try to match VerifyError
        $preMatch = [regex]::Match($content, '<pre[^>]*>(java\.lang\.VerifyError:.+?)</pre>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
        if ($preMatch.Success) {
            $errorMsg = $preMatch.Value
        } else {
            # Try to match other exceptions (IndexOutOfBoundsException, etc.)
            $preMatch = [regex]::Match($content, '<pre[^>]*>(java\.lang\.\w+Exception:.+?)</pre>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
            if ($preMatch.Success) {
                $errorMsg = $preMatch.Value
            }
        }
    }
    
    # Clean up error message
    if ($errorMsg -ne "") {
        $errorMsg = $errorMsg -replace '<[^>]+>', ''
        $errorMsg = $errorMsg -replace '&gt;', '>' -replace '&lt;', '<' -replace '&amp;', '&' -replace '&#39;', "'"
        $errorMsg = $errorMsg -replace '[\r\n\s]+', ' '
    }
    
    # Check if test file name contains negative patterns
    $isNegative = $false
    foreach ($pattern in $negativePatterns) {
        if ($testFile -match [regex]::Escape($pattern)) {
            $isNegative = $true
            break
        }
    }
    
    # Classification logic
    $category = "OTHER"
    
    # Check for VerifyError (bytecode generation issues)
    if ($errorMsg -match "VerifyError|Bad type on operand stack") {
        $category = "BYTECODE_ERROR"
    }
    # Check for CodeGenerator internal errors
    elseif ($errorMsg -match "IndexOutOfBoundsException|CodeGenerator|functions list is null") {
        $category = "CODEGEN_ERROR"
    }
    # Check for syntax error
    elseif ($errorMsg -match "AssertionFailedError:\s*syntax error") {
        if ($isNegative) {
            $category = "NEGATIVE_TEST"
        } else {
            $category = "SYNTAX_ERROR"
        }
    }
    # Check for TypeError runtime errors
    elseif ($errorMsg -match "AssertionFailedError:\s*TypeError") {
        $category = "RUNTIME_ERROR"
    }
    # Check for ReferenceError runtime errors
    elseif ($errorMsg -match "AssertionFailedError:\s*ReferenceError") {
        $category = "RUNTIME_ERROR"
    }
    # Check for RangeError runtime errors
    elseif ($errorMsg -match "AssertionFailedError:\s*RangeError") {
        $category = "RUNTIME_ERROR"
    }
    # Check for generic AssertionFailedError
    elseif ($errorMsg -match "AssertionFailedError") {
        if ($isNegative) {
            $category = "NEGATIVE_TEST"
        } else {
            if ($errorMsg -match "private field|private method|Cannot access") {
                $category = "RUNTIME_ERROR"
            } elseif ($errorMsg -match "super") {
                $category = "RUNTIME_ERROR"
            } else {
                $category = "OTHER"
            }
        }
    }
    # Missing feature detection
    elseif ($errorMsg -match "not (a )?function|undefined is not|has no") {
        $category = "EXPECTED_FEATURE"
    }
    
    # Store result
    $errShort = if ($errorMsg.Length -gt 0) { $errorMsg.Substring(0, [Math]::Min(400, $errorMsg.Length)) } else { "" }
    $result = @{
        File = $testFile
        Mode = $testMode
        Error = $errShort
        Category = $category
        IsNegative = $isNegative
    }
    
    $categories[$category] += $result
}

# Output summary
Write-Host ""
Write-Host "=" * 70
Write-Host "ES2022 Class Test Failure Analysis Report - Batch: $Batch"
Write-Host "=" * 70
Write-Host ""
Write-Host "Total files analyzed: $totalAnalyzed"
Write-Host ""

Write-Host "Category Breakdown:"
Write-Host "-" * 40

foreach ($cat in @("NEGATIVE_TEST", "SYNTAX_ERROR", "RUNTIME_ERROR", "BYTECODE_ERROR", "CODEGEN_ERROR", "EXPECTED_FEATURE", "OTHER")) {
    $count = $categories[$cat].Count
    if ($totalAnalyzed -gt 0) {
        $percent = [math]::Round($count / $totalAnalyzed * 100, 1)
    } else {
        $percent = 0
    }
    Write-Host ("{0,-20} : {1,5} ({2,5}%)" -f $cat, $count, $percent)
}

# Sub-categorize RUNTIME_ERROR
Write-Host ""
Write-Host "RUNTIME_ERROR Sub-categories:"
Write-Host "-" * 40
$privateFieldErrors = ($categories["RUNTIME_ERROR"] | Where-Object { $_.Error -match "private field" }).Count
$privateMethodErrors = ($categories["RUNTIME_ERROR"] | Where-Object { $_.Error -match "private method" }).Count
$superErrors = ($categories["RUNTIME_ERROR"] | Where-Object { $_.Error -match "super" }).Count
$otherRuntimeErrors = $categories["RUNTIME_ERROR"].Count - $privateFieldErrors - $privateMethodErrors - $superErrors

Write-Host "  Private Field Access  : $privateFieldErrors"
Write-Host "  Private Method Access : $privateMethodErrors"
Write-Host "  Super-related         : $superErrors"
Write-Host "  Other Runtime         : $otherRuntimeErrors"

# Mode breakdown
Write-Host ""
Write-Host "Mode Breakdown:"
Write-Host "-" * 40
$interpretedTotal = ($categories.Values | ForEach-Object { $_ } | Where-Object { $_.Mode -eq "INTERPRETED" }).Count
$compiledTotal = ($categories.Values | ForEach-Object { $_ } | Where-Object { $_.Mode -eq "COMPILED" }).Count
Write-Host "  INTERPRETED mode failures : $interpretedTotal"
Write-Host "  COMPILED mode failures    : $compiledTotal"

# BYTECODE_ERROR analysis
Write-Host ""
Write-Host "BYTECODE_ERROR Analysis:"
Write-Host "-" * 40
$bytecodeCompiled = ($categories["BYTECODE_ERROR"] | Where-Object { $_.Mode -eq "COMPILED" }).Count
Write-Host "  (BYTECODE_ERROR only affects COMPILED mode)"
Write-Host "  BYTECODE_ERROR in COMPILED mode: $bytecodeCompiled"

Write-Host ""
Write-Host "=" * 70
Write-Host "Sample Errors by Category"
Write-Host "=" * 70

foreach ($cat in @("NEGATIVE_TEST", "SYNTAX_ERROR", "RUNTIME_ERROR", "BYTECODE_ERROR", "CODEGEN_ERROR", "EXPECTED_FEATURE", "OTHER")) {
    if ($categories[$cat].Count -gt 0) {
        Write-Host ""
        Write-Host "--- $cat ($($categories[$cat].Count) tests) ---"
        $samples = $categories[$cat] | Select-Object -First 3
        foreach ($s in $samples) {
            Write-Host "  File: $($s.File)"
            Write-Host "  Mode: $($s.Mode)"
            $errShort = $s.Error.Substring(0, [Math]::Min(150, $s.Error.Length))
            Write-Host "  Error: $errShort ..."
            Write-Host ""
        }
    }
}

# Export detailed results to file
$outputPath = Join-Path $ProjectRoot "test_failure_analysis_$Batch.txt"
$output = [System.Text.StringBuilder]::new()
[void]$output.AppendLine("=" * 70)
[void]$output.AppendLine("ES2022 Class Test Failure Analysis Report - Batch: $Batch")
[void]$output.AppendLine("Test Class: $testClass")
[void]$output.AppendLine("=" * 70)
[void]$output.AppendLine("")
[void]$output.AppendLine("Total analyzed: $totalAnalyzed")
[void]$output.AppendLine("")

foreach ($cat in @("NEGATIVE_TEST", "SYNTAX_ERROR", "RUNTIME_ERROR", "BYTECODE_ERROR", "CODEGEN_ERROR", "EXPECTED_FEATURE", "OTHER")) {
    $count = $categories[$cat].Count
    if ($totalAnalyzed -gt 0) {
        $percent = [math]::Round($count / $totalAnalyzed * 100, 1)
    } else {
        $percent = 0
    }
    [void]$output.AppendLine("$cat : $count ($percent%)")
}

[void]$output.AppendLine("")
[void]$output.AppendLine("RUNTIME_ERROR Sub-categories:")
[void]$output.AppendLine("  Private Field Access: $privateFieldErrors")
[void]$output.AppendLine("  Private Method Access: $privateMethodErrors")
[void]$output.AppendLine("  Super-related: $superErrors")
[void]$output.AppendLine("  Other Runtime: $otherRuntimeErrors")

[void]$output.AppendLine("")
[void]$output.AppendLine("Mode Breakdown:")
[void]$output.AppendLine("  INTERPRETED mode failures: $interpretedTotal")
[void]$output.AppendLine("  COMPILED mode failures: $compiledTotal")

[void]$output.AppendLine("")
[void]$output.AppendLine("=" * 70)
[void]$output.AppendLine("ROOT CAUSE ANALYSIS")
[void]$output.AppendLine("=" * 70)
[void]$output.AppendLine("")

[void]$output.AppendLine("1. RUNTIME_ERROR ($($categories['RUNTIME_ERROR'].Count) tests)")
[void]$output.AppendLine("   - Main issue: Private field/method access semantics")
[void]$output.AppendLine("   - Error pattern: 'Cannot access private field X on non-class instance'")
[void]$output.AppendLine("   - Error pattern: 'Private field X not found on object'")
[void]$output.AppendLine("   - Likely cause: Incorrect 'this' binding in class contexts")
[void]$output.AppendLine("")

[void]$output.AppendLine("2. SYNTAX_ERROR ($($categories['SYNTAX_ERROR'].Count) tests)")
[void]$output.AppendLine("   - Main issue: Parser rejects valid private name syntax")
[void]$output.AppendLine("   - Error pattern: 'syntax error' on private identifiers")
[void]$output.AppendLine("   - Likely cause: Incomplete private name parsing support")
[void]$output.AppendLine("")

[void]$output.AppendLine("3. BYTECODE_ERROR ($($categories['BYTECODE_ERROR'].Count) tests)")
[void]$output.AppendLine("   - Main issue: JVM bytecode verification fails")
[void]$output.AppendLine("   - Error pattern: 'Bad type on operand stack'")
[void]$output.AppendLine("   - Error pattern: 'Type X is not assignable to Y'")
[void]$output.AppendLine("   - Affects: COMPILED mode only")
[void]$output.AppendLine("   - Likely cause: Incorrect bytecode generation for private members")
[void]$output.AppendLine("")

[void]$output.AppendLine("4. CODEGEN_ERROR ($($categories['CODEGEN_ERROR'].Count) tests)")
[void]$output.AppendLine("   - Main issue: Internal compiler errors")
[void]$output.AppendLine("   - Error pattern: 'IndexOutOfBoundsException: Function index 0 but functions list is null'")
[void]$output.AppendLine("   - Likely cause: Missing function node during code generation")
[void]$output.AppendLine("")

[void]$output.AppendLine("=" * 70)
[void]$output.AppendLine("DETAILED RESULTS")
[void]$output.AppendLine("=" * 70)

foreach ($cat in @("NEGATIVE_TEST", "SYNTAX_ERROR", "RUNTIME_ERROR", "BYTECODE_ERROR", "CODEGEN_ERROR", "EXPECTED_FEATURE", "OTHER")) {
    if ($categories[$cat].Count -gt 0) {
        [void]$output.AppendLine("")
        [void]$output.AppendLine("--- $cat ($($categories[$cat].Count) tests) ---")
        $count = 0
        foreach ($s in $categories[$cat]) {
            $count++
            if ($count -le 10) {
                [void]$output.AppendLine("")
                [void]$output.AppendLine("File: $($s.File)")
                [void]$output.AppendLine("Mode: $($s.Mode)")
                [void]$output.AppendLine("Error: $($s.Error.Substring(0, [Math]::Min(250, $s.Error.Length)))")
            }
        }
        if ($categories[$cat].Count -gt 10) {
            [void]$output.AppendLine("")
            [void]$output.AppendLine("... and $($categories[$cat].Count - 10) more tests in this category")
        }
    }
}

[System.IO.File]::WriteAllText($outputPath, $output.ToString())
Write-Host "Detailed results exported to: $outputPath"