package com.example.dist.remote;

import com.example.dist.common.AuditoriaEvento;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface AuditorRemote extends Remote {
    void registrarEvento(String token, AuditoriaEvento evento) throws RemoteException;
    List<AuditoriaEvento> consultar(String token, String filtroActor, String filtroAccion) throws RemoteException;
}
