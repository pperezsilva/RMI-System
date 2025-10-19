package com.example.dist.remote;

import com.example.dist.common.TokenInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ControladorSeguridadRemote extends Remote {
    TokenInfo emitirToken(String clientId, String username, String password) throws RemoteException;
    boolean validar(String token) throws RemoteException;
    void revocar(String token) throws RemoteException;
    String obtenerClavePublica() throws RemoteException; // placeholder para interfaz
}
