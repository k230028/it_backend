$ErrorActionPreference = 'Stop'

$scriptUnderTest = Join-Path (Split-Path -Parent $PSScriptRoot) 'check-repo-coverage.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("check-repo-coverage-test-" + [Guid]::NewGuid().ToString('N'))
$toolDir = Join-Path $testRoot 'tool'
$mirrorDir = Join-Path $testRoot 'mirror'
$manifestFile = Join-Path $testRoot 'manifest.txt'

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

try {
    New-Item -ItemType Directory -Force -Path $toolDir, $mirrorDir | Out-Null
    Copy-Item -LiteralPath $scriptUnderTest -Destination $toolDir

    $manifestPaths = @(
        'com/example/demo/1.0/demo-1.0.jar'
        'com/example/demo/1.0/demo-1.0.pom'
        'com/example/demo/1.0/demo-1.0.module'
        'com/example/demo/1.0/demo-1.0-sources.jar'
        'org/example/example-bom/2.0/example-bom-2.0.pom'
    )
    $manifestPaths | Out-File -LiteralPath $manifestFile -Encoding utf8

    foreach ($relativePath in $manifestPaths) {
        $destination = Join-Path $mirrorDir ($relativePath -replace '/', '\')
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        'fixture' | Out-File -LiteralPath $destination -Encoding ascii
    }

    $copiedScript = Join-Path $toolDir 'check-repo-coverage.ps1'
    $output = (& $copiedScript -ManifestFile $manifestFile -DownloadDir $mirrorDir 6>&1 | Out-String)

    Assert-True ($output -match '/ [^0-9\r\n]*3\s+\(') 'The result must count three files after excluding .module.'

    $allCsvName = (([char]0xC804), ([char]0xCCB4), ([char]0xBAA9), ([char]0xB85D) -join '') + '.csv'
    $allCsvPath = Join-Path $toolDir $allCsvName
    Assert-True (Test-Path -LiteralPath $allCsvPath -PathType Leaf) 'The complete-list CSV must be created.'

    $allRows = @(Import-Csv -LiteralPath $allCsvPath -Encoding utf8)
    Assert-True ($allRows.Count -eq 2) 'The complete-list CSV must contain two Maven coordinates.'
    $fileNameHeader = ([char]0xD30C), ([char]0xC77C), ([char]0xBA85) -join ''
    $moduleRows = @($allRows | Where-Object { $_.PSObject.Properties[$fileNameHeader].Value -like '*.module' })
    Assert-True ($moduleRows.Count -eq 0) 'The complete-list CSV must not contain .module files.'

    $sourcesRows = @($allRows | Where-Object { $_.PSObject.Properties[$fileNameHeader].Value -like '*-sources.jar' })
    Assert-True ($sourcesRows.Count -eq 0) 'The complete-list CSV must not contain -sources.jar files by default.'

    Write-Host 'PASS: module/sources exclusion and complete-list CSV generation'

    # -IncludeSources를 지정하면 소스 첨부용 jar도 점검 대상에 포함됩니다.
    $sourcesOutput = (& $copiedScript -ManifestFile $manifestFile -DownloadDir $mirrorDir -IncludeSources 6>&1 | Out-String)
    Assert-True ($sourcesOutput -match '/ [^0-9\r\n]*4\s+\(') 'The -IncludeSources run must count four files.'

    $withSourcesRows = @(Import-Csv -LiteralPath $allCsvPath -Encoding utf8)
    Assert-True ($withSourcesRows.Count -eq 3) 'The -IncludeSources run must add the sources coordinate.'
    $includedSourcesRows = @($withSourcesRows | Where-Object { $_.PSObject.Properties[$fileNameHeader].Value -like '*-sources.jar' })
    Assert-True ($includedSourcesRows.Count -eq 1) 'The -IncludeSources run must keep the sources jar row.'

    Write-Host 'PASS: -IncludeSources opt-in'
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
