# Sistema de Gestión de Préstamos Bancarios — CHN

Examen práctico **Analista Programador** — Crédito Hipotecario Nacional de Guatemala.

Aplicación web para administrar el ciclo completo de un préstamo bancario:
clientes, solicitudes, resolución (aprobación o rechazo), préstamos aprobados,
pagos en efectivo y saldo pendiente.

| | |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.4 · Spring Data JPA (Hibernate) · Spring Security · Flyway |
| **Base de datos** | SQL Server 2022 · Transact-SQL (tablas, vistas, funciones, procedimientos, secuencias, trigger) |
| **Frontend** | React 18 · Vite 5 · React Router 6 · componentes propios reutilizables |
| **Arquitectura** | Hexagonal (puertos y adaptadores), verificada con pruebas ArchUnit |
| **Despliegue** | Docker + Docker Compose (un solo comando) |

---

## 1. Puesta en marcha

Requisito único: **Docker Desktop** (o Docker Engine + Compose v2) en ejecución.
No hace falta instalar Java, Maven, Node ni SQL Server: todo se compila dentro
de los contenedores.

```bash
git clone https://github.com/Ranca2609/Examen_Practico_CHN.git && cd Examen_Practico_CHN
docker compose up -d --build
```

La primera ejecución descarga las imágenes base y compila el proyecto
(aproximadamente 5–8 minutos). Las siguientes arrancan en segundos.

Cuando los contenedores estén sanos:

| Recurso | URL |
|---|---|
| **Aplicación web** | <http://localhost:8080> |
| Documentación interactiva de la API (Swagger UI) | <http://localhost:8081/swagger-ui.html> |
| Especificación OpenAPI | <http://localhost:8081/v3/api-docs> |
| Estado del backend | <http://localhost:8081/actuator/health> |
| Base de datos (SQL Server) | `localhost:1433` |

### Usuarios de demostración

Se crean automáticamente al arrancar, con la contraseña cifrada en BCrypt.
La contraseña de todos es **`Chn2026*Demo`**.

| Usuario | Rol | Puede |
|---|---|---|
| `admin` | Administrador | Todo, incluido eliminar clientes y ver la bitácora de auditoría |
| `analista` | Analista de crédito | Gestionar clientes y solicitudes, aprobar y rechazar |
| `cajero` | Cajero | Registrar pagos en efectivo y consultar |
| `consulta` | Consulta | Solo lectura |

> Son credenciales de examen. En un entorno real deben definirse por variables
> de entorno (ver [`.env.example`](.env.example)) y rotarse tras el primer acceso.

### Comprobar el estado

```bash
docker compose ps
```

```bash
docker compose logs -f backend
```

### Detener y limpiar

```bash
docker compose down
```

```bash
docker compose down -v
```

El segundo comando elimina también el volumen de la base de datos, de modo que
el siguiente arranque vuelve a crear el esquema y los datos de prueba.

---

## 2. Qué resuelve el sistema

### Gestión de clientes
- Alta con nombre, apellido, número de identificación (DPI), fecha de
  nacimiento, dirección, correo electrónico y teléfono.
- Listado paginado con búsqueda por nombre, DPI o correo, incluyendo la
  información de contacto.
- Edición de los datos de un cliente existente.
- Eliminación del cliente **y de todas sus solicitudes, préstamos y pagos
  asociados**, en una sola transacción y con orden explícito de borrado.

### Solicitudes de préstamo
- Registro de la solicitud con monto, plazo, tasa, tipo de préstamo, destino e
  ingreso mensual declarado.
- Simulador previo: cuota mensual, total de intereses, monto total y evaluación
  de la capacidad de pago del cliente.
- Listado filtrable por cliente y por estado (**en proceso**, **aprobada**,
  **rechazada**).
- Aprobación o rechazo con registro de la resolución: fecha, usuario, monto,
  plazo y tasa aprobados, y motivo.

### Préstamos aprobados y pagos
- Al aprobar una solicitud se genera automáticamente el préstamo con su cuota
  mensual y su plan de amortización (sistema francés).
- Listado de préstamos por cliente con el estado de pago y el avance.
- Registro de pagos en efectivo con número de recibo, saldo anterior y
  posterior, y comprobante imprimible.
- **Cálculo y visualización del saldo pendiente** de cada préstamo; al llegar a
  cero el préstamo pasa a *liquidado*.

### Transversal
- Autenticación con JWT y autorización por rol.
- Tablero con los indicadores de la cartera y cuatro gráficas propias, sin librerías:
  solicitudes por estado, porcentaje recuperado, recaudación de los últimos 12 meses y
  cartera por tipo de préstamo. Los datos salen de vistas Transact-SQL.
- Bitácora de auditoría de las operaciones sensibles.
- Validación en el dominio, en la API y en la interfaz.

---

## 3. Documentación

| Documento | Contenido |
|---|---|
| [`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md) | Guía paso a paso con 25 capturas reales del sistema y los pasos señalizados sobre cada pantalla |
| [`docs/MANUAL_TECNICO.md`](docs/MANUAL_TECNICO.md) | Arquitectura, modelo de datos, API, reglas de negocio, seguridad, despliegue y pruebas |
| [`database/README.md`](database/README.md) | Scripts Transact-SQL, orden de ejecución y diccionario de datos |
| [`database/diagrama-entidad-relacion.md`](database/diagrama-entidad-relacion.md) | Diagrama entidad-relación en Mermaid y DBML, con el diccionario de datos |
| [`docs/img/diagrama-entidad-relacion.svg`](docs/img/diagrama-entidad-relacion.svg) | El mismo diagrama como imagen vectorial, para imprimir o insertar en un informe |
| [`postman/README.md`](postman/README.md) | Colecciones de Postman e Insomnia: 105 peticiones y 324 aserciones sobre los 25 endpoints, incluidas las descargas de reportes en PDF y Excel, más los casos de error, de permisos y de filtros |
| [`docs/img/README.md`](docs/img/README.md) | Cómo se generan las capturas del manual recorriendo el sistema real |

### Comprobación rápida de la API

Con el entorno levantado, esta prueba de humo recorre los 23 endpoints y verifica
el código HTTP de cada caso, incluidos los errores y la matriz de permisos:

```bash
bash tools/pruebas/prueba-api.sh
```

---

## 4. Estructura del repositorio

```
Examen_Practico_CHN/
├── docker-compose.yml          Orquestación completa del entorno
├── .env.example                Variables de entorno y secretos (plantilla)
│
├── database/                   Transact-SQL — única fuente de verdad del esquema
│   ├── 00_crear_base_datos.sql     Creación de la base y del usuario de aplicación
│   ├── migration/                  Migraciones que aplica Flyway (esquema, objetos, parámetros)
│   ├── demo/                       Juego de datos de prueba
│   └── init/                       Guion de inicialización usado por Docker Compose
│
├── backend/                    API REST — arquitectura hexagonal
│   └── src/main/java/gt/gob/chn/prestamos/
│       ├── domain/                 Núcleo de negocio (sin dependencias de framework)
│       ├── application/            Casos de uso y transacciones
│       └── infrastructure/         Adaptadores web, persistencia, seguridad y configuración
│
├── frontend/                   Aplicación web React + Vite
│   └── src/
│       ├── dominio/                Reglas y formatos puros
│       ├── aplicacion/             Contextos y hooks (casos de uso de la interfaz)
│       ├── infraestructura/        Adaptadores HTTP y de sesión
│       └── ui/                     Componentes reutilizables, layout y pantallas
│
├── postman/                    Colecciones de pruebas de la API
├── docs/                       Manuales de usuario y técnico
└── tools/capturas/             Generador automático de las capturas del manual
```

---

## 5. Desarrollo local sin Docker

Solo necesario para trabajar en el código; para evaluar el sistema basta con
Docker Compose.

**Base de datos** — levantar únicamente el motor y su inicialización:

```bash
docker compose up -d sqlserver db-init
```

**Backend** (Java 21 y Maven):

```bash
cd backend && mvn spring-boot:run
```

**Frontend** (Node 20):

```bash
cd frontend && npm install && npm run dev
```

El servidor de desarrollo queda en <http://localhost:5173> y redirige `/api`
al backend en el puerto 8081.

**Pruebas del backend**:

```bash
cd backend && mvn test
```

---

## 6. Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `db-init` termina con error de conexión | SQL Server necesita ~40 s en el primer arranque y al menos 2 GB de RAM asignados a Docker. Reintentar con `docker compose up -d db-init`. |
| El backend reinicia en bucle | Revisar `docker compose logs backend`. Lo habitual es que `db-init` no haya terminado; el servicio ya declara la dependencia, pero un volumen corrupto se resuelve con `docker compose down -v`. |
| Puerto 8080, 8081 o 1433 ocupado | Cambiar `WEB_PUERTO_HOST`, `API_PUERTO_HOST` o `DB_PUERTO_HOST` en el archivo `.env`. |
| La web carga pero la API responde 401 | El token expiró (8 h por defecto). Cerrar sesión y volver a entrar. |
| Quiero la base sin datos de prueba | Poner `APP_DATOS_DEMO=false` en `.env` y recrear con `docker compose down -v && docker compose up -d`. |

---

## 7. Licencia y autoría

Desarrollado como examen práctico para el Crédito Hipotecario Nacional de
Guatemala. Código de uso académico y evaluativo.
