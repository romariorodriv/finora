[CmdletBinding()]
param([string]$OutputPath)
$ErrorActionPreference = 'Stop'
$frontend = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $frontend '..')).Path
if (-not $OutputPath) { $OutputPath = Join-Path $repo 'sarelaapp-frontend-marca-sarela.zip' }
if (Test-Path -LiteralPath $OutputPath) { throw "The ZIP already exists: $OutputPath. Move it or choose another output path." }

Push-Location $repo
try {
  $branch = (& git branch --show-current).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'fix/sarela-rebrand-frontend') { throw 'Checkout fix/sarelaapp-domain-migration first.' }
  $changes = & git status --porcelain -- frontend
  if ($LASTEXITCODE -ne 0 -or $changes) { throw 'Frontend has uncommitted changes; commit or revert them before packaging.' }
} finally { Pop-Location }

Push-Location $frontend
try {
  & npm ci
  if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
  & npm test -- --watch=false --browsers=ChromeHeadless
  if ($LASTEXITCODE -ne 0) { throw 'Angular unit tests failed' }
  & npm run build
  if ($LASTEXITCODE -ne 0) { throw 'Angular production build failed' }
  & npm run qa:domain
  if ($LASTEXITCODE -ne 0) { throw 'Domain QA failed' }
  & npm run qa:legal
  if ($LASTEXITCODE -ne 0) { throw 'Legal page QA failed' }
  & npm run qa:brand
  if ($LASTEXITCODE -ne 0) { throw 'Sarela brand QA failed' }
} finally { Pop-Location }

$staging = Join-Path $env:TEMP ('sarelaapp-zip-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
try {
  $sourceZip = Join-Path $staging 'source.zip'
  & git -C $repo archive --format=zip --prefix=frontend/ -o $sourceZip HEAD:frontend
  if ($LASTEXITCODE -ne 0) { throw 'Cannot archive the committed frontend source' }
  $unpacked = Join-Path $staging 'unpacked'
  Expand-Archive -LiteralPath $sourceZip -DestinationPath $unpacked
  $dist = Join-Path $frontend 'dist/frontend/browser'
  if (-not (Test-Path -LiteralPath (Join-Path $dist 'index.html'))) { throw 'Compiled browser index was not found' }
  $distTarget = Join-Path $unpacked 'frontend/dist/frontend/browser'
  New-Item -ItemType Directory -Path $distTarget -Force | Out-Null
  Copy-Item -Path (Join-Path $dist '*') -Destination $distTarget -Recurse -Force
  Compress-Archive -Path (Join-Path $unpacked 'frontend') -DestinationPath $OutputPath -CompressionLevel Optimal
  Write-Output "ZIP created: $OutputPath"
  Write-Output 'Content: frontend source + dist/frontend/browser; excludes node_modules and backend.'
} finally {
  Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
}
