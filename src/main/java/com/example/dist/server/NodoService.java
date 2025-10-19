package com.example.dist.server;

import com.example.dist.remote.NodoRemote;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;

public class NodoService extends UnicastRemoteObject implements NodoRemote {
    private final String nodoId;
    private final String host;
    private final int rmiPort;
    private final List<String> servicios;
    private volatile long lastHb = System.currentTimeMillis();

    public NodoService(String nodoId, String host, int rmiPort, List<String> servicios) throws RemoteException {
        this.nodoId = nodoId; this.host = host; this.rmiPort = rmiPort; this.servicios = servicios;
    }

    @Override public String getNodoId() { return nodoId; }
    @Override public String getHost() { return host; }
    @Override public int getRmiPort() { return rmiPort; }
    @Override public List<String> getServiciosExportados() { return servicios; }
    @Override public boolean healthCheck() { lastHb = System.currentTimeMillis(); return true; }
    @Override public long lastHeartbeat() { return lastHb; }

    public static void export(String host, int port) throws Exception {
        try { LocateRegistry.createRegistry(port); } catch (Exception ignored) {}
        NodoService svc = new NodoService("nodo-demo", host, port, Arrays.asList("usuario", "archivo", "auditor", "nodo"));
        String url = String.format("rmi://%s:%d/nodo", host, port);
        Naming.rebind(url, svc);
        System.out.println("NodoService en " + url);
    }
}
