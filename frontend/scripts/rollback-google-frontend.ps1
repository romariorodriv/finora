[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$ProfileName,
  [Parameter(Mandatory=$true)][string]$BackupDirectory,
  [switch]$Apply
)
$ErrorActionPreference='Stop'
$BackupDirectory=(Resolve-Path -LiteralPath $BackupDirectory).Path
$release=Get-Content -LiteralPath (Join-Path $BackupDirectory 'release.json') -Raw | ConvertFrom-Json
if($release.FormatVersion -ne 1 -or -not $release.Hashes.'index.html'){throw 'Unsupported or incomplete backup.'}
$content=(Resolve-Path -LiteralPath (Join-Path $BackupDirectory 'content')).Path
# Verify every recorded file before any AWS write. Metadata is not a shell command.
foreach($entry in $release.Hashes.PSObject.Properties){
  $file=[IO.Path]::GetFullPath((Join-Path $content $entry.Name))
  if(-not $file.StartsWith($content.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Backup path escapes content directory.'}
  if(-not (Test-Path -LiteralPath $file) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.Value){throw "Missing or changed backup file: $($entry.Name)"}
}
function Invoke-FinoraAws {
  param([string[]]$AwsArguments)
  $result=& aws @AwsArguments --profile $ProfileName --no-cli-pager
  if($LASTEXITCODE -ne 0){throw "AWS command failed: $($AwsArguments[0]) $($AwsArguments[1])"}
  return $result
}
$identity=Invoke-FinoraAws @('sts','get-caller-identity','--output','json') | ConvertFrom-Json
if($identity.Account -ne $release.Account){throw 'AWS account differs from the recorded backup.'}
$distribution=(Invoke-FinoraAws @('cloudfront','get-distribution','--id',$release.Distribution,'--output','json') | ConvertFrom-Json).Distribution
if($distribution.DistributionConfig.Aliases.Items -notcontains $release.Domain){throw 'Distribution domain changed.'}
$origin=@($distribution.DistributionConfig.Origins.Items | Where-Object {$_.Id -eq $distribution.DistributionConfig.DefaultCacheBehavior.TargetOriginId})
if($origin.Count -ne 1 -or $origin[0].DomainName -notmatch '^(?<bucket>.+)\.s3(?:[.-][a-z0-9-]+)?\.amazonaws\.com$'){throw 'Cannot verify the original S3 origin.'}
$target='s3://'+$Matches['bucket']+'/'
$prefix=$origin[0].OriginPath.Trim('/')
if($prefix){$target+=$prefix+'/'}
if($target -ne $release.Target){throw 'Origin path or bucket differs from backup; refusing rollback.'}
$legal=@('privacidad.html','terminos.html','eliminar-datos.html','finora-legal.css','finora.svg')
$restore=@($release.Hashes.PSObject.Properties.Name)
if($release.LegalOnly){$restore=@($restore | Where-Object {$_ -in $legal})}
$remove=@($legal | Where-Object {$_ -notin $release.Hashes.PSObject.Properties.Name})
[pscustomobject]@{Domain=$release.Domain;Target=$target;Backup=$BackupDirectory;RestoreFiles=$restore.Count;RemoveNewLegalFiles=($remove -join ', ');Mode=$(if($Apply){'RESTORE'}else{'READ-ONLY PREVIEW'})} | Format-List
if(-not $Apply){Write-Output 'Preview only. No AWS writes. Review before -Apply.';return}
# cp (not sync): overwrite even when size or timestamps happen to coincide.
foreach($key in @($restore | Where-Object {-not $_.EndsWith('.html')})){
  $cache=if($key -in $legal){'no-cache,no-store,must-revalidate'}else{'public,max-age=31536000,immutable'}
  Invoke-FinoraAws @('s3','cp',(Join-Path $content $key),($target+$key),'--cache-control',$cache) | Out-Null
}
# Restore old HTML last, with index.html last of all.
$html=@($restore | Where-Object {$_.EndsWith('.html') -and $_ -ne 'index.html'})
if($restore -contains 'index.html'){$html+='index.html'}
foreach($key in $html){
  Invoke-FinoraAws @('s3','cp',(Join-Path $content $key),($target+$key),'--content-type','text/html; charset=utf-8','--cache-control','no-cache,no-store,must-revalidate') | Out-Null
}
# Only remove named legal files introduced by this publication; keep all chunks.
foreach($key in $remove){Invoke-FinoraAws @('s3','rm',($target+$key)) | Out-Null}
Invoke-FinoraAws @('cloudfront','create-invalidation','--distribution-id',$release.Distribution,'--paths','/*') | Out-Null
Write-Output 'Previous frontend content restored. New JS chunks were retained. Original object metadata/version IDs are not restored.'
Write-Output 'If legal pages were previously absent, rollback removes the newly added pages. Verify publicly. Backend and database were not changed.'
