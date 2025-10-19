package com.example.dist.common;

import java.io.Serial;
import java.io.Serializable;

public class ArchivoMetadata implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ownerUserId;
    private String nombre;
    private long tamano;
    private long version;
    private String lockedByUserId;
    private String checksum;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public long getTamano() { return tamano; }
    public void setTamano(long tamano) { this.tamano = tamano; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getLockedByUserId() { return lockedByUserId; }
    public void setLockedByUserId(String lockedByUserId) { this.lockedByUserId = lockedByUserId; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}
