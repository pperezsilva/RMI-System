package com.example.dist.bootstrap;

import com.example.dist.server.UsuarioService;
import com.example.dist.server.ArchivoService;
import com.example.dist.remote.BalanceadorCargaRemote;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.List;

public class SimpleNodeBootstrap {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 22001;
        String internalToken = args.length > 2 ? args[2] : "token-interno-123";
        
        System.out.println("Iniciando nodo en " + host + ":" + port);
        
        // Crear registry
        try {
            LocateRegistry.createRegistry(port);
        } catch (Exception ignored) {}
        
        // Exportar servicios
        UsuarioService.export(host, port);
        ArchivoService.export(host, port);
        
        // Registrar en el balanceador
        try {
            Thread.sleep(2000); // Esperar que se inicien los servicios
            BalanceadorCargaRemote balanceador = (BalanceadorCargaRemote) 
                Naming.lookup("rmi://localhost:21000/lb");
            
            String nodeId = "node-" + port;
            List<String> services = Arrays.asList("usuario", "archivo");
            
            balanceador.registrarNodo(internalToken, nodeId, host, port, services);
            System.out.println("Nodo registrado en balanceador: " + nodeId);
            
            // Enviar heartbeats
            Thread heartbeatThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        balanceador.heartbeat(internalToken, nodeId);
                        System.out.println("Heartbeat enviado para " + nodeId);
                    } catch (Exception e) {
                        System.err.println("Error enviando heartbeat: " + e.getMessage());
                    }
                }
            });
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            
        } catch (Exception e) {
            System.err.println("Error registrando nodo: " + e.getMessage());
        }
        
        System.out.println("Nodo iniciado correctamente en puerto " + port);
        System.out.println("Servicios disponibles: usuario, archivo");
        
        // Mantener el proceso vivo
        Object lock = new Object();
        synchronized (lock) {
            lock.wait();
        }
    }
}