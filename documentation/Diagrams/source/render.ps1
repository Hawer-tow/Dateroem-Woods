param(
  # Render only diagrams whose file name matches this pattern.
  [string]$Only = "*"
)
# Renders every diagrams\src\*.mmd (Mermaid) to diagrams\out\*.png at 2x,
# cropped to the drawing, using one headless Edge over the DevTools protocol.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root 'src'
$out = Join-Path $root 'out'
New-Item -ItemType Directory -Force $out | Out-Null
$template = [IO.File]::ReadAllText((Join-Path $root 'template.html'))
$profile = "C:\Users\PC\AppData\Local\Temp\docs_edge_profile"
if (Test-Path $profile) { Remove-Item $profile -Recurse -Force -Confirm:$false -ErrorAction SilentlyContinue }
$port = Get-Random -Minimum 9400 -Maximum 9800
$edge = Start-Process "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" -PassThru -WindowStyle Hidden -ArgumentList @(
  "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run", "--allow-file-access-from-files", "--user-data-dir=$profile",
  "--remote-debugging-port=$port", "--disable-sync", "--window-size=1600,1200", "about:blank")
try {
  $target = $null
  for ($i = 0; $i -lt 40 -and -not $target; $i++) {
    Start-Sleep -Milliseconds 250
    try { $target = (Invoke-RestMethod "http://127.0.0.1:$port/json" -TimeoutSec 2) | Where-Object { $_.type -eq 'page' -and $_.url -eq 'about:blank' } | Select-Object -First 1 } catch {}
  }
  if (-not $target) { throw "no DevTools target" }
  $ws = New-Object System.Net.WebSockets.ClientWebSocket
  $ws.ConnectAsync([Uri]$target.webSocketDebuggerUrl, [Threading.CancellationToken]::None).Wait()
  $script:nextId = 0
  function Send-Cdp([string]$method, $params = @{}) {
    $script:nextId++
    $id = $script:nextId
    $json = @{ id = $id; method = $method; params = $params } | ConvertTo-Json -Depth 10 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $ws.SendAsync([ArraySegment[byte]]$bytes, 'Text', $true, [Threading.CancellationToken]::None).Wait()
    while ($true) {
      $ms = New-Object IO.MemoryStream
      do {
        $buf = New-Object byte[] 4194304
        $cts = New-Object Threading.CancellationTokenSource 60000; $res = $ws.ReceiveAsync([ArraySegment[byte]]$buf, $cts.Token).Result
        if ($res.MessageType -eq 'Close') { throw 'DevTools connection closed' }
        $ms.Write($buf, 0, $res.Count)
      } while (-not $res.EndOfMessage)
      $msg = [Text.Encoding]::UTF8.GetString($ms.ToArray()) | ConvertFrom-Json
      if ($msg.id -eq $id) {
        if ($msg.error) { throw "$method failed: $($msg.error.message)" }
        return $msg.result
      }
    }
  }
  function Eval-Js([string]$js) {
    $r = Send-Cdp 'Runtime.evaluate' @{ expression = $js; returnByValue = $true; awaitPromise = $true }
    if ($r.exceptionDetails) { return "JS error: $($r.exceptionDetails.exception.description)" }
    return $r.result.value
  }
  Send-Cdp 'Emulation.setDeviceMetricsOverride' @{ width = 1600; height = 1200; deviceScaleFactor = 2; mobile = $false } | Out-Null
  Send-Cdp 'Page.enable' | Out-Null
  # A fresh profile may open Edge's sync dialog as a second page; close it so
  # this page is not left in the background (slowed timers, no frames).
  try { (Invoke-RestMethod "http://127.0.0.1:$port/json" -TimeoutSec 2) | Where-Object { $_.type -eq 'page' -and $_.id -ne $target.id } | ForEach-Object { try { Invoke-RestMethod "http://127.0.0.1:$port/json/close/$($_.id)" -TimeoutSec 2 | Out-Null } catch {} } } catch {}
  Send-Cdp 'Page.bringToFront' | Out-Null
  foreach ($file in Get-ChildItem $src -Include "$Only.mmd", "$Only.html" -File -Recurse | Sort-Object Name) {
    $name = [IO.Path]::GetFileNameWithoutExtension($file.Name)
    $code = [IO.File]::ReadAllText($file.FullName)
    # .mmd = Mermaid text dropped into the template; .html = a hand-drawn
    # page (inline SVG inside #wrap) that sets window.__state itself.
    $html = if ($file.Extension -eq '.html') { $code } else { $template.Replace('%%DIAGRAM%%', [Net.WebUtility]::HtmlEncode($code)) }
    $page = Join-Path $root "page_$name.html"
    [IO.File]::WriteAllText($page, $html, [Text.Encoding]::UTF8)
    Send-Cdp 'Page.navigate' @{ url = ([Uri]$page).AbsoluteUri } | Out-Null
    $state = $null
    for ($i = 0; $i -lt 60; $i++) {
      Start-Sleep -Milliseconds 300
      $state = Eval-Js "window.__state || ''"
      if ($state -eq 'ready' -or "$state".StartsWith('error')) { break }
    }
    if ($state -ne 'ready') { "FAILED $name : $state"; continue }
    $box = Eval-Js "(() => { const r = document.getElementById('wrap').getBoundingClientRect(); return [r.x, r.y, Math.ceil(r.width), Math.ceil(r.height)].join(',') })()"
    $x, $y, $w, $h = $box.Split(',') | ForEach-Object { [double]$_ }
    $shot = Send-Cdp 'Page.captureScreenshot' @{ format = 'png'; captureBeyondViewport = $true; clip = @{ x = $x; y = $y; width = $w; height = $h; scale = 1 } }
    $png = Join-Path $out "$name.png"
    [IO.File]::WriteAllBytes($png, [Convert]::FromBase64String($shot.data))
    Remove-Item $page -Force
    "rendered $name ($([int]$w) x $([int]$h))"
  }
  $ws.Dispose()
} finally {
  Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like '*docs_edge_profile*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false -ErrorAction SilentlyContinue }
}
