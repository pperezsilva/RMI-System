package com.example.dist.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NodoRemote extends Remote {
    String getNodoId() throws RemoteException;
    String getHost() throws RemoteException;
    int getRmiPort() throws RemoteException;
    List<String> getServiciosExportados() throws RemoteException;
    boolean healthCheck() throws RemoteException;
    long lastHeartbeat() throws RemoteException;
}
