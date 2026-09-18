# Colección de la API — CHN Sistema de Préstamos

Colección de pruebas de la API REST del sistema de gestión de préstamos del **Crédito Hipotecario
Nacional de Guatemala**. Cubre los 25 endpoints del contrato —incluidas las **descargas de reportes
en PDF y en Excel**—, sus **filtros de búsqueda**, la matriz de roles y 25 casos de error, con
**105 peticiones** repartidas en 9 carpetas y un script de pruebas en cada una.

| Archivo | Para qué sirve |
| --- | --- |
| `CHN-Prestamos.postman_collection.json` | Colección Postman v2.1.0 con las 105 peticiones y sus pruebas |
| `CHN-Prestamos.postman_environment.json` | Entorno «CHN - Local» con las variables y los ids de ejemplo |
| `CHN-Prestamos.insomnia.json` | La misma cobertura exportada para Insomnia (formato 4) |

## Antes de empezar

La colección prueba un sistema **en ejecución**. Levántelo desde la raíz del repositorio:

```bash
docker compose up -d --build
```

Verifique que responde antes de importar nada:

```bash
curl http://localhost:8081/actuator/health     # {"status":"UP"}
```

| Recurso | Dirección |
| --- | --- |
| API | http://localhost:8081/api/v1 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI | http://localhost:8081/v3/api-docs |
| Aplicación web | http://localhost:8080 |

## Importar en Postman

1. Abra Postman y pulse **Import** (arriba a la izquierda).
2. Arrastre **los dos archivos** de Postman, o use **Upload Files** y selecciónelos juntos:
   - `CHN-Prestamos.postman_collection.json`
   - `CHN-Prestamos.postman_environment.json`
3. Confirme con **Import**. Aparecerán la colección «CHN - Sistema de Prestamos» en la pestaña
   *Collections* y el entorno «CHN - Local» en *Environments*.
4. **Seleccione el entorno.** En el desplegable de la esquina superior derecha elija
   **CHN - Local**. Es el paso que más se olvida: sin entorno activo las variables `{{baseUrl}}` y
   `{{token}}` no se resuelven y todas las peticiones fallan.
5. Ejecute **00 - Autenticacion → Login ADMIN**. El script de pruebas guarda el token y el resto de
   la colección ya queda autenticada.

Si su API no está en `localhost:8081`, cambie solo la variable `baseUrl` del entorno.

## Importar en Insomnia

1. Abra Insomnia y pulse **Import** desde el panel de colecciones (o *Application → Preferences →
   Data → Import Data → From File*).
2. Seleccione `CHN-Prestamos.insomnia.json` y elija importar como **colección**.
3. Se crea la colección «CHN - Sistema de Prestamos» con las 9 carpetas y el entorno **CHN - Local**.
4. Active el entorno en el selector de la parte superior y compruebe que `baseUrl` apunta a su API.
5. Ejecute **00 - Autenticacion → Login ADMIN**.

> **Diferencia importante frente a Postman.** Los scripts de Postman usan el objeto `pm`, que no
> existe en Insomnia, así que la exportación de Insomnia **no los incluye** y no hay automatismo
> alguno. En la práctica:
>
> - **El token no se guarda solo.** Copie el valor de `token` de la respuesta del login y péguelo en
>   la variable `token` del entorno (*Manage Environments*). Lo mismo para `token_analista`,
>   `token_cajero` y `token_consulta` si va a probar la carpeta 07.
> - **Los ids encadenados se ponen a mano.** `cliente_id_nuevo`, `solicitud_id_nueva`,
>   `solicitud_id_rechazo`, `prestamo_id_nuevo` y `pago_id_nuevo` llegan vacías: cópielas del `id`
>   que devuelva cada respuesta antes de lanzar la petición que las usa.
> - **`dpi_nuevo` y `correo_nuevo` ya traen un valor listo para usar** (`9993000010101` y
>   `andrea.quinonez.9993000@correo.gt`), porque en Postman las genera un script. Son UNIQUE en la
>   base, así que si reutiliza el entorno y «Crear cliente» responde **409 DUPLICADO**, cambie el
>   sufijo numérico de ambas por otro cualquiera (o elimine antes el cliente creado).
> - **La carpeta 08 funciona tal cual.** Sus diecisiete variables de filtro traen un valor literal y
>   ninguna de sus peticiones depende de un id encadenado: basta con pegar el `token` para
>   recorrerla entera, incluso sin haber ejecutado las carpetas 01 a 04.
>
> Las pruebas automáticas (`pm.test`) tampoco se ejecutan: en Insomnia se verifican los códigos y
> los cuerpos a la vista. Para validación automatizada use Postman, el Collection Runner o Newman.

## Orden de ejecución recomendado

Ejecute las carpetas **en orden**. Cada una deja en el entorno lo que necesita la siguiente.

| # | Carpeta | Peticiones | Qué hace y qué deja |
| --- | --- | --- | --- |
| 00 | Autenticacion | 5 | **Primero, siempre.** Los cuatro logins guardan `token`, `token_analista`, `token_cajero` y `token_consulta` |
| 01 | Clientes | 8 | Crea un cliente desechable (`cliente_id_nuevo`), lo consulta, lo edita y lo elimina en cascada al final |
| 02 | Solicitudes de prestamo | 8 | Simula, crea dos solicitudes, aprueba una (`solicitud_id_nueva`) y rechaza la otra (`solicitud_id_rechazo`) |
| 03 | Prestamos aprobados | 11 | Localiza el préstamo de esa aprobación (`prestamo_id_nuevo`), detalle, amortización y pagos, y descarga los dos reportes en PDF y en Excel |
| 04 | Pagos | 5 | Registra un pago sobre ese préstamo (`pago_id_nuevo`) y lo verifica |
| 05 | Resumen y auditoria | 2 | Indicadores del tablero con las series de sus gráficas (5 tipos de préstamo y 12 meses de recaudación) y bitácora, que ya contiene lo hecho arriba |
| 06 | Casos de error y validaciones | 19 | Los 19 casos negativos. No modifican datos |
| 07 | Autorizacion por rol | 7 | Matriz de permisos con los tokens de cajero, consulta y analista |
| 08 | Filtros de busqueda | 40 | Los criterios de los cinco listados y sus 6 casos de error. **Solo lectura**: no modifica nada y se puede repetir |

**El login va primero porque guarda el token.** La colección autentica con *bearer* `{{token}}` a
nivel de colección, y esa variable nace vacía: hasta que «Login ADMIN» la rellene, cualquier otra
petición responde **401**. Los cinco logins son las únicas peticiones con `auth: noauth`.

Las carpetas 02 a 04 están encadenadas por variables: la 03 busca el préstamo cuyo `solicitudId`
coincide con `solicitud_id_nueva`, y la 04 paga sobre el `prestamo_id_nuevo` que encontró la 03.
Ejecutar la 04 sin la 02 y la 03 deja esas variables vacías y la petición falla.

La **08 es la excepción**: no depende de ninguna carpeta anterior más que del login, porque sus
pruebas verifican el *contenido* de cada respuesta —que todo elemento devuelto cumple el criterio
pedido— y no un total fijo. Por eso sigue en verde tanto sobre los datos de demostración recién
cargados como después de que las carpetas 02 a 04 hayan añadido los suyos en esa misma ejecución.

## Ejecutar todo con el Collection Runner

1. Pase el ratón sobre la colección → menú **···** → **Run collection**. (También desde el botón
   **Runner** de la barra inferior, arrastrando la colección.)
2. En el panel derecho compruebe que:
   - **Environment** es «CHN - Local».
   - **Iterations** es `1` y **Delay** `0` ms.
   - Están marcadas *Save responses* y *Keep variable values*, para que los ids que guardan los
     scripts queden en el entorno al terminar.
   - El orden de las peticiones es el de la colección (no reordene ni desmarque carpetas: la 03 y
     la 04 dependen de la 02).
3. Pulse **Run CHN - Sistema de Prestamos**.

Resultado esperado: **105 peticiones y 324 aserciones, todas en verde**.

## Ejecutar con Newman

La colección es apta para integración continua. Desde la raíz del repositorio:

```bash
npm install -g newman

newman run postman/CHN-Prestamos.postman_collection.json \
  -e postman/CHN-Prestamos.postman_environment.json
```

Con informe HTML, o apuntando a otro entorno sin tocar los archivos:

```bash
newman run postman/CHN-Prestamos.postman_collection.json \
  -e postman/CHN-Prestamos.postman_environment.json \
  -r cli,html --reporter-html-export informe-newman.html

newman run postman/CHN-Prestamos.postman_collection.json \
  -e postman/CHN-Prestamos.postman_environment.json \
  --env-var baseUrl=http://mi-servidor:8081/api/v1
```

Sin instalar nada, con la imagen oficial en Docker (`host.docker.internal` porque la API corre en
el anfitrión, no en el contenedor):

```bash
docker run --rm -v "$PWD/postman:/etc/newman" \
  --add-host=host.docker.internal:host-gateway \
  postman/newman:alpine run CHN-Prestamos.postman_collection.json \
  -e CHN-Prestamos.postman_environment.json \
  --env-var baseUrl=http://host.docker.internal:8081/api/v1
```

O uniendo el contenedor de Newman a la red del propio `docker compose`, que evita depender de
`host.docker.internal` y es la forma más limpia en integración continua:

```bash
docker run --rm -v "$PWD/postman:/etc/newman" \
  --network chn-red \
  postman/newman:alpine run CHN-Prestamos.postman_collection.json \
  -e CHN-Prestamos.postman_environment.json \
  --env-var baseUrl=http://backend:8081/api/v1
```

Newman devuelve código de salida distinto de cero si alguna aserción falla, así que sirve tal cual
como paso de una tubería.

## Usuarios de demostración

Los cuatro comparten la contraseña `Chn2026*Demo`, definida por las variables `ADMIN_PASSWORD`,
`ANALISTA_PASSWORD`, `CAJERO_PASSWORD` y `CONSULTA_PASSWORD`. **Deben cambiarse fuera del examen.**

| Usuario | Rol | Alcance |
| --- | --- | --- |
| `admin` | ADMIN | Todo, incluido eliminar clientes y consultar la auditoría |
| `analista` | ANALISTA | Clientes y solicitudes; aprueba y rechaza. **No** registra pagos |
| `cajero` | CAJERO | Registra pagos y consulta. **No** crea clientes ni resuelve solicitudes |
| `consulta` | CONSULTA | Solo lectura |

## Datos de demostración disponibles

Los carga la migración `V900__datos_demo.sql` al arrancar, si `APP_DATOS_DEMO=true`:
**8 clientes, 10 solicitudes, 4 préstamos y 7 pagos**.

### Clientes

| id | Nombre | DPI | Teléfono |
| --- | --- | --- | --- |
| 1 | María José Ramírez López | 1985043210101 | 55012345 |
| 2 | Carlos Enrique Morales Chávez | 2456789010102 | 42315678 |
| 3 | Ana Lucía González Pérez | 1874563210103 | 31245567 |
| 4 | Jorge Alberto Tzoc Batz | 3012456780104 | 78451236 |
| 5 | Sofía Alejandra Castillo Herrera | 2789456120105 | 57894123 |
| 6 | Luis Fernando Ixcoy Marroquín | 1956784320106 | 41236789 |
| 7 | Gabriela María Sandoval Ríos | 2634781290107 | 36987412 |
| 8 | Rodrigo Andrés Villagrán Pineda | 3145672890108 | 54127896 |

### Solicitudes por estado

| id | Número | Cliente | Monto (Q) | Plazo | Tipo | Estado |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | SC-001-2026-000001-3 | 1 | 75,000.00 | 24 | PERSONAL | **APROBADA** |
| 2 | SC-001-2026-000002-1 | 2 | 450,000.00 | 60 | VEHICULAR | **APROBADA** |
| 3 | SC-001-2026-000003-9 | 3 | 120,000.00 | 36 | EDUCATIVO | **APROBADA** |
| 4 | SC-001-2026-000004-7 | 5 | 850,000.00 | 120 | HIPOTECARIO | **APROBADA** |
| 5 | SC-001-2026-000005-4 | 4 | 300,000.00 | 48 | EMPRESARIAL | **RECHAZADA** |
| 6 | SC-001-2026-000006-2 | 6 | 60,000.00 | 18 | PERSONAL | **RECHAZADA** |
| 7 | SC-001-2026-000007-0 | 7 | 95,000.00 | 30 | PERSONAL | **EN_PROCESO** |
| 8 | SC-001-2026-000008-8 | 8 | 1,200,000.00 | 180 | HIPOTECARIO | **EN_PROCESO** |
| 9 | SC-001-2026-000009-6 | 2 | 180,000.00 | 36 | EMPRESARIAL | **EN_PROCESO** |
| 10 | SC-001-2026-000010-4 | 3 | 55,000.00 | 24 | EDUCATIVO | **EN_PROCESO** |

Para aprobar o rechazar a mano use una **EN_PROCESO** (7 a 10). Las solicitudes 1 a 6 ya están
resueltas y devuelven **409** si se intenta resolverlas otra vez; la colección aprovecha ese hecho
en el caso de error correspondiente.

### Préstamos con su saldo

| id | Número | Cliente | Aprobado (Q) | Plazo | Tasa | Cuota (Q) | Total a pagar (Q) | Pagado (Q) | **Saldo (Q)** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | PR-001-2026-000001-9 | 1 | 70,000.00 | 24 | 14.50% | 3,377.46 | 81,059.06 | 13,509.84 | **67,549.22** |
| 2 | PR-001-2026-000002-7 | 2 | 450,000.00 | 60 | 11.00% | 9,784.09 | 587,045.42 | 19,568.18 | **567,477.24** |
| 3 | PR-001-2026-000003-5 | 3 | 110,000.00 | 36 | 13.25% | 3,719.59 | 133,905.43 | 9,298.98 | **124,606.45** |
| 4 | PR-001-2026-000004-3 | 5 | 800,000.00 | 120 | 9.75% | 10,461.62 | 1,255,394.28 | 0.00 | **1,255,394.28** |

Los cuatro están **VIGENTE**. El préstamo 1 sirve para probar consultas con historial de pagos; el
4, el caso sin ningún pago. El saldo es una columna calculada PERSISTED
(`monto_total_a_pagar - total_pagado`), así que nunca puede quedar desalineado.

### Series del tablero (`GET /resumen`)

Recién cargada la demostración, las dos series de las gráficas del tablero traen:

| `carteraPorTipo` (orden del catálogo) | Préstamos | Monto aprobado (Q) | Saldo pendiente (Q) | Recuperado (Q) |
| --- | --- | --- | --- | --- |
| `PERSONAL` | 1 | 70,000.00 | 67,549.22 | 13,509.84 |
| `HIPOTECARIO` | 1 | 800,000.00 | 1,255,394.28 | 0.00 |
| `VEHICULAR` | 1 | 450,000.00 | 567,477.24 | 19,568.18 |
| `EMPRESARIAL` | 0 | 0.00 | 0.00 | 0.00 |
| `EDUCATIVO` | 1 | 110,000.00 | 124,606.45 | 9,298.98 |

El monto aprobado es el capital desembolsado (`prestamos.monto_aprobado`), no el solicitado: por eso
el hipotecario suma 800,000.00 aunque la solicitud 4 pidió 850,000.00.

`recaudacionMensual` trae siempre 12 meses que terminan en el mes en curso. Los 7 pagos de la
demostración caen en `2026-03` (1 pago, 3,377.46), `2026-04` (2, 13,161.55), `2026-05` (3, 20,258.60)
y `2026-06` (1, 5,579.39), que suman los 42,377.00 de `totalRecuperado`; el resto de los meses viaja
en cero. Desde marzo de 2027 la ventana de 12 meses empieza a dejar fuera esos meses (desde junio de
2027, todos): las pruebas de la colección comprueban la forma de la serie, no estas cifras.

## Endpoints cubiertos y rol necesario

Prefijo `/api/v1`. Todos exigen JWT *Bearer* salvo el login. «Autenticado» significa cualquiera de
los cuatro roles.

| # | Método y ruta | Rol necesario | Éxito | Carpeta |
| --- | --- | --- | --- | --- |
| 1 | `POST /auth/login` | Público | 200 | 00 |
| 2 | `GET /auth/perfil` | Autenticado | 200 | 00 |
| 3 | `GET /clientes` | Autenticado | 200 | 01 |
| 4 | `POST /clientes` | ADMIN, ANALISTA | 201 | 01 |
| 5 | `GET /clientes/{id}` | Autenticado | 200 | 01 |
| 6 | `PUT /clientes/{id}` | ADMIN, ANALISTA | 200 | 01 |
| 7 | `DELETE /clientes/{id}` | **Solo ADMIN** | 204 | 01 |
| 8 | `GET /clientes/{id}/solicitudes` | Autenticado | 200 | 01 |
| 9 | `GET /clientes/{id}/prestamos` | Autenticado | 200 | 01 |
| 10 | `GET /solicitudes` | Autenticado | 200 | 02 |
| 11 | `POST /solicitudes` | ADMIN, ANALISTA | 201 | 02 |
| 12 | `GET /solicitudes/{id}` | Autenticado | 200 | 02 |
| 13 | `POST /solicitudes/{id}/aprobar` | ADMIN, ANALISTA | 200 | 02 |
| 14 | `POST /solicitudes/{id}/rechazar` | ADMIN, ANALISTA | 200 | 02 |
| 15 | `POST /solicitudes/simulacion` | ADMIN, ANALISTA | 200 | 02 |
| 16 | `GET /prestamos` | Autenticado | 200 | 03 |
| 17 | `GET /prestamos/{id}` | Autenticado | 200 | 03 |
| 18 | `GET /prestamos/{id}/amortizacion` | Autenticado | 200 | 03 |
| 19 | `GET /prestamos/{id}/pagos` | Autenticado | 200 | 03 |
| 20 | `GET /prestamos/{id}/amortizacion.{pdf\|xlsx}` | Autenticado | 200 (archivo) | 03 |
| 21 | `GET /prestamos/{id}/pagos.{pdf\|xlsx}` | Autenticado | 200 (archivo) | 03 |
| 22 | `GET /pagos` | Autenticado | 200 | 04 |
| 23 | `POST /pagos` | ADMIN, CAJERO | 201 | 04 |
| 24 | `GET /resumen` | Autenticado | 200 | 05 |
| 25 | `GET /auditoria` | **Solo ADMIN** | 200 | 05 |

Los cinco listados aceptan `pagina` (desde 0) y `tamano` (**de 1 a 100**; fuera de ese rango,
**400**), más sus criterios de búsqueda, que se detallan en la sección siguiente.

### Descarga de reportes (carpeta 03)

Las dos descargas generan el archivo en el servidor y la extensión viaja en la ruta, de modo que el
nombre que propone el navegador ya trae el formato. La carpeta 03 pide los cuatro archivos del
préstamo 1:

| Petición | Ruta | `Content-Type` | Nombre del archivo |
| --- | --- | --- | --- |
| Plan de amortización en PDF | `/prestamos/{id}/amortizacion.pdf` | `application/pdf` | `plan-amortizacion-<numeroPrestamo>.pdf` |
| Plan de amortización en Excel | `/prestamos/{id}/amortizacion.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `plan-amortizacion-<numeroPrestamo>.xlsx` |
| Historial de pagos en PDF | `/prestamos/{id}/pagos.pdf` | `application/pdf` | `historial-pagos-<numeroPrestamo>.pdf` |
| Historial de pagos en Excel | `/prestamos/{id}/pagos.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `historial-pagos-<numeroPrestamo>.xlsx` |

Las pruebas comprueban el `Content-Type` exacto, que `Content-Disposition` lo entregue como
`attachment` con el nombre y la extensión correctos, `Cache-Control: no-store` (el archivo lleva
datos personales del cliente), que el cuerpo no esté vacío y la **firma de los primeros bytes**:
`%PDF-` en los PDF y `PK` (`50 4B 03 04`, un ZIP) en los `.xlsx`. Los bytes se leen de
`pm.response.stream`, que Newman entrega como `Buffer`.

Cualquier otra extensión responde **400 VALIDACION** con los formatos admitidos en el mensaje, y un
préstamo inexistente, **404 NO_ENCONTRADO**; ambos en JSON y sin archivo adjunto (carpeta 06).
Esas respuestas traen `Content-Disposition: inline;filename=f.txt`: lo agrega Spring a todo JSON
servido desde una URL con extensión, como defensa contra la descarga de archivos reflejados (RFD).
Por eso la prueba no exige que falte la cabecera, sino que no diga `attachment`. Para guardar el
archivo desde Postman use **Save Response → Save to a file**.

## Filtros de búsqueda (carpeta 08)

La carpeta 08 ejercita los criterios de los cinco listados con **40 peticiones**: 34 de búsqueda y
6 casos de error. Es la única carpeta enteramente de **solo lectura**, así que se puede repetir las
veces que haga falta y en cualquier momento.

Tres reglas valen para todos los listados:

- **Todo criterio es opcional** y se acumula con los demás mediante `AND`. El filtrado ocurre
  **siempre en la base de datos**, con parámetros nombrados y el patrón `:criterio IS NULL OR
  <condición>` en JPQL: así el conteo y la paginación siguen siendo exactos y no hay ni un punto
  donde se concatene SQL.
- Los **rangos son inclusivos en ambos extremos**. Sobre columnas `DATETIME2` —fecha de registro
  del cliente, fecha de solicitud, fecha de pago y fecha de la bitácora— el límite superior se
  traduce internamente a `< hasta + 1 día`, de modo que un registro de las **23:50** del último día
  del rango queda **dentro**. Las columnas `DATE` (nacimiento, desembolso, vencimiento) se comparan
  directamente.
- El texto libre `busqueda` es insensible a mayúsculas **y a tildes**, porque la base usa la
  colación `Modern_Spanish_CI_AI`: «ramirez» encuentra «Ramírez».

### Criterios por endpoint

Todos son opcionales y se suman a `pagina` y `tamano`.

| Endpoint | Criterios |
| --- | --- |
| `GET /clientes` | `busqueda`, `nacimientoDesde`, `nacimientoHasta`, `creacionDesde`, `creacionHasta`, `activo` |
| `GET /solicitudes` | `busqueda`, `clienteId`, `estado`, `tipoPrestamo`, `montoMinimo`, `montoMaximo`, `plazoMinimo`, `plazoMaximo`, `fechaDesde`, `fechaHasta` |
| `GET /prestamos` | `busqueda`, `clienteId`, `estado`, `montoMinimo`, `montoMaximo`, `saldoMinimo`, `saldoMaximo`, `desembolsoDesde`, `desembolsoHasta`, `vencimientoDesde`, `vencimientoHasta` |
| `GET /pagos` | `busqueda`, `prestamoId`, `clienteId`, `montoMinimo`, `montoMaximo`, `fechaDesde`, `fechaHasta`, `usuarioRegistro` |
| `GET /auditoria` | `busqueda`, `usuario`, `accion`, `entidad`, `fechaDesde`, `fechaHasta` |

Qué abarca el texto libre `busqueda` en cada listado:

| Listado | `busqueda` encuentra por |
| --- | --- |
| Clientes | Nombre, apellido, nombre completo, DPI, correo y teléfono |
| Solicitudes | Número de solicitud, destino, y nombre o DPI del cliente |
| Préstamos | Número de préstamo, número de la solicitud de origen, y nombre o DPI del cliente |
| Pagos | Número de recibo, número de préstamo, y nombre o DPI del cliente |
| Auditoría | Usuario, detalle e identificador de la entidad afectada |

Dos matices que conviene conocer:

- **El saldo pendiente de los préstamos.** `saldo_pendiente` es una columna calculada **PERSISTED**
  (`monto_total_a_pagar - total_pagado`) y **no está mapeada en JPA**, porque es de solo lectura. El
  rango `saldoMinimo` / `saldoMaximo` se resuelve por tanto con la **expresión**
  `(montoTotalAPagar - totalPagado)`, y se resuelve en la base: es el filtro de **gestión de
  cobro**, el que arma las campañas de recuperación por banda de saldo vivo.
- **`accion` y `entidad` de la auditoría se comparan por valor exacto**, porque salen de un catálogo
  cerrado; `usuario`, `usuarioRegistro` y el texto libre, por coincidencia parcial. Por eso los
  registros del *trigger* `tr_clientes_auditoria_delete`, que guardan la entidad como `clientes`
  (el nombre de la tabla), no se mezclan con los `CLIENTE` de la aplicación.

### Peticiones de la carpeta

| Listado | Peticiones |
| --- | --- |
| Clientes | Texto libre por apellido, DPI parcial, teléfono, rango de nacimiento, rango de registro, solo activos, solo inactivos y una combinación de tres criterios (8) |
| Solicitudes | Por número, destino, cliente, estado, tipo de préstamo, rango de monto, rango de plazo, rango de fecha y una combinación de tres criterios (9) |
| Préstamos | Por número de préstamo, número de la solicitud de origen, estado, rango de monto aprobado, **rango de saldo pendiente**, rango de desembolso y rango de vencimiento (7) |
| Pagos | Por número de recibo, cliente, rango de monto, rango de fecha y usuario que registró (5) |
| Auditoría | Por acción, entidad, usuario, texto libre y rango de fecha (5) |
| Casos de error | Los 6 de la tabla siguiente |

### Casos de error de los filtros

Los seis esperan **400** con código `VALIDACION`. Un rango incoherente es un **error de quien
pregunta**, no una búsqueda sin resultados: devolver una lista vacía con 200 dejaría al usuario
creyendo que no hay datos en ese intervalo.

| # | Caso | Petición | Esperado | Código |
| --- | --- | --- | --- | --- |
| 1 | Rango de fechas invertido | `/clientes?nacimientoDesde=1995-01-01&nacimientoHasta=1990-01-01` | **400** | `VALIDACION` |
| 2 | Monto mínimo mayor que el máximo | `/solicitudes?montoMinimo=200000&montoMaximo=50000` | **400** | `VALIDACION` |
| 3 | Plazo mínimo mayor que el máximo | `/solicitudes?plazoMinimo=36&plazoMaximo=12` | **400** | `VALIDACION` |
| 4 | Fecha mal formada (`31-01-2026`) | `/clientes?nacimientoDesde=31-01-2026` | **400** | `VALIDACION` |
| 5 | Tipo de préstamo inexistente | `/solicitudes?tipoPrestamo=INVENTADO` | **400** | `VALIDACION` |
| 6 | Tamaño de página 500 con filtro | `/prestamos?estado=VIGENTE&tamano=500` | **400** | `VALIDACION` |

Los mensajes no son genéricos y las pruebas lo comprueban: el caso 4 indica el parámetro que falló
**y el formato esperado** (`aaaa-MM-dd`), el 5 enumera los tipos admitidos y el 6 señala el campo
`tamano` en el arreglo `errores`. El límite de 100 elementos por página se aplica igual con filtros
que sin ellos, en los cinco listados: es lo que impide que un criterio muy amplio se convierta en
una descarga de la tabla entera.

### La misma cobertura desde la línea de comandos

`tools/pruebas/prueba-filtros.sh` recorre estos mismos criterios sin Postman ni Newman, con
**56 comprobaciones** sobre el sistema en ejecución:

```bash
docker compose up -d
bash tools/pruebas/prueba-filtros.sh
```

No fija los totales a mano: los lee de la propia API antes de empezar, así que sigue siendo válido
aunque el juego de datos haya crecido. Termina con código de salida 0 solo si todo pasa, igual que
Newman. Es la alternativa rápida cuando no se quiere levantar Postman solo para comprobar que los
filtros siguen respondiendo.

## Casos de error cubiertos (carpeta 06)

Formato uniforme en todas las respuestas de error, con `errores` por campo cuando falla la
validación de formato:

```json
{"timestamp":"...","estado":409,"codigo":"REGLA_NEGOCIO","mensaje":"...",
 "ruta":"/api/v1/pagos","errores":[{"campo":"monto","mensaje":"..."}]}
```

| # | Caso | Esperado | Código |
| --- | --- | --- | --- |
| 1 | Login con contraseña incorrecta | **401** | `AUTENTICACION` |
| 2 | Petición sin token | **401** | `AUTENTICACION` |
| 3 | Token inválido | **401** | `AUTENTICACION` |
| 4 | DPI de 12 dígitos | **400** | `VALIDACION` |
| 5 | Cliente menor de edad | **400** | `VALIDACION` |
| 6 | Correo con formato inválido | **400** | `VALIDACION` |
| 7 | DPI duplicado (1985043210101) | **409** | `DUPLICADO` |
| 8 | Cliente inexistente (id 99999) | **404** | `NO_ENCONTRADO` |
| 9 | Monto por debajo del mínimo (Q500) | **400** | `VALIDACION` |
| 10 | Plazo de 3 meses | **400** | `VALIDACION` |
| 11 | Estado inexistente en el filtro | **400** | `VALIDACION` |
| 12 | Tamaño de página 500 | **400** | `VALIDACION` |
| 13 | Rechazo con motivo de menos de 10 caracteres | **400** | `VALIDACION` |
| 14 | Aprobar la solicitud 1, ya resuelta | **409** | `REGLA_NEGOCIO` |
| 15 | Pago de monto 0 | **400** | `VALIDACION` |
| 16 | Pago mayor al saldo del préstamo 1 | **409** | `REGLA_NEGOCIO` |
| 17 | Pago sobre el préstamo 99999 | **404** | `NO_ENCONTRADO` |
| 18 | Reporte en formato `.docx` (solo se generan `pdf` y `xlsx`) | **400** | `VALIDACION` |
| 19 | Reporte en PDF del préstamo 99999 | **404** | `NO_ENCONTRADO` |

Códigos del contrato: `VALIDACION` (400), `AUTENTICACION` (401), `ACCESO_DENEGADO` (403),
`NO_ENCONTRADO` (404), `REGLA_NEGOCIO` (409), `DUPLICADO` (409), `LIMITE_PETICIONES` (429) y
`ERROR_INTERNO` (500). Los 500 no revelan detalles internos ni traza.

Dos matices que los casos 5 y 4 dejan a la vista: el **menor de edad** pasa la validación de formato
(la fecha es pasada y válida) y lo rechaza la **regla del dominio**, con mensaje explicativo y sin
arreglo `errores`; el **DPI de 12 dígitos** lo corta Bean Validation en el DTO y sí detalla el campo.

## Autorización por rol (carpeta 07)

| Caso | Token | Esperado |
| --- | --- | --- |
| El cajero no puede crear clientes | `token_cajero` | **403** `ACCESO_DENEGADO` |
| El cajero no puede ver la auditoría | `token_cajero` | **403** `ACCESO_DENEGADO` |
| El cajero **sí** puede registrar un pago | `token_cajero` | **201** |
| El usuario de consulta no puede registrar pagos | `token_consulta` | **403** `ACCESO_DENEGADO` |
| El usuario de consulta no puede eliminar clientes | `token_consulta` | **403** `ACCESO_DENEGADO` |
| El analista no puede registrar pagos | `token_analista` | **403** `ACCESO_DENEGADO` |
| El usuario de consulta **sí** puede descargar reportes (historial de pagos en PDF) | `token_consulta` | **200** |

Cada petición lleva su **propio *bearer*** en lugar de heredar el de administrador. La autorización
se aplica con `@PreAuthorize` **antes** de entrar al método, así que el 403 llega sin que la
operación toque la base: por eso la prueba de borrado puede apuntar al id 99999 sin riesgo alguno y
responde 403, no 404.

Que el analista no pueda cobrar es una **separación de funciones** deliberada: quien autoriza el
crédito no es quien recibe el dinero en caja.

Las descargas de reportes, en cambio, solo exigen `@PreAuthorize("isAuthenticated()")`: son de
lectura, así que hasta el rol **CONSULTA** las obtiene. Lo que sí queda es el rastro: cada descarga
se registra en la bitácora a nombre de quien la pidió.

## Variables del entorno

| Variable | Valor inicial | Notas |
| --- | --- | --- |
| `baseUrl` | `http://localhost:8081/api/v1` | Cámbiela para apuntar a otro entorno |
| `usuario_admin` / `usuario_analista` / `usuario_cajero` / `usuario_consulta` | `admin` / `analista` / `cajero` / `consulta` | Cuentas de demostración |
| `contrasena` | `Chn2026*Demo` | Marcada **secret** |
| `token` | *(vacía)* | **secret.** La rellena «Login ADMIN»; la usa el *bearer* de la colección |
| `token_analista` / `token_cajero` / `token_consulta` | *(vacías)* | **secret.** Las rellenan sus logins; las usa la carpeta 07 |
| `cliente_id` | `1` | María José Ramírez López |
| `cliente_dpi` | `1985043210101` | DPI del cliente 1, para provocar el 409 de duplicado |
| `solicitud_en_proceso_id` | `7` | SC-001-2026-000007-0, EN_PROCESO |
| `solicitud_resuelta_id` | `1` | SC-001-2026-000001-3, ya aprobada: da 409 |
| `prestamo_id` | `1` | PR-001-2026-000001-9, vigente y con pagos |
| `prestamo_sin_pagos_id` | `4` | PR-001-2026-000004-3, vigente y sin pagos |
| `cliente_id_nuevo` | *(vacía)* | La rellena «Crear cliente»; la vacía «Eliminar cliente» |
| `solicitud_id_nueva` | *(vacía)* | La rellena «Crear solicitud de prestamo» |
| `prestamo_id_nuevo` | *(vacía)* | La rellena «Localizar el prestamo generado» |
| `pago_id_nuevo` | *(vacía)* | La rellena «Registrar pago» |
| `solicitud_id_rechazo` | *(vacía)* | Auxiliar: la solicitud creada para probar el rechazo |
| `dpi_nuevo` / `correo_nuevo` | *(vacías en Postman)* | Auxiliares: par único que genera el script previo del alta. En Insomnia traen un valor literal, porque allí no hay scripts |

Las cuatro últimas filas son variables **auxiliares** que rellenan los scripts; no hace falta
tocarlas a mano en Postman.

Sobre `dpi_nuevo` y `correo_nuevo`: el DPI y el correo tienen restricción **UNIQUE** en la base, así
que «Crear cliente» genera un par nuevo en cada ejecución desde su script previo. Sin eso, la
segunda pasada devolvería 409 DUPLICADO. El resto de los campos del cuerpo son literales y
realistas.

### Variables de la carpeta 08

Estas diecisiete son **valores de ejemplo** de los filtros. Cambiarlas cambia lo que busca la
carpeta 08 sin tocar ninguna petición, y las pruebas se adaptan porque leen el mismo valor que viaja
en la consulta. Están también como variables de la colección, de modo que la carpeta funciona aunque
no haya entorno activo.

| Variable | Valor inicial | Para qué |
| --- | --- | --- |
| `cliente_apellido` | `Ramirez` | Texto libre de clientes; encuentra «Ramírez» pese a la tilde |
| `cliente_dpi_parcial` | `1985043` | Fragmento del DPI del cliente 1, para la búsqueda parcial |
| `cliente_telefono` | `55012345` | Teléfono del cliente 1, que el mismo texto libre encuentra |
| `solicitud_numero` | `SC-001-2026-000003-9` | Correlativo único: el filtro debe devolver exactamente una |
| `prestamo_numero` | `PR-001-2026-000001-9` | Correlativo único del préstamo |
| `recibo_numero` | `RC-001-2026-000001-4` | Correlativo único del recibo |
| `usuario_pagos` | `cajero` | Usuario que registró los siete pagos de demostración |
| `nacimiento_desde` / `nacimiento_hasta` | `1990-01-01` / `1996-12-31` | Rango de fecha de nacimiento |
| `creacion_desde` / `creacion_hasta` | `2026-01-01` / `2026-01-31` | Rango de fecha de alta del expediente |
| `fecha_desde` / `fecha_hasta` | `2026-01-01` / `2026-12-31` | Rango de fecha para pagos y auditoría |
| `monto_minimo` / `monto_maximo` | `50000` / `200000` | Banda de monto |
| `saldo_minimo` / `saldo_maximo` | `60000` / `130000` | Banda de saldo pendiente (gestión de cobro) |

## Lo que la colección modifica

Las carpetas **01, 06, 07 y 08 no dejan rastro**: la 01 elimina al final el cliente que creó, los
casos de la 06 fallan antes de persistir y la 08 es enteramente de lectura —sus 40 peticiones son
`GET`, así que se puede repetir sin límite.

Las carpetas **02 a 04 sí dejan datos**. Cada ejecución completa añade, sobre el cliente de
demostración 1: dos solicitudes (una aprobada y una rechazada), el préstamo de la aprobada y dos
pagos (uno de la carpeta 04 y otro del cajero en la 07). Es intencionado: así ninguna ejecución
gasta las solicitudes EN_PROCESO de demostración ni altera los saldos de los préstamos 1 a 4, y la
colección se puede repetir indefinidamente. Para volver al estado inicial:

```bash
docker compose down -v && docker compose up -d --build
```

Las descargas de reportes —cuatro en la 03 y una en la 07— no crean datos de negocio, pero cada una
deja un registro `REPORTE_DESCARGADO` en la bitácora, como corresponde a una exportación de datos
de clientes. Los dos casos de reportes de la 06 fallan antes de generar nada y no dejan registro.

### Dos comportamientos que conviene conocer

- **Limitador de `/auth/login`: 10 peticiones por IP cada 5 minutos** (ventana deslizante). Una
  ejecución completa gasta 5 (los cuatro logins más el caso de contraseña incorrecta), así que
  **dos ejecuciones seguidas dentro de la misma ventana agotan el margen**: el caso «Login con
  contraseña incorrecta» puede responder **429 LIMITE_PETICIONES** en lugar de 401. No es un fallo
  de la colección, es el limitador funcionando. Espere 5 minutos y vuelva a ejecutarla.
- **Bloqueo de cuenta: 5 intentos fallidos → 15 minutos.** Un login correcto reinicia el contador, y
  como la carpeta 00 se ejecuta antes, el único fallo por ejecución nunca llega a bloquear la
  cuenta. Ejecutar en bucle solo el caso de contraseña incorrecta sí la bloquea.

## Qué comprueban las pruebas

Las 105 peticiones llevan un script con `pm.test` y `pm.expect`: **324 aserciones** que verifican el
código HTTP esperado y, además, el contenido. No solo miran el 200. Entre otras cosas comprueban:

- La **cuota del sistema francés**: Q100,000.00 a 12 meses al 12% anual da **Q8,884.88**, cada cuota
  reparte capital más intereses y la última deja el saldo exactamente en **0.00**.
- La invariante `saldoPendiente = montoTotalAPagar - totalPagado` en cada préstamo devuelto, y
  `saldoPosterior = saldoAnterior - monto` en cada recibo.
- Los correlativos `SC-AAA-AAAA-NNNNNN-D`, `PR-AAA-AAAA-NNNNNN-D` y `RC-AAA-AAAA-NNNNNN-D`.
- Que una solicitud nace **EN_PROCESO** y sin resolución, que al aprobar el monto no excede el
  solicitado, y que un rechazo no aprueba monto alguno.
- Que los filtros por estado devuelven **solo** ese estado, y que el `tamano` devuelto no excede el
  solicitado.
- La **forma de las series del tablero** en `GET /resumen`: `carteraPorTipo` es un arreglo con los
  **5 tipos** de préstamo en el orden del catálogo (`PERSONAL`, `HIPOTECARIO`, `VEHICULAR`,
  `EMPRESARIAL`, `EDUCATIVO`); `recaudacionMensual` es un arreglo de **12 meses** con `periodo`
  `AAAA-MM` (`/^\d{4}-\d{2}$/`), contiguos y en orden ascendente, cuyo `anio` y `mes` coinciden con
  el `periodo`; y todas las cantidades y montos de ambas series son números mayores o iguales a
  cero. Son cuatro aserciones que no fijan cifras, así que siguen en verde con cualquier volumen de
  datos.
- Que **todo elemento devuelto por un filtro cumple el criterio pedido**: cada fecha dentro de su
  rango, cada monto dentro de su banda, cada saldo dentro de la suya, y los tres criterios a la vez
  en las peticiones combinadas. Es una comprobación de contenido, no de totales, así que no se
  rompe porque el sistema tenga más datos de los que trajo la demostración.
- Que un rango invertido, un mínimo mayor que el máximo, una fecha mal escrita o un tipo de préstamo
  inexistente responden **400 VALIDACION** con un mensaje que **dice qué falló**: el parámetro y el
  formato esperado, o la lista de valores admitidos.
- Que los **reportes descargados son archivos de verdad**: `Content-Type` exacto, `Content-Disposition`
  como adjunto con el nombre `plan-amortizacion-…` o `historial-pagos-…` y la extensión pedida, y
  la firma binaria del cuerpo (`%PDF-` o `PK`). Una extensión no soportada o un préstamo inexistente
  responden con el error uniforme en JSON y sin archivo.
- Que el mensaje de credenciales inválidas es **genérico** y no permite enumerar usuarios, y que el
  perfil no expone el hash de la contraseña.
- Que la bitácora registra usuario, acción, entidad y fecha, con dirección IP en las operaciones
  hechas por HTTP. Los registros del trigger `tr_clientes_auditoria_delete` (usuario `trigger_bd`)
  no llevan IP, porque los escribe la base de datos y no una petición: las pruebas lo contemplan.
- Que la búsqueda de clientes es **insensible a tildes** por la colación de SQL Server, de modo que
  «Ramirez» encuentra también «Ramírez».
