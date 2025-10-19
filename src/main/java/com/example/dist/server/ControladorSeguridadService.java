package com.example.dist.server;

import com.example.dist.common.TokenInfo;
import com.example.dist.common.SecurityUtils;
import com.example.dist.remote.ControladorSeguridadRemote;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ControladorSeguridadService extends UnicastRemoteObject implements ControladorSeguridadRemote {
    private final String secret;
    private final Map<String, Boolean> revoked = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userRoles = new ConcurrentHashMap<>();
    private final Map<String, String> userPasswords = new ConcurrentHashMap<>();

    public ControladorSeguridadService(String secret) throws RemoteException {
        super();
        this.secret = secret;
        // demo users
        userPasswords.put("admin", hash("admin123"));
        userRoles.put("admin", List.of("ADMIN"));
        userPasswords.put("user", hash("user123"));
        userRoles.put("user", List.of("USER"));
    }

    @Override
    public TokenInfo emitirToken(String clientId, String username, String password) throws RemoteException {
        if (!Objects.equals(userPasswords.get(username), hash(password))) throw new RemoteException("Credenciales invalidas");
        String token = SecurityUtils.issueCompactToken(username, userRoles.getOrDefault(username, List.of("USER")), Instant.now(), 3600, secret);
        TokenInfo info = new TokenInfo();
        info.setSubject(username);
        info.setRoles(userRoles.get(username));
        info.setIssuedAt(Instant.now());
        info.setExpiresAt(Instant.now().plusSeconds(3600));
        info.setToken(token);
        return info;
    }

    @Override
    public boolean validar(String token) throws RemoteException {
        return !revoked.getOrDefault(token, false) && SecurityUtils.validateCompactToken(token, secret);
    }

    @Override
    public void revocar(String token) throws RemoteException { revoked.put(token, true); }

    @Override
    public String obtenerClavePublica() throws RemoteException { return "HMAC"; }

    private String hash(String s) { return Integer.toHexString(Objects.hash(s)); }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 20000;
        String secret = args.length > 2 ? args[2] : SecurityUtils.generateSecret();
        try { LocateRegistry.createRegistry(port); } catch (Exception ignored) {}
        ControladorSeguridadService svc = new ControladorSeguridadService(secret);
        String url = String.format("rmi://%s:%d/seguridad", host, port);
        Naming.rebind(url, svc);
        System.out.printf("ControladorSeguridad en %s con secreto %s%n", url, secret);
    }
}
