#!/usr/bin/env bash
# Uso: [API=http://host:8081/api/v1] bash tools/pruebas/prueba-filtros.sh  (con el sistema levantado)
API=${API:-http://localhost:8081/api/v1}
ok=0; fallo=0

T=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","contrasena":"Chn2026*Demo"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$T" ] || { echo "sin token"; exit 1; }

# Los totales se leen de la API y no se fijan a mano: los datos crecen con cada uso del sistema.
totalDe() {
  curl -s "$API$1" -H "Authorization: Bearer $T" \
    | grep -o '"totalElementos":[0-9]*' | head -1 | cut -d: -f2
}

TOTAL_CLIENTES=$(totalDe "/clientes?tamano=1")
TOTAL_SOLICITUDES=$(totalDe "/solicitudes?tamano=1")
TOTAL_APROBADAS=$(totalDe "/solicitudes?estado=APROBADA&tamano=1")
TOTAL_PRESTAMOS=$(totalDe "/prestamos?tamano=1")
TOTAL_VIGENTES=$(totalDe "/prestamos?estado=VIGENTE&tamano=1")
TOTAL_PAGOS=$(totalDe "/pagos?tamano=1")
TOTAL_EN_PROCESO=$(totalDe "/solicitudes?estado=EN_PROCESO&tamano=1")

for par in "clientes:$TOTAL_CLIENTES" "solicitudes:$TOTAL_SOLICITUDES" \
           "aprobadas:$TOTAL_APROBADAS" "prestamos:$TOTAL_PRESTAMOS" \
           "vigentes:$TOTAL_VIGENTES" "pagos:$TOTAL_PAGOS" \
           "en proceso:$TOTAL_EN_PROCESO"; do
  if [ -z "${par#*:}" ]; then
    echo "No se pudo leer el total de ${par%%:*}: la prueba no puede afirmar nada. Se aborta." >&2
    exit 1
  fi
done

echo "(en el sistema: $TOTAL_CLIENTES clientes, $TOTAL_SOLICITUDES solicitudes," \
     "$TOTAL_PRESTAMOS prestamos, $TOTAL_PAGOS pagos)"

# probar <nombre> <esperado_http> <ruta> [<total_esperado>]
probar() {
  local n="$1" esp="$2" ruta="$3" totalEsp="$4"
  local cuerpo code total
  cuerpo=$(curl -s -w '\n%{http_code}' "$API$ruta" -H "Authorization: Bearer $T")
  code=$(printf '%s' "$cuerpo" | tail -1)
  total=$(printf '%s' "$cuerpo" | head -n -1 | sed -n 's/.*"totalElementos":\([0-9]*\).*/\1/p')
  if [ "$code" != "$esp" ]; then
    printf "  FALLO %-56s esperado=%s real=%s\n" "$n" "$esp" "$code"
    printf '%s' "$cuerpo" | head -n -1 | head -c 220; echo; fallo=$((fallo+1)); return
  fi
  if [ -n "$totalEsp" ] && [ -z "$total" ]; then
    printf "  FALLO %-56s no se pudo leer totalElementos de la respuesta\n" "$n"; fallo=$((fallo+1)); return
  fi
  if [ -n "$totalEsp" ] && [ "$total" != "$totalEsp" ]; then
    printf "  FALLO %-56s total esperado=%s real=%s\n" "$n" "$totalEsp" "$total"; fallo=$((fallo+1)); return
  fi
  printf "  OK    %-56s %s  total=%s\n" "$n" "$code" "${total:-n/a}"; ok=$((ok+1))
}

echo "=== CLIENTES ==="
probar "sin filtros" 200 "/clientes?tamano=50" "$TOTAL_CLIENTES"
probar "busqueda por apellido" 200 "/clientes?busqueda=Ramirez&tamano=50"
probar "busqueda por DPI parcial" 200 "/clientes?busqueda=1985043&tamano=50" 1
probar "busqueda por correo" 200 "/clientes?busqueda=correo.gt&tamano=50"
probar "busqueda por telefono" 200 "/clientes?busqueda=55012345&tamano=50" 1
probar "nacidos desde 1990" 200 "/clientes?nacimientoDesde=1990-01-01&tamano=50"
probar "nacidos hasta 1985" 200 "/clientes?nacimientoHasta=1985-12-31&tamano=50"
probar "rango de nacimiento 1990-1995" 200 "/clientes?nacimientoDesde=1990-01-01&nacimientoHasta=1995-12-31&tamano=50"
probar "rango de creacion amplio incluye a todos" 200 "/clientes?creacionDesde=2000-01-01&creacionHasta=2100-01-01&tamano=50" "$TOTAL_CLIENTES"
probar "rango de creacion futuro no incluye a nadie" 200 "/clientes?creacionDesde=2099-01-01&tamano=50" 0
probar "solo activos" 200 "/clientes?activo=true&tamano=50" "$TOTAL_CLIENTES"
probar "solo inactivos" 200 "/clientes?activo=false&tamano=50" 0
probar "combinado: texto + rango + activo" 200 "/clientes?busqueda=a&nacimientoDesde=1980-01-01&activo=true&tamano=50"
probar "rango invertido -> 400" 400 "/clientes?nacimientoDesde=1995-01-01&nacimientoHasta=1990-01-01"
probar "fecha mal formada -> 400" 400 "/clientes?nacimientoDesde=31-01-2026"
probar "tamano 500 -> 400" 400 "/clientes?tamano=500"

echo "=== SOLICITUDES ==="
probar "sin filtros" 200 "/solicitudes?tamano=50" "$TOTAL_SOLICITUDES"
probar "busqueda por numero" 200 "/solicitudes?busqueda=SC-001-2026-000003-9&tamano=50" 1
probar "busqueda por nombre de cliente" 200 "/solicitudes?busqueda=Morales&tamano=50"
probar "busqueda por destino" 200 "/solicitudes?busqueda=vivienda&tamano=50"
probar "estado EN_PROCESO" 200 "/solicitudes?estado=EN_PROCESO&tamano=50" "$TOTAL_EN_PROCESO"
probar "tipo HIPOTECARIO" 200 "/solicitudes?tipoPrestamo=HIPOTECARIO&tamano=50"
probar "tipo invalido -> 400" 400 "/solicitudes?tipoPrestamo=INVENTADO"
probar "monto entre 50k y 200k" 200 "/solicitudes?montoMinimo=50000&montoMaximo=200000&tamano=50"
probar "plazo entre 12 y 36" 200 "/solicitudes?plazoMinimo=12&plazoMaximo=36&tamano=50"
probar "monto invertido -> 400" 400 "/solicitudes?montoMinimo=200000&montoMaximo=50000"
probar "plazo invertido -> 400" 400 "/solicitudes?plazoMinimo=36&plazoMaximo=12"
probar "rango de fecha amplio incluye todas" 200 "/solicitudes?fechaDesde=2000-01-01&fechaHasta=2100-01-01&tamano=50" "$TOTAL_SOLICITUDES"
probar "rango de fecha futuro no incluye ninguna" 200 "/solicitudes?fechaDesde=2099-01-01&tamano=50" 0
probar "combinado: estado + monto amplio" 200 "/solicitudes?estado=APROBADA&montoMinimo=1000&tamano=50" "$TOTAL_APROBADAS"

echo "=== PRESTAMOS ==="
probar "sin filtros" 200 "/prestamos?tamano=50" "$TOTAL_PRESTAMOS"
probar "busqueda por numero de prestamo" 200 "/prestamos?busqueda=PR-001-2026-000001-9&tamano=50" 1
probar "busqueda por numero de solicitud" 200 "/prestamos?busqueda=SC-001-2026-000002-1&tamano=50" 1
probar "busqueda por cliente" 200 "/prestamos?busqueda=Ramirez&tamano=50"
probar "estado VIGENTE" 200 "/prestamos?estado=VIGENTE&tamano=50" "$TOTAL_VIGENTES"
probar "estado LIQUIDADO" 200 "/prestamos?estado=LIQUIDADO&tamano=50" 0
probar "monto aprobado desde 500k" 200 "/prestamos?montoMinimo=500000&tamano=50"
probar "saldo mayor a 500k" 200 "/prestamos?saldoMinimo=500000&tamano=50"
probar "rango de saldo amplio incluye todos" 200 "/prestamos?saldoMinimo=0&saldoMaximo=99999999&tamano=50" "$TOTAL_PRESTAMOS"
probar "rango de saldo imposible no incluye ninguno" 200 "/prestamos?saldoMinimo=99999999&tamano=50" 0
probar "rango de desembolso" 200 "/prestamos?desembolsoDesde=2026-01-01&desembolsoHasta=2026-12-31&tamano=50"
probar "rango de vencimiento" 200 "/prestamos?vencimientoDesde=2026-01-01&tamano=50"
probar "saldo invertido -> 400" 400 "/prestamos?saldoMinimo=200000&saldoMaximo=1000"

echo "=== PAGOS ==="
probar "sin filtros" 200 "/pagos?tamano=50" "$TOTAL_PAGOS"
probar "busqueda por recibo" 200 "/pagos?busqueda=RC-001-2026-000001-4&tamano=50" 1
probar "busqueda por numero de prestamo" 200 "/pagos?busqueda=PR-001-2026-000001-9&tamano=50"
probar "busqueda por cliente" 200 "/pagos?busqueda=Ramirez&tamano=50"
probar "monto entre 1000 y 10000" 200 "/pagos?montoMinimo=1000&montoMaximo=10000&tamano=50"
probar "rango de fecha amplio incluye todos" 200 "/pagos?fechaDesde=2000-01-01&fechaHasta=2100-01-01&tamano=50" "$TOTAL_PAGOS"
probar "rango de fecha futuro no incluye ninguno" 200 "/pagos?fechaDesde=2099-01-01&tamano=50" 0
probar "por usuario que registro" 200 "/pagos?usuarioRegistro=demo&tamano=50"
probar "por prestamo 1" 200 "/pagos?prestamoId=1&tamano=50"

echo "=== AUDITORIA ==="
probar "sin filtros" 200 "/auditoria?tamano=50"
probar "accion LOGIN_EXITOSO" 200 "/auditoria?accion=LOGIN_EXITOSO&tamano=50"
probar "entidad USUARIO" 200 "/auditoria?entidad=USUARIO&tamano=50"
probar "usuario admin" 200 "/auditoria?usuario=admin&tamano=50"
probar "busqueda libre" 200 "/auditoria?busqueda=rol&tamano=50"
probar "rango de fecha" 200 "/auditoria?fechaDesde=2026-01-01&fechaHasta=2026-12-31&tamano=50"
probar "fecha mal formada -> 400" 400 "/auditoria?fechaDesde=hoy"

echo
echo "=============================================================="
printf "  FILTROS: %s correctos, %s fallidos\n" "$ok" "$fallo"
echo "=============================================================="
[ "$fallo" -eq 0 ]
