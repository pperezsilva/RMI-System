# Script para iniciar el sistema RMI distribuido

Write-Host "🚀 Iniciando Sistema RMI Distribuido..." -ForegroundColor Green

# 1. Iniciar ControladorSeguridad
Write-Host "📡 Iniciando ControladorSeguridad en puerto 20000..."
$seguridad = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.server.ControladorSeguridadService", "localhost", "20000", "secreto123") -PassThru -WindowStyle Hidden
Start-Sleep 2

# 2. Iniciar BalanceadorCarga  
Write-Host "⚖️ Iniciando BalanceadorCarga en puerto 21000..."
$balanceador = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.server.BalanceadorCargaService", "localhost", "21000", "token-interno-123") -PassThru -WindowStyle Hidden
Start-Sleep 3

# 3. Iniciar Nodo con servicios Usuario y Archivo
Write-Host "🖥️ Iniciando Nodo con servicios en puerto 22001..."
$nodo = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.bootstrap.SimpleNodeBootstrap", "localhost", "22001", "token-interno-123") -PassThru -WindowStyle Hidden
Start-Sleep 3

# 4. Iniciar WebServer
Write-Host "🌐 Iniciando WebServer en puerto 8080..."
$web = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.web.WebServer") -PassThru -WindowStyle Hidden
Start-Sleep 2

Write-Host "✅ Sistema iniciado correctamente!" -ForegroundColor Green
Write-Host "📱 Abra http://localhost:8080 en su navegador" -ForegroundColor Yellow
Write-Host ""
Write-Host "Procesos iniciados:"
Write-Host "- ControladorSeguridad (PID: $($seguridad.Id))"
Write-Host "- BalanceadorCarga (PID: $($balanceador.Id))"  
Write-Host "- Nodo Usuario/Archivo (PID: $($nodo.Id))"
Write-Host "- WebServer (PID: $($web.Id))"
Write-Host ""
Write-Host "Para detener el sistema, ejecute: ./stop-system.ps1"