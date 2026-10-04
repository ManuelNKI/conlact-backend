# Arranque de Spring en Windows con JDK 25 y sockets temporales locales.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS

$candidateHomes = @($env:JAVA_HOME,
    [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
    [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine'))
$javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($javacCommand) {
    $candidateHomes += Split-Path -Parent (Split-Path -Parent $javacCommand.Source)
}

$selectedJavaHome = $null
foreach ($candidateHome in ($candidateHomes | Where-Object { $_ } | Select-Object -Unique)) {
    $javacPath = Join-Path $candidateHome 'bin/javac.exe'
    if (Test-Path -LiteralPath $javacPath) {
        $javacVersion = & $javacPath -version 2>&1
        if ($LASTEXITCODE -eq 0 -and ([string]$javacVersion) -match '^javac 25(?:[.\s]|$)') {
            $selectedJavaHome = $candidateHome
            break
        }
    }
}
if (!$selectedJavaHome) {
    throw 'No se encontró JDK 25. Configure JAVA_HOME con la carpeta del JDK 25 instalado.'
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $socketDirectory = Join-Path $PSScriptRoot 'target/java-sockets'
    New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
    $env:JAVA_HOME = $selectedJavaHome
    $socketOption = '-Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '"'
    $env:JAVA_TOOL_OPTIONS = ($previousJavaOptions + ' ' + $socketOption).Trim()

    Write-Host "Usando JDK 25: $selectedJavaHome"
    & .\mvnw.cmd '-Dspring-boot.run.profiles=local' spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "El backend terminó con código $LASTEXITCODE. Revise el error anterior."
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
