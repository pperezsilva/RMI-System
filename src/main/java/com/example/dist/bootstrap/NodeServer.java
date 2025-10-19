package com.example.dist.bootstrap;

import com.example.dist.remote.BalanceadorCargaRemote;

import java.rmi.Naming;
import java.util.*;

import static java.lang.Thread.sleep;

public class NodeServer {
    public static void main(String[] args) throws Exception {
        // args: host rmiPort lbUrl internalToken nodoId servicios(coma-separado)
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 22001;
        String lbUrl = args.length > 2 ? args[2] : "rmi://localhost:21000/lb";
        String internalToken = args.length > 3 ? args[3] : "changeme-internal";
        String nodoId = args.length > 4 ? args[4] : UUID.randomUUID().toString();
        String serviciosCsv = args.length > 5 ? args[5] : "usuario,archivo,auditor,nodo";
        List<String> servicios = Arrays.asList(serviciosCsv.split(","));

        // Export basic services on this node (demo: reusing export methods)
        com.example.dist.server.UsuarioService.export(host, port);
        com.example.dist.server.ArchivoService.export(host, port);
        com.example.dist.server.AuditorService.export(host, port);
        com.example.dist.server.NodoService.export(host, port);

        BalanceadorCargaRemote lb = (BalanceadorCargaRemote) Naming.lookup(lbUrl);
        lb.registrarNodo(internalToken, nodoId, host, port, servicios);
        System.out.printf("Nodo %s registrado en %s%n", nodoId, lbUrl);

        while (true) {
            try { lb.heartbeat(internalToken, nodoId); } catch (Exception ignored) {}
            sleep(5000);
        }
    }
}
