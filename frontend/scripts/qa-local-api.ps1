param([string]$BaseUrl = 'http://localhost:8080/api/v1')
$ErrorActionPreference = 'Stop'
$target = [Uri]$BaseUrl
if ($target.Host -notin @('localhost','127.0.0.1')) { throw 'QA only supports a disposable local backend; remote APIs are not allowed.' }
function Assert-QA([bool]$Condition, [string]$Message) { if (-not $Condition) { throw "FAIL: $Message" } }
function Invoke-QA([string]$Method, [string]$Path, $Body = $null, [string]$Token = '') {
  $arguments = @{Method=$Method;Uri="$BaseUrl$Path";ContentType='application/json'}
  if ($null -ne $Body) { $arguments.Body=($Body | ConvertTo-Json -Depth 5 -Compress) }
  if ($Token) { $arguments.Headers=@{Authorization="Bearer $Token"} }
  Invoke-RestMethod @arguments
}
# All data belongs to a disposable H2 test process, never a real user's account.
$nonce=[Guid]::NewGuid().ToString('N')
$qaPassword=[Guid]::NewGuid().ToString('N')+'Aa1!'
$qaEmail="premium-$nonce@example.test"
$owner=Invoke-QA POST '/auth/register' @{name='QA premium local';email=$qaEmail;password=$qaPassword}
$other=Invoke-QA POST '/auth/register' @{name='QA aislamiento local';email="other-$nonce@example.test";password=$qaPassword}
$today=[DateTime]::Today
$profile=Invoke-QA PUT '/planning/state' @{confirmedBalance=1000;reserve=50;nextIncomeDate=$today.AddDays(10).ToString('yyyy-MM-dd')} $owner.token
Assert-QA ($profile.projection.available -eq 950) 'Profile calculation'
$withPayment=Invoke-QA POST '/planning/payments' @{name='QA pago local';dueDate=$today.AddDays(2).ToString('yyyy-MM-dd');amount=300} $owner.token
Assert-QA ($withPayment.projection.available -eq 650 -and $withPayment.projection.dailyLimit -eq 65) 'Payment affects availability'
$paymentId=$withPayment.payments[0].id
$login=Invoke-QA POST '/auth/login' @{email=$qaEmail;password=$qaPassword}
$restored=Invoke-QA GET '/planning/state' $null $login.token
Assert-QA ($restored.profile.confirmedBalance -eq 1000 -and $restored.payments[0].id -eq $paymentId -and $restored.projection.available -eq 650) 'Persistence after a fresh login'
$isolated=Invoke-QA GET '/planning/state' $null $other.token
Assert-QA ($null -eq $isolated.profile -and $isolated.payments.Count -eq 0) 'Other user cannot read the plan'
$denied=$false
try { $null=Invoke-QA DELETE "/planning/payments/$paymentId" $null $other.token }
catch { $denied=([int]$_.Exception.Response.StatusCode -eq 404) }
Assert-QA $denied 'Other user cannot delete the payment'
$removed=Invoke-QA DELETE "/planning/payments/$paymentId" $null $owner.token
Assert-QA ($removed.payments.Count -eq 0 -and $removed.projection.available -eq 950) 'Owner can remove payment'
$tx=Invoke-QA POST '/transactions' @{description='QA local descartable';merchant='Comercio QA local';amount=42;date=$today.ToString('yyyy-MM-dd');category='Suscripciones';type='EXPENSE';recurring=$true} $owner.token
$dashboard=Invoke-QA GET '/dashboard' $null $owner.token
Assert-QA ($dashboard.expenses -eq 42 -and $dashboard.recurring.Count -eq 1 -and $dashboard.transactions.Count -eq 1) 'Dashboard and recurring records use real transactions'
$category=Invoke-QA GET '/dashboard/categories/SUSCRIPCIONES' $null $owner.token
Assert-QA ($category.total -eq 42) 'Category detail'
$merchantKey=[Uri]::EscapeDataString($dashboard.merchants[0].merchant)
$merchant=Invoke-QA GET "/dashboard/merchants/$merchantKey" $null $owner.token
Assert-QA ($merchant.total -eq 42) 'Merchant detail'
$feedback=Invoke-QA POST '/feedback' @{rating=5;understoodSpending='YES';liked='QA local';improvement='QA descartable';wantedFeature=''} $owner.token
Assert-QA ($feedback.rating -eq 5) 'Feedback persistence'
$gmail=Invoke-QA GET '/integrations/gmail/status' $null $owner.token
Assert-QA (-not $gmail.configured) 'Gmail disabled in isolated QA environment'
$null=Invoke-QA DELETE "/transactions/$($tx.id)" $null $owner.token
[ordered]@{result='PASS';environment='Local disposable H2';checks=@('JWT register/login','Planning save and calculation','Payment CRUD','Persistence after fresh login','Cross-user authorization','Dashboard and subscriptions','Category detail','Merchant detail','Feedback','Gmail configuration read');awsAccess=$false;externalGmailSync=$false} | ConvertTo-Json -Depth 4
