# Manual técnico — Sistema de Gestión de Préstamos Bancarios (CHN)

Examen práctico **Analista Programador** · Crédito Hipotecario Nacional de Guatemala
Versión del artefacto: **1.0.0** · Motor: **SQL Server 2022** · Moneda: **GTQ** · Zona horaria: **America/Guatemala**

---

## Índice

1. [Introducción](#1-introducción)
2. [Stack tecnológico](#2-stack-tecnológico)
3. [Arquitectura](#3-arquitectura)
   - [3.1 La arquitectura hexagonal aplicada](#31-la-arquitectura-hexagonal-aplicada)
   - [3.2 Diagrama de bloques](#32-diagrama-de-bloques)
   - [3.3 Responsabilidades por capa y clases reales](#33-responsabilidades-por-capa-y-clases-reales)
   - [3.4 Verificación automática con ArchUnit](#34-verificación-automática-con-archunit)
   - [3.5 Decisiones de diseño, justificación y contrapartida](#35-decisiones-de-diseño-justificación-y-contrapartida)
   - [3.6 Diagramas de secuencia](#36-diagramas-de-secuencia)
4. [Modelo de datos](#4-modelo-de-datos)
   - [4.1 Diccionario de datos](#41-diccionario-de-datos)
   - [4.2 Diagrama entidad-relación](#42-diagrama-entidad-relación)
   - [4.3 Objetos programables Transact-SQL](#43-objetos-programables-transact-sql)
   - [4.4 Estrategia de migraciones con Flyway](#44-estrategia-de-migraciones-con-flyway)
   - [4.5 Diagramas de estados](#45-diagramas-de-estados)
5. [API REST](#5-api-rest)
   - [5.1 Catálogo de endpoints](#51-catálogo-de-endpoints)
   - [5.2 Filtros de búsqueda de los listados](#52-filtros-de-búsqueda-de-los-listados)
   - [5.3 Ejemplos de petición y respuesta](#53-ejemplos-de-petición-y-respuesta)
   - [5.4 Formato de error y códigos](#54-formato-de-error-y-códigos)
   - [5.5 Documentación interactiva y pruebas](#55-documentación-interactiva-y-pruebas)
6. [Reglas de negocio](#6-reglas-de-negocio)
   - [6.1 Tabla de reglas, implementación y prueba](#61-tabla-de-reglas-implementación-y-prueba)
   - [6.2 La fórmula de amortización](#62-la-fórmula-de-amortización)
7. [Seguridad de la información](#7-seguridad-de-la-información)
8. [Frontend](#8-frontend)
9. [Despliegue](#9-despliegue)
10. [Entorno de desarrollo local sin Docker](#10-entorno-de-desarrollo-local-sin-docker)
11. [Pruebas](#11-pruebas)
12. [Operación y mantenimiento](#12-operación-y-mantenimiento)
13. [Solución de problemas](#13-solución-de-problemas)
14. [Estructura del repositorio](#14-estructura-del-repositorio)

---

## 1. Introducción

### Propósito

El sistema administra el ciclo de vida completo de un préstamo bancario: el registro del
cliente, la solicitud de crédito, su resolución por parte de un analista, el préstamo que
nace de una aprobación, los pagos que se reciben en ventanilla y el saldo pendiente que
resulta de ellos. Está construido como una aplicación web de tres piezas —base de datos
SQL Server, API REST en Spring Boot y cliente React— orquestadas con Docker Compose de
modo que el entorno completo se levanta con un solo comando.

### Alcance funcional

| Ámbito | Qué cubre | Qué no cubre |
|---|---|---|
| Clientes | Alta, consulta paginada con búsqueda libre, edición de datos de contacto, baja con borrado del historial completo | Documentos adjuntos, direcciones múltiples, referencias personales |
| Solicitudes | Registro, simulación previa con evaluación de capacidad de pago, listado filtrable, aprobación (total o parcial) y rechazo con motivo | Flujo de varias firmas, comité de crédito, garantías como entidad propia |
| Préstamos | Creación automática al aprobar, cuota por sistema francés, tabla de amortización completa, saldo pendiente, liquidación | Mora, intereses moratorios, reestructuración, castigo de cartera |
| Pagos | Registro en efectivo con recibo correlativo, saldo anterior y posterior, comprobante imprimible | Otras formas de pago (el enum `FormaPago` solo declara `EFECTIVO`), reversos, anulaciones |
| Transversal | Autenticación JWT, autorización por rol, bitácora de auditoría, tablero de indicadores con gráficas (solicitudes por estado, recuperación, recaudación mensual y cartera por tipo), validación en tres capas | Alta de usuarios desde la interfaz, recuperación de contraseña, notificaciones por correo |

La moneda es el quetzal (GTQ) y todas las fechas se manejan en la zona
`America/Guatemala`, fijada tanto en el arranque de la JVM
(`PrestamosApplication`, bloque `static`) como en Hibernate
(`spring.jpa.properties.hibernate.jdbc.time_zone`) y en los contenedores (`TZ`).

### A quién va dirigido este manual

- **Desarrolladores** que vayan a extender el sistema: las secciones 3, 6, 8 y 12 explican
  dónde va cada cosa y cómo agregar un caso de uso sin romper la arquitectura.
- **Administradores de base de datos**: las secciones 4 y 12.
- **Personal de infraestructura y despliegue**: secciones 9, 10 y 13.
- **Revisores técnicos y de seguridad**: secciones 3.5, 5, 7 y 11.

Para el uso funcional de la aplicación (pantalla por pantalla) existe un documento
aparte, el manual de usuario. Este manual no lo duplica.

---

## 2. Stack tecnológico

Las versiones que siguen son las declaradas en `backend/pom.xml`,
`frontend/package.json` y `docker-compose.yml`.

### Backend

| Tecnología | Versión | Por qué se eligió |
|---|---|---|
| Java | 21 (LTS) | `record`, `switch` con patrones y texto en bloque permiten escribir comandos y DTO inmutables sin generadores de código. Es LTS, con soporte a largo plazo. |
| Spring Boot | 3.4.1 | POM padre que gobierna las versiones de todo el ecosistema; autoconfiguración, servidor embebido y actuator sin ensamblaje manual. |
| Spring Web (MVC) | gestionada por el padre | Modelo servlet síncrono, que es el que corresponde a una carga transaccional con JDBC bloqueante. WebFlux no aportaría nada sobre un driver bloqueante. |
| Spring Validation (Jakarta Bean Validation) | gestionada | Valida el DTO antes de entrar al caso de uso y permite devolver el error campo por campo. |
| Spring Data JPA + Hibernate | gestionada | ORM exigido por la prueba. Se usa con `ddl-auto: validate`: el esquema lo gobierna Flyway y Hibernate solo comprueba que el mapeo coincida, fallando al arrancar si divergen. |
| mssql-jdbc | gestionada (runtime) | Driver oficial de Microsoft para SQL Server; habilita `encrypt=true` en la cadena de conexión. |
| Flyway (`flyway-core` + `flyway-sqlserver`) | gestionada | Migraciones versionadas con *checksum*: la base de pruebas y la de producción se construyen con el mismo script y la misma secuencia. El módulo `flyway-sqlserver` es obligatorio desde Flyway 10 para este motor. |
| Spring Security | gestionada | Cadena de filtros, `@PreAuthorize` por rol y cabeceras de endurecimiento sin escribirlas a mano. |
| jjwt (`api`/`impl`/`jackson`) | 0.12.6 | Emisión y verificación de JWT HS256 con una API que obliga a declarar la clave y el algoritmo. `impl` y `jackson` quedan en `runtime` para que el código de compilación no dependa de la implementación. |
| Spring Boot Actuator | gestionada | Sonda `/actuator/health` que consumen el `HEALTHCHECK` de Docker y el `depends_on: service_healthy` del frontend. Expone **solo** `health` e `info`. |
| springdoc-openapi (webmvc-ui) | 2.7.0 | Genera el `/v3/api-docs` y Swagger UI desde las anotaciones de los controladores, sin mantener un contrato aparte. |
| spring-boot-configuration-processor | gestionada (`optional`) | Genera metadatos de `PropiedadesAplicacion` para autocompletar el bloque `app` del YAML en el IDE. No viaja en el jar. |
| **Sin Lombok / sin MapStruct** | — | Decisión deliberada, justificada en [3.5](#35-decisiones-de-diseño-justificación-y-contrapartida). |

### Pruebas

| Tecnología | Versión | Para qué |
|---|---|---|
| JUnit 5 (Jupiter) | vía `spring-boot-starter-test` | Motor de pruebas. |
| Mockito | vía starter | Dobles de los puertos de salida en las pruebas de caso de uso. |
| AssertJ | vía starter | Aserciones legibles sobre `BigDecimal` (`isEqualByComparingTo`, `isCloseTo`). |
| spring-security-test | gestionada | `@WithMockUser` en los cortes `@WebMvcTest`. |
| ArchUnit (`archunit-junit5`) | 1.3.0 | Convierte las reglas de dependencia entre capas en pruebas ejecutables. |

### Base de datos

| Tecnología | Versión | Por qué |
|---|---|---|
| Microsoft SQL Server | 2022 (`mcr.microsoft.com/mssql/server:2022-latest`, `MSSQL_PID=Developer`) | Motor solicitado. La edición Developer es gratuita para desarrollo y pruebas y funcionalmente equivalente a Enterprise. |
| Transact-SQL | — | Se usan columnas calculadas `PERSISTED`, secuencias, `CREATE OR ALTER`, funciones escalares, vistas, procedimientos y un trigger: el examen pide demostrar el lenguaje del motor, no solo el ORM. |

### Frontend

| Tecnología | Versión | Por qué |
|---|---|---|
| React | 18.3.1 | Modelo de componentes con hooks; sin clases. |
| Vite | 5.4.10 | Arranque en frío casi inmediato y `build` con hash en los nombres de los recursos, que es lo que permite cachearlos un año en Nginx. |
| react-router-dom | 6.28.0 | Rutas anidadas: el marco de la aplicación se monta una sola vez y solo cambia el `<Outlet/>`. |
| axios | 1.7.9 | Interceptores de petición y respuesta, que es donde se concentran el envío del token y la normalización de errores. |
| CSS propio con variables de diseño | — | Sin librería de UI ni de gráficas. Los 31 componentes reutilizables son propios —incluidas las gráficas del tablero, dibujadas en SVG— y todos los colores salen de `src/estilos/tokens.css`, de modo que un cambio de imagen institucional se hace en un solo archivo. Ver [8](#8-frontend). |

### Contenedores

| Tecnología | Detalle |
|---|---|
| Docker + Compose v2 | Cuatro servicios más un perfil opcional de herramientas. |
| Imágenes multietapa | Backend: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`. Frontend: `node:20-alpine` → `nginx:1.27-alpine`. La imagen final no lleva compilador, fuentes ni dependencias de construcción. |
| Backend sin root | `addgroup`/`adduser` crean el usuario `chn` (UID/GID 10001) y el `ENTRYPOINT` corre con `USER chn:chn`. |

---

## 3. Arquitectura

### 3.1 La arquitectura hexagonal aplicada

El paquete base es `gt.gob.chn.prestamos` y se divide en tres capas concéntricas:

```
            ┌──────────────────────── infrastructure ────────────────────────┐
            │   ┌──────────────────── application ────────────────────┐      │
            │   │        ┌──────────── domain ────────────┐           │      │
   HTTP ───►│   │        │  modelo · servicios · puertos  │           │      │───► SQL Server
            │   │        └────────────────────────────────┘           │      │
            │   └─────────────────────────────────────────────────────┘      │
            └───────────────────────────────────────────────────────────────┘
```

**La regla de dependencias apunta siempre hacia adentro:**

| Capa | De quién puede depender | De quién no |
|---|---|---|
| `domain` | Solo del JDK (`java.math`, `java.time`, `java.util`) | De nadie: ni de `application`, ni de `infrastructure`, ni de Spring, Jakarta, JPA, Hibernate, Jackson, jjwt o Flyway |
| `application` | De `domain` | De `infrastructure` |
| `infrastructure` | De `domain` y de `application` | — |

El mecanismo que lo hace posible es la **inversión de dependencias**: el dominio no llama
a la base de datos, declara *qué* necesita en forma de interfaz (`domain/port/out/`) y la
infraestructura la implementa. Por eso el núcleo de negocio se compila y se prueba sin
Spring, sin base de datos y sin servidor: las pruebas de `CalculadoraAmortizacion`,
`Prestamo` o `SolicitudPrestamo` se instancian con `new` y corren en milisegundos.

Los **puertos de entrada** (`domain/port/in/`) son la otra mitad de la simetría: el
controlador HTTP no conoce `GestionarSolicitudesService`, conoce la interfaz
`GestionarSolicitudesUseCase`. Los datos de entrada viajan en *records* de comando
(`domain/port/in/command/`), que son del dominio y no de la web, de modo que un segundo
adaptador de entrada —una tarea programada, una cola de mensajes, una CLI— usaría el
mismo caso de uso sin tocar ni una línea del núcleo.

### 3.2 Diagrama de bloques

```mermaid
flowchart LR
    subgraph ADAPT_IN["Adaptadores de entrada · infrastructure/adapter/in/web"]
        direction TB
        C1["AutenticacionControlador"]
        C2["ClienteControlador"]
        C3["SolicitudControlador"]
        C4["PrestamoControlador"]
        C5["PagoControlador"]
        C6["ResumenControlador"]
        C7["AuditoriaControlador"]
        MEG["ManejadorExcepcionesGlobal<br/>ExtractorContextoOperacion<br/>DTO + 10 mapeadores web"]
    end

    subgraph PORT_IN["Puertos de entrada · domain/port/in"]
        direction TB
        PI1["AutenticarUsuarioUseCase"]
        PI2["GestionarClientesUseCase"]
        PI3["GestionarSolicitudesUseCase"]
        PI4["ConsultarPrestamosUseCase"]
        PI5["RegistrarPagosUseCase"]
        PI6["ConsultarResumenUseCase"]
        PI7["ConsultarAuditoriaUseCase"]
        PI8["GenerarReportesUseCase"]
    end

    subgraph APP["Capa de aplicación · application/usecase"]
        direction TB
        U1["AutenticarUsuarioService"]
        U2["GestionarClientesService"]
        U3["GestionarSolicitudesService"]
        U4["ConsultarPrestamosService"]
        U5["RegistrarPagosService"]
        U6["ConsultarResumenService"]
        U7["ConsultarAuditoriaService"]
        U8["GenerarReportesService"]
    end

    subgraph NUCLEO["Núcleo de dominio · domain (cero framework)"]
        direction TB
        MOD["model/<br/>Cliente · SolicitudPrestamo · ResolucionSolicitud<br/>Prestamo · Pago · Usuario · TokenAcceso<br/>RegistroAuditoria · ResumenGeneral<br/>CarteraPorTipo · RecaudacionMensual<br/>NumeroDocumento · Montos · Validaciones · 6 enums<br/>reporte/ (DocumentoReporte y sus 6 piezas)"]
        SRV["service/<br/>CalculadoraAmortizacion<br/>EvaluadorCapacidadPago"]
        EXC["exception/<br/>DomainException + 5 concretas"]
    end

    subgraph PORT_OUT["Puertos de salida · domain/port/out (12 interfaces)"]
        direction TB
        PO1["ClienteRepositorio"]
        PO2["SolicitudPrestamoRepositorio"]
        PO3["PrestamoRepositorio"]
        PO4["PagoRepositorio"]
        PO5["UsuarioRepositorio"]
        PO6["ResumenRepositorio"]
        PO7["AuditoriaPort"]
        PO8["CorrelativoPort"]
        PO9["RelojPort"]
        PO10["CodificadorContrasenaPort"]
        PO11["GeneradorTokenPort"]
        PO12["GeneradorReportePort"]
    end

    subgraph ADAPT_OUT["Adaptadores de salida · infrastructure/adapter/out"]
        direction TB
        AO1["persistencia/<br/>6 entidades JPA · 7 repositorios Spring Data<br/>6 proyecciones · 6 mapeadores · 8 adaptadores"]
        AO2["seguridad/<br/>ProveedorJwt · CodificadorContrasenaBCrypt<br/>RelojSistema<br/>JwtAutenticacionFiltro · LimitadorLoginFiltro"]
        AO3["reporte/<br/>GeneradorReportePdf (OpenPDF)<br/>GeneradorReporteExcel (Apache POI)<br/>FormatoValores · PaletaReporte"]
    end

    NAV(["Navegador / Swagger UI"]) -->|HTTP JSON| ADAPT_IN
    ADAPT_IN --> PORT_IN
    PORT_IN -.->|implementan| APP
    APP --> NUCLEO
    APP --> PORT_OUT
    NUCLEO --- PORT_OUT
    PORT_OUT -.->|implementan| ADAPT_OUT
    AO1 -->|JDBC cifrado| DB[("SQL Server 2022<br/>CHN_Prestamos")]
    AO2 -->|HS256 · BCrypt| JWTK{{"JWT_SECRET<br/>variable de entorno"}}

    CFG["config/<br/>PropiedadesAplicacion · SeguridadConfig<br/>CasosDeUsoConfig · OpenApiConfig<br/>CargadorUsuariosIniciales"] -.->|ensambla| APP
    CFG -.->|ensambla| ADAPT_OUT
```

### 3.3 Responsabilidades por capa y clases reales

#### Responsabilidades

| Capa | Responsabilidad | Prohibido |
|---|---|---|
| `domain/model` | Invariantes del negocio. Cada agregado se construye por fábrica estática (`nuevo`, `nueva`, `reconstituir`) y valida en el constructor privado; no hay setters públicos. | Anotaciones de framework, conocer la base de datos, conocer HTTP |
| `domain/service` | Cálculos de negocio sin estado y deterministas (cuota, plan de amortización, política de endeudamiento) | Persistir, auditar, leer configuración |
| `domain/port/in` | Contrato de lo que el sistema sabe hacer, más los *records* de comando | Tipos de la web (`HttpServletRequest`, DTO) |
| `domain/port/out` | Contrato de lo que el sistema necesita del mundo exterior | Tipos de JPA, Spring Data o jjwt |
| `domain/exception` | Errores de negocio con un `codigo()` estable | Códigos HTTP |
| `application/usecase` | Orquestación: secuencia de llamadas al dominio y a los puertos, y **frontera transaccional** | Lógica de negocio propia, SQL, HTTP |
| `infrastructure/adapter/in/web` | Traducir HTTP ↔ comandos y modelo ↔ DTO, autorizar por rol, formatear errores | Reglas de negocio |
| `infrastructure/adapter/out/persistencia` | Traducir modelo ↔ entidad JPA y ejecutar consultas | Reglas de negocio |
| `infrastructure/adapter/out/seguridad` | Criptografía, tokens, reloj, límite de peticiones | Reglas de negocio |
| `infrastructure/config` | Ensamblado de beans, propiedades validadas, cadena de seguridad, OpenAPI, usuarios de arranque | — |

#### Clases reales por paquete

**`domain/model/` (20 clases: 14 de modelo y 6 enums)**
`Cliente`, `SolicitudPrestamo`, `ResolucionSolicitud`, `Prestamo`, `Pago`, `Usuario`,
`RegistroAuditoria`, `TokenAcceso`, `ResumenGeneral`, `CarteraPorTipo`, `RecaudacionMensual`,
`NumeroDocumento`, `Montos`, `Validaciones`, y los enums `EstadoSolicitud`, `EstadoPrestamo`,
`TipoPrestamo`, `FormaPago`, `Rol`, `TipoDocumento`.

`ResumenGeneral` lleva los nueve totales del tablero y las dos series de sus gráficas, que
son *records* propios con sus invariantes: ningún conteo ni importe negativo y montos en
escala 2. Cada serie sabe **completarse**: `CarteraPorTipo.completar(...)` devuelve siempre
los cinco tipos del enum `TipoPrestamo`, en su orden y con ceros para los que no tienen
préstamos, y `RecaudacionMensual.completar(filas, desde, hasta)` devuelve un elemento por
mes del rango, en orden ascendente y con ceros en los meses sin pagos. Las dos suman las
filas repetidas en lugar de rechazarlas y devuelven listas inmutables
([3.5-m](#35-decisiones-de-diseño-justificación-y-contrapartida)).

`Validaciones` reúne las comprobaciones que comparten los agregados y los filtros:
`exigirNoNulo`, `exigirTexto`, `exigirTextoOpcional`, `exigirRango` (para `BigDecimal` y
para `int`), `exigirPositivo`, `exigirNoNegativo`, `exigirDigitos`, `exigirCorreo`,
`exigirOrdenCronologico` y las dos sobrecargas de `exigirOrdenDeRango` (`BigDecimal` e
`Integer`). Las tres últimas son las que hacen coherente un rango de búsqueda
([3.5-j](#35-decisiones-de-diseño-justificación-y-contrapartida)).

**`domain/model/consulta/` (9 clases)**
`PaginaDominio`; los cinco *records* de filtro `FiltroCliente`, `FiltroSolicitud`,
`FiltroPrestamo`, `FiltroPago` y `FiltroAuditoria`; y las tres vistas de detalle
`SolicitudDetalle`, `PrestamoDetalle`, `PagoDetalle`.

Los cinco filtros comparten contrato: todos los criterios son opcionales, el constructor
compacto recorta el texto y **valida la coherencia de los rangos**, una fábrica estática
`de(...)` cubre el caso habitual con los criterios mínimos, y `tieneFiltrosActivos()`
responde si hay algo más que paginación —que es lo que la interfaz usa para su contador—.
`FiltroPrestamo` añade `tieneRangoDeSaldo()`, porque ese criterio no corresponde a un
atributo directo de la entidad (ver [4.1](#41-diccionario-de-datos)).

**`domain/exception/` (6 clases)**

| Clase | `codigo()` | HTTP |
|---|---|---|
| `DomainException` (abstracta) | — | — |
| `ValidacionDominioException` | `VALIDACION` | 400 |
| `AutenticacionException` | `AUTENTICACION` | 401 |
| `RecursoNoEncontradoException` | `NO_ENCONTRADO` | 404 |
| `ReglaNegocioException` | `REGLA_NEGOCIO` | 409 |
| `ConflictoRecursoException` | `DUPLICADO` | 409 |

**`domain/service/` (6 clases)**
`CalculadoraAmortizacion`, `PlanAmortizacion`, `CuotaAmortizacion`,
`EvaluadorCapacidadPago`, `ResultadoEvaluacion`, `ResultadoSimulacion`.

**`domain/model/reporte/` (7 tipos)**
`DocumentoReporte` (título, encabezado, columnas, filas y totales de un reporte),
`ColumnaReporte`, `FilaReporte`, `ParDato`, la interfaz sellada `ValorCelda` (texto,
entero, moneda, porcentaje, fecha, fecha y hora), `FormatoReporte` (PDF / Excel) y
`ArchivoGenerado`. Describen **qué** se imprime; el **cómo** es de los adaptadores de salida.

**`domain/port/in/` (8 interfaces)**
`AutenticarUsuarioUseCase`, `GestionarClientesUseCase`, `GestionarSolicitudesUseCase`,
`ConsultarPrestamosUseCase`, `RegistrarPagosUseCase`, `ConsultarResumenUseCase`,
`ConsultarAuditoriaUseCase`, `GenerarReportesUseCase`.

**`domain/port/in/command/` (9 records)**
`CredencialesCommand`, `RegistrarClienteCommand`, `ActualizarClienteCommand`,
`CrearSolicitudCommand`, `AprobarSolicitudCommand`, `RechazarSolicitudCommand`,
`SimularSolicitudCommand`, `RegistrarPagoCommand`, `ContextoOperacion`.

**`domain/port/out/` (12 interfaces)**
`ClienteRepositorio`, `SolicitudPrestamoRepositorio`, `PrestamoRepositorio`,
`PagoRepositorio`, `UsuarioRepositorio`, `ResumenRepositorio`, `AuditoriaPort`,
`CorrelativoPort`, `RelojPort`, `CodificadorContrasenaPort`, `GeneradorTokenPort`,
`GeneradorReportePort`.

Los cinco listados paginados reciben su *record* de filtro y no una lista de argumentos
sueltos: `listar(FiltroCliente)`, `listar(FiltroSolicitud)`, … y, desde esta iteración,
también `AuditoriaPort.listar(FiltroAuditoria)` y
`ConsultarAuditoriaUseCase.listar(FiltroAuditoria)`, que antes recibían `(int pagina, int
tamano)`. Agregar un criterio nuevo ya no cambia la firma del puerto.

**`application/usecase/` (9 clases)**
`GestionarClientesService`, `GestionarSolicitudesService`, `ConsultarPrestamosService`,
`RegistrarPagosService`, `AutenticarUsuarioService`, `ConsultarResumenService`,
`ConsultarAuditoriaService`, `GenerarReportesService` y `AccionesAuditoria` (catálogo de
constantes de la bitácora: 5 entidades y 12 acciones, para que el nombre guardado en la
base no dependa de un literal escrito a mano en cada caso de uso).

**`infrastructure/adapter/in/web/`**

| Subpaquete | Clases |
|---|---|
| raíz | `ExtractorContextoOperacion`, `ManejadorExcepcionesGlobal` |
| `controlador/` (7) | `AutenticacionControlador`, `ClienteControlador`, `SolicitudControlador`, `PrestamoControlador`, `PagoControlador`, `ResumenControlador`, `AuditoriaControlador` |
| `dto/peticion/` (8) | `LoginRequest`, `ClienteRequest`, `ActualizarClienteRequest`, `SolicitudRequest`, `AprobarSolicitudRequest`, `RechazarSolicitudRequest`, `SimulacionRequest`, `PagoRequest` |
| `dto/respuesta/` (16) | `TokenResponse`, `UsuarioResponse`, `ClienteResponse`, `SolicitudResponse`, `ResolucionResponse`, `PrestamoResponse`, `PlanAmortizacionResponse`, `CuotaResponse`, `PagoResponse`, `SimulacionResponse`, `EvaluacionResponse`, `ResumenResponse`, `CarteraTipoResponse`, `RecaudacionMensualResponse`, `AuditoriaResponse`, `PaginaResponse`, más `ErrorResponse` y `ErrorCampo` |
| `mapeador/` (10) | `ConversorEnumWeb`, `MapeadorWebUsuario`, `MapeadorWebCliente`, `MapeadorWebSolicitud`, `MapeadorWebPrestamo`, `MapeadorWebPago`, `MapeadorWebSimulacion`, `MapeadorWebResumen`, `MapeadorWebAuditoria`, `MapeadorWebPagina` |

`ConversorEnumWeb` traduce a mano el texto de un parámetro al enum del dominio para poder
responder `400` **enumerando los valores permitidos**, en lugar del mensaje opaco de la
conversión automática. Distingue tres casos: `aTipoPrestamo` (obligatorio, el del cuerpo de
una solicitud nueva, donde el vacío es error), `aEstadoSolicitud`/`aEstadoPrestamo`
(criterios opcionales, donde el vacío significa «todos») y `aTipoPrestamoOpcional`, que es
el mismo tipo de préstamo pero usado como filtro del listado, donde tampoco el vacío es un
error. Las tres variantes comparten la conversión interna para que el mensaje sea idéntico.

**`infrastructure/adapter/out/persistencia/`**

| Subpaquete | Clases |
|---|---|
| `entidad/` (6) | `ClienteEntidad`, `SolicitudPrestamoEntidad`, `PrestamoEntidad`, `PagoEntidad`, `UsuarioEntidad`, `AuditoriaEntidad` |
| `repositorio/` (7) | `ClienteJpaRepositorio`, `SolicitudPrestamoJpaRepositorio`, `PrestamoJpaRepositorio`, `PagoJpaRepositorio`, `UsuarioJpaRepositorio`, `AuditoriaJpaRepositorio`, `ResumenJpaRepositorio` |
| `repositorio/proyeccion/` (6) | `SolicitudProyeccion`, `PrestamoProyeccion`, `PagoProyeccion`, `ResumenProyeccion`, `CarteraTipoProyeccion`, `RecaudacionMensualProyeccion` |
| `mapeador/` (6) | `MapeadorCliente`, `MapeadorSolicitud`, `MapeadorPrestamo`, `MapeadorPago`, `MapeadorUsuario`, `MapeadorAuditoria` |
| `adaptador/` (8 + 2 utilidades) | `ClienteRepositorioJpa`, `SolicitudPrestamoRepositorioJpa`, `PrestamoRepositorioJpa`, `PagoRepositorioJpa`, `UsuarioRepositorioJpa`, `ResumenRepositorioJpa`, `AuditoriaAdaptador`, `CorrelativoAdaptador`, más `PaginacionJpa` (conversión `Page` ↔ `PaginaDominio`) y `CriteriosJpa` (normalización de los criterios de búsqueda antes de la consulta) |

**`infrastructure/adapter/out/reporte/` (4 clases)**
`GeneradorReportePdf` (OpenPDF, encabezado y pie con «Página X de Y»),
`GeneradorReporteExcel` (Apache POI, celdas numéricas con formato de moneda),
`FormatoValores` y `PaletaReporte`.

**`infrastructure/adapter/out/seguridad/` (6 clases)**
`ProveedorJwt`, `DatosToken`, `JwtAutenticacionFiltro`, `LimitadorLoginFiltro`,
`CodificadorContrasenaBCrypt`, `RelojSistema`.

**`infrastructure/config/` (5 clases)**
`PropiedadesAplicacion`, `SeguridadConfig`, `CasosDeUsoConfig`, `OpenApiConfig`,
`CargadorUsuariosIniciales`.

> `PrestamosApplication` (la clase `@SpringBootApplication`) reside en la raíz del paquete
> base, `gt.gob.chn.prestamos`, no dentro de `config/`: Spring Boot escanea los
> subpaquetes de la clase de arranque, así que tiene que estar por encima de las tres capas.

### 3.4 Verificación automática con ArchUnit

`backend/src/test/java/gt/gob/chn/prestamos/arquitectura/ArquitecturaHexagonalTest.java`
contiene **12 pruebas**. Una revisión humana no puede vigilar en cada cambio que nadie
importe `@Entity` en el dominio; estas pruebas sí, y fallan la construcción de la imagen
(`mvn package` corre dentro del `Dockerfile`).

| Prueba | Regla que comprueba |
|---|---|
| `el_analisis_encuentra_las_clases` | Que el análisis importó clases reales y que hay **más de 20** clases de dominio. Es una salvaguarda contra el falso verde. |
| `el_dominio_no_depende_de_frameworks` | Ninguna clase de `..domain..` depende de `org.springframework..`, `jakarta..`, `javax..`, `com.fasterxml..`, `org.hibernate..`, `io.jsonwebtoken..` ni `org.flywaydb..` |
| `el_dominio_no_depende_de_las_capas_externas` | `..domain..` no depende de `...application..` ni de `...infrastructure..` |
| `la_aplicacion_no_depende_de_la_infraestructura` | `..application..` no depende de `...infrastructure..` |
| `el_dominio_usa_solo_la_api_moderna_de_fechas` | `..domain..` no usa `java.util.Date`, `java.util.Calendar` ni nada de `java.sql..` |
| `los_controladores_residen_en_el_adaptador_web` | Toda clase `@RestController` vive en `..infrastructure.adapter.in.web.controlador` y su nombre termina en `Controlador` |
| `las_entidades_jpa_residen_en_el_adaptador_de_persistencia` | Toda clase `@Entity` vive en `..infrastructure.adapter.out.persistencia.entidad` y su nombre termina en `Entidad` |
| `las_implementaciones_de_puertos_de_salida_son_adaptadores` | Ninguna implementación de una interfaz de `..domain.port.out..` reside fuera de `..infrastructure.adapter.out..` |
| `las_implementaciones_de_casos_de_uso_residen_en_la_capa_de_aplicacion` | Ninguna implementación de una interfaz de `..domain.port.in` reside fuera de `..application.usecase..` |
| `las_librerias_de_documentos_no_llegan_al_nucleo` | Ni `..domain..` ni `..application..` dependen de OpenPDF (`com.lowagie..`, `com.github.librepdf..`) ni de Apache POI (`org.apache.poi..`, `org.openxmlformats..`): el dominio describe el reporte y los adaptadores lo materializan |
| `los_generadores_de_reportes_residen_en_su_adaptador` | Toda implementación de `GeneradorReportePort` vive en `..infrastructure.adapter.out.reporte` |
| `spring_data_solo_se_usa_en_el_adaptador_de_persistencia` | `org.springframework.data..` solo aparece en el paquete raíz, en `..infrastructure.adapter.out..` y en `..infrastructure.config..` |

**Detalle de implementación que importa.** El conjunto de clases se importa con un
`ClassFileImporter` explícito y no con la extensión `@AnalyzeClasses`:

```java
clases = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .withImportOption(new ImportOption.DoNotIncludeJars())
        .importPackages("gt.gob.chn.prestamos");
```

`@AnalyzeClasses` resuelve los paquetes a través del cargador de clases y, bajo el
classpath de Surefire, puede no encontrar ninguna: las reglas pasarían en verde sin haber
comprobado nada. Por eso la primera prueba valida el conjunto importado antes que
cualquier regla.

### 3.5 Decisiones de diseño, justificación y contrapartida

#### a) La capa de aplicación sí usa anotaciones de Spring

Los ocho servicios de `application/usecase` llevan `@Service` y `@Transactional`.

- **Por qué.** La transacción no es una regla de negocio, es **orquestación**: decide qué
  conjunto de escrituras se confirma o se revierte como una unidad. Es exactamente la
  responsabilidad de esta capa. Aprobar una solicitud escribe en `solicitudes_prestamo`,
  en `prestamos` y dos veces en `auditoria`; si la transacción no fuera atómica podría
  quedar una solicitud aprobada sin préstamo. Lo mismo con un pago: el recibo y el nuevo
  saldo del préstamo se confirman juntos o no se confirman. Implementar esto a mano
  (`begin`/`commit`/`rollback` con `try`/`catch`) sería más código y más frágil que una
  anotación declarativa.
- **Contrapartida.** La capa de aplicación no es portable a otro framework tal cual: para
  moverla habría que sustituir `@Transactional`. Se aceptó porque la frontera que de verdad
  hay que proteger es la del **dominio**, y esa sí está libre de framework y vigilada por
  `el_dominio_no_depende_de_frameworks`.
- Los servicios de dominio (`CalculadoraAmortizacion`, `EvaluadorCapacidadPago`) **no**
  llevan anotaciones: se publican como beans desde `CasosDeUsoConfig`, en la
  infraestructura. Al no tener estado, una instancia compartida es segura entre hilos.
- Las consultas van con `@Transactional(readOnly = true)`, que permite al driver y al
  motor optimizar la lectura y evita un `flush` innecesario al cerrar.

#### b) Las entidades JPA referencian por identificador, no con `@ManyToOne`

`PrestamoEntidad` guarda `Long solicitudId` y `Long clienteId`, no
`@ManyToOne SolicitudPrestamoEntidad`.

- **Por qué.** Cada agregado (`Cliente`, `SolicitudPrestamo`, `Prestamo`, `Pago`) tiene su
  propia frontera de consistencia y se carga y se guarda por separado. Con `@ManyToOne` la
  navegación cómoda (`prestamo.getCliente().getNombre()`) arrastra tres problemas: carga
  perezosa fuera de transacción (`LazyInitializationException`, más probable aún porque
  `spring.jpa.open-in-view` está en `false`), consultas N+1 silenciosas en los listados, y
  un grafo de objetos en el que guardar un préstamo puede terminar escribiendo en el
  cliente. Referenciar por identificador hace explícito cada viaje a la base.
- Cuando la pantalla necesita datos de dos tablas, no se resuelven navegando: se pide una
  **proyección** con un `JOIN` escrito a propósito
  (`PrestamoProyeccion`, `SolicitudProyeccion`, `PagoProyeccion`), que trae exactamente las
  columnas necesarias en un solo viaje. El dominio recibe entonces un
  `PrestamoDetalle`/`SolicitudDetalle`/`PagoDetalle`.
- **Contrapartida.** Hay que escribir los `JOIN` a mano y el compilador no impide comparar
  un `clienteId` con un `solicitudId`, porque ambos son `Long`. Se acepta porque la
  integridad referencial la garantizan las llaves foráneas del esquema.

#### c) No se usa Lombok (ni MapStruct)

- **Por qué.** El dominio se modela con *records* de Java y con clases de invariantes
  validadas en el constructor privado, expuestas por fábricas estáticas. Los `@Data` y
  `@Builder` de Lombok empujan justo a lo contrario: setters públicos y objetos mutables.
  Además, prescindir de Lombok elimina un procesador de anotaciones de la compilación y
  garantiza que **el código que un evaluador lee es exactamente el que se ejecuta**, sin
  generación oculta. Por el mismo motivo los 16 mapeadores están escritos a mano en lugar
  de generados con MapStruct.
- **Contrapartida.** Más líneas: los *getters* de `Cliente`, `Prestamo` y `Usuario` están
  escritos uno por uno, y cada mapeador es código explícito que hay que mantener al agregar
  un campo. A cambio, el mapeo es depurable con un punto de ruptura y no depende de una
  fase de generación.

#### d) Numeración oficial con dígito verificador; el correlativo sale de una secuencia

Cada documento lleva un número oficial con la estructura `TT-AAA-AAAA-NNNNNN-D`, igual a
la de las cuentas y documentos bancarios reales:

| Bloque | Significado | Ejemplo |
|---|---|---|
| `TT` | Tipo de documento: `SC` solicitud de crédito, `PR` préstamo, `RC` recibo de caja | `PR` |
| `AAA` | Agencia emisora (`001` = oficinas centrales), configurable con `APP_CODIGO_AGENCIA` | `001` |
| `AAAA` | Año de emisión | `2026` |
| `NNNNNN` | Correlativo de la secuencia; mínimo seis dígitos, crece sin truncarse | `000004` |
| `D` | Dígito verificador | `3` |

Ejemplos: `SC-001-2026-000001-3`, `PR-001-2026-000004-3`, `RC-001-2026-000007-1`.

- **Dígito verificador.** Es Luhn (módulo 10) calculado sobre todo el número, con las
  letras del tipo convertidas a base 36 como en el IBAN (`S`=28, `C`=12, `P`=25, `R`=27).
  Detecta cualquier dígito mal copiado y casi cualquier par de dígitos vecinos
  intercambiados. Como el tipo entra en el cálculo, un número de préstamo al que solo se
  le cambia el prefijo a `RC` deja de ser válido.
- **Dónde vive la regla.** La compone y la valida el value object de dominio
  `NumeroDocumento`. `SolicitudPrestamo`, `Prestamo` y `Pago` la exigen al construirse,
  así que ninguna entidad existe con un número mal formado ni con el de otro tipo de
  documento. La base añade `CHECK` de estructura (`ck_solicitudes_numero`,
  `ck_prestamos_numero`, `ck_pagos_recibo`) contra cargas manuales, y
  `NumeracionDatosDemoTest` recalcula el verificador de cada número escrito a mano en
  `V900`.

`CorrelativoAdaptador` compone el número con la agencia configurada, el año de
`RelojPort` y el consecutivo que obtiene con `SELECT NEXT VALUE FOR dbo.seq_solicitud` (y
sus equivalentes).

- **Por qué.** La alternativa natural, `MAX(numero) + 1` en Java, se rompe con
  concurrencia: dos cajeros que cobran a la vez leen el mismo máximo y generan el mismo
  recibo, y el `UNIQUE` hace fallar a uno de ellos. Una secuencia de SQL Server es
  **atómica**, no bloquea la tabla y no repite valores. El año lo aporta `RelojPort`, para
  que el formato sea verificable en pruebas con un reloj fijo.
- Las tres sentencias son **constantes literales**: T-SQL no admite parámetros en
  `NEXT VALUE FOR`, y el nombre de la secuencia jamás proviene de entrada externa. Es el
  único punto del sistema con SQL nativo de escritura y por eso está aislado en una clase
  de nueve líneas útiles.
- **Contrapartida.** El correlativo se consume aunque la transacción se revierta, así que
  la serie puede tener huecos. Es el comportamiento correcto para un correlativo de
  negocio: un hueco es auditable, un número repetido no. También significa que el
  adaptador no es portable a otro motor sin cambiar la sentencia.

#### e) `prestamos.saldo_pendiente` es una columna calculada `PERSISTED`

```sql
saldo_pendiente AS (monto_total_a_pagar - total_pagado) PERSISTED NOT NULL
```

- **Por qué.** El saldo es un dato **derivado**. Si se guardara como columna normal habría
  dos fuentes de verdad y bastaría un `UPDATE` mal hecho para que el saldo dejara de
  cuadrar con los pagos. Siendo calculada, el motor la mantiene siempre; siendo
  `PERSISTED`, se materializa en disco y por tanto se puede **indexar** y sumar en
  reportes sin recalcularla (de hecho aparece en el `INCLUDE` de
  `ix_prestamos_cliente_estado` y de `ix_prestamos_estado`, y la usan
  `vw_resumen_general` y `vw_prestamos_saldo`).
- **Consecuencia en el ORM:** la columna **no se mapea** en `PrestamoEntidad`. Si se
  mapeara, Hibernate intentaría escribirla en los `INSERT`/`UPDATE` y SQL Server
  rechazaría la sentencia. Del lado de la aplicación el saldo lo expone el dominio con
  `Prestamo.getSaldoPendiente()`, que calcula `montoTotalAPagar - totalPagado` con la misma
  fórmula. Las dos definiciones son idénticas por diseño.
- **Contrapartida.** La fórmula está escrita dos veces (en T-SQL y en Java) y hay que
  cambiarlas juntas. Se aceptó porque es una resta, y porque tener el saldo disponible en
  SQL permite que un reporte hecho por el área de negocio dé la misma cifra que la API.

#### f) El borrado en cascada es explícito en el caso de uso

Todas las llaves foráneas se declaran `ON DELETE NO ACTION ON UPDATE NO ACTION`, y
`GestionarClientesService.eliminar(...)` borra en orden dentro de una sola transacción:

```java
pagoRepositorio.eliminarPorCliente(id);        // 1. pagos de todos sus préstamos
prestamoRepositorio.eliminarPorCliente(id);    // 2. préstamos
solicitudRepositorio.eliminarPorCliente(id);   // 3. solicitudes
clienteRepositorio.eliminar(id);               // 4. el cliente
```

- **Razón técnica.** `clientes` llega a `pagos` por **dos caminos**
  (`clientes → solicitudes → prestamos → pagos` y `clientes → prestamos → pagos`). SQL
  Server rechaza crear esa estructura con cascadas: *"may cause cycles or multiple cascade
  paths"*. Con `NO ACTION` el esquema se crea sin problema.
- **Razón de negocio.** Borrar un cliente es la operación más destructiva del sistema. Que
  el motor la ejecute en silencio esconde su alcance real. Así el borrado queda en el
  código, se lee, se prueba (`GestionarClientesServiceTest` verifica el **orden** de las
  llamadas) y se audita con el usuario real y su IP. Además el trigger
  `tr_clientes_auditoria_delete` deja constancia incluso si alguien borra directamente en
  la base.
- Como `pagos` no guarda el cliente, el primer paso lo localiza con una subconsulta:
  `DELETE FROM PagoEntidad g WHERE g.prestamoId IN (SELECT p.id FROM PrestamoEntidad p WHERE p.clienteId = :clienteId)`.
- **Contrapartida.** El orden es una precondición que vive en el código: invertir dos
  líneas rompe la operación. Lo detecta la prueba, y en último término la llave foránea.

#### g) La agrupación de sentencias de Hibernate está desactivada (`batch_size: 0`)

- **Por qué.** Todas las tablas usan columnas `IDENTITY`, así que Hibernate necesita
  recuperar la llave generada **fila por fila** y no puede agrupar los `INSERT` de todas
  formas: activar la agrupación solo añadiría configuración sin efecto.
- **Por qué además no se pierde nada.** La carga de este sistema son escrituras
  transaccionales de **una sola fila**: un cliente, una solicitud, un pago. No hay ningún
  caso de uso que inserte cientos de filas, así que la agrupación no tendría nada que
  agrupar.
- Sí se mantienen `order_inserts: true` y `order_updates: true`: el orden estable de las
  sentencias dentro de una transacción hace reproducible la secuencia de escritura, lo que
  ayuda al leer los registros de una operación.
- **Contrapartida.** Si en el futuro apareciera una carga masiva, habría que cambiar la
  estrategia de generación de claves (por ejemplo a `SEQUENCE`) antes de poder activar el
  *batching*.

#### h) El plan de amortización se recalcula, no se almacena

`GET /prestamos/{id}/amortizacion` genera la tabla completa en memoria con
`CalculadoraAmortizacion`; no existe tabla `cuotas`.

- **Por qué.** El plan es una **función pura** de cuatro datos que ya están guardados en
  `prestamos`: monto aprobado, plazo, tasa y la fórmula. Almacenarlo añadiría una tabla de
  hasta 360 filas por préstamo que no aporta información nueva y que puede quedar
  desincronizada de su encabezado. Recalcular evita esa clase entera de errores: el plan
  de un préstamo es siempre reproducible, hoy y en cinco años, porque la cuota y el plazo
  quedaron **congelados** en el préstamo al momento de aprobar (si mañana cambia la
  política de tasas, este préstamo no cambia).
- El costo es despreciable: 360 iteraciones de aritmética `BigDecimal`, sin acceso a base
  de datos, en un servicio sin estado.
- **Contrapartida.** No se puede registrar en la base que una cuota concreta fue pagada,
  porque las cuotas no existen como filas. El sistema lleva el control por **saldo
  acumulado**, que es lo que el alcance pide; `vw_prestamos_saldo` expone una aproximación
  de cuotas cubiertas con `FLOOR(total_pagado / cuota_mensual)`. Un modelo con mora por
  cuota exigiría materializar el plan.

#### i) El filtrado de los listados ocurre en la base de datos, nunca en el cliente

Cada criterio de búsqueda viaja hasta el `WHERE` de la consulta como parámetro nombrado. No
se trae una página —ni la tabla entera— para filtrarla después en Java o en el navegador.

- **Por qué.** Con paginación, filtrar después de consultar da **resultados incorrectos**,
  no solo lentos: si se piden 10 filas y se descartan 4 en memoria, la página muestra 6 y
  `totalElementos` sigue contando el conjunto sin filtrar, con lo que el paginador ofrece
  páginas que no existen y el usuario no tiene forma de saber cuántos registros cumplen su
  criterio. Por eso cada consulta lleva su `countQuery` con el **mismo `WHERE`**: el total y
  el contenido describen el mismo conjunto. Además, el motor resuelve el filtro con sus
  índices y solo viaja por la red lo que se va a mostrar.
- **Cómo se mantiene opcional sin armar SQL dinámico.** El patrón es
  `(:criterio IS NULL OR <condición>)` repetido para cada criterio. Una sola consulta
  estática cubre todas las combinaciones: no hay concatenación de cláusulas, no hay
  `Criteria API` ni `Specification`, y por tanto no hay superficie de inyección
  ([7.6](#76-prevención-de-inyección-sql)).
- **Contrapartida.** El JPQL es largo y el `WHERE` está escrito dos veces, en la consulta y
  en su `countQuery`; si se agrega un criterio hay que tocarlo en los dos sitios o el total
  deja de cuadrar. Se aceptó porque la alternativa —construir la consulta en Java— cambia
  texto legible y revisable por código que hay que ejecutar para saber qué SQL produce. Con
  muchas condiciones opcionales el plan de ejecución también puede volverse subóptimo; si
  llegara a notarse, la salida sería `OPTION (RECOMPILE)` y no volver a filtrar en memoria.
- La normalización previa (texto en blanco → `null`, límite superior de un rango sobre
  `DATETIME2` → inicio del día siguiente) está concentrada en `CriteriosJpa`, para que los
  cinco adaptadores no la repitan cada uno a su manera.

#### j) Los *records* de filtro validan la coherencia de los rangos en el dominio

`FiltroCliente`, `FiltroSolicitud`, `FiltroPrestamo`, `FiltroPago` y `FiltroAuditoria`
comprueban en su constructor compacto que ningún rango esté invertido, y lanzan
`ValidacionDominioException` si lo está. El controlador no valida nada de eso: solo
construye el filtro.

- **Por qué.** «El extremo inicial no puede ser posterior al final» es una regla del
  **significado** del criterio, no del protocolo HTTP. Si viviera en el controlador,
  quedaría fuera del alcance de cualquier otro adaptador de entrada —una tarea programada,
  una CLI, una cola— y habría que repetirla en cada uno; y habría que repetirla también en
  los cinco listados, que es exactamente donde se olvida una. Poniéndola en el *record* se
  cumple la misma promesa que en el resto del dominio: **un objeto no puede existir en
  estado inválido**. No hay forma de construir un `FiltroPago` con el rango al revés.
- **Por qué no se resuelve en la consulta.** Un rango invertido devolvería cero filas de
  forma natural, sin error. Pero responder `200` con una lista vacía a quien escribió mal
  las fechas esconde su equivocación: parece que no hay datos cuando lo que hay es un
  criterio imposible. Un `400` con el mensaje exacto se corrige en segundos.
- La traducción a HTTP es automática y ya existía: `ManejadorExcepcionesGlobal` convierte
  toda `ValidacionDominioException` en `400` con `codigo: VALIDACION`
  ([5.4](#54-formato-de-error-y-códigos)).
- **Contrapartida.** El dominio conoce un concepto —«criterio de búsqueda»— que no es una
  regla de negocio del crédito. Se aceptó porque el filtro es la **entrada** del caso de uso
  de consulta, igual que un comando lo es del de escritura, y porque el precio de la
  alternativa era duplicar la validación en cada adaptador.

#### k) `exigirOrdenCronologico` se declara con `Comparable<? super T>`

```java
public static <T extends Comparable<? super T>> void exigirOrdenCronologico(
        T desde, T hasta, String queRango) { … }
```

- **Por qué no `<T extends Comparable<T>>`.** Porque **no compilaría** con el tipo que de
  verdad se le pasa. `LocalDate` no implementa `Comparable<LocalDate>`: implementa
  `Comparable<ChronoLocalDate>`, la interfaz común a `LocalDate`, `JapaneseDate`,
  `HijrahDate` y las demás cronologías del JDK. Con la cota estrecha, `T = LocalDate`
  exigiría `LocalDate implements Comparable<LocalDate>`, que es falso, y el compilador
  rechazaría cada llamada. La cota `Comparable<? super T>` admite que la comparación esté
  declarada en un **supertipo** de `T`, que es justo el caso —y es la misma firma que usan
  `Collections.sort` y `Comparator.naturalOrder` por esta misma razón—.
- **Por qué genérico y no `LocalDate` a secas.** Firmarlo contra `LocalDate` funcionaría
  hoy, pero el día que un criterio compare `LocalDateTime` o `YearMonth` habría que añadir
  una sobrecarga idéntica. Con la cota correcta, el método sirve para cualquier tipo
  ordenado sin cambiar una línea.
- **Contrapartida.** La firma es más difícil de leer que un `LocalDate desde, LocalDate
  hasta`, y por eso está explicada en el propio Javadoc del método. Las dos sobrecargas de
  `exigirOrdenDeRango` (`BigDecimal` e `Integer`) **no** son genéricas: ahí el mensaje de
  error difiere —«monto mínimo» frente a «valor mínimo»— y un único método genérico
  obligaría a pasar ese texto como parámetro, que es más ruido que las dos sobrecargas.

#### l) En la interfaz, los criterios escritos a mano se aplican con retardo

`useFiltros` espera **350 ms** desde la última pulsación antes de aplicar un criterio que el
usuario teclea (el texto libre y los extremos numéricos de los rangos). Los que vienen de un
desplegable o del selector de fechas se aplican de inmediato.

- **Por qué.** Sin retardo, escribir «Ramírez» dispara siete peticiones y seis de ellas
  quedan obsoletas antes de responder. Además de gastar red y base de datos, las respuestas
  pueden llegar **desordenadas** y pintar el resultado de «Ram» sobre el de «Ramírez».
- **Por qué 350 ms.** Es más que la cadencia de escritura normal (unos 200 ms entre teclas)
  y menos que el umbral en el que una interfaz se percibe lenta. Con menos no se agrupa
  nada; con bastante más, el usuario deja de escribir y siente que la pantalla no reacciona.
- **Por qué solo a lo que se teclea.** Elegir «Aprobada» en un desplegable es una decisión
  terminada: hacerle esperar 350 ms sería un retraso sin contrapartida. El hook distingue
  ambos casos por la lista `clavesConRetardo`.
- El retardo **no sustituye** al control de respuestas obsoletas: cada carga lleva además su
  bandera de cancelación, de modo que una respuesta lenta nunca sobrescribe a una posterior.
- **Contrapartida.** Hay una ventana de 350 ms en la que la pantalla muestra el resultado
  anterior mientras el campo ya tiene el texto nuevo. Se asume a conciencia: el detalle está
  en [8.6](#86-los-filtros-en-la-interfaz).

#### m) Las series del tablero se agregan en SQL y se completan en el dominio

`GET /resumen` entrega, además de los nueve totales, dos series para las gráficas:
`carteraPorTipo` y `recaudacionMensual` ([5.3](#53-ejemplos-de-petición-y-respuesta)). El
trabajo se reparte en dos mitades:

- **La agregación es de la base.** Las vistas `dbo.vw_cartera_por_tipo` y
  `dbo.vw_recaudacion_mensual` (`V4`, [4.3](#43-objetos-programables-transact-sql)) hacen el
  `GROUP BY`, igual que `vw_resumen_general` con los totales: el tablero no carga entidades y
  su costo no crece con el tamaño de la cartera.
- **La forma es del dominio.** Las vistas solo devuelven los grupos **con datos**. Un tipo sin
  préstamos o un mes sin pagos no produce fila, y `GROUP BY` no puede inventarla sin una
  tabla de calendario o de catálogo. `CarteraPorTipo.completar` y `RecaudacionMensual.completar`
  rellenan con ceros a partir de lo que el dominio ya conoce: el enum `TipoPrestamo` y el mes
  en curso que da `RelojPort` en la zona `America/Guatemala`, no la hora del contenedor. Así
  el contrato es fijo (siempre 5 tipos en el orden del enum y 12 meses contiguos que terminan
  en el mes en curso) y el cliente no necesita lógica de relleno.
- **El rango de meses viaja como entero `AAAAMM`.** `ConsultarResumenService` calcula
  `desde` y `hasta` con `YearMonth` y el adaptador filtra con
  `(anio * 100 + mes) BETWEEN :desde AND :hasta`, un solo `BETWEEN` que cruza de diciembre a
  enero sin casos especiales y con parámetros con nombre.
- **Contrapartida: tres lecturas, no una foto única.** El servicio lee las tres vistas en
  sentencias separadas. Con `READ_COMMITTED_SNAPSHOT` cada una ve lo último confirmado al
  ejecutarse, de modo que un pago confirmado entre dos lecturas puede separar los totales de
  la suma de las series hasta la siguiente consulta. Se acepta porque el tablero es
  informativo. Una foto única exigiría el aislamiento `SNAPSHOT`, que `@Transactional` de
  Spring no ofrece, o juntar en una sentencia tres resultados de forma distinta.

### 3.6 Diagramas de secuencia

#### Aprobar una solicitud

```mermaid
sequenceDiagram
    autonumber
    actor AN as Analista (navegador)
    participant NG as Nginx (proxy /api)
    participant SEC as Cadena de seguridad<br/>JwtAutenticacionFiltro + @PreAuthorize
    participant CTRL as SolicitudControlador
    participant MAP as MapeadorWebSolicitud
    participant UC as GestionarSolicitudesService<br/>@Transactional
    participant SOL as SolicitudPrestamo (dominio)
    participant CALC as CalculadoraAmortizacion
    participant PRE as Prestamo (dominio)
    participant RS as SolicitudPrestamoRepositorio
    participant RP as PrestamoRepositorio
    participant COR as CorrelativoPort
    participant AUD as AuditoriaPort
    participant DB as SQL Server

    AN->>NG: POST /api/v1/solicitudes/7/aprobar<br/>Bearer + {montoAprobado, plazo, tasa, motivo}
    NG->>SEC: reenvía con X-Forwarded-For
    SEC->>SEC: verifica firma HS256, emisor y vigencia
    SEC->>SEC: exige rol ADMIN o ANALISTA
    SEC->>CTRL: petición autenticada
    CTRL->>CTRL: @Valid sobre AprobarSolicitudRequest (rangos)
    CTRL->>MAP: aComando(peticion)
    MAP-->>CTRL: AprobarSolicitudCommand
    CTRL->>UC: aprobar(7, cmd, ContextoOperacion(usuario, ip))

    rect rgb(238, 244, 250)
    note over UC,DB: Una sola transacción
    UC->>RS: buscarPorId(7)
    RS->>DB: SELECT ... FROM solicitudes_prestamo WHERE id = :id
    DB-->>RS: fila
    RS-->>UC: SolicitudPrestamo

    UC->>RP: buscarPorSolicitudId(7)
    RP->>DB: SELECT ... FROM prestamos WHERE solicitud_id = :id
    DB-->>RP: vacío
    RP-->>UC: Optional.empty()
    note right of UC: si ya existiera → ReglaNegocioException (409)

    UC->>UC: los campos omitidos toman el valor solicitado
    UC->>SOL: aprobar(monto, plazo, tasa, usuario, motivo, ahora)
    note right of SOL: exige estado EN_PROCESO;<br/>monto aprobado ≤ solicitado;<br/>rangos de plazo y tasa
    SOL-->>UC: estado APROBADA + ResolucionSolicitud
    UC->>RS: guardar(solicitud)
    RS->>DB: UPDATE solicitudes_prestamo SET estado, fecha_resolucion, ...

    UC->>CALC: calcular(monto, plazo, tasa)
    CALC-->>UC: PlanAmortizacion (cuota, total, cuotas)
    UC->>COR: siguienteNumeroPrestamo()
    COR->>DB: SELECT NEXT VALUE FOR dbo.seq_prestamo
    DB-->>COR: 5
    COR-->>UC: "PR-001-2026-000005-0"
    UC->>PRE: Prestamo.nuevo(...)
    note right of PRE: nace VIGENTE, sin pagos;<br/>vencimiento = desembolso + plazo
    PRE-->>UC: Prestamo
    UC->>RP: guardar(prestamo)
    RP->>DB: INSERT INTO prestamos (...)

    UC->>AUD: registrar(SOLICITUD_APROBADA, usuario, ip)
    AUD->>DB: INSERT INTO auditoria (...)
    UC->>AUD: registrar(PRESTAMO_CREADO, usuario, ip)
    AUD->>DB: INSERT INTO auditoria (...)

    UC->>RS: buscarDetallePorId(7)
    RS->>DB: SELECT con JOIN a clientes y prestamos
    DB-->>RS: proyección
    RS-->>UC: SolicitudDetalle
    end

    UC-->>CTRL: SolicitudDetalle
    CTRL->>MAP: aRespuesta(detalle)
    MAP-->>CTRL: SolicitudResponse
    CTRL-->>AN: 200 OK + JSON
```

Si cualquier paso falla, la transacción se revierte **completa**, incluidos los dos
registros de auditoría: no queda constancia de una acción que nunca ocurrió.

#### Registrar un pago

```mermaid
sequenceDiagram
    autonumber
    actor CJ as Cajero (navegador)
    participant SEC as Cadena de seguridad<br/>(rol ADMIN o CAJERO)
    participant CTRL as PagoControlador
    participant UC as RegistrarPagosService<br/>@Transactional
    participant PRE as Prestamo (dominio)
    participant PAG as Pago (dominio)
    participant RP as PrestamoRepositorio
    participant RG as PagoRepositorio
    participant COR as CorrelativoPort
    participant AUD as AuditoriaPort
    participant DB as SQL Server

    CJ->>SEC: POST /api/v1/pagos {prestamoId, monto, observaciones}
    SEC->>CTRL: autenticado y autorizado
    CTRL->>CTRL: @Valid: monto ≥ 0.01, hasta 2 decimales
    CTRL->>UC: registrar(RegistrarPagoCommand, ContextoOperacion)

    rect rgb(238, 244, 250)
    note over UC,DB: Una sola transacción
    UC->>RP: buscarPorId(prestamoId)
    RP->>DB: SELECT ... FROM prestamos WHERE id = :id
    DB-->>RP: fila
    RP-->>UC: Prestamo
    note right of UC: si no existe → RecursoNoEncontradoException (404)

    UC->>PRE: getSaldoPendiente()
    PRE-->>UC: saldoAnterior
    UC->>PRE: aplicarPago(monto)
    note right of PRE: monto > 0;<br/>rechaza si ya está LIQUIDADO;<br/>rechaza si excede el saldo;<br/>al llegar a 0 pasa a LIQUIDADO
    PRE-->>UC: totalPagado actualizado
    UC->>PRE: getSaldoPendiente()
    PRE-->>UC: saldoPosterior

    UC->>COR: siguienteNumeroRecibo()
    COR->>DB: SELECT NEXT VALUE FOR dbo.seq_recibo
    COR-->>UC: "RC-001-2026-000008-9"
    UC->>PAG: Pago.nuevo(recibo, prestamoId, monto, EFECTIVO,<br/>saldoAnterior, saldoPosterior, usuario, obs, ahora)
    PAG-->>UC: Pago

    UC->>RP: guardar(prestamo)
    RP->>DB: UPDATE prestamos SET total_pagado, estado
    note right of DB: saldo_pendiente se recalcula solo<br/>(columna PERSISTED)
    UC->>RG: guardar(pago)
    RG->>DB: INSERT INTO pagos (...)
    note right of DB: CHECK ck_pagos_aritmetica:<br/>saldo_posterior = saldo_anterior - monto

    UC->>AUD: registrar(PAGO_REGISTRADO, usuario, ip)
    AUD->>DB: INSERT INTO auditoria (...)
    alt el préstamo quedó liquidado
        UC->>AUD: registrar(PRESTAMO_LIQUIDADO, usuario, ip)
        AUD->>DB: INSERT INTO auditoria (...)
    end

    UC->>RG: buscarDetallePorId(id)
    RG->>DB: SELECT con JOIN a prestamos y clientes
    RG-->>UC: PagoDetalle
    end

    UC-->>CTRL: PagoDetalle
    CTRL-->>CJ: 201 Created + recibo con saldos
```

---

## 4. Modelo de datos

Base **`CHN_Prestamos`**, esquema **`dbo`**. Seis tablas transaccionales más un catálogo de
parámetros. El flujo del negocio es:

```
cliente  ──►  solicitud  ──(al aprobarse)──►  préstamo  ──►  pagos
```

La fuente de verdad del esquema es la carpeta `database/` del repositorio; el `pom.xml` la
copia al *classpath* del backend durante el empaquetado (`../database/migration → db/migration`,
`../database/demo → db/demo`) para que Flyway la aplique y para que un DBA pueda ejecutar
los mismos scripts a mano.

### 4.1 Diccionario de datos

#### `dbo.clientes` — titulares del crédito

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `id` | `BIGINT IDENTITY(1,1)` | `pk_clientes` (PK agrupada) | Identificador interno |
| `nombre` | `NVARCHAR(60)` | `NOT NULL`, `ck_clientes_nombre` (≥ 2 tras recortar) | Nombres del cliente |
| `apellido` | `NVARCHAR(60)` | `NOT NULL`, `ck_clientes_apellido` (≥ 2) | Apellidos |
| `numero_identificacion` | `NVARCHAR(13)` | `NOT NULL`, `uq_clientes_identificacion`, `ck_clientes_identificacion` (13 dígitos exactos) | DPI de Guatemala. Identificador natural; no editable |
| `fecha_nacimiento` | `DATE` | `NOT NULL`, `ck_clientes_fecha_nacimiento` (≥ 1900-01-01) | La mayoría de edad la valida el dominio, porque depende de la fecha actual y un `CHECK` no determinista solo se evaluaría al insertar. No editable |
| `direccion` | `NVARCHAR(200)` | `NOT NULL`, `ck_clientes_direccion` (≥ 5) | Dirección de residencia |
| `correo_electronico` | `NVARCHAR(120)` | `NOT NULL`, `uq_clientes_correo`, `ck_clientes_correo` (patrón mínimo de correo y sin espacios) | Único. El dominio lo normaliza a minúsculas |
| `telefono` | `NVARCHAR(8)` | `NOT NULL`, `ck_clientes_telefono` (8 dígitos exactos) | Teléfono de contacto |
| `activo` | `BIT` | `NOT NULL`, `df_clientes_activo` = `1` | Estado del registro |
| `fecha_creacion` | `DATETIME2(0)` | `NOT NULL` | Alta del registro |
| `fecha_modificacion` | `DATETIME2(0)` | `NULL`, `ck_clientes_fechas` (nula o ≥ `fecha_creacion`) | Última edición |

Índices: `ix_clientes_apellido_nombre` (con `INCLUDE` de DPI, correo y activo, que cubre el
listado paginado), `ix_clientes_activo`, `ix_clientes_fecha_creacion DESC`.

#### `dbo.solicitudes_prestamo` — solicitudes y su resolución

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `id` | `BIGINT IDENTITY(1,1)` | `pk_solicitudes` | Identificador |
| `numero_solicitud` | `NVARCHAR(25)` | `NOT NULL`, `uq_solicitudes_numero`, `ck_solicitudes_numero` | Número oficial `SC-AAA-AAAA-NNNNNN-D` (`seq_solicitud`) |
| `cliente_id` | `BIGINT` | `NOT NULL`, `fk_solicitudes_cliente` → `clientes(id)` `NO ACTION` | Solicitante |
| `monto_solicitado` | `DECIMAL(15,2)` | `NOT NULL`, `ck_solicitudes_monto` (1 000.00 – 5 000 000.00) | Monto pedido, en GTQ |
| `plazo_meses` | `INT` | `NOT NULL`, `ck_solicitudes_plazo` (6 – 360) | Plazo pedido |
| `tasa_interes_anual` | `DECIMAL(5,2)` | `NOT NULL`, `ck_solicitudes_tasa` (0.01 – 100.00) | Tasa nominal anual, en porcentaje |
| `tipo_prestamo` | `NVARCHAR(20)` | `NOT NULL`, `ck_solicitudes_tipo` (cotejo `Latin1_General_BIN2` desde `V5`) | `PERSONAL`, `HIPOTECARIO`, `VEHICULAR`, `EMPRESARIAL`, `EDUCATIVO`, en mayúsculas exactas. La vista `vw_cartera_por_tipo` agrupa por esta columna |
| `destino` | `NVARCHAR(200)` | `NOT NULL`, `ck_solicitudes_destino` (≥ 5) | Finalidad del financiamiento |
| `ingreso_mensual_declarado` | `DECIMAL(15,2)` | `NOT NULL`, `ck_solicitudes_ingreso` (> 0) | Base de la evaluación de capacidad de pago |
| `estado` | `NVARCHAR(15)` | `NOT NULL`, `ck_solicitudes_estado` | `EN_PROCESO`, `APROBADA`, `RECHAZADA` |
| `fecha_solicitud` | `DATETIME2(0)` | `NOT NULL` | Ingreso de la solicitud |
| `observaciones` | `NVARCHAR(500)` | `NULL` | Notas del asesor que captura |
| `fecha_resolucion` | `DATETIME2(0)` | `NULL`, `ck_solicitudes_fecha_resolucion` (≥ `fecha_solicitud`) | Nula mientras está `EN_PROCESO` |
| `usuario_resolucion` | `NVARCHAR(50)` | `NULL` | Analista que resolvió |
| `monto_aprobado` | `DECIMAL(15,2)` | `NULL`, `ck_solicitudes_monto_aprobado` (> 0 y ≤ `monto_solicitado`) | Solo en `APROBADA` |
| `plazo_aprobado_meses` | `INT` | `NULL`, `ck_solicitudes_plazo_aprobado` (6 – 360) | Solo en `APROBADA` |
| `tasa_aprobada` | `DECIMAL(5,2)` | `NULL`, `ck_solicitudes_tasa_aprobada` (0.01 – 100.00) | Solo en `APROBADA` |
| `motivo_resolucion` | `NVARCHAR(500)` | `NULL` | Obligatorio y de 10 caracteres como mínimo en `RECHAZADA`; opcional en `APROBADA` |

**Por qué `ck_solicitudes_tipo` compara en binario.** La base usa el cotejo
`Modern_Spanish_CI_AI`, insensible a mayúsculas y acentos, y la restricción original de `V1`
comparaba con él: una carga hecha fuera de la aplicación podía guardar `personal` o
`Personal` y cumplirla. Java lee ese texto como enum (`@Enumerated` y
`TipoPrestamo.valueOf`), que sí distingue mayúsculas, así que esa fila rompía la lectura de
la solicitud y el tablero completo. `V5__endurecer_ck_solicitudes_tipo.sql` pasa a mayúsculas
las filas con otra capitalización y rehace la restricción con `COLLATE Latin1_General_BIN2`,
el mismo recurso que `V1` ya usa para los números oficiales. Una variante con acentos u otro
texto no se corrige a ciegas: hace fallar la migración para revisarla a mano
([13.2](#132-persistencia-y-base-de-datos)).

La restricción compuesta **`ck_solicitudes_coherencia_resolucion`** impide que el estado y
los datos de resolución se contradigan:

- `EN_PROCESO` → sin fecha, sin usuario y sin montos aprobados.
- `APROBADA` → con fecha, usuario, monto, plazo y tasa aprobados.
- `RECHAZADA` → con fecha, usuario y motivo de 10 caracteres o más, y **sin** montos aprobados.

Índices: `ix_solicitudes_cliente` (`cliente_id`, `fecha_solicitud DESC`),
`ix_solicitudes_estado`, `ix_solicitudes_fecha DESC`.

#### `dbo.prestamos` — préstamos desembolsados

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `id` | `BIGINT IDENTITY(1,1)` | `pk_prestamos` | Identificador |
| `numero_prestamo` | `NVARCHAR(25)` | `NOT NULL`, `uq_prestamos_numero`, `ck_prestamos_numero` | Número oficial `PR-AAA-AAAA-NNNNNN-D` (`seq_prestamo`) |
| `solicitud_id` | `BIGINT` | `NOT NULL`, `uq_prestamos_solicitud` (**UNIQUE**), `fk_prestamos_solicitud` → `solicitudes_prestamo(id)` `NO ACTION` | El `UNIQUE` impone la relación 1 a 1: una solicitud genera **un** préstamo |
| `cliente_id` | `BIGINT` | `NOT NULL`, `fk_prestamos_cliente` → `clientes(id)` `NO ACTION` | Desnormalizado a propósito: evita un `JOIN` en todas las consultas de cartera |
| `monto_aprobado` | `DECIMAL(15,2)` | `NOT NULL`, `ck_prestamos_monto` (> 0) | Capital financiado |
| `plazo_meses` | `INT` | `NOT NULL`, `ck_prestamos_plazo` (6 – 360) | Plazo aprobado |
| `tasa_interes_anual` | `DECIMAL(5,2)` | `NOT NULL`, `ck_prestamos_tasa` (0.01 – 100.00) | Tasa aprobada, congelada al desembolso |
| `cuota_mensual` | `DECIMAL(15,2)` | `NOT NULL`, `ck_prestamos_cuota` (> 0) | Cuota fija por sistema francés |
| `monto_total_a_pagar` | `DECIMAL(15,2)` | `NOT NULL`, `ck_prestamos_total` (> 0) | Capital más intereses pactados |
| `total_pagado` | `DECIMAL(15,2)` | `NOT NULL`, `df_prestamos_total_pagado` = `0`, `ck_prestamos_pagado` (entre 0 y `monto_total_a_pagar`) | Acumulado de abonos |
| `saldo_pendiente` | `AS (monto_total_a_pagar - total_pagado) PERSISTED` | `NOT NULL` | **Columna calculada.** No se escribe nunca; no se mapea en JPA |
| `estado` | `NVARCHAR(15)` | `NOT NULL`, `ck_prestamos_estado`, `ck_prestamos_liquidado` (`LIQUIDADO` exige `total_pagado = monto_total_a_pagar`) | `VIGENTE`, `LIQUIDADO` |
| `fecha_desembolso` | `DATE` | `NOT NULL` | Fecha de entrega |
| `fecha_vencimiento` | `DATE` | `NOT NULL`, `ck_prestamos_vencimiento` (> `fecha_desembolso`) | Desembolso más el plazo en meses |
| `fecha_creacion` | `DATETIME2(0)` | `NOT NULL` | Alta del registro |

Índices: `ix_prestamos_cliente_estado` (con `INCLUDE` de totales y saldo, que es el que usa
el conteo de préstamos vigentes del evaluador de capacidad de pago),
`ix_prestamos_estado`, `ix_prestamos_fecha_desembolso DESC`,
`ix_prestamos_fecha_vencimiento`.

> **Cómo se filtra por saldo pendiente.** El listado de préstamos acepta `saldoMinimo` y
> `saldoMaximo` ([5.2](#52-filtros-de-búsqueda-de-los-listados)), pero la consulta **no**
> nombra la columna `saldo_pendiente`: filtra por la expresión equivalente
> `(p.montoTotalAPagar - p.totalPagado)`.
>
> El motivo es el de [3.5-e](#35-decisiones-de-diseño-justificación-y-contrapartida):
> `saldo_pendiente` es una columna calculada `PERSISTED`, de **solo lectura**, y por eso no
> está mapeada en `PrestamoEntidad` —si lo estuviera, Hibernate intentaría escribirla en
> cada `INSERT`/`UPDATE` y SQL Server rechazaría la sentencia—. Un atributo que no existe en
> la entidad no puede aparecer en una consulta JPQL, así que el criterio se expresa con las
> dos columnas que sí están mapeadas. La resta es exactamente la definición de la columna
> calculada, de modo que el filtro y el valor almacenado no pueden discrepar.
>
> La alternativa —mapear la columna con `insertable = false, updatable = false`— también
> funcionaría y permitiría escribir `p.saldoPendiente` en el JPQL, pero dejaría en la
> entidad un campo que el dominio ya sabe derivar (`Prestamo.getSaldoPendiente()`) y que
> alguien podría intentar asignar. Se prefirió no mapearla y dejar la expresión a la vista,
> documentada en el Javadoc de `PrestamoJpaRepositorio.buscarDetalle` y en
> `FiltroPrestamo.tieneRangoDeSaldo()`.
>
> El costo es que el filtro por saldo **no puede aprovechar el índice** sobre la columna
> calculada: la expresión obliga a evaluar fila por fila. Es asumible con el volumen de una
> cartera de préstamos, y es el mismo compromiso que ya se acepta con los `LIKE` del texto
> libre. Los reportes en SQL, que no pasan por JPA, sí usan `saldo_pendiente` directamente
> (`vw_resumen_general`, `vw_prestamos_saldo`) y se benefician del índice.

#### `dbo.pagos` — recibos aplicados

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `id` | `BIGINT IDENTITY(1,1)` | `pk_pagos` | Identificador |
| `numero_recibo` | `NVARCHAR(25)` | `NOT NULL`, `uq_pagos_recibo`, `ck_pagos_recibo` | Número oficial `RC-AAA-AAAA-NNNNNN-D` (`seq_recibo`) |
| `prestamo_id` | `BIGINT` | `NOT NULL`, `fk_pagos_prestamo` → `prestamos(id)` `NO ACTION` | Préstamo abonado |
| `monto` | `DECIMAL(15,2)` | `NOT NULL`, `ck_pagos_monto` (> 0) | Importe del recibo. Que no exceda el saldo lo valida el dominio |
| `fecha_pago` | `DATETIME2(0)` | `NOT NULL` | Momento del cobro |
| `forma_pago` | `NVARCHAR(15)` | `NOT NULL`, `ck_pagos_forma` (`EFECTIVO`) | Único medio en el alcance actual |
| `saldo_anterior` | `DECIMAL(15,2)` | `NOT NULL`, `ck_pagos_saldos_positivos` (≥ 0) | Corte histórico antes del abono |
| `saldo_posterior` | `DECIMAL(15,2)` | `NOT NULL`, `ck_pagos_saldos_positivos`, `ck_pagos_aritmetica` (`= saldo_anterior - monto`) | Corte histórico después del abono |
| `usuario_registro` | `NVARCHAR(50)` | `NOT NULL` | Cajero que cobró |
| `observaciones` | `NVARCHAR(300)` | `NULL` | Notas del cajero |

Guardar los dos saldos en el recibo es una decisión deliberada: permite **reimprimirlo
exactamente como se emitió**, sin recalcular nada y sin depender del estado actual del
préstamo. El `CHECK` de aritmética garantiza que el recibo siempre cuadre.

Índices: `ix_pagos_prestamo` (`prestamo_id`, `fecha_pago DESC`), `ix_pagos_fecha DESC`.

#### `dbo.usuarios` — operadores del sistema

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `id` | `BIGINT IDENTITY(1,1)` | `pk_usuarios` | Identificador |
| `username` | `NVARCHAR(50)` | `NOT NULL`, `uq_usuarios_username`, `ck_usuarios_username` (≥ 3) | El dominio además lo pasa a minúsculas y exige el patrón de letras, números, punto, guion y guion bajo |
| `password_hash` | `NVARCHAR(100)` | `NOT NULL` | **Siempre** un hash BCrypt de fuerza 12 (60 caracteres). Nunca la contraseña |
| `nombre_completo` | `NVARCHAR(120)` | `NOT NULL` | Nombre para mostrar |
| `correo` | `NVARCHAR(120)` | `NOT NULL`, `ck_usuarios_correo` | Correo institucional |
| `rol` | `NVARCHAR(15)` | `NOT NULL`, `ck_usuarios_rol` | `ADMIN`, `ANALISTA`, `CAJERO`, `CONSULTA` |
| `activo` | `BIT` | `NOT NULL`, `df_usuarios_activo` = `1` | Un usuario inactivo no puede autenticarse |
| `intentos_fallidos` | `INT` | `NOT NULL`, `df_usuarios_intentos` = `0`, `ck_usuarios_intentos` (≥ 0) | Contador consecutivo; se reinicia al bloquear y al entrar bien |
| `bloqueado_hasta` | `DATETIME2(0)` | `NULL` | Fin del bloqueo temporal |
| `ultimo_acceso` | `DATETIME2(0)` | `NULL` | Último inicio de sesión correcto |

**Los usuarios no se insertan por script.** Los crea la aplicación al arrancar
(`CargadorUsuariosIniciales`) para que la contraseña se almacene ya cifrada y nunca quede
en texto plano dentro de un archivo versionado. Índice: `ix_usuarios_rol` (la búsqueda por
`username`, que es la del login, ya la cubre el `UNIQUE`).

#### `dbo.auditoria` — bitácora

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `id` | `BIGINT IDENTITY(1,1)` | `pk_auditoria` | Identificador |
| `usuario` | `NVARCHAR(50)` | `NOT NULL` | `username` autenticado, `anonimo` en un login fallido sin sesión, o `trigger_bd` si lo registró la base |
| `accion` | `NVARCHAR(50)` | `NOT NULL` | Constante de `AccionesAuditoria` |
| `entidad` | `NVARCHAR(50)` | `NOT NULL` | `CLIENTE`, `SOLICITUD`, `PRESTAMO`, `PAGO`, `USUARIO` (o `clientes` en el trigger) |
| `entidad_id` | `NVARCHAR(50)` | `NULL` | Identificador afectado |
| `detalle` | `NVARCHAR(1000)` | `NULL` | Resumen legible. **Nunca contraseñas ni tokens** |
| `direccion_ip` | `NVARCHAR(45)` | `NULL` | 45 caracteres: alcanza para una IPv6 con mapeo IPv4 |
| `fecha` | `DATETIME2(0)` | `NOT NULL` | Momento del evento |

Tabla de **solo inserción y consulta**: no se actualiza ni se borra, para que la traza sea
confiable. Se expone únicamente en `GET /api/v1/auditoria`, restringido al rol `ADMIN`.
Índices: `ix_auditoria_fecha DESC`, `ix_auditoria_usuario`, `ix_auditoria_entidad`.

Acciones registradas (constantes de `AccionesAuditoria`):
`LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `CLIENTE_CREADO`, `CLIENTE_ACTUALIZADO`,
`CLIENTE_ELIMINADO`, `SOLICITUD_CREADA`, `SOLICITUD_APROBADA`, `SOLICITUD_RECHAZADA`,
`PRESTAMO_CREADO`, `PRESTAMO_LIQUIDADO`, `PAGO_REGISTRADO`; más `CLIENTE_ELIMINADO_BD`,
que inserta el trigger.

#### `dbo.parametros` — catálogo de negocio

| Columna | Tipo | Restricción | Descripción |
|---|---|---|---|
| `clave` | `NVARCHAR(50)` | `pk_parametros` | Nombre del parámetro |
| `valor` | `NVARCHAR(200)` | `NOT NULL`, `ck_parametros_valor` (no vacío tras recortar) | Valor como texto; lo interpreta quien lo consume |
| `descripcion` | `NVARCHAR(200)` | `NULL` | Explicación para el área de negocio |

Contenido que carga `V3`:

| Clave | Valor | Significado |
|---|---|---|
| `TASA_BASE_ANUAL` | `12.50` | Tasa anual de referencia sugerida, en porcentaje |
| `PLAZO_MINIMO_MESES` | `6` | Plazo mínimo aceptado |
| `PLAZO_MAXIMO_MESES` | `360` | Plazo máximo aceptado |
| `MONTO_MINIMO` | `1000.00` | Monto mínimo solicitable |
| `MONTO_MAXIMO` | `5000000.00` | Monto máximo solicitable |
| `PORCENTAJE_MAXIMO_ENDEUDAMIENTO` | `40` | Porcentaje máximo del ingreso que puede comprometer la cuota |
| `MAXIMO_PRESTAMOS_VIGENTES` | `3` | Préstamos vigentes que el cliente no debe alcanzar |
| `MONEDA` | `GTQ` | Moneda de operación |

Son los mismos límites que valida el dominio Java (`SolicitudPrestamo.MONTO_MINIMO`,
`EvaluadorCapacidadPago.PORCENTAJE_MAXIMO_ENDEUDAMIENTO`, …). Se publican aquí para que el
área de negocio los consulte y para que un reporte en SQL no lleve números mágicos.

> La carga es `INSERT ... WHERE NOT EXISTS` y no un `MERGE` con `UPDATE`: si un operador
> ajusta un valor en producción, una re-ejecución del script no se lo revierte.

### 4.2 Diagrama entidad-relación

![Diagrama entidad-relación](img/diagrama-entidad-relacion.svg)

El mismo modelo en Mermaid, para que se renderice directamente en GitHub
(`PK` = llave primaria, `FK` = llave foránea, `UK` = restricción única):

```mermaid
erDiagram
    clientes {
        bigint    id                        PK "IDENTITY(1,1)"
        nvarchar  nombre                       "NVARCHAR(60) obligatorio, 2 a 60"
        nvarchar  apellido                     "NVARCHAR(60) obligatorio, 2 a 60"
        nvarchar  numero_identificacion     UK "NVARCHAR(13) DPI, 13 digitos exactos"
        date      fecha_nacimiento             "obligatoria, mayor de 18 anios"
        nvarchar  direccion                    "NVARCHAR(200) obligatoria, 5 a 200"
        nvarchar  correo_electronico        UK "NVARCHAR(120) formato validado"
        nvarchar  telefono                     "NVARCHAR(8) 8 digitos exactos"
        bit       activo                       "DEFAULT 1"
        datetime2 fecha_creacion               "DATETIME2(0) obligatoria"
        datetime2 fecha_modificacion           "DATETIME2(0) nula hasta la primera edicion"
    }

    solicitudes_prestamo {
        bigint    id                        PK "IDENTITY(1,1)"
        nvarchar  numero_solicitud          UK "NVARCHAR(25) SC-001-2026-000001-3"
        bigint    cliente_id                FK "clientes.id - NO ACTION"
        decimal   monto_solicitado             "DECIMAL(15,2) 1000.00 a 5000000.00"
        int       plazo_meses                  "6 a 360"
        decimal   tasa_interes_anual           "DECIMAL(5,2) 0.01 a 100.00"
        nvarchar  tipo_prestamo                "PERSONAL HIPOTECARIO VEHICULAR EMPRESARIAL EDUCATIVO"
        nvarchar  destino                      "NVARCHAR(200) obligatorio, 5 a 200"
        decimal   ingreso_mensual_declarado    "DECIMAL(15,2) mayor que 0"
        nvarchar  estado                       "EN_PROCESO APROBADA RECHAZADA"
        datetime2 fecha_solicitud              "DATETIME2(0) obligatoria"
        nvarchar  observaciones                "NVARCHAR(500) opcional"
        datetime2 fecha_resolucion             "nula si EN_PROCESO"
        nvarchar  usuario_resolucion           "NVARCHAR(50) nulo si EN_PROCESO"
        decimal   monto_aprobado               "solo si APROBADA, no mayor al solicitado"
        int       plazo_aprobado_meses         "solo si APROBADA, 6 a 360"
        decimal   tasa_aprobada                "solo si APROBADA"
        nvarchar  motivo_resolucion            "NVARCHAR(500) obligatorio si RECHAZADA"
    }

    prestamos {
        bigint    id                        PK "IDENTITY(1,1)"
        nvarchar  numero_prestamo           UK "NVARCHAR(25) PR-001-2026-000001-9"
        bigint    solicitud_id              FK "solicitudes_prestamo.id - UNIQUE: impone el 1 a 1"
        bigint    cliente_id                FK "clientes.id - NO ACTION"
        decimal   monto_aprobado               "DECIMAL(15,2) mayor que 0"
        int       plazo_meses                  "6 a 360"
        decimal   tasa_interes_anual           "DECIMAL(5,2) 0.01 a 100.00"
        decimal   cuota_mensual                "DECIMAL(15,2) sistema frances"
        decimal   monto_total_a_pagar          "DECIMAL(15,2) capital mas intereses"
        decimal   total_pagado                 "DECIMAL(15,2) DEFAULT 0, nunca mayor al total"
        decimal   saldo_pendiente              "CALCULADA PERSISTED: total menos pagado"
        nvarchar  estado                       "VIGENTE LIQUIDADO"
        date      fecha_desembolso             "obligatoria"
        date      fecha_vencimiento            "desembolso mas el plazo en meses"
        datetime2 fecha_creacion               "DATETIME2(0) obligatoria"
    }

    pagos {
        bigint    id                        PK "IDENTITY(1,1)"
        nvarchar  numero_recibo             UK "NVARCHAR(25) RC-001-2026-000001-4"
        bigint    prestamo_id               FK "prestamos.id - NO ACTION"
        decimal   monto                        "DECIMAL(15,2) mayor que 0, no mayor al saldo"
        datetime2 fecha_pago                   "DATETIME2(0) obligatoria"
        nvarchar  forma_pago                   "EFECTIVO"
        decimal   saldo_anterior               "DECIMAL(15,2) saldo antes del abono"
        decimal   saldo_posterior              "DECIMAL(15,2) igual a anterior menos monto"
        nvarchar  usuario_registro             "NVARCHAR(50) quien cobro"
        nvarchar  observaciones                "NVARCHAR(300) opcional"
    }

    usuarios {
        bigint    id                        PK "IDENTITY(1,1)"
        nvarchar  username                  UK "NVARCHAR(50) minimo 3 caracteres"
        nvarchar  password_hash                "NVARCHAR(100) hash BCrypt fuerza 12"
        nvarchar  nombre_completo              "NVARCHAR(120) obligatorio"
        nvarchar  correo                       "NVARCHAR(120) obligatorio"
        nvarchar  rol                          "ADMIN ANALISTA CAJERO CONSULTA"
        bit       activo                       "DEFAULT 1"
        int       intentos_fallidos            "DEFAULT 0, bloqueo a los 5"
        datetime2 bloqueado_hasta              "fin del bloqueo de 15 minutos"
        datetime2 ultimo_acceso                "ultimo login exitoso"
    }

    auditoria {
        bigint    id                        PK "IDENTITY(1,1)"
        nvarchar  usuario                      "NVARCHAR(50) username, anonimo o trigger_bd"
        nvarchar  accion                       "NVARCHAR(50) LOGIN_EXITOSO PAGO_REGISTRADO"
        nvarchar  entidad                      "NVARCHAR(50) entidad afectada"
        nvarchar  entidad_id                   "NVARCHAR(50) id afectado, opcional"
        nvarchar  detalle                      "NVARCHAR(1000) sin contrasenas ni tokens"
        nvarchar  direccion_ip                 "NVARCHAR(45) soporta IPv6"
        datetime2 fecha                        "DATETIME2(0) momento del evento"
    }

    parametros {
        nvarchar  clave                     PK "NVARCHAR(50) MONTO_MAXIMO TASA_BASE_ANUAL"
        nvarchar  valor                        "NVARCHAR(200) obligatorio"
        nvarchar  descripcion                  "NVARCHAR(200) opcional"
    }

    clientes             ||--o{ solicitudes_prestamo : "presenta"
    clientes             ||--o{ prestamos            : "es titular de"
    solicitudes_prestamo ||--o| prestamos            : "al aprobarse origina"
    prestamos            ||--o{ pagos                : "recibe abonos en"
    usuarios             ||..o{ auditoria            : "genera (sin FK, por username)"
```

**Lectura de las cardinalidades**

| Relación | Cardinalidad | Cómo se impone |
|---|---|---|
| `clientes` → `solicitudes_prestamo` | 1 a N (0 o más) | `fk_solicitudes_cliente` |
| `clientes` → `prestamos` | 1 a N (0 o más) | `fk_prestamos_cliente`. Camino desnormalizado, redundante con la vía de la solicitud |
| `solicitudes_prestamo` → `prestamos` | 1 a 0-o-1 | `fk_prestamos_solicitud` **más** `uq_prestamos_solicitud`. Solo una solicitud aprobada genera préstamo, y genera exactamente uno |
| `prestamos` → `pagos` | 1 a N (0 o más) | `fk_pagos_prestamo`. Un préstamo puede no tener pagos |
| `usuarios` → `auditoria` | 1 a N, **sin llave foránea** | Deliberado: la bitácora referencia al usuario por `username` y debe sobrevivir al borrado de ese usuario. Una traza que se puede borrar no es una traza |

`parametros` no tiene relaciones: es un catálogo clave/valor.

> `clientes` alcanza `pagos` por dos caminos, y de ahí que todas las llaves foráneas sean
> `ON DELETE NO ACTION` y que el borrado se ejecute en orden explícito desde el caso de uso
> (ver [3.5-f](#35-decisiones-de-diseño-justificación-y-contrapartida)).

### 4.3 Objetos programables Transact-SQL

#### Secuencias (`V1`)

| Objeto | Genera | Formato | Por qué una secuencia |
|---|---|---|---|
| `dbo.seq_solicitud` | Correlativo de solicitud | `SC-001-2026-000001-3` | Atómica, no bloquea la tabla y no repite valores con usuarios concurrentes, a diferencia de un `MAX(...) + 1` |
| `dbo.seq_prestamo` | Correlativo de préstamo | `PR-001-2026-000001-9` | ídem |
| `dbo.seq_recibo` | Correlativo de recibo | `RC-001-2026-000001-4` | ídem |

Las tres se crean `AS BIGINT START WITH 1 INCREMENT BY 1 MINVALUE 1 NO CYCLE CACHE 10`.
`V900` las adelanta con `RESTART WITH` para que los correlativos de la demostración no
choquen con los que genere la aplicación.

#### Funciones (`V2`)

| Objeto | Firma | Propósito |
|---|---|---|
| `dbo.fn_calcular_cuota` | `(@monto DECIMAL(15,2), @tasa_anual DECIMAL(5,2), @plazo_meses INT)` devuelve `DECIMAL(15,2)` | Cuota mensual por sistema francés, con `i = tasa/100/12`. Con tasa 0 reparte el capital (`monto/n`). Es la **réplica exacta** de `CalculadoraAmortizacion` en Java, de modo que un reporte en SQL y la API nunca dan cifras distintas. Devuelve `NULL` ante entradas inválidas en lugar de fallar, porque se usa dentro de vistas donde una excepción abortaría toda la consulta |
| `dbo.fn_total_plan` | Misma firma, devuelve `DECIMAL(15,2)` | Total a pagar como **suma real del plan**, no como cuota × plazo. Recorre el plan mes a mes con el mismo criterio que `CalculadoraAmortizacion`, incluido el ajuste de la última cuota, así que coincide al centavo con `plan.montoTotal()`. La usa `V900` para el `monto_total_a_pagar` de los préstamos de demostración. Ver [6.2](#62-la-fórmula-de-amortización) |

`ROUND` de T-SQL redondea *la mitad hacia arriba*, igual que el `RoundingMode.HALF_UP` que
usa `Montos.normalizar` en el dominio. Los cálculos intermedios se hacen en coma flotante
(`1.0E0`) y solo el resultado se lleva a `DECIMAL(15,2)`.

#### Vistas (`V2`)

| Objeto | Devuelve | Consumidor |
|---|---|---|
| `dbo.vw_resumen_general` | **Una sola fila** con los 9 indicadores del tablero. Todos los montos pasan por `ISNULL`, así que nunca son `NULL` aunque la base esté vacía | `ResumenJpaRepositorio` → `GET /api/v1/resumen` (totales) |
| `dbo.vw_prestamos_saldo` | Cartera con el cliente resuelto, saldo, `porcentaje_pagado`, `cuotas_cubiertas`, `dias_plazo_total`, `dias_transcurridos` y `dias_para_vencimiento` | Reportes y el encabezado de `sp_estado_cuenta_prestamo` |
| `dbo.vw_solicitudes_detalle` | Solicitudes con datos del cliente y de la resolución, `dias_resolucion`, `cuota_estimada` (calculada con `fn_calcular_cuota`) y el `numero_prestamo` si llegó a desembolso | Consultas de seguimiento; evita repetir el mismo `JOIN` |

`vw_resumen_general` calcula los nueve indicadores con subconsultas independientes y un solo
viaje a la base, en lugar de varios `COUNT` y `SUM` lanzados desde Java. El saldo por cobrar
suma **solo** los préstamos `VIGENTE`, porque un liquidado ya no debe nada.

#### Vistas del tablero (`V4`)

| Objeto | Devuelve | Consumidor |
|---|---|---|
| `dbo.vw_cartera_por_tipo` | Una fila por tipo de préstamo **con préstamos**: `tipo_prestamo`, `cantidad_prestamos`, `monto_aprobado`, `saldo_pendiente` y `total_recuperado` | `ResumenJpaRepositorio.carteraPorTipo()` → `carteraPorTipo` de `GET /api/v1/resumen` |
| `dbo.vw_recaudacion_mensual` | Una fila por mes calendario **con pagos**: `anio`, `mes`, `cantidad_pagos` y `monto_recaudado` | `ResumenJpaRepositorio.recaudacionMensual(desde, hasta)` → `recaudacionMensual` de `GET /api/v1/resumen` |

- **`vw_cartera_por_tipo`** une `prestamos` con `solicitudes_prestamo` por `solicitud_id`,
  porque el tipo vive en la solicitud de origen. La relación es 1 a 1 (`uq_prestamos_solicitud`),
  así que el `JOIN` no duplica importes. `monto_aprobado` es el capital desembolsado
  (`prestamos.monto_aprobado`), no el solicitado. El saldo suma todos los estados: un
  préstamo `LIQUIDADO` tiene saldo cero por `ck_prestamos_liquidado`, así que no altera el
  total. Sobre los mismos datos, la suma de los tipos coincide con `monto_total_aprobado`,
  `saldo_pendiente_total` y `total_recuperado` de `vw_resumen_general` (bajo concurrencia
  puede separarse por un pago entre dos lecturas: ver
  [3.5-m](#35-decisiones-de-diseño-justificación-y-contrapartida)).
- **`vw_recaudacion_mensual`** agrupa `pagos` por `YEAR(fecha_pago)` y `MONTH(fecha_pago)`.
  `fecha_pago` se guarda ya en hora de Guatemala (`hibernate.jdbc.time_zone`), así que un
  pago de las 23:30 del último día no se corre al mes siguiente. El índice `ix_pagos_fecha`
  (`fecha_pago` con `INCLUDE (prestamo_id, monto)`) cubre la consulta.
- **Ninguna de las dos rellena huecos.** Un tipo sin préstamos o un mes sin pagos no produce
  fila: los completa con ceros el dominio
  ([3.5-m](#35-decisiones-de-diseño-justificación-y-contrapartida)). El adaptador acota los
  meses con `WHERE (anio * 100 + mes) BETWEEN :desde AND :hasta` y los ordena con
  `ORDER BY anio, mes`.
- Todos los importes se convierten a `DECIMAL(18,2)` y los conteos a `BIGINT`, para que la
  proyección de Spring Data reciba siempre el mismo tipo.

#### Procedimientos (`V2`)

| Objeto | Parámetro | Devuelve | Error |
|---|---|---|---|
| `dbo.sp_estado_cuenta_prestamo` | `@prestamo_id BIGINT` | 2 conjuntos: encabezado del préstamo (desde `vw_prestamos_saldo`) e histórico de pagos con `numero_movimiento`, **más antiguo primero** porque es un estado de cuenta | `THROW 50001` con mensaje claro si el préstamo no existe |
| `dbo.sp_historial_pagos_cliente` | `@cliente_id BIGINT` | 2 conjuntos: pagos de todos sus préstamos (más reciente primero) y una fila de totales (`cantidad_pagos`, `total_pagado`, `saldo_pendiente_total`, `prestamos_vigentes`, `ultimo_pago`) | `THROW 50001` si el cliente no existe |

Ambos usan `SET NOCOUNT ON` para que los mensajes de filas afectadas no se confundan con
conjuntos de resultados adicionales. En `sp_historial_pagos_cliente` cada total se calcula
con su propia subconsulta: unir `prestamos` con sus múltiples `pagos` en una sola consulta
duplicaría importes.

#### Trigger (`V2`)

| Objeto | Evento | Efecto |
|---|---|---|
| `dbo.tr_clientes_auditoria_delete` | `AFTER DELETE ON dbo.clientes` | Inserta en `auditoria` una fila por cliente eliminado (`usuario = 'trigger_bd'`, `accion = 'CLIENTE_ELIMINADO_BD'`) con nombre, DPI, correo y el *login* de la sesión (`SUSER_SNAME()`). Es una inserción en conjunto desde `deleted`, sin cursores |

Su razón de ser: garantizar la traza del borrado **incluso si se ejecuta fuera de la
aplicación** (por ejemplo desde SSMS). La aplicación audita además la misma operación con
el usuario real y su dirección IP.

Todos los objetos programables se crean con `CREATE OR ALTER`, así que la migración se puede
re-ejecutar sin error y el objeto queda siempre en su última versión.

### 4.4 Estrategia de migraciones con Flyway

#### Configuración

```yaml
spring:
  flyway:
    enabled: true
    locations: ${migraciones.locations-${app.datos-demo}:classpath:db/migration}
    baseline-on-migrate: true
    ignore-migration-patterns: "*:missing"
    out-of-order: true
    validate-on-migrate: true

migraciones:
  locations-true: classpath:db/migration,classpath:db/demo
  locations-false: classpath:db/migration
```

El `locations` usa un **placeholder anidado**: el valor booleano de `app.datos-demo` forma
parte del nombre de la clave que se resuelve. Así un solo interruptor (`APP_DATOS_DEMO`)
decide si se cargan los datos de prueba. El valor debe escribirse en minúsculas
(`true`/`false`) porque forma parte de la clave; ante cualquier otro valor se aplica el
respaldo `classpath:db/migration`, es decir solo el esquema.

| Ajuste | Efecto |
|---|---|
| `validate-on-migrate: true` | Si un script ya aplicado se modifica, su *checksum* cambia y **el arranque falla**, en lugar de dejar la base en un estado desconocido |
| `baseline-on-migrate: true` | Permite adoptar una base que ya tiene objetos, por ejemplo si un DBA ejecutó los scripts a mano antes del primer arranque |
| `ignore-migration-patterns: "*:missing"` | Permite arrancar aunque un script ya aplicado no esté presente en el *classpath*, por ejemplo al desplegar una versión que reorganizó carpetas |
| `out-of-order: true` | Aplica una versión de esquema **menor** que otra ya aplicada. Hace falta porque los datos de demostración son `V900`: en una base que ya los cargó, `V4` y `V5` quedan por debajo y, sin esta opción, Flyway las marcaría como ignoradas y la validación impediría arrancar hasta recrear el volumen. Con ella se aplican en su turno sin tocar los datos |

Flyway lleva el control en `dbo.flyway_schema_history`, con la versión, la descripción, el
*checksum* y el resultado de cada migración.

#### Orden de aplicación

| Orden | Script | Carpeta y destino en el *classpath* | Contenido | ¿Siempre? |
|---|---|---|---|---|
| — | `00_crear_base_datos.sql` | `database/` (no es migración) | Crea la base `CHN_Prestamos`, el login y el usuario de aplicación con `db_owner` **solo** sobre esa base. Lo ejecuta una persona o el servicio `db-init` con `sqlcmd` y credenciales de servidor | Una vez, antes de todo |
| 1 | `V1__esquema_tablas.sql` | `database/migration` → `db/migration` | 6 tablas, restricciones `CHECK`, llaves foráneas, 16 índices no agrupados, 3 secuencias y documentación en el catálogo (*extended properties*) | Sí |
| 2 | `V2__vistas_funciones_procedimientos.sql` | ídem | 2 funciones, 3 vistas, 2 procedimientos, 1 trigger | Sí |
| 3 | `V3__datos_iniciales.sql` | ídem | Tabla `parametros` y sus 8 parámetros de negocio | Sí |
| 4 | `V4__vistas_tablero.sql` | ídem | 2 vistas con las series del tablero: `vw_cartera_por_tipo` y `vw_recaudacion_mensual` | Sí |
| 5 | `V5__endurecer_ck_solicitudes_tipo.sql` | ídem | Normaliza a mayúsculas `solicitudes_prestamo.tipo_prestamo` y rehace `ck_solicitudes_tipo` con cotejo binario | Sí |
| 6 | `V900__datos_demo.sql` | `database/demo` → `db/demo` | 8 clientes, 10 solicitudes, 4 préstamos, 7 pagos | **Solo si `APP_DATOS_DEMO=true`** |

En una base nueva el orden es el de la tabla. En una base que ya tenía `V900` (por ejemplo,
el volumen de una versión anterior), `V4` y `V5` se aplican después, fuera de orden, gracias a
`out-of-order: true`; el resultado es el mismo porque ninguna de las dos depende de los datos
de demostración.

#### Convención de nombres

```
V<versión>__<descripción_en_snake_case>.sql
  │              └── descripción breve, en español, sin tildes
  └── entero creciente; DOS guiones bajos separan versión y descripción
```

| Rango | Uso |
|---|---|
| `V1` – `V3` | Esquema y catálogos iniciales. Se aplican siempre |
| `V4` – `V899` | Evoluciones del esquema: `V4` agrega las vistas del tablero y `V5` endurece `ck_solicitudes_tipo`. La próxima es `V6`. Como quedan por debajo de `V900`, necesitan `out-of-order: true` |
| `V900` – `V999` | Datos de demostración. Se numeran muy arriba para que siempre se apliquen al final y para no estorbar al esquema |

Reglas prácticas:

- **Un cambio ya liberado no se modifica**: se corrige con una versión nueva. Editar un
  script aplicado altera su *checksum* y rompe la validación.
- Cada script debe poder ejecutarse **dos veces sin error**. Los siete lo cumplen: los
  objetos se crean con `IF OBJECT_ID(...) IS NULL` o `IF NOT EXISTS`, los programables con
  `CREATE OR ALTER`, las restricciones que se rehacen con `DROP CONSTRAINT IF EXISTS` antes
  de crearlas (`V5`) y las inserciones filtran por la clave natural (DPI, número de
  solicitud, de préstamo o de recibo).
- `GO` se usa solo donde el motor lo exige: `CREATE VIEW`, `FUNCTION`, `PROCEDURE` y
  `TRIGGER` deben ser la primera sentencia de su lote, y un `CREATE TABLE` debe cerrar su
  lote antes de que otra sentencia lo referencie. Flyway interpreta `GO` correctamente.
- `V900__datos_demo.sql` es **un solo lote sin `GO`**, porque comparte variables de tabla
  entre sus bloques.
- `V900` no inventa importes: la cuota sale de `dbo.fn_calcular_cuota`, el total a pagar de
  `dbo.fn_total_plan` (la suma real del plan), los montos de los recibos son múltiplos de la
  cuota real, los saldos se derivan con una suma acumulada y al
  final `total_pagado` se **reconstruye** desde la suma real de pagos. Si el cuadre no da,
  lanza `THROW 50002` o `50003` y la migración falla, en lugar de dejar la base incoherente.

### 4.5 Diagramas de estados

#### `EstadoSolicitud`

```mermaid
stateDiagram-v2
    direction LR
    [*] --> EN_PROCESO : POST /solicitudes<br/>SolicitudPrestamo.nueva(...)

    EN_PROCESO --> APROBADA : POST /solicitudes/{id}/aprobar<br/>aprobar(monto, plazo, tasa, usuario, motivo, ahora)<br/>y se crea el prestamo
    EN_PROCESO --> RECHAZADA : POST /solicitudes/{id}/rechazar<br/>rechazar(usuario, motivo, ahora)<br/>motivo de 10 a 500 caracteres

    APROBADA --> [*]
    RECHAZADA --> [*]

    note right of EN_PROCESO
      Unico estado que admite resolucion.
      Sin datos de resolucion
      (ck_solicitudes_coherencia_resolucion).
    end note

    note right of APROBADA
      Estado final. Guarda fecha, usuario,
      monto, plazo y tasa aprobados.
      Un segundo intento de resolver
      lanza ReglaNegocioException y responde 409.
    end note

    note left of RECHAZADA
      Estado final. Guarda fecha, usuario
      y motivo; NO guarda montos aprobados.
    end note
```

La máquina de estados vive en el dominio: `SolicitudPrestamo.exigirEnProceso()` es el
guardián, y lo prueban `SolicitudPrestamoTest` (15 pruebas) y
`GestionarSolicitudesServiceTest`. El esquema la respalda con
`ck_solicitudes_coherencia_resolucion`.

#### `EstadoPrestamo`

```mermaid
stateDiagram-v2
    direction LR
    [*] --> VIGENTE : al aprobar la solicitud<br/>Prestamo.nuevo(...)<br/>total_pagado = 0

    VIGENTE --> VIGENTE : aplicarPago(monto)<br/>monto positivo y menor al saldo pendiente
    VIGENTE --> LIQUIDADO : aplicarPago(monto)<br/>monto igual al saldo pendiente exacto

    LIQUIDADO --> [*]

    note right of VIGENTE
      saldo_pendiente mayor que 0.
      Un pago mayor al saldo se rechaza
      con ReglaNegocioException y responde 409:
      el saldo nunca queda negativo.
    end note

    note right of LIQUIDADO
      saldo_pendiente igual a 0.
      No admite mas pagos (409).
      ck_prestamos_liquidado exige
      total_pagado = monto_total_a_pagar.
    end note
```

No existe transición de vuelta a `VIGENTE`: el alcance no contempla reversos ni anulaciones
de pago. `Prestamo.aplicarPago(...)` es el único camino que cambia el estado, y lo prueban
`PrestamoTest` (8 pruebas) y `RegistrarPagosServiceTest`.

---

## 5. API REST

- **Prefijo:** `/api/v1`
- **Base en local:** `http://localhost:8081/api/v1` (o `http://localhost:8080/api/v1` a
  través del proxy de Nginx, que es la vía que usa el navegador).
- **Autenticación:** cabecera `Authorization: Bearer {token}` en **todos** los endpoints
  salvo `POST /auth/login`.
- **Contenido:** `application/json` en petición y respuesta, `UTF-8`.
- **Fechas:** ISO-8601 sin zona (`2026-02-10T11:05:00`), interpretadas en
  `America/Guatemala`. No viajan como marcas de tiempo numéricas.
- **Nulos:** Jackson está configurado con `default-property-inclusion: non_null`, así que
  los campos nulos **no aparecen** en la respuesta.
- **Paginación:** `pagina` (base 0, por omisión `0`) y `tamano` (por omisión `10`). El
  `tamano` admite de **1 a 100**; fuera de ese rango la API responde `400`. La respuesta
  viene envuelta en `PaginaResponse` (`contenido`, `pagina`, `tamano`, `totalElementos`,
  `totalPaginas`), que es una envoltura propia y no el `Page` de Spring Data, para no
  filtrar detalles de la persistencia en el contrato.
- **Filtros:** los cinco listados paginados aceptan además criterios de búsqueda, todos
  opcionales y combinables entre sí. Se detallan en [5.2](#52-filtros-de-búsqueda-de-los-listados).

### 5.1 Catálogo de endpoints

Son **23 operaciones** sobre 11 rutas, repartidas en 7 controladores. La columna «Rol» refleja las dos capas que lo
imponen: las reglas de `SeguridadConfig.cadenaApi` y la anotación `@PreAuthorize` del método.

#### Autenticación — `AutenticacionControlador`

| Método | Ruta | Rol | Respuestas | Descripción |
|---|---|---|---|---|
| `POST` | `/auth/login` | **público** | `200`, `400`, `401`, `429` | Valida las credenciales y devuelve un token JWT con los datos del usuario. Único endpoint público de la API |
| `GET` | `/auth/perfil` | autenticado | `200`, `401`, `404` | Devuelve el usuario propietario del token enviado |

#### Clientes — `ClienteControlador`

| Método | Ruta | Rol | Respuestas | Descripción |
|---|---|---|---|---|
| `GET` | `/clientes` | autenticado | `200`, `400`, `401` | Listado paginado y filtrable: `busqueda`, `nacimientoDesde`, `nacimientoHasta`, `creacionDesde`, `creacionHasta`, `activo` ([5.2](#52-filtros-de-búsqueda-de-los-listados)). Ordena por apellido, nombre e id |
| `POST` | `/clientes` | `ADMIN`, `ANALISTA` | `201`, `400`, `401`, `403`, `409` | Registra un cliente. Devuelve la cabecera `Location`. DPI y correo deben ser únicos |
| `GET` | `/clientes/{id}` | autenticado | `200`, `401`, `404` | Consulta un cliente por identificador |
| `PUT` | `/clientes/{id}` | `ADMIN`, `ANALISTA` | `200`, `400`, `401`, `403`, `404`, `409` | Actualiza datos de contacto. **El DPI y la fecha de nacimiento no son editables** |
| `DELETE` | `/clientes/{id}` | **solo `ADMIN`** | `204`, `401`, `403`, `404`, `409` | Borra el cliente y su historial (pagos, préstamos y solicitudes) en una sola transacción |
| `GET` | `/clientes/{id}/solicitudes` | autenticado | `200`, `401`, `404` | Historial completo de solicitudes del cliente, sin paginar |
| `GET` | `/clientes/{id}/prestamos` | autenticado | `200`, `401`, `404` | Historial completo de préstamos del cliente, sin paginar |

#### Solicitudes — `SolicitudControlador`

| Método | Ruta | Rol | Respuestas | Descripción |
|---|---|---|---|---|
| `GET` | `/solicitudes` | autenticado | `200`, `400`, `401` | Listado paginado y filtrable: `busqueda`, `clienteId`, `estado`, `tipoPrestamo`, `montoMinimo`, `montoMaximo`, `plazoMinimo`, `plazoMaximo`, `fechaDesde`, `fechaHasta` ([5.2](#52-filtros-de-búsqueda-de-los-listados)). Un `estado` o un `tipoPrestamo` fuera del enum responde `400` enumerando los valores permitidos |
| `POST` | `/solicitudes` | `ADMIN`, `ANALISTA` | `201`, `400`, `401`, `403`, `404` | Crea la solicitud. Nace `EN_PROCESO` con correlativo de `seq_solicitud` |
| `GET` | `/solicitudes/{id}` | autenticado | `200`, `401`, `404` | Detalle con datos del cliente y de la resolución |
| `POST` | `/solicitudes/{id}/aprobar` | `ADMIN`, `ANALISTA` | `200`, `400`, `401`, `403`, `404`, `409` | Aprueba y **crea el préstamo** con su cuota. Los campos omitidos toman el valor solicitado. Una solicitud ya resuelta responde `409` |
| `POST` | `/solicitudes/{id}/rechazar` | `ADMIN`, `ANALISTA` | `200`, `400`, `401`, `403`, `404`, `409` | Rechaza con motivo obligatorio de 10 a 500 caracteres. Una solicitud ya resuelta responde `409` |
| `POST` | `/solicitudes/simulacion` | `ADMIN`, `ANALISTA` | `200`, `400`, `401`, `403` | Simula un préstamo: plan de amortización completo y evaluación de capacidad de pago. **No persiste ni audita nada** |

#### Préstamos — `PrestamoControlador`

| Método | Ruta | Rol | Respuestas | Descripción |
|---|---|---|---|---|
| `GET` | `/prestamos` | autenticado | `200`, `400`, `401` | Cartera paginada y filtrable: `busqueda`, `clienteId`, `estado` (`VIGENTE` o `LIQUIDADO`), `montoMinimo`, `montoMaximo`, `saldoMinimo`, `saldoMaximo`, `desembolsoDesde`, `desembolsoHasta`, `vencimientoDesde`, `vencimientoHasta` ([5.2](#52-filtros-de-búsqueda-de-los-listados)) |
| `GET` | `/prestamos/{id}` | autenticado | `200`, `401`, `404` | Detalle con cliente, saldo y porcentaje pagado |
| `GET` | `/prestamos/{id}/amortizacion` | autenticado | `200`, `401`, `404` | Plan completo: una fila por cuota del plazo. Se **recalcula** en cada consulta |
| `GET` | `/prestamos/{id}/pagos` | autenticado | `200`, `401`, `404` | Historial de pagos del préstamo, sin paginar |

#### Pagos — `PagoControlador`

| Método | Ruta | Rol | Respuestas | Descripción |
|---|---|---|---|---|
| `GET` | `/pagos` | autenticado | `200`, `400`, `401` | Listado paginado de recibos, filtrable por `busqueda`, `prestamoId`, `clienteId`, `montoMinimo`, `montoMaximo`, `fechaDesde`, `fechaHasta` y `usuarioRegistro` ([5.2](#52-filtros-de-búsqueda-de-los-listados)) |
| `POST` | `/pagos` | `ADMIN`, `CAJERO` | `201`, `400`, `401`, `403`, `404`, `409` | Registra un pago en efectivo. `409` si el monto excede el saldo o el préstamo ya está liquidado |

#### Resumen y auditoría

| Método | Ruta | Rol | Respuestas | Descripción |
|---|---|---|---|---|
| `GET` | `/resumen` | autenticado | `200`, `401` | Los 9 indicadores del tablero, leídos de `dbo.vw_resumen_general`, más dos series para sus gráficas: `carteraPorTipo` (siempre los 5 tipos, de `dbo.vw_cartera_por_tipo`) y `recaudacionMensual` (siempre 12 meses, de `dbo.vw_recaudacion_mensual`). Contrato en [5.3](#consultar-el-resumen-del-tablero) |
| `GET` | `/auditoria` | **solo `ADMIN`** | `200`, `400`, `401`, `403` | Bitácora en orden descendente por fecha, filtrable por `busqueda`, `usuario`, `accion`, `entidad`, `fechaDesde` y `fechaHasta` ([5.2](#52-filtros-de-búsqueda-de-los-listados)) |

#### Superficie no versionada

| Ruta | Acceso | Para qué |
|---|---|---|
| `/actuator/health` | público | Sonda de vida. Sin detalle de componentes (`show-details: never`) |
| `/actuator/info` | público | Información mínima. `management.info.env.enabled: false` |
| `/v3/api-docs` | público | Especificación OpenAPI 3 en JSON |
| `/swagger-ui.html` | público | Interfaz de documentación interactiva |

Las rutas de documentación se atienden con una **cadena de seguridad aparte**
(`cadenaDocumentacion`, `@Order(1)`): la cadena de la API aplica una política de contenido
`default-src 'none'`, que impediría al navegador cargar los scripts y estilos de Swagger UI.

### 5.2 Filtros de búsqueda de los listados

Los cinco listados paginados —clientes, solicitudes, préstamos, pagos y auditoría— aceptan
criterios de búsqueda que se suman a `pagina` y `tamano`. Tres reglas gobiernan a todos:

1. **Todo criterio es opcional.** Omitirlo, o enviarlo vacío, significa «sin ese filtro»; no
   significa «buscar la cadena vacía». Los criterios presentes se combinan con `AND`.
2. **El filtrado ocurre siempre en la base de datos**, nunca en memoria. Cada consulta JPQL
   aplica el patrón `:criterio IS NULL OR <condición>` y su `countQuery` repite el mismo
   `WHERE`, de modo que `totalElementos` y `totalPaginas` corresponden al conjunto
   **filtrado** y la paginación sigue siendo exacta. El razonamiento está en
   [3.5-i](#35-decisiones-de-diseño-justificación-y-contrapartida).
3. **Todo valor viaja como parámetro nombrado** (`@Param`), incluido el término del `LIKE`,
   cuyo comodín se arma dentro de la consulta con `LOWER(CONCAT('%', :busqueda, '%'))`. No
   se concatena SQL en Java en ningún punto ([7.6](#76-prevención-de-inyección-sql)).

#### `GET /api/v1/clientes`

| Parámetro | Tipo | Qué acota | Ejemplo |
|---|---|---|---|
| `busqueda` | texto (≤ 120) | Nombre, apellido, nombre completo, DPI, correo y teléfono | `busqueda=Ramirez` |
| `nacimientoDesde` | fecha `aaaa-MM-dd` | Nacidos en esa fecha o después (columna `DATE`) | `nacimientoDesde=1990-01-01` |
| `nacimientoHasta` | fecha `aaaa-MM-dd` | Nacidos en esa fecha o antes | `nacimientoHasta=1999-12-31` |
| `creacionDesde` | fecha `aaaa-MM-dd` | Registrados ese día o después (columna `DATETIME2`) | `creacionDesde=2026-09-01` |
| `creacionHasta` | fecha `aaaa-MM-dd` | Registrados ese día o antes, **día completo** | `creacionHasta=2026-09-30` |
| `activo` | `true` / `false` | Estado del registro; omitirlo devuelve activos e inactivos | `activo=true` |

#### `GET /api/v1/solicitudes`

| Parámetro | Tipo | Qué acota | Ejemplo |
|---|---|---|---|
| `busqueda` | texto (≤ 120) | Número de solicitud, destino y nombre o DPI del cliente | `busqueda=SC-001-2026-000003-9` |
| `clienteId` | entero | Solicitudes de un cliente | `clienteId=7` |
| `estado` | enum | `EN_PROCESO`, `APROBADA`, `RECHAZADA` | `estado=EN_PROCESO` |
| `tipoPrestamo` | enum | `PERSONAL`, `HIPOTECARIO`, `VEHICULAR`, `EMPRESARIAL`, `EDUCATIVO` | `tipoPrestamo=HIPOTECARIO` |
| `montoMinimo` / `montoMaximo` | decimal | Rango sobre `monto_solicitado` | `montoMinimo=50000&montoMaximo=200000` |
| `plazoMinimo` / `plazoMaximo` | entero | Rango sobre `plazo_meses` | `plazoMinimo=12&plazoMaximo=36` |
| `fechaDesde` / `fechaHasta` | fecha `aaaa-MM-dd` | Rango sobre `fecha_solicitud` (`DATETIME2`) | `fechaDesde=2026-01-01&fechaHasta=2026-03-31` |

#### `GET /api/v1/prestamos`

| Parámetro | Tipo | Qué acota | Ejemplo |
|---|---|---|---|
| `busqueda` | texto (≤ 120) | Número de préstamo, número de la solicitud de origen y nombre o DPI del cliente | `busqueda=PR-001-2026-000001-9` |
| `clienteId` | entero | Cartera de un cliente | `clienteId=3` |
| `estado` | enum | `VIGENTE`, `LIQUIDADO` | `estado=VIGENTE` |
| `montoMinimo` / `montoMaximo` | decimal | Rango sobre `monto_aprobado` | `montoMinimo=100000` |
| `saldoMinimo` / `saldoMaximo` | decimal | Rango sobre el **saldo pendiente**, resuelto como `(monto_total_a_pagar − total_pagado)` (ver [4.1](#41-diccionario-de-datos)) | `saldoMinimo=500000` |
| `desembolsoDesde` / `desembolsoHasta` | fecha `aaaa-MM-dd` | Rango sobre `fecha_desembolso` (`DATE`) | `desembolsoDesde=2026-01-01` |
| `vencimientoDesde` / `vencimientoHasta` | fecha `aaaa-MM-dd` | Rango sobre `fecha_vencimiento` (`DATE`); útil para anticipar los créditos que están por terminar | `vencimientoHasta=2026-12-31` |

#### `GET /api/v1/pagos`

| Parámetro | Tipo | Qué acota | Ejemplo |
|---|---|---|---|
| `busqueda` | texto (≤ 120) | Número de recibo, número de préstamo y nombre o DPI del cliente | `busqueda=RC-001-2026-000008-9` |
| `prestamoId` | entero | Recibos de un préstamo | `prestamoId=1` |
| `clienteId` | entero | Recibos de todos los préstamos de un cliente | `clienteId=1` |
| `montoMinimo` / `montoMaximo` | decimal | Rango sobre el importe del recibo; localiza abonos extraordinarios | `montoMinimo=10000` |
| `fechaDesde` / `fechaHasta` | fecha `aaaa-MM-dd` | Rango sobre `fecha_pago` (`DATETIME2`); cuadra la recaudación de un día o de un mes | `fechaDesde=2026-09-01&fechaHasta=2026-09-30` |
| `usuarioRegistro` | texto (≤ 50) | Cajero que recibió el pago; coincidencia parcial | `usuarioRegistro=cajero` |

#### `GET /api/v1/auditoria`

| Parámetro | Tipo | Qué acota | Ejemplo |
|---|---|---|---|
| `busqueda` | texto (≤ 120) | Usuario, detalle e identificador de la entidad afectada | `busqueda=2547896320101` |
| `usuario` | texto (≤ 50) | Quién ejecutó la operación; coincidencia parcial | `usuario=admin` |
| `accion` | texto (≤ 50) | Constante de `AccionesAuditoria`; **valor exacto** | `accion=PAGO_REGISTRADO` |
| `entidad` | texto (≤ 50) | `CLIENTE`, `SOLICITUD`, `PRESTAMO`, `PAGO`, `USUARIO`; **valor exacto** | `entidad=PRESTAMO` |
| `fechaDesde` / `fechaHasta` | fecha `aaaa-MM-dd` | Rango sobre `fecha` (`DATETIME2`) | `fechaDesde=2026-09-01` |

`accion` y `entidad` se comparan por igualdad y no con `LIKE` porque provienen de un
catálogo cerrado que escribe la propia aplicación; `usuario` sí admite coincidencia parcial,
porque quien investiga suele recordar solo un fragmento del nombre.

#### Qué recorre el texto libre `busqueda`

| Listado | Campos que recorre |
|---|---|
| Clientes | `nombre`, `apellido`, nombre completo (`nombre + ' ' + apellido`), DPI, correo y teléfono |
| Solicitudes | `numero_solicitud`, `destino`, y nombre, nombre completo o DPI del cliente |
| Préstamos | `numero_prestamo`, `numero_solicitud` de origen, y nombre, nombre completo o DPI del cliente |
| Pagos | `numero_recibo`, `numero_prestamo`, y nombre, nombre completo o DPI del cliente |
| Auditoría | `usuario`, `detalle` y `entidad_id` |

Buscar por el **nombre completo** está resuelto en la consulta y no es redundante: quien
teclea «Maria Jose Ramirez» no coincidiría ni con la columna `nombre` ni con `apellido` por
separado.

En solicitudes, préstamos y pagos los datos del cliente ya vienen del `JOIN` de la
proyección que el listado usa para evitar el N+1 ([3.5-b](#35-decisiones-de-diseño-justificación-y-contrapartida)),
así que buscar por ellos **no cuesta una sentencia adicional**.

> **La búsqueda es insensible a mayúsculas y a acentos.** La base se crea con
> `COLLATE Modern_Spanish_CI_AI` (`00_crear_base_datos.sql`): *CI* = *case insensitive*,
> *AI* = *accent insensitive*. Por eso `busqueda=ramirez` encuentra «Ramírez» y `busqueda=MARIA`
> encuentra «María José». El `LOWER(...)` que aparece a ambos lados del `LIKE` en el JPQL es
> el cinturón además del tirante: deja la consulta correcta aunque un día se despliegue
> sobre una base con otra *collation*.

#### Semántica de los rangos

Todo rango (`…Desde`/`…Hasta`, `…Minimo`/`…Maximo`) es **inclusivo en ambos extremos**, y
cada extremo es independiente: se puede enviar solo el inferior, solo el superior o los dos.

Para las fechas hay un detalle que importa y que resuelve `CriteriosJpa`:

| Tipo de columna | Ejemplos | Cómo se compara |
|---|---|---|
| `DATE` (solo el día) | `fecha_nacimiento`, `fecha_desembolso`, `fecha_vencimiento` | Comparación directa: `>= :desde` y `<= :hasta` |
| `DATETIME2` (día y hora) | `fecha_creacion`, `fecha_solicitud`, `fecha_pago`, `auditoria.fecha` | `>= :desde` (00:00 del día) y **`< :hasta + 1 día`** |

La razón del ajuste: en una columna `DATETIME2`, `fecha_pago <= '2026-09-30'` se evalúa
contra `2026-09-30 00:00:00` y **dejaría fuera** un recibo emitido a las 23:50 de ese mismo
día. Traduciendo el límite superior al inicio del día siguiente y comparando con `<`, el día
«hasta» entra completo y el rango es inclusivo tal como el usuario lo entiende. La
conversión vive en un solo sitio (`CriteriosJpa.inicioDelDia` e `inicioDelDiaSiguiente`) para
que los cinco adaptadores no la repitan cada uno a su manera.

#### Criterios inválidos

Un criterio incoherente es un **error del solicitante**, no una búsqueda que simplemente no
arroja resultados: responder `200` con una lista vacía escondería el error. Por eso los
cinco *records* de filtro validan la coherencia en su constructor compacto
([3.5-j](#35-decisiones-de-diseño-justificación-y-contrapartida)) y la API responde `400`:

| Caso | HTTP | `codigo` | Mensaje |
|---|---|---|---|
| Rango de fechas invertido | `400` | `VALIDACION` | `La fecha inicial del rango de <rango> no puede ser posterior a la fecha final.` |
| Monto mínimo mayor que el máximo | `400` | `VALIDACION` | `El monto minimo del rango de <rango> no puede ser mayor que el monto maximo.` |
| Valor mínimo mayor que el máximo (plazo) | `400` | `VALIDACION` | `El valor minimo del rango de plazo en meses no puede ser mayor que el valor maximo.` |
| Fecha con formato distinto de `aaaa-MM-dd` | `400` | `VALIDACION` | `El parametro '<nombre>' no tiene un formato valido. Se espera una fecha en formato aaaa-MM-dd.` |
| Número o booleano mal formado | `400` | `VALIDACION` | El mensaje nombra el tipo esperado: decimal con punto, entero, `true`/`false` |
| Enum fuera del catálogo (`estado`, `tipoPrestamo`) | `400` | `VALIDACION` | `Estado invalido. Valores permitidos: EN_PROCESO, APROBADA, RECHAZADA` |
| `tamano` fuera de 1–100, `pagina` negativa | `400` | `VALIDACION` | El detalle por campo indica el límite |

`ManejadorExcepcionesGlobal` distingue el **tipo esperado** (fecha, fecha y hora, decimal,
entero, booleano, enum) al traducir una `MethodArgumentTypeMismatchException`, de modo que
el mensaje diga qué escribir y no solo que el valor es inválido.

Respuesta real de un rango invertido:

```http
GET /api/v1/clientes?nacimientoDesde=1995-12-31&nacimientoHasta=1990-01-01 HTTP/1.1
Authorization: Bearer {token}
```

`400 Bad Request`

```json
{
  "timestamp": "2026-09-17T14:02:11",
  "estado": 400,
  "codigo": "VALIDACION",
  "mensaje": "La fecha inicial del rango de nacimiento no puede ser posterior a la fecha final.",
  "ruta": "/api/v1/clientes"
}
```

Y de una fecha mal formada:

```http
GET /api/v1/clientes?nacimientoDesde=31-01-2026 HTTP/1.1
```

`400 Bad Request`

```json
{
  "timestamp": "2026-09-17T14:03:40",
  "estado": 400,
  "codigo": "VALIDACION",
  "mensaje": "El parametro 'nacimientoDesde' no tiene un formato valido. Se espera una fecha en formato aaaa-MM-dd.",
  "ruta": "/api/v1/clientes",
  "errores": [
    { "campo": "nacimientoDesde", "mensaje": "El parametro 'nacimientoDesde' no tiene un formato valido. Se espera una fecha en formato aaaa-MM-dd." }
  ]
}
```

En los dos casos la conversión falla **en el borde** y el caso de uso no llega a ejecutarse;
`ClienteControladorTest` y `AuditoriaControladorTest` lo comprueban con `verifyNoInteractions`.

#### Ejemplos con varios criterios combinados

Clientes nacidos en los noventa, activos y cuyo nombre o DPI contenga «ram», segunda página
de 25:

```http
GET /api/v1/clientes?busqueda=ram&nacimientoDesde=1990-01-01&nacimientoHasta=1999-12-31
    &activo=true&pagina=1&tamano=25 HTTP/1.1
Authorization: Bearer {token}
```

Solicitudes hipotecarias en proceso, de entre Q200 000 y Q1 000 000, a más de 120 meses,
presentadas en el tercer trimestre:

```http
GET /api/v1/solicitudes?estado=EN_PROCESO&tipoPrestamo=HIPOTECARIO&montoMinimo=200000
    &montoMaximo=1000000&plazoMinimo=120&fechaDesde=2026-07-01&fechaHasta=2026-09-30 HTTP/1.1
```

Préstamos vigentes con más de Q500 000 de saldo vivo que vencen antes de que acabe el año
—la consulta de exposición que pide el área de riesgo—:

```http
GET /api/v1/prestamos?estado=VIGENTE&saldoMinimo=500000&vencimientoHasta=2026-12-31&tamano=50 HTTP/1.1
```

Recaudación de un cajero en un día concreto:

```http
GET /api/v1/pagos?usuarioRegistro=cajero&fechaDesde=2026-09-17&fechaHasta=2026-09-17 HTTP/1.1
```

Todo lo que hizo un usuario sobre préstamos en septiembre:

```http
GET /api/v1/auditoria?usuario=admin&entidad=PRESTAMO&fechaDesde=2026-09-01&fechaHasta=2026-09-30 HTTP/1.1
```

> Los saltos de línea de estos ejemplos son solo para que quepan en la página: la
> petición real lleva la cadena de consulta en una sola línea.

`tools/pruebas/prueba-filtros.sh` recorre estos criterios contra el sistema en ejecución;
ver [11.5](#115-prueba-de-humo-de-los-filtros).

### 5.3 Ejemplos de petición y respuesta

Los ejemplos reflejan el estado exacto de la base con los datos de demostración recién
cargados (`APP_DATOS_DEMO=true`).

#### Iniciar sesión

```http
POST /api/v1/auth/login HTTP/1.1
Content-Type: application/json
```

```json
{
  "username": "admin",
  "contrasena": "Chn2026*Demo"
}
```

`200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbCI6IkFETUlOIiwibm9tYnJlIjoiQWRtaW5pc3RyYWRvciBkZWwgU2lzdGVtYSIsImlzcyI6ImNobi1wcmVzdGFtb3MiLCJpYXQiOjE3NjkxMjM0NTYsImV4cCI6MTc2OTE1MjI1Nn0.firma-base64url",
  "tipo": "Bearer",
  "expiraEnSegundos": 28800,
  "usuario": {
    "id": 1,
    "username": "admin",
    "nombreCompleto": "Administrador del Sistema",
    "correo": "admin@chn.com.gt",
    "rol": "ADMIN"
  }
}
```

El token lleva como *claims* `sub` (username), `rol`, `nombre`, `iss` (`chn-prestamos`),
`iat` y `exp`. `expiraEnSegundos` son los 480 minutos por omisión.

Con credenciales incorrectas responde `401` con el mensaje genérico
`Credenciales inválidas`, idéntico para usuario inexistente, inactivo y contraseña errónea.

#### Registrar un cliente

```http
POST /api/v1/clientes HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "nombre": "Maria Jose",
  "apellido": "Ramirez Lopez",
  "numeroIdentificacion": "2547896320101",
  "fechaNacimiento": "1992-04-18",
  "direccion": "5a Avenida 12-45 Zona 10, Ciudad de Guatemala",
  "correoElectronico": "maria.ramirez@correo.com.gt",
  "telefono": "55123456"
}
```

`201 Created` · `Location: /api/v1/clientes/9`

```json
{
  "id": 9,
  "nombre": "Maria Jose",
  "apellido": "Ramirez Lopez",
  "nombreCompleto": "Maria Jose Ramirez Lopez",
  "numeroIdentificacion": "2547896320101",
  "fechaNacimiento": "1992-04-18",
  "edad": 34,
  "direccion": "5a Avenida 12-45 Zona 10, Ciudad de Guatemala",
  "correoElectronico": "maria.ramirez@correo.com.gt",
  "telefono": "55123456",
  "activo": true,
  "fechaCreacion": "2026-09-17T13:05:00"
}
```

`nombreCompleto` y `edad` son datos derivados que el dominio ya calcula; se exponen para que
el frontend no replique esa lógica. `fechaModificacion` no aparece porque es nula.

#### Simular un préstamo

```http
POST /api/v1/solicitudes/simulacion HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "clienteId": 1,
  "monto": 100000.00,
  "plazoMeses": 12,
  "tasaInteresAnual": 12.00,
  "ingresoMensual": 30000.00
}
```

`200 OK` (plan recortado a las tres primeras cuotas y la última; la API devuelve las 12)

```json
{
  "evaluacion": {
    "porcentajeComprometido": 29.62,
    "recomendado": true,
    "observacion": "La cuota compromete el 29.62% del ingreso declarado, dentro del limite institucional del 40%. El cliente registra 1 prestamo(s) vigente(s).",
    "prestamosVigentes": 1
  },
  "plan": {
    "cuotaMensual": 8884.88,
    "totalIntereses": 6618.53,
    "montoTotal": 106618.53,
    "cuotas": [
      { "numero": 1,  "saldoInicial": 100000.00, "cuota": 8884.88, "abonoCapital": 7884.88, "abonoInteres": 1000.00, "saldoFinal": 92115.12 },
      { "numero": 2,  "saldoInicial": 92115.12,  "cuota": 8884.88, "abonoCapital": 7963.73, "abonoInteres": 921.15,  "saldoFinal": 84151.39 },
      { "numero": 3,  "saldoInicial": 84151.39,  "cuota": 8884.88, "abonoCapital": 8043.37, "abonoInteres": 841.51,  "saldoFinal": 76108.02 },
      { "numero": 12, "saldoInicial": 8796.88,   "cuota": 8884.85, "abonoCapital": 8796.88, "abonoInteres": 87.97,   "saldoFinal": 0.00 }
    ]
  }
}
```

Nótese la **última cuota**: `8884.85` en lugar de `8884.88`. Absorbe el residuo de los
redondeos para que `saldoFinal` sea exactamente `0.00`. Ver [6.2](#62-la-fórmula-de-amortización).

La simulación es una consulta pura: `@Transactional(readOnly = true)`, sin escrituras y sin
registro en la bitácora. Solo consulta el número de préstamos vigentes del cliente, porque
es parte de la política de riesgo.

#### Aprobar una solicitud

```http
POST /api/v1/solicitudes/7/aprobar HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "montoAprobado": 90000.00,
  "plazoAprobadoMeses": 30,
  "tasaAprobada": 13.75,
  "motivo": "Capacidad de pago verificada y constancia laboral recibida"
}
```

Los cuatro campos son **opcionales**: si se envía `{}`, la solicitud se aprueba en las
condiciones que el cliente pidió.

`200 OK`

```json
{
  "id": 7,
  "numeroSolicitud": "SC-001-2026-000007-0",
  "clienteId": 7,
  "nombreCliente": "Gabriela Maria Sandoval Rios",
  "identificacionCliente": "2634781290107",
  "montoSolicitado": 95000.00,
  "plazoMeses": 30,
  "tasaInteresAnual": 13.75,
  "tipoPrestamo": "PERSONAL",
  "destino": "Gastos medicos y tratamiento de un familiar",
  "ingresoMensualDeclarado": 14200.00,
  "estado": "APROBADA",
  "fechaSolicitud": "2026-08-28T10:15:00",
  "observaciones": "Pendiente de adjuntar constancia laboral actualizada.",
  "resolucion": {
    "fechaResolucion": "2026-09-17T13:12:00",
    "usuarioResolucion": "admin",
    "montoAprobado": 90000.00,
    "plazoAprobadoMeses": 30,
    "tasaAprobada": 13.75,
    "motivo": "Capacidad de pago verificada y constancia laboral recibida"
  }
}
```

En la misma transacción se creó el préstamo, consultable con
`GET /api/v1/prestamos?clienteId=7`. Repetir la llamada responde `409`:

```json
{
  "timestamp": "2026-09-17T13:12:30",
  "estado": 409,
  "codigo": "REGLA_NEGOCIO",
  "mensaje": "La solicitud ya fue resuelta y no admite cambios de estado",
  "ruta": "/api/v1/solicitudes/7/aprobar"
}
```

#### Registrar un pago

```http
POST /api/v1/pagos HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "prestamoId": 1,
  "monto": 3377.46,
  "observaciones": "Pago de cuota en ventanilla, agencia central"
}
```

`201 Created`

```json
{
  "id": 8,
  "numeroRecibo": "RC-001-2026-000008-9",
  "prestamoId": 1,
  "numeroPrestamo": "PR-001-2026-000001-9",
  "clienteId": 1,
  "nombreCliente": "Maria Jose Ramirez Lopez",
  "monto": 3377.46,
  "fechaPago": "2026-09-17T13:20:00",
  "formaPago": "EFECTIVO",
  "saldoAnterior": 67549.22,
  "saldoPosterior": 64171.76,
  "usuarioRegistro": "cajero",
  "observaciones": "Pago de cuota en ventanilla, agencia central"
}
```

`saldoAnterior` es el saldo vivo del préstamo 1 con los datos de demostración
(`67,549.22`). Un monto mayor a ese saldo responde `409`:

```json
{
  "timestamp": "2026-09-17T13:21:00",
  "estado": 409,
  "codigo": "REGLA_NEGOCIO",
  "mensaje": "El monto del pago excede el saldo pendiente del prestamo. Saldo disponible: 64171.76.",
  "ruta": "/api/v1/pagos"
}
```

#### Consultar el resumen del tablero

```http
GET /api/v1/resumen HTTP/1.1
Authorization: Bearer {token}
```

`200 OK` (estado exacto tras un arranque limpio, `docker compose down -v && docker compose up -d`,
con datos de demostración, antes de cualquier operación y con el reloj del sistema en
septiembre de 2026)

```json
{
  "totalClientes": 8,
  "solicitudesEnProceso": 4,
  "solicitudesAprobadas": 4,
  "solicitudesRechazadas": 2,
  "prestamosVigentes": 4,
  "prestamosLiquidados": 0,
  "montoTotalAprobado": 1430000.00,
  "saldoPendienteTotal": 2015027.19,
  "totalRecuperado": 42377.00,
  "carteraPorTipo": [
    { "tipoPrestamo": "PERSONAL",    "cantidadPrestamos": 1, "montoAprobado": 70000.00,  "saldoPendiente": 67549.22,   "totalRecuperado": 13509.84 },
    { "tipoPrestamo": "HIPOTECARIO", "cantidadPrestamos": 1, "montoAprobado": 800000.00, "saldoPendiente": 1255394.28, "totalRecuperado": 0.00 },
    { "tipoPrestamo": "VEHICULAR",   "cantidadPrestamos": 1, "montoAprobado": 450000.00, "saldoPendiente": 567477.24,  "totalRecuperado": 19568.18 },
    { "tipoPrestamo": "EMPRESARIAL", "cantidadPrestamos": 0, "montoAprobado": 0.00,      "saldoPendiente": 0.00,       "totalRecuperado": 0.00 },
    { "tipoPrestamo": "EDUCATIVO",   "cantidadPrestamos": 1, "montoAprobado": 110000.00, "saldoPendiente": 124606.45,  "totalRecuperado": 9298.98 }
  ],
  "recaudacionMensual": [
    { "periodo": "2025-10", "anio": 2025, "mes": 10, "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2025-11", "anio": 2025, "mes": 11, "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2025-12", "anio": 2025, "mes": 12, "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2026-01", "anio": 2026, "mes": 1,  "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2026-02", "anio": 2026, "mes": 2,  "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2026-03", "anio": 2026, "mes": 3,  "cantidadPagos": 1, "monto": 3377.46 },
    { "periodo": "2026-04", "anio": 2026, "mes": 4,  "cantidadPagos": 2, "monto": 13161.55 },
    { "periodo": "2026-05", "anio": 2026, "mes": 5,  "cantidadPagos": 3, "monto": 20258.60 },
    { "periodo": "2026-06", "anio": 2026, "mes": 6,  "cantidadPagos": 1, "monto": 5579.39 },
    { "periodo": "2026-07", "anio": 2026, "mes": 7,  "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2026-08", "anio": 2026, "mes": 8,  "cantidadPagos": 0, "monto": 0.00 },
    { "periodo": "2026-09", "anio": 2026, "mes": 9,  "cantidadPagos": 0, "monto": 0.00 }
  ]
}
```

Los nueve totales no cambiaron respecto de la versión anterior del contrato; las dos series
son **aditivas**, de modo que un cliente que no las conozca las ignora sin romperse.

| Campo | Tipo | Garantía |
|---|---|---|
| `carteraPorTipo` | arreglo de `CarteraTipoResponse` | **Siempre 5 elementos**, uno por valor del enum `TipoPrestamo` y en su orden: `PERSONAL`, `HIPOTECARIO`, `VEHICULAR`, `EMPRESARIAL`, `EDUCATIVO`. Un tipo sin préstamos viaja con conteo y montos en cero |
| `carteraPorTipo[].tipoPrestamo` | `String` | Nombre exacto del enum |
| `carteraPorTipo[].cantidadPrestamos` | `long` | Préstamos desembolsados de ese tipo, en cualquier estado |
| `carteraPorTipo[].montoAprobado` | `BigDecimal`, escala 2 | Capital desembolsado (`prestamos.monto_aprobado`), no el monto solicitado |
| `carteraPorTipo[].saldoPendiente` | `BigDecimal`, escala 2 | Capital más intereses por cobrar |
| `carteraPorTipo[].totalRecuperado` | `BigDecimal`, escala 2 | Suma de los abonos recibidos |
| `recaudacionMensual` | arreglo de `RecaudacionMensualResponse` | **Siempre 12 meses contiguos** en orden ascendente, el último es el mes en curso según `RelojPort` (zona `America/Guatemala`). Un mes sin pagos viaja en cero |
| `recaudacionMensual[].periodo` | `String` `AAAA-MM` | Ordenado como texto, queda en orden cronológico |
| `recaudacionMensual[].anio`, `mes` | `int` | Año y mes (1 a 12) del periodo |
| `recaudacionMensual[].cantidadPagos` | `long` | Pagos con `fecha_pago` dentro del mes |
| `recaudacionMensual[].monto` | `BigDecimal`, escala 2 | Suma de `pagos.monto` del mes |

Comprobación de consistencia: `montoTotalAprobado` es la suma de los cuatro capitales
colocados (70 000 + 450 000 + 110 000 + 800 000), y `saldoPendienteTotal` la suma de los
cuatro saldos vivos (67 549.22 + 567 477.24 + 124 606.45 + 1 255 394.28). El total a pagar
de la cartera menos lo recuperado da exactamente el saldo. Las series cuadran con los
totales: la suma de `carteraPorTipo` da los mismos `montoTotalAprobado`,
`saldoPendienteTotal` y `totalRecuperado`, y los siete pagos de la demostración, repartidos
entre marzo y junio de 2026, suman los 42 377.00 recuperados. Los 800 000.00 del
hipotecario son lo aprobado; la solicitud pidió 850 000.00. Desde marzo de 2027 la ventana de 12 meses
empieza a dejar fuera esos pagos (y desde junio de 2027, todos): su suma ya no coincide con
`totalRecuperado`, que acumula toda la historia.

### 5.4 Formato de error y códigos

**Todos** los errores de la API tienen la misma forma, la produzca
`ManejadorExcepcionesGlobal`, la cadena de seguridad o el limitador de peticiones:

```json
{
  "timestamp": "2026-09-17T13:25:00",
  "estado": 400,
  "codigo": "VALIDACION",
  "mensaje": "La peticion contiene campos invalidos",
  "ruta": "/api/v1/clientes",
  "errores": [
    { "campo": "numeroIdentificacion", "mensaje": "El numero de identificacion (DPI) debe tener exactamente 13 digitos" },
    { "campo": "telefono", "mensaje": "El telefono debe tener exactamente 8 digitos" }
  ]
}
```

| Campo | Tipo | Siempre presente | Contenido |
|---|---|---|---|
| `timestamp` | `LocalDateTime` | sí | Momento en que se generó el error |
| `estado` | `int` | sí | Código de estado HTTP |
| `codigo` | `String` | sí | Código de negocio, estable y apto para lógica del cliente |
| `mensaje` | `String` | sí | Texto legible para el usuario final |
| `ruta` | `String` | sí | `requestURI` de la petición |
| `errores` | `[{campo, mensaje}]` | **no** | Solo en errores de validación; ordenado por nombre de campo. `ErrorResponse` lleva `@JsonInclude(NON_EMPTY)`, así que se omite cuando está vacío |

#### Tabla de códigos

| `codigo` | HTTP | Se produce cuando | Origen |
|---|---|---|---|
| `VALIDACION` | 400 | Falla `@Valid` sobre el cuerpo, falla una restricción sobre un parámetro (`pagina`, `tamano`), el JSON es ilegible, falta un parámetro obligatorio, un parámetro tiene un tipo incompatible (una fecha fuera de `aaaa-MM-dd`, un decimal con coma, un enum inventado), un criterio de búsqueda es incoherente (rango invertido, mínimo mayor que el máximo), o el dominio rechaza un valor | `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`, `ValidacionDominioException` |
| `AUTENTICACION` | 401 | Falta el token, es inválido o expiró; credenciales incorrectas; usuario inactivo; cuenta bloqueada | `AuthenticationEntryPoint` de `SeguridadConfig`, `AutenticacionException`, `BadCredentialsException`, `AuthenticationException` |
| `ACCESO_DENEGADO` | 403 | El token es válido pero el rol no cubre la operación | `AccessDeniedHandler` de `SeguridadConfig`, `AccessDeniedException` |
| `NO_ENCONTRADO` | 404 | El recurso no existe, o la ruta no corresponde a ningún controlador | `RecursoNoEncontradoException`, `NoResourceFoundException`, `NoHandlerFoundException` |
| `METODO_NO_PERMITIDO` | 405 | El verbo HTTP no está permitido en esa ruta | `HttpRequestMethodNotSupportedException` |
| `REGLA_NEGOCIO` | 409 | Resolver una solicitud ya resuelta, pagar un préstamo liquidado, pagar más que el saldo pendiente, aprobar una solicitud que ya generó préstamo | `ReglaNegocioException` |
| `DUPLICADO` | 409 | DPI o correo ya registrados (detectado por el caso de uso), o una restricción de integridad de la base | `ConflictoRecursoException`, `DataIntegrityViolationException` |
| `LIMITE_PETICIONES` | 429 | Más de 10 intentos de inicio de sesión desde la misma IP en 5 minutos. La respuesta incluye la cabecera `Retry-After` | `LimitadorLoginFiltro` |
| `ERROR_INTERNO` | 500 | Cualquier fallo no previsto | Manejador de último recurso |

Dos decisiones de seguridad gobiernan estos mensajes:

1. **Los 500 no revelan nada.** El manejador genérico registra la traza completa en el log
   con nivel `ERROR` y responde siempre
   `Ocurrió un error inesperado. Contacte al administrador.` Además
   `server.error.include-stacktrace`, `include-message` e `include-exception` están en
   `never`/`false`.
2. **Los mensajes de autenticación y autorización son deliberadamente genéricos.** Un 401
   no dice si el usuario existe; un 403 no dice qué rol haría falta. El detalle de una
   violación de integridad (que revelaría el nombre de la restricción y con él el esquema)
   se registra en el log y no viaja al cliente.

### 5.5 Documentación interactiva y pruebas

#### Swagger UI y OpenAPI

| Recurso | URL |
|---|---|
| Swagger UI | <http://localhost:8081/swagger-ui.html> |
| Especificación OpenAPI 3 (JSON) | <http://localhost:8081/v3/api-docs> |

La especificación se genera desde las anotaciones de los controladores
(`@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`, `@Schema`), de modo que **no existe un
contrato mantenido aparte que pueda quedar desfasado**. `OpenApiConfig` añade el título, la
versión, el contacto, el servidor local y el esquema de seguridad `bearerAuth` como
requisito global: en Swagger UI aparece el botón **Authorize**, y una vez pegado el token
de `POST /api/v1/auth/login` se envía en todas las operaciones protegidas.

Recorrido sugerido en Swagger UI:

1. `POST /api/v1/auth/login` con `admin` / `Chn2026*Demo` y copiar el `token`.
2. **Authorize** → pegar el token.
3. `GET /api/v1/resumen` para confirmar el acceso.
4. `POST /api/v1/solicitudes/simulacion`, y después `aprobar` sobre la solicitud **7** u
   **8** (las demás ya están resueltas y responden `409`).
5. `POST /api/v1/pagos` sobre el préstamo **1** o el **4**.

#### Colecciones de Postman e Insomnia

La carpeta `postman/` mantiene una colección con las **105 peticiones** repartidas en 9
carpetas, con un guion de aserciones en cada una (**324 aserciones**): cubre los 25 endpoints
—incluidas las descargas de reportes en PDF y Excel, cuyas pruebas verifican el `Content-Type`,
el `Content-Disposition` y la firma binaria del archivo—, sus **filtros de búsqueda**, la
matriz de roles y 25 casos de error.

| Archivo | Qué es |
|---|---|
| `postman/CHN-Prestamos.postman_collection.json` | Colección Postman v2.1.0 |
| `postman/CHN-Prestamos.postman_environment.json` | Entorno «CHN - Local» con las variables y los identificadores de ejemplo |
| `postman/CHN-Prestamos.insomnia.json` | La misma cobertura exportada para Insomnia (formato 4) |

El token no se pega petición por petición: se define a nivel de colección
(*Authorization* → *Bearer Token* → `{{token}}`) y la carpeta de autenticación lo guarda en
la variable de entorno al iniciar sesión. El detalle de uso está en `postman/README.md`.

Como alternativa siempre vigente —porque se genera del código y no puede desfasarse— la
especificación se importa directamente: *Import* → *Link* →
`http://localhost:8081/v3/api-docs` en Postman, o *Import From* → *URL* en Insomnia. Genera
una petición por operación con sus parámetros y ejemplos, pero sin las aserciones de la
colección mantenida.

#### Prueba de humo ejecutable

`tools/pruebas/prueba-api.sh` es la comprobación de referencia de extremo a extremo:
recorre los 23 endpoints con datos reales y verifica el código HTTP de cada caso.

```bash
docker compose up -d
bash tools/pruebas/prueba-api.sh
```

Con otro entorno de destino:

```bash
API=http://mi-servidor:8081/api/v1 bash tools/pruebas/prueba-api.sh
```

Son **57 comprobaciones** en 8 grupos y el script termina con código de salida `0` solo si
todas pasan. Junto a él, `tools/pruebas/prueba-filtros.sh` recorre los criterios de búsqueda
de los cinco listados con **59 comprobaciones** más. El detalle de ambos está en
[11](#11-pruebas).

---

## 6. Reglas de negocio

La regla de oro del sistema es que **toda regla de negocio vive en el dominio**. El DTO de
entrada repite algunas validaciones para poder responder un error por campo antes de
llegar al caso de uso, y el esquema repite otras como última línea de defensa, pero la
definición autoritativa está siempre en una clase de `domain/`.

### 6.1 Tabla de reglas, implementación y prueba

#### Cliente

| Regla | Dónde se implementa | Cómo se comprueba |
|---|---|---|
| Nombre y apellido de 2 a 60 caracteres | `Cliente` constructor → `Validaciones.exigirTexto(..., 2, 60)` | `ClienteTest`; `ClienteRequest` con `@Size(min=2,max=60)`; `ck_clientes_nombre`, `ck_clientes_apellido` |
| DPI de exactamente 13 dígitos | `Cliente` → `Validaciones.exigirDigitos(..., DIGITOS_IDENTIFICACION)` | `ClienteTest`; `ClienteControladorTest.registrar_con_dpi_invalido_devuelve_400_con_la_lista_de_errores_de_campo`; `@Pattern("\\d{13}")`; `ck_clientes_identificacion`; `prueba-api.sh` «alta con DPI invalido → 400» |
| Cliente mayor de edad (18 años cumplidos) | `Cliente.nuevo(...)` → `Period.between(fechaNacimiento, hoy).getYears() < EDAD_MINIMA`. Se valida en la fábrica y no en el constructor porque necesita la fecha actual, que llega desde fuera vía `RelojPort` | `ClienteTest`; `prueba-api.sh` «alta de menor de edad → 400». El esquema solo puede acotar con `ck_clientes_fecha_nacimiento` (≥ 1900-01-01), porque un `CHECK` no determinista solo se evaluaría al insertar |
| Fecha de nacimiento no futura | `Cliente.nuevo(...)` | `ClienteTest`; `@Past` en `ClienteRequest` |
| Dirección de 5 a 200 caracteres | `Cliente` constructor | `ClienteTest`; `@Size(min=5,max=200)`; `ck_clientes_direccion` |
| Correo con formato válido y normalizado a minúsculas | `Cliente` → `Validaciones.exigirCorreo(..., 120)`; la normalización a minúsculas también en `GestionarClientesService.exigirCorreoLibre` | `ClienteTest`; `@Email`; `ck_clientes_correo` |
| Teléfono de exactamente 8 dígitos | `Cliente` → `Validaciones.exigirDigitos(..., DIGITOS_TELEFONO)` | `ClienteTest`; `@Pattern("\\d{8}")`; `ck_clientes_telefono` |
| DPI único | `GestionarClientesService.exigirIdentificacionLibre(...)` → `ConflictoRecursoException` (409) | `GestionarClientesServiceTest`; `ClienteControladorTest.registrar_con_dpi_ya_existente_devuelve_409`; `uq_clientes_identificacion`; `prueba-api.sh` «DPI duplicado → 409» |
| Correo único, y al editar no puede colisionar con **otro** cliente | `GestionarClientesService.exigirCorreoLibre(correo, idPropio)` | `GestionarClientesServiceTest`; `uq_clientes_correo` |
| DPI y fecha de nacimiento **no editables** | `Cliente.actualizarDatos(...)` no los recibe; los campos son `final`. `ActualizarClienteRequest` tampoco los declara | `ClienteTest`; compilación |
| Borrar un cliente arrastra pagos, préstamos y solicitudes, en ese orden y en una transacción | `GestionarClientesService.eliminar(...)` | `GestionarClientesServiceTest` verifica el **orden** de las llamadas; `prueba-api.sh` grupo 7 comprueba que tras el `204` el cliente, su préstamo y su solicitud responden `404` |

#### Solicitud

| Regla | Dónde se implementa | Cómo se comprueba |
|---|---|---|
| Monto de Q1 000.00 a Q5 000 000.00 | `SolicitudPrestamo` constructor → `Validaciones.exigirRango(monto, MONTO_MINIMO, MONTO_MAXIMO)` | `SolicitudPrestamoTest`; `@DecimalMin`/`@DecimalMax` en `SolicitudRequest`; `ck_solicitudes_monto`; `prueba-api.sh` «monto fuera de rango → 400» |
| Plazo de 6 a 360 meses | `SolicitudPrestamo` constructor (`PLAZO_MINIMO_MESES`, `PLAZO_MAXIMO_MESES`) | `SolicitudPrestamoTest`; `@Min(6)`/`@Max(360)`; `ck_solicitudes_plazo` |
| Tasa anual de 0.01 % a 100 % | `SolicitudPrestamo` constructor (`TASA_MINIMA`, `TASA_MAXIMA`) | `SolicitudPrestamoTest`; `@DecimalMin`/`@DecimalMax`; `ck_solicitudes_tasa` |
| Destino de 5 a 200 caracteres | `SolicitudPrestamo` constructor | `SolicitudPrestamoTest`; `@Size(min=5,max=200)`; `ck_solicitudes_destino` |
| Ingreso mensual declarado mayor que cero | `SolicitudPrestamo` → `Validaciones.exigirPositivo(...)` | `SolicitudPrestamoTest`; `@DecimalMin("0.01")`; `ck_solicitudes_ingreso` |
| La solicitud nace `EN_PROCESO` | `SolicitudPrestamo.nueva(...)` fija `EstadoSolicitud.EN_PROCESO` y `resolucion = null` | `SolicitudPrestamoTest`; `ck_solicitudes_coherencia_resolucion` |
| El cliente de la solicitud debe existir | `GestionarSolicitudesService.exigirClienteExistente(...)` → `RecursoNoEncontradoException` (404) | `GestionarSolicitudesServiceTest` |
| Solo una solicitud `EN_PROCESO` puede aprobarse o rechazarse | `SolicitudPrestamo.exigirEnProceso()` → `ReglaNegocioException` (409) | `SolicitudPrestamoTest`; `GestionarSolicitudesServiceTest`; `prueba-api.sh` «aprobar una ya resuelta → 409» y «rechazar una ya resuelta → 409» |
| El monto aprobado no puede exceder el solicitado | `SolicitudPrestamo.aprobar(...)` | `SolicitudPrestamoTest`; `ck_solicitudes_monto_aprobado` |
| Los campos que el analista no modifique toman el valor solicitado | `GestionarSolicitudesService.aprobar(...)`, con el operador ternario sobre cada campo del comando | `GestionarSolicitudesServiceTest`; en `AprobarSolicitudRequest` los cuatro campos son opcionales |
| El rechazo exige motivo de 10 a 500 caracteres | `SolicitudPrestamo.rechazar(...)` → `Validaciones.exigirTexto(motivo, MOTIVO_MINIMO, MOTIVO_MAXIMO)` | `SolicitudPrestamoTest`; `@Size(min=10,max=500)` en `RechazarSolicitudRequest`; `ck_solicitudes_coherencia_resolucion`; `prueba-api.sh` «rechazo con motivo corto → 400» |
| Una solicitud genera **un solo** préstamo | `GestionarSolicitudesService.aprobar(...)` consulta `prestamoRepositorio.buscarPorSolicitudId(...)` antes de crear y traduce el caso a un mensaje de negocio | `GestionarSolicitudesServiceTest`; `uq_prestamos_solicitud` como respaldo final |
| El correlativo es único y seguro ante concurrencia | `CorrelativoAdaptador` con `SELECT NEXT VALUE FOR dbo.seq_solicitud` | `uq_solicitudes_numero`; ver [3.5-d](#35-decisiones-de-diseño-justificación-y-contrapartida) |

#### Préstamo y pagos

| Regla | Dónde se implementa | Cómo se comprueba |
|---|---|---|
| Al aprobar se crea el préstamo con la cuota del sistema francés | `GestionarSolicitudesService.aprobar(...)` → `CalculadoraAmortizacion.calcular(...)` → `Prestamo.nuevo(...)`, todo en la misma transacción | `GestionarSolicitudesServiceTest`; `CalculadoraAmortizacionTest` |
| El préstamo nace `VIGENTE`, sin pagos, con vencimiento = desembolso + plazo | `Prestamo.nuevo(...)` → `fechaDesembolso.plusMonths(plazoMeses)` | `PrestamoTest`; `ck_prestamos_vencimiento` |
| La cuota y la tasa quedan congeladas al desembolso | Los campos son `final` en `Prestamo`; el plan se recalcula siempre desde ellos | `PrestamoTest`; ver [3.5-h](#35-decisiones-de-diseño-justificación-y-contrapartida) |
| El monto del pago debe ser mayor que cero | `Prestamo.aplicarPago(...)` → `Validaciones.exigirPositivo(...)` | `PrestamoTest`; `PagoControladorTest.registrar_un_monto_de_cero_devuelve_400_con_el_campo_monto`; `@DecimalMin("0.01")`; `ck_pagos_monto`; `prueba-api.sh` «pago de monto 0 → 400» |
| El pago nunca puede exceder el saldo pendiente | `Prestamo.aplicarPago(...)` → `ReglaNegocioException` con el saldo disponible en el mensaje | `PrestamoTest`; `PagoControladorTest.registrar_un_monto_mayor_al_saldo_devuelve_409`; `ck_prestamos_pagado` como respaldo; `prueba-api.sh` «pago mayor al saldo → 409» |
| Se rechaza un pago sobre un préstamo `LIQUIDADO` | `Prestamo.aplicarPago(...)` comprueba `estaLiquidado()` primero | `PrestamoTest`; `RegistrarPagosServiceTest` |
| El préstamo debe existir | `RegistrarPagosService.registrar(...)` → `RecursoNoEncontradoException` (404) | `PagoControladorTest.registrar_sobre_un_prestamo_inexistente_devuelve_404`; `prueba-api.sh` |
| Cada recibo guarda saldo anterior y posterior | `RegistrarPagosService.registrar(...)` los toma antes y después de `aplicarPago`; `Pago.nuevo(...)` los conserva | `RegistrarPagosServiceTest`; `PagoControladorTest.registrar_devuelve_201_con_el_recibo_y_los_saldos`; `ck_pagos_aritmetica` |
| Al llegar el saldo a cero el préstamo pasa a `LIQUIDADO` | `Prestamo.aplicarPago(...)`: `if (getSaldoPendiente().signum() == 0) estado = LIQUIDADO` | `PrestamoTest`; `RegistrarPagosServiceTest` verifica además el segundo registro de auditoría `PRESTAMO_LIQUIDADO`; `ck_prestamos_liquidado` |
| El saldo se deriva siempre, nunca se escribe | `Prestamo.getSaldoPendiente()` = `montoTotalAPagar - totalPagado`; en la base, columna calculada `PERSISTED` | `PrestamoTest`; ver [3.5-e](#35-decisiones-de-diseño-justificación-y-contrapartida) |
| Todo importe se maneja con 2 decimales y redondeo `HALF_UP` | `Montos.normalizar(...)`, aplicado en cada agregado y en la calculadora | `CalculadoraAmortizacionTest` comprueba `cuota.scale() == 2` |

#### Evaluación de capacidad de pago (informativa)

| Regla | Dónde se implementa | Cómo se comprueba |
|---|---|---|
| Se recomienda si la cuota compromete **40 % o menos** del ingreso declarado | `EvaluadorCapacidadPago.evaluar(...)`, constante `PORCENTAJE_MAXIMO_ENDEUDAMIENTO = 40` | `EvaluadorCapacidadPagoTest` (8 pruebas); parámetro `PORCENTAJE_MAXIMO_ENDEUDAMIENTO` en `dbo.parametros` |
| …y el cliente tiene **menos de 3** préstamos vigentes | `EvaluadorCapacidadPago`, constante `MAXIMO_PRESTAMOS_VIGENTES = 3`; el conteo lo aporta `PrestamoRepositorio.contarVigentesPorCliente(...)` | `EvaluadorCapacidadPagoTest`; parámetro `MAXIMO_PRESTAMOS_VIGENTES` |
| El resultado es una **recomendación, no un bloqueo** | Ningún caso de uso consulta `recomendado` para impedir la aprobación: `GestionarSolicitudesService.simular(...)` solo lo devuelve | `GestionarSolicitudesServiceTest`: una solicitud se aprueba aunque la evaluación no la recomiende |
| El dictamen viene redactado en texto | `EvaluadorCapacidadPago.describir(...)` compone la frase que se muestra en pantalla y queda en el expediente | `EvaluadorCapacidadPagoTest` |

El porcentaje comprometido se calcula como
`cuotaMensual × 100 / ingresoMensual`, con escala 2 y `HALF_UP`.

#### Autenticación

| Regla | Dónde se implementa | Cómo se comprueba |
|---|---|---|
| Mensaje idéntico para usuario inexistente, inactivo y contraseña incorrecta | `AutenticarUsuarioService`, constante `CREDENCIALES_INVALIDAS` | `AutenticarUsuarioServiceTest` (7 pruebas) |
| 5 intentos fallidos bloquean la cuenta 15 minutos | `Usuario.registrarIntentoFallido(maxIntentos, minutosBloqueo, ahora)`; los valores llegan de `app.seguridad.max-intentos-fallidos` y `minutos-bloqueo` | `UsuarioTest` (6 pruebas); `AutenticarUsuarioServiceTest` |
| Con la cuenta bloqueada **no se llega a comparar el hash** | `AutenticarUsuarioService`: la comprobación `usuario.estaBloqueado(ahora)` va **antes** de `codificadorContrasena.coincide(...)` | `AutenticarUsuarioServiceTest` |
| Un acceso correcto limpia el bloqueo y el contador | `Usuario.registrarAccesoExitoso(ahora)` | `UsuarioTest` |
| Al bloquear, el contador se reinicia | `Usuario.registrarIntentoFallido(...)`: al alcanzar el máximo fija `bloqueadoHasta` y pone `intentosFallidos = 0`, para que al vencer el bloqueo el usuario disponga del cupo completo | `UsuarioTest` |
| Todo intento, exitoso o fallido, queda en la bitácora | `AutenticarUsuarioService.auditarFallo(...)` y el registro de `LOGIN_EXITOSO`. El detalle explica la causa **solo en el log**, nunca en la respuesta HTTP | `AutenticarUsuarioServiceTest` |
| El `username` se normaliza a minúsculas y solo admite letras, números, punto, guion y guion bajo | `Usuario.validarUsername(...)` | `UsuarioTest` |

### 6.2 La fórmula de amortización

El sistema usa el **sistema francés** (cuota fija):

```
i     = tasaAnual / 100 / 12                (tasa efectiva mensual, como fracción)
cuota = monto · i / (1 − (1 + i)^−n)         (n = plazo en meses)
```

Con tasa cero la fórmula se indefine (el denominador vale 0), así que la cuota es el simple
reparto del capital: `cuota = monto / n`.

La implementación está en `CalculadoraAmortizacion.calcularCuotaMensual(...)`:

```java
BigDecimal tasaMensual = tasaAnual.divide(CIEN, PRECISION).divide(MESES_DEL_ANIO, PRECISION);
if (tasaMensual.signum() == 0) {
    return Montos.normalizar(monto.divide(new BigDecimal(plazoMeses), PRECISION));
}
BigDecimal factor = BigDecimal.ONE.add(tasaMensual).pow(plazoMeses, PRECISION);
BigDecimal denominador = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(factor, PRECISION), PRECISION);
return Montos.normalizar(monto.multiply(tasaMensual, PRECISION).divide(denominador, PRECISION));
```

Tres detalles de precisión importan:

1. Todo se calcula con `BigDecimal`, nunca con `double`: un importe en quetzales no admite
   el error de representación binaria.
2. Los pasos intermedios usan `MathContext.DECIMAL64` (16 dígitos significativos) y **solo
   el resultado** se redondea a 2 decimales con `Montos.normalizar` (`HALF_UP`).
3. `dbo.fn_calcular_cuota` replica exactamente estos pasos en T-SQL, incluido el
   redondeo *la mitad hacia arriba*, para que un reporte en SQL y la API coincidan.

#### Generación del plan y cuadre al centavo

`CalculadoraAmortizacion.calcular(...)` recorre el plazo mes a mes:

```
saldoInicial  = saldo del mes anterior (el primero es el monto financiado)
abonoInteres  = redondear(saldoInicial · i)
abonoCapital  = redondear(cuota − abonoInteres)
saldoFinal    = saldoInicial − abonoCapital
```

En la **última cuota** —o antes, si el abono calculado agotaría el saldo— el capital se
fija al saldo exacto y la cuota se recompone (`abonoCapital + abonoInteres`). Así la suma
de los abonos a capital coincide **al centavo** con el monto financiado y el saldo final es
exactamente `0.00`, sin arrastrar el residuo de los redondeos.

#### Ejemplo numérico comprobado

**Q100 000.00 a 12 meses al 12 % anual.**

```
i     = 12 / 100 / 12 = 0.01
cuota = 100000 · 0.01 / (1 − 1.01^−12)
      = 1000 / (1 − 0.887449)
      = 1000 / 0.112551
      = 8884.88
```

| Cuota | Saldo inicial | Cuota | Abono a capital | Abono a interés | Saldo final |
|---:|---:|---:|---:|---:|---:|
| 1 | 100 000.00 | 8 884.88 | 7 884.88 | 1 000.00 | 92 115.12 |
| 2 | 92 115.12 | 8 884.88 | 7 963.73 | 921.15 | 84 151.39 |
| 3 | 84 151.39 | 8 884.88 | 8 043.37 | 841.51 | 76 108.02 |
| 4 | 76 108.02 | 8 884.88 | 8 123.80 | 761.08 | 67 984.22 |
| 5 | 67 984.22 | 8 884.88 | 8 205.04 | 679.84 | 59 779.18 |
| 6 | 59 779.18 | 8 884.88 | 8 287.09 | 597.79 | 51 492.09 |
| 7 | 51 492.09 | 8 884.88 | 8 369.96 | 514.92 | 43 122.13 |
| 8 | 43 122.13 | 8 884.88 | 8 453.66 | 431.22 | 34 668.47 |
| 9 | 34 668.47 | 8 884.88 | 8 538.20 | 346.68 | 26 130.27 |
| 10 | 26 130.27 | 8 884.88 | 8 623.58 | 261.30 | 17 506.69 |
| 11 | 17 506.69 | 8 884.88 | 8 709.81 | 175.07 | 8 796.88 |
| **12** | 8 796.88 | **8 884.85** | 8 796.88 | 87.97 | **0.00** |
| | | **106 618.53** | **100 000.00** | **6 618.53** | |

Comprobaciones que hacen las pruebas:

- La cuota de referencia es **Q8 884.88**
  (`CalculadoraAmortizacionTest.calcula_la_cuota_conocida_de_referencia`).
- La suma de los abonos a capital es **exactamente el monto financiado**, Q100 000.00.
- El saldo final de la última cuota es **0.00**.
- El plan tiene una cuota por cada mes del plazo, numeradas en orden
  (`el_plan_contiene_una_cuota_por_cada_mes_del_plazo_y_numeradas_en_orden`).
- Con tasa 0, la cuota es `monto / n` y el plan no genera intereses
  (`con_tasa_cero_la_cuota_es_el_monto_entre_el_plazo`,
  `con_tasa_cero_el_plan_no_genera_intereses`).
- La cuota siempre tiene escala 2.

#### Nota de precisión sobre `monto_total_a_pagar`

El total a pagar es la **suma real de las cuotas del plan**, no `cuota × plazo`. Las dos
cifras difieren en los céntimos que absorbe la última cuota: en el ejemplo,
`8 884.88 × 12 = 106 618.56`, pero el plan suma **106 618.53** (3 céntimos menos).

Los dos orígenes de un préstamo guardan la misma cifra:

| Origen | Cómo se calcula | Valor en el ejemplo |
|---|---|---|
| Préstamo creado por la aplicación al aprobar | `plan.montoTotal()`, la **suma real de las 12 cuotas** (la última ya ajustada) | 106 618.53 |
| Préstamo cargado por `V900__datos_demo.sql` | `dbo.fn_total_plan(monto, tasa, plazo)`, que recorre el plan en T-SQL con el mismo ajuste final | 106 618.53 |

Así el encabezado de un reporte y la fila de totales de su plan de amortización cuadran
también en los préstamos de demostración, y un préstamo de la demo se puede comparar
céntimo a céntimo con uno creado por la API.

---

## 7. Seguridad de la información

### 7.1 Autenticación

**Esquema:** JWT firmado con **HS256** (`io.jsonwebtoken` 0.12.6), sin estado.

| Aspecto | Implementación |
|---|---|
| Emisión | `ProveedorJwt.generar(usuario)`. *Claims*: `sub` (username), `rol`, `nombre`, `iss` (`chn-prestamos`), `iat`, `exp` |
| Clave | Se deriva **una sola vez al arrancar** con `Keys.hmacShaKeyFor(secreto.getBytes(UTF_8))`. El secreto llega por `JWT_SECRET` |
| Longitud mínima del secreto | `PropiedadesAplicacion.Jwt` lo valida con `@NotBlank @Size(min = 32)`. **Si falta o es corto, la aplicación no arranca**: HS256 derivaría una clave por debajo del tamaño del digest y jjwt la rechazaría |
| Vigencia | 480 minutos (8 horas, una jornada laboral) por omisión, configurable con `JWT_EXPIRACION_MINUTOS`, con un mínimo de 5 (`@Min(5)`) |
| Marcas de tiempo | `Instant.now()` (epoch UTC), como exige la especificación del JWT, no el reloj de zona del negocio |
| Verificación | `ProveedorJwt.validar(token)` comprueba **firma, emisor y vigencia** con `verifyWith(clave).requireIssuer(emisor)` |
| Resultado de una verificación fallida | `Optional.empty()` sin distinguir el motivo. El filtro no debe dar pistas sobre si el token está caducado, mal firmado o manipulado. Solo se registra el **tipo** de excepción a nivel `DEBUG`, nunca el token |
| Traducción a Spring Security | `JwtAutenticacionFiltro` pone un `UsernamePasswordAuthenticationToken` con la autoridad `ROLE_{rol}` y las credenciales en `null`, para que la contraseña no esté en memoria fuera del inicio de sesión |
| Sin consulta a la base por petición | El token ya viene verificado criptográficamente; no se relee el usuario en cada llamada |
| Token ausente o inválido | El filtro **no responde**: deja el contexto sin autenticación y la cadena decide (401 si la ruta es privada, 200 si es pública) |
| Bloqueo de cuenta | 5 intentos fallidos → 15 minutos de bloqueo (`Usuario.registrarIntentoFallido`). Configurable en `app.seguridad` |
| No enumeración de usuarios | Mensaje `Credenciales inválidas` idéntico para usuario inexistente, inactivo y contraseña errónea |
| Sin oráculo de tiempos | Con la cuenta bloqueada no se llega a comparar el hash, así que el tiempo de respuesta no delata si el usuario existe |
| Límite por IP | `LimitadorLoginFiltro`: 10 intentos por IP cada 5 minutos → `429` con cabecera `Retry-After`. Se instala **antes** del filtro JWT, para frenar la fuerza bruta antes de gastar ciclos en verificar firmas o consultar la base |
| Sin usuario de respaldo | `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)`. Sin esta exclusión, Spring Boot crearía un usuario `user` en memoria con contraseña aleatoria impresa en el log |

El bloqueo de cuenta y el límite por IP son complementarios: el primero frena el ataque
contra **un usuario concreto**, el segundo el barrido de **muchos usuarios** desde el mismo
origen. El limitador usa `getRemoteAddr()` y **no** `X-Forwarded-For`, precisamente porque
un cliente puede falsificar esa cabecera y burlar el límite.

### 7.2 Autorización

Dos capas, ambas activas:

1. **Reglas por ruta y método** en `SeguridadConfig.cadenaApi`, con las más específicas
   primero y un cierre por defecto `anyRequest().authenticated()`.
2. **`@PreAuthorize`** en cada método de controlador, que documenta el permiso junto al
   código y sobrevive a un refactor de rutas.

#### Matriz de permisos

| Operación | ADMIN | ANALISTA | CAJERO | CONSULTA |
|---|:---:|:---:|:---:|:---:|
| Iniciar sesión y ver su perfil | sí | sí | sí | sí |
| Consultar clientes, solicitudes, préstamos, pagos, amortización y resumen | sí | sí | sí | sí |
| Crear y editar clientes | sí | sí | — | — |
| **Eliminar clientes** | **sí** | — | — | — |
| Crear solicitudes | sí | sí | — | — |
| Aprobar y rechazar solicitudes | sí | sí | — | — |
| Simular préstamos | sí | sí | — | — |
| **Registrar pagos** | sí | — | **sí** | — |
| **Ver la auditoría** | **sí** | — | — | — |

Los tres permisos exclusivos tienen una razón explícita en el código:

- **Eliminar un cliente** arrastra sus solicitudes, préstamos y pagos: es la operación más
  destructiva del sistema.
- **La auditoría** revela la actividad de todos los usuarios y sus direcciones IP.
- **Recibir dinero** es función de caja, separada del análisis de crédito. Un analista que
  aprueba no cobra, y un cajero que cobra no aprueba: es una separación de funciones
  deliberada.

El frontend replica la matriz en `src/dominio/catalogos.js` (`PERMISOS`,
`PERMISOS_POR_ROL`) para ocultar lo que el usuario no puede hacer, pero **eso no es
control de acceso**: solo evita llegar a un 403 innecesario. La decisión la toma siempre
el backend, y `prueba-api.sh` lo verifica con seis casos cruzados (grupo 6).

### 7.3 Protección de credenciales

| Control | Implementación |
|---|---|
| Algoritmo | **BCrypt con fuerza 12** (2^12 iteraciones), `BCryptPasswordEncoder(12)` en `SeguridadConfig` |
| Sal | BCrypt genera una sal aleatoria por contraseña, así que dos usuarios con la misma clave tienen hashes distintos |
| Comparación | `codificador.matches(...)`, que compara en tiempo constante. Si el hash almacenado fuera nulo se devuelve `false` en lugar de lanzar, para no distinguir usuarios inexistentes |
| Almacenamiento | Solo el hash, en `usuarios.password_hash`. El esquema no tiene ninguna columna donde pudiera caber una contraseña en claro |
| Nunca en el log | `Usuario.toString()` **excluye** el hash a propósito. `LoginRequest` no se registra. El detalle de un login fallido dice «Contraseña incorrecta (intento N de M)», nunca el valor |
| Nunca en un script versionado | Los cuatro usuarios de arranque los crea `CargadorUsuariosIniciales` al primer arranque, no `V3__datos_iniciales.sql`, precisamente para que la contraseña se cifre antes de tocar la base |
| Idempotencia | Si el usuario ya existe **no se toca**: un reinicio nunca sobrescribe una contraseña cambiada por el administrador |
| Advertencia activa | Si no se definió la variable de entorno correspondiente, el usuario se crea con la contraseña de demostración y queda un `WARN` explícito en el log indicando que debe cambiarse |
| Token en el navegador | `sessionStorage`, no `localStorage` (ver [8](#8-frontend)) |

### 7.4 Protección del transporte y cabeceras

#### Transporte

| Tramo | Estado |
|---|---|
| Navegador → Nginx | HTTP en el entorno de examen. **Pendiente para producción:** terminar TLS aquí |
| Nginx → backend | HTTP dentro de la red privada `chn-red` de Docker; ningún puerto del backend se publica salvo el que el compose expone a propósito |
| Backend → SQL Server | **Cifrado**: la cadena JDBC lleva `encrypt=true`. Con `trustServerCertificate=true` para aceptar el certificado autofirmado del contenedor de desarrollo. **Pendiente para producción:** instalar un certificado de una CA de confianza y pasar a `trustServerCertificate=false`, para que el cifrado también autentique al servidor y no sea vulnerable a un intermediario |

#### Cabeceras en el backend (`SeguridadConfig`)

| Cabecera | Valor | Contra qué |
|---|---|---|
| `X-Frame-Options` | `DENY` | Clickjacking |
| `X-Content-Type-Options` | `nosniff` | Adivinación de tipo MIME |
| `Referrer-Policy` | `no-referrer` | Fuga de la URL interna al navegar a un sitio externo |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Degradación a HTTP una vez servido tras TLS |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` | La API solo devuelve JSON: no debe cargar ningún recurso |

#### Cabeceras en Nginx (`frontend/nginx.conf`)

| Cabecera | Valor |
|---|---|
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), accelerometer=()` |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |

La política del SPA permite `'unsafe-inline'` **solo para estilos**, porque React inyecta
estilos en línea; los scripts quedan restringidos a `'self'`, que es lo que de verdad
protege frente a XSS. No se cargan CDNs, fuentes externas ni scripts de terceros.

Las cabeceras están **repetidas en cada bloque `location`** de forma deliberada: en Nginx,
si un `location` declara aunque sea un `add_header`, deja de heredar **todos** los del
bloque padre. Omitirlas haría que `index.html` —justo el documento que ejecuta la
aplicación— se sirviera sin CSP.

Otros ajustes de Nginx: `server_tokens off` (no revela la versión),
`client_max_body_size 1m` (el sistema solo envía formularios JSON pequeños),
`location ~ /\. { deny all; }` (bloquea archivos ocultos como `.env` o `.git` que pudieran
quedar en la raíz), y `Cache-Control: no-store` en todas las respuestas de `/api`, porque
son datos vivos.

#### CORS y CSRF

| Aspecto | Decisión |
|---|---|
| CORS | Lista blanca configurable con `CORS_ORIGENES`, **nunca comodín**. Métodos `GET, POST, PUT, DELETE, OPTIONS`; cabeceras `Authorization`, `Content-Type`, `Accept`; expone `Location`; `allowCredentials = false` porque el token viaja en una cabecera y no en cookie. Se registra solo para `/api/**` |
| CSRF | **Deshabilitado, y es correcto**: la API no usa cookies de sesión. La credencial es una cabecera `Authorization` que un sitio de terceros no puede añadir a una petición del navegador |
| Sesión | `STATELESS`. Sin `formLogin`, sin `httpBasic`, sin `logout` de servidor |
| Superficie real de CORS | En producción el navegador usa **un solo origen**: Nginx sirve el SPA y hace proxy de `/api`. CORS solo interviene si alguien apunta el frontend directamente al puerto 8081 |

### 7.5 Validación de entrada en las tres capas

| Capa | Mecanismo | Qué aporta |
|---|---|---|
| **Frontend** | `src/dominio/validaciones.js`: `requerido`, `longitud`, `dpi`, `telefono`, `correo`, `fechaNacimientoMayorDeEdad`, `montoEnRango`, `plazoEnRango`, `tasaEnRango`, `numeroPositivo`, `identificadorValido`, `maximoCaracteres`, más `validarFormulario` | Avisa al usuario antes de gastar una petición. Es **comodidad, no seguridad**: se puede saltar con cualquier cliente HTTP |
| **API (DTO)** | Jakarta Bean Validation sobre los 8 *records* de petición: `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, `@Email`, `@Past`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Digits`, `@Positive`. Los parámetros de consulta se validan con `@Validated` en la clase más `@Min`/`@Max` en el argumento | Rechaza la petición antes de entrar al caso de uso y permite devolver el error **campo por campo** |
| **Dominio** | `Validaciones` (`exigirTexto`, `exigirDigitos`, `exigirCorreo`, `exigirRango`, `exigirPositivo`, `exigirNoNegativo`, `exigirNoNulo`, `exigirTextoOpcional`) invocada desde el constructor privado de cada agregado | **Autoritativa.** Un objeto de dominio no puede existir en estado inválido, entre por donde entre |
| **Base de datos** | Restricciones `CHECK`, `UNIQUE` y llaves foráneas | Defensa en profundidad: aunque alguien escriba directamente en la base, no puede dejar un estado incoherente |

Ejemplo del recorrido completo con el DPI: el formulario lo comprueba con
`validaciones.dpi`, el DTO con `@Pattern("\\d{13}")`, el dominio con
`Validaciones.exigirDigitos(..., 13)` y la base con `ck_clientes_identificacion`.

La duplicación es intencional y las cuatro definiciones se mantienen juntas. La alternativa
—validar solo en un sitio— obliga a elegir entre un mensaje de error útil (capa web) y una
garantía real (dominio y base).

### 7.6 Prevención de inyección SQL

| Vía de acceso | Cómo se protege |
|---|---|
| Consultas JPQL con `@Query` | **Siempre** parámetros nombrados con `@Param`. Nunca se concatena texto en la consulta |
| Búsqueda libre de los cinco listados | El comodín se construye **dentro** de la consulta con `LIKE LOWER(CONCAT('%', :busqueda, '%'))`: el término del usuario viaja como parámetro enlazado, no como fragmento de SQL. El patrón `:busqueda IS NULL OR ...` hace el filtro opcional con una sola consulta, sin armar SQL dinámico en Java |
| Filtros opcionales (estado, tipo, cliente, rangos de fecha, monto, plazo y saldo) | Mismo patrón `:parametro IS NULL OR <condición>`, en lugar de concatenar cláusulas `WHERE`. Ninguna consulta se construye en Java: las combinaciones de criterios se resuelven con una sentencia estática ([3.5-i](#35-decisiones-de-diseño-justificación-y-contrapartida)) |
| Valores de enum recibidos por la URL | `ConversorEnumWeb` los traduce con `Enum.valueOf` antes de llegar a la consulta: un valor fuera del catálogo responde `400` y nunca alcanza la base |
| Longitud de los criterios de texto | Los *records* de filtro acotan el texto libre a 120 caracteres y las etiquetas (usuario, acción, entidad) a 50, para limitar también el coste de los `LIKE` |
| Borrados en cascada | JPQL con `@Modifying` y parámetro nombrado, incluida la subconsulta de pagos |
| Vistas del tablero | Las tres consultas `@Query(nativeQuery = true)` del proyecto están en `ResumenJpaRepositorio`, son de solo lectura y son `SELECT` de columnas fijas: sobre `dbo.vw_resumen_general` y `dbo.vw_cartera_por_tipo`, **sin parámetros**, y sobre `dbo.vw_recaudacion_mensual`, con los dos extremos del rango como **parámetros con nombre** (`:desde`, `:hasta`, enteros `AAAAMM` calculados en el servidor con el reloj, nunca tomados de la petición) |
| Correlativos | Tres sentencias nativas que son **constantes literales** (`SELECT NEXT VALUE FOR dbo.seq_solicitud`). T-SQL no admite parámetros en `NEXT VALUE FOR`, y el nombre de la secuencia jamás proviene de entrada externa |
| Procedimientos almacenados | Se invocan con parámetros, nunca concatenando texto |

Además, `spring.jpa.open-in-view: false` y `ddl-auto: validate` reducen la superficie:
ninguna consulta se dispara fuera de la transacción y Hibernate no puede alterar el
esquema.

### 7.7 Mínimo privilegio en la base de datos

| Control | Detalle |
|---|---|
| Usuario de aplicación | El backend se conecta como **`chn_app`**, que es `db_owner` **solo** de `CHN_Prestamos`. Nunca `sa`, nunca `sysadmin` |
| Contención | Si la aplicación se viera comprometida, el daño queda dentro de esta base: no puede leer otras bases ni cambiar configuración del servidor |
| Quién usa `sa` | Únicamente el servicio efímero `db-init`, que ejecuta `00_crear_base_datos.sql` **una vez** y termina. No queda residente |
| Credenciales | `DB_USER` y `DB_PASSWORD` por variable de entorno. Ningún script del repositorio contiene una contraseña real; la de `00_crear_base_datos.sql` está parametrizada con variables de `sqlcmd` |
| Superficie de red | El puerto 1433 se publica solo para poder inspeccionar la base durante la evaluación. En producción no debería publicarse: basta con la red interna `chn-red` |

### 7.8 Gestión de secretos

| Secreto | Variable | Dónde vive |
|---|---|---|
| Contraseña de `sa` | `MSSQL_SA_PASSWORD` | `.env` (no versionado) o valor por omisión del compose |
| Contraseña de `chn_app` | `DB_PASSWORD` | ídem. En `application.yml` es `${DB_PASSWORD}` **sin valor por omisión**: si falta, la aplicación no arranca |
| Secreto de firma JWT | `JWT_SECRET` | ídem, con validación de longitud mínima al arrancar |
| Contraseñas de los 4 usuarios | `ADMIN_PASSWORD`, `ANALISTA_PASSWORD`, `CAJERO_PASSWORD`, `CONSULTA_PASSWORD` | ídem. En `application.yml` quedan **vacías** a propósito |

Controles:

- `application.yml` se versiona y **no contiene ningún secreto real**: todo llega por
  `${VARIABLE}`.
- `.env` está en `.gitignore`, junto con `*.pem` y `*.key`.
- `.dockerignore` excluye `.env`, `.env.*` (salvo `.env.example`), `*.pem`, `*.key`,
  `*.p12`, `*.pfx` y `secrets/`: **ningún secreto viaja a una imagen**.
- `.env.example` documenta cada variable con su propósito y advierte que los valores son de
  examen y deben sustituirse.
- En el frontend, `frontend/.env.example` advierte que solo las variables con prefijo
  `VITE_` llegan al navegador y que por tanto **nunca** debe ponerse ahí una clave: quedaría
  visible en el *bundle*.
- Los valores por omisión del `docker-compose.yml` existen para que el entorno de examen
  levante con un solo comando, y el propio archivo lo advierte en su encabezado: ninguna de
  esas claves debe usarse fuera del examen.

### 7.9 Auditoría

| Aspecto | Detalle |
|---|---|
| Qué se registra | Accesos (`LOGIN_EXITOSO`, `LOGIN_FALLIDO`), alta, edición y baja de clientes, creación y resolución de solicitudes, creación y liquidación de préstamos, y registro de pagos |
| Qué se guarda | Usuario, acción, entidad, identificador, detalle legible, dirección IP y fecha |
| De dónde sale el usuario y la IP | `ExtractorContextoOperacion`, centralizado para que ningún controlador interprete cabeceras de proxy por su cuenta. La IP real se toma del **primer valor** de `X-Forwarded-For` (que Nginx rellena) y, si no existe, de `getRemoteAddr()` |
| Transaccionalidad | `AuditoriaAdaptador.registrar(...)` se ejecuta **dentro de la transacción del caso de uso**: si la operación se revierte, tampoco queda el registro de una acción que nunca ocurrió |
| Robustez | Cada texto se recorta a la longitud de su columna: un detalle demasiado largo no debe tumbar la operación de negocio que se está auditando |
| Nombres estables | Las acciones son constantes de `AccionesAuditoria`, no literales escritos a mano: la bitácora se filtra por esos valores y un cambio de texto rompería el histórico |
| Contenido prohibido | **Nunca** contraseñas ni tokens. El detalle de un login fallido explica la causa para el administrador, pero esa causa no viaja en la respuesta HTTP |
| Inmutabilidad | Tabla de solo inserción y consulta. No hay endpoint que la actualice ni la borre |
| Respaldo en la base | `tr_clientes_auditoria_delete` registra el borrado de un cliente **incluso si se ejecuta fuera de la aplicación** |
| Acceso | Solo `ADMIN`, vía `GET /api/v1/auditoria` |

### 7.10 Endurecimiento de los contenedores

| Control | Detalle |
|---|---|
| Imágenes multietapa | La imagen final del backend no contiene Maven, JDK completo, código fuente ni el repositorio local de dependencias. La del frontend no contiene Node, `node_modules` ni el código fuente: solo el HTML, CSS y JS compilados |
| Base mínima | `eclipse-temurin:21-jre-alpine` y `nginx:1.27-alpine` |
| Usuario sin privilegios | El backend crea el grupo y usuario `chn` (UID/GID 10001) sin *shell* (`/sbin/nologin`) y corre con `USER chn:chn`. Si un atacante lograra ejecución remota, no sería root |
| Nginx | La imagen oficial ya declara `user nginx;`, así que los procesos *worker* —los que atienden peticiones y procesan la entrada de la red— corren sin privilegios. El maestro permanece como root solo porque el 80 es un puerto privilegiado; para eliminar incluso eso se usaría `nginxinc/nginx-unprivileged` en el 8080 |
| PID 1 correcto | `ENTRYPOINT` en forma exec (sin *shell* intermedia): la JVM es el PID 1 y recibe `SIGTERM` directo, lo que permite un apagado ordenado del pool de conexiones (`server.shutdown: graceful`) |
| Límites de memoria | `-XX:MaxRAMPercentage=75` hace que el *heap* respete el límite del contenedor; `-XX:+ExitOnOutOfMemoryError` evita una JVM zombi que responde al *healthcheck* pero no trabaja |
| Sondas de vida | `HEALTHCHECK` en las dos imágenes, más `healthcheck` en el compose, con `start_period` que cubre el arranque de Flyway |
| Las pruebas bloquean la imagen | `RUN mvn -B -f backend/pom.xml clean package` ejecuta las 262 pruebas, incluidas las 12 de ArchUnit: **un fallo detiene la construcción de la imagen** |
| Contexto de construcción reducido | `.dockerignore` excluye historial de Git, artefactos, `node_modules`, documentación, imágenes de los manuales, configuración de IDE, secretos y temporales |
| Red | Los cuatro servicios comparten la red privada `chn-red`; solo se publican los puertos declarados |
| Sin `latest` en las imágenes propias | Se etiquetan `chn-prestamos-backend:1.0.0` y `chn-prestamos-frontend:1.0.0` |

### 7.11 Tratamiento de errores

| Control | Implementación |
|---|---|
| Formato uniforme | `ManejadorExcepcionesGlobal` (`@RestControllerAdvice`) traduce **toda** excepción al mismo cuerpo. La cadena de seguridad y el limitador de peticiones serializan exactamente la misma forma, de modo que el cliente nunca recibe dos formatos distintos |
| Los 500 no revelan nada | El manejador de último recurso registra la traza completa en el log (`LOG.error`) y responde `Ocurrió un error inesperado. Contacte al administrador.` |
| Sin traza ni mensaje interno | `server.error.include-stacktrace: never`, `include-message: never`, `include-exception: false` |
| Mensajes internos sustituidos | Un JSON ilegible expondría nombres de clases internas: el mensaje original va al log y al cliente se le responde `El cuerpo de la peticion no tiene un formato JSON valido` |
| Restricciones no reveladas | El detalle de una `DataIntegrityViolationException` (que delataría el esquema) se registra en el log y al cliente se le responde `La operacion viola una restriccion de integridad de los datos` |
| Rutas inexistentes | `NoResourceFoundException` y `NoHandlerFoundException` se traducen al mismo formato de error, para que un 404 de ruta no devuelva la página HTML por omisión de Tomcat |
| Fallos de autenticación no clasificados | Un manejador de `AuthenticationException` actúa de red de seguridad: sin él, esos fallos caerían en el manejador genérico y responderían 500 |
| Datos personales fuera del log | `org.hibernate.SQL` y `org.hibernate.orm.jdbc.bind` están en `WARN`: las sentencias y sus parámetros pueden contener DPI, direcciones y correos, y no deben quedar en los archivos de log |
| Sin `ProblemDetail` | `spring.mvc.problemdetails.enabled: false`, para que Spring no emita respuestas RFC 7807 que competirían con el formato propio |

### 7.12 Riesgo → control implementado

| Riesgo | Control implementado |
|---|---|
| Fuerza bruta contra una cuenta | Bloqueo de cuenta: 5 intentos → 15 minutos (`Usuario.registrarIntentoFallido`) |
| Fuerza bruta distribuida entre muchas cuentas | `LimitadorLoginFiltro`: 10 intentos por IP cada 5 minutos → 429 con `Retry-After` |
| Enumeración de usuarios válidos | Mensaje `Credenciales inválidas` idéntico para usuario inexistente, inactivo y contraseña errónea |
| Filtración del tiempo de respuesta como oráculo | Con la cuenta bloqueada no se compara el hash; BCrypt compara en tiempo constante |
| Robo de la base de credenciales | BCrypt fuerza 12 con sal por contraseña; el esquema no admite texto plano |
| Manipulación o falsificación de un token | Firma HS256 verificada en cada petición, con emisor y vigencia obligatorios; secreto de 32 caracteres como mínimo, validado al arrancar |
| Token robado usado indefinidamente | Vigencia de 8 horas; `sessionStorage` se borra al cerrar la pestaña |
| Escalada de privilegios | Doble control: reglas por ruta en `SeguridadConfig` más `@PreAuthorize` por método. `prueba-api.sh` verifica 6 casos cruzados de rol |
| Inyección SQL | Parámetros nombrados en todo JPQL; el comodín del `LIKE` se arma dentro de la consulta; ninguna concatenación de SQL con entrada del usuario |
| XSS | CSP con `script-src 'self'`; React escapa el contenido por omisión; `X-Content-Type-Options: nosniff` |
| CSRF | API sin cookies: la credencial es una cabecera que un tercero no puede añadir. Sesión `STATELESS` |
| Clickjacking | `X-Frame-Options: DENY` y `frame-ancestors 'none'`, en el backend y en Nginx |
| Peticiones desde orígenes no autorizados | CORS por lista blanca (`CORS_ORIGENES`), nunca comodín, sin credenciales por cookie |
| Fuga de información por mensajes de error | Formato uniforme sin trazas; mensajes internos sustituidos; detalle solo en el log |
| Fuga de datos personales por los registros | SQL y sus parámetros en `WARN`; contraseñas y tokens nunca se registran; `Usuario.toString()` excluye el hash |
| Fuga de información por el endpoint de salud | Actuator expone solo `health` e `info`, sin detalle de componentes ni variables de entorno |
| Compromiso de la aplicación escalado al servidor de base de datos | `chn_app` es `db_owner` solo de `CHN_Prestamos`; el backend nunca usa `sa` |
| Escapada de un contenedor comprometido a root | El backend corre como usuario no root (UID 10001) |
| Interceptación del tráfico a la base | `encrypt=true` en la cadena JDBC |
| Secretos filtrados por el repositorio o la imagen | Variables de entorno; `.env` en `.gitignore`; `.dockerignore` excluye secretos; `application.yml` sin valores reales |
| Usuario por omisión creado por el framework | `UserDetailsServiceAutoConfiguration` excluida |
| Borrado silencioso o no trazado de datos | Cascada explícita en el caso de uso, restringida a `ADMIN`, auditada por la aplicación **y** por el trigger `tr_clientes_auditoria_delete` |
| Manipulación de la bitácora | Tabla de solo inserción y consulta; sin endpoint de escritura ni de borrado |
| Estado incoherente escrito por fuera de la aplicación | Restricciones `CHECK` que replican las reglas del dominio, incluidas las compuestas de coherencia de resolución y de aritmética del recibo |
| Saldo desincronizado de los pagos | `saldo_pendiente` es columna calculada `PERSISTED`: no se puede escribir |
| Correlativos duplicados con usuarios concurrentes | Secuencias atómicas de SQL Server, más restricciones `UNIQUE` |
| Cambio del esquema por descuido del ORM | `ddl-auto: validate`: Hibernate solo comprueba y falla al arrancar si el mapeo divergió |
| Corrupción del esquema por editar una migración aplicada | `validate-on-migrate: true`: el *checksum* cambia y el arranque falla |
| Degradación de la arquitectura con el tiempo | 12 pruebas ArchUnit que bloquean la construcción de la imagen |

### 7.13 Pendiente para producción

Lo que sigue **no** está implementado y sería exigible antes de un despliegue real. Se
enumera de forma explícita porque un manual que presume de seguridad completa es menos útil
que uno que dice dónde están los límites.

| Pendiente | Situación actual | Qué habría que hacer |
|---|---|---|
| **TLS con certificado propio** | El navegador habla HTTP con Nginx. El tramo a la base va cifrado pero con `trustServerCertificate=true`, que cifra sin autenticar al servidor | Terminar TLS en Nginx (o en el balanceador) con un certificado de una CA de confianza y redirección forzada de HTTP; en la base, instalar certificado y pasar a `trustServerCertificate=false`. La cabecera HSTS ya está emitida y empezará a surtir efecto en ese momento |
| **Secretos en un gestor** | Variables de entorno y un `.env` no versionado | Mover `JWT_SECRET`, `DB_PASSWORD` y las contraseñas iniciales a un gestor (Azure Key Vault, HashiCorp Vault, AWS Secrets Manager) e inyectarlos en ejecución, para que no queden en el entorno del contenedor ni en el historial de despliegue |
| **Limitador distribuido** | `LimitadorLoginFiltro` cuenta **en memoria del proceso**. Con varias réplicas cada una contaría por separado, y el límite efectivo se multiplicaría por el número de instancias | Delegar en un API Gateway o WAF, o usar un contador compartido en Redis. El código lo documenta en su propio Javadoc |
| **Rotación de claves** | La clave HS256 se deriva una vez al arrancar. Rotarla invalida todos los tokens vigentes de golpe | Publicar un identificador de clave (`kid`) en el encabezado del token y aceptar dos claves durante la ventana de rotación; o pasar a firma asimétrica (RS256/ES256) con un JWKS, de modo que la clave privada no tenga que estar en cada instancia |
| **Copias de seguridad** | El volumen `chn-sqlserver-datos` persiste entre reinicios, pero **no hay respaldos** ni prueba de restauración | Respaldo completo periódico más *log* de transacciones, con retención definida, almacenamiento fuera del host y **prueba de restauración** verificada. Un respaldo que nunca se restauró no es un respaldo |
| **Revocación de tokens** | Al ser sin estado, un token robado sirve hasta su expiración: no hay forma de invalidarlo antes | Lista de revocación de corta vida en Redis indexada por un *claim* `jti`, o tokens de acceso muy breves más un *refresh token* revocable. Ambas opciones reintroducen estado, que es exactamente el costo que la arquitectura sin estado evita: es un compromiso a decidir con el negocio |
| Alta de usuarios y gestión de contraseñas | Los cuatro usuarios los crea el arranque; no hay endpoint de alta, cambio ni recuperación | Módulo de administración de usuarios con política de complejidad, caducidad, historial de contraseñas y un flujo de recuperación que no dependa del administrador |
| Vigilancia y alertas | Solo `health` e `info`, sin métricas ni alertas | Exponer métricas a un recolector (Prometheus), centralizar los registros y alertar sobre picos de `LOGIN_FALLIDO`, `429` y `500` |
| Cifrado de datos personales en reposo | El volumen no está cifrado | Transparent Data Encryption en SQL Server y cifrado del volumen o del disco subyacente |
| Revisión de dependencias | No hay análisis automático | Integrar un análisis de vulnerabilidades de dependencias y de imágenes base en la canalización de construcción |

---

## 8. Frontend

React 18.3.1 con Vite 5.4.10, **sin librería de UI ni de gráficas**: los 31 componentes
reutilizables son propios —incluidas las gráficas del tablero, dibujadas en SVG— y todo el
estilo sale de variables CSS.

### 8.1 Estructura de carpetas

```
frontend/
├── index.html                    Documento raíz (lang="es")
├── vite.config.js                Plugin de React, proxy /api, división de chunks
├── package.json                   4 dependencias de producción, 2 de desarrollo
├── nginx.conf                    SPA, compresión, proxy /api, cabeceras de seguridad
├── Dockerfile                     Multietapa: node:20-alpine → nginx:1.27-alpine
├── .env.example                   VITE_API_URL con su advertencia de seguridad
└── src/
    ├── main.jsx                   Punto de entrada; monta los proveedores
    ├── App.jsx                    Mapa de rutas
    │
    ├── dominio/                   LÓGICA PURA — sin React, sin axios, sin DOM
    │   ├── catalogos.js           ESTADOS_SOLICITUD, ESTADOS_PRESTAMO, TIPOS_PRESTAMO,
    │   │                          ROLES, FORMAS_PAGO, PERMISOS, PERMISOS_POR_ROL,
    │   │                          LIMITES, y las funciones de etiqueta y tono
    │   ├── formato.js             moneda, monedaCorta, numero, porcentaje, fecha,
    │   │                          fechaHora, fechaISOParaInput, hoyParaInput, plazo,
    │   │                          iniciales, recortar
    │   ├── fechas.js              Única fuente de verdad del formato de fecha: máscara
    │   │                          dd/mm/aaaa, conversión ISO ↔ visible, validación real,
    │   │                          rejilla del mes (lunes primero) y aritmética de días
    │   ├── acciones.js            Catálogo de acciones: color e icono de cada una
    │   ├── validaciones.js        12 validadores más validarFormulario y sinErrores
    │   └── amortizacion.js        redondear, calcularCuota, generarPlan,
    │                              porcentajeComprometido
    │
    ├── aplicacion/                CASOS DE USO DE LA INTERFAZ
    │   ├── AutenticacionContexto.jsx   Sesión, permisos y manejo del 401
    │   ├── NotificacionContexto.jsx    Cola de avisos y useAvisoDeError
    │   └── hooks/                      useClientes, useSolicitudes, usePrestamos,
    │                                   usePagos, useResumen, useAuditoria, useFiltros,
    │                                   useFormulario
    │
    ├── infraestructura/           ADAPTADORES
    │   ├── http/
    │   │   ├── clienteHttp.js          Instancia única de axios + interceptores
    │   │   ├── adaptadorAutenticacion.js
    │   │   ├── adaptadorClientes.js
    │   │   ├── adaptadorSolicitudes.js
    │   │   ├── adaptadorPrestamos.js
    │   │   ├── adaptadorPagos.js
    │   │   ├── adaptadorResumen.js
    │   │   └── adaptadorAuditoria.js
    │   └── almacenamiento/
    │       └── almacenSesion.js        sessionStorage con try/catch en cada acceso
    │
    ├── estilos/
    │   ├── tokens.css             Variables de diseño: ÚNICO lugar con colores
    │   ├── animaciones.css        15 keyframes, sus clases utilitarias y el bloque
    │   │                          final de prefers-reduced-motion
    │   └── global.css             Reinicio, tipografía y utilidades
    │
    └── ui/
        ├── componentes/           31 componentes reutilizables + base.css +
        │                          contextoCampo.js + graficas.js + 2 hooks de
        │                          gráficas + index.js (barrel)
        ├── disenio/               AppLayout, BarraLateral, BarraSuperior,
        │                          RutaProtegida, usarSesion.js
        └── paginas/               13 pantallas
```

Es el **mismo esquema hexagonal del backend, traducido al navegador**: `dominio` no conoce
React, `aplicacion` no conoce axios (habla con los adaptadores), y solo
`infraestructura/http` importa axios. La correspondencia es deliberada:

| Backend | Frontend | Regla |
|---|---|---|
| `domain/` | `src/dominio/` | Funciones puras. Sin React, sin axios, sin DOM |
| `application/usecase/` | `src/aplicacion/` | Orquestación: hooks y contextos que combinan adaptadores y estado |
| `infrastructure/adapter/out/` | `src/infraestructura/` | Único punto que conoce la tecnología externa (HTTP, almacenamiento del navegador) |
| `infrastructure/adapter/in/web/` | `src/ui/` | Presentación |

### 8.2 Catálogo de los 31 componentes reutilizables

Todos se exportan desde `src/ui/componentes/index.js`, de modo que una pantalla importa
`{ Boton, Tabla, Modal } from '../componentes/index.js'`.

#### Acciones e iconografía

| Componente | Propósito |
|---|---|
| `Boton` | Acción de la interfaz, con el color al servicio del significado. Nueve variantes (`primario`, `secundario`, `exito`, `peligro`, `advertencia`, `info`, `neutro`, `texto`, `enlace`) elegidas por **funcionalidad** y no por estética; tres tonos (`solido`, `suave`, `contorno`); tamaños `sm`/`md`/`lg`; `ancho` `auto` o `completo`; icono a la izquierda por nombre o como nodo; y `soloIcono` para el botón cuadrado de las filas de una tabla, que **exige `aria-label`**. Con `cargando=true` se inhabilita **de verdad** (`disabled`), muestra un indicador en línea y expone `aria-busy`. Ver [8.3](#83-sistema-de-diseño-tokens-color-y-movimiento) |
| `Icono` | Único punto de acceso a los **37 iconos** del sistema, como SVG en línea (sin fuente de iconos ni peticiones extra). Si el nombre no existe devuelve `null`, así un nombre equivocado nunca rompe la pantalla. Por omisión es decorativo (`aria-hidden`) |

#### Formularios

| Componente | Propósito |
|---|---|
| `CampoFormulario` | Envoltorio de un control: `<label>` asociada por `htmlFor`, marca de obligatorio, texto de ayuda y mensaje de error. **Publica los identificadores de ayuda y error en un contexto** (`contextoCampo.js`) para que el control los tome con `aria-describedby` y `aria-invalid` automáticamente |
| `CampoTexto` | `input` de texto controlado (texto, correo, contraseña). Hereda del `CampoFormulario` los enlaces ARIA |
| `CampoNumero` | `input` numérico con prefijo y sufijo opcionales (`Q`, `%`, `meses`). Los adornos son decorativos: la unidad se indica también en la etiqueta o la ayuda |
| `CampoFecha` | Campo de fecha **propio**, ya no el `<input type="date">` del navegador. Una caja de texto con máscara `dd/mm/aaaa` más un panel de calendario de tres columnas —años desplazables, meses en rejilla de 2×6 y días con la semana empezando en lunes—, dibujado en un portal. Emite el valor en ISO `aaaa-MM-dd`, así que el contrato con la API no cambió. Ver [8.4](#84-el-selector-de-fechas) |
| `CampoRangoFechas` | Pareja «`[dd/mm/aaaa]` **al** `[dd/mm/aaaa]`» construida con dos `CampoFecha`, con botón de buscar opcional. Propaga los límites de uno al otro (al campo final se le pone como mínimo el valor inicial y al inicial como máximo el final), de modo que **el rango no se puede invertir desde la interfaz** |
| `CampoSelect` | Desplegable **propio**, ya no un `<select>` del navegador. El disparador es un `<button role="combobox">` con el aspecto exacto de `.chn-control` y la lista es un `listbox` dibujado en un portal: opciones con los colores institucionales, la elegida marcada con un visto, teclado completo (flechas, `Inicio`/`Fin`, `Intro`, `Esc` y salto por escritura) y **cuadro de búsqueda automático a partir de 8 opciones**, que filtra por etiqueta sin tildes ni mayúsculas. La API pública **no cambió**: `opciones` como `[{valor, etiqueta}]`, la opción de *placeholder* conserva el valor vacío para poder limpiar filtros y el `onChange` sigue emitiendo `{ target: { name, value } }`. Ver [8.5](#85-el-desplegable-de-opciones) |
| `CampoTextarea` | Área de texto para observaciones y motivos. Con `maxLength` muestra un contador de caracteres con `aria-live="polite"`, que avisa sin interrumpir |
| `BarraBusqueda` | Campo de búsqueda con icono y botón de limpiar. A diferencia de los `Campo*`, su `onChange` recibe **el texto ya extraído** (`onChange(valor, evento)`), para poder pasarle un *setter* de estado tal cual. Es una excepción deliberada al contrato de los demás campos y conviene tenerla presente ([13.4](#134-frontend-y-desarrollo)) |

#### Datos

| Componente | Propósito |
|---|---|
| `Tabla` | Tabla de datos con estados de carga y de vacío integrados. Columnas declarativas `[{clave, encabezado, alineacion, ancho, render, numerica}]`, `claveFila`, `esFilaActiva` (realce lateral de la fila abierta) y una columna final de `acciones`. `render` permite celdas con `Etiqueta`, botones o cualquier nodo |
| `Paginador` | Navegación entre páginas de un listado paginado por la API. Trabaja en base 0, igual que Spring Data. Muestra «Mostrando X-Y de Z registros», números con elipsis y selector de tamaño (10, 25, 50). Los extremos quedan inhabilitados de verdad |
| `PanelFiltros` | Tarjeta plegable que agrupa los filtros de un listado: cabecera con el contador «N filtros aplicados», pastillas de los criterios puestos —que se quitan una a una—, botón **Limpiar filtros**, un resumen a la derecha (el total encontrado) y una rejilla responsiva donde van los campos. Es la pieza común de los cinco listados. Ver [8.6](#86-los-filtros-en-la-interfaz) |
| `DefinicionDatos` | Lista de definición (`<dl>`) para los datos de un registro en las pantallas de detalle. 1, 2 o 3 columnas; en pantallas angostas cae siempre a una |

#### Superposiciones

| Componente | Propósito |
|---|---|
| `Modal` | Ventana modal dibujada con `createPortal` en `document.body`. Cierre con `Escape` y con clic en el fondo, bloqueo del desplazamiento del cuerpo, **foco atrapado** dentro del panel y devuelto al elemento que lo abrió |
| `DialogoConfirmacion` | Confirmación de acciones sensibles (eliminar un cliente, aprobar o rechazar, registrar un pago). Construido sobre `Modal` |
| `Notificaciones` | Contenedor de avisos flotantes (*toasts*). Se monta **una sola vez** en `AppLayout` y consume la cola del contexto. Cada aviso va **entero** del color de su tono, con el icono en un círculo, un título por omisión según el tono, botón de cerrar y una barra inferior que se vacía durante su vida; entra y sale deslizándose por la derecha. Región con `aria-live="polite"` |

#### Presentación y retroalimentación

| Componente | Propósito |
|---|---|
| `Etiqueta` | Distintivo de estado para tablas y detalles. Tonos `neutro`, `exito`, `peligro`, `advertencia`, `info`. **El color nunca es el único portador del significado**: el texto siempre se lee |
| `Tarjeta` | Panel de contenido con encabezado opcional (título, subtítulo, acciones) y modo `sinPadding` para tablas a sangre |
| `TarjetaIndicador` | Tarjeta de métrica del tablero. El título y el valor se asocian con `aria-labelledby` para que el lector de pantalla anuncie «Clientes registrados: 24» |
| `BarraProgreso` | Avance de pago de un préstamo. Expone `role="progressbar"` con `aria-valuenow`/`min`/`max` y un texto accesible, de modo que el avance no dependa solo del color |
| `Alerta` | Aviso en línea dentro de una pantalla (errores, reglas de negocio). Es la **versión suave** del lenguaje de los avisos flotantes: fondo claro del tono, franja lateral con el color fuerte y título en el color oscuro, para acompañar al *toast* sin competir con él. Los tonos `peligro` y `advertencia` usan `role="alert"` para anunciarse de inmediato; el resto usa `role="status"`, menos intrusivo |
| `EstadoVacio` | Mensaje explícito cuando una consulta no devuelve registros, con acción opcional. Distingue los dos casos que no son lo mismo: «aún no hay registros» (y ofrece crear el primero) y «ningún registro cumple el filtro» (y ofrece limpiar los filtros). Evita las tablas «vacías sin explicación» |
| `Cargando` | Indicador de carga centrado, con `altura` para reservar espacio y evitar saltos de maquetación. Se anuncia con `role="status"` y `aria-live="polite"` |
| `EncabezadoPagina` | Título, descripción, migas de pan y acciones de la pantalla. Las migas anteriores usan `<Link>` de react-router (nunca `<a href>`, que recargaría la aplicación) y la última lleva `aria-current="page"` |

#### Gráficas

Las gráficas del tablero se dibujan con **SVG propio, sin ninguna librería de gráficas**: no
se agregó ninguna dependencia al `package.json`. Todas se dibujan con el ancho real de su
contenedor en píxeles, en lugar de estirar un `viewBox`, para que el texto conserve su
tamaño en cualquier pantalla y se pueda medir antes de colocarlo.

| Componente | Propósito |
|---|---|
| `FiguraGrafica` | Marco común: `<figure>` con su título en un `figcaption` visible solo para lectores de pantalla, el conmutador **Ver tabla / Ver gráfica** (la tabla es el equivalente accesible y usa `Tabla`), el estado de carga (la gráfica anterior se conserva atenuada en lugar de parpadear) y el estado vacío con `EstadoVacio` |
| `GraficaDona` | Parte del todo en un anillo de hasta 6 segmentos, con el total al centro y una leyenda con muestra, nombre, cantidad y porcentaje (debajo del anillo en pantallas angostas). Los porcentajes se reparten con el método del mayor residuo para que **sumen exactamente 100 %**, y el mismo texto alimenta la leyenda, la tabla, el tooltip y los nombres accesibles. En el tablero: *Solicitudes por estado* |
| `GraficaColumnas` | Serie temporal en columnas verticales, pensada para 12 meses: eje Y con marcas redondas (`pasoMinimoEje` evita rótulos repetidos cuando el máximo es pequeño), rótulo del año al inicio y cada vez que cambia, y cifra escrita **solo** sobre la columna más alta y la resaltada. La columna con `resaltado` lleva el azul pleno; las demás, el mismo azul un escalón más claro. En el tablero: *Recaudación mensual*, con el mes en curso resaltado |
| `GraficaBarras` | Categorías en barras horizontales de un solo color, en el orden que decide quien la usa, con una nota bajo cada rótulo y el valor al final de cada barra. En el tablero: *Cartera por tipo de préstamo*, ordenada de mayor a menor monto aprobado |
| `MedidorSegmentado` | Una razón contra su total en una pista partida en tramos, con la cifra principal arriba y el rótulo y monto de cada tramo en los extremos. La pista es decorativa (`aria-hidden`): todo se lee como texto. En el tablero: *Recuperado del total a pagar* |
| `TooltipGrafica` | Detalle flotante de la marca activa. Se coloca encima de la marca; si no cabe, debajo, a un lado o, como último recurso, arriba del todo pegado al borde con más espacio, siempre dentro del lienzo. Es `aria-hidden`: la marca enfocada ya anuncia lo mismo |

Tres piezas de apoyo, también exportadas por el *barrel*:

- **`useMarcasGrafica`**: la interacción común de las marcas. Con el ratón la marca se activa al
  pasar encima; con el toque queda activa hasta tocar fuera; con el teclado hay **foco
  itinerante** (una sola marca en el orden de `Tab`), las flechas recorren las marcas,
  `Inicio` y `Fin` saltan a los extremos y `Escape` oculta el tooltip sin perder el foco.
- **`useAnchoContenedor`**: el ancho real del contenedor, al día con `ResizeObserver`.
- **`graficas.js`** (interno): medición de texto, escalas redondas (`escalaRedonda`), reparto de
  porcentajes (`repartirPorcentajes`) y trazos de columnas y barras con el extremo redondeado.

El tablero ([`Tablero.jsx`](../frontend/src/ui/paginas/Tablero.jsx)) solo prepara las series y
reparte el espacio: los datos llegan de `useResumen`, que vale `null` hasta la primera respuesta
del backend. Por eso, mientras carga, se ve «Cargando indicadores...» y, si la consulta falla,
solo la alerta con **Reintentar**: el tablero nunca presenta ceros inventados como si fueran
datos. El porcentaje del medidor es **recuperado ÷ (recuperado + saldo pendiente)**, es decir,
lo cobrado contra el total a pagar con intereses: dividir entre el capital aprobado lo
sobrestimaría, porque lo cobrado incluye intereses.

Marco de la aplicación, en `src/ui/disenio/`: `AppLayout` (barra lateral, barra superior y
`<Outlet/>`), `BarraLateral` (navegación filtrada por permisos), `BarraSuperior` (usuario,
rol y cierre de sesión), `RutaProtegida` (guarda de acceso) y `usarSesion.js` (acceso al
contexto de autenticación).

Las 13 pantallas son `Login`, `Tablero`, `Clientes`, `ClienteFormulario`, `Solicitudes`,
`SolicitudNueva`, `SolicitudDetalle`, `Prestamos`, `PrestamoDetalle`, `Pagos`,
`PagoFormulario`, `Auditoria` y `NoEncontrado`. `ClienteFormulario` y `PagoFormulario` no
tienen ruta propia: son formularios que se abren como modal desde `Clientes`, `Pagos` y
`PrestamoDetalle`, de modo que el usuario no pierde el contexto del listado.

### 8.3 Sistema de diseño: tokens, color y movimiento

No hay librería de UI ni hoja de estilos de terceros. Lo que hace que 31 componentes y 13
pantallas se vean como un solo producto son tres piezas: un archivo de variables, un
catálogo de acciones y un catálogo de animaciones.

#### Los tokens (`src/estilos/tokens.css`)

**106 variables** en `:root`, único lugar del proyecto donde se escribe un color. Ningún
componente pone un literal: un cambio de imagen institucional se hace en este archivo. Las
59 originales siguen intactas —no se renombró ni se eliminó ninguna, porque las consumen
todos los componentes—; el rediseño añadió 36 en un bloque aparte y las gráficas del tablero,
10 más en un tercer bloque. Las 46 añadidas se agrupan así:

| Grupo | Variables | Para qué sirve |
|---|---|---|
| Variantes del color institucional | `--color-primario-hover`, `--color-primario-suave` | El oscurecido al pasar el ratón y un fondo casi blanco para realces muy tenues |
| Tríos de cada tono de estado | `--color-{exito,peligro,advertencia,info}-oscuro` y `-borde` | Junto con el `-claro` que ya existía, dan el trío **fondo claro + borde + texto oscuro** con el que se arma cualquier aviso suave. Que el trío esté completo es lo que evita inventar un color a ojo |
| Familia neutra | `--color-neutro`, `--color-neutro-oscuro`, `--color-neutro-borde` | Completa la familia del gris, que solo tenía el `-claro`. Es el color de las acciones sin consecuencia |
| Degradados (7) | `--degradado-primario`, `-lateral`, `-exito`, `-peligro`, `-advertencia`, `-info`, `-superficie` | Dan volumen a los botones sólidos, a la barra lateral y a los avisos flotantes, que van coloreados por completo |
| Radios y espaciado | `--radio-xl`, `--esp-9`, `--esp-10` | El peldaño más alto de la escala de radios —reservado a las capas flotantes grandes, hoy la ventana modal— y los dos pasos altos de la escala de 4 px |
| Sombras | `--sombra-xs`, `--sombra-xl`, y `--sombra-color-{exito,peligro,advertencia,info,primario}` | `xs` apenas separa del fondo y `xl` es para lo que flota (calendario, modales). Las de color tiñen la sombra con el color de la acción y solo aparecen al pasar el ratón y en los avisos |
| Foco | `--anillo-foco`, `--anillo-foco-peligro` | **Un solo estilo de foco para todo el sistema**, para que el teclado se vea igual en cualquier control. La variante roja existe porque el anillo azul sobre un botón rojo no contrasta |
| Movimiento | `--transicion-lenta`, `--duracion-lenta`, `--curva-suave`, `--curva-rebote` | La duración y las dos curvas del sistema: una desacelera al final (entradas) y otra sobrepasa un poco el destino (confirmaciones). `--transicion-lenta` ya trae su curva y sirve para `transition`; en `animation` se combina `--duracion-lenta` con una curva, porque la forma abreviada no admite dos funciones de tiempo y el navegador descartaría la animación completa |
| Capas | `--capa-popover` | El calendario y los menús van sobre la barra superior, pero debajo de los modales y de las notificaciones. El `z-index` está centralizado para que nadie invente un `9999` |
| Gráficas (10) | `--color-grafica-{serie,serie-suave,pista,cuadricula,eje,realce}`, `--color-grafica-estado-{exito,advertencia,peligro}`, `--radio-grafica` | Colores de las marcas de las gráficas, validados con el verificador de paletas. La serie es el azul institucional (8.4:1 sobre blanco) y la serie suave y la pista, el mismo azul en escalones más claros. Los tres de estado conservan el significado verde, ámbar y rojo de la dona de solicitudes, pero con luminosidades separadas para que los tres segmentos vecinos se distingan también con daltonismo (ΔE 10.9, meta 8). `--radio-grafica` (4 px) redondea el extremo de barras y columnas |

La escala de radios completa es `--radio-sm` 8 px, `--radio-md` 14 px, `--radio-lg` 20 px,
`--radio-xl` 28 px y `--radio-completo` 999 px. El peldaño se elige por el **tamaño** del
elemento, no por su tipo, para que la curvatura se perciba igual de suave en una celda de
28 px que en un panel de 600 px: `sm` para lo pequeño anidado dentro de otra cosa (celdas del
calendario, pulgar de la barra de desplazamiento), `md` para los controles (campos, botones,
desplegables), `lg` para las superficies (tarjetas, tablas, avisos, panel de filtros), `xl`
para las capas flotantes y `completo` para píldoras y círculos. Ningún componente escribe un
radio literal, así que redondear más o menos el sistema entero es cambiar estos cinco valores.
La única excepción es `border-radius: 0` dentro del `@media print` del comprobante de pago:
el papel no tiene esquinas redondeadas. Las gráficas quedan fuera de esta escala a
propósito: redondean marcas de datos, no superficies, y usan su propio `--radio-grafica`.
Las bandas de realce y las zonas interactivas de columnas y barras llevan el radio como
atributo `rx` del SVG (6 px en columnas y 8 px en barras), escrito en el propio componente.

#### El color dice la consecuencia de la acción

`src/dominio/acciones.js` asocia cada acción del sistema con su variante de color, su icono
y su etiqueta por omisión. El criterio no es estético: **el color comunica qué le va a pasar
al registro**.

| Color | Significado | Acciones del catálogo |
|---|---|---|
| Verde (`exito`) | Resuelve a favor del cliente | `aprobar`, `pagar` |
| Rojo (`peligro`) | Destruye o niega | `eliminar`, `rechazar` |
| Ámbar (`advertencia`) | Modifica lo que ya existe | `editar` |
| Azul (`info`) | Consulta sin cambiar nada | `ver`, `simular` |
| Azul institucional (`primario`) | Acción principal de la pantalla o del formulario | `guardar`, `crear`, `buscar` |
| Gris azulado (`secundario`) | Apoyo sobre el listado | `filtrar`, `descargar`, `refrescar` |
| Gris (`neutro`) | Sin consecuencia: se puede pulsar sin miedo | `cancelar`, `imprimir`, `limpiar` |
| Sin relleno (`texto`) | Navegación | `volver` |

**El verde está racionado a propósito.** Solo lo llevan `aprobar` y `pagar`, que son los dos
momentos en que el sistema cierra algo a favor del cliente: se resuelve un crédito o entra
dinero. Si además se pintara de verde cada `guardar`, el color dejaría de informar y el
analista no distinguiría de un vistazo un alta rutinaria de la aprobación de un crédito.
Enviar un formulario es la **acción principal de ese formulario**, no una resolución a favor,
así que va en el azul institucional: el color de la marca y el que el usuario aprende a
buscar como «el botón que continúa».

### Peso visual: el segundo eje

La variante decide el **matiz**; la prop `tono` (`solido`, `suave`, `contorno`) decide el
**peso**. Cuando una pantalla no la pasa, la deduce el propio `Boton` a partir de la variante
(`TONO_POR_VARIANTE`): `neutro` y `secundario` nacen con **contorno** en lugar de relleno.

El motivo es de jerarquía, no de gusto: un *Cancelar* relleno de gris macizo pesa lo mismo que
el botón que sí cierra el trámite, y el usuario pierde la referencia de dónde continuar. Con
contorno, la acción sigue siendo legible y pulsable pero cede el protagonismo. Las pantallas
conservan la última palabra: los iconos de acción de las filas, por ejemplo, pasan
`tono="suave"` para colorearse sin convertirse en tres bloques macizos por fila.

La función `accion('eliminar')` devuelve **una copia** del descriptor, de modo que una
pantalla no puede alterar el catálogo para las demás, y una clave desconocida devuelve un
descriptor neutro en lugar de romper el render. El resultado se expande sobre el botón:
`<Boton {...accion('eliminar')} onClick={…} />`.

El valor de tenerlo centralizado es que el botón de eliminar es rojo en clientes, en
préstamos y en pagos **porque los tres leen de aquí**, no porque cada pantalla lo haya
elegido por su cuenta. En las acciones de fila de las tablas el botón va en `tono="suave"` y
`soloIcono`, así que el icono queda coloreado —ver azul, editar ámbar, eliminar rojo— sin
que tres botones sólidos compitan por la atención dentro de una celda.

> El color **nunca es el único portador del significado**. Todo botón de solo icono lleva
> `aria-label` y `title`, y `Etiqueta` siempre muestra su texto ([8.9](#89-decisiones-de-accesibilidad)).

#### Las animaciones (`src/estilos/animaciones.css`)

Un único catálogo de **15 keyframes** con el prefijo `chn-`, más las clases utilitarias que
los aplican. Todas comparten la curva de los tokens; las cuatro de las gráficas duran algo más
(480 ms, y 640 ms el anillo), porque la marca recorre toda su longitud y más rápido el
crecimiento se percibiría como un salto:

| Keyframe | Dónde se usa |
|---|---|
| `chn-aparecer` | Cambio de contenido en el mismo sitio, sin desplazamiento |
| `chn-subir` | Entrada general dentro de la página |
| `chn-bajar` | Paneles anclados a un control (el calendario, la lista de un desplegable, el cuerpo de los filtros) |
| `chn-entrar-derecha` / `chn-salir-derecha` | Entrada y salida de los avisos flotantes |
| `chn-escalar` | Superposiciones (modales) |
| `chn-girar` | Indicadores de carga |
| `chn-pulso` | Latido de un solo ciclo en una confirmación |
| `chn-brillo` | Barrido del esqueleto de carga |
| `chn-vaciar-barra` | La barra de vida del aviso flotante |
| `chn-marcar` | Dibujo del trazo del *check* |
| `chn-crecer-vertical` | Las columnas de `GraficaColumnas`, que crecen desde su línea base |
| `chn-crecer-horizontal` | Las barras de `GraficaBarras`, que crecen desde su borde izquierdo |
| `chn-dibujar-trazo` | El anillo de `GraficaDona`, que se recorre desde las 12 (sobre una máscara con `pathLength="100"`) |
| `chn-revelar` | El `MedidorSegmentado`, que se descubre de izquierda a derecha |

A ellas se suma la **entrada escalonada** `chn-anim-escalonado`, que combinada con una clase
de entrada hace que las filas de una tabla aparezcan una tras otra: el retardo es
`índice × 40 ms` y el índice se topa en 10, porque 400 ms es el límite a partir del cual la
lista deja de sentirse fluida y empieza a sentirse lenta.

> **El bloque final es un requisito de accesibilidad, no un adorno.** El archivo termina —y
> la regla de la casa es que nada se añade después— con
> `@media (prefers-reduced-motion: reduce)`, que anula **todas** las animaciones y
> transiciones del documento (`*`, `*::before`, `*::after`) y muestra el contenido
> directamente en su estado final. Quien tenga activada esa preferencia en su sistema
> operativo —por trastorno vestibular, por migraña o simplemente por preferencia— ve la
> interfaz completa, sin movimiento y sin perder ninguna función. Es la pauta WCAG 2.3.3
> aplicada en un solo sitio, en vez de recordarlo componente por componente.

### 8.4 El selector de fechas

`CampoFecha` es un componente propio. **No** envuelve al `<input type="date">` del navegador,
que es lo que usaba antes.

#### Por qué no se usa el control nativo

| Motivo | Detalle |
|---|---|
| El formato lo decidía el equipo del usuario | El nativo muestra `dd/mm/aaaa`, `mm/dd/aaaa` o `aaaa-mm-dd` según el idioma y la región configurados en el sistema operativo, no según la aplicación. En una agencia con equipos configurados de distinta forma, el mismo formulario se ve distinto, y `03/04/2026` puede leerse como 3 de abril o como 4 de marzo |
| El aspecto no se puede controlar | Chrome, Firefox y Safari dibujan cada uno su propio calendario y su propio icono, y el CSS apenas los alcanza. Era el primero de los dos controles del sistema que no respetaban los tokens; el otro era la lista desplegada de un `<select>`, sustituida por el mismo motivo ([8.5](#85-el-desplegable-de-opciones)) |
| El panel que pidió el usuario no existe en el nativo | Tres columnas simultáneas —años desplazables, meses en rejilla de 2×6, días con la semana empezando en lunes— para llegar a una fecha de nacimiento de 1965 sin pulsar «mes anterior» cuarenta veces |

#### Cómo está construido

- **Caja de texto con máscara.** Se escribe con el teclado numérico y las barras se ponen
  solas: `1506` → `15/06`, `15061992` → `15/06/1992`. La barra aparece solo cuando hay algo
  detrás, para que borrar con retroceso no se quede atascado reponiendo el separador. Se
  acepta además un ISO pegado (`1992-06-15`) y ocho dígitos seguidos. Al perder el foco, un
  texto incompleto o imposible se descarta y vuelve el último valor válido.
- **Panel de tres columnas.** Años de **1930 al año en curso más 10** en una lista
  desplazable que se centra sola en el año activo; meses abreviados a tres letras en una
  rejilla de 2×6; días con los encabezados `lu ma mi ju vi sa do`. El año y el mes activos
  van resaltados y el día elegido en un círculo. **Elegir año o mes solo reubica la vista;
  únicamente elegir un día cierra el panel**, que es lo que permite navegar sin sobresaltos.
- **Límites reales.** Con `min`/`max`, un día fuera del intervalo se inhabilita; un mes o un
  año se inhabilitan solo si **ninguno** de sus días cae dentro.
- **Teclado completo.** `Flecha abajo` o `Enter` abren el panel; flechas izquierda/derecha
  mueven un día y arriba/abajo una semana; `RePág`/`AvPág` cambian de mes; `Inicio` y `Fin`
  van al primero y al último día del mes; `Enter` o la barra espaciadora eligen; `Escape`
  cierra y devuelve el foco a la caja de texto. El `Escape` **detiene la propagación**, de
  modo que cerrar el calendario dentro de un modal no cierra también el modal.
- **Se dibuja en un portal.** El panel se monta con `createPortal` en `document.body` y se
  posiciona a mano sobre el campo. Dentro de un modal o de una tarjeta con `overflow`, un
  panel posicionado en el flujo normal quedaría recortado; el portal lo saca de ese
  contenedor. La posición se recalcula al abrir y en cada `scroll` o cambio de tamaño de la
  ventana: si no cabe debajo se coloca encima, y si se sale por la derecha se alinea al
  borde derecho del campo. Como el campo y su panel viven en ramas distintas del DOM, la
  salida de foco se comprueba contra el elemento activo real y no con `relatedTarget`.

#### El contrato de valor

`CampoFecha` **sigue emitiendo ISO `aaaa-MM-dd`**, con la misma forma de evento que los
demás campos —`onChange({ target: { name, value } })`—, que es lo que espera
`useFormulario`. Por eso el rediseño del control **no cambió ni un endpoint ni un DTO**: el
formato `dd/mm/aaaa` es exclusivamente de presentación y nunca sale del navegador.

Toda la conversión vive en `src/dominio/fechas.js`, un módulo puro y sin React que es la
única fuente de verdad del formato: máscara, ISO ↔ visible, validación de que la fecha
**existe de verdad** (el 31 de febrero se rechaza, y el 29 de febrero depende del año),
rejilla del mes empezando en lunes y aritmética de días y meses. Dos decisiones suyas
importan:

- **No se usa `Date` para representar una fecha**, solo para calcular el día de la semana.
  Un día sin hora interpretado como UTC se muestra un día antes en Guatemala (UTC−6), y ese
  error ya costó caro una vez ([13.2](#132-persistencia-y-base-de-datos)). Lo demás son
  números: año, mes (0–11) y día.
- **Ninguna función lanza.** Ante `null`, `undefined` o basura devuelven `null`, `''` o
  `false` según el caso; nunca `NaN` ni una excepción que tumbe el render.

`CampoRangoFechas` monta dos `CampoFecha` con la palabra «al» en medio y cruza sus límites,
de modo que **desde la interfaz el rango no se puede invertir**. La validación del backend
([3.5-j](#35-decisiones-de-diseño-justificación-y-contrapartida)) sigue ahí de todas formas:
la interfaz evita el error, no lo sustituye.

### 8.5 El desplegable de opciones

`CampoSelect` es el segundo control propio del sistema. **No** envuelve al `<select>` del
navegador, que es lo que usaba antes.

#### Por qué se abandonó el control nativo

| Motivo | Detalle |
|---|---|
| La lista desplegada la dibuja el sistema operativo | El control **cerrado** siempre fue nuestro: un `.chn-control` con su borde, su `--radio-md` y su chevron. Pero al abrirlo aparecía la lista del sistema —fondo gris, tipografía ajena, ningún color institucional, sin la marca de la opción elegida—. No es una carencia de CSS nuestro: **el interior de un `<select>` no admite estilo**; sus `<option>` los pinta el sistema y ninguna regla los alcanza |
| Era el punto más visible de la interfaz | El desplegable es la herramienta de filtrado más usada: hay **catorce**, trece repartidos en siete pantallas y uno más en el selector de *Registros por página* del `Paginador`. El único trozo del sistema que se veía «predeterminado» era justo el que el usuario abre decenas de veces al día, y así lo pidió: «Ponle un diseño a esto, no quiero que esté predeterminado» |
| Al nativo le faltaba la búsqueda | El catálogo de clientes o de préstamos puede traer cientos de filas, y en un `<select>` lo único que ayuda a llegar a una es el salto por la primera letra |

Un desplegable con la lista sin estilo no es un defecto estético aislado: rompe la promesa del
resto del sistema, que es que 31 componentes y 13 pantallas se vean como un solo producto
([8.3](#83-sistema-de-diseño-tokens-color-y-movimiento)). El `<select>` nativo no deja
elegir: o se acepta su lista, o se sustituye entera por un `listbox` propio.

#### La contrapartida, sin adornos

Sustituir el nativo **no es gratis**. Un `<select>` trae de fábrica el teclado, el manejo del
foco, la semántica para el lector de pantalla, el desplazamiento y el comportamiento táctil.
Un `listbox` propio obliga a reimplementarlo todo a mano, y todo eso es código que puede
tener defectos donde antes no había código. Las tres decisiones que acotan ese riesgo son:

- **No se inventa el comportamiento.** Se sigue el *patrón combobox* de la guía ARIA: roles
  `combobox`/`listbox`/`option`, `aria-expanded`, `aria-haspopup`, `aria-controls`,
  `aria-selected` y `aria-activedescendant`, con el juego de teclas que ese patrón define. Lo
  que el usuario aprendió en cualquier otro desplegable de la web sigue funcionando aquí.
- **No se inventa el mecanismo.** El panel se dibuja con `createPortal` en `document.body` y
  se posiciona a mano, exactamente como el calendario de `CampoFecha`
  ([8.4](#84-el-selector-de-fechas)): el mismo motivo (que un `overflow` de un modal o de una
  tarjeta no lo recorte), la misma capa `--capa-popover`, el mismo recálculo en `scroll` y
  `resize` y la misma comprobación de la salida de foco contra el elemento activo real.
- **En el teléfono se pierde el selector nativo.** Es la pérdida más clara y conviene decirla:
  en un móvil, un `<select>` abre la rueda o la lista a pantalla completa del sistema, que es
  cómoda con el pulgar y a la que el usuario está acostumbrado. Aquí abre un panel dentro de
  la página. A cambio, en el móvil se gana el buscador, que en una lista de doscientos
  clientes vale más que la rueda, y las filas llevan relleno `--esp-2 --esp-3` (8 px y 12 px),
  de modo que el área pulsable no es solo la altura del texto.

**Desviación conocida del patrón.** Cuando aparece el cuadro de búsqueda, este vive *dentro*
del elemento con `role="listbox"`; el patrón estricto lo colocaría fuera, como un `textbox`
hermano. Se dejó dentro porque es lo que mantiene el panel como una sola superficie flotante
—una sola caja, una sola sombra, un solo portal— y porque el foco y la opción activa se
anuncian de todas formas con `aria-activedescendant` sobre el cuadro de búsqueda. Queda
anotado como lo que es: una desviación deliberada, no un olvido.

#### Cómo está construido

- **El disparador queda en su sitio.** Es un `<button type="button" role="combobox">` con las
  clases `chn-select chn-control`, que permanece en el formulario (no en el portal) y recibe
  el `className` de la pantalla y el resto de props. Con el panel cerrado **es indistinguible
  del control anterior**: misma altura, mismo borde, mismo `--radio-md` y el mismo chevron a
  la derecha. Lo único que cambió es que ese chevron es un `Icono` de verdad en lugar de un
  fondo pintado, así que ocupa el hueco que antes reservaba el relleno derecho; con el panel
  abierto gira 180° y toma el color de marca. El `type="button"` es obligatorio y no se puede
  sobrescribir desde fuera: hay `CampoSelect` dentro de formularios reales y un botón sin tipo
  los enviaría al pulsarlo.
- **El panel vive en un portal.** `position: fixed`, con las coordenadas que escribe el
  componente. Nunca es más estrecho que el disparador, crece si una etiqueta es larga hasta
  `min(480px, 100vw − 2 × --esp-2)` y recorta con elipsis; el alto máximo es de 320 px con su
  propio desplazamiento. Si no cabe debajo se voltea encima —clase
  `chn-select__panel--arriba`, que invierte la animación de entrada—, y si se sale por la
  derecha se alinea a ese borde. La posición se recalcula al abrir, en cada `scroll` (en
  captura y pasivo) y en cada `resize`, y además con un `ResizeObserver` sobre el propio
  panel, porque filtrar cambia el número de filas, y con él el alto y la esquina de la que
  cuelga. Al abrir, la opción seleccionada se desplaza a la vista con `scrollIntoView` y
  `block: 'nearest'`.
- **El buscador aparece solo.** A partir de **8 opciones** (`MINIMO_PARA_BUSCADOR`, sin contar
  la fila del *placeholder*) el panel incluye un cuadro de búsqueda pegado arriba, con su lupa
  y el aspecto de `.chn-control`. Filtra por etiqueta normalizando con `normalize('NFD')` y
  quitando los diacríticos, de modo que `jose` encuentra «José» y `MARIA` encuentra «María»,
  igual que la búsqueda de los listados. Por debajo de ese umbral estorba más que ayuda. La
  prop `buscable` lo fuerza o lo desactiva y `etiquetaVacio` cambia el mensaje de «ningún
  resultado», pero **ninguna pantalla pasa hoy ninguna de las dos**: decide el umbral. En la
  práctica lo llevan los desplegables de cliente y de préstamo —que se llenan con el
  catálogo— y el de **Acción** de la bitácora, que tiene doce; los de estado, tipo, entidad y
  tamaño de página se quedan sin él.
- **Teclado completo.** Con el panel cerrado, `↓`, `↑`, `Intro`, la barra espaciadora y
  `Alt+↓` lo abren, y teclear una letra lo abre saltando a la primera etiqueta que empieza por
  ella; `Inicio` y `Fin` no hacen nada. Con el panel abierto, `↓` y `↑` mueven la opción
  **activa** con envoltura en los extremos, `Inicio` y `Fin` van a la primera y a la última,
  `Intro` elige y cierra, la barra espaciadora también —salvo con el foco en el buscador,
  donde escribe un espacio—, `Esc` cierra sin cambiar nada y `Tab` cierra dejando pasar al
  control siguiente. Sin buscador, escribir salta a la etiqueta que empieza por lo tecleado,
  con un margen de 500 ms para encadenar varias letras como una sola búsqueda.
- **Activa, seleccionada y bajo el ratón son tres cosas distintas.** La **activa** es la que
  mueve el teclado: fondo `--color-primario-suave` más un realce interior de 2 px, que es lo
  que la separa del simple *hover*, que solo tiene el fondo. La **seleccionada** es la que
  tiene el valor: texto de marca, `--peso-semi` y un visto a la derecha. Si una fila es las
  dos cosas los dos realces se suman sin competir, porque uno es fondo y el otro contorno.
- **Cierre y foco.** Se cierra al elegir, con `Esc`, con `Tab`, con clic fuera o al perder el
  foco; al elegir y con `Esc` el foco **vuelve siempre al disparador**. Las filas no son
  enfocables y su `mousedown` hace `preventDefault`, para que el clic no mueva el foco y
  cierre el panel antes de que la elección se cuente. Como el disparador y el panel viven en
  ramas distintas del DOM, la salida de foco se comprueba contra `document.activeElement` en
  un temporizador de 0 ms y no con `relatedTarget`, que apuntaría fuera del componente.
- **`Escape` detiene la propagación.** Hay un `CampoSelect` dentro del modal de pagos
  (`PagoFormulario`): cerrar su lista no debe cerrar el modal. Es la misma decisión que en el
  calendario.
- **Repetir la selección no emite nada.** Elegir la opción que ya estaba puesta no llama al
  `onChange`, porque cada cambio de filtro dispara una consulta al backend y recargar el
  listado para dejarlo igual es trabajo regalado.

#### La API pública no cambió

`valor` sigue siendo una cadena —un identificador numérico se normaliza a cadena en un solo
sitio—, `opciones` sigue siendo `[{valor, etiqueta}]`, el `placeholder` sigue siendo la
primera fila seleccionable con valor vacío (la que limpia un filtro: «Todos los clientes»,
«Todos los estados») y el `onChange` sigue recibiendo un objeto con **forma de evento**:

```js
onChange({ target: { name: nombre, value: valorElegido } })
```

Eso es lo que leen `useFormulario` y los filtros de los listados, que esperan
`evento.target.name` y `evento.target.value`. Por eso los catorce usos no se tocaron: el
`Paginador`, que necesita un número, sigue convirtiéndolo a la salida
(`Number(evento.target.value)`), y `usarContextoCampo()` sigue aportando `aria-describedby` y
`aria-invalid` desde el `CampoFormulario`, igual que en los demás campos.

#### El contrato de DOM

Las clases y los atributos del control **son contrato**, no detalle de implementación: son la
única forma que tiene una prueba automatizada —y el generador de capturas del manual de
usuario— de operar el desplegable y de comprobar qué quedó elegido. Un `<select>` ofrecía
`selectOption()` y la propiedad `value`; un `listbox` propio tiene que ofrecer algo
equivalente, y lo hace con marcas explícitas y estables.

| Elemento | Marcas | Para qué están |
|---|---|---|
| Disparador | `.chn-select.chn-control` (más `.chn-control--error`), `role="combobox"`, `aria-expanded`, `aria-haspopup="listbox"`, `aria-controls="<id>-lista"` y **`data-valor`** con el valor actual | `data-valor` es la lectura de la selección **sin depender del texto visible**, que cambia con la etiqueta, el formato o el idioma. El `data-captura` que pase la pantalla llega también aquí, porque el disparador es lo que se señala en una captura |
| Panel | `.chn-select__panel` (más `--arriba` si está volteado), `id="<id>-lista"`, `role="listbox"` | Al vivir en el portal no es descendiente del disparador: se localiza siguiendo su `aria-controls`, y así nunca se confunde con el panel de otro campo |
| Opción | `.chn-select__opcion` (más `--vacia`, `--elegida`, `--activa`), `role="option"`, `aria-selected`, `data-valor` e `id="<id>-opcion-N"` | Se pulsa por `data-valor`, que es el valor que viajará al servidor. La `N` del identificador es el índice en la lista **completa**, no en la filtrada, para que no cambie mientras se escribe en el buscador |
| Fila del *placeholder* | Una opción normal con `data-valor=""` y la clase `--vacia` | Permite distinguir «Todos los clientes» de una opción real: es la fila que se salta quien quiere elegir «la primera opción de verdad». Lo que siempre marca a esas filas es el `data-valor` vacío, no la clase: una pantalla puede meter su propio «Todos los estados» como una opción más de `opciones` —lo hace `Solicitudes`— y entonces la fila no lleva `--vacia` |
| Buscador | `.chn-control.chn-select__buscador` y, si la pantalla pasó `data-captura="x"`, `data-captura="x-buscar"` | El sufijo evita que el cuadro de búsqueda y el disparador compartan ancla |
| Sin coincidencias | `.chn-select__vacio` con el texto de `etiquetaVacio` | Comprobar que una búsqueda sin resultados lo dice, en vez de dejar el panel vacío |

Cambiar cualquiera de esas marcas rompe el guion de capturas
([11.6](#116-el-guion-de-capturas-y-el-desplegable-propio)), que es exactamente el aviso que
se quería tener.

#### Estilo

Todo sale de `estilos/tokens.css`: **ni un color literal** en `CampoSelect.css`. El panel usa
`--color-superficie` sobre borde `--color-borde`, `--radio-lg` (el mismo peldaño que el
calendario, porque es una superficie flotante del mismo tamaño), `--sombra-xl`, relleno
`--esp-1` y la animación `chn-bajar` con `--transicion-lenta`. Las opciones usan `--radio-sm`,
`--texto-md` y `--color-primario-suave` al pasar el ratón; la fila del *placeholder* lleva
solo `--color-texto-suave`, sin cursiva, para no romper la sobriedad institucional. El
movimiento reducido no necesita nada especial: lo anula el bloque global de
`animaciones.css`.

### 8.6 Los filtros en la interfaz

Los cinco listados filtran igual porque comparten dos piezas: el componente `PanelFiltros`
y el hook `useFiltros`.

#### `useFiltros`

Centraliza el estado de los criterios de un listado. Recibe los valores iniciales —los
mismos nombres que acepta el endpoint, con su valor neutro— y tres opciones:
`clavesConRetardo`, `rangos` y `describir`.

Mantiene **dos estados**: `valores`, que es lo que el usuario ve en los controles, y
`aplicados`, que es lo que se manda al servidor. Solo difieren durante el retardo de
escritura, y quien consume el hook envía siempre `aplicados`, de modo que el listado no se
recarga mientras se sigue tecleando.

| Lo que expone | Para qué |
|---|---|
| `valores`, `aplicados` | Lo que se pinta y lo que se consulta |
| `establecerFiltro`, `establecerFiltros` | Cambiar uno o varios criterios (los dos extremos de un rango a la vez) |
| `quitarFiltro`, `limpiarFiltros` | Devolver uno o todos a su valor neutro |
| `filtrosActivos` | El número que muestra el contador de la cabecera |
| `chips` | Las pastillas listas para `PanelFiltros`, cada una con su acción de quitar |

**El retardo de 350 ms** se aplica a los criterios que el usuario escribe a mano: el texto
libre y los extremos numéricos de los rangos (`montoMinimo`, `saldoMaximo`, `plazoMinimo`,
`usuarioRegistro`…). Los que vienen de un desplegable o del selector de fechas se aplican de
inmediato, porque son decisiones ya terminadas. El razonamiento completo está en
[3.5-l](#35-decisiones-de-diseño-justificación-y-contrapartida).

**Un rango es un solo filtro.** Para el usuario, «fecha de pago entre el 1 y el 15» es un
criterio, aunque la API reciba dos parámetros. Por eso el hook agrupa cada par
`desde`/`hasta` declarado en `rangos`: cuenta **uno** en el contador y se muestra en **una**
pastilla, que al quitarse borra los dos extremos de golpe. Un rango con un solo extremo
puesto también cuenta uno, y su texto lo dice («desde el 01/09/2026»).

**Cualquier cambio devuelve el listado a la primera página.** Cada hook de listado observa
`filtros.aplicados` y reinicia `pagina` a 0: la página 5 del resultado anterior no significa
nada para el criterio nuevo, y dejarla puesta produce la pantalla vacía más desconcertante
posible. La carga lleva además una bandera de cancelación, para que una respuesta lenta no
sobrescriba a otra posterior.

#### `PanelFiltros`

Tarjeta plegable que arranca **cerrada**, salvo que la pantalla se abra con filtros ya
puestos —por ejemplo al llegar desde otra pantalla con un criterio en la URL—, en cuyo caso
arranca abierta. Es un cálculo de una sola vez al montar: a partir de ahí manda el usuario.

- La cabecera es un `<button>` con `aria-expanded` y `aria-controls`; el cuerpo cerrado
  queda fuera del orden de tabulación.
- El distintivo de la cabecera dice «1 filtro aplicado» o «N filtros aplicados».
- Las **pastillas** siguen visibles aunque el cuerpo esté cerrado, para que nunca haya un
  filtro invisible alterando lo que se ve; cada una lleva su botón de quitar con
  `aria-label` propio.
- A la derecha de la cabecera va el `resumen`: el total de registros encontrados, con
  `aria-live` para que el cambio se anuncie.
- Los campos se reparten en una rejilla responsiva, y el botón **Limpiar filtros** aparece
  solo cuando hay algo que limpiar.

#### Cuando el filtro no arroja resultados

El `EstadoVacio` distingue los dos casos, porque no son el mismo problema: «Aún no hay
solicitudes» invita a crear la primera, mientras que «Sin resultados» explica que ningún
registro cumple los criterios y ofrece el botón de limpiarlos. Una tabla vacía sin
explicación deja al usuario sin saber si el sistema falló, si no tiene permiso o si
simplemente buscó mal.

#### Atajos de la pantalla de solicitudes

Los contadores por estado que aparecen sobre la tabla («En proceso: 4», «Aprobada: 4»…) son
**pulsables**: aplican ese estado como filtro, y volver a pulsarlos lo quitan. Cada conteo
se pide con `tamano=1` —solo interesa `totalElementos`— y respeta **el resto** de criterios
puestos, de modo que los números describen el subconjunto que el usuario está mirando y no
totales globales. Si una de esas consultas falla se muestra «—» sin molestar con un aviso,
porque el listado principal ya informa de sus propios errores.

### 8.7 Cómo se separan dominio, aplicación e infraestructura

#### `src/dominio/` — lógica pura

Seis módulos sin una sola importación de React, axios o del DOM:

- **`catalogos.js`** traduce los enums del backend a etiquetas en español y tonos visuales
  (`etiquetaEstadoSolicitud`, `tonoEstadoPrestamo`, …), publica las opciones para los
  `CampoSelect` y define `PERMISOS`, `PERMISOS_POR_ROL` y `LIMITES`. Es el único sitio donde
  aparece la cadena `EN_PROCESO`, así que si el backend renombra un estado hay un solo
  archivo que tocar.
- **`formato.js`** concentra la presentación de datos: `moneda` (quetzales),
  `porcentaje`, `fecha`, `fechaHora`, `plazo`, y el `SIN_DATO` que se muestra cuando un
  campo viene nulo. Ninguna pantalla formatea números por su cuenta.
- **`validaciones.js`** son 12 validadores con la firma `(valor, valores) => mensaje|null`,
  más `validarFormulario(valores, reglas)`. Los rangos **replican los del backend**
  tomándolos de `LIMITES`.
- **`amortizacion.js`** replica la fórmula del sistema francés para que el simulador muestre
  la cuota al instante mientras el usuario mueve los campos, sin esperar la respuesta de la
  API. El cálculo autoritativo sigue siendo el del servidor.
- **`fechas.js`** es la única fuente de verdad del formato de fecha: máscara, conversión
  ISO ↔ visible, validación de que la fecha existe, rejilla del mes empezando en lunes y
  aritmética de días y meses. Lo consumen `CampoFecha` y `CampoRangoFechas`
  ([8.4](#84-el-selector-de-fechas)).
- **`acciones.js`** asocia cada acción con su color y su icono, para que el mismo verbo
  signifique lo mismo en todas las pantallas ([8.3](#83-sistema-de-diseño-tokens-color-y-movimiento)).

Que `fechas.js` y `acciones.js` vivan en `dominio/` y no en `ui/` no es casual: son reglas
—qué es una fecha válida, qué consecuencia tiene una acción— y no piezas de presentación.
Se prueban con `new` y una llamada, sin montar ningún componente.

#### `src/aplicacion/` — casos de uso de la interfaz

Los ocho hooks siguen todos la misma forma: exponen `datos`, `cargando`, `error` y las
acciones del caso de uso, y son el único lugar donde se combina el estado de React con las
llamadas a los adaptadores. Una pantalla no llama nunca a un adaptador directamente.

- `useClientes`, `useSolicitudes`, `usePrestamos`, `usePagos`, `useResumen`, `useAuditoria`
  cubren un caso de uso cada uno. Los cinco que listan declaran sus criterios en una
  constante (`FILTROS_CLIENTES`, `FILTROS_SOLICITUDES`, …) cuyas claves son **exactamente**
  las que acepta el endpoint, de modo que el objeto de filtros se envía tal cual.
  `useResumen` tiene una particularidad deliberada: su `resumen` vale
  `null` hasta la primera respuesta y, si una consulta falla, conserva el último resumen bueno
  y deja el error aparte, para que la pantalla no confunda un fallo con una cartera en cero.
  Completa con `RESUMEN_VACIO` los campos que falten y asegura que `carteraPorTipo` y
  `recaudacionMensual` sean siempre listas.
- `useFiltros` es transversal: centraliza el estado de los filtros de los cinco listados, el
  retardo de escritura, el agrupado de rangos y las pastillas
  ([8.6](#86-los-filtros-en-la-interfaz)).
- `useFormulario` también es transversal: mantiene valores, errores y estado de envío, aplica
  las reglas de `validaciones.js` y **absorbe los errores por campo que devuelve el backend**
  (`errores: [{campo, mensaje}]`) colocándolos junto al control correspondiente.

Dos contextos:

- **`AutenticacionContexto`** inicia y cierra sesión, restaura la sesión guardada al
  recargar, atiende el 401 del cliente HTTP y responde `tienePermiso()` / `esRol()`.
  Al montar muestra de inmediato la sesión guardada —para evitar el parpadeo hacia el
  login— y en paralelo confirma contra `GET /auth/perfil` que el token siga vigente. Si el
  servidor está caído, **no** expulsa al usuario: conserva lo guardado y la pantalla avisa.
- **`NotificacionContexto`** mantiene la cola de avisos, con un máximo de 4 simultáneos,
  cierre automático a los 5 segundos y limpieza de todos los temporizadores al desmontar.

#### `src/infraestructura/` — adaptadores

- **`clienteHttp.js`** es la **única** instancia de axios del sistema y el único punto donde
  se configura la URL base, el envío del token, el tiempo de espera (20 s) y la
  normalización de errores. Ningún componente ni hook importa axios.
  - La URL base es **relativa** (`/api/v1`) por omisión, de modo que navegador y API
    comparten origen: en desarrollo lo resuelve el proxy de Vite y en producción el de
    Nginx. Sin CORS y sin que el token viaje a otro dominio.
  - El interceptor de petición añade `Authorization: Bearer {token}`, **salvo** en
    `/auth/login`.
  - El interceptor de respuesta devuelve directamente el cuerpo (`respuesta.data`), así los
    adaptadores trabajan con datos y no con la envoltura de axios.
  - `parametrosLimpios(...)` descarta los filtros vacíos para que no se envíen como
    `?estado=&clienteId=`. Su comentario recuerda que por esta vía solo viajan filtros y
    paginación: **ningún dato personal ni credencial va nunca en la URL**.
- Los **siete adaptadores** (`adaptadorClientes`, `adaptadorSolicitudes`, …) son funciones
  delgadas, una por endpoint.
- **`almacenSesion.js`** guarda el token y el usuario en **`sessionStorage`**, no en
  `localStorage`. La decisión está documentada en el propio archivo: `sessionStorage` se
  borra al cerrar la pestaña, así que en una agencia bancaria donde varias personas usan la
  misma computadora el siguiente turno no encuentra una sesión abierta. `localStorage`
  sobreviviría al reinicio del navegador, alargando la ventana de exposición sin ninguna
  ventaja. Lo ideal sería una cookie `HttpOnly` + `SameSite`, inaccesible para JavaScript,
  pero eso exige que el backend la emita y maneje CSRF; dentro del esquema `Bearer` que el
  examen define, `sessionStorage` es la opción más segura, y el compromiso queda escrito.
  **Todos** los accesos van dentro de `try`/`catch`: en modo privado o con el almacenamiento
  bloqueado por política del navegador, leer o escribir lanza excepción, y la aplicación
  debe seguir funcionando (solo que la sesión no persiste entre recargas).

### 8.8 Manejo de errores y notificaciones

El flujo de un error es siempre el mismo, sin importar su origen:

```
Error (backend / red / tiempo de espera)
        │
        ▼
clienteHttp: normalizarError(error)
        │   { estado, codigo, mensaje, errores }
        ▼
   ¿estado === 401 y no es /auth/login?
        │ sí → limpiarSesion() + manejadorNoAutorizado()
        │      → AutenticacionContexto borra el estado
        │      → react-router navega a /login SIN recargar
        │      → mensaje "Su sesión expiró. Inicie sesión nuevamente."
        ▼
   Hook del caso de uso
        │
        ├─ ¿el error trae errores por campo? → useFormulario los coloca junto a cada control
        └─ si no                              → useAvisoDeError() encola un aviso flotante
```

**Normalización.** `normalizarError` reduce cualquier fallo a la misma forma
`{ estado, codigo, mensaje, errores }`, de manera que los hooks y los formularios tratan un
solo tipo de objeto:

| Situación | `estado` | `codigo` | Mensaje |
|---|---|---|---|
| El backend respondió con su formato de error | el HTTP real | el `codigo` del backend | el `mensaje` del backend |
| La petición salió pero no hubo respuesta | `0` | `SIN_CONEXION` | «No se pudo conectar con el servidor. Verifique su conexión e intente de nuevo.» |
| Se agotó el tiempo de espera (20 s) | `0` | `TIEMPO_AGOTADO` | «El servidor tardó demasiado en responder. Intente de nuevo.» |
| Error al construir la petición | `0` | `ERROR_INTERNO` | el mensaje de la excepción |

**Manejo del 401 sin recargar.** En lugar de un `window.location.href = '/login'` dentro del
módulo de infraestructura, `clienteHttp` guarda un *callback* que registra el contexto de
autenticación (`registrarManejadorNoAutorizado`). Una redirección dura recargaría toda la
aplicación, perdería el estado de React y haría que un módulo de infraestructura dependiera
del enrutador y del navegador. Con el *callback*, el contexto limpia su estado y react-router
navega sin recargar, mostrando además el mensaje de sesión expirada. Si nadie registró un
manejador —por ejemplo si un 401 llega antes de que monte la aplicación— la sesión se limpia
de todas formas, para no dejar un token inválido guardado.

Un 401 en `/auth/login` es una excepción deliberada: ahí significa «credenciales
incorrectas» y debe llegar al formulario, no disparar el cierre de sesión.

**Sin avisos duplicados.** `useAvisoDeError` **omite** el aviso flotante cuando el backend
devolvió errores por campo, porque en ese caso `useFormulario` ya los muestra junto a cada
control y un aviso encima solo repetiría la información.

**Notificaciones.** La cola vive en `NotificacionContexto` y la dibuja un único
`Notificaciones` montado en `AppLayout`. Tonos `info`, `exito`, `peligro`, `advertencia`;
duración por omisión de 5 s (con `duracion: 0` el aviso queda fijo hasta que el usuario lo
cierre); máximo de 4 simultáneos para no tapar la pantalla; identificadores generados con un
contador y no con la hora del sistema; y todos los temporizadores se cancelan al desmontar,
para no dejar fugas.

El aviso va **entero del color de su tono** —el degradado correspondiente y texto blanco—
para que el resultado de la operación se lea de un vistazo sin llegar a leer el texto:

| Tono | Color | Título por omisión |
|---|---|---|
| `exito` | Verde | «Operación exitosa» |
| `peligro` | Rojo | «No se pudo completar» |
| `advertencia` | Ámbar | «Atención» |
| `info` | Azul | «Información» |

Cada aviso lleva el icono del tono dentro de un círculo, botón de cerrar y una **barra
inferior que se vacía** durante los 5 segundos de vida, de modo que se ve cuánto le queda.
Entra deslizándose desde la derecha y sale igual; la animación de salida se resuelve en el
componente con un conjunto local de identificadores «saliendo», sin tocar la lógica de la
cola. Quien retira el aviso por tiempo sigue siendo el contexto.

El título por omisión solo se usa cuando quien notifica no manda uno propio: los casos de
negocio sí lo mandan («Cliente registrado», «Pago aplicado»).

`Alerta` mantiene la **versión suave** del mismo lenguaje —fondo claro del tono, franja
lateral con el color fuerte y título en el color oscuro— para los mensajes que quedan fijos
dentro de una pantalla. Así el aviso flotante y la alerta en línea se reconocen como la
misma familia sin competir entre sí.

**Errores dentro de una pantalla.** Cuando el fallo afecta al contenido completo (no a un
campo), la pantalla muestra un `Alerta` en línea en lugar de un aviso flotante: el mensaje
permanece visible mientras el problema exista. Y cuando una consulta no devuelve registros
—que no es un error— se muestra `EstadoVacio` con una explicación y, si procede, la acción
para crear el primer registro.

### 8.9 Decisiones de accesibilidad

Las verificaciones se hicieron sobre el código; se documenta lo que está implementado.

| Decisión | Dónde |
|---|---|
| Idioma declarado | `<html lang="es">` en `index.html`, para que el lector de pantalla use la pronunciación correcta |
| Etiquetas asociadas de verdad | `CampoFormulario` emite `<label htmlFor>` apuntando al `id` del control. Hay 65 usos de `htmlFor` en el código: ningún control queda con una etiqueta solo visual |
| Errores anunciados, no solo coloreados | `CampoFormulario` publica los identificadores de ayuda y error en un contexto, y cada `Campo*` los consume con `aria-describedby` y `aria-invalid` **automáticamente**. Así es imposible olvidar el enlace al agregar un campo nuevo |
| El color nunca es el único portador de significado | `Etiqueta` siempre lleva texto legible («En proceso», «Liquidado»); `BarraProgreso` expone el valor con `aria-valuenow` y un texto accesible; los tonos de `Alerta` van acompañados de icono y título |
| Urgencia correcta de los anuncios | `role="alert"` (asertivo) en los avisos de `peligro` y `advertencia`; `role="status"` con `aria-live="polite"` en los informativos, en `Cargando` y en el contador de caracteres de `CampoTextarea`. Se interrumpe al usuario solo cuando hay que interrumpirlo |
| Modal accesible | `role="dialog"`, `aria-modal="true"`, `aria-labelledby` al título, cierre con `Escape`, **foco atrapado** dentro del panel con ciclo entre el primero y el último elemento enfocable, foco inicial en el primer control y **devuelto al elemento que abrió el modal** al cerrarse. El desplazamiento del cuerpo se bloquea mientras está abierto |
| Desplegable con la semántica declarada a mano | `CampoSelect` ya no es un `<select>`, que traía su semántica de fábrica, así que la declara siguiendo el patrón *combobox* de la guía ARIA: `role="combobox"` con `aria-expanded`, `aria-haspopup="listbox"` y `aria-controls` en el disparador; `role="listbox"` en el panel; `role="option"` con `aria-selected` en cada fila; y `aria-activedescendant` apuntando a la opción activa —en el disparador, o en el cuadro de búsqueda cuando existe, porque es ahí donde vive el foco—. El foco vuelve al disparador al cerrar, y el `Escape` de la lista no atraviesa hasta el modal que la aloja ([8.5](#85-el-desplegable-de-opciones)) |
| Foco siempre visible | 38 apariciones de `:focus-visible` en los CSS. Los controles usan todos el mismo `--anillo-foco` de `tokens.css` ([8.3](#83-sistema-de-diseño-tokens-color-y-movimiento)); las marcas de las gráficas, que son elementos SVG donde `box-shadow` no se dibuja, cambian el anillo por un trazo azul institucional alrededor de la marca. No se elimina el foco visible en ningún control |
| Estados inhabilitados reales | `Boton` con `cargando` aplica `disabled` de verdad (no solo un estilo atenuado) y expone `aria-busy`; los extremos del `Paginador` se inhabilitan igual. Un control que parece pulsable pero no lo es es peor que uno visiblemente inhabilitado |
| Botones de solo icono con nombre | `Icono` es decorativo por omisión (`aria-hidden`), de modo que el botón que lo contiene debe aportar su propio `aria-label`. Hay 51 atributos `aria-label` en los componentes y pantallas (`.jsx`), 10 de ellos en las gráficas |
| Estructura de tabla correcta | `Tabla` emite `<caption>` oculto visualmente y `<th scope="col">`, y desplaza horizontalmente en pantallas pequeñas en lugar de romper la maquetación |
| Ubicación en la navegación | `aria-current="page"` en la última miga de `EncabezadoPagina` y en el elemento activo de `BarraLateral` |
| Métricas anunciadas con su nombre | `TarjetaIndicador` asocia título y valor con `aria-labelledby`, para que se lea «Clientes registrados: 24» y no un número suelto |
| Navegación sin recargar | Las migas y la barra lateral usan `<Link>` de react-router, nunca `<a href>`: se conserva el foco y el estado de la aplicación |
| Contraste | `tokens.css` documenta que los pares texto/fondo cumplen el mínimo AA (4.5:1). El texto suave (`--color-texto-suave: #5A6472`) se reserva para texto secundario |
| Movimiento reducido | 3 bloques `@media (prefers-reduced-motion: reduce)` —en `animaciones.css`, `global.css` y `componentes/base.css`— que desactivan las animaciones y transiciones para quien lo haya pedido en su sistema. El de `animaciones.css` es el que anula `*`, `*::before` y `*::after` ([8.3](#83-sistema-de-diseño-tokens-color-y-movimiento)) |
| Sin trampas de contenido vacío | `EstadoVacio` obliga a explicar por qué una tabla está vacía, en lugar de dejar un área en blanco |
| Gráficas con equivalente accesible | Cada gráfica es un `<figure>` con su título en un `figcaption` oculto. El dibujo lleva `role="img"` y un `aria-label` que lo resume (total, valor más alto, mes en curso); cada marca es enfocable con **foco itinerante** —flechas, `Inicio`, `Fin` y `Escape`— y anuncia su valor en su propio `aria-label`, de modo que el tooltip puede ser `aria-hidden`. El conmutador **Ver tabla** muestra las mismas cifras en una `Tabla` con `<caption>`. La identidad nunca depende solo del color: la dona tiene leyenda con el nombre de cada estado y sus tres colores pasaron el verificador de paletas para daltonismo ([8.3](#83-sistema-de-diseño-tokens-color-y-movimiento)) |

**Límite conocido.** No se ejecutó una auditoría formal WCAG 2.1 AA con herramientas
automatizadas ni pruebas con lector de pantalla real. Lo anterior son decisiones de
implementación verificables en el código, no un certificado de conformidad.

---

## 9. Despliegue

### 9.1 Comando de despliegue

```bash
git clone https://github.com/Ranca2609/Examen_Practico_CHN.git && cd Examen_Practico_CHN
docker compose up -d --build
```

Requisito único: **Docker Desktop** (o Docker Engine con Compose v2) en ejecución, con al
menos 2 GB de memoria disponibles para SQL Server. No hace falta instalar Java, Maven, Node
ni SQL Server: todo se compila dentro de los contenedores.

La primera ejecución descarga las imágenes base y compila el proyecto (aproximadamente
5–8 minutos, incluidas las 262 pruebas del backend). Las siguientes arrancan en segundos.

Cuando los contenedores estén sanos:

| Recurso | URL |
|---|---|
| Aplicación web | <http://localhost:8080> |
| API | <http://localhost:8081/api/v1> |
| Swagger UI | <http://localhost:8081/swagger-ui.html> |
| OpenAPI | <http://localhost:8081/v3/api-docs> |
| Salud del backend | <http://localhost:8081/actuator/health> |
| SQL Server | `localhost:1433`, base `CHN_Prestamos`, usuario `chn_app` |

### 9.2 Servicios del compose

`name: chn-prestamos`. Cuatro servicios en la ruta normal y uno más bajo perfil.

#### `sqlserver`

| Aspecto | Valor |
|---|---|
| Imagen | `mcr.microsoft.com/mssql/server:2022-latest` |
| Contenedor | `chn-sqlserver` |
| Edición | `MSSQL_PID=Developer` (gratuita para desarrollo y pruebas) |
| Puerto | `${DB_PUERTO_HOST:-1433}:1433` |
| Volumen | `sqlserver-datos:/var/opt/mssql` |
| Sonda de vida | Comprueba el **puerto TCP** con `bash -c '</dev/tcp/localhost/1433'`, cada 10 s, 20 reintentos, `start_period` de 30 s. La imagen 2022 no incluye `sqlcmd`, así que no se puede consultar el motor; la disponibilidad real la garantiza `db-init` |
| Reinicio | `unless-stopped` |

#### `db-init`

| Aspecto | Valor |
|---|---|
| Imagen | `mcr.microsoft.com/mssql-tools:latest` (la que sí trae `sqlcmd`) |
| Contenedor | `chn-db-init` |
| Depende de | `sqlserver` con condición `service_started` |
| Qué hace | Espera a que SQL Server acepte conexiones (hasta 60 intentos cada 3 s, unos 3 minutos) y ejecuta `/scripts/init/inicializar.sh`, que a su vez corre `00_crear_base_datos.sql` con `sqlcmd` y credenciales de `sa`. Luego **verifica** que `chn_app` pueda conectarse a la base |
| Monta | `./database:/scripts:ro` (solo lectura) |
| Reinicio | `"no"`: se ejecuta una vez y termina. **No queda residente**, así que `sa` no está disponible en ningún proceso de larga vida |

Por qué existe: SQL Server arranca solo con las bases del sistema. Antes de que Flyway
pueda migrar hay que crear la base de la aplicación y su usuario, y eso requiere permisos
de servidor que el backend no debe tener. El script que ejecuta es **el mismo** que se usa
de forma manual, parametrizado con variables de `sqlcmd`, para no mantener dos versiones de
la misma lógica.

#### `backend`

| Aspecto | Valor |
|---|---|
| Construcción | `context: .` (la **raíz** del repositorio) con `dockerfile: backend/Dockerfile` |
| Imagen | `chn-prestamos-backend:1.0.0` |
| Contenedor | `chn-backend` |
| Depende de | `db-init` con condición **`service_completed_successfully`** |
| Puerto | `${API_PUERTO_HOST:-8081}:8081` |
| Sonda de vida | `curl -fsS http://localhost:8081/actuator/health`, cada 15 s, 20 reintentos, `start_period` de 90 s (cubre el arranque de Flyway) |
| Reinicio | `unless-stopped` |

El contexto de construcción es la raíz y no `backend/` porque el `pom.xml` toma los scripts
SQL de `../database`, que es la única fuente de verdad del esquema.

#### `frontend`

| Aspecto | Valor |
|---|---|
| Construcción | `context: ./frontend` con `dockerfile: Dockerfile` |
| Imagen | `chn-prestamos-frontend:1.0.0` |
| Contenedor | `chn-frontend` |
| Depende de | `backend` con condición **`service_healthy`** |
| Puerto | `${WEB_PUERTO_HOST:-8080}:80` |
| Sonda de vida | `wget -q -O /dev/null http://127.0.0.1/salud`, cada 15 s, 10 reintentos, `start_period` de 15 s |
| Reinicio | `unless-stopped` |

Nginx publica el SPA y hace proxy de `/api` al backend, de modo que el navegador usa **un
único origen** y la superficie de CORS desaparece.

#### `capturas` (perfil `herramientas`)

| Aspecto | Valor |
|---|---|
| Imagen | `mcr.microsoft.com/playwright:v1.49.1-jammy` |
| Perfil | `herramientas`, así que **no** arranca con `docker compose up` |
| Para qué | Genera las capturas anotadas del manual de usuario ejecutando `tools/capturas/capturar.mjs` contra `http://frontend:80` |
| Ejecución | `docker compose --profile herramientas run --rm capturas` |
| Monta | `./tools/capturas:/trabajo` y `./docs/img:/salida` |

La imagen de Playwright trae los navegadores en `/ms-playwright` pero no el paquete npm, y
`NODE_PATH` no aplica a módulos ES. El comando copia el guion a una carpeta temporal del
contenedor (sin tocar el repositorio), instala ahí el paquete y reutiliza los navegadores ya
presentes con `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`.

### 9.3 Orden de arranque y sus condiciones

```mermaid
flowchart LR
    A["sqlserver<br/>SQL Server 2022"] -->|service_started| B["db-init<br/>crea base y usuario<br/>y termina"]
    B -->|service_completed_successfully| C["backend<br/>Flyway + Spring Boot"]
    C -->|service_healthy| D["frontend<br/>Nginx + SPA"]

    A -.->|healthcheck: puerto TCP 1433| A
    C -.->|healthcheck: /actuator/health| C
    D -.->|healthcheck: /salud| D
```

| Paso | Condición de avance | Por qué esa condición |
|---|---|---|
| `sqlserver` → `db-init` | `service_started` | `db-init` ya implementa su propia espera activa (60 intentos), así que no necesita esperar al *healthcheck*: empieza a intentar en cuanto el contenedor existe |
| `db-init` → `backend` | **`service_completed_successfully`** | El backend no debe arrancar hasta que la base y el usuario existan **y la inicialización haya terminado bien**. Si `db-init` falla, el backend no arranca: el fallo se ve donde ocurrió, en lugar de degenerar en un `Login failed for user` confuso |
| `backend` → `frontend` | **`service_healthy`** | Nginx solo se publica cuando la API responde de verdad en `/actuator/health`, es decir cuando Flyway ya aplicó las migraciones y el contexto de Spring levantó |

Dentro del arranque del backend, la secuencia es: validación de `PropiedadesAplicacion`
(falla si falta `JWT_SECRET` o `DB_PASSWORD`) → Flyway aplica las migraciones pendientes →
Hibernate valida el mapeo contra el esquema (`ddl-auto: validate`) →
`CargadorUsuariosIniciales` crea los cuatro usuarios que falten → el servidor acepta
peticiones.

Nginx merece una nota: su `proxy_pass` usa una **variable** y una expresión regular en el
`location`:

```nginx
location ~ ^/api/(.*)$ {
    resolver 127.0.0.11 valid=10s ipv6=off;
    set $destino_backend http://backend:8081;
    proxy_pass $destino_backend/api/$1$is_args$args;
    ...
}
```

Si se escribiera el nombre del servicio literalmente, Nginx lo resolvería **al arrancar** y,
si el contenedor del backend todavía no existiera, el frontend moriría con
`host not found in upstream`. Con una variable la resolución se aplaza a cada petición: el
sitio levanta siempre y, mientras la API no esté disponible, solo `/api` responde 502. Como
al usar variables Nginx ya no reemplaza el prefijo del `location`, la ruta se reconstruye
con el grupo capturado (`$1`) y la cadena de consulta (`$is_args$args`).

### 9.4 Variables de entorno

Todas se leen del archivo `.env` de la raíz si existe; si no, el `docker-compose.yml` aplica
los valores por omisión indicados. `.env.example` es la plantilla.

#### Base de datos

| Variable | Valor por omisión | Para qué sirve |
|---|---|---|
| `MSSQL_SA_PASSWORD` | `Chn#Sa2026Demo` | Contraseña de `sa`. La usa **solo** `db-init`. SQL Server exige mínimo 8 caracteres con mayúsculas, minúsculas, dígitos y símbolos |
| `DB_NAME` | `CHN_Prestamos` | Nombre de la base que crea `db-init` y a la que se conecta el backend |
| `DB_USER` | `chn_app` | Usuario de aplicación, `db_owner` **solo** de esa base |
| `DB_PASSWORD` | `Chn#App2026Demo` | Contraseña del usuario de aplicación. En `application.yml` es `${DB_PASSWORD}` **sin valor por omisión**: si falta, el backend no arranca |
| `DB_HOST` | `sqlserver` (fijado en el compose) | Nombre del servicio en la red de Docker |
| `DB_PORT` | `1433` (fijado en el compose) | Puerto del motor **dentro** de la red |

#### Seguridad de la API

| Variable | Valor por omisión | Para qué sirve |
|---|---|---|
| `JWT_SECRET` | `clave-demo-chn-prestamos-2026-cambiar-en-produccion` | Secreto de firma HMAC-SHA256. **Mínimo 32 caracteres**, validado al arrancar. Generarlo al azar, por ejemplo con `openssl rand -base64 48` |
| `JWT_EXPIRACION_MINUTOS` | `480` | Vigencia del token en minutos (480 = 8 horas, una jornada laboral). Mínimo aceptado: 5 |
| `CORS_ORIGENES` | `http://localhost:8080,http://localhost:5173` | Lista blanca de orígenes autorizados, separados por coma. Nunca comodín. El segundo valor es el servidor de desarrollo de Vite |

Parámetros de seguridad que **no** son variables de entorno y se ajustan en
`application.yml` (bloque `app.seguridad`): `max-intentos-fallidos` (5),
`minutos-bloqueo` (15), `login.max-peticiones` (10) y `login.ventana-minutos` (5). El
emisor del token es fijo, `chn-prestamos`.

#### Usuarios iniciales

| Variable | Valor por omisión | Para qué sirve |
|---|---|---|
| `ADMIN_PASSWORD` | `Chn2026*Demo` | Contraseña inicial de `admin` (rol `ADMIN`) |
| `ANALISTA_PASSWORD` | `Chn2026*Demo` | Contraseña inicial de `analista` (rol `ANALISTA`) |
| `CAJERO_PASSWORD` | `Chn2026*Demo` | Contraseña inicial de `cajero` (rol `CAJERO`) |
| `CONSULTA_PASSWORD` | `Chn2026*Demo` | Contraseña inicial de `consulta` (rol `CONSULTA`) |

Se aplican **solo si el usuario no existe**: un reinicio nunca sobrescribe una contraseña ya
cambiada. Si alguna queda vacía, `CargadorUsuariosIniciales` usa la contraseña de
demostración y deja una **advertencia explícita** en el log.

#### Datos de demostración

| Variable | Valor por omisión | Para qué sirve |
|---|---|---|
| `APP_DATOS_DEMO` | `true` | `true` añade `classpath:db/demo` a los `locations` de Flyway y carga 8 clientes, 10 solicitudes, 4 préstamos y 7 pagos. `false` deja solo el esquema y los parámetros. **Debe escribirse en minúsculas**, porque el valor forma parte de la clave del *placeholder* anidado |
| `APP_CODIGO_AGENCIA` | `001` | Agencia emisora: tres dígitos que forman parte de cada número oficial (`SC-001-2026-000001-3`). Un valor que no sean tres dígitos impide el arranque (`@Pattern` en `PropiedadesAplicacion`). Ver [3.5-d](#35-decisiones-de-diseño-justificación-y-contrapartida) |

#### Publicación de puertos y otros

| Variable | Valor por omisión | Para qué sirve |
|---|---|---|
| `WEB_PUERTO_HOST` | `8080` | Puerto del **host** para la aplicación web (dentro, Nginx siempre escucha en 80) |
| `API_PUERTO_HOST` | `8081` | Puerto del host para la API |
| `DB_PUERTO_HOST` | `1433` | Puerto del host para SQL Server. En producción no debería publicarse |
| `SERVER_PORT` | `8081` (fijado en el compose y en el Dockerfile) | Puerto en el que escucha Spring Boot **dentro** del contenedor |
| `TZ` | `America/Guatemala` (fijado en el compose) | Zona horaria de los contenedores |
| `CAPTURAS_USUARIO` | `admin` | Usuario con el que el servicio `capturas` inicia sesión |

Variable del frontend, que se resuelve **en tiempo de construcción** (no en ejecución,
porque el *bundle* ya está compilado):

| Variable | Valor por omisión | Para qué sirve |
|---|---|---|
| `VITE_API_URL` | `/api/v1` | URL base de la API. El valor relativo funciona en desarrollo (proxy de Vite) y en producción (proxy de Nginx). Solo se cambia si el backend se publica en otro dominio. **Advertencia:** las variables `VITE_` llegan al navegador; nunca poner ahí un secreto |

### 9.5 Volúmenes, puertos y red

#### Volúmenes

| Volumen | Nombre real | Monta en | Contenido |
|---|---|---|---|
| `sqlserver-datos` | `chn-sqlserver-datos` | `sqlserver:/var/opt/mssql` | Archivos de datos de SQL Server. **Persiste entre reinicios**, así que las migraciones no se vuelven a aplicar y los datos capturados durante la evaluación se conservan |

Montajes de enlace (*bind mounts*), solo en servicios auxiliares:

| Origen | Destino | Servicio | Modo |
|---|---|---|---|
| `./database` | `/scripts` | `db-init` | solo lectura (`:ro`) |
| `./tools/capturas` | `/trabajo` | `capturas` | lectura y escritura |
| `./docs/img` | `/salida` | `capturas` | lectura y escritura |

El backend y el frontend **no montan nada**: todo lo que necesitan está dentro de su imagen.

#### Puertos

| Servicio | Host | Contenedor | Protocolo |
|---|---|---|---|
| `frontend` | `8080` (`WEB_PUERTO_HOST`) | `80` | HTTP |
| `backend` | `8081` (`API_PUERTO_HOST`) | `8081` | HTTP |
| `sqlserver` | `1433` (`DB_PUERTO_HOST`) | `1433` | TDS |
| `db-init` | — | — | no publica |
| `capturas` | — | — | no publica |

#### Red

Una sola red *bridge*, `chn-red`. Los servicios se resuelven por su nombre
(`sqlserver`, `backend`, `frontend`) mediante el DNS interno de Docker en `127.0.0.11`.

### 9.6 Cómo cambiar los puertos

**Los tres puertos publicados son variables.** Solo hay que crear o editar `.env` en la raíz
y recrear los contenedores:

```bash
cp .env.example .env
```

```dotenv
WEB_PUERTO_HOST=9090
API_PUERTO_HOST=9091
DB_PUERTO_HOST=14330
```

```bash
docker compose up -d
```

La aplicación queda en `http://localhost:9090` y la API en `http://localhost:9091`.

**Tres detalles importantes:**

1. **No hace falta reconstruir las imágenes.** Solo cambia la publicación de puertos del
   host; dentro de los contenedores Nginx sigue en el 80 y Spring Boot en el 8081, y el
   proxy interno los conecta por nombre de servicio.
2. **Si se cambia `WEB_PUERTO_HOST`, hay que actualizar `CORS_ORIGENES`.** Solo es necesario
   si el navegador llamara a la API directamente; a través del proxy de Nginx el origen es
   único y CORS no interviene. De todos modos conviene mantener la lista coherente:

   ```dotenv
   CORS_ORIGENES=http://localhost:9090
   ```

3. **Para dejar de publicar SQL Server** (recomendado fuera de la evaluación), se comenta el
   bloque `ports` del servicio `sqlserver` en `docker-compose.yml`. El backend seguirá
   conectándose por la red interna; solo se pierde el acceso desde herramientas del host.

### 9.7 Comandos de diagnóstico

Estado y salud de los servicios:

```bash
docker compose ps
```

Registros del backend en vivo:

```bash
docker compose logs -f backend
```

Solo los errores, de todos los servicios:

```bash
docker compose logs --since 10m | grep -iE "error|exception|fatal"
```

Comprobar que la inicialización de la base terminó bien:

```bash
docker compose logs db-init
```

Salud de la API y del sitio:

```bash
curl -s http://localhost:8081/actuator/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/salud
```

Migraciones aplicadas. La imagen `mssql/server:2022-latest` **no incluye `sqlcmd`**, así que
se lanza un contenedor efímero con las herramientas, conectado a la misma red:

```bash
docker run --rm --network chn-red mcr.microsoft.com/mssql-tools:latest \
  /opt/mssql-tools/bin/sqlcmd -S sqlserver -U chn_app -P "Chn#App2026Demo" -d CHN_Prestamos \
  -Q "SELECT version, description, success, installed_on FROM dbo.flyway_schema_history ORDER BY installed_rank"
```

Prueba de humo completa:

```bash
bash tools/pruebas/prueba-api.sh
```

Consumo de recursos y variables efectivas de un contenedor:

```bash
docker stats --no-stream chn-sqlserver chn-backend chn-frontend
docker compose config          # composición final, con las variables ya resueltas
```

---

## 10. Entorno de desarrollo local sin Docker

Solo es necesario para trabajar en el código. Para **evaluar** el sistema basta con
`docker compose up -d --build`.

Requisitos: **JDK 21**, **Maven 3.9 o superior**, **Node 20** y Docker (únicamente para la
base de datos).

### 10.1 Base de datos en contenedor

El motor y su inicialización se levantan solos, sin el backend ni el frontend:

```bash
docker compose up -d sqlserver db-init
```

Comprobar que `db-init` terminó bien antes de seguir:

```bash
docker compose logs db-init
```

Debe aparecer `[init] Listo. Flyway aplicará las migraciones al arrancar el backend.`
Eso confirma que existen la base `CHN_Prestamos` y el usuario `chn_app`, y que ese usuario
puede conectarse. Las tablas todavía no existen: las crea Flyway cuando arranque el backend.

Si se prefiere no usar Docker en absoluto, hay que tener una instancia propia de SQL Server
2022 y ejecutar los cinco scripts a mano, en orden (ver `database/README.md`, sección 2.2).

### 10.2 Backend con Maven

El `application.yml` usa `${DB_HOST:sqlserver}`, es decir toma `sqlserver` por omisión, que
es el nombre del servicio **dentro** de la red de Docker. Desde el host hay que apuntar a
`localhost`. Además `DB_PASSWORD` y `JWT_SECRET` no tienen valor por omisión, así que son
obligatorias.

**Linux, macOS o Git Bash:**

```bash
cd backend

export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=CHN_Prestamos
export DB_USER=chn_app
export DB_PASSWORD='Chn#App2026Demo'
export JWT_SECRET='clave-demo-chn-prestamos-2026-cambiar-en-produccion'
export CORS_ORIGENES='http://localhost:5173,http://localhost:8080'
export APP_DATOS_DEMO=true
export ADMIN_PASSWORD='Chn2026*Demo'
export ANALISTA_PASSWORD='Chn2026*Demo'
export CAJERO_PASSWORD='Chn2026*Demo'
export CONSULTA_PASSWORD='Chn2026*Demo'

mvn spring-boot:run
```

**Windows PowerShell:**

```powershell
cd backend

$env:DB_HOST = 'localhost'
$env:DB_PORT = '1433'
$env:DB_NAME = 'CHN_Prestamos'
$env:DB_USER = 'chn_app'
$env:DB_PASSWORD = 'Chn#App2026Demo'
$env:JWT_SECRET = 'clave-demo-chn-prestamos-2026-cambiar-en-produccion'
$env:CORS_ORIGENES = 'http://localhost:5173,http://localhost:8080'
$env:APP_DATOS_DEMO = 'true'
$env:ADMIN_PASSWORD = 'Chn2026*Demo'
$env:ANALISTA_PASSWORD = 'Chn2026*Demo'
$env:CAJERO_PASSWORD = 'Chn2026*Demo'
$env:CONSULTA_PASSWORD = 'Chn2026*Demo'

mvn spring-boot:run
```

`CORS_ORIGENES` debe incluir `http://localhost:5173` para el caso en que el navegador llame
a la API directamente; a través del proxy de Vite no hace falta, pero tenerlo evita una
sorpresa.

La API queda en <http://localhost:8081/api/v1> y Swagger UI en
<http://localhost:8081/swagger-ui.html>. En el log de arranque se ven las migraciones de
Flyway y la creación de los usuarios iniciales.

Otros comandos útiles:

```bash
mvn test                     # las 262 pruebas, sin base de datos (perfil "test")
mvn -B clean package         # compila, prueba y genera target/prestamos.jar
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=America/Guatemala"
```

Para reiniciar el esquema desde cero durante el desarrollo, la vía limpia es borrar el
volumen (`docker compose down -v`) y volver a levantar `sqlserver` y `db-init`. **No** se
debe borrar a mano `dbo.flyway_schema_history`: la base quedaría con objetos y sin
historial.

### 10.3 Frontend con Vite

```bash
cd frontend
npm install
npm run dev
```

El servidor de desarrollo queda en <http://localhost:5173>, con recarga en caliente. El
guion es `vite --host`, así que también se expone en la red local y se puede probar desde un
teléfono en la misma red (útil para revisar la maquetación móvil).

No hace falta configurar nada más: `VITE_API_URL` no está definida y el cliente HTTP usa el
valor relativo `/api/v1`, que resuelve el proxy.

Otros guiones:

```bash
npm run build      # genera dist/ (minificado, sin sourcemaps)
npm run preview    # sirve dist/ para verificar el bundle de producción
```

### 10.4 El proxy de desarrollo

```javascript
// frontend/vite.config.js
server: {
  port: 5173,
  host: true,
  proxy: {
    '/api': {
      target: 'http://localhost:8081',
      changeOrigin: true,
    },
  },
}
```

El frontend **siempre** llama a rutas relativas (`/api/v1/...`), nunca a un host absoluto.
Quien las reenvía cambia según el entorno:

| Entorno | Quién sirve el SPA | Quién reenvía `/api` | Destino |
|---|---|---|---|
| Desarrollo | Vite en el 5173 | El proxy de `vite.config.js` | `http://localhost:8081` |
| Producción (Docker) | Nginx en el 80 | El `location ~ ^/api/(.*)$` de `nginx.conf` | `http://backend:8081` |

Consecuencias de esta simetría:

- **El código no necesita saber en qué entorno corre.** No hay condicionales por entorno ni
  `.env` distintos por despliegue.
- **El navegador ve un solo origen**, así que no hay peticiones *preflight* de CORS ni
  tokens viajando entre dominios.
- **El comportamiento de desarrollo se parece al de producción**, que es donde aparecen los
  errores caros de descubrir tarde.

`changeOrigin: true` reescribe la cabecera `Host` para que el backend valide bien el
destino.

Si el backend corriera en otro puerto, se cambia el `target` en `vite.config.js`. Si se
quisiera apuntar el frontend a una API remota, se define `VITE_API_URL` en un `.env` local
(por ejemplo `VITE_API_URL=https://api.ejemplo.gt/api/v1`), pero entonces vuelve a
intervenir CORS y ese origen debe estar en la lista blanca del backend.

### 10.5 Combinaciones habituales

| Quiero trabajar en… | Qué levanto |
|---|---|
| El frontend, con una API estable | `docker compose up -d` completo y `npm run dev` apuntando al 8081 publicado |
| El backend, sin tocar la interfaz | `docker compose up -d sqlserver db-init` y `mvn spring-boot:run`; la interfaz se prueba en Swagger UI |
| Las dos cosas | `docker compose up -d sqlserver db-init`, `mvn spring-boot:run` y `npm run dev` |
| Solo el dominio o los casos de uso | `mvn test`: no necesita base de datos ni contenedores |

---

## 11. Pruebas

### 11.1 Inventario por tipo

**262 pruebas en 26 clases**, todas en verde. Solo `NumeroDocumentoTest` usa pruebas
parametrizadas (dos métodos que suman 11 casos); en el resto cada método es un caso. Dos
clases agrupan con `@Nested`: `FiltrosTest`, por listado, y `DocumentoReporteTest`, por
pieza del reporte.

| Tipo | Clase | Pruebas | Qué cubre |
|---|---|---:|---|
| **Dominio — modelo** | `ClienteTest` | 13 | Invariantes de identidad y contacto: DPI de 13 dígitos, teléfono de 8, nombre y apellido de 2 a 60, dirección de 5 a 200, correo válido, mayoría de edad, fecha de nacimiento no futura, inmutabilidad del DPI y de la fecha de nacimiento al actualizar, `nombreCompleto()`, `edad(hoy)` |
| | `SolicitudPrestamoTest` | 15 | Rangos permitidos (monto, plazo, tasa, destino, ingreso) y **máquina de estados**: nace `EN_PROCESO`, solo desde ahí se aprueba o rechaza, el monto aprobado no excede el solicitado, el motivo de rechazo exige de 10 a 500 caracteres, una solicitud resuelta no admite cambios |
| | `PrestamoTest` | 8 | Saldo, abonos y liquidación: el saldo se deriva, un pago no puede exceder el saldo, un préstamo liquidado no admite pagos, al llegar a cero pasa a `LIQUIDADO`, `getPorcentajePagado()`, vencimiento = desembolso + plazo |
| | `UsuarioTest` | 6 | Bloqueo por intentos fallidos, reinicio del contador al bloquear y al entrar bien, `estaBloqueado(ahora)`, normalización y patrón del `username` |
| | `NumeroDocumentoTest` | 21 | Numeración oficial ([3.5-d](#35-decisiones-de-diseño-justificación-y-contrapartida)): composición `SC`/`PR`/`RC`, vector de control de Luhn, correlativo de más de seis dígitos que se relee igual, **cualquier dígito mal copiado se detecta** (se prueba posición por posición), dígitos vecinos intercambiados, prefijo cambiado, número de otro tipo de documento, ocho estructuras inválidas (entre ellas la nomenclatura anterior y los ceros de relleno de más) y que una entidad rechaza un número ajeno |
| | `ResumenGeneralTest` | 5 | El resumen vacío trae totales en cero y series vacías; el constructor de solo totales deja las series vacías y normaliza los montos; las series nulas se convierten en listas vacías; `conSeries(...)` devuelve una copia con las series y conserva los totales; y las series se copian de forma defensiva, así que cambiar la lista de origen no altera el resumen |
| | `CarteraPorTipoTest` | 10 | Montos a escala 2 y nulos convertidos en cero; sin datos, los **cinco tipos en cero y en el orden del enum**; una lista nula se trata como vacía; conserva los tipos con datos, rellena los que faltan y reordena según el enum; un tipo repetido se suma; la serie es inmutable; y los rechazos: elemento nulo, tipo ausente, cantidad negativa y montos negativos |
| | `RecaudacionMensualTest` | 11 | Monto a escala 2 y nulo convertido en cero; sin datos, **12 meses en cero, contiguos y ascendentes, cruzando de año**; cada mes con pagos en su posición y el resto en cero; los meses fuera del rango se ignoran en lugar de rechazarse; un mes repetido se suma; un rango de un mes da un solo elemento; la serie es inmutable; y los rechazos: rango invertido, extremos ausentes, elemento nulo y periodo, cantidad o monto inválidos |
| **Dominio — reportes** | `DocumentoReporteTest` | 19 | En cuatro clases anidadas: `Formatos` 4 (extensión y tipo de contenido, sin distinguir mayúsculas, extensión desconocida o ausente rechazada), `Documento` 8 (título y columnas obligatorios, totales con tantas celdas como columnas, listas inmutables y copiadas), `Filas` 4 (celdas obligatorias, inmutables y tipadas; `ValorCelda` sellada) y `Archivos` 3 (nombre, tipo y contenido; contenido vacío o nulo rechazado) |
| **Dominio — criterios de consulta** | `FiltrosTest` | 39 | Los cinco *records* de filtro, en cinco clases anidadas (`Clientes` 10, `Solicitudes` 8, `Prestamos` 8, `Pagos` 7, `Auditoria` 6) que repiten el mismo guion sobre cada uno: que la fábrica breve `de(...)` deja los demás criterios en `null`; que `tieneFiltrosActivos()` refleja exactamente los criterios presentes; que la paginación fuera de rango se rechaza; que **cada rango invertido se rechaza** —nacimiento y creación en clientes, monto, plazo y fecha en solicitudes, monto, saldo, desembolso y vencimiento en préstamos, monto y fecha en pagos, fecha en auditoría—; que un rango con un solo extremo puesto es válido (en clientes, también con los dos extremos iguales); y que el texto libre se normaliza, recortando el espacio y convirtiendo el blanco en `null`, mientras una búsqueda de más de 120 caracteres se rechaza |
| **Dominio — servicios** | `CalculadoraAmortizacionTest` | 9 | La cuota de referencia Q8 884.88, el caso de tasa cero, el cuadre al centavo (la suma de capitales es exactamente el monto), una cuota por mes numeradas en orden, escala 2, rechazo de parámetros inválidos |
| | `EvaluadorCapacidadPagoTest` | 8 | El límite del 40 % (incluido el borde exacto), el límite de 3 préstamos vigentes, la combinación de ambos, el texto del dictamen, el rechazo de un conteo negativo y de un ingreso no positivo |
| **Casos de uso** | `GestionarClientesServiceTest` | 12 | Unicidad de DPI y correo (incluido «el correo puede seguir siendo el propio al editar»), auditoría de cada operación y **el orden del borrado en cascada** |
| | `GestionarSolicitudesServiceTest` | 10 | Resolución de solicitudes y alta del préstamo en la misma operación, los campos omitidos toman el valor solicitado, la simulación no persiste, una solicitud que ya generó préstamo se rechaza, la evaluación no bloquea la aprobación |
| | `RegistrarPagosServiceTest` | 6 | Abonos, saldos anterior y posterior del recibo, liquidación y el segundo registro de auditoría `PRESTAMO_LIQUIDADO` |
| | `AutenticarUsuarioServiceTest` | 7 | Mensaje idéntico para usuario inexistente, inactivo y contraseña errónea; con la cuenta bloqueada no se compara el hash; persistencia del contador; auditoría de accesos correctos y fallidos |
| | `GenerarReportesServiceTest` | 9 | Plan de amortización con una fila por cuota y totales que cuadran, bloque de encabezado completo, historial de pagos del más antiguo al más reciente, préstamo sin pagos con su nota, préstamo inexistente → no encontrado, **auditoría `REPORTE_DESCARGADO` con el formato**, delegación solo en el generador pedido y falla al construirse si falta un generador |
| | `ConsultarResumenServiceTest` | 3 | Pide la recaudación de los 12 meses que terminan en el mes en curso del `RelojPort`; en enero la ventana cruza al año anterior (febrero a enero); y compone los totales, que pasan intactos, con las series ya completadas por el dominio: 5 tipos en el orden del enum y 12 meses aunque el repositorio devuelva menos filas y desordenadas |
| **Arquitectura** | `ArquitecturaHexagonalTest` | 12 | La comprobación de que el análisis sí importa las clases y las 11 reglas de dependencia entre capas detalladas en [3.4](#34-verificación-automática-con-archunit) |
| **Capa web** | `ClienteControladorTest` | 14 | Contrato HTTP: `201` con cabecera `Location`, `400` con la lista de errores por campo (uno y varios), `404`, `409`, `204` sin cuerpo, `200` en la actualización, metadatos de paginación, `400` con página negativa. Y los **parámetros de consulta** ([5.2](#52-filtros-de-búsqueda-de-los-listados)): que la URL traslada al caso de uso cada criterio con el tipo correcto, que una URL sin criterios entrega un filtro **vacío** y no uno con cadenas en blanco, y que una fecha mal formada o un rango invertido devuelven `400` sin llamar al caso de uso |
| | `PagoControladorTest` | 7 | `201` con el recibo y los saldos, `409` por monto mayor al saldo, `400` con el campo `monto` en cero, `400` sin préstamo, `404` sobre préstamo inexistente, `400` con JSON ilegible, `200` en el listado |
| | `AuditoriaControladorTest` | 5 | El listado de la bitácora desde que `ConsultarAuditoriaUseCase.listar` recibe un `FiltroAuditoria` en lugar de `(int, int)`: `200` con la página y cada criterio trasladado, la consulta completa con la paginación por omisión cuando no hay criterios, y los tres `400` del borde —fecha fuera del formato ISO nombrando el parámetro, `tamano` mayor que 100 y rango de fechas invertido—, los tres **sin tocar la bitácora** (`verifyNoInteractions`) |
| | `PrestamoReportesControladorTest` | 6 | Plan e historial en PDF y en Excel como adjuntos con su tipo de contenido y nombre de archivo, extensión desconocida → `400` y préstamo inexistente → `404` |
| | `ResumenControladorTest` | 4 | Los nueve totales llegan sin cambios; `carteraPorTipo` es un arreglo de 5 tipos en orden con sus indicadores; `recaudacionMensual` es un arreglo de 12 meses con `periodo` `AAAA-MM`, `anio`, `mes`, `cantidadPagos` y `monto`; y con el resumen vacío las series viajan como `[]`, no como `null`. Los montos se verifican además **sobre el texto** de la respuesta (`"montoAprobado":850000.00`, `"monto":0.00`), porque `jsonPath().value()` convierte el número y no detectaría una escala distinta de 2 |
| **Adaptador de persistencia** | `CorrelativoAdaptadorTest` | 2 | Cada documento usa su secuencia y su prefijo, y la agencia configurada forma parte del número |
| | `NumeracionDatosDemoTest` | 1 | Recalcula el dígito verificador de **cada** número escrito a mano en `V900__datos_demo.sql`; exige encontrar los tres tipos para no pasar en verde sin revisar nada |

Reparto por capa: **164** de dominio (62.6 %), **47** de casos de uso (17.9 %), **36** de
capa web (13.7 %), **12** de arquitectura (4.6 %) y **3** del adaptador de persistencia
(1.1 %). La pirámide está donde debe: la mayoría del esfuerzo en el núcleo, que es donde vive
el negocio y donde las pruebas son rápidas y deterministas. El peso del dominio subió con
`FiltrosTest` justamente porque la coherencia de los rangos se valida ahí y no en el
controlador ([3.5-j](#35-decisiones-de-diseño-justificación-y-contrapartida)): la prueba está
en el mismo nivel que la regla. Lo mismo pasa con las series del tablero: de sus **33
pruebas**, 26 están en el dominio (`ResumenGeneralTest`, `CarteraPorTipoTest` y
`RecaudacionMensualTest`), porque es el dominio el que rellena con ceros y fija el orden
([3.5-m](#35-decisiones-de-diseño-justificación-y-contrapartida)); el caso de uso solo
comprueba la ventana de 12 meses que calcula con el reloj, y el controlador, la forma del
JSON.

### 11.2 Cómo ejecutarlas

```bash
cd backend
mvn test
```

**No hace falta base de datos.** El `maven-surefire-plugin` activa el perfil `test`
(`spring.profiles.active=test`), que excluye la autoconfiguración de
`DataSource`, `DataSourceTransactionManager`, `HibernateJpa`, `JpaRepositories` y `Flyway`,
y desactiva `CargadorUsuariosIniciales` con `@Profile("!test")`.

Variantes:

```bash
mvn test -Dtest=CalculadoraAmortizacionTest                 # una clase
mvn test -Dtest='*Test#calcula_la_cuota_conocida_de_referencia'   # un método
mvn test -Dtest='gt.gob.chn.prestamos.domain.**'            # solo el dominio
mvn test -Dtest=FiltrosTest                                 # solo los filtros
mvn test -Dtest='FiltrosTest$Prestamos'                     # una clase anidada
mvn test -Dtest=ArquitecturaHexagonalTest                   # solo la arquitectura
mvn -B clean package                                        # pruebas + jar
```

Los informes quedan en `backend/target/surefire-reports/` (un `.txt` y un `.xml` por clase).

**Las pruebas bloquean la imagen.** El `backend/Dockerfile` ejecuta
`RUN mvn -B -f backend/pom.xml clean package`, así que un fallo detiene la construcción y
`docker compose up --build` no llega a levantar nada.

### 11.3 Qué cubre cada grupo y por qué está ahí

| Grupo | Estrategia | Por qué importa |
|---|---|---|
| **Dominio** | `new` directo, sin Spring, sin dobles. El tiempo entra como parámetro (`LocalDateTime ahora`), así que no hay dependencia del reloj real | Son las pruebas más valiosas por unidad de esfuerzo: verifican las reglas de negocio de verdad, corren en milisegundos y no se rompen por un cambio de framework. Aquí es donde se comprueba que un objeto **no puede existir en estado inválido**, y desde esta iteración eso incluye los **criterios de consulta**: `FiltrosTest` construye cada *record* de filtro con el rango al revés y espera la excepción, sin levantar un solo controlador |
| **Casos de uso** | JUnit 5 + Mockito. Los 12 puertos de salida se sustituyen por dobles; `RelojPort` devuelve una fecha fija | Verifican la **orquestación**, que es lo que el dominio no puede probar por sí solo: que las llamadas ocurren en el orden correcto, que se audita lo que hay que auditar y que los errores del dominio se propagan. El caso claro es el orden del borrado en cascada: es una precondición que solo se ve aquí |
| **Arquitectura** | ArchUnit sobre las clases compiladas, con el propio conjunto importado validado primero | Convierten en prueba ejecutable lo que normalmente es una convención en un documento. Un `import` de `@Entity` en el dominio no se detecta leyendo un diagrama; se detecta con `mvn test` |
| **Capa web** | `@WebMvcTest` con `MockMvc`, casos de uso sustituidos por `@MockitoBean` y filtros desactivados (`addFilters = false`) | Verifican el **contrato HTTP**: código de estado, cuerpo, cabecera `Location` y la traducción de cada excepción de dominio al formato de error uniforme. El `ExtractorContextoOperacion` se usa **real** (es un colaborador sin estado) y `ManejadorExcepcionesGlobal` lo aporta el propio corte al ser `@RestControllerAdvice`, de modo que lo que se prueba es la cadena completa del adaptador. También cubren la **traducción de la cadena de consulta al filtro**, que es la única lógica propia que el controlador tiene sobre un listado: se captura el `Filtro*` que recibe el caso de uso con un `ArgumentCaptor` y se afirma criterio por criterio, incluido que un parámetro ausente llega como `null` y no como cadena vacía |

Dos ausencias deliberadas y su compensación:

- **No hay pruebas de integración con base de datos** (Testcontainers o similar). Habrían
  añadido varios minutos a cada construcción y la dependencia de un motor disponible. Lo que
  ellas cubrirían —que el mapeo JPA coincide con el esquema real, que las consultas son
  válidas y que las migraciones se aplican— lo cubren tres mecanismos en tiempo de arranque
  y ejecución: `ddl-auto: validate` (falla si el mapeo divergió), `validate-on-migrate`
  (falla si una migración cambió) y las dos pruebas de humo contra el sistema real. Esto
  pesa especialmente en los filtros: el JPQL de cada listado es texto que Hibernate solo
  valida al construir el `EntityManagerFactory`, y su corrección semántica —que el
  `countQuery` repita el mismo `WHERE`, que el rango de saldo use la resta y no la columna
  calculada— solo se ve consultando de verdad. De ahí `prueba-filtros.sh` ([11.5](#115-prueba-de-humo-de-los-filtros)).
- **La autorización por rol no se prueba en el corte web**, porque los filtros están
  desactivados. Se prueba donde de verdad importa: contra el sistema en ejecución, en el
  grupo 6 de `prueba-api.sh`, con seis casos cruzados de rol.

### 11.4 Prueba de humo de la API

`tools/pruebas/prueba-api.sh` es la comprobación de extremo a extremo contra el sistema en
ejecución. Recorre los 23 endpoints con datos reales, crea un cliente y su préstamo, registra
pagos y al final lo borra todo.

```bash
docker compose up -d
bash tools/pruebas/prueba-api.sh
```

Contra otro entorno:

```bash
API=http://mi-servidor:8081/api/v1 bash tools/pruebas/prueba-api.sh
```

**57 comprobaciones en 8 grupos.** El script imprime `OK` o `FALLO` por caso (con el código
esperado y el real) y termina con código de salida `0` **solo si todas pasan**, así que sirve
tal cual en una canalización de integración continua.

| Grupo | Casos | Qué valida |
|---|---:|---|
| **1. Autenticación y JWT** | 6 | Login sin credenciales → `400`; clave incorrecta → `401`; login correcto devuelve token; `/auth/perfil` con token → `200`; petición sin token → `401`; token inválido (`aa.bb.cc`) → `401` |
| **2. Gestión de clientes** | 10 | Listado paginado y búsqueda por texto → `200`; cliente inexistente → `404`; DPI inválido → `400`; menor de edad → `400`; alta válida → `201`; DPI duplicado → `409`; edición → `200`; solicitudes y préstamos del cliente → `200` |
| **3. Solicitudes** | 11 | Listado y filtro por `EN_PROCESO` → `200`; estado inventado → `400`; simulación → `200`; monto fuera de rango → `400`; creación → `201` con **número oficial `SC` válido** (estructura y dígito verificador recalculado en bash); rechazo con motivo corto → `400`; aprobación → `200`; **aprobar y rechazar una ya resuelta → `409`** |
| **4. Préstamos y pagos** | 11 | Préstamos del cliente (con **número `PR` válido**), detalle, **plan de amortización** (cuenta las cuotas devueltas) y pagos del préstamo → `200`; pago de monto 0 → `400`; pago mayor al saldo → `409`; registro de pago → `201` con **recibo `RC` válido** (imprime recibo y saldos); pago sobre préstamo inexistente → `404`; listado de pagos → `200` |
| **5. Resumen y auditoría** | 4 | `/resumen` → `200` (imprime los indicadores); sobre esa misma respuesta, `carteraPorTipo` trae **los 5 tipos en el orden del catálogo** y `recaudacionMensual` **12 periodos `AAAA-MM` en orden ascendente** (cuenta con `grep -o`, como el resto del guion); `/auditoria` como `ADMIN` → `200` |
| **6. Autorización por rol** | 6 | Cajero **no** ve auditoría → `403`; cajero **no** crea clientes → `403`; cajero **sí** consulta clientes → `200`; cajero **sí** registra pagos → `201`; consulta **no** registra pagos → `403`; consulta **no** elimina clientes → `403` |
| **7. Borrado en cascada** | 4 | `DELETE` del cliente como `ADMIN` → `204`; y después el cliente, **su préstamo** y **su solicitud** responden `404`. Es la verificación de que la cascada explícita funciona de verdad |
| **8. Superficie pública** | 5 | `/actuator/health` → `200`; `/v3/api-docs` → `200`; Swagger UI → `200`; aplicación web en el 8080 → `200`; **`/api/v1/resumen` a través del proxy de Nginx → `401`**, que confirma que el proxy funciona y que la API sigue protegida detrás de él |

Lo que esta prueba cubre y las unitarias no:

- Que los **cuatro contenedores** se orquestan y hablan entre sí.
- Que **Flyway aplicó** las migraciones y el esquema real acepta las escrituras.
- Que las **vistas del tablero** (`V4`) existen en la base real y que `/resumen` arma sus series
  con la forma del contrato: 5 tipos y 12 meses, sin importar cuántos datos haya.
- Que el **mapeo JPA coincide** con las tablas (`ddl-auto: validate` ya lo habría impedido
  al arrancar, pero aquí se confirma con escrituras reales).
- Que las **secuencias** generan correlativos válidos.
- Que la **autorización por rol** funciona con filtros activos y tokens reales.
- Que el **proxy de Nginx** reenvía correctamente y preserva la protección.
- Que la **cascada explícita** borra en el orden correcto sin violar ninguna llave foránea.

Requisitos: el sistema levantado, `curl` disponible, y los datos de demostración cargados
(el script usa los usuarios `admin`, `cajero` y `consulta` con la contraseña de examen). El
script deja la base **como la encontró**: todo lo que crea lo borra en el grupo 7.

### 11.5 Prueba de humo de los filtros

`tools/pruebas/prueba-filtros.sh` es el complemento del anterior: donde `prueba-api.sh`
recorre los endpoints, este recorre los **criterios de búsqueda** de los cinco listados
([5.2](#52-filtros-de-búsqueda-de-los-listados)) contra el sistema en ejecución.

```bash
docker compose up -d
bash tools/pruebas/prueba-filtros.sh
```

Admite la misma variable `API` para apuntar a otro entorno. Son **59 comprobaciones** en
cinco bloques, con el mismo contrato que la otra prueba: una línea `OK` o `FALLO` por caso y
salida `0` solo si todas pasan.

| Bloque | Casos | Qué valida |
|---|---:|---|
| **Clientes** | 16 | Texto libre por apellido, DPI parcial, correo y teléfono; `nacimientoDesde`/`Hasta` por separado y como rango; el rango de creación amplio que debe incluir a **todos** y el futuro que no debe incluir a **nadie**; `activo=true` / `activo=false`; una combinación de texto, rango de nacimiento y `activo`; y los tres `400` (rango invertido, fecha mal formada y `tamano=500`) |
| **Solicitudes** | 14 | Búsqueda por número, por nombre del cliente y por destino; `estado` y `tipoPrestamo`; rangos de monto y de plazo; rango de fecha amplio y futuro; una combinación; y los `400` de tipo inválido, monto invertido y plazo invertido |
| **Préstamos** | 13 | Búsqueda por número de préstamo, por el **número de la solicitud de origen** y por cliente; `estado`; rango de monto aprobado; **rango de saldo pendiente** —el amplio incluye todos, el imposible ninguno—; rangos de desembolso y de vencimiento; y el `400` del saldo invertido |
| **Pagos** | 9 | Búsqueda por recibo, por número de préstamo y por cliente; rango de monto; rango de fecha amplio y futuro; `usuarioRegistro`; `prestamoId` |
| **Auditoría** | 7 | `accion` y `entidad` por valor exacto, `usuario` parcial, texto libre, rango de fechas y el `400` de una fecha ilegible |

**Por qué lee los totales de la API en vez de fijarlos.** El juego de datos de este sistema
**crece**: cada vez que alguien usa la aplicación, que se ejecuta `prueba-api.sh` o que se
regeneran las capturas del manual quedan clientes, solicitudes, pagos y —sobre todo—
registros de auditoría nuevos. Un guion que afirmara «clientes debe devolver 8» empezaría a
fallar en la primera ejecución siguiente, y el mantenedor aprendería a ignorar sus fallos,
que es la peor cosa que le puede pasar a una prueba. Por eso el guion consulta primero los
totales con `tamano=1` —solo interesa `totalElementos`— y a partir de ahí afirma
**relaciones invariantes**, que son verdad con cualquier volumen de datos:

- un filtro deliberadamente amplio (`creacionDesde=2000-01-01&creacionHasta=2100-01-01`)
  tiene que devolver **exactamente el total sin filtrar**;
- uno deliberadamente imposible (`creacionDesde=2099-01-01`, `saldoMinimo=99999999`) tiene
  que devolver **exactamente 0**;
- el total de `estado=VIGENTE` con `tamano=50` tiene que ser el mismo que con `tamano=1`:
  `totalElementos` no puede depender del tamaño de la página;
- añadir un criterio que no restringe (`estado=APROBADA&montoMinimo=1000`) no puede cambiar
  el total de `estado=APROBADA` a secas;
- una búsqueda por un identificador único (`SC-001-2026-000003-9`, un DPI, un teléfono) tiene que
  devolver **1**.

Estas cinco formas son las que de verdad detectan un filtro roto: un criterio que se ignore
en silencio rompería la primera, uno que filtre de más rompería la segunda y uno que aplique
el `WHERE` al contenido pero no al `countQuery` rompería las dos primeras a la vez —que es
justo el error que [3.5-i](#35-decisiones-de-diseño-justificación-y-contrapartida) explica—.
Si alguno de los totales de referencia no se puede leer, el guion **aborta** en lugar de
seguir: una prueba que no puede afirmar nada no debe terminar en verde.

A diferencia de `prueba-api.sh`, este guion **no escribe ningún dato de negocio**: todas sus
peticiones son `GET`, y lo único que deja tras de sí es el asiento de auditoría del inicio de
sesión, igual que cualquier uso normal del sistema. Se puede ejecutar cuantas veces se quiera
y en cualquier momento, incluso sobre un entorno con datos reales.

### 11.6 El guion de capturas y el desplegable propio

`tools/capturas/capturar.mjs` no es una prueba —no afirma nada ni devuelve un código de
salida por caso—, pero es el único automatismo del proyecto que **opera la interfaz real** con
Playwright: inicia sesión, recorre cada flujo y fotografía las pantallas del manual de usuario
([9.2](#92-servicios-del-compose)). Por eso el rediseño del desplegable
([8.5](#85-el-desplegable-de-opciones)) le afectó de lleno, y conviene dejar escrito qué
cambió.

**Ya no se usa `selectOption()`.** Esa API de Playwright existe únicamente para el `<select>`
nativo: opera el control del navegador y fija su propiedad `value`. Cuando la lista es un
`div` con `role="listbox"` dibujado en un portal, no hay nada que operar; `selectOption()`
falla porque el elemento no es un `<select>`. La sustitución no es un parche: el guion ahora
hace **lo que hace el usuario** —pulsar el disparador y pulsar la fila—, que es además lo que
de verdad se quiere probar al generar una captura.

Cuatro ayudantes cubren todo el trato con el control —apoyados en dos auxiliares,
`disparadorDe`, que localiza el disparador por su `data-captura`, y `esperarValor`, que espera
a que el `data-valor` del disparador refleje la selección—, y cada uno se apoya en el contrato
de DOM del componente:

| Ayudante | Qué hace y de qué marca depende |
|---|---|
| `abrirSelect` | Pulsa el disparador solo si su `aria-expanded` no es ya `true`, y espera el panel. El `click()` de Playwright espera por su cuenta a que el control esté habilitado, que es lo que hace falta mientras el catálogo de clientes o de préstamos sigue cargando (el campo llega `deshabilitado`) |
| `panelDe` | Localiza el panel siguiendo el `aria-controls` del disparador. Al vivir en el portal el panel **no es descendiente** del disparador, y un `.chn-select__panel` a secas podría atrapar el de otro campo |
| `elegir` | Pulsa `.chn-select__opcion[data-valor="…"]`, espera a que el panel se desmonte y **confirma la selección leyendo el `data-valor` del disparador**, nunca su texto visible |
| `elegirPrimeraOpcion` | Elige la primera fila con `data-valor` no vacío —así se salta el *placeholder* y los «Todos…»— y devuelve el valor elegido. Sustituye al antiguo `primerValorDe`, que leía el valor sin elegirlo y obligaba a abrir el panel dos veces |

De paso desapareció una incomodidad del nativo: para saber si el catálogo ya había cargado
había que consultar `select.options.length` **desde dentro de la página**, porque Playwright
considera invisibles los `<option>` de un `<select>` cerrado y un `waitFor()` sobre ellos no
se cumplía nunca. Las filas del `listbox` propio son elementos visibles como cualquier otro,
así que valen las esperas normales de Playwright.

A las dos pruebas de humo ([11.4](#114-prueba-de-humo-de-la-api) y
[11.5](#115-prueba-de-humo-de-los-filtros)) **el cambio no les afecta**: hablan HTTP con la
API con `curl`, nunca con la interfaz, y el rediseño del desplegable es exclusivamente de
presentación, sin efecto en ningún *endpoint* ni en ningún DTO.

**Las gráficas del tablero se fotografían en dos capturas.** Con la ventana de 1440 × 900 del
guion, la primera pantalla del tablero alcanza los indicadores y la primera fila de paneles:
`02-tablero` añade a sus cuatro marcas de siempre la 5 (*Solicitudes por estado*) y la 6
(*Cartera*), al final, para no cambiar los números que cita el manual. La segunda fila no
cabe, así que `25-tablero-graficas` desplaza la página hasta dejar la tarjeta de recaudación
bajo la barra superior fija, pasa el puntero sobre la **columna más alta** para que se vea su
tooltip y señala la gráfica, el mes en curso, el tooltip, el botón **Ver tabla** y la cartera
por tipo. La columna se busca por su posición horizontal y no por su orden, porque los meses
en cero no dibujan columna; si no hay pagos en la ventana, la captura sale igual con el estado
vacío y las anclas que faltan quedan en `avisos`. Al terminar, el guion devuelve el puntero y
el desplazamiento a su sitio para no contaminar las capturas siguientes.

---

## 12. Operación y mantenimiento

### 12.1 Ver los registros

```bash
docker compose logs -f backend          # en vivo
docker compose logs --tail 200 backend  # últimas 200 líneas
docker compose logs --since 15m         # todos los servicios, últimos 15 minutos
docker compose logs db-init             # inicialización de la base (se ejecuta una vez)
docker compose logs frontend            # accesos y errores de Nginx
```

Filtrar lo que interesa:

```bash
docker compose logs backend | grep -iE "error|exception"
docker compose logs backend | grep -i flyway           # migraciones aplicadas
docker compose logs backend | grep -i "ADVERTENCIA"    # contraseñas de demostración en uso
docker compose logs backend | grep -i "CORS habilitado" # orígenes efectivos
```

Niveles configurados (`application.yml`): `root` en `INFO`,
`gt.gob.chn.prestamos` en `INFO`, y `org.hibernate.SQL`,
`org.hibernate.orm.jdbc.bind`, `org.springframework.security` y `org.springframework.web`
en **`WARN`**. Ese `WARN` en las dos primeras es deliberado: las sentencias SQL y sus
parámetros pueden contener DPI, direcciones y correos, y no deben quedar en los archivos de
log.

Para diagnosticar una consulta en desarrollo se puede subir el nivel temporalmente **sin
reconstruir la imagen**, con una variable de entorno de Spring Boot:

```bash
docker compose run --rm -e LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG backend
```

Debe volverse a bajar antes de cualquier uso con datos reales.

El formato de línea es
`yyyy-MM-dd HH:mm:ss.SSS NIVEL [hilo] logger - mensaje`, en hora de Guatemala.

Los registros van a la salida estándar, que es lo correcto en un contenedor: los recoge el
*driver* de log de Docker. No se escriben archivos dentro de la imagen.

### 12.2 Conectarse a la base de datos

**Desde una herramienta del host** (SSMS, Azure Data Studio, DBeaver, DataGrip):

| Campo | Valor |
|---|---|
| Servidor | `localhost,1433` |
| Base | `CHN_Prestamos` |
| Usuario | `chn_app` |
| Contraseña | `Chn#App2026Demo` (o el valor de `DB_PASSWORD`) |
| Cifrado | Activado, **confiar en el certificado del servidor** (es autofirmado) |

Para inspeccionar con el usuario de aplicación basta ese acceso: es `db_owner` de la base.
`sa` solo se necesita para operaciones de servidor.

**Desde la línea de comandos.** La imagen del motor no trae `sqlcmd`, así que se lanza un
contenedor efímero con las herramientas en la misma red:

```bash
docker run --rm -it --network chn-red mcr.microsoft.com/mssql-tools:latest \
  /opt/mssql-tools/bin/sqlcmd -S sqlserver -U chn_app -P "Chn#App2026Demo" -d CHN_Prestamos
```

Consultas de verificación (las mismas que documenta `database/README.md`):

```sql
-- Migraciones aplicadas y su resultado
SELECT installed_rank, version, description, success, installed_on
FROM dbo.flyway_schema_history
ORDER BY installed_rank;
GO

-- Tablero general (una sola fila)
SELECT * FROM dbo.vw_resumen_general;
GO

-- Series del tablero: solo los tipos con préstamos y los meses con pagos
SELECT * FROM dbo.vw_cartera_por_tipo ORDER BY tipo_prestamo;
GO
SELECT * FROM dbo.vw_recaudacion_mensual ORDER BY anio, mes;
GO

-- Cartera con saldos
SELECT numero_prestamo, nombre_cliente, monto_total_a_pagar, total_pagado,
       saldo_pendiente, porcentaje_pagado, estado
FROM dbo.vw_prestamos_saldo
ORDER BY numero_prestamo;
GO

-- Estado de cuenta de un préstamo (encabezado + pagos)
EXEC dbo.sp_estado_cuenta_prestamo @prestamo_id = 1;
GO

-- Historial de pagos de un cliente
EXEC dbo.sp_historial_pagos_cliente @cliente_id = 1;
GO

-- La cuota de SQL debe coincidir con la de la aplicación
SELECT dbo.fn_calcular_cuota(100000.00, 12.00, 12) AS cuota_esperada;   -- 8884.88
GO

-- Cuadre de la cartera: NO debe devolver ninguna fila
SELECT p.numero_prestamo, p.total_pagado, SUM(pg.monto) AS suma_pagos
FROM dbo.prestamos p
    LEFT JOIN dbo.pagos pg ON pg.prestamo_id = p.id
GROUP BY p.numero_prestamo, p.total_pagado
HAVING p.total_pagado <> ISNULL(SUM(pg.monto), 0);
GO

-- Últimos movimientos de la bitácora
SELECT TOP 20 fecha, usuario, accion, entidad, entidad_id, direccion_ip
FROM dbo.auditoria
ORDER BY fecha DESC, id DESC;
GO
```

### 12.3 Restablecer el entorno

| Objetivo | Comando | Efecto |
|---|---|---|
| Detener sin perder nada | `docker compose down` | Elimina los contenedores. **El volumen persiste**, así que al volver a levantar los datos siguen ahí y Flyway no reaplica nada |
| Reiniciar un servicio | `docker compose restart backend` | Reinicia solo ese contenedor |
| Recargar código del backend | `docker compose up -d --build backend` | Reconstruye la imagen (ejecuta las pruebas) y recrea el contenedor |
| **Borrón y cuenta nueva** | `docker compose down -v` y después `docker compose up -d --build` | `-v` **elimina el volumen**: la base se crea de cero, Flyway aplica las 4 migraciones y se recargan los datos de demostración. Es la forma correcta de volver al estado inicial |
| Liberar espacio | `docker compose down -v --rmi local` | Además elimina las imágenes construidas localmente |

> `docker compose down -v` **borra todos los datos capturados**. Es lo que se quiere antes de
> una demostración limpia, y lo que no se quiere nunca en un entorno con información real.

Nota sobre restablecimientos parciales: **no** se debe borrar a mano
`dbo.flyway_schema_history` ni las tablas sueltas. La base quedaría con objetos y sin
historial, y el siguiente arranque fallaría o dejaría el esquema a medias. La única forma
limpia de reiniciar el esquema es eliminar el volumen.

### 12.4 Desactivar los datos de demostración

```bash
cp .env.example .env          # si aún no existe
```

En `.env`:

```dotenv
APP_DATOS_DEMO=false
```

Y recrear desde cero, porque el volumen conserva los datos ya cargados:

```bash
docker compose down -v
docker compose up -d --build
```

Qué queda y qué no:

| Elemento | Con `false` |
|---|---|
| Esquema completo (`V1`), objetos programables (`V2`), parámetros (`V3`), vistas del tablero (`V4`) y restricción endurecida (`V5`) | **Sí** |
| Los 4 usuarios (`admin`, `analista`, `cajero`, `consulta`) | **Sí**: los crea la aplicación, no Flyway |
| 8 clientes, 10 solicitudes, 4 préstamos, 7 pagos (`V900`) | **No** |
| Correlativos | Empiezan en `SC-001-2026-000001-3`, `PR-001-2026-000001-9`, `RC-001-2026-000001-4` |

El valor debe escribirse en minúsculas (`false`), porque forma parte de la clave del
*placeholder* anidado que arma los `locations` de Flyway. Ante cualquier otro valor se aplica
el respaldo, que también es solo el esquema, pero conviene no depender de eso.

Para comprobar que quedó como se espera:

```bash
docker compose logs backend | grep -i "db/demo"     # no debe aparecer
curl -s http://localhost:8081/api/v1/resumen -H "Authorization: Bearer $TOKEN"
# totalClientes debe ser 0
```

### 12.5 Cómo agregar un nuevo caso de uso, paso a paso

El recorrido siempre va **de adentro hacia afuera**: primero el dominio, al final los
adaptadores. Como ejemplo, «anular un pago» (que hoy no existe).

**Paso 1 — Dominio: la regla.**
Escribir primero el comportamiento en el agregado. Si «anular un pago» significa devolver
el saldo, el método va en `Prestamo`:

```java
// domain/model/Prestamo.java
public void revertirPago(BigDecimal monto) {
    Validaciones.exigirPositivo(monto, "monto a revertir");
    BigDecimal montoNormalizado = Montos.normalizar(monto);
    if (montoNormalizado.compareTo(totalPagado) > 0) {
        throw new ReglaNegocioException("No se puede revertir más de lo pagado.");
    }
    this.totalPagado = Montos.normalizar(totalPagado.subtract(montoNormalizado));
    this.estado = EstadoPrestamo.VIGENTE;
}
```

Reglas de la capa: sin anotaciones, sin dependencias de framework, y todo lo que necesite
del exterior (la fecha, por ejemplo) entra como parámetro.

**Paso 2 — Dominio: la prueba de la regla.**
Añadir los casos a `PrestamoTest`: que rechaza un monto mayor a lo pagado, que devuelve el
préstamo a `VIGENTE`, que el saldo cuadra. Corre con `mvn test`, sin base de datos.

**Paso 3 — Dominio: el comando y el puerto de entrada.**

```java
// domain/port/in/command/AnularPagoCommand.java
public record AnularPagoCommand(Long pagoId, String motivo) { }
```

```java
// domain/port/in/RegistrarPagosUseCase.java  (se amplía el existente)
PagoDetalle anular(AnularPagoCommand cmd, ContextoOperacion ctx);
```

Criterio para decidir: si la operación pertenece al mismo agregado y al mismo rol, se amplía
el caso de uso existente. Si es un ámbito nuevo, se crea una interfaz nueva.

**Paso 4 — Dominio: el puerto de salida, solo si falta algo.**
Si se necesita una operación que ningún puerto ofrece (por ejemplo
`PagoRepositorio.eliminar(id)`), se declara en la interfaz de `domain/port/out/`.
**Sin tipos de JPA ni de Spring** en la firma.

**Paso 5 — Aplicación: la orquestación.**

```java
// application/usecase/RegistrarPagosService.java
@Override
public PagoDetalle anular(AnularPagoCommand cmd, ContextoOperacion ctx) {
    Pago pago = pagoRepositorio.buscarPorId(cmd.pagoId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Pago", cmd.pagoId()));
    Prestamo prestamo = prestamoRepositorio.buscarPorId(pago.getPrestamoId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo", pago.getPrestamoId()));

    prestamo.revertirPago(pago.getMonto());

    prestamoRepositorio.guardar(prestamo);
    pagoRepositorio.eliminar(pago.getId());

    auditoria.registrar(ctx.usuario(), AccionesAuditoria.PAGO_ANULADO,
            AccionesAuditoria.ENTIDAD_PAGO, String.valueOf(pago.getId()),
            "Anulado el recibo " + pago.getNumeroRecibo() + ". Motivo: " + cmd.motivo(),
            ctx.direccionIp());
    // ...
}
```

Reglas de la capa: la clase lleva `@Service` y `@Transactional` (todo el método es una
unidad atómica), **cero lógica de negocio propia** —solo secuencia de llamadas—, y la
auditoría dentro de la transacción. La acción nueva se añade como constante en
`AccionesAuditoria`, nunca como literal.

**Paso 6 — Aplicación: la prueba de la orquestación.**
Añadir los casos a `RegistrarPagosServiceTest` con Mockito: que se llama a `revertirPago`,
que se guarda el préstamo **antes** de borrar el pago si el orden importa, que se audita, y
que un pago inexistente lanza `RecursoNoEncontradoException`.

**Paso 7 — Infraestructura: el adaptador de salida.**
Si el paso 4 añadió un método al puerto, implementarlo en
`adapter/out/persistencia/adaptador/PagoRepositorioJpa`, apoyándose en el repositorio de
Spring Data. Si hace falta una consulta, va en la interfaz `PagoJpaRepositorio` con
`@Query` y **parámetros nombrados**.

**Paso 8 — Infraestructura: el adaptador de entrada.**

1. DTO de petición en `dto/peticion/` con las anotaciones de validación.
2. Método de mapeo en el `MapeadorWeb*` correspondiente (a mano, sin MapStruct).
3. Método en el controlador, con `@PreAuthorize`, el verbo y la ruta, y las anotaciones
   `@Operation` y `@ApiResponses` para que aparezca en Swagger UI.
4. Si la operación lanza un tipo de excepción nuevo, añadir su `@ExceptionHandler` a
   `ManejadorExcepcionesGlobal`; las cinco excepciones de dominio existentes ya están
   cubiertas.
5. Si la autorización no encaja en las reglas vigentes, añadir el `requestMatchers`
   correspondiente en `SeguridadConfig.cadenaApi`, **antes** del cierre
   `anyRequest().authenticated()` y respetando el orden de lo más específico a lo más
   general.

**Paso 9 — Base de datos, si el esquema cambia.**
Crear una migración **nueva** (`V6__...`, porque `V4` y `V5` ya existen), nunca editar una aplicada. Añadir los `CHECK`
que replican la regla del dominio. Verificar que el script sea idempotente. Si se agregó una
columna, mapearla en la entidad JPA: `ddl-auto: validate` hace fallar el arranque si el
mapeo y el esquema no coinciden, lo cual es exactamente lo que se quiere.

**Paso 10 — Capa web: la prueba del contrato.**
Añadir los casos a `PagoControladorTest`: el código de estado correcto, el cuerpo, y la
traducción de la excepción de dominio al formato de error uniforme.

**Paso 11 — Frontend.**
En el mismo orden, de adentro hacia afuera:

1. `src/dominio/` si hay una etiqueta, un formato o una validación nueva.
2. `src/infraestructura/http/adaptadorPagos.js`: una función por endpoint.
3. `src/aplicacion/hooks/usePagos.js`: la acción del caso de uso, con `cargando` y `error`.
4. `src/dominio/catalogos.js`: el permiso nuevo en `PERMISOS` y `PERMISOS_POR_ROL`.
5. La pantalla, **reutilizando** los componentes existentes. Si hace falta uno nuevo, va en
   `src/ui/componentes/` con su CSS y su entrada en `index.js`.

**Paso 12 — Verificación.**

```bash
cd backend && mvn test                 # las 262 + las nuevas, todas en verde
docker compose up -d --build           # levanta el entorno completo
bash tools/pruebas/prueba-api.sh       # 57 comprobaciones + las que se agreguen
bash tools/pruebas/prueba-filtros.sh   # 59 comprobaciones de filtros
```

Y añadir el caso al guion de humo, para que el endpoint nuevo quede cubierto de extremo a
extremo.

**Lista de comprobación de la arquitectura.** Las 12 pruebas de ArchUnit fallan solas si algo
se sale del sitio, pero conviene revisar a conciencia:

- [ ] ¿El dominio sigue sin importar Spring, Jakarta, JPA, Jackson ni jjwt?
- [ ] ¿La capa de aplicación sigue sin importar nada de `infrastructure`?
- [ ] ¿La regla de negocio quedó en el dominio y no en el controlador ni en el caso de uso?
- [ ] ¿La transacción cubre exactamente lo que debe ser atómico?
- [ ] ¿La operación sensible quedó auditada, con una constante y no un literal?
- [ ] ¿La autorización está en las dos capas (`SeguridadConfig` y `@PreAuthorize`)?
- [ ] ¿Toda consulta nueva usa parámetros nombrados?
- [ ] ¿El esquema replica la regla con un `CHECK`?
- [ ] ¿Hay prueba en el nivel donde vive la lógica (dominio), no solo en el de arriba?

---

## 13. Solución de problemas

Los casos marcados con **(real)** se encontraron durante la construcción del sistema y su
solución ya está aplicada en el código; se documentan porque volverán a aparecer si alguien
cambia la configuración correspondiente.

### 13.1 Arranque y contenedores

| Síntoma | Causa | Solución |
|---|---|---|
| `db-init` termina con error de conexión | SQL Server necesita unos 40 s en el primer arranque y al menos 2 GB de memoria asignados a Docker | Asignar más memoria a Docker Desktop y reintentar con `docker compose up -d db-init`. El script ya espera hasta 60 intentos (unos 3 minutos) |
| El backend reinicia en bucle | Casi siempre la base no está lista o el volumen quedó inconsistente | `docker compose logs backend` para ver la causa real. Si el volumen está corrupto: `docker compose down -v && docker compose up -d --build` |
| `El secreto JWT debe tener al menos 32 caracteres` al arrancar | `JWT_SECRET` ausente o demasiado corto. La validación de `PropiedadesAplicacion` **impide el arranque a propósito**, para no firmar con una clave débil | Definir un secreto de 32 caracteres o más: `openssl rand -base64 48` |
| `Could not resolve placeholder 'DB_PASSWORD'` | `application.yml` la declara **sin valor por omisión** deliberadamente | Definir `DB_PASSWORD`. En Docker ya viene del compose; en local hay que exportarla |
| `host not found in upstream "backend"` al levantar el frontend **(real)** | Nginx resuelve los nombres de los `upstream` **al arrancar**; si el contenedor del backend aún no existía, el proceso moría | Ya resuelto: `nginx.conf` usa `resolver 127.0.0.11` y una **variable** en `proxy_pass`, de modo que la resolución se aplaza a cada petición. No revertir ese patrón a un nombre literal |
| El *healthcheck* del frontend falla con `connection refused` aunque el sitio sirva bien **(real)** | Dentro del contenedor, `localhost` resuelve primero a `::1` (IPv6) y Nginx escucha en IPv4 | Ya resuelto: la sonda usa `http://127.0.0.1/salud`, no `http://localhost/salud`. La misma razón obliga a `ipv6=off` en el `resolver` |
| `docker compose up` no levanta el servicio `capturas` | Está bajo el perfil `herramientas` a propósito | `docker compose --profile herramientas run --rm capturas` |
| `bad interpreter: /bin/bash^M` al ejecutar un `.sh` | El repositorio se clonó en Windows y Git convirtió los finales de línea a CRLF | Ya previsto por `.gitattributes` (`*.sh text eol=lf`). Si el archivo ya está mal: `git rm --cached -r . && git reset --hard` |

### 13.2 Persistencia y base de datos

| Síntoma | Causa | Solución |
|---|---|---|
| `A result set was generated for update` en la **primera escritura** tras arrancar con una base nueva **(real)** | Flyway ejecutaba las migraciones tomando una conexión del **mismo pool de HikariCP** que usa Hibernate y la devolvía con la respuesta de la última sentencia sin consumir. Hibernate reutilizaba esa conexión física y su primer `executeUpdate` fallaba. Se manifestaba solo con la base recién creada —al insertar los usuarios iniciales—, porque en los reinicios posteriores esos usuarios ya existían y no se ejecutaba ninguna escritura | Ya resuelto: Flyway tiene **conexión propia** mediante `spring.flyway.url`, `user` y `password`, así que crea y cierra su conexión sin tocar el pool. Si se quitaran esas tres claves, el error volvería en el siguiente despliegue desde cero |
| `The column "saldo_pendiente" cannot be modified` | Alguien mapeó en JPA la columna calculada `PERSISTED` | Ya resuelto: `PrestamoEntidad` **no la mapea**. El saldo se expone desde el dominio con `Prestamo.getSaldoPendiente()`. Si hiciera falta leerla desde JPA, mapearla con `insertable = false, updatable = false` |
| `Schema-validation: missing column ...` al arrancar | `ddl-auto: validate` detectó que el mapeo JPA y el esquema real no coinciden. **Es el comportamiento correcto**: avisa en el arranque en lugar de fallar en producción | Añadir la migración que falta (`V6__...` o la siguiente libre) o corregir la entidad. Nunca poner `ddl-auto: update` para «arreglarlo» |
| `Validate failed: Migration checksum mismatch` | Se editó un script que ya estaba aplicado | Revertir la edición y crear una versión nueva (`V6`, `V7`, …). Si es un entorno de desarrollo desechable: `docker compose down -v` |
| `Login failed for user 'chn_app'` | No se ejecutó `00_crear_base_datos.sql`, o `DB_PASSWORD` no coincide con la del login | Revisar `docker compose logs db-init` y la variable de entorno |
| `Cannot open database "CHN_Prestamos"` | La base no existe | Ejecutar `00_crear_base_datos.sql` (lo hace `db-init`) |
| `The database ... is not empty` en el primer arranque | Los scripts se ejecutaron a mano antes que Flyway | Ya previsto: `baseline-on-migrate: true` |
| `THROW 50002` o `50003` al aplicar `V900` | Los datos de demostración no cuadraron, normalmente por una base con datos previos que reutilizan los mismos correlativos | Cargar la demostración sobre una base limpia: `docker compose down -v` |
| No aparecen los datos de prueba | `APP_DATOS_DEMO` no está en `true`, **o** el volumen ya tenía la migración aplicada | Ajustar la variable **y** recrear con `docker compose down -v`, porque Flyway no reaplica una versión ya registrada |
| `The ALTER TABLE statement conflicted with the CHECK constraint "ck_solicitudes_tipo"` al aplicar `V5` | Una solicitud tiene en `tipo_prestamo` un texto que no es del catálogo ni siquiera en mayúsculas (con acentos, por ejemplo), cargado fuera de la aplicación. `V5` corrige solo mayúsculas y minúsculas, y lo demás lo deja fallar a propósito | Corregir la fila a mano con el nombre exacto del tipo (`PERSONAL`, `HIPOTECARIO`, `VEHICULAR`, `EMPRESARIAL` o `EDUCATIVO`) y volver a arrancar el backend |
| La recaudación mensual del tablero sale en cero aunque la base tiene pagos | La serie cubre solo los 12 meses que terminan en el mes en curso según `RelojPort`. Los pagos de la demostración son de marzo a junio de 2026: desde junio de 2027 quedan todos fuera de la ventana | No es un error. `totalRecuperado` sigue sumando toda la historia. Los pagos que se registren desde la aplicación sí aparecen en el mes en curso |
| `The conversion of a datetime2 data type ...` o fechas desplazadas un día **(real)** | Los contenedores arrancan en UTC. Un pago registrado a las 23:00 en Guatemala se guardaría con la fecha del día siguiente | Ya resuelto en tres niveles: bloque `static` de `PrestamosApplication` (`TimeZone.setDefault`), `hibernate.jdbc.time_zone: America/Guatemala` y `TZ` en los contenedores. `RelojSistema` además trunca a segundos, porque las columnas son `DATETIME2(0)` |
| Un correlativo tiene huecos (`SC-001-2026-000004-7` tras el `000002`) | **No es un error.** La secuencia entrega el valor aunque la transacción se revierta | Es el comportamiento deseado: un hueco es auditable, un número repetido no |

### 13.3 API y seguridad

| Síntoma | Causa | Solución |
|---|---|---|
| `401` en todas las llamadas después de un rato | El token expiró (8 horas por omisión) | Cerrar sesión y volver a entrar. Para ajustar la vigencia: `JWT_EXPIRACION_MINUTOS` |
| `401` inmediato tras reiniciar el backend | Al reiniciar se deriva la clave de firma otra vez; si `JWT_SECRET` cambió, los tokens anteriores ya no validan | Fijar `JWT_SECRET` en `.env` para que sea estable entre reinicios |
| `429` al intentar entrar | 10 intentos de inicio de sesión desde la misma IP en 5 minutos | Esperar la ventana; la respuesta incluye `Retry-After`. Ajustable en `app.seguridad.login` |
| `La cuenta está bloqueada temporalmente` | 5 intentos fallidos consecutivos sobre ese usuario | Esperar 15 minutos, o quitar el bloqueo en la base: `UPDATE dbo.usuarios SET bloqueado_hasta = NULL, intentos_fallidos = 0 WHERE username = 'admin';` |
| `403` con un token válido | El rol no cubre la operación | Revisar la [matriz de permisos](#72-autorización). El mensaje es genérico a propósito: no revela qué rol haría falta |
| Un usuario `user` aparece en el log con una contraseña aleatoria | Se quitó la exclusión de `UserDetailsServiceAutoConfiguration` | Restaurar el `exclude` en `@SpringBootApplication`. Ese usuario de respaldo **no debe existir** |
| `409 DUPLICADO` sin mensaje claro | Lo detectó la base (índice único o llave foránea) y no el caso de uso. El detalle **no viaja al cliente a propósito**, porque revelaría el esquema | El nombre de la restricción está en el log del backend, nivel `WARN` |
| `400` al listar con `tamano=200` | El parámetro admite de 1 a 100 (`@Min(1) @Max(100)`) | Paginar en varias llamadas |
| `400` con `estado=INVENTADO` | El valor no corresponde a ningún miembro del enum | Usar `EN_PROCESO`, `APROBADA` o `RECHAZADA` (o `VIGENTE` / `LIQUIDADO` en préstamos) |
| **`400` al aplicar un filtro de búsqueda** | Un criterio incoherente es un error del solicitante y **no** una búsqueda sin resultados, así que la API lo rechaza en lugar de devolver `200` con una lista vacía ([3.5-j](#35-decisiones-de-diseño-justificación-y-contrapartida)) | Leer el campo `mensaje` de la respuesta, que dice exactamente cuál es el problema. Son tres familias: **rango invertido** (`nacimientoDesde` posterior a `nacimientoHasta`, `fechaDesde` posterior a `fechaHasta`…), **mínimo mayor que el máximo** (`montoMinimo`, `saldoMinimo`, `plazoMinimo`) y **valor mal formado**. El catálogo completo con los mensajes está en [5.2](#52-filtros-de-búsqueda-de-los-listados) |
| `400` con `nacimientoDesde=31-01-2026` | Las fechas de los filtros viajan en ISO `aaaa-MM-dd`, no en el formato visible `dd/mm/aaaa`. El `dd/mm/aaaa` es **solo de presentación** y no sale del navegador ([8.4](#84-el-selector-de-fechas)) | Enviar `2026-01-31`. El mensaje ya nombra el parámetro y el formato esperado; `ManejadorExcepcionesGlobal` distingue además si lo esperado era una fecha, un decimal, un entero, un booleano o un enum |
| Un filtro no parece surtir efecto y `totalElementos` no cambia | Casi siempre el criterio se envió vacío (`?estado=&clienteId=`), y vacío significa «sin ese filtro» | En la interfaz lo evita `parametrosLimpios(...)` de `clienteHttp`, que descarta los criterios vacíos antes de armar la URL. Desde `curl` o Postman, omitir el parámetro en lugar de mandarlo en blanco |
| `409` al aprobar o rechazar una solicitud | Ya fue resuelta; los estados `APROBADA` y `RECHAZADA` son finales | Con los datos de demostración, usar la solicitud **7**, **8**, **9** o **10**, que están `EN_PROCESO`. Las 1 a 6 ya están resueltas |
| `409` al registrar un pago | El monto excede el saldo pendiente, o el préstamo ya está liquidado | El mensaje incluye el saldo disponible. Con los datos de demostración, el préstamo **1** y el **4** tienen saldo vivo |
| Swagger UI carga en blanco | Se unificaron las cadenas de seguridad y la política `default-src 'none'` de la API bloqueó sus scripts y estilos | Mantener la cadena separada `cadenaDocumentacion` con `@Order(1)` y su propia CSP |
| Error de CORS en el navegador | El frontend está llamando a la API en otro origen (por ejemplo directo al 8081) y ese origen no está en la lista blanca | Añadirlo a `CORS_ORIGENES`, o mejor, usar el proxy (Vite o Nginx) para que el origen sea único |
| `502 Bad Gateway` solo en `/api` | El sitio levantó pero el backend todavía no responde | Es el comportamiento esperado del patrón de resolución diferida. Esperar a que el backend esté sano: `docker compose ps` |
| `/api/...` devuelve HTML en lugar de JSON | Se añadió `error_page 404 /index.html` en Nginx, y eso convierte los errores del proxy en respuestas HTML | No declarar `error_page 404`. El `try_files` del `location /` ya cubre el enrutado del SPA |
| Un archivo de `/assets/` devuelve `index.html` | Se cambió el `try_files $uri =404` del bloque `/assets/` | Mantener el `=404`: un recurso inexistente debe fallar como 404 y no entregar HTML donde el navegador espera JavaScript o CSS |
| `index.html` se sirve sin `Content-Security-Policy` **(real)** | En Nginx, si un `location` declara aunque sea un `add_header`, deja de heredar **todos** los del bloque padre | Ya resuelto: las cabeceras de seguridad están repetidas en cada `location` que tiene su propio `Cache-Control`. La repetición es deliberada, no un descuido |

### 13.4 Frontend y desarrollo

| Síntoma | Causa | Solución |
|---|---|---|
| `npm run dev` levanta pero las llamadas fallan con 404 | El backend no está corriendo en el 8081, o se cambió el `target` del proxy | Levantar el backend y revisar `vite.config.js` |
| La sesión se pierde al recargar la página | Es lo esperado si se cerró la pestaña: se usa `sessionStorage` a propósito. Si se pierde con la pestaña abierta, el navegador está bloqueando el almacenamiento (modo privado o política de sitio) | La aplicación sigue funcionando, solo que la sesión no persiste. Todos los accesos van dentro de `try`/`catch` |
| La aplicación se recarga completa al navegar | Se usó `<a href>` en lugar de `<Link>` de react-router | Sustituir por `<Link>`: conserva el foco y el estado |
| Al recargar `/prestamos/5` directamente sale un 404 | Falta el `try_files $uri $uri/ /index.html` del bloque `location /` | Ya está en `nginx.conf`. En desarrollo Vite lo resuelve solo |
| Se ve el aviso flotante **y** el error junto al campo | `useAvisoDeError` debe omitir el aviso cuando el error trae `errores` por campo | Usar `useAvisoDeError`, que ya lo implementa, en lugar de llamar a `notificar` directamente |
| `TypeError: Cannot read properties of undefined (reading 'value')` al teclear en el buscador **(real)** | `BarraBusqueda` **no** sigue el contrato de los `Campo*`: su `onChange` entrega el **texto ya extraído** (`onChange(valor, evento)`), no el evento. La pantalla lo trataba como evento y hacía `e.target.value` sobre una cadena, así que `e.target` era `undefined` y la primera tecla rompía el render | Ya resuelto: se le pasa el *setter* tal cual (`onChange={setBusqueda}`). **Al escribir una pantalla nueva conviene mirar la firma antes de suponerla**: `CampoTexto`, `CampoNumero`, `CampoFecha` y `CampoSelect` emiten el evento (`{ target: { name, value } }`, que es lo que espera `useFormulario`); `BarraBusqueda` emite el valor. La excepción es deliberada —permite `onChange={setTexto}` sin envoltorio— y está anotada en la cabecera del componente y en [8.2](#82-catálogo-de-los-31-componentes-reutilizables) |
| Al escribir en un filtro de texto se dispara una petición por tecla | Se está enviando `filtros.valores` en lugar de `filtros.aplicados`, o la clave no se declaró en `clavesConRetardo` | Enviar siempre `aplicados` y declarar en `clavesConRetardo` todo criterio que el usuario **teclee**. Los que vienen de un desplegable o del selector de fechas se aplican de inmediato a propósito ([8.6](#86-los-filtros-en-la-interfaz)) |
| El listado se queda en una página vacía tras cambiar un filtro | El hook del listado no reinició `pagina` al cambiar `filtros.aplicados` | Los cinco hooks de listado ya lo hacen con un `useEffect` sobre `[filtros.aplicados]` que llama a `setPagina(0)`: la página 5 del resultado anterior no significa nada para el criterio nuevo. Si se agrega un listado, replicar ese reinicio |
| El panel del calendario aparece recortado dentro de un modal | Se quitó el `createPortal` de `CampoFecha`, o se envolvió el panel en un contenedor con `overflow` | Mantener el portal a `document.body` y el posicionamiento a mano. Es la razón de que exista `--capa-popover` en `tokens.css` ([8.4](#84-el-selector-de-fechas)) |
| Pulsar `Esc` en la lista de un desplegable cierra también el modal que lo contiene | Se quitó el `stopPropagation()` del `Escape` en `CampoSelect`, así que el evento sigue subiendo hasta el `Modal` | Mantenerlo: con el panel abierto, el `Escape` cierra **solo** la lista y devuelve el foco al disparador. Es la misma decisión que en `CampoFecha`, y el caso real es el desplegable de préstamo de `PagoFormulario` ([8.5](#85-el-desplegable-de-opciones)) |
| Un guion de Playwright falla al elegir en un desplegable («element is not a `<select>`») | Se llamó a `selectOption()`, que solo funciona con el `<select>` nativo. `CampoSelect` es un `listbox` propio en un portal | Pulsar el disparador y después la fila por su `data-valor`, y comprobar la selección con el `data-valor` del disparador. Los ayudantes de `tools/capturas/capturar.mjs` ya lo hacen así ([11.6](#116-el-guion-de-capturas-y-el-desplegable-propio)) |
| El *bundle* de producción crece mucho | Se agregó una dependencia pesada | El proyecto mantiene solo 4 dependencias de producción a propósito. `manualChunks` ya separa las librerías del código propio |
| `mvn test` falla pidiendo una base de datos | Se ejecutó sin el perfil `test`, o se quitó la configuración de Surefire | El `pom.xml` fija `spring.profiles.active=test`, que excluye la autoconfiguración de `DataSource`, JPA y Flyway |
| Las reglas de ArchUnit pasan «en verde» sospechosamente rápido **(real)** | Con `@AnalyzeClasses`, la resolución por cargador de clases puede no encontrar ninguna clase bajo el classpath de Surefire, y las reglas pasan sin comprobar nada | Ya resuelto: se usa un `ClassFileImporter` explícito y la primera prueba valida que el conjunto importado no esté vacío y tenga más de 20 clases de dominio |

---

## 14. Estructura del repositorio

```
Examen_Practico_CHN/
│
├── docker-compose.yml              Orquestación: sqlserver → db-init → backend → frontend
│                                   (+ perfil "herramientas" para las capturas)
├── .env.example                    Plantilla de variables y secretos, documentada
├── .gitignore                      Excluye .env, *.pem, *.key, target/, node_modules/, dist/
├── .gitattributes                  Fuerza LF en *.sh, *.sql y Dockerfile: los scripts corren
│                                   en contenedores Linux aunque se clone en Windows
├── .dockerignore                   Contexto de construcción mínimo; excluye secretos e imágenes
├── README.md                       Puesta en marcha, alcance funcional e índice de documentos
│
├── database/                       TRANSACT-SQL — única fuente de verdad del esquema
│   ├── README.md                       Diccionario de datos, orden de ejecución, política de
│   │                                   borrado, inventario de objetos y consultas de verificación
│   ├── diagrama-entidad-relacion.md    Diagrama ER en Mermaid y DBML, con reglas de integridad
│   ├── 00_crear_base_datos.sql         Base + login + usuario de aplicación (db_owner de una
│   │                                   sola base). Requiere permisos de servidor
│   ├── init/
│   │   └── inicializar.sh              Espera a SQL Server y ejecuta el script anterior.
│   │                                   Lo invoca el servicio db-init
│   ├── migration/                      Migraciones que aplica Flyway SIEMPRE
│   │   ├── V1__esquema_tablas.sql          6 tablas, CHECK, FK, 16 índices, 3 secuencias
│   │   ├── V2__vistas_funciones_procedimientos.sql
│   │   │                                   2 funciones, 3 vistas, 2 procedimientos, 1 trigger
│   │   ├── V3__datos_iniciales.sql         Tabla parametros y sus 8 parámetros de negocio
│   │   ├── V4__vistas_tablero.sql          2 vistas: series del tablero (cartera por tipo
│   │   │                                   y recaudación mensual)
│   │   └── V5__endurecer_ck_solicitudes_tipo.sql
│   │                                       ck_solicitudes_tipo con cotejo binario
│   └── demo/
│       └── V900__datos_demo.sql        8 clientes, 10 solicitudes, 4 préstamos, 7 pagos.
│                                       Solo con APP_DATOS_DEMO=true. Verifica su propio cuadre
│
├── backend/                        API REST — Java 21, Spring Boot 3.4, arquitectura hexagonal
│   ├── pom.xml                         Dependencias y copia de ../database al classpath
│   ├── Dockerfile                      Multietapa; las pruebas bloquean la imagen; usuario no root
│   └── src/
│       ├── main/
│       │   ├── resources/
│       │   │   └── application.yml      Configuración + perfil "test". Ningún secreto real
│       │   └── java/gt/gob/chn/prestamos/
│       │       ├── PrestamosApplication.java    Arranque; fija la zona antes de crear el DataSource
│       │       │
│       │       ├── domain/                      NÚCLEO PURO — cero framework (vigilado por ArchUnit)
│       │       │   ├── model/                       20 clases: agregados, series del tablero,
│       │       │   │                                NumeroDocumento, 6 enums, Montos, Validaciones
│       │       │   │   ├── consulta/                9 clases: PaginaDominio, los 5 filtros y 3 detalles
│       │       │   │   └── reporte/                 7 tipos: DocumentoReporte, ColumnaReporte, FilaReporte,
│       │       │   │                                ValorCelda (sellada), ParDato, FormatoReporte,
│       │       │   │                                ArchivoGenerado
│       │       │   ├── exception/                   DomainException + 5 concretas, cada una con su código
│       │       │   ├── service/                     CalculadoraAmortizacion, EvaluadorCapacidadPago (+4)
│       │       │   └── port/
│       │       │       ├── in/                      8 interfaces de caso de uso
│       │       │       │   └── command/             9 records de comando
│       │       │       └── out/                     12 interfaces: repositorios, auditoría,
│       │       │                                    correlativo, reloj, codificador, token,
│       │       │                                    generador de reportes
│       │       │
│       │       ├── application/usecase/         ORQUESTACIÓN Y TRANSACCIONES
│       │       │                                    9 clases: los 8 servicios + AccionesAuditoria
│       │       │
│       │       └── infrastructure/              ADAPTADORES Y ENSAMBLADO
│       │           ├── adapter/
│       │           │   ├── in/web/                  7 controladores, 8 DTO de petición,
│       │           │   │                            18 de respuesta, 10 mapeadores,
│       │           │   │                            ExtractorContextoOperacion,
│       │           │   │                            ManejadorExcepcionesGlobal
│       │           │   └── out/
│       │           │       ├── persistencia/        6 entidades JPA, 7 repositorios Spring Data,
│       │           │       │                        6 proyecciones, 6 mapeadores, 8 adaptadores,
│       │           │       │                        PaginacionJpa y CriteriosJpa (normalización
│       │           │       │                        de los criterios de búsqueda)
│       │           │       ├── reporte/             GeneradorReportePdf, GeneradorReporteExcel,
│       │           │       │                        FormatoValores y PaletaReporte
│       │           │       └── seguridad/           ProveedorJwt, DatosToken, JwtAutenticacionFiltro,
│       │           │                                LimitadorLoginFiltro,
│       │           │                                CodificadorContrasenaBCrypt, RelojSistema
│       │           └── config/                      PropiedadesAplicacion (validada), SeguridadConfig,
│       │                                            CasosDeUsoConfig, OpenApiConfig,
│       │                                            CargadorUsuariosIniciales
│       │
│       └── test/java/gt/gob/chn/prestamos/       262 pruebas en 26 clases
│           ├── domain/model/                        ClienteTest, SolicitudPrestamoTest,
│           │                                         PrestamoTest, UsuarioTest,
│           │                                         NumeroDocumentoTest, ResumenGeneralTest,
│           │                                         CarteraPorTipoTest,
│           │                                         RecaudacionMensualTest (89)
│           │   ├── consulta/                        FiltrosTest: los 5 records de filtro
│           │   │                                     en 5 clases anidadas (39)
│           │   └── reporte/                         DocumentoReporteTest, en 4 clases
│           │                                         anidadas (19)
│           ├── domain/service/                      CalculadoraAmortizacionTest,
│           │                                         EvaluadorCapacidadPagoTest (17)
│           ├── application/usecase/                 6 servicios, entre ellos
│           │                                         ConsultarResumenServiceTest (47)
│           ├── arquitectura/
│           │   └── ArquitecturaHexagonalTest.java   12 pruebas de dependencia entre capas
│           └── infrastructure/adapter/
│               ├── in/web/                          ClienteControladorTest,
│               │                                     PagoControladorTest,
│               │                                     AuditoriaControladorTest,
│               │                                     PrestamoReportesControladorTest,
│               │                                     ResumenControladorTest (36)
│               └── out/persistencia/                CorrelativoAdaptadorTest,
│                                                     NumeracionDatosDemoTest (3)
│
├── frontend/                       APLICACIÓN WEB — React 18 + Vite 5
│   ├── package.json                    4 dependencias de producción, 2 de desarrollo
│   ├── vite.config.js                  Proxy /api → 8081, división de chunks, sin sourcemaps
│   ├── index.html                      Documento raíz (lang="es")
│   ├── nginx.conf                      SPA, compresión, proxy /api con resolución diferida,
│   │                                   cabeceras de seguridad repetidas por location
│   ├── Dockerfile                      Multietapa: node:20-alpine → nginx:1.27-alpine
│   ├── .env.example                    VITE_API_URL, con la advertencia sobre secretos
│   ├── .dockerignore
│   └── src/
│       ├── main.jsx                    Monta los proveedores y el enrutador
│       ├── App.jsx                     Mapa de rutas; /auditoria exige VER_AUDITORIA
│       ├── dominio/                    LÓGICA PURA: catalogos, formato, validaciones,
│       │                               amortizacion, fechas (formato dd/mm/aaaa y rejilla
│       │                               del mes) y acciones (color e icono por acción)
│       ├── aplicacion/                 AutenticacionContexto, NotificacionContexto y 8 hooks
│       │                               (los 6 de caso de uso, useFormulario y useFiltros)
│       ├── infraestructura/            clienteHttp (única instancia de axios) + 7 adaptadores;
│       │                               descargas.js (reportes PDF/Excel como Blob, con el
│       │                               token); almacenSesion (sessionStorage con try/catch)
│       ├── estilos/                    tokens.css (único lugar con colores, 106 variables),
│       │                               animaciones.css (15 keyframes + prefers-reduced-motion)
│       │                               y global.css
│       └── ui/
│           ├── componentes/            31 componentes reutilizables propios, con su CSS,
│           │                           contextoCampo.js y el barrel index.js. Los últimos:
│           │                           las 6 piezas de las gráficas del tablero, con
│           │                           graficas.js, useAnchoContenedor y useMarcasGrafica
│           ├── disenio/                AppLayout, BarraLateral, BarraSuperior, RutaProtegida
│           └── paginas/                13 pantallas
│
├── docs/                           DOCUMENTACIÓN
│   ├── MANUAL_TECNICO.md               Este documento
│   ├── MANUAL_USUARIO.md               Guía funcional pantalla por pantalla
│   └── img/                            Las 25 capturas anotadas del manual de usuario,
│                                       generadas por tools/capturas, el diagrama ER en SVG
│                                       y el informe JSON de la generación
│
└── tools/                          UTILIDADES DE APOYO (no forman parte del producto)
    ├── pruebas/
    │   ├── prueba-api.sh               Prueba de humo de la API: 57 comprobaciones en
    │   │                               8 grupos. Crea y borra lo que necesita
    │   └── prueba-filtros.sh           Prueba de humo de los filtros: 59 comprobaciones
    │                                   en 5 bloques, solo de lectura. Lee los totales de
    │                                   la propia API, así que no caduca al crecer los datos
    └── capturas/
        ├── README.md
        ├── package.json
        ├── capturar.mjs                Genera las capturas anotadas con Playwright
        └── diagnostico.mjs             Apoyo para depurar la generación de capturas
```

### Qué mirar primero, según el interés

| Si le interesa… | Empiece por |
|---|---|
| Levantar el sistema | `README.md`, y luego [9](#9-despliegue) de este documento |
| El modelo de datos | `database/README.md` y `database/diagrama-entidad-relacion.md` |
| Las reglas de negocio | `backend/src/main/java/.../domain/model/` y `domain/service/` |
| La arquitectura y su verificación | `backend/src/test/java/.../arquitectura/ArquitecturaHexagonalTest.java` |
| El contrato de la API | <http://localhost:8081/swagger-ui.html> con el sistema levantado |
| La seguridad | `infrastructure/config/SeguridadConfig.java`, `adapter/out/seguridad/` y `frontend/nginx.conf` |
| El sistema de diseño (color, movimiento, fechas, desplegables) | `frontend/src/estilos/tokens.css`, `estilos/animaciones.css`, `dominio/acciones.js` y `dominio/fechas.js`, con [8.3](#83-sistema-de-diseño-tokens-color-y-movimiento), [8.4](#84-el-selector-de-fechas) y [8.5](#85-el-desplegable-de-opciones) |
| Los filtros de búsqueda | [5.2](#52-filtros-de-búsqueda-de-los-listados) para el contrato HTTP, `domain/model/consulta/` para su validación y [8.6](#86-los-filtros-en-la-interfaz) para la interfaz |
| Verificar que todo funciona | `cd backend && mvn test`, `bash tools/pruebas/prueba-api.sh` y `bash tools/pruebas/prueba-filtros.sh` |
