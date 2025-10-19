# Sistema distribuido Java (RMI): Usuarios, Archivos y Auditoría

Componentes distribuidos (clases/servicios RMI):
- Usuario (UsuarioService + UsuarioRemote)
- Archivo (ArchivoService + ArchivoRemote)
- Auditor (AuditorService + AuditorRemote)
- Nodo (NodoService + NodoRemote)
- ControladorSeguridad (ControladorSeguridadService + ControladorSeguridadRemote)
- BalanceadorCarga (BalanceadorCargaService + BalanceadorCargaRemote)

Diseño de seguridad
- Autenticación: tokens compactos tipo JWT firmados HMAC (HS256) emitidos por ControladorSeguridad.
- Autorización: cada invocación remota recibe `token`; los servicios validan su presencia (ejemplo mínimo).
- Canal: Para mTLS en RMI usar stubs con RMISocketFactory SSL (no incluido por brevedad). Alternativa: túnel TLS (stunnel/SSH) o ejecutar en red segura.
- Rotación/Revocación: ControladorSeguridad permite revocar tokens; secreto configurable al arrancar.

Escalabilidad
- Múltiples nodos exportan los servicios; BalanceadorCarga mantiene registro y hace round-robin por servicio.
- Servicios son stateless excepto el almacenamiento en memoria (demo). En producción: DB replicada/S3.

Tolerancia a fallos
- Heartbeats de nodos al Balanceador; eliminación de nodos caídos (>15s sin latido).
- Cliente consulta `obtenerEndpoint(servicio)` para reintentar contra otro nodo.
- Replicación en demo: stubs `replicarUpsert/Delete` (best-effort). Sustituir por log de eventos o base de datos replicada.

Cómo compilar
- Requiere Java 17+ y Maven.
- En la raíz del proyecto:
  - `mvn -q -DskipTests package`

Cómo ejecutar (mínimo local)
1) Seguridad (puerto RMI 20000):
   - `java -cp target/distributed-rmi-1.0-SNAPSHOT-shaded.jar com.example.dist.server.ControladorSeguridadService localhost 20000 <SECRETO_HMAC>`
   - Anote el secreto HMAC impreso.
2) Balanceador (puerto RMI 21000):
   - `java -cp target/distributed-rmi-1.0-SNAPSHOT-shaded.jar com.example.dist.server.BalanceadorCargaService localhost 21000 <TOKEN_INTERNO>`
   - Anote el token interno impreso.
3) Nodos (ejemplo, puerto 22001):
   - Exporte servicios (usuario/archivo/auditor/nodo) en el mismo RMI registry del nodo. En esta demo puede arrancar uno a uno llamando a los `main/export()` o crear un bootstrap propio.

Notas de asignación de nodos
- Cada nodo expone: `rmi://HOST:PUERTO/usuario`, `.../archivo`, `.../auditor`, `.../nodo`.
- Regístrelos en el Balanceador con `registrarNodo(TOKEN_INTERNO, nodoId, host, puerto, ["usuario","archivo","auditor","nodo"])`.
- Envíe `heartbeat` periódico (cada 5s) con `heartbeat(TOKEN_INTERNO, nodoId)`.

Siguientes pasos recomendados
- Añadir bootstrap `NodeServer` que arranque todos los servicios del nodo, se registre en el LB y envíe heartbeats.
- Implementar validación real de token (consultar ControladorSeguridad) y RBAC por rol.
- Añadir replicación real (event log + confirmaciones) y almacenamiento persistente.
