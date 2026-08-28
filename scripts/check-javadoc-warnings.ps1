param(
    [int]$Baseline = 1915
)

$ErrorActionPreference = 'Stop'
$logFile = New-TemporaryFile

try {
    & "$PSScriptRoot\..\gradlew.bat" javadoc 2>&1 | Tee-Object -FilePath $logFile.FullName
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
