package com.example.dist.remote;

import com.example.dist.common.ArchivoMetadata;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ArchivoRemote extends Remote {
    ArchivoMetadata subir(String token, String userId, String nombre, byte[] contenido) throws RemoteException;
    byte[] descargar(String token, String fileId) throws RemoteException;
    List<ArchivoMetadata> listar(String token, String userId) throws RemoteException;
    void eliminar(String token, String fileId) throws RemoteException;
    void renombrar(String token, String fileId, String nuevoNombre) throws RemoteException;
    boolean bloquear(String token, String fileId, String userId) throws RemoteException;
    boolean desbloquear(String token, String fileId, String userId) throws RemoteException;
    void replicarUpsert(String internalToken, ArchivoMetadata meta, byte[] contenido) throws RemoteException;
    void replicarDelete(String internalToken, String fileId) throws RemoteException;
}
