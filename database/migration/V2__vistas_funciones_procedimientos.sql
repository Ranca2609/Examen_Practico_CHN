SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- Replica la formula de CalculadoraAmortizacion (Java) para que SQL y la aplicacion no difieran.
CREATE OR ALTER FUNCTION dbo.fn_calcular_cuota
(
    @monto          DECIMAL(15,2),
    @tasa_anual     DECIMAL(5,2),
    @plazo_meses    INT
)
RETURNS DECIMAL(15,2)
AS
BEGIN
    -- NULL en vez de error: se usa en vistas, donde una excepcion abortaria toda la consulta.
    IF @monto IS NULL OR @monto <= 0 OR @plazo_meses IS NULL OR @plazo_meses <= 0
        RETURN NULL;

    DECLARE @tasa_mensual FLOAT = ISNULL(@tasa_anual, 0) / 100.0E0 / 12.0E0;
    DECLARE @cuota DECIMAL(15,2);

    IF @tasa_mensual <= 0
        SET @cuota = ROUND(@monto / @plazo_meses, 2);
    ELSE
        SET @cuota = ROUND(
                        @monto * @tasa_mensual
                        / (1.0E0 - POWER(1.0E0 + @tasa_mensual, -1.0E0 * @plazo_meses)),
                        2);

    -- ROUND de T-SQL equivale a HALF_UP del dominio Java.
    RETURN @cuota;
END
GO

-- Suma real del plan, no cuota * plazo: la ultima cuota absorbe el residuo del redondeo
-- y el dominio guarda esa suma en monto_total_a_pagar.
CREATE OR ALTER FUNCTION dbo.fn_total_plan
(
    @monto          DECIMAL(15,2),
    @tasa_anual     DECIMAL(5,2),
    @plazo_meses    INT
)
RETURNS DECIMAL(15,2)
AS
BEGIN
    IF @monto IS NULL OR @monto <= 0 OR @plazo_meses IS NULL OR @plazo_meses <= 0
        RETURN NULL;

    DECLARE @cuota DECIMAL(15,2) = dbo.fn_calcular_cuota(@monto, @tasa_anual, @plazo_meses);
    IF @cuota IS NULL RETURN NULL;

    DECLARE @tasa_mensual FLOAT = ISNULL(@tasa_anual, 0) / 100.0E0 / 12.0E0;
    DECLARE @saldo DECIMAL(15,2) = @monto;
    DECLARE @total DECIMAL(15,2) = 0;
    DECLARE @periodo INT = 1;
    DECLARE @interes DECIMAL(15,2);
    DECLARE @capital DECIMAL(15,2);
    DECLARE @cuota_periodo DECIMAL(15,2);

    WHILE @periodo <= @plazo_meses
    BEGIN
        SET @interes = ROUND(@saldo * @tasa_mensual, 2);
        SET @capital = ROUND(@cuota - @interes, 2);
        SET @cuota_periodo = @cuota;

        -- Mismo criterio del dominio: el ultimo periodo liquida exactamente el saldo.
        IF @periodo = @plazo_meses OR @capital >= @saldo
        BEGIN
            SET @capital = @saldo;
            SET @cuota_periodo = ROUND(@capital + @interes, 2);
        END

        SET @total = @total + @cuota_periodo;
        SET @saldo = @saldo - @capital;
        SET @periodo = @periodo + 1;
    END

    RETURN @total;
END
GO

-- Siempre una fila; ISNULL para que el tablero reciba 0.00 y no NULL con la base vacia.
CREATE OR ALTER VIEW dbo.vw_resumen_general
AS
SELECT
    CAST((SELECT COUNT_BIG(*) FROM dbo.clientes)                                        AS BIGINT)        AS total_clientes,
    CAST((SELECT COUNT_BIG(*) FROM dbo.solicitudes_prestamo WHERE estado = N'EN_PROCESO') AS BIGINT)      AS solicitudes_en_proceso,
    CAST((SELECT COUNT_BIG(*) FROM dbo.solicitudes_prestamo WHERE estado = N'APROBADA')   AS BIGINT)      AS solicitudes_aprobadas,
    CAST((SELECT COUNT_BIG(*) FROM dbo.solicitudes_prestamo WHERE estado = N'RECHAZADA')  AS BIGINT)      AS solicitudes_rechazadas,
    CAST((SELECT COUNT_BIG(*) FROM dbo.prestamos WHERE estado = N'VIGENTE')               AS BIGINT)      AS prestamos_vigentes,
    CAST((SELECT COUNT_BIG(*) FROM dbo.prestamos WHERE estado = N'LIQUIDADO')             AS BIGINT)      AS prestamos_liquidados,
    CAST(ISNULL((SELECT SUM(monto_aprobado) FROM dbo.prestamos), 0)                       AS DECIMAL(19,2)) AS monto_total_aprobado,
    CAST(ISNULL((SELECT SUM(saldo_pendiente) FROM dbo.prestamos WHERE estado = N'VIGENTE'), 0) AS DECIMAL(19,2)) AS saldo_pendiente_total,
    CAST(ISNULL((SELECT SUM(total_pagado) FROM dbo.prestamos), 0)                         AS DECIMAL(19,2)) AS total_recuperado;
GO

CREATE OR ALTER VIEW dbo.vw_prestamos_saldo
AS
SELECT
    p.id,
    p.numero_prestamo,
    s.numero_solicitud,
    p.cliente_id,
    c.nombre + N' ' + c.apellido                AS nombre_cliente,
    c.numero_identificacion                     AS identificacion_cliente,
    c.telefono                                  AS telefono_cliente,
    p.monto_aprobado,
    p.plazo_meses,
    p.tasa_interes_anual,
    p.cuota_mensual,
    p.monto_total_a_pagar,
    p.total_pagado,
    p.saldo_pendiente,
    CAST(CASE WHEN p.monto_total_a_pagar > 0
              THEN ROUND(p.total_pagado * 100.0 / p.monto_total_a_pagar, 2)
              ELSE 0 END AS DECIMAL(5,2))       AS porcentaje_pagado,
    -- Aproximacion para el tablero: cuotas equivalentes, no cuotas reales del plan.
    CAST(CASE WHEN p.cuota_mensual > 0
              THEN FLOOR(p.total_pagado / p.cuota_mensual)
              ELSE 0 END AS INT)                AS cuotas_cubiertas,
    p.estado,
    p.fecha_desembolso,
    p.fecha_vencimiento,
    DATEDIFF(DAY, p.fecha_desembolso, p.fecha_vencimiento)              AS dias_plazo_total,
    DATEDIFF(DAY, p.fecha_desembolso, CAST(SYSDATETIME() AS DATE))      AS dias_transcurridos,
    DATEDIFF(DAY, CAST(SYSDATETIME() AS DATE), p.fecha_vencimiento)     AS dias_para_vencimiento,
    p.fecha_creacion
FROM dbo.prestamos p
    INNER JOIN dbo.clientes c              ON c.id = p.cliente_id
    INNER JOIN dbo.solicitudes_prestamo s  ON s.id = p.solicitud_id;
GO

CREATE OR ALTER VIEW dbo.vw_solicitudes_detalle
AS
SELECT
    s.id,
    s.numero_solicitud,
    s.cliente_id,
    c.nombre + N' ' + c.apellido            AS nombre_cliente,
    c.numero_identificacion                 AS identificacion_cliente,
    c.correo_electronico                    AS correo_cliente,
    s.monto_solicitado,
    s.plazo_meses,
    s.tasa_interes_anual,
    s.tipo_prestamo,
    s.destino,
    s.ingreso_mensual_declarado,
    s.estado,
    s.fecha_solicitud,
    s.observaciones,
    s.fecha_resolucion,
    s.usuario_resolucion,
    s.monto_aprobado,
    s.plazo_aprobado_meses,
    s.tasa_aprobada,
    s.motivo_resolucion,
    DATEDIFF(DAY, s.fecha_solicitud, s.fecha_resolucion)    AS dias_resolucion,
    dbo.fn_calcular_cuota(
        ISNULL(s.monto_aprobado, s.monto_solicitado),
        ISNULL(s.tasa_aprobada, s.tasa_interes_anual),
        ISNULL(s.plazo_aprobado_meses, s.plazo_meses))      AS cuota_estimada,
    pr.numero_prestamo
FROM dbo.solicitudes_prestamo s
    INNER JOIN dbo.clientes c   ON c.id = s.cliente_id
    LEFT  JOIN dbo.prestamos pr ON pr.solicitud_id = s.id;
GO

-- Devuelve dos conjuntos de resultados: encabezado del prestamo e historico de pagos.
CREATE OR ALTER PROCEDURE dbo.sp_estado_cuenta_prestamo
    @prestamo_id BIGINT
AS
BEGIN
    -- Sin NOCOUNT, algunos drivers toman "n filas afectadas" como otro conjunto de resultados.
    SET NOCOUNT ON;

    DECLARE @mensaje NVARCHAR(400);

    IF @prestamo_id IS NULL OR NOT EXISTS (SELECT 1 FROM dbo.prestamos WHERE id = @prestamo_id)
    BEGIN
        SET @mensaje = CONCAT(N'No existe un prestamo con el identificador ',
                              ISNULL(CAST(@prestamo_id AS NVARCHAR(20)), N'(nulo)'),
                              N'. Verifique el numero de prestamo.');
        THROW 50001, @mensaje, 1;
    END

    SELECT
        v.id,
        v.numero_prestamo,
        v.numero_solicitud,
        v.cliente_id,
        v.nombre_cliente,
        v.identificacion_cliente,
        v.telefono_cliente,
        v.monto_aprobado,
        v.plazo_meses,
        v.tasa_interes_anual,
        v.cuota_mensual,
        v.monto_total_a_pagar,
        v.total_pagado,
        v.saldo_pendiente,
        v.porcentaje_pagado,
        v.cuotas_cubiertas,
        v.estado,
        v.fecha_desembolso,
        v.fecha_vencimiento,
        v.dias_para_vencimiento
    FROM dbo.vw_prestamos_saldo v
    WHERE v.id = @prestamo_id;

    SELECT
        pg.id,
        pg.numero_recibo,
        pg.fecha_pago,
        pg.forma_pago,
        pg.monto,
        pg.saldo_anterior,
        pg.saldo_posterior,
        pg.usuario_registro,
        pg.observaciones,
        ROW_NUMBER() OVER (ORDER BY pg.fecha_pago, pg.id) AS numero_movimiento
    FROM dbo.pagos pg
    WHERE pg.prestamo_id = @prestamo_id
    ORDER BY pg.fecha_pago, pg.id;
END
GO

CREATE OR ALTER PROCEDURE dbo.sp_historial_pagos_cliente
    @cliente_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @mensaje NVARCHAR(400);

    IF @cliente_id IS NULL OR NOT EXISTS (SELECT 1 FROM dbo.clientes WHERE id = @cliente_id)
    BEGIN
        SET @mensaje = CONCAT(N'No existe un cliente con el identificador ',
                              ISNULL(CAST(@cliente_id AS NVARCHAR(20)), N'(nulo)'),
                              N'. Verifique el numero de identificacion.');
        THROW 50001, @mensaje, 1;
    END

    SELECT
        pg.id,
        pg.numero_recibo,
        p.numero_prestamo,
        p.id                        AS prestamo_id,
        pg.fecha_pago,
        pg.forma_pago,
        pg.monto,
        pg.saldo_anterior,
        pg.saldo_posterior,
        pg.usuario_registro,
        pg.observaciones
    FROM dbo.pagos pg
        INNER JOIN dbo.prestamos p ON p.id = pg.prestamo_id
    WHERE p.cliente_id = @cliente_id
    ORDER BY pg.fecha_pago DESC, pg.id DESC;

    -- Una subconsulta por total: unir prestamos con sus pagos duplicaria importes.
    SELECT
        @cliente_id AS cliente_id,
        (SELECT COUNT_BIG(*)
           FROM dbo.pagos pg INNER JOIN dbo.prestamos p ON p.id = pg.prestamo_id
          WHERE p.cliente_id = @cliente_id)                             AS cantidad_pagos,
        (SELECT CAST(ISNULL(SUM(pg.monto), 0) AS DECIMAL(19,2))
           FROM dbo.pagos pg INNER JOIN dbo.prestamos p ON p.id = pg.prestamo_id
          WHERE p.cliente_id = @cliente_id)                             AS total_pagado,
        (SELECT CAST(ISNULL(SUM(p.saldo_pendiente), 0) AS DECIMAL(19,2))
           FROM dbo.prestamos p
          WHERE p.cliente_id = @cliente_id AND p.estado = N'VIGENTE')   AS saldo_pendiente_total,
        (SELECT COUNT_BIG(*) FROM dbo.prestamos p
          WHERE p.cliente_id = @cliente_id AND p.estado = N'VIGENTE')   AS prestamos_vigentes,
        (SELECT MAX(pg.fecha_pago)
           FROM dbo.pagos pg INNER JOIN dbo.prestamos p ON p.id = pg.prestamo_id
          WHERE p.cliente_id = @cliente_id)                             AS ultimo_pago;
END
GO

-- Deja traza aunque el borrado se haga fuera de la aplicacion (que audita aparte con usuario e IP).
CREATE OR ALTER TRIGGER dbo.tr_clientes_auditoria_delete
ON dbo.clientes
AFTER DELETE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.auditoria (usuario, accion, entidad, entidad_id, detalle, direccion_ip, fecha)
    SELECT
        N'trigger_bd',
        N'CLIENTE_ELIMINADO_BD',
        N'clientes',
        CAST(d.id AS NVARCHAR(50)),
        CONCAT(N'Cliente eliminado en base de datos: ', d.nombre, N' ', d.apellido,
               N' | DPI ', d.numero_identificacion,
               N' | correo ', d.correo_electronico,
               N' | sesion ', SUSER_SNAME()),
        NULL,
        SYSDATETIME()
    FROM deleted d;
END
GO
