# USBThermalPrinter B4X Library - Build & Package Script
# Requires: JDK 8+ (javac, jar), PowerShell

param(
    [string]$LibraryVersion = "1.0.0"
)

$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
$SrcDir = Join-Path $RootDir "src"
$BuildDir = Join-Path $RootDir "build"
$ClassesDir = Join-Path $BuildDir "classes"
$OutputDir = Join-Path $RootDir "output"
$LibName = "escposthermal"
$JarName = "$LibName.jar"
$XmlName = "$LibName.xml"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " USBThermalPrinter B4J Library Builder" -ForegroundColor Cyan
Write-Host " Version: $LibraryVersion" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ─── Clean ──────────────────────────────────────────────────────────
Write-Host "`n[1/5] Cleaning build directories..." -ForegroundColor Yellow
if (Test-Path $BuildDir)  { Remove-Item -Recurse -Force $BuildDir }
if (Test-Path $OutputDir) { Remove-Item -Recurse -Force $OutputDir }
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

# ─── Find Java Sources ──────────────────────────────────────────────
$JavaFiles = Get-ChildItem -Path $SrcDir -Recurse -Filter "*.java"
if ($JavaFiles.Count -eq 0) {
    Write-Host "ERROR: No .java files found in $SrcDir" -ForegroundColor Red
    exit 1
}
Write-Host "`n[2/5] Found $($JavaFiles.Count) Java source files:" -ForegroundColor Yellow
foreach ($f in $JavaFiles) {
    Write-Host "       $($f.FullName)"
}

# ─── Find JDK ──────────────────────────────────────────────────────
$javacPath = $null
$jarPath = $null

# Check common locations
$candidateJdks = @(
    "C:\Java\jdk-19.0.2",
    "C:\Program Files\Java\jdk-19",
    "C:\Program Files\Java\jdk-14",
    "C:\Program Files\Java\jdk-11",
    "C:\Program Files\Eclipse Adoptium\jdk-11.0.18.10-hotspot"
)

# Also try JAVA_HOME / B4J_JAVA_HOME env vars
if ($env:JAVA_HOME) { $candidateJdks = @($env:JAVA_HOME) + $candidateJdks }
if ($env:B4J_JAVA_HOME) { $candidateJdks = @($env:B4J_JAVA_HOME) + $candidateJdks }

foreach ($jdk in $candidateJdks) {
    $testJavac = Join-Path $jdk "bin\javac.exe"
    if (Test-Path $testJavac) {
        $javacExe = $testJavac
        $jarExe = Join-Path $jdk "bin\jar.exe"
        Write-Host "       Found JDK: $jdk" -ForegroundColor Green
        break
    }
}

if (-not $javacExe) {
    # Last resort: check PATH
    $pathJavac = (Get-Command javac -ErrorAction SilentlyContinue)
    if ($pathJavac) {
        $javacExe = "javac"
        $jarExe = "jar"
        Write-Host "       Using javac from PATH" -ForegroundColor Yellow
    } else {
        Write-Host "ERROR: No JDK found. Set JAVA_HOME or install JDK." -ForegroundColor Red
        exit 1
    }
}

# ─── Compile (4 files, no need for @argfile) ──────────────────────
Write-Host "`n[3/5] Compiling Java sources..." -ForegroundColor Yellow

# --release 11 produces bytecode compatible with Java 11+ (B4J uses 14.0.1)
$CompileArgs = @(
    "-encoding", "UTF-8",
    "-sourcepath", $SrcDir,
    "-d", $ClassesDir,
    "--release", "11"
) + ($JavaFiles | ForEach-Object { $_.FullName })

$javacOutput = & "$javacExe" @CompileArgs 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "COMPILATION FAILED:" -ForegroundColor Red
    Write-Host $javacOutput
    exit 1
}
Write-Host "       Compilation successful." -ForegroundColor Green

# ─── Package JAR ────────────────────────────────────────────────────
Write-Host "`n[4/5] Packaging JAR..." -ForegroundColor Yellow
$JarPath = Join-Path $OutputDir $JarName
Push-Location $ClassesDir
$jarOutput = & "$jarExe" cvf "$JarPath" * 2>&1
Pop-Location
if ($LASTEXITCODE -ne 0) {
    Write-Host "JAR PACKAGING FAILED:" -ForegroundColor Red
    Write-Host $jarOutput
    exit 1
}
Write-Host "       JAR created: $JarPath" -ForegroundColor Green

# ─── Copy XML descriptor ────────────────────────────────────────────────
Write-Host "`n[5/5] Copying B4J XML descriptor..." -ForegroundColor Yellow
$XmlPath = Join-Path $OutputDir $XmlName
Copy-Item (Join-Path $RootDir $XmlName) $XmlPath -Force
Write-Host "       XML copied: $XmlPath" -ForegroundColor Green

# ─── Summary ────────────────────────────────────────────────────────
$JarSize = (Get-Item $JarPath).Length
$XmlSize = (Get-Item $XmlPath).Length

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " BUILD COMPLETE" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Output directory: $OutputDir" -ForegroundColor White
Write-Host "  JAR: $JarName ($([math]::Round($JarSize/1KB, 2)) KB)" -ForegroundColor White
Write-Host "  XML: $XmlName ($([math]::Round($XmlSize/1KB, 2)) KB)" -ForegroundColor White
Write-Host ""
Write-Host "  To use in B4J:" -ForegroundColor Gray
Write-Host "    1. Copy $JarName and $XmlName to your B4J Additional Libraries folder" -ForegroundColor Gray
Write-Host "    2. In B4J IDE: Libraries tab -> Refresh" -ForegroundColor Gray
Write-Host "    3. Tick escposthermal in the libraries list" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
