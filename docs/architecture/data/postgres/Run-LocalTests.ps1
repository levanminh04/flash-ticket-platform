[CmdletBinding()]
param([string]$Image = 'postgres:16')

$ErrorActionPreference = 'Stop'
$candidateFiles = @('00-bootstrap.sql','10-event.sql','20-booking.sql','30-payment.sql','40-ticket.sql','90-grants.sql','95-design-rationale.sql')
$candidateRoot = [IO.Path]::GetFullPath($PSScriptRoot)
foreach ($candidateFile in $candidateFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $candidateRoot $candidateFile))) { throw "Missing baseline file: $candidateFile" }
}
if ($Image -notmatch '^postgres:(16([.][0-9]+)?)([-a-z0-9.]*)?(@sha256:[a-f0-9]{64})?$') {
    throw 'Only an explicitly supplied official PostgreSQL 16 image is accepted by this test runner.'
}
& docker info --format '{{.OSType}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker daemon unavailable; no test resources were created.' }
$candidateRunId = [guid]::NewGuid().ToString('N')
$candidateContainer = "b12-pg-test-$candidateRunId"
$candidateCreated = $false
function Invoke-CandidateDocker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed ($($Arguments[0])); stopping test run." }
}
try {
    # No host ports, no host mounts, no network. Trust is limited to this disposable fixture instance.
    Invoke-CandidateDocker -Arguments @('run','--detach','--name',$candidateContainer,'--label',"flash-ticket.b12-test=$candidateRunId",'--network','none','--env','POSTGRES_HOST_AUTH_METHOD=trust',$Image)
    $candidateCreated = $true
    $candidateReady = $false
    for ($candidateAttempt = 0; $candidateAttempt -lt 40; $candidateAttempt++) {
        & docker exec $candidateContainer pg_isready -U postgres -d postgres *> $null
        if ($LASTEXITCODE -eq 0) { $candidateReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $candidateReady) { throw 'Disposable PostgreSQL did not become ready in 20 seconds.' }
    Invoke-CandidateDocker -Arguments @('inspect','--format','{{.Image}}',$candidateContainer)
    Invoke-CandidateDocker -Arguments @('exec',$candidateContainer,'psql','-X','-U','postgres','-d','postgres','-Atc','SHOW server_version;')
    Invoke-CandidateDocker -Arguments @('cp',"$candidateRoot/.","${candidateContainer}:/b12")
    foreach ($candidateFile in $candidateFiles) {
        Invoke-CandidateDocker -Arguments @('exec',$candidateContainer,'psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d','postgres','-f',"/b12/$candidateFile")
    }
    Invoke-CandidateDocker -Arguments @('exec',$candidateContainer,'psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d','postgres','-f','/b12/tests/constraints.sql')
    foreach ($candidateService in @('event','booking','payment','ticket')) {
        Invoke-CandidateDocker -Arguments @('exec',$candidateContainer,'psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d',"${candidateService}_db",'-f','/b12/tests/catalog.sql')
        Invoke-CandidateDocker -Arguments @('exec',$candidateContainer,'psql','-X','-v','ON_ERROR_STOP=1','-U',"${candidateService}_app",'-d',"${candidateService}_db",'-Atc','SELECT 1;')
        foreach ($candidateOther in @('event','booking','payment','ticket','keycloak')) {
            if ($candidateOther -eq $candidateService) { continue }
            $candidateDeniedOutput = & docker exec $candidateContainer psql -X -U "${candidateService}_app" -d "${candidateOther}_db" -Atc 'SELECT 1;' 2>&1
            if ($LASTEXITCODE -eq 0 -or "$candidateDeniedOutput" -notmatch 'permission denied for database') {
                throw "Expected database authorization denial: $candidateService -> $candidateOther"
            }
        }
    }
    Write-Output 'PASS: fresh init, constraints, catalog/default privileges and live cross-database connection denials. No service/concurrency/restore claim.'
}
finally {
    if ($candidateCreated) {
        $candidateOwnership = & docker inspect --format '{{index .Config.Labels "flash-ticket.b12-test"}}' $candidateContainer
        if ($LASTEXITCODE -eq 0 -and $candidateOwnership.Trim() -eq $candidateRunId -and $candidateContainer -match '^b12-pg-test-[a-f0-9]{32}$') {
            & docker stop --time 10 $candidateContainer | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop owned test container $candidateContainer; retained for inspection." }
            else {
                & docker rm --volumes $candidateContainer | Out-Null
                if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove owned test container $candidateContainer." }
                else { Write-Output 'Removed only this run-owned disposable container and its anonymous data volume; fixture data is not retained.' }
            }
        }
        else { Write-Warning 'Cleanup refused: test-container ownership could not be verified.' }
    }
}
