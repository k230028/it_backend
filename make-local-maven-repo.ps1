# Gradle 캐시(modules-2/files-2.1)를 Maven2 레이아웃의 로컬 폴더 저장소로 변환합니다.
# Nexus 업로드가 불가한 폐쇄망에서, 변환된 폴더를 반입해 file:/// URL 저장소로 사용합니다.
#
# 사용 절차:
#   1. 외부망 PC에서 깨끗한 캐시 수집:
#        $env:GRADLE_USER_HOME = 'C:\gradle-mirror'
#        .\gradlew --no-daemon clean build
#   2. 본 스크립트로 변환:
#        .\make-local-maven-repo.ps1 -CacheDir 'C:\gradle-mirror\caches\modules-2\files-2.1' -OutDir 'C:\maven-repo'
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
