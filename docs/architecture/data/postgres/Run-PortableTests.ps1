[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$BinDir)
$ErrorActionPreference = 'Stop'
$candidateBin = (Resolve-Path -LiteralPath $BinDir).Path
foreach ($program in @('initdb.exe','pg_ctl.exe','psql.exe','postgres.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $candidateBin $program) -PathType Leaf)) { throw "Missing $program" }
}
$candidateVersion = & (Join-Path $candidateBin 'postgres.exe') --version
if ($LASTEXITCODE -ne 0 -or "$candidateVersion" -notmatch 'PostgreSQL\) 16[.]') { throw 'This runner requires PostgreSQL 16 binaries.' }
$candidateRun = Join-Path ([IO.Path]::GetTempPath()) ('flash-ticket-b12-run-' + [guid]::NewGuid().ToString('N'))
$candidateData = Join-Path $candidateRun 'data'
New-Item -ItemType Directory -Path $candidateRun | Out-Null
# Reserve an available loopback port, then release it for PostgreSQL. Startup fails if another process takes it.
$candidateListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$candidateListener.Start()
$candidatePort = $candidateListener.LocalEndpoint.Port
$candidateListener.Stop()
$candidateStarted = $false
$candidatePsql = Join-Path $candidateBin 'psql.exe'
$candidateCtl = Join-Path $candidateBin 'pg_ctl.exe'
function Invoke-CandidateSql {
    param([string]$Database = 'postgres', [string]$User = 'postgres', [string[]]$SqlArguments)
    & $candidatePsql -X -h 127.0.0.1 -p $candidatePort -U $User -d $Database -v ON_ERROR_STOP=1 @SqlArguments
    if ($LASTEXITCODE -ne 0) { throw "SQL test failed for $User / $Database" }
}
try {
    Write-Output "Test directory: $candidateRun"
    Write-Output $candidateVersion
    # No real secrets/data. Trust is restricted to this temporary loopback-only test, never a deployment setting.
    & (Join-Path $candidateBin 'initdb.exe') -D $candidateData -U postgres --encoding=UTF8 --locale=C --auth=trust
    if ($LASTEXITCODE -ne 0) { throw 'Fresh initdb failed.' }
    # Wait only for pg_ctl, not the server descendants (Start-Process -Wait waits for the whole tree on Windows).
    $candidateStart = Start-Process -FilePath $candidateCtl -ArgumentList @('-D', ('"' + $candidateData + '"'), '-l', ('"' + (Join-Path $candidateRun 'server.log') + '"'), '-o', ('"-h 127.0.0.1 -p ' + $candidatePort + '"'), '-w','-t','30','start') -WindowStyle Hidden -PassThru
    if (-not $candidateStart.WaitForExit(40000)) { throw 'pg_ctl did not exit within 40 seconds.' }
    if ($candidateStart.ExitCode -ne 0) { throw 'Local PostgreSQL startup failed; inspect server.log.' }
    $candidateStarted = $true
    foreach ($file in @('00-bootstrap.sql','10-event.sql','20-booking.sql','30-payment.sql','40-ticket.sql','90-grants.sql','95-design-rationale.sql')) {
        Invoke-CandidateSql -SqlArguments @('-f', (Join-Path $PSScriptRoot $file))
    }
    Invoke-CandidateSql -SqlArguments @('-f', (Join-Path $PSScriptRoot 'tests/constraints.sql'))
    foreach ($service in @('event','booking','payment','ticket')) {
        Invoke-CandidateSql -Database "${service}_db" -SqlArguments @('-f', (Join-Path $PSScriptRoot 'tests/catalog.sql'))
        Invoke-CandidateSql -Database "${service}_db" -User "${service}_app" -SqlArguments @('-f', (Join-Path $PSScriptRoot 'tests/runtime.sql'))
        $update = @{
            event = 'UPDATE event_schema.events SET title = title;'
            booking = 'UPDATE booking_schema.orders SET row_version = row_version;'
            payment = 'UPDATE payment_schema.payment_attempts SET row_version = row_version;'
            ticket = 'UPDATE ticket_schema.tickets SET row_version = row_version;'
        }[$service]
        Invoke-CandidateSql -Database "${service}_db" -User "${service}_app" -SqlArguments @('-c','BEGIN;', '-f', (Join-Path $PSScriptRoot "tests/${service}-fixture.sql"), '-c',$update, '-c','ROLLBACK;')
    }
    $candidateDenied = 0
    foreach ($actor in @('event_app','booking_app','payment_app','ticket_app','keycloak_app','rca_observer')) {
        foreach ($database in @('event_db','booking_db','payment_db','ticket_db','keycloak_db','postgres','template1')) {
            if ($actor -ne 'rca_observer' -and $database -eq $actor.Replace('_app','_db')) {
                Invoke-CandidateSql -Database $database -User $actor -SqlArguments @('-Atc','SELECT 1;')
                continue
            }
            $denied = & $candidatePsql -X -h 127.0.0.1 -p $candidatePort -U $actor -d $database -Atc 'SELECT 1;' 2>&1
            if ($LASTEXITCODE -eq 0 -or "$denied" -notmatch 'permission denied for database') { throw "Expected CONNECT authorization denial: $actor / $database" }
            $candidateDenied++
        }
    }
    Write-Output "PASS: init, constraints, catalog, runtime DDL denials and $candidateDenied real CONNECT denials. No service/concurrency/restore claim."
}
finally {
    if ($candidateStarted -or (Test-Path -LiteralPath (Join-Path $candidateData 'postmaster.pid'))) {
        & $candidateCtl -D $candidateData -m fast -w -t 30 stop
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop test process for $candidateData; inspect this exact cluster." }
        else { Write-Output 'Stopped this run-owned PostgreSQL instance. Test files retained for inspection; no user data removed.' }
    }
}
