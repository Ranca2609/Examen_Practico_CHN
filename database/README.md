# Base de datos — Sistema de Gestión de Préstamos Bancarios (CHN)

Motor: **Microsoft SQL Server 2022** · Base: **CHN_Prestamos** · Esquema: **dbo** · Moneda: **GTQ**
Migraciones: **Flyway** (`flyway-core` + `flyway-sqlserver`) · ORM: **Spring Data JPA / Hibernate**

Todo el modelo de datos vive en este directorio y está versionado. **No se usa
`hibernate.ddl-auto`**: el esquema lo crea y evoluciona exclusivamente Flyway, de modo
que la base de producción y la de pruebas se construyen con el mismo script y la
misma secuencia de cambios.

---

## 1. Contenido del directorio

| Archivo | Lo ejecuta | Propósito |
|---|---|---|
| `00_crear_base_datos.sql` | Persona / `init` de Docker | Crea la base, el login y el usuario de aplicación con `db_owner` **solo** sobre esa base. Requiere permisos de servidor. Está parametrizado con variables de `sqlcmd` (`DB_NAME`, `APP_USER`, `APP_PASSWORD`). |
| `init/inicializar.sh` | Docker Compose | Espera a que SQL Server acepte conexiones y ejecuta el script anterior con los valores del entorno, para no mantener dos versiones de la misma lógica. |
| `migration/V1__esquema_tablas.sql` | Flyway | Tablas, restricciones, llaves foráneas, índices y secuencias. |
| `migration/V2__vistas_funciones_procedimientos.sql` | Flyway | 2 funciones (cuota y total del plan), 3 vistas, 2 procedimientos y 1 trigger de auditoría. |
| `migration/V3__datos_iniciales.sql` | Flyway | Tabla `parametros` y parámetros de negocio no sensibles. |
| `migration/V4__vistas_tablero.sql` | Flyway | 2 vistas con las series del tablero: cartera por tipo de préstamo y recaudación mensual. |
| `migration/V5__endurecer_ck_solicitudes_tipo.sql` | Flyway | Rehace `ck_solicitudes_tipo` con cotejo binario: el `CI_AI` de la base aceptaba `personal` o `Personal`. |
| `demo/V900__datos_demo.sql` | Flyway (solo si `APP_DATOS_DEMO=true`) | 8 clientes, 10 solicitudes, 4 préstamos y 7 pagos de prueba. |
| `diagrama-entidad-relacion.md` | — | Diagrama ER (Mermaid + DBML), diccionario de datos y reglas de integridad. |

---

## 2. Cómo se ejecuta

### 2.1 Automático (lo normal)

`00_crear_base_datos.sql` se ejecuta una sola vez al preparar el servidor. A partir de
ahí, **cada arranque del backend aplica las migraciones pendientes** antes de levantar
el contexto de Spring:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration        # + classpath:db/demo si APP_DATOS_DEMO=true
    baseline-on-migrate: true
    out-of-order: true                       # V4+ se aplica aunque V900 (demo) ya esté cargada
    validate-on-migrate: true
```

Los scripts de este directorio son la **fuente de verdad** y se publican en el
*classpath* del backend bajo `db/migration` y `db/demo`. Flyway lleva el control en la
tabla `dbo.flyway_schema_history`: registra cada versión aplicada con su *checksum*, y
si un script ya aplicado se modifica, el arranque **falla** en lugar de dejar la base en
un estado desconocido (`validate-on-migrate: true`).

> Los datos de demostración se cargan **solo** cuando `APP_DATOS_DEMO=true`. En un
> ambiente real esa variable va en `false` y la carpeta `db/demo` nunca entra en la
> lista de *locations*.

### 2.2 Manual (SSMS, Azure Data Studio o `sqlcmd`)

El orden es obligatorio: cada script asume que el anterior ya corrió.

```
1) 00_crear_base_datos.sql                        -- conectado al servidor (login con permisos de servidor)
2) migration/V1__esquema_tablas.sql               -- conectado a CHN_Prestamos
3) migration/V2__vistas_funciones_procedimientos.sql
4) migration/V3__datos_iniciales.sql
5) migration/V4__vistas_tablero.sql
6) migration/V5__endurecer_ck_solicitudes_tipo.sql
7) demo/V900__datos_demo.sql                      -- opcional, solo para probar
```

Con `sqlcmd` (el parámetro `-f 65001` evita que se corrompan los acentos de los
comentarios y `-b` aborta al primer error):

```bash
sqlcmd -S localhost,1433 -U sa -P "<clave>" -C -b -f 65001 -i database/00_crear_base_datos.sql
sqlcmd -S localhost,1433 -U chn_app -P "<clave>" -C -b -f 65001 -d CHN_Prestamos -i database/migration/V1__esquema_tablas.sql
sqlcmd -S localhost,1433 -U chn_app -P "<clave>" -C -b -f 65001 -d CHN_Prestamos -i database/migration/V2__vistas_funciones_procedimientos.sql
sqlcmd -S localhost,1433 -U chn_app -P "<clave>" -C -b -f 65001 -d CHN_Prestamos -i database/migration/V3__datos_iniciales.sql
sqlcmd -S localhost,1433 -U chn_app -P "<clave>" -C -b -f 65001 -d CHN_Prestamos -i database/migration/V4__vistas_tablero.sql
sqlcmd -S localhost,1433 -U chn_app -P "<clave>" -C -b -f 65001 -d CHN_Prestamos -i database/migration/V5__endurecer_ck_solicitudes_tipo.sql
sqlcmd -S localhost,1433 -U chn_app -P "<clave>" -C -b -f 65001 -d CHN_Prestamos -i database/demo/V900__datos_demo.sql
```

**Los siete scripts son idempotentes**: se pueden ejecutar dos veces seguidas sin error
y sin duplicar datos. Cada objeto se crea con `IF OBJECT_ID(...) IS NULL` /
`IF NOT EXISTS`, los objetos programables usan `CREATE OR ALTER` y las inserciones
filtran por la clave natural (DPI, número de solicitud, de préstamo o de recibo).

> Si se ejecutan a mano *antes* del primer arranque, hay que poner
> `spring.flyway.baseline-on-migrate=true` (ya está configurado) para que Flyway
> adopte una base que ya tiene objetos.

---

## 3. Convención de versionado

```
V<versión>__<descripción_en_snake_case>.sql
  │            └── descripción breve, en español, sin tildes
  └── entero creciente; dos guiones bajos separan versión y descripción
```

| Rango | Uso |
|---|---|
| `V1` – `V3` | Esquema y catálogos iniciales (aplicados siempre). |
| `V4` – `V899` | Evoluciones del esquema (`V4`: vistas del tablero; `V5`: `ck_solicitudes_tipo` con cotejo binario). **Nunca se edita una versión ya aplicada**: se agrega una nueva. Como quedan por debajo de `V900`, Flyway corre con `out-of-order: true` para aplicarlas también en una base que ya tiene la demo. |
| `V900` – `V999` | Datos de demostración. Se numeran muy arriba para que siempre se apliquen al final y para no estorbar al esquema. |

Reglas prácticas:

- Un cambio ya liberado **no se modifica**, se corrige con una versión nueva. Cambiar un
  script aplicado altera su *checksum* y rompe la validación de Flyway.
- Cada script debe poder ejecutarse dos veces sin error.
- `GO` se usa solo donde el motor lo exige (`CREATE VIEW/FUNCTION/PROCEDURE/TRIGGER`
  deben ser la primera sentencia de su lote, y un `CREATE TABLE` debe cerrar su lote
  antes de que otra sentencia lo referencie). Flyway interpreta `GO` correctamente.
- `V900__datos_demo.sql` es **un solo lote sin `GO`** porque comparte variables de tabla
  entre sus bloques.

---

## 4. Modelo de datos

Seis tablas transaccionales más un catálogo de parámetros. El flujo del negocio es:

```
cliente  ──►  solicitud  ──(aprobada)──►  préstamo  ──►  pagos
```

### 4.1 `clientes` — titulares del crédito

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `nombre`, `apellido` | `NVARCHAR(60)` | Obligatorios, 2–60 caracteres |
| `numero_identificacion` | `NVARCHAR(13)` | **UNIQUE**. DPI de Guatemala: exactamente 13 dígitos (`CHECK`) |
| `fecha_nacimiento` | `DATE` | Obligatoria. La mayoría de edad (18 años) la valida el dominio, porque depende de la fecha actual |
| `direccion` | `NVARCHAR(200)` | Obligatoria, 5–200 caracteres |
| `correo_electronico` | `NVARCHAR(120)` | **UNIQUE**, formato `algo@algo.xx` (`CHECK`), se normaliza a minúsculas |
| `telefono` | `NVARCHAR(8)` | Exactamente 8 dígitos (`CHECK`) |
| `activo` | `BIT` | Por omisión `1` |
| `fecha_creacion` / `fecha_modificacion` | `DATETIME2(0)` | La modificación nunca es anterior a la creación (`CHECK`) |

### 4.2 `solicitudes_prestamo` — solicitudes y su resolución

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `numero_solicitud` | `NVARCHAR(25)` | **UNIQUE** y `CHECK` de estructura (`ck_solicitudes_numero`). Número oficial `SC-001-2026-000001-3` (secuencia `seq_solicitud`) |
| `cliente_id` | `BIGINT` | **FK** → `clientes(id)`, `NO ACTION` |
| `monto_solicitado` | `DECIMAL(15,2)` | 1 000.00 – 5 000 000.00 |
| `plazo_meses` | `INT` | 6 – 360 |
| `tasa_interes_anual` | `DECIMAL(5,2)` | 0.01 – 100.00 (porcentaje anual) |
| `tipo_prestamo` | `NVARCHAR(20)` | `PERSONAL`, `HIPOTECARIO`, `VEHICULAR`, `EMPRESARIAL`, `EDUCATIVO`, en mayúsculas exactas (`ck_solicitudes_tipo` compara con `Latin1_General_BIN2` desde `V5`) |
| `destino` | `NVARCHAR(200)` | Obligatorio, 5–200 |
| `ingreso_mensual_declarado` | `DECIMAL(15,2)` | Mayor que 0 |
| `estado` | `NVARCHAR(15)` | `EN_PROCESO`, `APROBADA`, `RECHAZADA` |
| `fecha_solicitud` | `DATETIME2(0)` | Obligatoria |
| `observaciones` | `NVARCHAR(500)` | Opcional |
| `fecha_resolucion`, `usuario_resolucion` | `DATETIME2(0)`, `NVARCHAR(50)` | Nulos mientras está `EN_PROCESO` |
| `monto_aprobado`, `plazo_aprobado_meses`, `tasa_aprobada` | — | Solo en `APROBADA`. `monto_aprobado ≤ monto_solicitado` |
| `motivo_resolucion` | `NVARCHAR(500)` | **Obligatorio (≥ 10 caracteres) en `RECHAZADA`**; opcional en `APROBADA` |

La restricción `ck_solicitudes_coherencia_resolucion` impide que el estado y los datos de
resolución se contradigan: una solicitud `EN_PROCESO` no puede tener resolución, una
`APROBADA` debe traer monto, plazo y tasa, y una `RECHAZADA` debe traer motivo y **no**
montos aprobados. Una solicitud resuelta ya no cambia de estado (lo impone el dominio).

### 4.3 `prestamos` — préstamos desembolsados

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `numero_prestamo` | `NVARCHAR(25)` | **UNIQUE** y `CHECK` de estructura (`ck_prestamos_numero`). Número oficial `PR-001-2026-000001-9` (`seq_prestamo`) |
| `solicitud_id` | `BIGINT` | **FK UNIQUE** → `solicitudes_prestamo(id)`. Una solicitud genera **un** préstamo |
| `cliente_id` | `BIGINT` | **FK** → `clientes(id)` (desnormalizado a propósito: evita un JOIN en todas las consultas de cartera) |
| `monto_aprobado` | `DECIMAL(15,2)` | > 0 |
| `plazo_meses` | `INT` | 6 – 360 |
| `tasa_interes_anual` | `DECIMAL(5,2)` | 0.01 – 100.00 |
| `cuota_mensual` | `DECIMAL(15,2)` | > 0. Sistema francés |
| `monto_total_a_pagar` | `DECIMAL(15,2)` | > 0. Suma del plan de amortización, con la última cuota ajustada; puede diferir en centavos de cuota × plazo |
| `total_pagado` | `DECIMAL(15,2)` | 0 ≤ `total_pagado` ≤ `monto_total_a_pagar` |
| `saldo_pendiente` | calculada `PERSISTED` | `monto_total_a_pagar - total_pagado`. **No se escribe**: el ORM la mapea como `insertable=false, updatable=false` |
| `estado` | `NVARCHAR(15)` | `VIGENTE`, `LIQUIDADO`. `LIQUIDADO` exige saldo cero (`CHECK`) |
| `fecha_desembolso`, `fecha_vencimiento` | `DATE` | Vencimiento = desembolso + plazo, y siempre posterior (`CHECK`) |

### 4.4 `pagos` — recibos aplicados

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `numero_recibo` | `NVARCHAR(25)` | **UNIQUE** y `CHECK` de estructura (`ck_pagos_recibo`). Número oficial `RC-001-2026-000001-4` (`seq_recibo`) |
| `prestamo_id` | `BIGINT` | **FK** → `prestamos(id)` |
| `monto` | `DECIMAL(15,2)` | > 0 y nunca mayor al saldo pendiente (lo valida el dominio) |
| `fecha_pago` | `DATETIME2(0)` | Obligatoria |
| `forma_pago` | `NVARCHAR(15)` | `EFECTIVO` |
| `saldo_anterior`, `saldo_posterior` | `DECIMAL(15,2)` | Corte histórico del recibo. `saldo_posterior = saldo_anterior - monto` (`CHECK`) |
| `usuario_registro` | `NVARCHAR(50)` | Quién cobró |
| `observaciones` | `NVARCHAR(300)` | Opcional |

Guardar los saldos en el recibo es una decisión deliberada: permite reimprimirlo
exactamente como se emitió, sin recalcular nada y sin depender del estado actual del
préstamo.

### 4.5 `usuarios` — operadores del sistema

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `username` | `NVARCHAR(50)` | **UNIQUE**, mínimo 3 caracteres |
| `password_hash` | `NVARCHAR(100)` | **Siempre** un hash BCrypt (fuerza 12). Nunca la contraseña |
| `nombre_completo`, `correo` | `NVARCHAR(120)` | Obligatorios |
| `rol` | `NVARCHAR(15)` | `ADMIN`, `ANALISTA`, `CAJERO`, `CONSULTA` |
| `activo` | `BIT` | Por omisión `1` |
| `intentos_fallidos` | `INT` | ≥ 0. A los 5 intentos se bloquea la cuenta |
| `bloqueado_hasta` | `DATETIME2(0)` | Fin del bloqueo temporal (15 minutos) |
| `ultimo_acceso` | `DATETIME2(0)` | Último inicio de sesión correcto |

**Los usuarios no se insertan por script.** Los crea la aplicación al arrancar
(`CargadorUsuariosIniciales`) para que la contraseña se almacene ya cifrada con BCrypt y
nunca quede en texto plano dentro de un archivo versionado en el repositorio.

### 4.6 `auditoria` — bitácora

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `usuario` | `NVARCHAR(50)` | Quién ejecutó la operación (`trigger_bd` si la registró la base) |
| `accion` | `NVARCHAR(50)` | `LOGIN_EXITOSO`, `CLIENTE_CREADO`, `SOLICITUD_APROBADA`, `PAGO_REGISTRADO`, … |
| `entidad`, `entidad_id` | `NVARCHAR(50)` | Qué se tocó |
| `detalle` | `NVARCHAR(1000)` | Resumen legible. **Nunca contraseñas ni tokens** |
| `direccion_ip` | `NVARCHAR(45)` | Soporta IPv6 |
| `fecha` | `DATETIME2(0)` | Momento del evento |

Tabla de **solo inserción y consulta**: no se actualiza ni se borra, para que la traza
sea confiable. Se expone únicamente en `GET /api/v1/auditoria`, restringido al rol
`ADMIN`.

### 4.7 `parametros` — catálogo de negocio

`clave` (PK) / `valor` / `descripcion`. Contiene los límites del negocio
(`TASA_BASE_ANUAL`, `PLAZO_MINIMO_MESES`, `PLAZO_MAXIMO_MESES`, `MONTO_MINIMO`,
`MONTO_MAXIMO`, `PORCENTAJE_MAXIMO_ENDEUDAMIENTO`, `MAXIMO_PRESTAMOS_VIGENTES`,
`MONEDA`). Son los mismos valores que valida el dominio Java; se publican aquí para
consulta del área de negocio y para que los reportes en SQL no lleven números mágicos.

---

## 5. Política de borrado: cascada explícita, no del motor

**Todas las llaves foráneas se declaran `ON DELETE NO ACTION` / `ON UPDATE NO ACTION`.**

Dos razones:

1. **Técnica.** `clientes` llega a `pagos` por dos caminos
   (`clientes → solicitudes → prestamos → pagos` y `clientes → prestamos → pagos`).
   SQL Server rechaza la creación de múltiples rutas de cascada
   (*"may cause cycles or multiple cascade paths"*).
2. **De negocio.** Borrar un cliente es la operación más destructiva del sistema. Que el
   motor la ejecute en silencio esconde el alcance real del borrado.

Por eso el caso de uso `GestionarClientesUseCase.eliminar(...)` borra en **orden
explícito y dentro de una sola transacción**:

```
1. pagos                de todos los préstamos del cliente
2. prestamos            del cliente
3. solicitudes_prestamo del cliente
4. cliente
```

Si cualquiera de los cuatro pasos falla, la transacción se revierte completa y no queda
nada a medias. Además:

- El trigger `dbo.tr_clientes_auditoria_delete` deja un registro en `auditoria`
  (`usuario = 'trigger_bd'`, `accion = 'CLIENTE_ELIMINADO_BD'`) por cada cliente
  eliminado, **incluso si el borrado se hizo fuera de la aplicación**.
- La aplicación audita la misma operación con el usuario real y su dirección IP.
- La operación está restringida al rol `ADMIN` (`DELETE /api/v1/clientes/{id}`).

---

## 6. Inventario de objetos programables

### Secuencias

| Objeto | Genera | Formato |
|---|---|---|
| `dbo.seq_solicitud` | Correlativo de solicitud | `SC-001-2026-000001-3` |
| `dbo.seq_prestamo` | Correlativo de préstamo | `PR-001-2026-000001-9` |
| `dbo.seq_recibo` | Correlativo de recibo | `RC-001-2026-000001-4` |

Se usan secuencias y no `MAX(...) + 1`: son atómicas, no bloquean y no repiten valores
con usuarios concurrentes. `V900` las adelanta (`RESTART WITH`) para que los correlativos
de la demo no choquen con los que genere la aplicación.

### Funciones

| Objeto | Firma | Propósito |
|---|---|---|
| `dbo.fn_calcular_cuota` | `(@monto DECIMAL(15,2), @tasa_anual DECIMAL(5,2), @plazo_meses INT) → DECIMAL(15,2)` | Cuota mensual por sistema francés: `monto · i / (1 − (1+i)^−n)`, con `i = tasa/100/12`. Con tasa 0 reparte el capital (`monto/n`). Réplica exacta de `CalculadoraAmortizacion` en Java, para que un reporte en SQL y la API nunca den cifras distintas. Devuelve `NULL` ante entradas inválidas en lugar de fallar, porque se usa dentro de vistas. |
| `dbo.fn_total_plan` | `(@monto DECIMAL(15,2), @tasa_anual DECIMAL(5,2), @plazo_meses INT) → DECIMAL(15,2)` | Total a pagar como **suma real del plan**, no como cuota × plazo. Recorre el plan mes a mes igual que `CalculadoraAmortizacion`: en la última cuota (o antes, si el abono agotaría el saldo) el capital se fija al saldo exacto y la cuota se recompone, así que el total puede diferir en centavos de la multiplicación. `V900` la usa para que los préstamos de la demo guarden el mismo `monto_total_a_pagar` que calcularía la aplicación. Devuelve `NULL` ante entradas inválidas. |

### Vistas

| Objeto | Devuelve |
|---|---|
| `dbo.vw_resumen_general` | **Una sola fila** con los 9 indicadores del tablero (`GET /api/v1/resumen`). Todos los montos pasan por `ISNULL`, así que nunca son `NULL`. |
| `dbo.vw_prestamos_saldo` | Cartera con cliente resuelto, saldo, porcentaje pagado, cuotas cubiertas y días de vigencia / para vencimiento. |
| `dbo.vw_solicitudes_detalle` | Solicitudes con datos del cliente, la resolución, los días que tardó, la cuota estimada y el número de préstamo si llegó a desembolso. |
| `dbo.vw_cartera_por_tipo` | Una fila por tipo de préstamo **con préstamos**: cantidad, monto aprobado, saldo pendiente y total recuperado (`V4`). El tipo se toma de la solicitud de origen. La API completa con ceros los tipos que falten. |
| `dbo.vw_recaudacion_mensual` | Una fila por mes calendario **con pagos**: año, mes, cantidad de pagos y monto recaudado (`V4`). La API arma los 12 meses que terminan en el mes en curso y rellena con ceros. |

### Procedimientos

| Objeto | Parámetro | Devuelve | Errores |
|---|---|---|---|
| `dbo.sp_estado_cuenta_prestamo` | `@prestamo_id BIGINT` | 2 conjuntos: encabezado del préstamo + histórico de pagos (más antiguo primero) | `THROW 50001` si el préstamo no existe |
| `dbo.sp_historial_pagos_cliente` | `@cliente_id BIGINT` | 2 conjuntos: pagos de todos sus préstamos + totales del cliente | `THROW 50001` si el cliente no existe |

Ambos usan `SET NOCOUNT ON` para que los mensajes de "*n* filas afectadas" no se
confundan con conjuntos de resultados.

### Trigger

| Objeto | Evento | Efecto |
|---|---|---|
| `dbo.tr_clientes_auditoria_delete` | `AFTER DELETE ON dbo.clientes` | Inserta en `auditoria` una fila por cliente eliminado (`trigger_bd` / `CLIENTE_ELIMINADO_BD`), con nombre, DPI, correo y el *login* de la sesión. Es una inserción en conjunto, sin cursores. |

### Índices no agrupados

Uno por cada patrón de consulta real de la API:

| Tabla | Índices |
|---|---|
| `clientes` | `ix_clientes_apellido_nombre`, `ix_clientes_activo`, `ix_clientes_fecha_creacion` |
| `solicitudes_prestamo` | `ix_solicitudes_cliente`, `ix_solicitudes_estado`, `ix_solicitudes_fecha` |
| `prestamos` | `ix_prestamos_cliente_estado`, `ix_prestamos_estado`, `ix_prestamos_fecha_desembolso`, `ix_prestamos_fecha_vencimiento` |
| `pagos` | `ix_pagos_prestamo`, `ix_pagos_fecha` |
| `auditoria` | `ix_auditoria_fecha`, `ix_auditoria_usuario`, `ix_auditoria_entidad` |
| `usuarios` | `ix_usuarios_rol` (la búsqueda por `username` ya la cubre el `UNIQUE`) |

Los `UNIQUE` sobre `numero_identificacion`, `correo_electronico`, `username`,
`numero_solicitud`, `numero_prestamo`, `numero_recibo` y `solicitud_id` crean además su
propio índice, que es el que usan las búsquedas por número.

---

## 7. Datos de demostración (`V900`)

Se cargan solo con `APP_DATOS_DEMO=true`:

| Entidad | Cantidad | Detalle |
|---|---|---|
| Clientes | 8 | DPI de 13 dígitos, teléfonos de 8 dígitos, direcciones con zonas reales de Guatemala, todos mayores de edad |
| Solicitudes | 10 | 4 `APROBADA`, 2 `RECHAZADA` (con motivo redactado), 4 `EN_PROCESO`, variando el tipo de préstamo |
| Préstamos | 4 | Derivados de las solicitudes aprobadas |
| Pagos | 7 | En efectivo, repartidos en 3 préstamos |

Cómo se garantiza que los importes cuadren:

- La cuota **no se escribe a mano**: sale de `dbo.fn_calcular_cuota` con el monto, la
  tasa y el plazo de la resolución de la solicitud.
- `monto_total_a_pagar = dbo.fn_total_plan(monto, tasa, plazo)`: la suma real del plan,
  con la última cuota ajustada, igual que la calcula la aplicación al aprobar. Por eso
  puede diferir en centavos de `cuota × plazo`.
- El monto de cada recibo es un múltiplo de la cuota real del préstamo.
- Los saldos del recibo se derivan de una **suma acumulada** de los pagos anteriores del
  mismo préstamo, por lo que `saldo_posterior = saldo_anterior - monto` siempre cuadra.
- Al final, `total_pagado` se **reconstruye** desde la suma real de pagos y el script
  verifica el cuadre: si algo no coincide lanza `THROW 50002` / `50003` y la migración
  falla, en lugar de dejar la base incoherente.

Un préstamo (`PR-001-2026-000004-3`) queda **sin pagos** y **ninguno queda liquidado**, a
propósito: así el manual de usuario muestra saldos vivos y también el caso "préstamo sin
movimientos".

---

## 8. Seguridad de la información aplicada a la base

- **Mínimo privilegio.** El backend se conecta con el usuario de aplicación, que es
  `db_owner` **solo** de `CHN_Prestamos`. Nunca `sa`, nunca `sysadmin`. Si la aplicación
  se viera comprometida, el daño queda contenido en esta base.
- **Credenciales fuera del código.** Usuario y contraseña llegan por variables de
  entorno (`DB_USER`, `DB_PASSWORD`). Ningún script del repositorio contiene la
  contraseña real; la de `00_crear_base_datos.sql` es de examen y está parametrizada.
- **Contraseñas de usuarios.** Solo se almacena el hash BCrypt (fuerza 12). El esquema no
  tiene ninguna columna donde pudiera caber una contraseña en claro.
- **Sin inyección SQL.** Todo el acceso pasa por Spring Data JPA con consultas
  parametrizadas y los procedimientos se invocan con parámetros, nunca concatenando
  texto.
- **Defensa en profundidad.** Los `CHECK` replican las reglas del dominio: aunque alguien
  escriba directamente en la base, no puede dejar un estado incoherente (una solicitud
  aprobada sin monto, un recibo cuyos saldos no cuadran, un préstamo liquidado con saldo).
- **Trazabilidad.** Tabla `auditoria` de solo inserción, más el trigger de borrado de
  clientes que registra incluso lo que se haga fuera de la aplicación.
- **Datos personales.** Se guarda lo mínimo necesario para el trámite (DPI, dirección,
  correo, teléfono). No se almacenan datos de tarjetas ni de cuentas bancarias.

---

## 9. Consultas útiles para verificar la instalación

```sql
-- Migraciones aplicadas y su resultado
SELECT installed_rank, version, description, success, installed_on
FROM dbo.flyway_schema_history
ORDER BY installed_rank;

-- Tablero general (una sola fila)
SELECT * FROM dbo.vw_resumen_general;

-- Series del tablero: solo los tipos con préstamos y los meses con pagos
-- (la API completa con ceros los 5 tipos y los 12 meses)
SELECT * FROM dbo.vw_cartera_por_tipo ORDER BY tipo_prestamo;
SELECT * FROM dbo.vw_recaudacion_mensual ORDER BY anio, mes;

-- La suma de la cartera por tipo cuadra con los totales del tablero: no debe devolver filas
SELECT r.monto_total_aprobado, c.monto_aprobado,
       r.saldo_pendiente_total, c.saldo_pendiente,
       r.total_recuperado, c.total_recuperado
FROM dbo.vw_resumen_general r
    CROSS JOIN (SELECT ISNULL(SUM(monto_aprobado), 0)   AS monto_aprobado,
                       ISNULL(SUM(saldo_pendiente), 0)  AS saldo_pendiente,
                       ISNULL(SUM(total_recuperado), 0) AS total_recuperado
                FROM dbo.vw_cartera_por_tipo) c
WHERE r.monto_total_aprobado  <> c.monto_aprobado
   OR r.saldo_pendiente_total <> c.saldo_pendiente
   OR r.total_recuperado      <> c.total_recuperado;

-- Cartera con saldos
SELECT numero_prestamo, nombre_cliente, monto_total_a_pagar, total_pagado,
       saldo_pendiente, porcentaje_pagado, estado
FROM dbo.vw_prestamos_saldo
ORDER BY numero_prestamo;

-- Estado de cuenta de un préstamo (encabezado + pagos)
EXEC dbo.sp_estado_cuenta_prestamo @prestamo_id = 1;

-- Historial de pagos de un cliente
EXEC dbo.sp_historial_pagos_cliente @cliente_id = 1;

-- La cuota y el total a pagar de SQL deben coincidir con los de la aplicación
SELECT dbo.fn_calcular_cuota(70000.00, 14.50, 24) AS cuota_esperada,
       dbo.fn_total_plan(70000.00, 14.50, 24)     AS total_esperado;

-- Cuadre de la cartera: no debe devolver ninguna fila
SELECT p.numero_prestamo, p.total_pagado, SUM(pg.monto) AS suma_pagos
FROM dbo.prestamos p
    LEFT JOIN dbo.pagos pg ON pg.prestamo_id = p.id
GROUP BY p.numero_prestamo, p.total_pagado
HAVING p.total_pagado <> ISNULL(SUM(pg.monto), 0);
```

---

## 10. Diagnóstico de problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `Login failed for user 'chn_app'` | No se ejecutó `00_crear_base_datos.sql`, o `DB_PASSWORD` no coincide con la del login | Ejecutar el script de preparación y revisar la variable de entorno |
| `Cannot open database "CHN_Prestamos"` | La base no existe | Ejecutar `00_crear_base_datos.sql` |
| `Validate failed: Migration checksum mismatch` | Se editó un script ya aplicado | Revertir la edición y crear una versión nueva (`V6`, `V7`, …) |
| `The database ... is not empty` al primer arranque | Se ejecutaron los scripts a mano antes que Flyway | Ya está previsto: `baseline-on-migrate=true` |
| No aparecen los datos de prueba | `APP_DATOS_DEMO` no está en `true` | Ajustar la variable y reiniciar el backend |
| `THROW 50002 / 50003` al aplicar `V900` | Los datos demo no cuadraron (base con datos previos que reutilizan los mismos correlativos) | Cargar la demo sobre una base limpia |
| `The ALTER TABLE statement conflicted with the CHECK constraint "ck_solicitudes_tipo"` al aplicar `V5` | Una solicitud tiene en `tipo_prestamo` un texto que no es del catálogo ni siquiera en mayúsculas (por ejemplo, con acentos), cargado fuera de la aplicación. `V5` corrige solo mayúsculas y minúsculas | Corregir la fila con el nombre exacto del tipo y volver a arrancar el backend |
