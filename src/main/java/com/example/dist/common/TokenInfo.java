package com.example.dist.common;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class TokenInfo implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private String subject;
    private List<String> roles;
    private Instant issuedAt;
    private Instant expiresAt;
    private String token; // compact form

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
