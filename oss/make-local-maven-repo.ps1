# Gradle 캐시(modules-2/files-2.1)를 Maven2 레이아웃의 로컬 폴더 저장소로 변환합니다.
# Nexus 업로드가 불가한 폐쇄망에서, 변환된 폴더를 반입해 file:/// URL 저장소로 사용합니다.
#
# 사용 절차:
#   1. 외부망 PC에서 "깨끗한" 캐시 수집 (핵심: gradle clean은 build/만 지우고 의존성 캐시는
#      비우지 않으므로, 미사용 의존성이 섞이지 않도록 modules-2를 먼저 삭제):
#        $env:GRADLE_USER_HOME = 'C:\it\.gradle'
#        .\gradlew --stop
#        Remove-Item 'C:\it\.gradle\caches\modules-2' -Recurse -Force -ErrorAction SilentlyContinue
#        .\gradlew --no-daemon clean build
#      (위 1~2단계를 한 번에 수행하려면 rebuild-local-maven-repo.ps1 사용)
#   2. 본 스크립트로 변환 (OutDir는 변환 전 자동으로 비워집니다):
#        .\make-local-maven-repo.ps1 -CacheDir 'C:\it\.gradle\caches\modules-2\files-2.1' -OutDir 'C:\maven-repo'
#      → 변환과 함께 manifest 2종이 스크립트 폴더(it_backend)에 생성됩니다:
#        - maven-repo-manifest.txt          : 전체 파일(jar + pom + .module)
#        - maven-repo-manifest-pom-only.txt : .module 제외 (check-repo-coverage.ps1 -ManifestFile 입력)
#   3. C:\maven-repo 폴더를 폐쇄망 PC의 동일 경로(C:\maven-repo)로 복사.
#   4. 폐쇄망 PC의 settings.gradle / build.gradle에서 저장소 URL을
#        url = 'file:///C:/maven-repo'
#      로 지정 (allowInsecureProtocol 불필요).
param(
    [Parameter(Mandatory = $true)]
    [string]$CacheDir,                  # files-2.1 디렉토리 경로
    [Parameter(Mandatory = $true)]
    [string]$OutDir                     # 생성할 Maven2 레이아웃 저장소 경로
)

if (-not (Test-Path $CacheDir)) {
    throw "캐시 디렉토리를 찾을 수 없습니다: $CacheDir"
}
# OutDir에 이전 실행의 잔존 파일이 남으면 manifest(=OutDir 전체 스캔)에 미사용분이 섞입니다.
# 현재 캐시 내용만 정확히 반영하도록 변환 전에 OutDir를 완전히 비웁니다.
if (Test-Path $OutDir) {
    Write-Host "기존 OutDir 정리: $OutDir"
    Remove-Item -Path $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$copied = 0
# 캐시 구조: <group>/<artifact>/<version>/<sha1해시>/<파일명>
# Maven2 구조: <group을 /로 분해>/<artifact>/<version>/<파일명>
Get-ChildItem -Path $CacheDir -Directory | ForEach-Object {
    $groupPath = $_.Name -replace '\.', '\'
    Get-ChildItem -Path $_.FullName -Directory | ForEach-Object {
        $artifact = $_.Name
        Get-ChildItem -Path $_.FullName -Directory | ForEach-Object {
            $version = $_.Name
            $destDir = Join-Path $OutDir "$groupPath\$artifact\$version"
            Get-ChildItem -Path $_.FullName -File -Recurse | ForEach-Object {
                if (-not (Test-Path $destDir)) {
                    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
                }
                Copy-Item -Path $_.FullName -Destination (Join-Path $destDir $_.Name) -Force
                $script:copied++
            }
        }
    }
}

Write-Host "변환 완료: $copied개 파일 → $OutDir"
Write-Host "폐쇄망 저장소 URL: file:///$($OutDir -replace '\\','/')"

# 변환된 미러(OutDir)의 파일 목록을 manifest로 생성합니다.
# check-repo-coverage.ps1의 -ManifestFile 입력으로 사용합니다.
#   - maven-repo-manifest.txt          : 전체 파일(jar + pom + .module)
#   - maven-repo-manifest-pom-only.txt : Gradle 모듈 메타(.module) 제외 (.module 미호스팅 Nexus/Maven 대상)
# Gradle 배포판 zip(gradle-*)은 Maven 저장소 대상이 아니므로 제외합니다.
$base = (Resolve-Path $OutDir).Path
$allPaths = Get-ChildItem $base -File -Recurse |
    ForEach-Object { $_.FullName.Substring($base.Length + 1) -replace '\\', '/' } |
    Where-Object { $_ -notlike 'gradle-*' } |
    Sort-Object

$pomOnlyPaths = $allPaths | Where-Object { $_ -notlike '*.module' }

$manifestFull    = Join-Path $PSScriptRoot 'maven-repo-manifest.txt'
$manifestPomOnly = Join-Path $PSScriptRoot 'maven-repo-manifest-pom-only.txt'
$allPaths     | Out-File -FilePath $manifestFull    -Encoding utf8
$pomOnlyPaths | Out-File -FilePath $manifestPomOnly -Encoding utf8

Write-Host "manifest 생성: $($allPaths.Count)건 → $manifestFull"
Write-Host "manifest 생성(.module 제외): $($pomOnlyPaths.Count)건 → $manifestPomOnly"
