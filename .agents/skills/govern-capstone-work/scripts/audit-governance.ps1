[CmdletBinding()]
param(
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function ConvertTo-AsciiText {
    param([string]$Text)

    $decomposed = $Text.Normalize([System.Text.NormalizationForm]::FormD)
    $builder = [System.Text.StringBuilder]::new()
    foreach ($character in $decomposed.ToCharArray()) {
        $category = [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($character)
        if ($category -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$builder.Append($character)
        }
    }

    return $builder.ToString().Replace([char]0x0111, [char]0x0064).Replace([char]0x0110, [char]0x0044).Replace([char]0x2013, [char]0x002D).Replace([char]0x2014, [char]0x002D)
}

function Get-ArtifactStatus {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    $text = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path)
    $match = [regex]::Match($text, 'Trang thai:\s*`?(DRAFT|REVIEW_READY|APPROVED)`?', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $match.Success) {
        return $null
    }

    return $match.Groups[1].Value.ToUpperInvariant()
}

function Get-ArtifactVersion {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    $text = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path)
    $match = [regex]::Match($text, 'Phien ban:\s*`?([^`\r\n]+)`?', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $match.Success) {
        return $null
    }

    return $match.Groups[1].Value.Trim()
}

function Get-RepoRelativePath {
    param(
        [string]$Root,
        [string]$Path
    )

    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd('\').TrimEnd('/')
    $pathFull = [System.IO.Path]::GetFullPath($Path)
    $prefix = $rootFull + [System.IO.Path]::DirectorySeparatorChar
    if (-not $pathFull.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path '$pathFull' is outside repository root '$rootFull'."
    }

    return $pathFull.Substring($prefix.Length).Replace('\', '/')
}

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
}

try {
    $resolvedRoot = (Resolve-Path -LiteralPath $RepoRoot).Path.TrimEnd('\')
} catch {
    Write-Error "Repository path does not exist: $RepoRoot"
    exit 1
}

$expectedRoot = 'D:\Project\flash-ticket-platform'
if (-not $resolvedRoot.Equals($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    $errors.Add("Canonical workspace mismatch: expected '$expectedRoot', got '$resolvedRoot'.")
}

$gitRoot = (& git -C $resolvedRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitRoot)) {
    $errors.Add('The resolved workspace is not a Git repository.')
} elseif (-not $gitRoot.Trim().Replace('/', '\').Equals($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    $errors.Add("Git root mismatch: $($gitRoot.Trim())")
}

$requiredFiles = @(
    'AGENTS.md',
    '.agents/skills/govern-capstone-work/SKILL.md',
    '.agents/skills/govern-capstone-work/agents/openai.yaml',
    '.agents/skills/govern-capstone-work/references/project-authority-and-gates.md',
    '.agents/skills/govern-capstone-work/references/evaluation-cases.md',
    '.agents/skills/govern-capstone-work/scripts/audit-governance.ps1',
    'docs/project/decision-register.md'
)

foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedRoot $relativePath) -PathType Leaf)) {
        $errors.Add("Missing required governance file: $relativePath")
    }
}

$projectAgents = Join-Path $resolvedRoot 'AGENTS.md'
$globalAgents = Join-Path $env:USERPROFILE '.codex\AGENTS.md'
$instructionBytes = 0
foreach ($instructionFile in @($globalAgents, $projectAgents)) {
    if (Test-Path -LiteralPath $instructionFile -PathType Leaf) {
        $instructionBytes += [System.Text.Encoding]::UTF8.GetByteCount(
            (Get-Content -Raw -Encoding UTF8 -LiteralPath $instructionFile)
        )
    }
}
if ($instructionBytes -gt 32768) {
    $errors.Add("Combined global and project AGENTS.md size is $instructionBytes bytes; default limit is 32768.")
}

$skillPath = Join-Path $resolvedRoot '.agents/skills/govern-capstone-work/SKILL.md'
if (Test-Path -LiteralPath $skillPath -PathType Leaf) {
    $skillLineCount = (Get-Content -Encoding UTF8 -LiteralPath $skillPath).Count
    if ($skillLineCount -ge 500) {
        $errors.Add("SKILL.md has $skillLineCount lines; keep it under 500.")
    }
}

$repoHookPath = Join-Path $resolvedRoot '.codex/hooks.json'
if (Test-Path -LiteralPath $repoHookPath) {
    $errors.Add('Repository hooks are present even though governance v1 forbids enabling hooks.')
}

$trackedChanges = @(& git -C $resolvedRoot diff --name-only)
$untrackedChanges = @(& git -C $resolvedRoot ls-files --others --exclude-standard)
$changedFiles = @($trackedChanges + $untrackedChanges | Where-Object { $_ } | Sort-Object -Unique)
if ($changedFiles.Count -gt 3) {
    $warnings.Add("Current diff touches $($changedFiles.Count) files; an approved impact map is required.")
}

$statusPath = Join-Path $resolvedRoot 'docs/project/implementation-status.md'
$currentPhase = $null
if (Test-Path -LiteralPath $statusPath -PathType Leaf) {
    $statusText = Get-Content -Raw -Encoding UTF8 -LiteralPath $statusPath
    $normalizedStatusText = ConvertTo-AsciiText $statusText
    $phaseMatch = [regex]::Match($normalizedStatusText, 'Giai doan hien tai:\*\*\s*Giai doan\s+(\d+)')
    if ($phaseMatch.Success) {
        $currentPhase = [int]$phaseMatch.Groups[1].Value
    } else {
        $warnings.Add('Could not determine the current phase from implementation-status.md.')
    }
}

if ($null -ne $currentPhase -and $currentPhase -lt 4) {
    $earlyAdrs = @($changedFiles | Where-Object { $_ -match '^docs/adr/ADR-(?!000)[^/]+\.md$' })
    if ($earlyAdrs.Count -gt 0) {
        $errors.Add("Architecture ADR files changed before Phase 4: $($earlyAdrs -join ', ')")
    }

    $earlyB11Artifacts = @($changedFiles | Where-Object { $_ -in @(
        'docs/architecture/B11-A-independent-alternatives.md',
        'docs/architecture/B11-B-legacy-feasibility.md'
    ) })
    if ($earlyB11Artifacts.Count -gt 0) {
        $errors.Add("B11 architecture artifacts changed before Phase 4: $($earlyB11Artifacts -join ', ')")
    }
}

$docsRoot = Join-Path $resolvedRoot 'docs'
$markdownFiles = @(Get-ChildItem -LiteralPath $docsRoot -Recurse -File -Filter '*.md')
$staleMethodologyPatterns = @(
    'Chi dung B5\.5 sau khi da co context map so bo',
    'Sau khi hoan thanh B5, dung B5\.5',
    'Noi quyet dinh tach service',
    '3 phuong an.*B5\.5',
    'ba phuong an.*B5\.5',
    'ADR dau tien ve kien truc',
    'Moi lan lam vay ghi mot ADR'
)

foreach ($markdownFile in $markdownFiles) {
    $text = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $markdownFile.FullName)
    foreach ($pattern in $staleMethodologyPatterns) {
        if ($text -match $pattern) {
            $relativePath = Get-RepoRelativePath -Root $resolvedRoot -Path $markdownFile.FullName
            $errors.Add("Stale methodology instruction '$pattern' remains in $relativePath.")
        }
    }
}

$workflowPath = Join-Path $resolvedRoot 'docs/quy-trinh-lam-viec.md'
if (Test-Path -LiteralPath $workflowPath -PathType Leaf) {
    $workflowText = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $workflowPath)
    $stageTwoMatch = [regex]::Match($workflowText, 'GIAI DOAN 2(?<body>.*?)GIAI DOAN 3', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if ($stageTwoMatch.Success -and $stageTwoMatch.Groups['body'].Value -match '\|[^\r\n]*B5\.5[^\r\n]*\|') {
        $errors.Add('Phase 2 still contains a B5.5 work-table row.')
    }

    if ($workflowText -match 'khong sinh them phuong an chi tu legacy') {
        $errors.Add('Master workflow still permits mixed legacy-driven option generation through the weak phrase "chi tu legacy".')
    }

    foreach ($requiredWorkflowText in @(
        'docs/architecture/B11-A-independent-alternatives.md',
        'docs/architecture/B11-B-legacy-feasibility.md',
        'Tac dong len A1-A6'
    )) {
        if ($workflowText -notmatch [regex]::Escape($requiredWorkflowText)) {
            $errors.Add("Master workflow is missing the B11 governance contract: $requiredWorkflowText")
        }
    }
}

$technicalWorkflowPath = Join-Path $resolvedRoot 'docs/tang-b-quy-trinh-ky-thuat.md'
if (Test-Path -LiteralPath $technicalWorkflowPath -PathType Leaf) {
    $technicalWorkflowText = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $technicalWorkflowPath)
    foreach ($requiredTechnicalText in @(
        'docs/architecture/B11-A-independent-alternatives.md',
        'docs/architecture/B11-B-legacy-feasibility.md',
        'B11-B, bang tai su dung/di tru',
        'khong sinh, bo sung, xep hang hoac sua phuong an kien truc',
        'Tac dong len A1-A6'
    )) {
        if ($technicalWorkflowText -notmatch [regex]::Escape($requiredTechnicalText)) {
            $errors.Add("Tang B is missing the B11 governance contract: $requiredTechnicalText")
        }
    }
}

$adrReadmePath = Join-Path $resolvedRoot 'docs/adr/README.md'
if (Test-Path -LiteralPath $adrReadmePath -PathType Leaf) {
    $adrReadmeText = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $adrReadmePath)
    foreach ($requiredAdrGuidance in @('B11-C', 'B11-A-independent-alternatives.md', 'B11-B-legacy-feasibility.md', 'Tac dong len A1-A6')) {
        if ($adrReadmeText -notmatch [regex]::Escape($requiredAdrGuidance)) {
            $errors.Add("ADR guidance is missing the target-architecture gate: $requiredAdrGuidance")
        }
    }
}

$b55Path = Join-Path $resolvedRoot 'docs/b5.5-doi-chieu-ma-nguon-va-ba-tang.md'
if (Test-Path -LiteralPath $b55Path -PathType Leaf) {
    $b55Text = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $b55Path)
    foreach ($forbiddenB55Pattern in @('Ba loi ra', 'Ton kho ve voi booking', 'Ton kho o lai voi event', 'Khong tach event va booking', 'he thong mot may', 'chia workload he thong', 'hai cau hinh')) {
        if ($b55Text -match [regex]::Escape($forbiddenB55Pattern)) {
            $errors.Add("B5.5 reintroduces a quarantined target option: $forbiddenB55Pattern")
        }
    }
}

$formationFiles = @()
$formationFiles += @(Get-ChildItem -LiteralPath (Join-Path $resolvedRoot 'docs/research') -File -Filter '*.md' -ErrorAction SilentlyContinue)
$formationFiles += @(Get-ChildItem -LiteralPath (Join-Path $resolvedRoot 'docs/domain') -File -Filter '*.md' -ErrorAction SilentlyContinue)
$formationFiles += @(Get-ChildItem -LiteralPath (Join-Path $resolvedRoot 'docs/quality-scenarios') -File -Filter '*.md' -ErrorAction SilentlyContinue)
$formationFiles += @(Get-ChildItem -LiteralPath (Join-Path $resolvedRoot 'docs/contracts') -File -Filter '*.md' -ErrorAction SilentlyContinue)
$glossaryPath = Join-Path $resolvedRoot 'docs/glossary.md'
if (Test-Path -LiteralPath $glossaryPath -PathType Leaf) {
    $formationFiles += Get-Item -LiteralPath $glossaryPath
}
$b11APath = Join-Path $resolvedRoot 'docs/architecture/B11-A-independent-alternatives.md'
if (Test-Path -LiteralPath $b11APath -PathType Leaf) {
    $formationFiles += Get-Item -LiteralPath $b11APath
}

$legacyReferencePattern = '(?i)B5\.5|legacy-baseline|flash-ticket-system|core-service|user-service|discovery-service'
foreach ($formationFile in @($formationFiles | Sort-Object FullName -Unique)) {
    $text = Get-Content -Raw -Encoding UTF8 -LiteralPath $formationFile.FullName
    if ($text -match $legacyReferencePattern) {
        $relativePath = Get-RepoRelativePath -Root $resolvedRoot -Path $formationFile.FullName
        $errors.Add("FORMATION artifact contains a legacy/comparison reference: $relativePath")
    }
}

$phaseTwoArtifacts = [ordered]@{
    B2 = 'docs/glossary.md'
    B3 = 'docs/domain/B3-business-processes.md'
    B4 = 'docs/domain/B4-domain-event-map.md'
    B5 = 'docs/domain/B5-bounded-context-map.md'
    B7 = 'docs/domain/B7-aggregates-and-invariants.md'
}
$phaseTwoDependencies = @{
    B3 = @('B2')
    B4 = @('B2', 'B3')
    B5 = @('B4')
    B7 = @('B2', 'B5')
}
$phaseTwoStatuses = @{}
foreach ($artifactId in $phaseTwoArtifacts.Keys) {
    $artifactPath = Join-Path $resolvedRoot $phaseTwoArtifacts[$artifactId]
    if (Test-Path -LiteralPath $artifactPath -PathType Leaf) {
        $phaseTwoStatuses[$artifactId] = Get-ArtifactStatus $artifactPath
        $relativeArtifactPath = $phaseTwoArtifacts[$artifactId].Replace('\', '/')
        $artifactChanged = $changedFiles -contains $relativeArtifactPath
        $metadataRequired = $artifactChanged -or ($null -ne $currentPhase -and $currentPhase -ge 2)
        if ($metadataRequired -and $null -eq $phaseTwoStatuses[$artifactId]) {
            $errors.Add("$artifactId exists without DRAFT/REVIEW_READY/APPROVED metadata: $relativeArtifactPath")
        }
        if ($metadataRequired) {
            $artifactText = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $artifactPath)
            foreach ($metadataLabel in @('Nguoi duyet:', 'Ngay duyet:', 'Dau vao va phien ban:')) {
                if ($artifactText -notmatch [regex]::Escape($metadataLabel)) {
                    $errors.Add("$artifactId is missing required review metadata '$metadataLabel': $relativeArtifactPath")
                }
            }
        }
    }
}

$domainRoot = Join-Path $resolvedRoot 'docs/domain'
if (Test-Path -LiteralPath $domainRoot -PathType Container) {
    $canonicalDomainPaths = @($phaseTwoArtifacts.Values | Where-Object { $_ -like 'docs/domain/*' } | ForEach-Object { $_.Replace('\', '/') })
    foreach ($domainFile in @(Get-ChildItem -LiteralPath $domainRoot -File -Filter '*.md')) {
        $relativeDomainPath = Get-RepoRelativePath -Root $resolvedRoot -Path $domainFile.FullName
        $looksLikeAlternatePrimary = $domainFile.Name -match '^B(2|3|4|5|7)[-_].*\.md$' -and $canonicalDomainPaths -notcontains $relativeDomainPath
        $looksLikeSeparateHotspot = $domainFile.Name -match '(?i)hotspot'
        if ($looksLikeAlternatePrimary -or $looksLikeSeparateHotspot) {
            $errors.Add("Non-canonical Phase 2 primary artifact: $relativeDomainPath")
        }
    }
}

foreach ($artifactId in $phaseTwoDependencies.Keys) {
    if ($phaseTwoStatuses.ContainsKey($artifactId) -and $phaseTwoStatuses[$artifactId] -eq 'APPROVED') {
        foreach ($dependencyId in $phaseTwoDependencies[$artifactId]) {
            if (-not $phaseTwoStatuses.ContainsKey($dependencyId) -or $phaseTwoStatuses[$dependencyId] -ne 'APPROVED') {
                $errors.Add("$artifactId is APPROVED while required input $dependencyId is not APPROVED.")
            }
        }
    }
}

$b2Path = Join-Path $resolvedRoot $phaseTwoArtifacts['B2']
$b3Path = Join-Path $resolvedRoot $phaseTwoArtifacts['B3']
if ((Test-Path -LiteralPath $b2Path -PathType Leaf) -and (Test-Path -LiteralPath $b3Path -PathType Leaf)) {
    $b2Version = Get-ArtifactVersion $b2Path
    $b3Text = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $b3Path)
    if ([string]::IsNullOrWhiteSpace($b2Version)) {
        $errors.Add('B2 is missing a readable artifact version.')
    } elseif ($b3Text -notmatch ('Dau vao va phien ban:[^\r\n]*' + [regex]::Escape($b2Version))) {
        $errors.Add("B3 does not reference the current B2 version '$b2Version' in its input metadata.")
    }

    $requiredB3Diagrams = @(
        'docs/diagrams/src/B3-01-event-lifecycle.puml',
        'docs/diagrams/src/B3-02-order-payment-ticket.puml',
        'docs/diagrams/src/B3-03-refund-reconciliation-payout.puml',
        'docs/diagrams/src/B3-04-online-check-in.puml'
    )
    $missingB3Diagrams = @($requiredB3Diagrams | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $resolvedRoot $_) -PathType Leaf)
    })
    if ($missingB3Diagrams.Count -gt 0) {
        if ($phaseTwoStatuses['B3'] -in @('REVIEW_READY', 'APPROVED')) {
            $errors.Add("B3 is $($phaseTwoStatuses['B3']) but required swimlane sources are missing: $($missingB3Diagrams -join ', ')")
        } else {
            $warnings.Add("B3 draft is missing swimlane sources: $($missingB3Diagrams -join ', ')")
        }
    }
}

$b11BPath = Join-Path $resolvedRoot 'docs/architecture/B11-B-legacy-feasibility.md'
$b11AStatus = Get-ArtifactStatus $b11APath
$b11BStatus = Get-ArtifactStatus $b11BPath

foreach ($b11Artifact in @(
    @{ Id = 'B11-A'; Path = $b11APath },
    @{ Id = 'B11-B'; Path = $b11BPath }
)) {
    if (Test-Path -LiteralPath $b11Artifact.Path -PathType Leaf) {
        $b11Text = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $b11Artifact.Path)
        if ($null -eq (Get-ArtifactStatus $b11Artifact.Path)) {
            $errors.Add("$($b11Artifact.Id) exists without DRAFT/REVIEW_READY/APPROVED metadata.")
        }
        foreach ($metadataLabel in @('Nguoi duyet:', 'Ngay duyet:', 'Dau vao va phien ban:')) {
            if ($b11Text -notmatch [regex]::Escape($metadataLabel)) {
                $errors.Add("$($b11Artifact.Id) is missing required review metadata '$metadataLabel'.")
            }
        }
    }
}

if (Test-Path -LiteralPath $b11BPath -PathType Leaf) {
    if ($b11AStatus -ne 'APPROVED') {
        $errors.Add('B11-B exists before the human-approved B11-A gate.')
    }
    $b11BText = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $b11BPath)
    if ($b11BText -notmatch 'Dau vao va phien ban:\s*[^\r\n]*B11-A[^\r\n]+') {
        $errors.Add('B11-B does not record a non-empty B11-A input version.')
    }
}

$architectureRoot = Join-Path $resolvedRoot 'docs/architecture'
if (Test-Path -LiteralPath $architectureRoot -PathType Container) {
    $canonicalB11Paths = @(
        'docs/architecture/B11-A-independent-alternatives.md',
        'docs/architecture/B11-B-legacy-feasibility.md'
    )
    foreach ($architectureFile in @(Get-ChildItem -LiteralPath $architectureRoot -File -Filter '*.md')) {
        $relativeArchitecturePath = Get-RepoRelativePath -Root $resolvedRoot -Path $architectureFile.FullName
        if ($architectureFile.Name -match '^B11-[AB][-_].*\.md$' -and $canonicalB11Paths -notcontains $relativeArchitecturePath) {
            $errors.Add("Non-canonical B11 gate artifact: $relativeArchitecturePath")
        }
    }
}

$targetAdrFiles = @(Get-ChildItem -LiteralPath (Join-Path $resolvedRoot 'docs/adr') -File -Filter 'ADR-*.md' | Where-Object { $_.Name -notmatch '^ADR-000-' })
foreach ($targetAdrFile in $targetAdrFiles) {
    $targetAdrText = ConvertTo-AsciiText (Get-Content -Raw -Encoding UTF8 -LiteralPath $targetAdrFile.FullName)
    if ($targetAdrText -match 'Trang thai:\*\*\s*Chap nhan|Trang thai:\s*Chap nhan') {
        if ($b11AStatus -ne 'APPROVED' -or $b11BStatus -ne 'APPROVED') {
            $errors.Add("Accepted target ADR lacks approved B11-A/B inputs: $($targetAdrFile.Name)")
        }
        foreach ($requiredAdrField in @('B11-A', 'B11-B', 'Tac dong len A1-A6')) {
            if ($targetAdrText -notmatch [regex]::Escape($requiredAdrField)) {
                $errors.Add("Accepted target ADR is missing '$requiredAdrField': $($targetAdrFile.Name)")
            }
        }
    }
}

$decisionPath = Join-Path $resolvedRoot 'docs/project/decision-register.md'
if (Test-Path -LiteralPath $decisionPath -PathType Leaf) {
    $decisionText = Get-Content -Raw -Encoding UTF8 -LiteralPath $decisionPath
    $normalizedDecisionText = ConvertTo-AsciiText $decisionText
    $requiredHeaders = @('ID', 'Noi dung quyet dinh', 'Loai', 'Trang thai', 'Nguoi chot', 'Bang chung', 'Ngay chot', 'Gate ap dung', 'File chiu anh huong', 'Thay the')
    foreach ($header in $requiredHeaders) {
        if ($normalizedDecisionText -notmatch [regex]::Escape($header)) {
            $errors.Add("Decision register is missing required field: $header")
        }
    }
}

foreach ($warning in $warnings) {
    Write-Output "WARN: $warning"
}
foreach ($errorMessage in $errors) {
    Write-Output "ERROR: $errorMessage"
}

Write-Output "INFO: canonical_root=$resolvedRoot"
Write-Output "INFO: instruction_bytes=$instructionBytes"
Write-Output "INFO: changed_files=$($changedFiles.Count)"
if ($null -ne $currentPhase) {
    Write-Output "INFO: current_phase=$currentPhase"
}

if ($errors.Count -gt 0) {
    Write-Output "FAIL: governance audit found $($errors.Count) error(s)."
    exit 1
}

Write-Output "PASS: governance audit completed with $($warnings.Count) warning(s)."
exit 0
