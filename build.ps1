param(
    [ValidateSet('FullScreen','NavKeys','TabletAdaptive')][string]$Variant='FullScreen',
    [string]$AndroidSdk=$env:ANDROID_HOME,
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$KeyStore,
    [string]$KeyAlias
)
$ErrorActionPreference='Stop'
$Project=[IO.Path]::GetFullPath($PSScriptRoot)
if (-not $AndroidSdk -or -not $JavaHome) { throw 'Set ANDROID_HOME and JAVA_HOME, or pass -AndroidSdk and -JavaHome.' }
$Sdk=(Resolve-Path -LiteralPath $AndroidSdk).Path
$Jdk=(Resolve-Path -LiteralPath $JavaHome).Path
$BuildTools=Join-Path $Sdk 'build-tools\35.0.0'
$AndroidJar=Join-Path $Sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME=$Jdk
$env:Path="$(Join-Path $Jdk 'bin');$env:Path"
$Stamp=(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$Out=Join-Path $Project ".build\$Variant-$Stamp"
$Dist=Join-Path $Project 'dist'
New-Item -ItemType Directory -Path "$Out\classes","$Out\dex",$Dist -Force | Out-Null
function Invoke-Tool([string]$Tool,[string[]]$ToolArgs) {
    & $Tool @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}
$Manifest=Join-Path $Out 'AndroidManifest.xml'
$Xml=Get-Content -LiteralPath (Join-Path $Project 'AndroidManifest.xml') -Raw -Encoding UTF8
if ($Variant -eq 'NavKeys') { $Xml=$Xml -replace '(android:name="com\.example\.worklog\.NAV_KEYS_LAYOUT"\s+android:value=")false(")','${1}true${2}' }
if ($Variant -eq 'TabletAdaptive') {
    $Xml=$Xml -replace '(android:name="com\.example\.worklog\.TABLET_LAYOUT"\s+android:value=")false(")','${1}true${2}'
    $Xml=$Xml -replace 'android:screenOrientation="portrait"','android:screenOrientation="fullUser"'
}
[IO.File]::WriteAllText($Manifest,$Xml,(New-Object Text.UTF8Encoding($false)))
$Sources=@(Get-ChildItem -LiteralPath (Join-Path $Project 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Tool (Join-Path $Jdk 'bin\javac.exe') (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$AndroidJar,'-d',"$Out\classes")+$Sources)
Invoke-Tool (Join-Path $Jdk 'bin\jar.exe') @('cf',"$Out\classes.jar",'-C',"$Out\classes",'.')
Invoke-Tool (Join-Path $BuildTools 'd8.bat') @('--lib',$AndroidJar,'--output',"$Out\dex","$Out\classes.jar")
Invoke-Tool (Join-Path $BuildTools 'aapt2.exe') @('compile','--dir',(Join-Path $Project 'res'),'-o',"$Out\resources.zip")
Invoke-Tool (Join-Path $BuildTools 'aapt2.exe') @('link','-o',"$Out\unsigned.apk",'-I',$AndroidJar,'--manifest',$Manifest,'--min-sdk-version','26','--target-sdk-version','35','-R',"$Out\resources.zip")
Push-Location "$Out\dex"
try { Invoke-Tool (Join-Path $BuildTools 'aapt.exe') @('add',"$Out\unsigned.apk",'classes.dex') } finally { Pop-Location }
Invoke-Tool (Join-Path $BuildTools 'zipalign.exe') @('-f','4',"$Out\unsigned.apk","$Out\aligned.apk")
if ($KeyStore) {
    if (-not $KeyAlias -or -not $env:WORKLOG_STORE_PASSWORD -or -not $env:WORKLOG_KEY_PASSWORD) { throw 'Provide -KeyAlias and both WORKLOG_*_PASSWORD environment variables for signing.' }
    $Destination=Join-Path $Dist "WorkLog-v1.70-$Variant-$Stamp.apk"
    Invoke-Tool (Join-Path $BuildTools 'apksigner.bat') @('sign','--ks',(Resolve-Path -LiteralPath $KeyStore).Path,'--ks-key-alias',$KeyAlias,'--ks-pass','env:WORKLOG_STORE_PASSWORD','--key-pass','env:WORKLOG_KEY_PASSWORD','--out',$Destination,"$Out\aligned.apk")
    Invoke-Tool (Join-Path $BuildTools 'apksigner.bat') @('verify','--verbose',$Destination)
} else {
    $Destination=Join-Path $Dist "WorkLog-v1.70-$Variant-$Stamp-unsigned.apk"
    Copy-Item -LiteralPath "$Out\aligned.apk" -Destination $Destination
    Write-Output 'Unsigned build only. Original private signing key is not included.'
}
Write-Output $Destination
