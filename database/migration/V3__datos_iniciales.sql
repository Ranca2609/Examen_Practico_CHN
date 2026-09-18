-- Sin usuarios a proposito: los crea CargadorUsuariosIniciales para que ninguna
-- contrasena quede en un script versionado.

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.parametros', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.parametros
    (
        clave       NVARCHAR(50)    NOT NULL,
        valor       NVARCHAR(200)   NOT NULL,
        descripcion NVARCHAR(200)   NULL,

        CONSTRAINT pk_parametros PRIMARY KEY CLUSTERED (clave),
        CONSTRAINT ck_parametros_valor CHECK (LEN(LTRIM(RTRIM(valor))) > 0)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.parametros') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description',
        @value = N'Parametros de negocio (limites de monto, plazo y endeudamiento). No contiene datos sensibles.',
        @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'parametros';
GO

-- INSERT ... WHERE NOT EXISTS y no MERGE: re-ejecutar no revierte valores ajustados por el negocio.
INSERT INTO dbo.parametros (clave, valor, descripcion)
SELECT v.clave, v.valor, v.descripcion
FROM (VALUES
    (N'TASA_BASE_ANUAL',                 N'12.50',      N'Tasa de interes anual de referencia sugerida, en porcentaje.'),
    (N'PLAZO_MINIMO_MESES',              N'6',          N'Plazo minimo aceptado en una solicitud de prestamo.'),
    (N'PLAZO_MAXIMO_MESES',              N'360',        N'Plazo maximo aceptado en una solicitud de prestamo.'),
    (N'MONTO_MINIMO',                    N'1000.00',    N'Monto minimo que se puede solicitar, en quetzales.'),
    (N'MONTO_MAXIMO',                    N'5000000.00', N'Monto maximo que se puede solicitar, en quetzales.'),
    (N'PORCENTAJE_MAXIMO_ENDEUDAMIENTO', N'40',         N'Porcentaje maximo del ingreso mensual que puede comprometer la cuota.'),
    (N'MAXIMO_PRESTAMOS_VIGENTES',       N'3',          N'Cantidad maxima de prestamos vigentes por cliente para recomendar aprobacion.'),
    (N'MONEDA',                          N'GTQ',        N'Moneda de operacion del sistema (quetzal guatemalteco).')
) AS v (clave, valor, descripcion)
WHERE NOT EXISTS (SELECT 1 FROM dbo.parametros p WHERE p.clave = v.clave);
GO
