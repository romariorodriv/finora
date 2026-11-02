[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$ProfileName,
  [string]$DomainName='sarela.pgoapp.com',
  [string]$BuildDirectory,
  [string]$BackupDirectory,
  [switch]$Apply
)
$ErrorActionPreference='Stop'
if(-not $BuildDirectory){$BuildDirectory=Join-Path $PSScriptRoot '../dist/frontend/browser'}
$BuildDirectory=(Resolve-Path -LiteralPath $BuildDirectory).Path
foreach($sarelaPage in @('index.html','privacidad.html','terminos.html','eliminar-datos.html')){
  if(-not (Test-Path -LiteralPath (Join-Path $BuildDirectory $sarelaPage))){throw "Missing build artifact: $sarelaPage"}
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
  Mode= $(if($Apply){'PUBLISH'}else{'READ-ONLY PREVIEW'})
} | Format-List
if(-not $Apply){
  Write-Output 'Preview only. Review the target, Google approvals, database migration and backend before using -Apply.'
  return
}
if(-not $BackupDirectory){$BackupDirectory=Join-Path $env:TEMP ('sarela-frontend-backup-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
if(Test-Path -LiteralPath $BackupDirectory){throw 'Backup directory already exists; choose a new directory to avoid mixing releases.'}
New-Item -ItemType Directory -Path $BackupDirectory | Out-Null
Invoke-SarelaAws @('s3','sync',$sarelaTarget,$BackupDirectory) | Out-Null
if(-not (Test-Path -LiteralPath (Join-Path $BackupDirectory 'index.html'))){throw 'Backup has no index.html. Deployment stopped before upload.'}
# Keep old chunks; open tabs may still need them. No --delete.
Invoke-SarelaAws @('s3','sync',$BuildDirectory,$sarelaTarget,'--exclude','*.html','--cache-control','public,max-age=31536000,immutable') | Out-Null
foreach($sarelaPage in @('privacidad.html','terminos.html','eliminar-datos.html','index.html')){
  Invoke-SarelaAws @('s3','cp',(Join-Path $BuildDirectory $sarelaPage),($sarelaTarget+$sarelaPage),'--content-type','text/html; charset=utf-8','--cache-control','no-cache,no-store,must-revalidate') | Out-Null
}
Invoke-SarelaAws @('cloudfront','create-invalidation','--distribution-id',$sarelaDistribution.Id,'--paths','/','/index.html','/privacidad.html','/terminos.html','/eliminar-datos.html') | Out-Null
Write-Output "Frontend published. Backup: $BackupDirectory"
Write-Output 'Verify sign-in, consent and import with staging-approved accounts. This script does not change OAuth, IAM, database schema or CloudFront routing.'
