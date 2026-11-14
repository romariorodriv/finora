[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$ProfileName,
  [string[]]$DistributionIds=@('E2R1MKOPXY5S3Z','E2K6NVKTSILZ56')
)
$ErrorActionPreference='Stop'
function Invoke-SarelaAwsRead {
  param([string[]]$Arguments)
  $output=& aws @Arguments --profile $ProfileName --no-cli-pager --output json
  if($LASTEXITCODE -ne 0){throw "Read-only AWS command failed: $($Arguments -join ' ')"}
  return $output | ConvertFrom-Json
}
$identity=Invoke-SarelaAwsRead @('sts','get-caller-identity')
Write-Host "AWS account:" $identity.Account
foreach($id in $DistributionIds){
  $r=Invoke-SarelaAwsRead @('cloudfront','get-distribution-config','--id',$id)
  $c=$r.DistributionConfig
  Write-Host "=== Distribution $id ==="
  Write-Host "Aliases:" ($c.Aliases.Items -join ', ')
  Write-Host "Origins:" (($c.Origins.Items | ForEach-Object { $_.Id + ' -> ' + $_.DomainName }) -join ', ')
  Write-Host "Default origin:" $c.DefaultCacheBehavior.TargetOriginId
  Write-Host "Viewer protocol:" $c.DefaultCacheBehavior.ViewerProtocolPolicy
  Write-Host "ETag (for conditional updates):" $r.ETag
  Write-Host "Functions:" (($c.DefaultCacheBehavior.FunctionAssociations.Items | ForEach-Object { $_.EventType + ' -> ' + $_.FunctionARN }) -join ', ')
  Write-Host "Lambda@Edge:" (($c.DefaultCacheBehavior.LambdaFunctionAssociations.Items | ForEach-Object { $_.EventType + ' -> ' + $_.LambdaFunctionARN }) -join ', ')
  Write-Host "Custom errors:" (($c.CustomErrorResponses.Items | ForEach-Object { [string]$_.ErrorCode + ' -> ' + [string]$_.ResponseCode + ' path=' + [string]$_.ResponsePagePath }) -join ', ')
  Write-Host "Additional behaviors:" (($c.CacheBehaviors.Items | ForEach-Object { $_.PathPattern + ' -> ' + $_.TargetOriginId }) -join ', ')
}
Write-Warning 'READ-ONLY. No CloudFront configuration was changed. Check Google OAuth callback and cookie origin before applying legacy-domain redirects.'
