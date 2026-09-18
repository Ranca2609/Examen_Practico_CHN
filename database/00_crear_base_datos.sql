-- Uso: sqlcmd -S <host>,1433 -U sa -b -I -i 00_crear_base_datos.sql  (DB_NAME, APP_USER y APP_PASSWORD se leen del entorno)
-- Requiere permisos de servidor, por eso no lo ejecuta Flyway. Es idempotente.

SET NOCOUNT ON;
GO

-- EXEC dinamico: CREATE DATABASE no admite el nombre como variable.
-- Modern_Spanish_CI_AI hace las busquedas insensibles a mayusculas y acentos.
IF DB_ID(N'$(DB_NAME)') IS NULL
BEGIN
    PRINT N'Creando base de datos $(DB_NAME)...';
    EXEC (N'CREATE DATABASE [$(DB_NAME)] COLLATE Modern_Spanish_CI_AI;');
END
ELSE
    PRINT N'La base de datos $(DB_NAME) ya existe. No se modifica.';
GO

IF EXISTS (SELECT 1 FROM sys.databases WHERE name = N'$(DB_NAME)' AND recovery_model <> 3)
BEGIN
    PRINT N'Ajustando modelo de recuperacion a SIMPLE...';
    EXEC (N'ALTER DATABASE [$(DB_NAME)] SET RECOVERY SIMPLE;');
END
GO

-- Los reportes de saldos no deben bloquear el registro de pagos.
IF EXISTS (SELECT 1 FROM sys.databases WHERE name = N'$(DB_NAME)' AND is_read_committed_snapshot_on = 0)
BEGIN
    PRINT N'Habilitando READ_COMMITTED_SNAPSHOT...';
    EXEC (N'ALTER DATABASE [$(DB_NAME)] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;');
END
GO

-- APP_PASSWORD se interpola en SQL dinamico: no puede contener comillas simples.
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(APP_USER)' AND type = 'S')
BEGIN
    PRINT N'Creando login $(APP_USER)...';
    EXEC (N'CREATE LOGIN [$(APP_USER)]
                WITH PASSWORD = ''$(APP_PASSWORD)'',
                     DEFAULT_DATABASE = [$(DB_NAME)],
                     CHECK_EXPIRATION = OFF,
                     CHECK_POLICY = ON;');
END
ELSE
BEGIN
    -- Se resincroniza para que un cambio en .env no deje al backend sin acceso.
    PRINT N'El login $(APP_USER) ya existe. Sincronizando la contrasena...';
    EXEC (N'ALTER LOGIN [$(APP_USER)] WITH PASSWORD = ''$(APP_PASSWORD)'';');
END
GO

USE [$(DB_NAME)];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(APP_USER)')
BEGIN
    PRINT N'Creando usuario $(APP_USER) en $(DB_NAME)...';
    CREATE USER [$(APP_USER)] FOR LOGIN [$(APP_USER)] WITH DEFAULT_SCHEMA = dbo;
END
ELSE
    PRINT N'El usuario $(APP_USER) ya existe en $(DB_NAME).';
GO

-- db_owner solo de esta base (Flyway crea objetos); nunca un rol de servidor.
IF ISNULL(IS_ROLEMEMBER(N'db_owner', N'$(APP_USER)'), 0) = 0
BEGIN
    PRINT N'Agregando $(APP_USER) al rol db_owner de $(DB_NAME)...';
    ALTER ROLE db_owner ADD MEMBER [$(APP_USER)];
END
ELSE
    PRINT N'$(APP_USER) ya pertenece a db_owner.';
GO

PRINT N'-----------------------------------------------------------------';
PRINT N'Preparacion terminada.';
PRINT N'Siguiente paso: arrancar el backend. Flyway aplicara en orden:';
PRINT N'  V1__esquema_tablas.sql';
PRINT N'  V2__vistas_funciones_procedimientos.sql';
PRINT N'  V3__datos_iniciales.sql';
PRINT N'  V4__vistas_tablero.sql';
PRINT N'  V5__endurecer_ck_solicitudes_tipo.sql';
PRINT N'  V900__datos_demo.sql   (solo si APP_DATOS_DEMO=true)';
PRINT N'Cadena de conexion esperada por el backend:';
PRINT N'  jdbc:sqlserver://<DB_HOST>:<DB_PORT>;databaseName=$(DB_NAME);encrypt=true;trustServerCertificate=true';
PRINT N'-----------------------------------------------------------------';
GO
