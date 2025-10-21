package com.example.dist.remote;

import com.example.dist.common.Usuario;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface UsuarioRemote extends Remote {
    Usuario crear(String token, String username, String email, String password) throws RemoteException;
    Usuario obtener(String token, String userId) throws RemoteException;
    List<Usuario> listar(String token) throws RemoteException;
    void asignarRol(String token, String userId, String rol) throws RemoteException;
    boolean autenticar(String token, String email, String password) throws RemoteException;
    // replicación
    void replicarUpsert(String internalToken, Usuario usuario) throws RemoteException;
}
