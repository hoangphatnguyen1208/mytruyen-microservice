param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\.env.jwt.local")
)

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$rsa = [System.Security.Cryptography.RSA]::Create(2048)

try {
    $privateKey = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
    $publicKey = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
    $content = @(
        "JWT_PRIVATE_KEY_BASE64=$privateKey"
        "JWT_PUBLIC_KEY_BASE64=$publicKey"
        "JWT_ALGORITHM=RS256"
    )
    [System.IO.File]::WriteAllLines($resolvedOutput, $content)
    Write-Host "JWT key pair written to $resolvedOutput"
    Write-Host "Keep this file secret; only auth-service may receive JWT_PRIVATE_KEY_BASE64."
}
finally {
    $rsa.Dispose()
}
