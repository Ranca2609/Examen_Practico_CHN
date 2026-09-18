SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- Tabla: clientes
IF OBJECT_ID(N'dbo.clientes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.clientes
    (
        id                      BIGINT          IDENTITY(1,1) NOT NULL,
        nombre                  NVARCHAR(60)    NOT NULL,
        apellido                NVARCHAR(60)    NOT NULL,
        numero_identificacion   NVARCHAR(13)    NOT NULL,
        fecha_nacimiento        DATE            NOT NULL,
        direccion               NVARCHAR(200)   NOT NULL,
        correo_electronico      NVARCHAR(120)   NOT NULL,
        telefono                NVARCHAR(8)     NOT NULL,
        activo                  BIT             NOT NULL CONSTRAINT df_clientes_activo DEFAULT (1),
        fecha_creacion          DATETIME2(0)    NOT NULL,
        fecha_modificacion      DATETIME2(0)    NULL,

        CONSTRAINT pk_clientes PRIMARY KEY CLUSTERED (id),
        CONSTRAINT uq_clientes_identificacion UNIQUE (numero_identificacion),
        CONSTRAINT uq_clientes_correo UNIQUE (correo_electronico),

        CONSTRAINT ck_clientes_identificacion
            CHECK (numero_identificacion LIKE N'[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
        CONSTRAINT ck_clientes_telefono
            CHECK (telefono LIKE N'[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
        -- Solo forma minima; el formato fino lo valida el dominio.
        CONSTRAINT ck_clientes_correo
            CHECK (correo_electronico LIKE N'%_@_%._%' AND correo_electronico NOT LIKE N'% %'),
        CONSTRAINT ck_clientes_nombre     CHECK (LEN(LTRIM(RTRIM(nombre))) >= 2),
        CONSTRAINT ck_clientes_apellido   CHECK (LEN(LTRIM(RTRIM(apellido))) >= 2),
        CONSTRAINT ck_clientes_direccion  CHECK (LEN(LTRIM(RTRIM(direccion))) >= 5),
        -- La mayoria de edad va en el dominio: depende de la fecha actual y un CHECK
        -- no deterministico solo se evaluaria al insertar.
        CONSTRAINT ck_clientes_fecha_nacimiento CHECK (fecha_nacimiento >= '1900-01-01'),
        CONSTRAINT ck_clientes_fechas
            CHECK (fecha_modificacion IS NULL OR fecha_modificacion >= fecha_creacion)
    );
END
GO

-- Tabla: usuarios
-- password_hash guarda un hash BCrypt, nunca la contrasena.
IF OBJECT_ID(N'dbo.usuarios', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.usuarios
    (
        id                  BIGINT          IDENTITY(1,1) NOT NULL,
        username            NVARCHAR(50)    NOT NULL,
        password_hash       NVARCHAR(100)   NOT NULL,
        nombre_completo     NVARCHAR(120)   NOT NULL,
        correo              NVARCHAR(120)   NOT NULL,
        rol                 NVARCHAR(15)    NOT NULL,
        activo              BIT             NOT NULL CONSTRAINT df_usuarios_activo DEFAULT (1),
        intentos_fallidos   INT             NOT NULL CONSTRAINT df_usuarios_intentos DEFAULT (0),
        bloqueado_hasta     DATETIME2(0)    NULL,
        ultimo_acceso       DATETIME2(0)    NULL,

        CONSTRAINT pk_usuarios PRIMARY KEY CLUSTERED (id),
        CONSTRAINT uq_usuarios_username UNIQUE (username),
        CONSTRAINT ck_usuarios_rol CHECK (rol IN (N'ADMIN', N'ANALISTA', N'CAJERO', N'CONSULTA')),
        CONSTRAINT ck_usuarios_username CHECK (LEN(LTRIM(RTRIM(username))) >= 3),
        CONSTRAINT ck_usuarios_intentos CHECK (intentos_fallidos >= 0),
        CONSTRAINT ck_usuarios_correo CHECK (correo LIKE N'%_@_%._%')
    );
END
GO

-- Tabla: auditoria
-- Solo insercion y consulta: nunca se actualiza ni se borra.
IF OBJECT_ID(N'dbo.auditoria', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.auditoria
    (
        id              BIGINT          IDENTITY(1,1) NOT NULL,
        usuario         NVARCHAR(50)    NOT NULL,
        accion          NVARCHAR(50)    NOT NULL,
        entidad         NVARCHAR(50)    NOT NULL,
        entidad_id      NVARCHAR(50)    NULL,
        detalle         NVARCHAR(1000)  NULL,
        direccion_ip    NVARCHAR(45)    NULL,   -- 45: longitud maxima de IPv6 textual
        fecha           DATETIME2(0)    NOT NULL,

        CONSTRAINT pk_auditoria PRIMARY KEY CLUSTERED (id)
    );
END
GO

-- Tabla: solicitudes_prestamo
IF OBJECT_ID(N'dbo.solicitudes_prestamo', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.solicitudes_prestamo
    (
        id                          BIGINT          IDENTITY(1,1) NOT NULL,
        numero_solicitud            NVARCHAR(25)    NOT NULL,
        cliente_id                  BIGINT          NOT NULL,
        monto_solicitado            DECIMAL(15,2)   NOT NULL,
        plazo_meses                 INT             NOT NULL,
        tasa_interes_anual          DECIMAL(5,2)    NOT NULL,
        tipo_prestamo               NVARCHAR(20)    NOT NULL,
        destino                     NVARCHAR(200)   NOT NULL,
        ingreso_mensual_declarado   DECIMAL(15,2)   NOT NULL,
        estado                      NVARCHAR(15)    NOT NULL,
        fecha_solicitud             DATETIME2(0)    NOT NULL,
        observaciones               NVARCHAR(500)   NULL,
        fecha_resolucion            DATETIME2(0)    NULL,
        usuario_resolucion          NVARCHAR(50)    NULL,
        monto_aprobado              DECIMAL(15,2)   NULL,
        plazo_aprobado_meses        INT             NULL,
        tasa_aprobada               DECIMAL(5,2)    NULL,
        motivo_resolucion           NVARCHAR(500)   NULL,

        CONSTRAINT pk_solicitudes PRIMARY KEY CLUSTERED (id),
        CONSTRAINT uq_solicitudes_numero UNIQUE (numero_solicitud),
        -- NO ACTION en todas las FK: SQL Server rechaza multiples rutas de cascada
        -- hacia pagos; el borrado en orden lo hace el caso de uso.
        CONSTRAINT fk_solicitudes_cliente FOREIGN KEY (cliente_id)
            REFERENCES dbo.clientes (id) ON DELETE NO ACTION ON UPDATE NO ACTION,

        -- Solo estructura; el digito verificador lo valida el dominio. BIN2 evita que
        -- el cotejo CI_AI acepte minusculas o digitos de otros alfabetos en [0-9].
        CONSTRAINT ck_solicitudes_numero CHECK (
            numero_solicitud COLLATE Latin1_General_BIN2
                LIKE N'SC-[0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]%-[0-9]'
            AND numero_solicitud COLLATE Latin1_General_BIN2 NOT LIKE N'SC-%[^0-9-]%'
            AND LEN(numero_solicitud) - LEN(REPLACE(numero_solicitud, N'-', N'')) = 4),

        CONSTRAINT ck_solicitudes_estado
            CHECK (estado IN (N'EN_PROCESO', N'APROBADA', N'RECHAZADA')),
        CONSTRAINT ck_solicitudes_tipo
            CHECK (tipo_prestamo IN (N'PERSONAL', N'HIPOTECARIO', N'VEHICULAR', N'EMPRESARIAL', N'EDUCATIVO')),
        CONSTRAINT ck_solicitudes_monto
            CHECK (monto_solicitado >= 1000.00 AND monto_solicitado <= 5000000.00),
        CONSTRAINT ck_solicitudes_plazo
            CHECK (plazo_meses >= 6 AND plazo_meses <= 360),
        CONSTRAINT ck_solicitudes_tasa
            CHECK (tasa_interes_anual >= 0.01 AND tasa_interes_anual <= 100.00),
        CONSTRAINT ck_solicitudes_ingreso
            CHECK (ingreso_mensual_declarado > 0),
        CONSTRAINT ck_solicitudes_destino
            CHECK (LEN(LTRIM(RTRIM(destino))) >= 5),
        CONSTRAINT ck_solicitudes_monto_aprobado
            CHECK (monto_aprobado IS NULL OR (monto_aprobado > 0 AND monto_aprobado <= monto_solicitado)),
        CONSTRAINT ck_solicitudes_plazo_aprobado
            CHECK (plazo_aprobado_meses IS NULL OR (plazo_aprobado_meses >= 6 AND plazo_aprobado_meses <= 360)),
        CONSTRAINT ck_solicitudes_tasa_aprobada
            CHECK (tasa_aprobada IS NULL OR (tasa_aprobada >= 0.01 AND tasa_aprobada <= 100.00)),
        CONSTRAINT ck_solicitudes_fecha_resolucion
            CHECK (fecha_resolucion IS NULL OR fecha_resolucion >= fecha_solicitud),
        CONSTRAINT ck_solicitudes_coherencia_resolucion CHECK
        (
            (   estado = N'EN_PROCESO'
                AND fecha_resolucion IS NULL AND usuario_resolucion IS NULL
                AND monto_aprobado IS NULL AND plazo_aprobado_meses IS NULL AND tasa_aprobada IS NULL )
         OR (   estado = N'APROBADA'
                AND fecha_resolucion IS NOT NULL AND usuario_resolucion IS NOT NULL
                AND monto_aprobado IS NOT NULL AND plazo_aprobado_meses IS NOT NULL AND tasa_aprobada IS NOT NULL )
         OR (   estado = N'RECHAZADA'
                AND fecha_resolucion IS NOT NULL AND usuario_resolucion IS NOT NULL
                AND motivo_resolucion IS NOT NULL AND LEN(LTRIM(RTRIM(motivo_resolucion))) >= 10
                AND monto_aprobado IS NULL AND plazo_aprobado_meses IS NULL AND tasa_aprobada IS NULL )
        )
    );
END
GO

-- Tabla: prestamos
IF OBJECT_ID(N'dbo.prestamos', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.prestamos
    (
        id                   BIGINT         IDENTITY(1,1) NOT NULL,
        numero_prestamo      NVARCHAR(25)   NOT NULL,
        solicitud_id         BIGINT         NOT NULL,
        cliente_id           BIGINT         NOT NULL,
        monto_aprobado       DECIMAL(15,2)  NOT NULL,
        plazo_meses          INT            NOT NULL,
        tasa_interes_anual   DECIMAL(5,2)   NOT NULL,
        cuota_mensual        DECIMAL(15,2)  NOT NULL,
        monto_total_a_pagar  DECIMAL(15,2)  NOT NULL,
        total_pagado         DECIMAL(15,2)  NOT NULL CONSTRAINT df_prestamos_total_pagado DEFAULT (0),
        -- Calculada para que no pueda desincronizarse; PERSISTED para indexarla.
        saldo_pendiente      AS (monto_total_a_pagar - total_pagado) PERSISTED NOT NULL,
        estado               NVARCHAR(15)   NOT NULL,
        fecha_desembolso     DATE           NOT NULL,
        fecha_vencimiento    DATE           NOT NULL,
        fecha_creacion       DATETIME2(0)   NOT NULL,

        CONSTRAINT pk_prestamos PRIMARY KEY CLUSTERED (id),
        CONSTRAINT uq_prestamos_numero UNIQUE (numero_prestamo),
        CONSTRAINT uq_prestamos_solicitud UNIQUE (solicitud_id),
        CONSTRAINT fk_prestamos_solicitud FOREIGN KEY (solicitud_id)
            REFERENCES dbo.solicitudes_prestamo (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
        CONSTRAINT fk_prestamos_cliente FOREIGN KEY (cliente_id)
            REFERENCES dbo.clientes (id) ON DELETE NO ACTION ON UPDATE NO ACTION,

        CONSTRAINT ck_prestamos_numero CHECK (
            numero_prestamo COLLATE Latin1_General_BIN2
                LIKE N'PR-[0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]%-[0-9]'
            AND numero_prestamo COLLATE Latin1_General_BIN2 NOT LIKE N'PR-%[^0-9-]%'
            AND LEN(numero_prestamo) - LEN(REPLACE(numero_prestamo, N'-', N'')) = 4),
        CONSTRAINT ck_prestamos_estado CHECK (estado IN (N'VIGENTE', N'LIQUIDADO')),
        CONSTRAINT ck_prestamos_monto CHECK (monto_aprobado > 0),
        CONSTRAINT ck_prestamos_plazo CHECK (plazo_meses >= 6 AND plazo_meses <= 360),
        CONSTRAINT ck_prestamos_tasa CHECK (tasa_interes_anual >= 0.01 AND tasa_interes_anual <= 100.00),
        CONSTRAINT ck_prestamos_cuota CHECK (cuota_mensual > 0),
        CONSTRAINT ck_prestamos_total CHECK (monto_total_a_pagar > 0),
        CONSTRAINT ck_prestamos_pagado
            CHECK (total_pagado >= 0 AND total_pagado <= monto_total_a_pagar),
        CONSTRAINT ck_prestamos_liquidado
            CHECK (estado <> N'LIQUIDADO' OR total_pagado = monto_total_a_pagar),
        CONSTRAINT ck_prestamos_vencimiento
            CHECK (fecha_vencimiento > fecha_desembolso)
    );
END
GO

-- Tabla: pagos
-- saldo_anterior/saldo_posterior congelan el recibo tal como se emitio.
IF OBJECT_ID(N'dbo.pagos', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.pagos
    (
        id                BIGINT         IDENTITY(1,1) NOT NULL,
        numero_recibo     NVARCHAR(25)   NOT NULL,
        prestamo_id       BIGINT         NOT NULL,
        monto             DECIMAL(15,2)  NOT NULL,
        fecha_pago        DATETIME2(0)   NOT NULL,
        forma_pago        NVARCHAR(15)   NOT NULL,
        saldo_anterior    DECIMAL(15,2)  NOT NULL,
        saldo_posterior   DECIMAL(15,2)  NOT NULL,
        usuario_registro  NVARCHAR(50)   NOT NULL,
        observaciones     NVARCHAR(300)  NULL,

        CONSTRAINT pk_pagos PRIMARY KEY CLUSTERED (id),
        CONSTRAINT uq_pagos_recibo UNIQUE (numero_recibo),
        CONSTRAINT fk_pagos_prestamo FOREIGN KEY (prestamo_id)
            REFERENCES dbo.prestamos (id) ON DELETE NO ACTION ON UPDATE NO ACTION,

        CONSTRAINT ck_pagos_recibo CHECK (
            numero_recibo COLLATE Latin1_General_BIN2
                LIKE N'RC-[0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]%-[0-9]'
            AND numero_recibo COLLATE Latin1_General_BIN2 NOT LIKE N'RC-%[^0-9-]%'
            AND LEN(numero_recibo) - LEN(REPLACE(numero_recibo, N'-', N'')) = 4),
        CONSTRAINT ck_pagos_forma CHECK (forma_pago IN (N'EFECTIVO')),
        CONSTRAINT ck_pagos_monto CHECK (monto > 0),
        CONSTRAINT ck_pagos_saldos_positivos CHECK (saldo_anterior >= 0 AND saldo_posterior >= 0),
        CONSTRAINT ck_pagos_aritmetica CHECK (saldo_posterior = saldo_anterior - monto)
    );
END
GO

-- Secuencias de correlativos: atomicas bajo concurrencia, a diferencia de MAX(...)+1.
IF OBJECT_ID(N'dbo.seq_solicitud', N'SO') IS NULL
    CREATE SEQUENCE dbo.seq_solicitud AS BIGINT START WITH 1 INCREMENT BY 1 MINVALUE 1 NO CYCLE CACHE 10;
GO

IF OBJECT_ID(N'dbo.seq_prestamo', N'SO') IS NULL
    CREATE SEQUENCE dbo.seq_prestamo AS BIGINT START WITH 1 INCREMENT BY 1 MINVALUE 1 NO CYCLE CACHE 10;
GO

IF OBJECT_ID(N'dbo.seq_recibo', N'SO') IS NULL
    CREATE SEQUENCE dbo.seq_recibo AS BIGINT START WITH 1 INCREMENT BY 1 MINVALUE 1 NO CYCLE CACHE 10;
GO

-- Indices
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_clientes_apellido_nombre' AND object_id = OBJECT_ID(N'dbo.clientes'))
    CREATE NONCLUSTERED INDEX ix_clientes_apellido_nombre
        ON dbo.clientes (apellido, nombre) INCLUDE (numero_identificacion, correo_electronico, activo);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_clientes_activo' AND object_id = OBJECT_ID(N'dbo.clientes'))
    CREATE NONCLUSTERED INDEX ix_clientes_activo ON dbo.clientes (activo) INCLUDE (apellido, nombre);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_clientes_fecha_creacion' AND object_id = OBJECT_ID(N'dbo.clientes'))
    CREATE NONCLUSTERED INDEX ix_clientes_fecha_creacion ON dbo.clientes (fecha_creacion DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_solicitudes_cliente' AND object_id = OBJECT_ID(N'dbo.solicitudes_prestamo'))
    CREATE NONCLUSTERED INDEX ix_solicitudes_cliente
        ON dbo.solicitudes_prestamo (cliente_id, fecha_solicitud DESC) INCLUDE (estado, monto_solicitado, tipo_prestamo);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_solicitudes_estado' AND object_id = OBJECT_ID(N'dbo.solicitudes_prestamo'))
    CREATE NONCLUSTERED INDEX ix_solicitudes_estado
        ON dbo.solicitudes_prestamo (estado) INCLUDE (cliente_id, monto_solicitado, monto_aprobado);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_solicitudes_fecha' AND object_id = OBJECT_ID(N'dbo.solicitudes_prestamo'))
    CREATE NONCLUSTERED INDEX ix_solicitudes_fecha ON dbo.solicitudes_prestamo (fecha_solicitud DESC);
GO

-- Tambien lo usa el evaluador de capacidad de pago para contar vigentes por cliente.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_prestamos_cliente_estado' AND object_id = OBJECT_ID(N'dbo.prestamos'))
    CREATE NONCLUSTERED INDEX ix_prestamos_cliente_estado
        ON dbo.prestamos (cliente_id, estado) INCLUDE (monto_total_a_pagar, total_pagado, saldo_pendiente);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_prestamos_estado' AND object_id = OBJECT_ID(N'dbo.prestamos'))
    CREATE NONCLUSTERED INDEX ix_prestamos_estado ON dbo.prestamos (estado) INCLUDE (saldo_pendiente, monto_aprobado);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_prestamos_fecha_desembolso' AND object_id = OBJECT_ID(N'dbo.prestamos'))
    CREATE NONCLUSTERED INDEX ix_prestamos_fecha_desembolso ON dbo.prestamos (fecha_desembolso DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_prestamos_fecha_vencimiento' AND object_id = OBJECT_ID(N'dbo.prestamos'))
    CREATE NONCLUSTERED INDEX ix_prestamos_fecha_vencimiento ON dbo.prestamos (fecha_vencimiento) INCLUDE (estado, cliente_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_pagos_prestamo' AND object_id = OBJECT_ID(N'dbo.pagos'))
    CREATE NONCLUSTERED INDEX ix_pagos_prestamo
        ON dbo.pagos (prestamo_id, fecha_pago DESC) INCLUDE (monto, saldo_posterior);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_pagos_fecha' AND object_id = OBJECT_ID(N'dbo.pagos'))
    CREATE NONCLUSTERED INDEX ix_pagos_fecha ON dbo.pagos (fecha_pago DESC) INCLUDE (prestamo_id, monto);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_auditoria_fecha' AND object_id = OBJECT_ID(N'dbo.auditoria'))
    CREATE NONCLUSTERED INDEX ix_auditoria_fecha ON dbo.auditoria (fecha DESC) INCLUDE (usuario, accion, entidad);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_auditoria_usuario' AND object_id = OBJECT_ID(N'dbo.auditoria'))
    CREATE NONCLUSTERED INDEX ix_auditoria_usuario ON dbo.auditoria (usuario, fecha DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_auditoria_entidad' AND object_id = OBJECT_ID(N'dbo.auditoria'))
    CREATE NONCLUSTERED INDEX ix_auditoria_entidad ON dbo.auditoria (entidad, entidad_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_usuarios_rol' AND object_id = OBJECT_ID(N'dbo.usuarios'))
    CREATE NONCLUSTERED INDEX ix_usuarios_rol ON dbo.usuarios (rol) INCLUDE (username, activo);
GO

-- Documentacion en el catalogo (extended properties)
IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.clientes') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Clientes titulares de credito. numero_identificacion es el DPI (13 digitos) y es su identificador natural.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'clientes';
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.solicitudes_prestamo') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Solicitudes de prestamo. Conserva en la misma fila la resolucion (aprobacion o rechazo) con su usuario, fecha y motivo.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'solicitudes_prestamo';
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.prestamos') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Prestamos desembolsados, uno por solicitud aprobada. saldo_pendiente es columna calculada persistida.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'prestamos';
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.prestamos') AND minor_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.prestamos') AND name = N'saldo_pendiente') AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Columna calculada PERSISTED = monto_total_a_pagar - total_pagado. No se escribe desde la aplicacion.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'prestamos',
        @level2type = N'COLUMN', @level2name = N'saldo_pendiente';
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.pagos') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Pagos aplicados a los prestamos. saldo_anterior y saldo_posterior son el corte historico del recibo.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'pagos';
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.usuarios') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Usuarios operadores del sistema. password_hash siempre almacena un hash BCrypt, nunca la contrasena.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'usuarios';
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.auditoria') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Bitacora de operaciones sensibles. Tabla de solo insercion y consulta.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'auditoria';
GO
