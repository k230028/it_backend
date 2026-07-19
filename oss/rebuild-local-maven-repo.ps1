# 폐쇄망 반입용 로컬 Maven2 미러를 "처음부터" 다시 만드는 오케스트레이션 스크립트.
# 권장 절차(캐시 + OutDir 둘 다 초기화)를 한 번에 수행합니다.
#
# 단계:
#   1. GRADLE_USER_HOME 지정 후 Gradle 데몬 정지.
#   2. 의존성 캐시(modules-2) 완전 삭제 — gradle clean은 build/만 지우고 캐시는 비우지
#      않으므로, 미사용/구버전 의존성이 미러에 섞이지 않도록 여기서 비웁니다.
#   3. 기존 미러(OutDir) 백업 후 제거 — settings.gradle은 C:\maven-repo 폴더가 있으면
#      2순위 저장소(file:///)로 등록합니다. 미러가 남아 있으면 빌드가 거기서 해석해
#      아무것도 새로 받지 않아 캐시(files-2.1)가 비게 됩니다. 따라서 빌드 전에 치워
#      원격(Nexus/mavenCentral)에서 새로 받도록 강제합니다. 빌드 실패 시 복원합니다.
#   4. 빈 캐시에서 clean build → 현재 빌드가 실제로 필요로 하는 의존성만 새로 다운로드.
#   5. make-local-maven-repo.ps1 호출 → 캐시를 Maven2 레이아웃으로 변환 + manifest 재생성.
#
# 사용 예 (원격 저장소 접근 가능한 외부망 PC, it_backend 디렉토리에서):
#   .\rebuild-local-maven-repo.ps1
#   .\rebuild-local-maven-repo.ps1 -GradleUserHome 'C:\it\.gradle' -OutDir 'C:\maven-repo'
#
# 주의:
#   - GradleUserHome\caches\modules-2 의 기존 내용은 삭제됩니다.
#   - OutDir는 빌드 전 '<OutDir>.bak'으로 백업되며, 변환 성공 시 백업을 삭제합니다.
#   - 원격 저장소(Nexus 또는 mavenCentral)에 닿지 못하면 다운로드가 없어 변환이 중단되고
#     백업 미러가 복원됩니다(폐쇄망 PC에서는 이 스크립트로 미러를 새로 만들 수 없음).
param(
    [string]$GradleUserHome = 'C:\it\.gradle',
    [string]$OutDir         = 'C:\maven-repo'
)

$ErrorActionPreference = 'Stop'

# 콘솔 한글 출력 보정: 출력 인코딩을 UTF-8(BOM 없음)로 맞춰 mojibake 방지.
# (네이티브 명령 출력에 BOM이 섞이지 않도록 UTF8Encoding($false) 사용.)
try {
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
}
catch {
    Write-Verbose '콘솔 UTF-8 인코딩을 설정하지 못해 현재 인코딩으로 계속합니다.'
}

$gradlew  = Join-Path $PSScriptRoot 'gradlew.bat'
$makeRepo = Join-Path $PSScriptRoot 'make-local-maven-repo.ps1'
$cacheDir = Join-Path $GradleUserHome 'caches\modules-2'
$filesDir = Join-Path $cacheDir 'files-2.1'
$backup   = "$OutDir.bak"

if (-not (Test-Path $gradlew))  { throw "gradlew.bat를 찾을 수 없습니다: $gradlew" }
if (-not (Test-Path $makeRepo)) { throw "make-local-maven-repo.ps1을 찾을 수 없습니다: $makeRepo" }

# 빌드 실패/다운로드 없음일 때 백업 미러를 되돌리는 헬퍼.
function Restore-Backup {
    if ((Test-Path $backup) -and -not (Test-Path $OutDir)) {
        Write-Host "백업 미러 복원: $backup → $OutDir"
        Move-Item -Path $backup -Destination $OutDir
    }
}

# 1) GRADLE_USER_HOME 고정 + 데몬 정지 (캐시 파일 잠금 해제)
$env:GRADLE_USER_HOME = $GradleUserHome
Write-Host "[1/5] GRADLE_USER_HOME = $GradleUserHome / Gradle 데몬 정지"
& $gradlew --stop

# 2) 의존성 캐시 완전 삭제
if (Test-Path $cacheDir) {
    Write-Host "[2/5] 의존성 캐시 삭제: $cacheDir"
    Remove-Item -Path $cacheDir -Recurse -Force
} else {
    Write-Host "[2/5] 의존성 캐시 없음(이미 비어 있음): $cacheDir"
}

# 3) 기존 미러 백업 후 제거 (빌드가 file:///OutDir 대신 원격에서 받도록 강제)
if (Test-Path $backup) { Remove-Item -Path $backup -Recurse -Force }
if (Test-Path $OutDir) {
    Write-Host "[3/5] 기존 미러 백업 후 제거: $OutDir → $backup"
    Move-Item -Path $OutDir -Destination $backup
} else {
    Write-Host "[3/5] 기존 미러 없음: $OutDir"
}

# 4) 빈 캐시에서 새로 받기 (실패 시 백업 복원 후 중단)
Write-Host "[4/5] clean build (의존성 새로 다운로드)"
& $gradlew --no-daemon clean build
if ($LASTEXITCODE -ne 0) {
    Restore-Backup
    throw "gradle clean build 실패 (exit code $LASTEXITCODE) — 미러 변환을 중단합니다."
}

# 다운로드 검증: 원격 저장소에 닿지 못하면 캐시가 비어 변환할 것이 없습니다.
if (-not (Test-Path $filesDir)) {
    Restore-Backup
    throw "빌드는 성공했지만 다운로드된 의존성이 없습니다 ($filesDir 없음). " +
          "원격 저장소(Nexus 또는 mavenCentral)에 접근 가능한 외부망 PC에서 실행하세요."
}

# 5) 캐시 → Maven2 미러 변환 + manifest 재생성 (OutDir는 make 스크립트가 새로 생성)
Write-Host "[5/5] 캐시 → Maven2 미러 변환: $filesDir → $OutDir"
& $makeRepo -CacheDir $filesDir -OutDir $OutDir

# 변환 성공 → 백업 미러 삭제
if (Test-Path $backup) { Remove-Item -Path $backup -Recurse -Force }

Write-Host ""
Write-Host "완료. 폐쇄망 PC로 '$OutDir' 폴더와 manifest를 반입하세요."
