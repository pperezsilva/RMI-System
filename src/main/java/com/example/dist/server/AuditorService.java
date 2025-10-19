package com.example.dist.server;

import com.example.dist.common.AuditoriaEvento;
import com.example.dist.remote.AuditorRemote;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditorService extends UnicastRemoteObject implements AuditorRemote {
    private final List<AuditoriaEvento> eventos = new CopyOnWriteArrayList<>();

    public AuditorService() throws RemoteException {}

    @Override
    public void registrarEvento(String token, AuditoriaEvento evento) throws RemoteException {
        if (token == null || token.isBlank()) throw new RemoteException("token requerido");
        eventos.add(evento);
    }

    @Override
    public List<AuditoriaEvento> consultar(String token, String filtroActor, String filtroAccion) throws RemoteException {
        if (token == null || token.isBlank()) throw new RemoteException("token requerido");
        List<AuditoriaEvento> out = new ArrayList<>();
        for (AuditoriaEvento e : eventos) {
            if ((filtroActor == null || filtroActor.equals(e.getActorUserId())) &&
                (filtroAccion == null || filtroAccion.equals(e.getAccion()))) {
                out.add(e);
            }
        }
        return out;
    }

    public static void export(String host, int port) throws Exception {
        try { LocateRegistry.createRegistry(port); } catch (Exception ignored) {}
        AuditorService svc = new AuditorService();
        String url = String.format("rmi://%s:%d/auditor", host, port);
        Naming.rebind(url, svc);
        System.out.println("AuditorService en " + url);
    }
}
