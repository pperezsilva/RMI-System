package com.example.dist.server;

import com.example.dist.common.ArchivoMetadata;
import com.example.dist.remote.ArchivoRemote;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArchivoService extends UnicastRemoteObject implements ArchivoRemote {
    private final Map<String, ArchivoMetadata> metas = new ConcurrentHashMap<>();
    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    private final String internalToken;

    public ArchivoService(String internalToken) throws RemoteException { this.internalToken = internalToken; }

    private void assertAuth(String token) throws RemoteException {
        if (token == null || token.isBlank()) throw new RemoteException("token requerido");
    }

    @Override
    public ArchivoMetadata subir(String token, String userId, String nombre, byte[] contenido) throws RemoteException {
        assertAuth(token);
        String id = UUID.randomUUID().toString();
        ArchivoMetadata m = new ArchivoMetadata();
        m.setId(id); m.setOwnerUserId(userId); m.setNombre(nombre); m.setTamano(contenido.length); m.setVersion(1);
        metas.put(id, m); blobs.put(id, contenido);
        return m;
    }

    @Override
    public byte[] descargar(String token, String fileId) throws RemoteException {
        assertAuth(token);
        return blobs.get(fileId);
    }

    @Override
    public List<ArchivoMetadata> listar(String token, String userId) throws RemoteException {
        assertAuth(token);
        List<ArchivoMetadata> out = new ArrayList<>();
        for (ArchivoMetadata m : metas.values()) if (Objects.equals(m.getOwnerUserId(), userId)) out.add(m);
        return out;
    }

    @Override
    public void eliminar(String token, String fileId) throws RemoteException {
        assertAuth(token);
        metas.remove(fileId); blobs.remove(fileId);
    }

    @Override
    public boolean bloquear(String token, String fileId, String userId) throws RemoteException {
        assertAuth(token);
        ArchivoMetadata m = metas.get(fileId);
        if (m == null) return false;
        if (m.getLockedByUserId() == null || Objects.equals(m.getLockedByUserId(), userId)) {
            m.setLockedByUserId(userId); return true;
        }
        return false;
    }

    @Override
    public boolean desbloquear(String token, String fileId, String userId) throws RemoteException {
        assertAuth(token);
        ArchivoMetadata m = metas.get(fileId);
        if (m == null) return false;
        if (Objects.equals(m.getLockedByUserId(), userId)) { m.setLockedByUserId(null); return true; }
        return false;
    }

    @Override
    public void replicarUpsert(String internalToken, ArchivoMetadata meta, byte[] contenido) throws RemoteException {
        if (!Objects.equals(this.internalToken, internalToken)) throw new RemoteException("token interno invalido");
        metas.put(meta.getId(), meta); if (contenido != null) blobs.put(meta.getId(), contenido);
    }

    @Override
    public void replicarDelete(String internalToken, String fileId) throws RemoteException {
        if (!Objects.equals(this.internalToken, internalToken)) throw new RemoteException("token interno invalido");
        metas.remove(fileId); blobs.remove(fileId);
    }

    public static void export(String host, int port) throws Exception {
        try { LocateRegistry.createRegistry(port); } catch (Exception ignored) {}
        ArchivoService svc = new ArchivoService(UUID.randomUUID().toString());
        String url = String.format("rmi://%s:%d/archivo", host, port);
        Naming.rebind(url, svc);
        System.out.println("ArchivoService en " + url);
    }
}
