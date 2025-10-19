package com.example.dist.web;

import com.example.dist.common.Usuario;
import com.example.dist.common.TokenInfo;
import com.example.dist.remote.ControladorSeguridadRemote;
import com.example.dist.remote.UsuarioRemote;
import com.example.dist.remote.BalanceadorCargaRemote;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.rmi.Naming;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WebServer {
    private static final int PORT = 8080;
    private static String currentToken = null;
    private static final ObjectMapper mapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        System.out.println("Iniciando servidor web en http://localhost:" + PORT);
        
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor web listo. Abra http://localhost:" + PORT + " en su navegador");
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> handleRequest(clientSocket)).start();
        }
    }
    
    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"))) {
            
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];
            
            // Leer headers
            String line;
            int contentLength = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // Leer body si existe
            String body = "";
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                in.read(buffer, 0, contentLength);
                body = new String(buffer);
            }
            
            String response = handlePath(method, path, body);
            
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: " + getContentType(path) + "; charset=UTF-8");
            out.println("Content-Length: " + response.getBytes("UTF-8").length);
            out.println("Access-Control-Allow-Origin: *");
            out.println("Access-Control-Allow-Methods: GET, POST, OPTIONS");
            out.println("Access-Control-Allow-Headers: Content-Type");
            out.println();
            out.print(response);
            out.flush();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static String handlePath(String method, String path, String body) {
        try {
            if ("OPTIONS".equals(method)) {
                return "";
            }
            
            switch (path) {
                case "/":
                    return getIndexHtml();
                case "/api/login":
                    return handleLogin(body);
                case "/api/users":
                    if ("GET".equals(method)) return getUsers();
                    if ("POST".equals(method)) return createUser(body);
                    break;
                case "/api/authenticate":
                    return authenticateUser(body);
            }
            
            return "404 Not Found";
        } catch (Exception e) {
            try {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "Error desconocido");
                return mapper.writeValueAsString(errorResponse);
            } catch (Exception jsonEx) {
                return "{\"error\": \"Error procesando respuesta\"}"; 
            }
        }
    }
    
    private static String handleLogin(String body) throws Exception {
        ControladorSeguridadRemote seguridad = (ControladorSeguridadRemote) 
            Naming.lookup("rmi://localhost:20000/seguridad");
        
        TokenInfo tokenInfo = seguridad.emitirToken("web-client", "admin", "admin123");
        currentToken = tokenInfo.getToken();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", currentToken.substring(0, 20) + "...");
        return mapper.writeValueAsString(response);
    }
    
    private static String getUsers() throws Exception {
        if (currentToken == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "usuario");
        
        UsuarioRemote usuarioService = (UsuarioRemote) Naming.lookup(endpoint);
        List<Usuario> usuarios = usuarioService.listar(currentToken);
        
        return mapper.writeValueAsString(usuarios);
    }
    
    private static String createUser(String body) throws Exception {
        if (currentToken == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        Map<String, String> data = mapper.readValue(body, Map.class);
        String username = data.get("username");
        String password = data.get("password");
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "usuario");
        
        UsuarioRemote usuarioService = (UsuarioRemote) Naming.lookup(endpoint);
        Usuario usuario = usuarioService.crear(currentToken, username, password);
        usuarioService.asignarRol(currentToken, usuario.getId(), "USER");
        
        return mapper.writeValueAsString(usuario);
    }
    
    private static String authenticateUser(String body) throws Exception {
        if (currentToken == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        Map<String, String> data = mapper.readValue(body, Map.class);
        String username = data.get("username");
        String password = data.get("password");
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "usuario");
        
        UsuarioRemote usuarioService = (UsuarioRemote) Naming.lookup(endpoint);
        boolean authenticated = usuarioService.autenticar(currentToken, username, password);
        
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", authenticated);
        
        if (authenticated) {
            // Buscar el usuario para obtener información adicional
            List<Usuario> usuarios = usuarioService.listar(currentToken);
            System.out.println("[DEBUG] Total usuarios encontrados: " + usuarios.size());
            
            for (Usuario u : usuarios) {
                System.out.println("[DEBUG] Usuario: " + u.getUsername() + " - Buscando: " + username);
            }
            
            Usuario usuario = usuarios.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
            
            System.out.println("[DEBUG] Usuario encontrado: " + (usuario != null ? usuario.getUsername() : "null"));
            
            if (usuario != null) {
                response.put("username", usuario.getUsername());
                response.put("userId", usuario.getId());
                response.put("roles", usuario.getRoles());
                System.out.println("[DEBUG] Información del usuario agregada a la respuesta");
            } else {
                System.out.println("[DEBUG] No se encontró el usuario " + username + " en la lista");
            }
        }
        
        return mapper.writeValueAsString(response);
    }
    
    private static String getContentType(String path) {
        if (path.endsWith(".html") || path.equals("/")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.startsWith("/api/")) return "application/json";
        return "text/plain";
    }
    
    private static String getIndexHtml() {
        return """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sistema RMI Distribuido</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
        }
        
        .container {
            background: white;
            border-radius: 10px;
            padding: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
        }
        
        h1 {
            color: #333;
            text-align: center;
            margin-bottom: 30px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.1);
        }
        
        .section {
            margin-bottom: 30px;
            padding: 20px;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            background: #fafafa;
        }
        
        .section h2 {
            color: #555;
            border-bottom: 2px solid #667eea;
            padding-bottom: 10px;
        }
        
        .form-group {
            margin-bottom: 15px;
        }
        
        label {
            display: block;
            margin-bottom: 5px;
            font-weight: bold;
            color: #555;
        }
        
        input[type="text"], input[type="password"] {
            width: 100%;
            padding: 12px;
            border: 2px solid #ddd;
            border-radius: 5px;
            font-size: 16px;
            transition: border-color 0.3s;
        }
        
        input[type="text"]:focus, input[type="password"]:focus {
            border-color: #667eea;
            outline: none;
        }
        
        button {
            background: linear-gradient(45deg, #667eea, #764ba2);
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 5px;
            cursor: pointer;
            font-size: 16px;
            margin-right: 10px;
            margin-bottom: 10px;
            transition: transform 0.2s;
        }
        
        button:hover {
            transform: translateY(-2px);
        }
        
        button:disabled {
            background: #ccc;
            cursor: not-allowed;
            transform: none;
        }
        
        .result {
            margin-top: 15px;
            padding: 15px;
            border-radius: 5px;
            white-space: pre-wrap;
            font-family: monospace;
        }
        
        .user-info {
            background: #e8f5e8;
            border: 2px solid #4caf50;
            border-radius: 8px;
            padding: 15px;
            margin-top: 10px;
            font-family: 'Segoe UI', sans-serif;
        }
        
        .user-info strong {
            color: #2e7d32;
        }
        
        .user-info small {
            color: #666;
            font-style: italic;
        }
        
        .success {
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        
        .error {
            background: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
        
        .user-list {
            background: white;
            border: 1px solid #ddd;
            border-radius: 5px;
            padding: 15px;
            max-height: 300px;
            overflow-y: auto;
        }
        
        .user-item {
            padding: 10px;
            border-bottom: 1px solid #eee;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .user-item:last-child {
            border-bottom: none;
        }
        
        .status {
            padding: 10px;
            margin: 10px 0;
            border-radius: 5px;
            font-weight: bold;
        }
        
        .status.connected {
            background: #d4edda;
            color: #155724;
        }
        
        .status.disconnected {
            background: #f8d7da;
            color: #721c24;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Sistema RMI Distribuido - Panel Web</h1>
        
        <div id="status" class="status disconnected">
            Estado: Desconectado - Haga clic en "Conectar" para iniciar
        </div>
        
        <div id="userStatus" class="user-info" style="display: none;">
            <h3 style="margin: 0 0 10px 0; color: #2e7d32;">👤 Usuario Autenticado</h3>
            <div id="userInfo"></div>
        </div>
        
        <div class="section">
            <h2>🔑 Conexión al Sistema</h2>
            <button onclick="connectToSystem()">Conectar al Sistema RMI</button>
            <div id="connectionResult" class="result"></div>
        </div>
        
        <div class="section">
            <h2>👥 Gestión de Usuarios</h2>
            
            <div style="display: flex; gap: 20px; flex-wrap: wrap;">
                <div style="flex: 1; min-width: 300px;">
                    <h3>Crear Usuario</h3>
                    <div class="form-group">
                        <label>Nombre de Usuario:</label>
                        <input type="text" id="newUsername" placeholder="Ingrese nombre de usuario">
                    </div>
                    <div class="form-group">
                        <label>Contraseña:</label>
                        <input type="password" id="newPassword" placeholder="Ingrese contraseña">
                    </div>
                    <button onclick="createUser()">Crear Usuario</button>
                    <div id="createResult" class="result"></div>
                </div>
                
                <div style="flex: 1; min-width: 300px;">
                    <h3>Autenticar Usuario</h3>
                    <div class="form-group">
                        <label>Nombre de Usuario:</label>
                        <input type="text" id="authUsername" placeholder="Nombre de usuario">
                    </div>
                    <div class="form-group">
                        <label>Contraseña:</label>
                        <input type="password" id="authPassword" placeholder="Contraseña">
                    </div>
                    <button onclick="authenticateUser()">Autenticar</button>
                    <div id="authResult" class="result"></div>
                </div>
            </div>
            
            <div style="margin-top: 20px;">
                <button onclick="loadUsers()">🔄 Actualizar Lista de Usuarios</button>
                <div id="usersList" class="user-list" style="margin-top: 15px;"></div>
            </div>
        </div>
    </div>

    <script>
        let connected = false;
        
        async function connectToSystem() {
            try {
                const response = await fetch('/api/login', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({})
                });
                
                const data = await response.json();
                
                if (data.success) {
                    connected = true;
                    document.getElementById('status').className = 'status connected';
                    document.getElementById('status').textContent = 'Estado: Conectado ✅ - Token: ' + data.token;
                    document.getElementById('connectionResult').innerHTML = '<div class="success">✅ Conexión exitosa al sistema RMI</div>';
                    await loadUsers();
                } else {
                    throw new Error(data.error || 'Error de conexión');
                }
            } catch (error) {
                document.getElementById('connectionResult').innerHTML = '<div class="error">❌ Error: ' + error.message + '</div>';
            }
        }
        
        async function createUser() {
            if (!connected) {
                alert('Debe conectarse al sistema primero');
                return;
            }
            
            const username = document.getElementById('newUsername').value;
            const password = document.getElementById('newPassword').value;
            
            if (!username || !password) {
                alert('Complete todos los campos');
                return;
            }
            
            try {
                const response = await fetch('/api/users', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({username, password})
                });
                
                const data = await response.json();
                
                if (data.error) {
                    throw new Error(data.error);
                }
                
                document.getElementById('createResult').innerHTML = 
                    '<div class="success">✅ Usuario creado: ' + data.username + ' (ID: ' + data.id + ')</div>';
                
                document.getElementById('newUsername').value = '';
                document.getElementById('newPassword').value = '';
                
                await loadUsers();
            } catch (error) {
                document.getElementById('createResult').innerHTML = '<div class="error">❌ Error: ' + error.message + '</div>';
            }
        }
        
        async function authenticateUser() {
            if (!connected) {
                alert('Debe conectarse al sistema primero');
                return;
            }
            
            const username = document.getElementById('authUsername').value;
            const password = document.getElementById('authPassword').value;
            
            if (!username || !password) {
                alert('Complete todos los campos');
                return;
            }
            
            try {
                const response = await fetch('/api/authenticate', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({username, password})
                });
                
                const data = await response.json();
                
                if (data.error) {
                    throw new Error(data.error);
                }
                
                const resultClass = data.authenticated ? 'success' : 'error';
                const resultIcon = data.authenticated ? '✅' : '❌';
                let resultText = data.authenticated ? 'Autenticación exitosa' : 'Credenciales incorrectas';
                
                if (data.authenticated && data.username) {
                    resultText += '<br><strong>Usuario:</strong> ' + data.username;
                    if (data.roles && data.roles.length > 0) {
                        resultText += '<br><strong>Roles:</strong> ' + data.roles.join(', ');
                    }
                    resultText += '<br><small>ID: ' + data.userId + '</small>';
                    
                    // Mostrar información del usuario en la sección superior
                    const userStatus = document.getElementById('userStatus');
                    const userInfo = document.getElementById('userInfo');
                    userInfo.innerHTML = 
                        '<strong>Nombre:</strong> ' + data.username + '<br>' +
                        '<strong>Roles:</strong> ' + (data.roles ? data.roles.join(', ') : 'Ninguno') + '<br>' +
                        '<small>ID: ' + data.userId + '</small>';
                    userStatus.style.display = 'block';
                }
                
                document.getElementById('authResult').innerHTML = 
                    '<div class="' + resultClass + '">' + resultIcon + ' ' + resultText + '</div>';
                
            } catch (error) {
                document.getElementById('authResult').innerHTML = '<div class="error">❌ Error: ' + error.message + '</div>';
            }
        }
        
        async function loadUsers() {
            if (!connected) return;
            
            try {
                const response = await fetch('/api/users');
                const users = await response.json();
                
                if (users.error) {
                    throw new Error(users.error);
                }
                
                const usersList = document.getElementById('usersList');
                
                if (users.length === 0) {
                    usersList.innerHTML = '<div style="text-align: center; color: #666;">No hay usuarios registrados</div>';
                    return;
                }
                
                usersList.innerHTML = users.map(user => 
                    '<div class="user-item">' +
                    '<div><strong>' + user.username + '</strong><br><small>ID: ' + user.id + '</small></div>' +
                    '<div><span style="background: #e3f2fd; padding: 4px 8px; border-radius: 3px; font-size: 12px;">' +
                    user.roles.join(', ') + '</span></div>' +
                    '</div>'
                ).join('');
                
            } catch (error) {
                document.getElementById('usersList').innerHTML = '<div class="error">❌ Error cargando usuarios: ' + error.message + '</div>';
            }
        }
    </script>
</body>
</html>
""";
    }
}