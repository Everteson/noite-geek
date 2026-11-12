# noite-feed: controla o buscador do Noite Geek (o feed do YouTube e da Crunchyroll pra TV).
#   noite-feed            mostra se esta rodando e as ultimas linhas do log
#   noite-feed iniciar    inicia escondido (se ja nao estiver rodando)
#   noite-feed parar      para
#   noite-feed reiniciar  para e inicia (atualiza as listas na hora)
#   noite-feed log        mostra o log inteiro de hoje
#   noite-feed canal X    usa o canal X do YouTube (ex.: noite-feed canal @seu_canal) e reinicia
#   noite-feed pasta      abre a pasta do feed
param([string]$cmd = "status", [string]$arg = "")
$ErrorActionPreference = "Stop"
$dir = Split-Path -Parent $PSScriptRoot
$py = Join-Path $dir "venv\Scripts\pythonw.exe"
$server = Join-Path $dir "feed_server.py"
$logf = Join-Path $dir "feed.log"

function Get-Feed {
    Get-CimInstance Win32_Process -Filter "Name='pythonw.exe' OR Name='python.exe'" |
        Where-Object { $_.CommandLine -like "*feed_server.py*" -and $_.CommandLine -notlike "*--once*" }
}
function Start-Feed {
    if (Get-Feed) { Write-Host "Ja esta rodando."; return }
    Start-Process -FilePath $py -ArgumentList @("-I", "`"$server`"") -WorkingDirectory $dir -WindowStyle Hidden
    Write-Host "Iniciado. Atualizando as listas (uns 20 segundos)..."
    Start-Sleep -Seconds 20
    Get-Content $logf -Tail 6 -Encoding UTF8
}
function Stop-Feed {
    $p = @(Get-Feed)
    if (-not $p) { Write-Host "Nao estava rodando."; return }
    $p | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
    Write-Host "Parado."
}

switch ($cmd.ToLower()) {
    { $_ -in "status", "estado" } {
        $p = @(Get-Feed)
        if ($p) { Write-Host "Rodando (processo $($p[0].ProcessId)), porta 8787." -ForegroundColor Green }
        else { Write-Host "Parado. Use: noite-feed iniciar" -ForegroundColor Yellow }
        if (Test-Path $logf) { Write-Host "--- ultimas linhas do log ---"; Get-Content $logf -Tail 8 -Encoding UTF8 }
    }
    { $_ -in "iniciar", "start" } { Start-Feed }
    { $_ -in "parar", "stop" } { Stop-Feed }
    { $_ -in "reiniciar", "restart", "atualizar" } { Stop-Feed; Start-Sleep -Seconds 1; Start-Feed }
    "log" { $d = Get-Date -Format "yyyy-MM-dd"; Get-Content $logf -Encoding UTF8 | Where-Object { $_ -like "$d*" } }
    "canal" {
        if (-not $arg) { Write-Host "Uso: noite-feed canal @nome_do_canal   (vazio = o canal ativo)"; break }
        $cfgf = Join-Path $dir "config.json"
        $cfg = Get-Content $cfgf -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($cfg.PSObject.Properties.Name -contains "yt_channel") { $cfg.yt_channel = $arg }
        else { $cfg | Add-Member -NotePropertyName yt_channel -NotePropertyValue $arg }
        [IO.File]::WriteAllText($cfgf, ($cfg | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
        Write-Host "Canal do YouTube: $arg"
        Stop-Feed; Start-Sleep -Seconds 1; Start-Feed
    }
    "pasta" { Invoke-Item $dir }
    default { Get-Content $PSCommandPath -TotalCount 8 | Select-Object -Skip 1 | ForEach-Object { $_.TrimStart("# ") } }
}
