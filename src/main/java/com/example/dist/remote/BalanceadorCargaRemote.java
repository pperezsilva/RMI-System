package com.example.dist.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface BalanceadorCargaRemote extends Remote {
    void registrarNodo(String token, String nodoId, String host, int rmiPort, List<String> servicios) throws RemoteException;
    void heartbeat(String token, String nodoId) throws RemoteException;
    String obtenerEndpoint(String token, String servicio) throws RemoteException; // devuelve URL RMI
    List<String> nodosSaludables(String token, String servicio) throws RemoteException;
}
