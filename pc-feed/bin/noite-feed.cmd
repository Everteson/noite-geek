@echo off
rem Atalho pro noite-feed.ps1 (funciona no cmd, no PowerShell e no Executar).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0noite-feed.ps1" %*
