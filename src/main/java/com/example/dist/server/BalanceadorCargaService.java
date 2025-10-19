package com.example.dist.server;

import com.example.dist.remote.BalanceadorCargaRemote;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceadorCargaService extends UnicastRemoteObject implements BalanceadorCargaRemote {
    private static class NodoInfo {
        String nodoId; String host; int port; Set<String> servicios = new HashSet<>(); long lastHb;
    }
    private final Map<String, NodoInfo> nodos = new ConcurrentHashMap<>();
    private final Map<String, List<String>> servicioToNodos = new ConcurrentHashMap<>();
    private final Map<String, Integer> rrIndex = new ConcurrentHashMap<>();
    private final String internalToken;

    public BalanceadorCargaService(String internalToken) throws RemoteException { this.internalToken = internalToken; }

    private void assertInternal(String token) throws RemoteException {
        if (!Objects.equals(token, internalToken)) throw new RemoteException("token interno invalido");
    }

    @Override
    public synchronized void registrarNodo(String token, String nodoId, String host, int rmiPort, List<String> servicios) throws RemoteException {
        assertInternal(token);
        NodoInfo ni = new NodoInfo();
        ni.nodoId = nodoId; ni.host = host; ni.port = rmiPort; ni.servicios.addAll(servicios); ni.lastHb = System.currentTimeMillis();
        nodos.put(nodoId, ni);
        for (String s : servicios) {
            servicioToNodos.computeIfAbsent(s, k -> new ArrayList<>());
            String url = String.format("rmi://%s:%d/%s", host, rmiPort, s);
            if (!servicioToNodos.get(s).contains(url)) servicioToNodos.get(s).add(url);
        }
    }

    @Override
    public synchronized void heartbeat(String token, String nodoId) throws RemoteException {
        assertInternal(token);
        NodoInfo ni = nodos.get(nodoId);
        if (ni != null) ni.lastHb = System.currentTimeMillis();
        // Purge dead nodes
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, NodoInfo>> it = nodos.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, NodoInfo> e = it.next();
            if (now - e.getValue().lastHb > 15000) { // 15s timeout
                it.remove();
                for (String s : e.getValue().servicios) {
                    servicioToNodos.getOrDefault(s, List.of()).removeIf(url -> url.contains(e.getValue().host + ":" + e.getValue().port));
                }
            }
        }
    }

    @Override
    public synchronized String obtenerEndpoint(String token, String servicio) throws RemoteException {
        List<String> urls = servicioToNodos.getOrDefault(servicio, List.of());
        if (urls.isEmpty()) throw new RemoteException("no hay nodos para servicio " + servicio);
        int idx = rrIndex.merge(servicio, 1, (a,b)->(a+b)%urls.size());
        return urls.get(idx % urls.size());
    }

    @Override
    public synchronized List<String> nodosSaludables(String token, String servicio) throws RemoteException {
        return new ArrayList<>(servicioToNodos.getOrDefault(servicio, List.of()));
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 21000;
        String internalToken = args.length > 2 ? args[2] : UUID.randomUUID().toString();
        try { LocateRegistry.createRegistry(port); } catch (Exception ignored) {}
        BalanceadorCargaService svc = new BalanceadorCargaService(internalToken);
        String url = String.format("rmi://%s:%d/lb", host, port);
        Naming.rebind(url, svc);
        System.out.printf("BalanceadorCarga en %s con token interno %s%n", url, internalToken);
    }
}
