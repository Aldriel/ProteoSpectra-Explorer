<#
.SYNOPSIS
    Builds the portable Windows distribution of ProteoSpectra-Explorer.

.DESCRIPTION
    Produces target\dist\ProteoSpectra-Explorer-<version>-windows-x64.zip containing
    "ProteoSpectra-Explorer.exe", its own trimmed Java runtime and the "samples" folder:
    users unzip it and run the executable, no Java installation required.

    Requires JDK 21 (jlink and jpackage) and Maven 3.9. The JDK is taken from JAVA_HOME,
    otherwise from the PATH.

.PARAMETER SkipTests
    Packages without running the unit tests.

.EXAMPLE
    .\package-windows.ps1
#>
param([switch]$SkipTests)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

function Find-JdkTool([string]$Name) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\$Name.exe")) {
        return "$env:JAVA_HOME\bin\$Name.exe"
    }
    return (Get-Command $Name -ErrorAction Stop).Source
}

$pomVersion = ([xml](Get-Content ..\pom.xml -Raw -Encoding UTF8)).project.version
# jpackage only accepts numeric versions: 1.0.0-SNAPSHOT becomes 1.0.0.
$version = $pomVersion -replace '-.*$', ''
$name = 'ProteoSpectra-Explorer'
$work = 'target\jpackage'
$libs = "$work\libs"
$runtime = "$work\runtime"
$image = "$work\image"
$zip = "target\dist\$name-$version-windows-x64.zip"

Write-Host "Packaging $name $version"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }

# 1. Build the modules, then collect the desktop jar and its runtime dependencies.
$mvnArgs = @('-q', '-f', '..\pom.xml', 'install')
if ($SkipTests) { $mvnArgs += '-DskipTests' }
Invoke-Checked 'mvn' $mvnArgs
Invoke-Checked 'mvn' @('-q',
    'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies',
    '-DincludeScope=runtime', "-DoutputDirectory=$libs")
Copy-Item "target\pride-desktop-$pomVersion.jar" $libs
# JavaFX ships an empty jar plus a platform jar per module: keep the Windows ones only.
Get-ChildItem $libs -Filter 'javafx-*.jar' |
    Where-Object { $_.Name -notmatch '-win\.jar$' } |
    Remove-Item

# 2. Trimmed Java runtime. The application and JavaFX run from the class path.
#    java.scripting is required by FXML, jdk.localedata provides French formats,
#    jdk.crypto.ec the TLS ciphers used by the UniProt and PRIDE APIs.
$modules = @(
    'java.base', 'java.desktop', 'java.logging', 'java.net.http', 'java.prefs', 'java.scripting',
    'java.sql', 'java.xml', 'java.naming', 'jdk.unsupported', 'jdk.crypto.ec', 'jdk.localedata',
    'jdk.charsets', 'jdk.accessibility'
) -join ','
Invoke-Checked (Find-JdkTool 'jlink') @(
    '--add-modules', $modules, '--strip-debug', '--no-header-files', '--no-man-pages',
    '--compress=zip-6', '--output', $runtime)

# 3. Executable with its icon.
Invoke-Checked (Find-JdkTool 'jpackage') @(
    '--type', 'app-image',
    '--name', $name,
    '--app-version', $version,
    '--vendor', 'Maxime Ethier',
    '--copyright', "(c) $((Get-Date).Year) Maxime Ethier",
    '--description', 'Proteomics explorer: peptide masses, MS/MS spectra, UniProt and PRIDE',
    '--icon', 'packaging\ProteoSpectraExplorer.ico',
    '--runtime-image', $runtime,
    '--input', $libs,
    '--main-jar', "pride-desktop-$pomVersion.jar",
    '--main-class', 'com.bioinfo.proteomics.desktop.DesktopLauncher',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--dest', $image)

# 4. Sample files next to the executable, where the application looks for them.
Copy-Item -Recurse '..\samples' "$image\$name\samples"

# 5. Portable archive.
New-Item -ItemType Directory -Force (Split-Path $zip) | Out-Null
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$image\$name" -DestinationPath $zip -CompressionLevel Optimal
$sizeMb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Created $zip ($sizeMb MB)"
