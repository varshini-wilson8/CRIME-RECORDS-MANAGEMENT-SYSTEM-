$targetZip = "dist\CRMS_2.0_Complete_System.zip"
$stagingDir = "dist\staging"

if (Test-Path $targetZip) {
    Remove-Item -Path $targetZip -Force
}
if (Test-Path $stagingDir) {
    Remove-Item -Path $stagingDir -Recurse -Force
}

New-Item -ItemType Directory -Path $stagingDir | Out-Null

$filesToInclude = Get-ChildItem -Path . | Where-Object { 
    $_.Name -notin @('ccecs.db', 'out', 'dist', '.git', '.idea') -and -not $_.Name.EndsWith('.tmp')
}

foreach ($item in $filesToInclude) {
    Copy-Item -Path $item.FullName -Destination $stagingDir -Recurse -Force
}

Compress-Archive -Path "$stagingDir\*" -DestinationPath $targetZip -CompressionLevel Optimal
Remove-Item -Path $stagingDir -Recurse -Force

$size = (Get-Item $targetZip).Length
Write-Host "SUCCESS: Created $targetZip (Size: $size bytes)"

