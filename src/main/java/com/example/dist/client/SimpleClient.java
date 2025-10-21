package com.example.dist.client;

import com.example.dist.common.Usuario;
import com.example.dist.remote.ControladorSeguridadRemote;
import com.example.dist.remote.UsuarioRemote;
import com.example.dist.remote.BalanceadorCargaRemote;

import java.rmi.Naming;
import java.util.List;

public class SimpleClient {
    public static void main(String[] args) {
        try {
            System.out.println("=== Cliente RMI Distribuido ===");
            
            // 1. Conectar al controlador de seguridad
            System.out.println("1. Conectando al ControladorSeguridad...");
            ControladorSeguridadRemote seguridad = (ControladorSeguridadRemote) 
                Naming.lookup("rmi://localhost:20000/seguridad");
            
            // 2. Obtener un token de autenticación
            System.out.println("2. Obteniendo token de autenticación...");
            com.example.dist.common.TokenInfo tokenInfo = seguridad.emitirToken("client-1", "admin", "admin123");
            String token = tokenInfo.getToken();
            System.out.println("Token obtenido: " + token.substring(0, Math.min(50, token.length())) + "...");
            
            // 3. Conectar al balanceador de carga
            System.out.println("3. Conectando al BalanceadorCarga...");
            BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
                Naming.lookup("rmi://localhost:21000/lb");
            
            // 4. Obtener endpoint del servicio de usuario
            System.out.println("4. Obteniendo endpoint del servicio usuario...");
            String endpointUsuario = balanceador.obtenerEndpoint(token, "usuario");
            System.out.println("Endpoint usuario: " + endpointUsuario);
            
            // 5. Conectar al servicio de usuario
            System.out.println("5. Conectando al servicio de usuario...");
            UsuarioRemote usuarioService = (UsuarioRemote) Naming.lookup(endpointUsuario);
            
            // 6. Crear un usuario
            System.out.println("6. Creando usuario de prueba...");
            Usuario nuevoUsuario = usuarioService.crear(token, "testuser", "testuser@example.com", "password123");
            System.out.println("Usuario creado: " + nuevoUsuario.getUsername() + " (Email: " + nuevoUsuario.getEmail() + ", ID: " + nuevoUsuario.getId() + ")");
            
            // 7. Asignar rol al usuario
            System.out.println("7. Asignando rol USER al usuario...");
            usuarioService.asignarRol(token, nuevoUsuario.getId(), "USER");
            
            // 8. Listar usuarios
            System.out.println("8. Listando todos los usuarios...");
            List<Usuario> usuarios = usuarioService.listar(token);
            System.out.println("Total usuarios: " + usuarios.size());
            for (Usuario u : usuarios) {
                System.out.println("  - " + u.getUsername() + " (roles: " + u.getRoles() + ")");
            }
            
            // 9. Autenticar usuario
            System.out.println("9. Probando autenticación...");
            boolean autenticado = usuarioService.autenticar(token, "testuser@example.com", "password123");
            System.out.println("Autenticación exitosa: " + autenticado);
            
            // 10. Probar autenticación incorrecta
            System.out.println("10. Probando autenticación incorrecta...");
            boolean autenticacionIncorrecta = usuarioService.autenticar(token, "testuser@example.com", "wrongpassword");
            System.out.println("Autenticación fallida (esperado): " + autenticacionIncorrecta);
            
            System.out.println("\n=== Prueba completada exitosamente ===");
            
        } catch (Exception e) {
            System.err.println("Error durante la prueba: " + e.getMessage());
            e.printStackTrace();
        }
    }
}