# Usage: .\fault_inject.ps1 backend1|backend2 on|off
param(
    [Parameter(Mandatory=$true)][ValidateSet("backend1","backend2")][string]$Target,
    [Parameter(Mandatory=$true)][ValidateSet("on","off")][string]$Action
)

$Port = if ($Target -eq "backend1") { 8081 } else { 8082 }
$Path = if ($Action -eq "on") { "/fault/exhaust-pool" } else { "/fault/reset" }

$response = Invoke-RestMethod -Method Post -Uri "http://localhost:$Port$Path"
$response | ConvertTo-Json
