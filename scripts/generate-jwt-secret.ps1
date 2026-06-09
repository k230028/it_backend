<#
.SYNOPSIS
    운영용 JWT_SECRET(HMAC-SHA 서명키)을 암호학적 난수로 생성합니다.

.DESCRIPTION
    JJWT(Keys.hmacShaKeyFor)는 최소 256비트(32바이트) 길이의 키를 요구합니다.
    이 스크립트는 .NET의 RandomNumberGenerator(암호학적 CSPRNG)로 난수 바이트를
    생성한 뒤 Base64로 인코딩하여 출력합니다. 기본 64바이트(512비트)로 여유 있게 생성합니다.

    Math.random 류의 약한 난수가 아니라 OS CSPRNG를 사용하므로 운영 시크릿 생성에 적합합니다.

.PARAMETER Bytes
    생성할 난수 바이트 수. 최소 32(256비트). 기본값 64(512비트).

.PARAMETER SetEnv
    지정 시 생성한 값을 사용자 환경변수 JWT_SECRET 에 setx로 등록합니다.
    (등록은 이후 새로 여는 터미널/프로세스부터 적용됩니다.)

.PARAMETER Machine
    -SetEnv 와 함께 사용. 시스템(전역) 환경변수로 등록합니다. 관리자 권한 필요.

.EXAMPLE
    .\generate-jwt-secret.ps1
    512비트 시크릿을 생성해 화면에 출력합니다.

.EXAMPLE
    .\generate-jwt-secret.ps1 -Bytes 32
    최소 길이(256비트) 시크릿을 생성합니다.

.EXAMPLE
    .\generate-jwt-secret.ps1 -SetEnv
    시크릿을 생성하고 사용자 환경변수 JWT_SECRET 으로 등록합니다.
#>
[CmdletBinding()]
param(
    [ValidateRange(32, 256)]
    [int]$Bytes = 64,

    [switch]$SetEnv,

    [switch]$Machine
)

$ErrorActionPreference = 'Stop'

# 1) 암호학적 난수 바이트 생성 (OS CSPRNG)
$buffer = New-Object byte[] $Bytes
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($buffer)
}
finally {
    $rng.Dispose()
}

# 2) Base64 인코딩 (환경변수/프로퍼티에 안전하게 담기는 ASCII 형태)
$secret = [System.Convert]::ToBase64String($buffer)
$bits = $Bytes * 8

Write-Host ""
Write-Host "생성된 JWT_SECRET ($bits bit / $Bytes byte):" -ForegroundColor Cyan
Write-Host $secret
Write-Host ""

# 3) 선택: 환경변수 등록
if ($SetEnv) {
    $scope = if ($Machine) { 'Machine' } else { 'User' }
    [Environment]::SetEnvironmentVariable('JWT_SECRET', $secret, $scope)
    Write-Host "환경변수 JWT_SECRET 을 '$scope' 범위로 등록했습니다." -ForegroundColor Green
    Write-Host "주의: 이미 열려 있는 터미널/IDE에는 반영되지 않습니다. 새로 시작해야 적용됩니다." -ForegroundColor Yellow
}
else {
    Write-Host "환경변수로 바로 등록하려면 -SetEnv 옵션을 추가하세요." -ForegroundColor DarkGray
    Write-Host "예) .\generate-jwt-secret.ps1 -SetEnv" -ForegroundColor DarkGray
}

# 4) 호출 측에서 변수로 받을 수 있도록 시크릿 문자열을 반환
return $secret
