param(
    [int]$Baseline = 2106
)

# Gradle javadoc의 warning은 stderr로 나오므로 PowerShell의 Stop 모드에서는
# 기준선 집계 전에 예외로 바뀝니다. 명령 종료코드는 별도로 검사합니다.
$ErrorActionPreference = 'Continue'
$logFile = New-TemporaryFile

try {
    & "$PSScriptRoot\..\gradlew.bat" javadoc --rerun 2>&1 | Tee-Object -FilePath $logFile.FullName
    if ($LASTEXITCODE -ne 0) {
        throw "Javadoc 생성이 실패했습니다(exit=$LASTEXITCODE)."
    }

    $warningCount = (Select-String -Path $logFile.FullName -Pattern 'warning:' | Measure-Object).Count
    Write-Host "Javadoc warnings: $warningCount (baseline: $Baseline)"
    if ($warningCount -gt $Baseline) {
        throw "Javadoc 경고가 기준선보다 $($warningCount - $Baseline)건 증가했습니다."
    }
} finally {
    Remove-Item -LiteralPath $logFile.FullName -Force -ErrorAction SilentlyContinue
}
