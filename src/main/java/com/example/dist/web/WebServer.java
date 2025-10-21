package com.example.dist.web;

import com.example.dist.common.Usuario;
import com.example.dist.common.TokenInfo;
import com.example.dist.common.ArchivoMetadata;
import com.example.dist.remote.ControladorSeguridadRemote;
import com.example.dist.remote.UsuarioRemote;
import com.example.dist.remote.ArchivoRemote;
import com.example.dist.remote.BalanceadorCargaRemote;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.rmi.Naming;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

public class WebServer {
    private static final int PORT = 8080;
    private static String currentToken = null;
    private static String currentUserId = null;
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
                case "/test":
                    return getTestHtml();
                case "/api/login":
                    return handleLogin(body);
                case "/api/users":
                    if ("GET".equals(method)) return getUsers();
                    if ("POST".equals(method)) return createUser(body);
                    break;
                case "/api/authenticate":
                    return authenticateUser(body);
                case "/api/files":
                    if ("GET".equals(method)) return getFiles();
                    if ("POST".equals(method)) return uploadFile(body);
                    break;
            }
            
            if (path.startsWith("/api/files/")) {
                String fileId = path.substring(11); // Remove "/api/files/"
                if ("DELETE".equals(method)) return deleteFile(fileId);
                if ("PUT".equals(method)) return renameFile(fileId, body);
                if ("GET".equals(method)) return downloadFile(fileId);
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
        String email = data.get("email");
        String password = data.get("password");
        
        if (email == null || email.trim().isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "email is required");
            return mapper.writeValueAsString(errorResponse);
        }
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "usuario");
        
        UsuarioRemote usuarioService = (UsuarioRemote) Naming.lookup(endpoint);
        Usuario usuario = usuarioService.crear(currentToken, username, email, password);
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
        String email = data.get("email");
        String password = data.get("password");
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "usuario");
        
        UsuarioRemote usuarioService = (UsuarioRemote) Naming.lookup(endpoint);
        boolean authenticated = usuarioService.autenticar(currentToken, email, password);
        
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", authenticated);
        
        if (authenticated) {
            // Buscar el usuario para obtener información adicional
            List<Usuario> usuarios = usuarioService.listar(currentToken);
            Usuario usuario = usuarios.stream()
                .filter(u -> email != null && email.equalsIgnoreCase(u.getEmail()))
                .findFirst()
                .orElse(null);
            
            if (usuario != null) {
                response.put("username", usuario.getUsername());
                response.put("email", usuario.getEmail());
                response.put("userId", usuario.getId());
                response.put("roles", usuario.getRoles());
                currentUserId = usuario.getId(); // Guardar ID para uso en archivos
            }
        }
        
        return mapper.writeValueAsString(response);
    }
    
    private static String getFiles() throws Exception {
        if (currentToken == null || currentUserId == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login and authenticate first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "archivo");
        
        ArchivoRemote archivoService = (ArchivoRemote) Naming.lookup(endpoint);
        List<ArchivoMetadata> archivos = archivoService.listar(currentToken, currentUserId);
        
        return mapper.writeValueAsString(archivos);
    }
    
    private static String uploadFile(String body) throws Exception {
        if (currentToken == null || currentUserId == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login and authenticate first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        Map<String, String> data = mapper.readValue(body, Map.class);
        String fileName = data.get("fileName");
        String fileContent = data.get("fileContent"); // Base64 encoded
        
        if (fileName == null || fileContent == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "fileName and fileContent are required");
            return mapper.writeValueAsString(errorResponse);
        }
        
        // Decode base64 content
        byte[] contenido = Base64.getDecoder().decode(fileContent);
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "archivo");
        
        ArchivoRemote archivoService = (ArchivoRemote) Naming.lookup(endpoint);
        ArchivoMetadata metadata = archivoService.subir(currentToken, currentUserId, fileName, contenido);
        
        return mapper.writeValueAsString(metadata);
    }
    
    private static String deleteFile(String fileId) throws Exception {
        if (currentToken == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "archivo");
        
        ArchivoRemote archivoService = (ArchivoRemote) Naming.lookup(endpoint);
        archivoService.eliminar(currentToken, fileId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Archivo eliminado correctamente");
        return mapper.writeValueAsString(response);
    }
    
    private static String renameFile(String fileId, String body) throws Exception {
        if (currentToken == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        Map<String, String> data = mapper.readValue(body, Map.class);
        String newName = data.get("newName");
        
        if (newName == null || newName.trim().isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "newName is required");
            return mapper.writeValueAsString(errorResponse);
        }
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "archivo");
        
        ArchivoRemote archivoService = (ArchivoRemote) Naming.lookup(endpoint);
        archivoService.renombrar(currentToken, fileId, newName.trim());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Archivo renombrado correctamente");
        return mapper.writeValueAsString(response);
    }
    
    private static String downloadFile(String fileId) throws Exception {
        if (currentToken == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No token available. Login first.");
            return mapper.writeValueAsString(errorResponse);
        }
        
        BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
            Naming.lookup("rmi://localhost:21000/lb");
        String endpoint = balanceador.obtenerEndpoint(currentToken, "archivo");
        
        ArchivoRemote archivoService = (ArchivoRemote) Naming.lookup(endpoint);
        byte[] contenido = archivoService.descargar(currentToken, fileId);
        
        if (contenido == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Archivo no encontrado");
            return mapper.writeValueAsString(errorResponse);
        }
        
        // Obtener metadata del archivo para el nombre
        List<ArchivoMetadata> archivos = archivoService.listar(currentToken, currentUserId);
        String fileName = "archivo_" + fileId.substring(0, 8); // fallback
        for (ArchivoMetadata archivo : archivos) {
            if (archivo.getId().equals(fileId)) {
                fileName = archivo.getNombre();
                break;
            }
        }
        
        // Return base64 encoded content with filename
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("content", Base64.getEncoder().encodeToString(contenido));
        response.put("fileName", fileName);
        return mapper.writeValueAsString(response);
    }
    
    private static String getContentType(String path) {
        if (path.endsWith(".html") || path.equals("/")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.startsWith("/api/")) return "application/json";
        return "text/plain";
    }
    
    private static String getTestHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Test RMI</title></head>" +
               "<body><h1>Test Sistema RMI</h1>" +
               "<button onclick='testConnection()'>Test Conexion</button>" +
               "<div id='result'></div><script>" +
               "async function testConnection() {" +
               "console.log('Boton clickeado');" +
               "document.getElementById('result').innerHTML = 'Probando...';" +
               "try { const response = await fetch('/api/login', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({}) });" +
               "const data = await response.json();" +
               "console.log('Respuesta:', data);" +
               "document.getElementById('result').innerHTML = 'Exito: ' + JSON.stringify(data);" +
               "} catch (error) {" +
               "console.error('Error:', error);" +
               "document.getElementById('result').innerHTML = 'Error: ' + error.message;" +
               "}}</script></body></html>";
    }
    
    private static String getIndexHtml() {
        return "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<title>Sistema RMI Distribuido</title><style>" +
               "body{font-family:'Segoe UI',sans-serif;max-width:1200px;margin:0 auto;padding:20px;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);min-height:100vh}" +
               ".container{background:white;border-radius:10px;padding:30px;box-shadow:0 10px 30px rgba(0,0,0,0.1)}" +
               "h1{color:#333;text-align:center;margin-bottom:30px}" +
               ".section{margin-bottom:30px;padding:20px;border:1px solid #e0e0e0;border-radius:8px;background:#fafafa}" +
               ".section h2{color:#555;border-bottom:2px solid #667eea;padding-bottom:10px}" +
               ".form-group{margin-bottom:15px}" +
               "label{display:block;margin-bottom:5px;font-weight:bold;color:#555}" +
               "input[type='text'],input[type='password'],input[type='file']{width:100%;padding:12px;border:2px solid #ddd;border-radius:5px;font-size:16px;transition:border-color 0.3s}" +
               "input[type='text']:focus,input[type='password']:focus{border-color:#667eea;outline:none}" +
               ".toolbar{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin:10px 0}" +
               ".btn{background:linear-gradient(45deg,#667eea,#764ba2);color:white;border:none;padding:12px 20px;border-radius:6px;cursor:pointer;font-size:15px;transition:transform .2s,opacity .2s}" +
               ".btn:hover{transform:translateY(-2px)}" +
               ".btn-secondary{background:linear-gradient(45deg,#6c757d,#6c757d)}" +
               ".btn-success{background:linear-gradient(45deg,#2e7d32,#43a047)}" +
               ".btn-danger{background:linear-gradient(45deg,#c62828,#e53935)}" +
               ".btn-outline{background:#fff;color:#555;border:1px solid #ccc} .btn-outline:hover{background:#f5f5f5}" +
               ".btn-sm{padding:6px 10px;font-size:13px;border-radius:4px}" +
               ".btn-group{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}" +
               ".result{margin-top:15px;padding:15px;border-radius:5px;font-family:monospace}" +
               ".success{background:#d4edda;color:#155724;border:1px solid #c3e6cb}" +
               ".error{background:#f8d7da;color:#721c24;border:1px solid #f5c6cb}" +
               ".user-info{background:#e8f5e8;border:2px solid #4caf50;border-radius:8px;padding:15px;margin-top:10px}" +
               ".user-list{background:white;border:1px solid #ddd;border-radius:5px;padding:15px;max-height:300px;overflow-y:auto}" +
               ".user-item{padding:10px;border-bottom:1px solid #eee;display:flex;justify-content:space-between;align-items:center;gap:10px}" +
               ".user-item:last-child{border-bottom:none}" +
               ".status{padding:10px;margin:10px 0;border-radius:5px;font-weight:bold}" +
               ".status.connected{background:#d4edda;color:#155724}" +
               ".status.disconnected{background:#f8d7da;color:#721c24}" +
               "</style></head><body>" +
               "<div class='container'>" +
"<h1>Gestor de Archivos - Panel Web</h1>" +
               "<div id='status' class='status disconnected'>Estado: Desconectado - Haga clic en 'Conectar' para iniciar</div>" +
               "<div id='userStatus' class='user-info' style='display:none'><h3 style='margin:0 0 10px 0;color:#2e7d32'>Usuario Autenticado</h3><div id='userInfo'></div></div>" +
               "<div class='section'><h2>Conexion al Sistema</h2><div class='toolbar'><button class='btn' onclick='connectToSystem()'>Conectar al Sistema RMI</button></div><div id='connectionResult' class='result'></div></div>" +
               "<div class='section'><h2>Gestor de Archivos</h2>" +
               "<div id='fileManagerSection' style='display:none'>" +
               "<div style='display:flex;gap:20px;flex-wrap:wrap;margin-bottom:20px'>" +
               "<div style='flex:1;min-width:300px'><h3>Subir Archivo</h3>" +
               "<div class='form-group'><label>Seleccionar Archivo:</label><input type='file' id='fileInput' accept='*/*'></div>" +
               "<div class='toolbar'><button class='btn' onclick='uploadFile()'>Subir Archivo</button></div><div id='uploadResult' class='result'></div></div>" +
               "</div><div style='margin-top:20px'><div class='toolbar'><button class='btn btn-secondary' onclick='loadFiles()'>Actualizar Lista de Archivos</button></div>" +
               "<div id='filesList' class='user-list' style='margin-top:15px'></div></div>" +
               "</div>" +
               "<div id='fileManagerDisabled' class='result error' style='margin-top:10px'>Para usar el gestor de archivos, debe conectarse al sistema y autenticar un usuario primero.</div>" +
               "</div>" +
               "<div class='section'><h2>Gestion de Usuarios</h2>" +
               "<div style='display:flex;gap:60px;flex-wrap:wrap'>" +
               "<div style='flex:1;min-width:300px'><h3>Crear Usuario</h3>" +
               "<div class='form-group'><label>Nombre de Usuario:</label><input type='text' id='newUsername' placeholder='Ingrese nombre de usuario'></div>" +
               "<div class='form-group'><label>Correo:</label><input type='text' id='newEmail' placeholder='correo@ejemplo.com'></div>" +
               "<div class='form-group'><label>Contraseña:</label><input type='password' id='newPassword' placeholder='Ingrese contraseña'></div>" +
               "<div class='toolbar'><button class='btn' onclick='createUser()'>Crear Usuario</button></div><div id='createResult' class='result'></div></div>" +
               "<div style='flex:1;min-width:300px;margin-left:20px'><h3>Autenticar Usuario</h3>" +
               "<div class='form-group'><label>Correo:</label><input type='text' id='authEmail' placeholder='correo@ejemplo.com'></div>" +
               "<div class='form-group'><label>Contraseña:</label><input type='password' id='authPassword' placeholder='Contraseña'></div>" +
               "<div class='toolbar'><button class='btn' onclick='authenticateUser()'>Autenticar</button></div><div id='authResult' class='result'></div></div>" +
               "</div><div style='margin-top:20px'><div class='toolbar'><button class='btn btn-secondary' onclick='loadUsers()'>Actualizar Lista de Usuarios</button></div><div id='usersList' class='user-list' style='margin-top:15px'></div></div></div>" +
               "</div><script>" +
               "let connected=false;" +
               "async function connectToSystem(){try{const response=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({})});const data=await response.json();if(data.success){connected=true;document.getElementById('status').className='status connected';document.getElementById('status').textContent='Estado: Conectado al sistema RMI';document.getElementById('connectionResult').innerHTML='<div class=\"success\">Conexion exitosa al sistema RMI</div>';await loadUsers()}else{throw new Error(data.error||'Error de conexion')}}catch(error){document.getElementById('connectionResult').innerHTML='<div class=\"error\">Error: '+error.message+'</div>'}}" +
               "async function createUser(){if(!connected){alert('Debe conectarse al sistema primero');return}const username=document.getElementById('newUsername').value;const email=document.getElementById('newEmail').value;const password=document.getElementById('newPassword').value;if(!username||!email||!password){alert('Complete todos los campos');return}try{const response=await fetch('/api/users',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,email,password})});const data=await response.json();if(data.error){throw new Error(data.error)}document.getElementById('createResult').innerHTML='<div class=\"success\">Usuario creado: '+data.username+' ('+data.email+') (ID: '+data.id+')</div>';document.getElementById('newUsername').value='';document.getElementById('newEmail').value='';document.getElementById('newPassword').value='';await loadUsers()}catch(error){document.getElementById('createResult').innerHTML='<div class=\"error\">Error: '+error.message+'</div>'}}" +
               "async function authenticateUser(){if(!connected){alert('Debe conectarse al sistema primero');return}const email=document.getElementById('authEmail').value;const password=document.getElementById('authPassword').value;if(!email||!password){alert('Complete todos los campos');return}try{const response=await fetch('/api/authenticate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});const data=await response.json();if(data.error){throw new Error(data.error)}const resultClass=data.authenticated?'success':'error';const resultIcon=data.authenticated?'✅':'❌';let resultText=data.authenticated?'Autenticacion exitosa':'Credenciales incorrectas';if(data.authenticated&&(data.username||data.email)){resultText+='<br><strong>Usuario:</strong> '+(data.username||'');if(data.email){resultText+='<br><strong>Correo:</strong> '+data.email}if(data.roles&&data.roles.length>0){resultText+='<br><strong>Roles:</strong> '+data.roles.join(', ')}resultText+='<br><small>ID: '+data.userId+'</small>';const userStatus=document.getElementById('userStatus');const userInfo=document.getElementById('userInfo');userInfo.innerHTML='<strong>Nombre:</strong> '+(data.username||'')+'<br><strong>Correo:</strong> '+(data.email||'')+'<br><strong>Roles:</strong> '+(data.roles?data.roles.join(', '):'Ninguno')+'<br><small>ID: '+data.userId+'</small>';userStatus.style.display='block';document.getElementById('fileManagerSection').style.display='block';document.getElementById('fileManagerDisabled').style.display='none';await loadFiles()}document.getElementById('authResult').innerHTML='<div class=\"'+resultClass+'\">'+resultIcon+' '+resultText+'</div>'}catch(error){document.getElementById('authResult').innerHTML='<div class=\"error\">Error: '+error.message+'</div>'}}" +
               "async function loadUsers(){if(!connected)return;try{const response=await fetch('/api/users');const users=await response.json();if(users.error){throw new Error(users.error)}const usersList=document.getElementById('usersList');if(users.length===0){usersList.innerHTML='<div style=\"text-align:center;color:#666\">No hay usuarios registrados</div>';return}usersList.innerHTML=users.map(user=>'<div class=\"user-item\"><div><strong>'+user.username+'</strong><br><small>ID: '+user.id+'</small></div><div><span style=\"background:#e3f2fd;padding:4px 8px;border-radius:3px;font-size:12px\">'+user.roles.join(', ')+'</span></div></div>').join('')}catch(error){document.getElementById('usersList').innerHTML='<div class=\"error\">Error cargando usuarios: '+error.message+'</div>'}}" +
               "async function uploadFile(){const fileInput=document.getElementById('fileInput');const file=fileInput.files[0];if(!file){alert('Seleccione un archivo primero');return}try{const fileContent=await fileToBase64(file);const response=await fetch('/api/files',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({fileName:file.name,fileContent:fileContent})});const data=await response.json();if(data.error){throw new Error(data.error)}document.getElementById('uploadResult').innerHTML='<div class=\"success\">Archivo subido: '+data.nombre+' ('+formatFileSize(data.tamano)+')</div>';fileInput.value='';await loadFiles()}catch(error){document.getElementById('uploadResult').innerHTML='<div class=\"error\">Error: '+error.message+'</div>'}}" +
               "function fileToBase64(file){return new Promise((resolve,reject)=>{const reader=new FileReader();reader.readAsDataURL(file);reader.onload=()=>{const result=reader.result.split(',')[1];resolve(result)};reader.onerror=error=>reject(error)})}" +
               "async function loadFiles(){try{const response=await fetch('/api/files');const files=await response.json();if(files.error){throw new Error(files.error)}const filesList=document.getElementById('filesList');if(files.length===0){filesList.innerHTML='<div style=\"text-align:center;color:#666;padding:20px\">No hay archivos subidos</div>';return}filesList.innerHTML=files.map(file=>'<div class=\"user-item\"><div><strong>'+file.nombre+'</strong><br><small>Tamaño: '+formatFileSize(file.tamano)+' | ID: '+file.id.substring(0,8)+'...</small></div><div class=\"btn-group\"><button class=\"btn btn-sm btn-success\" onclick=\"downloadFile(\\\''+file.id+'\\\')\">Descargar</button><button class=\"btn btn-sm btn-secondary\" onclick=\"renameFile(\\\''+file.id+'\\\')\">Renombrar</button><button class=\"btn btn-sm btn-danger\" onclick=\"deleteFile(\\\''+file.id+'\\\')\">Eliminar</button></div></div>').join('')}catch(error){document.getElementById('filesList').innerHTML='<div class=\"error\">Error cargando archivos: '+error.message+'</div>'}}" +
               "async function deleteFile(fileId){if(!confirm('¿Esta seguro que desea eliminar este archivo?'))return;try{const response=await fetch('/api/files/'+fileId,{method:'DELETE'});const data=await response.json();if(data.error){throw new Error(data.error)}await loadFiles();alert('Archivo eliminado correctamente')}catch(error){alert('Error eliminando archivo: '+error.message)}}" +
               "async function renameFile(fileId){const newName=prompt('Ingrese el nuevo nombre para el archivo:');if(!newName||newName.trim()==='')return;try{const response=await fetch('/api/files/'+fileId,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({newName:newName.trim()})});const data=await response.json();if(data.error){throw new Error(data.error)}await loadFiles();alert('Archivo renombrado correctamente')}catch(error){alert('Error renombrando archivo: '+error.message)}}" +
               "async function downloadFile(fileId){try{const response=await fetch('/api/files/'+fileId);const data=await response.json();if(data.error){throw new Error(data.error)}const byteCharacters=atob(data.content);const byteNumbers=new Array(byteCharacters.length);for(let i=0;i<byteCharacters.length;i++){byteNumbers[i]=byteCharacters.charCodeAt(i)}const byteArray=new Uint8Array(byteNumbers);const blob=new Blob([byteArray]);const url=window.URL.createObjectURL(blob);const a=document.createElement('a');a.style.display='none';a.href=url;a.download=data.fileName||'archivo';document.body.appendChild(a);a.click();window.URL.revokeObjectURL(url);document.body.removeChild(a)}catch(error){alert('Error descargando archivo: '+error.message)}}" +
               "function formatFileSize(bytes){if(bytes===0)return '0 Bytes';const k=1024;const sizes=['Bytes','KB','MB','GB'];const i=Math.floor(Math.log(bytes)/Math.log(k));return parseFloat((bytes/Math.pow(k,i)).toFixed(2))+' '+sizes[i]}" +
               "</script></body></html>";
    }
}
