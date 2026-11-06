[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$ProfileName,
  [string]$DomainName='sarelaapp.com',
  [string]$BuildDirectory,
  [string]$ExpectedDistributionId='E2R1MKOPXY5S3Z',
  [string]$BackupDirectory,
  [switch]$LegalOnly,
  [switch]$Apply
)
$ErrorActionPreference='Stop'
if(-not $BuildDirectory){$BuildDirectory=Join-Path $PSScriptRoot '../dist/frontend/browser'}
$BuildDirectory=(Resolve-Path -LiteralPath $BuildDirectory).Path
$sarelaLegalFiles=@('privacidad.html','terminos.html','eliminar-datos.html','sarela-legal.css','sarela-icon.svg','robots.txt','sitemap.xml')
foreach($sarelaPage in (@('index.html')+$sarelaLegalFiles)){
  if(-not (Test-Path -LiteralPath (Join-Path $BuildDirectory $sarelaPage))){throw "Missing build artifact: $sarelaPage"}
}
# Only operator-reviewed legal documents may be published.
foreach($sarelaPage in @('privacidad.html','terminos.html','eliminar-datos.html')){
  $sarelaHtml=Get-Content -LiteralPath (Join-Path $BuildDirectory $sarelaPage) -Raw
  if($sarelaHtml -notmatch 'name="sarela-publication-status" content="operator-reviewed"'){
    if($Apply){throw "Legal page is not approved for publication: $sarelaPage. No AWS writes performed."}
    Write-Warning "LEGAL DOCUMENT NOT REVIEWED / PUBLICATION BLOCKED: $sarelaPage"
  }
}
function Invoke-SarelaAws {
  param([string[]]$AwsArguments)
  $sarelaResult = & aws @AwsArguments --profile $ProfileName --no-cli-pager
  if($LASTEXITCODE -ne 0){throw "AWS command failed: $($AwsArguments[0]) $($AwsArguments[1])"}
  return $sarelaResult
}
$sarelaIdentity=Invoke-SarelaAws @('sts','get-caller-identity','--output','json') | ConvertFrom-Json
$sarelaInventory=Invoke-SarelaAws @('cloudfront','list-distributions','--output','json') | ConvertFrom-Json
$sarelaMatches=@($sarelaInventory.DistributionList.Items | Where-Object {$_.Aliases.Items -contains $DomainName})
if($sarelaMatches.Count -ne 1){throw 'Expected one distribution matching the exact Sarela domain. No changes made.'}
$sarelaDistribution=$sarelaMatches[0]
if($sarelaDistribution.Id -ne $ExpectedDistributionId){throw 'Unexpected CloudFront distribution. Expected the dedicated sarelaapp.com distribution. No changes made.'}
$sarelaOrigin=@($sarelaDistribution.Origins.Items | Where-Object {$_.Id -eq $sarelaDistribution.DefaultCacheBehavior.TargetOriginId})
if($sarelaOrigin.Count -ne 1){throw 'Cannot identify default frontend origin. No changes made.'}
$sarelaHost=$sarelaOrigin[0].DomainName
if($sarelaHost -notmatch '^(?<bucket>.+)\.s3(?:[.-][a-z0-9-]+)?\.amazonaws\.com$'){throw 'Default origin is not a recognized S3 hostname. Review manually; no guessed bucket.'}
$sarelaBucket=$Matches['bucket']
$sarelaPrefix=$sarelaOrigin[0].OriginPath.Trim('/')
$sarelaTarget="s3://$sarelaBucket/"
if($sarelaPrefix){$sarelaTarget+="$sarelaPrefix/"}
Invoke-SarelaAws @('s3api','head-bucket','--bucket',$sarelaBucket) | Out-Null
[pscustomobject]@{
  AwsAccount=$sarelaIdentity.Account
  Profile=$ProfileName
  Domain=$DomainName
  Distribution=$sarelaDistribution.Id
  Target=$sarelaTarget
  Source=$BuildDirectory
  Scope= $(if($LegalOnly){'LEGAL PAGES ONLY'}else{'FULL FRONTEND - verify deployed API compatibility first'})
  Mode= $(if($Apply){'PUBLISH'}else{'READ-ONLY PREVIEW'})
} | Format-List
if(-not $Apply){
  Write-Output 'Preview only. No AWS writes. Confirm OAuth/backend origin compatibility before -Apply; this script never modifies backend or database.'
  return
}
if(-not $BackupDirectory){$BackupDirectory=Join-Path $env:TEMP ('sarela-frontend-backup-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
if(Test-Path -LiteralPath $BackupDirectory){throw 'Backup directory already exists; choose a new directory to avoid mixing releases.'}
New-Item -ItemType Directory -Path $BackupDirectory | Out-Null
$sarelaBackupContent=Join-Path $BackupDirectory 'content'
New-Item -ItemType Directory -Path $sarelaBackupContent | Out-Null
Invoke-SarelaAws @('s3','sync',$sarelaTarget,$sarelaBackupContent,'--no-follow-symlinks') | Out-Null
if(-not (Test-Path -LiteralPath (Join-Path $sarelaBackupContent 'index.html'))){throw 'Backup has no index.html. Deployment stopped before upload.'}
# Record the exact previous content and target, outside the files uploaded to S3.
$sarelaHashes=@{}
$sarelaBackupRoot=(Resolve-Path -LiteralPath $sarelaBackupContent).Path.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
Get-ChildItem -LiteralPath $sarelaBackupContent -Recurse -File | ForEach-Object {
  $sarelaKey=$_.FullName.Substring($sarelaBackupRoot.Length).Replace([IO.Path]::DirectorySeparatorChar,'/')
  $sarelaHashes[$sarelaKey]=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
}
@{FormatVersion=1;Account=$sarelaIdentity.Account;Domain=$DomainName;Distribution=$sarelaDistribution.Id;Target=$sarelaTarget;LegalOnly=[bool]$LegalOnly;CreatedAt=(Get-Date).ToUniversalTime().ToString('o');Hashes=$sarelaHashes} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $BackupDirectory 'release.json') -Encoding UTF8
# Keep old chunks; open tabs may still need them. No --delete.
if(-not $LegalOnly){
  Invoke-SarelaAws @('s3','sync',$BuildDirectory,$sarelaTarget,'--exclude','*.html','--exclude','sarela-legal.css','--exclude','sarela-icon.svg','--exclude','robots.txt','--exclude','sitemap.xml','--cache-control','public,max-age=31536000,immutable','--no-follow-symlinks') | Out-Null
}
foreach($sarelaAsset in @('sarela-legal.css','sarela-icon.svg')){
  $sarelaType=if($sarelaAsset.EndsWith('.css')){'text/css; charset=utf-8'}else{'image/svg+xml'}
  Invoke-SarelaAws @('s3','cp',(Join-Path $BuildDirectory $sarelaAsset),($sarelaTarget+$sarelaAsset),'--content-type',$sarelaType,'--cache-control','no-cache,no-store,must-revalidate') | Out-Null
}
foreach($sarelaAsset in @('robots.txt','sitemap.xml')){
  $sarelaType=if($sarelaAsset.EndsWith('.xml')){'application/xml; charset=utf-8'}else{'text/plain; charset=utf-8'}
  Invoke-SarelaAws @('s3','cp',(Join-Path $BuildDirectory $sarelaAsset),($sarelaTarget+$sarelaAsset),'--content-type',$sarelaType,'--cache-control','no-cache,no-store,must-revalidate') | Out-Null
}
$sarelaHtmlFiles=@('privacidad.html','terminos.html','eliminar-datos.html')
if(-not $LegalOnly){$sarelaHtmlFiles+= 'index.html'}
foreach($sarelaPage in $sarelaHtmlFiles){
  Invoke-SarelaAws @('s3','cp',(Join-Path $BuildDirectory $sarelaPage),($sarelaTarget+$sarelaPage),'--content-type','text/html; charset=utf-8','--cache-control','no-cache,no-store,must-revalidate') | Out-Null
}
$sarelaPaths=@('/privacidad.html','/terminos.html','/eliminar-datos.html','/sarela-legal.css','/sarela-icon.svg','/robots.txt','/sitemap.xml')
if(-not $LegalOnly){$sarelaPaths+=@('/','/index.html')}
Invoke-SarelaAws (@('cloudfront','create-invalidation','--distribution-id',$sarelaDistribution.Id,'--paths')+$sarelaPaths) | Out-Null
Write-Output "Frontend files published. Backup: $BackupDirectory"
Write-Output 'Rollback: run rollback-google-frontend.ps1 with this BackupDirectory and the same ProfileName; preview first, then -Apply.'
Write-Output 'Verify public legal HTML after publication. This script does not change OAuth, IAM, database schema, backend or CloudFront routing.'
