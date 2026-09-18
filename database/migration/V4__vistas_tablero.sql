-- Solo devuelven grupos con datos; los tipos y meses vacios los completa con ceros el dominio.

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- El saldo suma todos los estados sin filtrar: ck_prestamos_liquidado garantiza saldo cero
-- en los LIQUIDADO. El JOIN es 1 a 1 por uq_prestamos_solicitud.
CREATE OR ALTER VIEW dbo.vw_cartera_por_tipo
AS
SELECT
    s.tipo_prestamo,
    CAST(COUNT_BIG(*)               AS BIGINT)        AS cantidad_prestamos,
    CAST(SUM(p.monto_aprobado)      AS DECIMAL(18,2)) AS monto_aprobado,
    CAST(SUM(p.saldo_pendiente)     AS DECIMAL(18,2)) AS saldo_pendiente,
    CAST(SUM(p.total_pagado)        AS DECIMAL(18,2)) AS total_recuperado
FROM dbo.prestamos p
    INNER JOIN dbo.solicitudes_prestamo s ON s.id = p.solicitud_id
GROUP BY s.tipo_prestamo;
GO

-- fecha_pago se guarda en hora de Guatemala, asi que YEAR/MONTH ya agrupan en la zona del negocio.
CREATE OR ALTER VIEW dbo.vw_recaudacion_mensual
AS
SELECT
    CAST(YEAR(pg.fecha_pago)        AS INT)           AS anio,
    CAST(MONTH(pg.fecha_pago)       AS INT)           AS mes,
    CAST(COUNT_BIG(*)               AS BIGINT)        AS cantidad_pagos,
    CAST(SUM(pg.monto)              AS DECIMAL(18,2)) AS monto_recaudado
FROM dbo.pagos pg
GROUP BY YEAR(pg.fecha_pago), MONTH(pg.fecha_pago);
GO
