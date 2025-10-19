package com.example.dist.common;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public class AuditoriaEvento implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private Instant timestamp;
    private String actorUserId;
    private String accion;
    private String recurso;
    private String detalles;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getRecurso() { return recurso; }
    public void setRecurso(String recurso) { this.recurso = recurso; }
    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }
}
