[CmdletBinding(DefaultParameterSetName = 'New')]
param(
    [Parameter(Mandatory, ParameterSetName = 'New')][string]$BuyerSubject,
    [Parameter(ParameterSetName = 'New')][string]$Amount = '100000',
    [Parameter(ParameterSetName = 'New')][ValidateRange(5, 3600)][int]$LifetimeSeconds = 900,
    [Parameter(ParameterSetName = 'New')][string]$OutputFile,
    [Parameter(Mandatory, ParameterSetName = 'Replay')][string]$Replay,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$module = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $module
try {
    if (-not $SkipBuild) {
        & .\mvnw.cmd compile dependency:build-classpath '-Dmdep.outputFile=target/simulator-classpath.txt' -B -ntp
        if ($LASTEXITCODE -ne 0) { throw 'Simulator build failed' }
    }
    $classpath = (Join-Path $module 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath 'target/simulator-classpath.txt' -Raw).Trim()
    $arguments = @('-cp', $classpath, 'com.flashticket.payment_service.dev.booking.BookingSimulator')
    if ($PSCmdlet.ParameterSetName -eq 'Replay') {
        $arguments += @('--replay', $Replay)
    } else {
        $arguments += @('--buyer-subject', $BuyerSubject, '--amount', $Amount, '--lifetime-seconds', "$LifetimeSeconds")
        if ($OutputFile) { $arguments += @('--output', $OutputFile) }
    }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Publish failed. Replay the saved command after resolving the connection/routing error.' }
} finally {
    Pop-Location
}
