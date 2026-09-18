-- Las filas se relacionan por DPI y correlativo, nunca por id: los IDENTITY los asigna el motor.
-- Todo el cuerpo es un solo lote (sin GO) porque comparte variables de tabla entre bloques.

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @insertados INT;

-- Clientes
DECLARE @clientes TABLE
(
    orden               INT             NOT NULL,
    nombre              NVARCHAR(60)    NOT NULL,
    apellido            NVARCHAR(60)    NOT NULL,
    dpi                 NVARCHAR(13)    NOT NULL,
    fecha_nacimiento    DATE            NOT NULL,
    direccion           NVARCHAR(200)   NOT NULL,
    correo              NVARCHAR(120)   NOT NULL,
    telefono            NVARCHAR(8)     NOT NULL
);

INSERT INTO @clientes (orden, nombre, apellido, dpi, fecha_nacimiento, direccion, correo, telefono)
VALUES
    (1, N'Maria Jose',      N'Ramirez Lopez',      N'1985043210101', '1990-04-12',
        N'3a Avenida 12-45, Zona 10, Ciudad de Guatemala',                  N'maria.ramirez@correo.gt',     N'55012345'),
    (2, N'Carlos Enrique',  N'Morales Chavez',     N'2456789010102', '1985-08-23',
        N'7a Calle 8-19, Zona 1, Ciudad de Guatemala',                      N'carlos.morales@correo.gt',    N'42315678'),
    (3, N'Ana Lucia',       N'Gonzalez Perez',     N'1874563210103', '1993-01-30',
        N'Calzada Roosevelt 24-50, Zona 11, Ciudad de Guatemala',           N'ana.gonzalez@correo.gt',      N'31245567'),
    (4, N'Jorge Alberto',   N'Tzoc Batz',          N'3012456780104', '1988-11-05',
        N'4a Avenida 3-21, Zona 3, Quetzaltenango',                         N'jorge.tzoc@correo.gt',        N'78451236'),
    (5, N'Sofia Alejandra', N'Castillo Herrera',   N'2789456120105', '1996-06-18',
        N'Boulevard Vista Hermosa 15-30, Zona 15, Ciudad de Guatemala',     N'sofia.castillo@correo.gt',    N'57894123'),
    (6, N'Luis Fernando',   N'Ixcoy Marroquin',    N'1956784320106', '1979-02-27',
        N'2a Calle 5-14, Colonia El Rosario, Zona 2, Mixco',                N'luis.ixcoy@correo.gt',        N'41236789'),
    (7, N'Gabriela Maria',  N'Sandoval Rios',      N'2634781290107', '1991-09-09',
        N'6a Avenida 10-05, Zona 9, Ciudad de Guatemala',                   N'gabriela.sandoval@correo.gt', N'36987412'),
    (8, N'Rodrigo Andres',  N'Villagran Pineda',   N'3145672890108', '1983-12-14',
        N'Km 15.5 Carretera a El Salvador, Santa Catarina Pinula',          N'rodrigo.villagran@correo.gt', N'54127896');

-- Fecha escalonada un dia por cliente para que el orden por antiguedad sea estable en las capturas.
INSERT INTO dbo.clientes
    (nombre, apellido, numero_identificacion, fecha_nacimiento, direccion,
     correo_electronico, telefono, activo, fecha_creacion, fecha_modificacion)
SELECT
    d.nombre, d.apellido, d.dpi, d.fecha_nacimiento, d.direccion,
    d.correo, d.telefono, 1,
    DATEADD(HOUR, 8, DATEADD(DAY, d.orden, CAST('2026-01-05' AS DATETIME2(0)))),
    NULL
FROM @clientes d
WHERE NOT EXISTS (SELECT 1 FROM dbo.clientes c WHERE c.numero_identificacion = d.dpi);

SET @insertados = @@ROWCOUNT;
PRINT CONCAT(N'(demo) Clientes insertados: ', @insertados);

-- Solicitudes
DECLARE @solicitudes TABLE
(
    numero              NVARCHAR(25)    NOT NULL,
    dpi                 NVARCHAR(13)    NOT NULL,
    monto               DECIMAL(15,2)   NOT NULL,
    plazo               INT             NOT NULL,
    tasa                DECIMAL(5,2)    NOT NULL,
    tipo                NVARCHAR(20)    NOT NULL,
    destino             NVARCHAR(200)   NOT NULL,
    ingreso             DECIMAL(15,2)   NOT NULL,
    estado              NVARCHAR(15)    NOT NULL,
    fecha_solicitud     DATETIME2(0)    NOT NULL,
    observaciones       NVARCHAR(500)   NULL,
    fecha_resolucion    DATETIME2(0)    NULL,
    usuario_resolucion  NVARCHAR(50)    NULL,
    monto_aprobado      DECIMAL(15,2)   NULL,
    plazo_aprobado      INT             NULL,
    tasa_aprobada       DECIMAL(5,2)    NULL,
    motivo              NVARCHAR(500)   NULL
);

INSERT INTO @solicitudes
    (numero, dpi, monto, plazo, tasa, tipo, destino, ingreso, estado, fecha_solicitud,
     observaciones, fecha_resolucion, usuario_resolucion, monto_aprobado, plazo_aprobado, tasa_aprobada, motivo)
VALUES
    -- Aprobadas
    (N'SC-001-2026-000001-3', N'1985043210101',   75000.00,  24, 14.50, N'PERSONAL',
     N'Consolidacion de deudas de tarjetas de credito', 12500.00, N'APROBADA', '2026-01-20T09:15:00',
     N'Cliente con historial crediticio limpio en el sistema.', '2026-02-05T11:30:00', N'analista',
     70000.00, 24, 14.50, N'Aprobada con monto ajustado: la capacidad de pago soporta la cuota resultante.'),

    (N'SC-001-2026-000002-1', N'2456789010102',  450000.00,  60, 11.00, N'VEHICULAR',
     N'Compra de vehiculo nuevo para uso familiar', 22000.00, N'APROBADA', '2026-02-10T10:00:00',
     N'Se presento cotizacion del distribuidor autorizado.', '2026-02-27T15:45:00', N'analista',
     450000.00, 60, 11.00, N'Aprobada por el monto total solicitado; garantia sobre el vehiculo.'),

    (N'SC-001-2026-000003-9', N'1874563210103',  120000.00,  36, 13.25, N'EDUCATIVO',
     N'Maestria en administracion de empresas', 15800.00, N'APROBADA', '2026-03-12T08:40:00',
     N'Adjunta carta de aceptacion de la universidad.', '2026-04-10T09:20:00', N'analista',
     110000.00, 36, 13.25, N'Aprobada con monto ajustado al plan de estudios presentado.'),

    (N'SC-001-2026-000004-7', N'2789456120105',  850000.00, 120,  9.75, N'HIPOTECARIO',
     N'Compra de vivienda en zona 15 de la capital', 38000.00, N'APROBADA', '2026-04-22T14:05:00',
     N'Avaluo del inmueble entregado y aprobado por el area de riesgos.', '2026-06-02T16:10:00', N'analista',
     800000.00, 120, 9.75, N'Aprobada al 94% del valor solicitado segun avaluo del inmueble.'),

    -- Rechazadas
    (N'SC-001-2026-000005-4', N'3012456780104',  300000.00,  48, 12.50, N'EMPRESARIAL',
     N'Capital de trabajo para taller textil en Quetzaltenango', 9500.00, N'RECHAZADA', '2026-05-06T11:20:00',
     N'Primera solicitud del cliente.', '2026-05-15T10:05:00', N'analista',
     NULL, NULL, NULL,
     N'La cuota estimada compromete mas del 40% del ingreso mensual declarado, por lo que no cumple la politica de endeudamiento vigente.'),

    (N'SC-001-2026-000006-2', N'1956784320106',   60000.00,  18, 15.00, N'PERSONAL',
     N'Remodelacion de vivienda familiar en Mixco', 4200.00, N'RECHAZADA', '2026-06-18T09:50:00',
     NULL, '2026-06-25T12:40:00', N'analista',
     NULL, NULL, NULL,
     N'Ingreso mensual declarado insuficiente para el monto y plazo solicitados; se sugiere reingresar la solicitud por un monto menor.'),

    -- En proceso
    (N'SC-001-2026-000007-0', N'2634781290107',   95000.00,  30, 13.75, N'PERSONAL',
     N'Gastos medicos y tratamiento de un familiar', 14200.00, N'EN_PROCESO', '2026-08-28T10:15:00',
     N'Pendiente de adjuntar constancia laboral actualizada.', NULL, NULL, NULL, NULL, NULL, NULL),

    (N'SC-001-2026-000008-8', N'3145672890108', 1200000.00, 180, 10.25, N'HIPOTECARIO',
     N'Construccion de casa en Santa Catarina Pinula', 52000.00, N'EN_PROCESO', '2026-09-02T11:45:00',
     N'En espera del avaluo del terreno.', NULL, NULL, NULL, NULL, NULL, NULL),

    (N'SC-001-2026-000009-6', N'2456789010102',  180000.00,  36, 12.00, N'EMPRESARIAL',
     N'Ampliacion de local comercial en zona 1', 22000.00, N'EN_PROCESO', '2026-09-08T08:55:00',
     N'Cliente con un prestamo vehicular vigente y al dia.', NULL, NULL, NULL, NULL, NULL, NULL),

    (N'SC-001-2026-000010-4', N'1874563210103',   55000.00,  24, 14.00, N'EDUCATIVO',
     N'Diplomado internacional en finanzas corporativas', 15800.00, N'EN_PROCESO', '2026-09-12T15:30:00',
     N'Segunda solicitud educativa de la misma clienta.', NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO dbo.solicitudes_prestamo
    (numero_solicitud, cliente_id, monto_solicitado, plazo_meses, tasa_interes_anual, tipo_prestamo,
     destino, ingreso_mensual_declarado, estado, fecha_solicitud, observaciones,
     fecha_resolucion, usuario_resolucion, monto_aprobado, plazo_aprobado_meses, tasa_aprobada, motivo_resolucion)
SELECT
    d.numero, c.id, d.monto, d.plazo, d.tasa, d.tipo,
    d.destino, d.ingreso, d.estado, d.fecha_solicitud, d.observaciones,
    d.fecha_resolucion, d.usuario_resolucion, d.monto_aprobado, d.plazo_aprobado, d.tasa_aprobada, d.motivo
FROM @solicitudes d
    INNER JOIN dbo.clientes c ON c.numero_identificacion = d.dpi
WHERE NOT EXISTS (SELECT 1 FROM dbo.solicitudes_prestamo s WHERE s.numero_solicitud = d.numero);

SET @insertados = @@ROWCOUNT;
PRINT CONCAT(N'(demo) Solicitudes insertadas: ', @insertados);

-- Prestamos: importes calculados con las mismas funciones que replica el backend, nunca a mano.
DECLARE @prestamos TABLE
(
    numero              NVARCHAR(25)    NOT NULL,
    numero_solicitud    NVARCHAR(25)    NOT NULL,
    fecha_desembolso    DATE            NOT NULL
);

INSERT INTO @prestamos (numero, numero_solicitud, fecha_desembolso)
VALUES
    (N'PR-001-2026-000001-9', N'SC-001-2026-000001-3', '2026-02-10'),
    (N'PR-001-2026-000002-7', N'SC-001-2026-000002-1', '2026-03-05'),
    (N'PR-001-2026-000003-5', N'SC-001-2026-000003-9', '2026-04-20'),
    (N'PR-001-2026-000004-3', N'SC-001-2026-000004-7', '2026-06-15');

INSERT INTO dbo.prestamos
    (numero_prestamo, solicitud_id, cliente_id, monto_aprobado, plazo_meses, tasa_interes_anual,
     cuota_mensual, monto_total_a_pagar, total_pagado, estado, fecha_desembolso, fecha_vencimiento, fecha_creacion)
SELECT
    d.numero,
    s.id,
    s.cliente_id,
    s.monto_aprobado,
    s.plazo_aprobado_meses,
    s.tasa_aprobada,
    q.cuota,
    -- Suma real del plan, no cuota * plazo: difieren en centavos por el ajuste de la ultima cuota.
    q.total,
    0,
    N'VIGENTE',
    d.fecha_desembolso,
    DATEADD(MONTH, s.plazo_aprobado_meses, d.fecha_desembolso),
    DATEADD(HOUR, 9, CAST(d.fecha_desembolso AS DATETIME2(0)))
FROM @prestamos d
    INNER JOIN dbo.solicitudes_prestamo s ON s.numero_solicitud = d.numero_solicitud
    CROSS APPLY (SELECT
        dbo.fn_calcular_cuota(s.monto_aprobado, s.tasa_aprobada, s.plazo_aprobado_meses) AS cuota,
        dbo.fn_total_plan(s.monto_aprobado, s.tasa_aprobada, s.plazo_aprobado_meses)     AS total
    ) q
WHERE s.estado = N'APROBADA'
  AND q.cuota IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM dbo.prestamos p WHERE p.numero_prestamo = d.numero OR p.solicitud_id = s.id);

SET @insertados = @@ROWCOUNT;
PRINT CONCAT(N'(demo) Prestamos insertados: ', @insertados);

-- Pagos: PR-...04-3 queda sin pagos a proposito para mostrar el caso "sin movimientos".
DECLARE @pagos TABLE
(
    numero_recibo       NVARCHAR(25)    NOT NULL,
    numero_prestamo     NVARCHAR(25)    NOT NULL,
    orden               INT             NOT NULL,   -- mes de pago contado desde el desembolso
    multiplicador       DECIMAL(5,2)    NOT NULL,   -- cuantas cuotas cubre el recibo
    observaciones       NVARCHAR(300)   NULL
);

INSERT INTO @pagos (numero_recibo, numero_prestamo, orden, multiplicador, observaciones)
VALUES
    (N'RC-001-2026-000001-4', N'PR-001-2026-000001-9', 1, 1.00, N'Pago de cuota en ventanilla, agencia central.'),
    (N'RC-001-2026-000002-2', N'PR-001-2026-000001-9', 2, 1.00, N'Pago de cuota en ventanilla, agencia central.'),
    (N'RC-001-2026-000003-0', N'PR-001-2026-000001-9', 3, 2.00, N'Abono extraordinario equivalente a dos cuotas.'),
    (N'RC-001-2026-000004-8', N'PR-001-2026-000002-7', 1, 1.00, N'Pago de cuota, agencia zona 1.'),
    (N'RC-001-2026-000005-5', N'PR-001-2026-000002-7', 2, 1.00, N'Pago de cuota, agencia zona 1.'),
    (N'RC-001-2026-000006-3', N'PR-001-2026-000003-5', 1, 1.00, N'Pago de cuota, agencia zona 11.'),
    (N'RC-001-2026-000007-1', N'PR-001-2026-000003-5', 2, 1.50, N'Pago de cuota mas abono parcial a capital.');

WITH base AS
(
    SELECT
        d.numero_recibo,
        d.orden,
        d.observaciones,
        p.id                                                            AS prestamo_id,
        p.monto_total_a_pagar,
        CAST(ROUND(p.cuota_mensual * d.multiplicador, 2) AS DECIMAL(15,2)) AS monto,
        DATEADD(HOUR, 10, CAST(DATEADD(MONTH, d.orden, p.fecha_desembolso) AS DATETIME2(0))) AS fecha_pago
    FROM @pagos d
        INNER JOIN dbo.prestamos p ON p.numero_prestamo = d.numero_prestamo
),
acumulado AS
(
    -- La ventana excluye la fila actual: es lo abonado antes de emitir este recibo.
    SELECT
        b.*,
        ISNULL(SUM(b.monto) OVER (PARTITION BY b.prestamo_id
                                  ORDER BY b.orden
                                  ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS pagado_previo
    FROM base b
)
INSERT INTO dbo.pagos
    (numero_recibo, prestamo_id, monto, fecha_pago, forma_pago,
     saldo_anterior, saldo_posterior, usuario_registro, observaciones)
SELECT
    a.numero_recibo,
    a.prestamo_id,
    a.monto,
    a.fecha_pago,
    N'EFECTIVO',
    a.monto_total_a_pagar - a.pagado_previo,
    a.monto_total_a_pagar - a.pagado_previo - a.monto,
    N'cajero',
    a.observaciones
FROM acumulado a
WHERE NOT EXISTS (SELECT 1 FROM dbo.pagos ex WHERE ex.numero_recibo = a.numero_recibo);

SET @insertados = @@ROWCOUNT;
PRINT CONCAT(N'(demo) Pagos insertados: ', @insertados);

-- total_pagado se reconstruye desde los pagos, que son la fuente de verdad.
UPDATE p
SET total_pagado = t.total,
    estado = CASE WHEN t.total >= p.monto_total_a_pagar THEN N'LIQUIDADO' ELSE N'VIGENTE' END
FROM dbo.prestamos p
    CROSS APPLY (SELECT CAST(ISNULL(SUM(pg.monto), 0) AS DECIMAL(15,2)) AS total
                 FROM dbo.pagos pg WHERE pg.prestamo_id = p.id) t
WHERE p.numero_prestamo IN (SELECT numero FROM @prestamos)
  AND (p.total_pagado <> t.total
       OR p.estado <> CASE WHEN t.total >= p.monto_total_a_pagar THEN N'LIQUIDADO' ELSE N'VIGENTE' END);

PRINT CONCAT(N'(demo) Prestamos recalculados: ', @@ROWCOUNT);

-- Si algo no cuadra, la migracion falla en vez de dejar datos incoherentes.
IF EXISTS
(
    SELECT 1
    FROM dbo.prestamos p
        CROSS APPLY (SELECT ISNULL(SUM(pg.monto), 0) AS total FROM dbo.pagos pg WHERE pg.prestamo_id = p.id) t
    WHERE p.numero_prestamo IN (SELECT numero FROM @prestamos)
      AND p.total_pagado <> t.total
)
BEGIN
    THROW 50002, N'Datos demo inconsistentes: total_pagado no coincide con la suma de pagos.', 1;
END

IF EXISTS
(
    SELECT 1
    FROM dbo.pagos pg
    WHERE pg.saldo_posterior <> pg.saldo_anterior - pg.monto
)
BEGIN
    THROW 50003, N'Datos demo inconsistentes: los saldos de los recibos no cuadran.', 1;
END

-- Adelanta las secuencias tras los correlativos de la demo, sin retrocederlas si la aplicacion ya avanzo.
IF (SELECT CAST(current_value AS BIGINT) FROM sys.sequences
     WHERE name = N'seq_solicitud' AND schema_id = SCHEMA_ID(N'dbo')) < 11
    ALTER SEQUENCE dbo.seq_solicitud RESTART WITH 11;

IF (SELECT CAST(current_value AS BIGINT) FROM sys.sequences
     WHERE name = N'seq_prestamo' AND schema_id = SCHEMA_ID(N'dbo')) < 5
    ALTER SEQUENCE dbo.seq_prestamo RESTART WITH 5;

IF (SELECT CAST(current_value AS BIGINT) FROM sys.sequences
     WHERE name = N'seq_recibo' AND schema_id = SCHEMA_ID(N'dbo')) < 8
    ALTER SEQUENCE dbo.seq_recibo RESTART WITH 8;

PRINT N'(demo) Secuencias de correlativos ajustadas.';
PRINT N'(demo) Carga de datos de prueba finalizada.';
GO
