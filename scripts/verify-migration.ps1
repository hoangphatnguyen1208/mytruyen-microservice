param([switch]$CheckTopboxes)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectName = 'mytruyen-smoke-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$tempPath = Join-Path ([IO.Path]::GetTempPath()) $projectName
$envPath = Join-Path $tempPath 'test.env'
$oldEnvironment = @{}

function Free-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    try { $listener.Start(); return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Compose {
    & docker compose --project-name $projectName --env-file $envPath --file (Join-Path $repoPath 'docker-compose.yml') @args
    if ($LASTEXITCODE -ne 0) { throw 'Disposable Compose command failed' }
}

& docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop before verification' }
New-Item -ItemType Directory -Path $tempPath | Out-Null
$rsa = [Security.Cryptography.RSA]::Create(2048)
$settings = @{
    JWT_PRIVATE_KEY_BASE64 = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
    JWT_PUBLIC_KEY_BASE64 = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
    JWT_ISSUER = 'mytruyen-auth'
    JWT_AUDIENCE = 'mytruyen-api'
    POSTGRES_USER = 'mytruyen'
    IDENTITY_POSTGRES_DB = 'mytruyen_identity'
    CATALOG_POSTGRES_DB = 'mytruyen_catalog'
    ENGAGEMENT_POSTGRES_DB = 'mytruyen_engagement'
    RABBITMQ_DEFAULT_USER = 'mytruyen'
    BOOTSTRAP_ADMIN_ENABLED = 'true'
    BOOTSTRAP_ADMIN_EMAIL = 'smoke-admin@example.test'
    ALLOW_DISPOSABLE_SMOKE = 'true'
    GATEWAY_PORT = [string](Free-Port)
    GATEWAY_BIND_ADDRESS = '127.0.0.1'
    MEILI_SEARCH_KEY = ''
    MEILI_WRITE_KEY = ''
    RABBITMQ_MANAGEMENT_PORT = [string](Free-Port)
}
$rsa.Dispose()
foreach ($key in @('POSTGRES_PASSWORD','REDIS_PASSWORD','MEILI_MASTER_KEY','RABBITMQ_DEFAULT_PASS','BOOTSTRAP_ADMIN_PASSWORD')) {
    $settings[$key] = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
}
try {
    # Generated ephemeral configuration, never copies or modifies the user's .env.
    [IO.File]::WriteAllLines($envPath, [string[]]($settings.GetEnumerator() | ForEach-Object { '{0}={1}' -f $_.Key,$_.Value }))
    foreach ($key in $settings.Keys) {
        $oldEnvironment[$key] = [Environment]::GetEnvironmentVariable($key,'Process')
        [Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')
    }
    Write-Host "Disposable project: $projectName; gateway port: $($settings.GATEWAY_PORT)"
    Invoke-Compose config --quiet
    Invoke-Compose up -d --build
    $smokeArguments = @('--base-url',"http://localhost:$($settings.GATEWAY_PORT)")
    if ($CheckTopboxes) { $smokeArguments += '--check-topboxes' }
    & uv run --no-project --with httpx python (Join-Path $PSScriptRoot 'smoke-migration.py') @smokeArguments
    if ($LASTEXITCODE -ne 0) { throw 'Migration smoke test failed' }
} catch {
    & docker compose --project-name $projectName --env-file $envPath --file (Join-Path $repoPath 'docker-compose.yml') logs --no-color --tail=80
    throw
} finally {
    # Only resources belonging to this freshly generated Compose project are removed.
    & docker compose --project-name $projectName --env-file $envPath --file (Join-Path $repoPath 'docker-compose.yml') down --volumes --remove-orphans
    $cleanupSucceeded = $LASTEXITCODE -eq 0
    foreach ($key in $oldEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key,$oldEnvironment[$key],'Process') }
    if (Test-Path -LiteralPath $envPath) { Remove-Item -LiteralPath $envPath }
    if (Test-Path -LiteralPath $tempPath) { Remove-Item -LiteralPath $tempPath }
    if ($cleanupSucceeded) {
        Write-Host "Disposed test project $projectName and its temporary credentials. No application database was targeted."
    } else {
        Write-Warning "Cleanup failed for test project $projectName. Inspect and remove only that project's test resources. Temporary credentials were removed."
    }
}
