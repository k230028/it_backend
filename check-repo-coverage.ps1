# 빌드에 필요한 의존성 파일이 내부 Nexus 저장소에 얼마나 존재하는지 일괄 점검하고,
# -DownloadDir 지정 시 존재하는 파일을 로컬 미러(maven-repo)에 다운로드합니다.
# 빌드와 달리 누락 파일이 있어도 중단하지 않고 전체 커버리지와 누락 목록을 보고합니다.
#
# 기준 목록은 둘 중 하나로 지정:
#   -RepoDir      : 외부망에서 만든 로컬 미러 폴더(C:\maven-repo). 폴더 구조에서 목록 생성.
#   -ManifestFile : 미러 폴더가 없을 때, 미리 생성한 manifest 텍스트 파일(상대경로 목록).
#
# 사용 예 (폐쇄망 PC):
#   # 점검만
#   .\check-repo-coverage.ps1 -ManifestFile .\maven-repo-manifest.txt
#   # 점검 + 존재 파일을 C:\maven-repo로 다운로드 (로컬 미러 구축)
#   .\check-repo-coverage.ps1 -ManifestFile .\maven-repo-manifest.txt -DownloadDir 'C:\maven-repo'
param(
    [string]$RepoDir,
    [string]$ManifestFile,
    [string]$RepoUrl = 'http://10.6.65.151:20080/repository/maven-releases',
    [string]$DownloadDir,               # 지정 시 존재 파일을 이 폴더에 다운로드 (Maven2 레이아웃 유지)
    [string]$Username,
    [string]$Password
)

# 기준 파일 목록 수집 (Gradle 배포판 zip은 Maven 저장소 대상이 아니므로 제외)
if ($RepoDir) {
    if (-not (Test-Path $RepoDir)) { throw "폴더를 찾을 수 없습니다: $RepoDir" }
    $base = (Resolve-Path $RepoDir).Path
    $paths = Get-ChildItem $base -File -Recurse |
        ForEach-Object { $_.FullName.Substring($base.Length + 1) -replace '\\', '/' } |
        Where-Object { $_ -notlike 'gradle-*' }
} elseif ($ManifestFile) {
    if (-not (Test-Path $ManifestFile)) { throw "manifest 파일을 찾을 수 없습니다: $ManifestFile" }
    $paths = Get-Content $ManifestFile | Where-Object { $_.Trim() }
} else {
    throw '-RepoDir 또는 -ManifestFile 중 하나를 지정하세요.'
}

# 인증 헤더 (선택)
$headers = @{}
if ($Username) {
    $pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
    $headers['Authorization'] = "Basic $pair"
}

$total = $paths.Count
$mode = if ($DownloadDir) { "점검 + 다운로드 → $DownloadDir" } else { '점검만' }
Write-Host "대상: $total개 파일 / 저장소: $RepoUrl / 모드: $mode"
Write-Host ""

$found = 0; $downloaded = 0; $skippedLocal = 0; $missing = @(); $checked = 0
foreach ($p in $paths) {
    $checked++
    $url = "$RepoUrl/$p"

    if ($DownloadDir) {
        $dest = Join-Path $DownloadDir ($p -replace '/', '\')
        # 이미 로컬에 있으면 네트워크 요청 없이 건너뜀 (재실행 안전)
        if (Test-Path $dest) {
            $found++; $skippedLocal++
        } else {
            try {
                $destParent = Split-Path $dest -Parent
                if (-not (Test-Path $destParent)) {
                    New-Item -ItemType Directory -Force -Path $destParent | Out-Null
                }
                Invoke-WebRequest -Uri $url -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 60
                $found++; $downloaded++
            } catch {
                $missing += $p
                # 실패 시 불완전 파일 제거
                if (Test-Path $dest) { Remove-Item $dest -Force }
            }
        }
    } else {
        try {
            Invoke-WebRequest -Uri $url -Method Head -Headers $headers -UseBasicParsing -TimeoutSec 15 | Out-Null
            $found++
        } catch {
            $missing += $p
        }
    }

    # 진행률 표시 (50건마다)
    if ($checked % 50 -eq 0) {
        Write-Host ("진행: {0}/{1} (존재 {2} / 누락 {3})" -f $checked, $total, $found, $missing.Count)
    }
}

Write-Host ""
Write-Host ("결과: 존재 {0} / 누락 {1} / 전체 {2}  (커버리지 {3:P1})" -f $found, $missing.Count, $total, ($found / $total))
if ($DownloadDir) {
    Write-Host ("다운로드 {0}건 / 로컬 기존재 건너뜀 {1}건" -f $downloaded, $skippedLocal)
}

if ($missing.Count -gt 0) {
    $out = Join-Path (Get-Location) 'repo-missing-files.txt'
    $missing | Out-File -FilePath $out -Encoding utf8
    Write-Host "누락 목록 저장: $out"
    Write-Host ""
    Write-Host "누락 상위 20건:"
    $missing | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
}

# ── 반입신청목록 CSV 생성 (저장소에 없는 누락분만 대상) ──────────────────────
# 누락 경로를 Maven2 좌표(그룹:아티팩트:버전)로 묶어 좌표당 1건을 기록합니다.
#   - jar가 있으면 jar, jar 없이 pom만 있으면 pom을 대표 파일로 선택 (타입은 소문자).
#   - .module/.xml/.asc 등 부가 파일만 누락된 좌표는 제외.
# 누락이 없으면 헤더만 있는 빈 CSV를 생성합니다(이전 실행의 잔존 파일 방지).
$coords = [ordered]@{}
foreach ($rawPath in $missing) {
    $segs = ($rawPath.Trim()) -split '/'
    if ($segs.Count -lt 4) { continue }   # Maven2 레이아웃(group/artifact/version/file)이 아니면 제외
    $file       = $segs[-1]
    $version    = $segs[-2]
    $artifactId = $segs[-3]
    $groupId    = ($segs[0..($segs.Count - 4)] -join '.')
    $ext        = ([IO.Path]::GetExtension($file)).TrimStart('.').ToLower()

    # 분류자(classifier) 추출: 파일명이 {artifactId}-{version}-{classifier}.{ext} 형태이면 가운데 부분.
    #   예: ...-4.1.115.Final-linux-x86_64.jar → linux-x86_64, *-sources.jar → sources. 없으면 공란.
    $baseName   = [IO.Path]::GetFileNameWithoutExtension($file)
    $prefix     = $artifactId + '-' + $version
    $classifier = ''
    if ($baseName.StartsWith($prefix + '-')) {
        $classifier = $baseName.Substring($prefix.Length + 1)
    }

    # 분류자가 다르면 별개 산출물이므로 좌표 키에 분류자를 포함해 각각 별도 행으로 유지.
    $key = $groupId + ':' + $artifactId + ':' + $version + ':' + $classifier
    if (-not $coords.Contains($key)) {
        $coords[$key] = [ordered]@{
            groupId = $groupId; artifactId = $artifactId; version = $version
            classifier = $classifier; files = @{}
        }
    }
    $coords[$key].files[$ext] = $file
}

$csvRows = foreach ($key in $coords.Keys) {
    $c = $coords[$key]
    if     ($c.files.ContainsKey('jar')) { $type = 'jar'; $file = $c.files['jar'] }
    elseif ($c.files.ContainsKey('pom')) { $type = 'pom'; $file = $c.files['pom'] }
    else   { continue }                   # jar/pom 없는 좌표는 반입 대상에서 제외

    [pscustomobject][ordered]@{
        '그룹'        = $c.groupId
        '아티팩트'      = $c.artifactId
        '버전'        = $c.version
        'classifier' = $c.classifier
        '타입'        = $type
        '파일명'       = $file
    }
}

$csvPath = Join-Path $PSScriptRoot '반입신청목록.csv'
$csvRows = @($csvRows)
if ($csvRows.Count -gt 0) {
    $csvRows | Sort-Object '그룹', '아티팩트', '버전' |
        Export-Csv -Path $csvPath -NoTypeInformation -Encoding utf8
} else {
    # 누락이 없으면 Export-Csv가 빈 파일을 만들므로 헤더만 직접 기록
    '"그룹","아티팩트","버전","classifier","타입","파일명"' | Out-File -FilePath $csvPath -Encoding utf8
}
Write-Host ""
Write-Host ("반입신청목록(누락분) 생성: {0} ({1}건)" -f $csvPath, $csvRows.Count)
