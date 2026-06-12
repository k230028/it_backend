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
#   # Nexus가 익명 조회를 막은 경우
#   .\check-repo-coverage.ps1 -ManifestFile .\maven-repo-manifest.txt -DownloadDir 'C:\maven-repo' -Username admin -Password '****'
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
