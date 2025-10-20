# Script para detener el sistema RMI distribuido

Write-Host "🛑 Deteniendo Sistema RMI Distribuido..." -ForegroundColor Red

# Buscar y terminar procesos Java relacionados con el sistema
$javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like "*distributed-rmi*" -or 
    $_.CommandLine -like "*ControladorSeguridadService*" -or
    $_.CommandLine -like "*BalanceadorCargaService*" -or
    $_.CommandLine -like "*SimpleNodeBootstrap*" -or
    $_.CommandLine -like "*WebServer*"
}

if ($javaProcesses) {
    Write-Host "Terminando procesos Java del sistema..."
    foreach ($process in $javaProcesses) {
        Write-Host "- Terminando proceso PID: $($process.Id)"
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    Write-Host "✅ Procesos terminados correctamente!" -ForegroundColor Green
} else {
    Write-Host "⚠️ No se encontraron procesos del sistema ejecutándose." -ForegroundColor Yellow
}

Write-Host "🔚 Sistema detenido."