$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$testRoot=Join-Path $env:TEMP ('finora-legal-script-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$build=Join-Path $testRoot 'build';New-Item -ItemType Directory -Path $build | Out-Null
foreach($name in @('privacidad.html','terminos.html','eliminar-datos.html','finora-legal.css','finora.svg')){Copy-Item -LiteralPath (Join-Path "$repo\frontend\public" $name) -Destination $build}
foreach($name in @('privacidad.html','terminos.html','eliminar-datos.html')){$path=Join-Path $build $name;$html=[IO.File]::ReadAllText($path).Replace('content="operator-reviewed"','content="draft"');[IO.File]::WriteAllText($path,$html)}
Set-Content -LiteralPath (Join-Path $build 'index.html') -Value '<html>new frontend</html>'
Set-Content -LiteralPath (Join-Path $build 'main-new.js') -Value 'new asset'
$global:fakeObjects=@{'index.html'=[Text.Encoding]::UTF8.GetBytes('<html>previous frontend</html>');'main-old.js'=[Text.Encoding]::UTF8.GetBytes('old asset')}
$global:fakeCalls=[Collections.Generic.List[object]]::new()
$global:fakeAccount='111111111111'
$global:fakeAmbiguous=$false
function global:aws {
 $a=@($args);$global:LASTEXITCODE=0;$global:fakeCalls.Add($a)
 if($a[0] -eq 'sts'){return (@{Account=$global:fakeAccount}|ConvertTo-Json)}
 $origin=@{Id='static';DomainName='finora-test-only.s3.us-east-1.amazonaws.com';OriginPath='/site'}
 $config=@{Aliases=@{Items=@('sarelaapp.com')};Origins=@{Items=@($origin)};DefaultCacheBehavior=@{TargetOriginId='static'}}
 if($a[0] -eq 'cloudfront' -and $a[1] -eq 'list-distributions'){
  $item=@{Id='E2R1MKOPXY5S3Z';Aliases=$config.Aliases;Origins=$config.Origins;DefaultCacheBehavior=$config.DefaultCacheBehavior}
  $items=@($item);if($global:fakeAmbiguous){$items+= $item}
  return (@{DistributionList=@{Items=$items}}|ConvertTo-Json -Depth 8)
 }
 if($a[0] -eq 'cloudfront' -and $a[1] -eq 'get-distribution'){return (@{Distribution=@{DistributionConfig=$config}}|ConvertTo-Json -Depth 8)}
 if($a[0] -eq 's3api' -or $a[0] -eq 'cloudfront'){return '{}'}
 if($a[0] -ne 's3'){throw 'Unexpected AWS mock call'}
 $target='s3://finora-test-only/site/'
 if($a[1] -eq 'sync' -and $a[2] -eq $target){
  foreach($key in $global:fakeObjects.Keys){$file=Join-Path $a[3] $key;New-Item -ItemType Directory -Path (Split-Path -Parent $file) -Force | Out-Null;[IO.File]::WriteAllBytes($file,$global:fakeObjects[$key])};return
 }
 if($a[1] -eq 'sync' -and $a[3] -eq $target){
  foreach($file in Get-ChildItem -LiteralPath $a[2] -File){if($file.Extension -ne '.html'){$global:fakeObjects[$file.Name]=[IO.File]::ReadAllBytes($file.FullName)}};return
 }
 if($a[1] -eq 'cp' -and $a[3].StartsWith($target)){$key=$a[3].Substring($target.Length);$global:fakeObjects[$key]=[IO.File]::ReadAllBytes($a[2]);return}
 if($a[1] -eq 'rm' -and $a[2].StartsWith($target)){$global:fakeObjects.Remove($a[2].Substring($target.Length));return}
 throw 'Unexpected target in AWS mock'
}
function Assert-True($value,$message){if(-not $value){throw $message}}
function Mutation-Count {return @($global:fakeCalls | Where-Object {($_[0] -eq 's3' -and $_[1] -in @('cp','rm')) -or ($_[0] -eq 'cloudfront' -and $_[1] -eq 'create-invalidation') -or ($_[0] -eq 's3' -and $_[1] -eq 'sync' -and $_[3] -like 's3://*')}).Count}
$publish="$repo\frontend\scripts\publish-google-frontend.ps1"
$rollback="$repo\frontend\scripts\rollback-google-frontend.ps1"
$blocked=$false;try {& $publish -ProfileName TEST_ONLY -BuildDirectory $build -Apply | Out-Null}catch{$blocked=$_.Exception.Message -like '*not approved*'}
Assert-True $blocked 'Draft publish was not blocked';Assert-True ($global:fakeCalls.Count -eq 0) 'Draft attempted AWS calls'
& $publish -ProfileName TEST_ONLY -BuildDirectory $build | Out-Null
Assert-True ((Mutation-Count) -eq 0) 'Preview mutated AWS'
# Restore the reviewed marker only in disposable test fixture files.
foreach($name in @('privacidad.html','terminos.html','eliminar-datos.html')){$path=Join-Path $build $name;$html=[IO.File]::ReadAllText($path).Replace('content="draft"','content="operator-reviewed"');[IO.File]::WriteAllText($path,$html)}
$backup=Join-Path $testRoot 'backup-full'
& $publish -ProfileName TEST_ONLY -BuildDirectory $build -BackupDirectory $backup -Apply | Out-Null
Assert-True (Test-Path -LiteralPath (Join-Path $backup 'release.json')) 'Missing backup manifest'
Assert-True ($global:fakeObjects.ContainsKey('privacidad.html')) 'Legal HTML not uploaded'
$writes=@($global:fakeCalls | Where-Object {$_[0] -eq 's3' -and $_[1] -eq 'cp'})
Assert-True ($writes[-1][3] -eq 's3://finora-test-only/site/index.html') 'Index was not last'
$before=Mutation-Count
& $rollback -ProfileName TEST_ONLY -BackupDirectory $backup | Out-Null
Assert-True ((Mutation-Count) -eq $before) 'Rollback preview mutated AWS'
$global:fakeAccount='222222222222';$blocked=$false
try{& $rollback -ProfileName TEST_ONLY -BackupDirectory $backup -Apply | Out-Null}catch{$blocked=$_.Exception.Message -like '*account differs*'}
Assert-True $blocked 'Wrong AWS account was accepted';Assert-True ((Mutation-Count) -eq $before) 'Wrong-account rollback mutated AWS';$global:fakeAccount='111111111111'
& $rollback -ProfileName TEST_ONLY -BackupDirectory $backup -Apply | Out-Null
Assert-True ([Text.Encoding]::UTF8.GetString($global:fakeObjects['index.html']) -eq '<html>previous frontend</html>') 'Old index not restored'
Assert-True (-not $global:fakeObjects.ContainsKey('privacidad.html')) 'Previously absent legal file was not removed'
Assert-True ($global:fakeObjects.ContainsKey('main-new.js')) 'Rollback removed chunks'
$backupLegal=Join-Path $testRoot 'backup-legal'
& $publish -ProfileName TEST_ONLY -BuildDirectory $build -BackupDirectory $backupLegal -LegalOnly -Apply | Out-Null
Assert-True ([Text.Encoding]::UTF8.GetString($global:fakeObjects['index.html']) -eq '<html>previous frontend</html>') 'LegalOnly changed app index'
& $rollback -ProfileName TEST_ONLY -BackupDirectory $backupLegal -Apply | Out-Null
Assert-True ([Text.Encoding]::UTF8.GetString($global:fakeObjects['index.html']) -eq '<html>previous frontend</html>') 'Legal rollback changed index'
$global:fakeAmbiguous=$true;$before=Mutation-Count;$blocked=$false
try{& $publish -ProfileName TEST_ONLY -BuildDirectory $build -Apply | Out-Null}catch{$blocked=$_.Exception.Message -like '*Expected one distribution*'}
Assert-True $blocked 'Ambiguous distribution was accepted';Assert-True ((Mutation-Count) -eq $before) 'Ambiguous distribution mutated AWS'
$global:fakeAmbiguous=$false
Set-Content -LiteralPath (Join-Path $backup 'content/index.html') -Value 'tampered'
$blocked=$false;try{& $rollback -ProfileName TEST_ONLY -BackupDirectory $backup -Apply | Out-Null}catch{$blocked=$_.Exception.Message -like '*Missing or changed backup file*'}
Assert-True $blocked 'Changed backup was accepted'
'PASS: draft guard, read-only preview, full backup/publication, index-last, rollback preview, account guard, hash guard, full rollback, legal-only publish/rollback, ambiguous origin. AWS entirely mocked.'
Remove-Item Function:\aws
