# Script de prueba para verificar botones web

Write-Host "🧪 Iniciando prueba de botones web..." -ForegroundColor Yellow

# 1. Iniciar servicios mínimos
Write-Host "1. Iniciando ControladorSeguridad..."
$seguridad = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.server.ControladorSeguridadService", "localhost", "20000", "secreto123") -PassThru -WindowStyle Hidden
Start-Sleep 2

Write-Host "2. Iniciando BalanceadorCarga..."
$balanceador = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.server.BalanceadorCargaService", "localhost", "21000", "token-interno-123") -PassThru -WindowStyle Hidden
Start-Sleep 2

Write-Host "3. Iniciando Nodo..."
$nodo = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.bootstrap.SimpleNodeBootstrap", "localhost", "22001", "token-interno-123") -PassThru -WindowStyle Hidden
Start-Sleep 2

Write-Host "4. Iniciando WebServer..."
$web = Start-Process -FilePath "java" -ArgumentList @("-cp", "target\distributed-rmi-1.0-SNAPSHOT-shaded.jar", "com.example.dist.web.WebServer") -PassThru -WindowStyle Hidden
Start-Sleep 3

# 2. Pruebas HTTP
Write-Host "`n🔍 Ejecutando pruebas..."

# Prueba 1: Página principal
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080" -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Página principal carga correctamente" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ Error cargando página principal: $_" -ForegroundColor Red
}

# Prueba 2: Página de test
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/test" -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Página de test carga correctamente" -ForegroundColor Green
        
        # Verificar que contiene el botón
        if ($response.Content -like "*testConnection*") {
            Write-Host "✅ Función JavaScript encontrada en la página" -ForegroundColor Green
        } else {
            Write-Host "⚠️ Función JavaScript NO encontrada" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "❌ Error cargando página de test: $_" -ForegroundColor Red
}

# Prueba 3: API de login (simula click del botón)
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/login" -Method POST -ContentType "application/json" -Body "{}" -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        $data = $response.Content | ConvertFrom-Json
        if ($data.success) {
            Write-Host "✅ API de login funciona correctamente" -ForegroundColor Green
            Write-Host "   Token recibido: $($data.token.Substring(0, 20))..." -ForegroundColor Cyan
        } else {
            Write-Host "❌ API de login falló: $($data.error)" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "❌ Error en API de login: $_" -ForegroundColor Red
}

# Prueba 4: API de usuarios
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -Method GET -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ API de usuarios responde correctamente" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ Error en API de usuarios: $_" -ForegroundColor Red
}

Write-Host "`n📱 Abre http://localhost:8080/test en tu navegador para probar manualmente" -ForegroundColor Yellow
Write-Host "🖱️ Si el botón no funciona, revisa la consola del navegador (F12)" -ForegroundColor Yellow
Write-Host "`n⏹️ Para detener los servicios, presiona Ctrl+C y ejecuta:"
Write-Host "   Get-Process java | Stop-Process -Force" -ForegroundColor Gray