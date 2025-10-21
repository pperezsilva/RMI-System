package com.example.dist.server;

import com.example.dist.common.Usuario;
import com.example.dist.remote.UsuarioRemote;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UsuarioService extends UnicastRemoteObject implements UsuarioRemote {
    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    private final Map<String, String> emailIndex = new ConcurrentHashMap<>(); // email(normalizado) -> userId
    private final String internalToken; // para replicación

    public UsuarioService(String internalToken) throws RemoteException { this.internalToken = internalToken; }

    private void assertAuth(String token) throws RemoteException {
        if (token == null || token.isBlank()) throw new RemoteException("token requerido");
    }

    private static String normEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public Usuario crear(String token, String username, String email, String password) throws RemoteException {
        assertAuth(token);
        if (email == null || email.isBlank()) throw new RemoteException("email requerido");
        String emailKey = normEmail(email);
        if (emailIndex.containsKey(emailKey)) throw new RemoteException("email ya registrado");
        String id = UUID.randomUUID().toString();
        Usuario u = new Usuario(id, username);
        u.setEmail(emailKey);
        u.setPasswordHash(Integer.toHexString(Objects.hash(password)));
        usuarios.put(id, u);
        emailIndex.put(emailKey, id);
        // best-effort replication would call peers here
        return u;
    }

    @Override
    public Usuario obtener(String token, String userId) throws RemoteException {
        assertAuth(token);
        return usuarios.get(userId);
    }

    @Override
    public List<Usuario> listar(String token) throws RemoteException {
        assertAuth(token);
        return new ArrayList<>(usuarios.values());
    }

    @Override
    public void asignarRol(String token, String userId, String rol) throws RemoteException {
        assertAuth(token);
        Usuario u = usuarios.get(userId);
        if (u == null) throw new RemoteException("usuario no existe");
        u.getRoles().add(rol);
    }

    @Override
    public boolean autenticar(String token, String email, String password) throws RemoteException {
        assertAuth(token);
        String emailKey = normEmail(email);
        String userId = emailIndex.get(emailKey);
        if (userId == null) return false;
        Usuario u = usuarios.get(userId);
        if (u == null) return false;
        return Objects.equals(u.getPasswordHash(), Integer.toHexString(Objects.hash(password)));
    }

    @Override
    public void replicarUpsert(String internalToken, Usuario usuario) throws RemoteException {
        if (!Objects.equals(this.internalToken, internalToken)) throw new RemoteException("token interno invalido");
        usuarios.put(usuario.getId(), usuario);
        if (usuario.getEmail() != null) {
            emailIndex.put(normEmail(usuario.getEmail()), usuario.getId());
        }
    }

    public static void export(String host, int port) throws Exception {
        try { LocateRegistry.createRegistry(port); } catch (Exception ignored) {}
        UsuarioService svc = new UsuarioService(UUID.randomUUID().toString());
        String url = String.format("rmi://%s:%d/usuario", host, port);
        Naming.rebind(url, svc);
        System.out.println("UsuarioService en " + url);
    }
}
