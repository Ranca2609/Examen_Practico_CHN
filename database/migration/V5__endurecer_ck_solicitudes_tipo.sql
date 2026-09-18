-- El cotejo CI_AI dejaba pasar 'personal' o 'Personal', que rompen el mapeo a enum en Java;
-- BIN2 solo acepta el nombre exacto del catalogo.

-- Solo se corrige la capitalizacion; cualquier otra variante hace fallar el CHECK y detiene la migracion.
UPDATE dbo.solicitudes_prestamo
SET    tipo_prestamo = UPPER(tipo_prestamo)
WHERE  tipo_prestamo COLLATE Latin1_General_BIN2
       NOT IN (N'PERSONAL', N'HIPOTECARIO', N'VEHICULAR', N'EMPRESARIAL', N'EDUCATIVO');

ALTER TABLE dbo.solicitudes_prestamo DROP CONSTRAINT IF EXISTS ck_solicitudes_tipo;

ALTER TABLE dbo.solicitudes_prestamo WITH CHECK
    ADD CONSTRAINT ck_solicitudes_tipo CHECK (
        tipo_prestamo COLLATE Latin1_General_BIN2
            IN (N'PERSONAL', N'HIPOTECARIO', N'VEHICULAR', N'EMPRESARIAL', N'EDUCATIVO'));
