#!/usr/bin/env bash
# Uso: [API=http://host:8081/api/v1] bash tools/pruebas/prueba-api.sh  (con el sistema levantado)
API=${API:-http://localhost:8081/api/v1}
ok=0; fallo=0

probar() {
  local n="$1" esp="$2" met="$3" ruta="$4" cuerpo="$5" tok="$6"
  local args=(-s -o /tmp/resp.json -w "%{http_code}" -X "$met" "$API$ruta" -H "Content-Type: application/json")
  [ -n "$tok" ] && args+=(-H "Authorization: Bearer $tok")
  [ -n "$cuerpo" ] && args+=(-d "$cuerpo")
  local code
  code=$(curl "${args[@]}")
  if [ "$code" = "$esp" ]; then
    printf "  OK    %-52s %s\n" "$n" "$code"; ok=$((ok+1))
  else
    printf "  FALLO %-52s esperado=%s real=%s\n" "$n" "$esp" "$code"; head -c 300 /tmp/resp.json; echo; fallo=$((fallo+1))
  fi
}

jsonval() { sed -n "s/.*\"$1\":\([0-9.]*\).*/\1/p" /tmp/resp.json | head -1; }
jsonstr() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" /tmp/resp.json | head -1; }

# Luhn reimplementado aqui a proposito: la prueba no debe validar el algoritmo con el mismo codigo
# que lo implementa. Entrada: tipo en base 36 (A=10 ... Z=35) + agencia + anio + correlativo.
luhn() {
  local s=$1 suma=0 pos=0 i d
  for (( i=${#s}-1; i>=0; i-- )); do
    d=${s:i:1}
    if (( pos % 2 == 0 )); then d=$(( d*2 )); (( d > 9 )) && d=$(( d-9 )); fi
    suma=$(( suma + d )); pos=$(( pos+1 ))
  done
  echo $(( (10 - suma % 10) % 10 ))
}

numero_oficial() {
  local n="$1" valor="$2" tipo="$3" cuerpo letra
  if [[ "$valor" =~ ^($tipo)-([0-9]{3})-([0-9]{4})-([0-9]{6,9})-([0-9])$ ]]; then
    cuerpo=""
    for letra in $(echo "$tipo" | grep -o .); do cuerpo+=$(( $(printf '%d' "'$letra") - 55 )); done
    cuerpo+="${BASH_REMATCH[2]}${BASH_REMATCH[3]}${BASH_REMATCH[4]}"
    if [ "$(luhn "$cuerpo")" = "${BASH_REMATCH[5]}" ]; then
      printf "  OK    %-52s %s\n" "$n" "$valor"; ok=$((ok+1)); return
    fi
  fi
  printf "  FALLO %-52s valor=%s\n" "$n" "$valor"; fallo=$((fallo+1))
}

comparar() {
  local n="$1" esperado="$2" real="$3"
  if [ "$real" = "$esperado" ]; then
    printf "  OK    %-52s %s\n" "$n" "$real"; ok=$((ok+1))
  else
    printf "  FALLO %-52s esperado=%s real=%s\n" "$n" "$esperado" "$real"; fallo=$((fallo+1))
  fi
}

echo "=== 1. Autenticacion y JWT ==="
probar "login sin credenciales -> 400" 400 POST /auth/login "{}"
probar "login con clave incorrecta -> 401" 401 POST /auth/login '{"username":"admin","contrasena":"claveMala123"}'
TOKEN=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","contrasena":"Chn2026*Demo"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -n "$TOKEN" ]; then printf "  OK    %-52s %s\n" "login admin devuelve token" "${#TOKEN} car."; ok=$((ok+1)); else echo "  FALLO login admin sin token"; exit 1; fi
probar "perfil con token -> 200" 200 GET /auth/perfil "" "$TOKEN"
probar "peticion sin token -> 401" 401 GET /clientes ""
probar "peticion con token invalido -> 401" 401 GET /clientes "" "aa.bb.cc"

echo "=== 2. Gestion de clientes ==="
probar "listar clientes -> 200" 200 GET "/clientes?pagina=0&tamano=5" "" "$TOKEN"
probar "busqueda por texto -> 200" 200 GET "/clientes?busqueda=Mar" "" "$TOKEN"
probar "cliente inexistente -> 404" 404 GET /clientes/99999 "" "$TOKEN"
probar "alta con DPI invalido -> 400" 400 POST /clientes '{"nombre":"Ana","apellido":"Lopez","numeroIdentificacion":"123","fechaNacimiento":"1990-01-01","direccion":"Zona 1, Guatemala","correoElectronico":"a@b.com","telefono":"55512345"}' "$TOKEN"
probar "alta de menor de edad -> 400" 400 POST /clientes '{"nombre":"Ana","apellido":"Lopez","numeroIdentificacion":"1111111111111","fechaNacimiento":"2015-01-01","direccion":"Zona 1, Guatemala","correoElectronico":"menor@b.com","telefono":"55512345"}' "$TOKEN"
probar "alta valida -> 201" 201 POST /clientes '{"nombre":"Prueba","apellido":"Integracion","numeroIdentificacion":"9988776655443","fechaNacimiento":"1991-05-20","direccion":"5a Avenida 1-23, Zona 4, Guatemala","correoElectronico":"prueba.integracion@correo.gt","telefono":"55009911"}' "$TOKEN"
ID_CLI=$(jsonval id)
echo "        -> cliente id=$ID_CLI"
probar "DPI duplicado -> 409" 409 POST /clientes '{"nombre":"Otro","apellido":"Cliente","numeroIdentificacion":"9988776655443","fechaNacimiento":"1991-05-20","direccion":"5a Avenida 1-23, Zona 4","correoElectronico":"otro@correo.gt","telefono":"55009912"}' "$TOKEN"
probar "editar cliente -> 200" 200 PUT "/clientes/$ID_CLI" '{"nombre":"Prueba","apellido":"Integracion","direccion":"6a Calle 7-70, Zona 9, Guatemala","correoElectronico":"prueba.integracion@correo.gt","telefono":"55009999"}' "$TOKEN"
probar "solicitudes del cliente -> 200" 200 GET "/clientes/$ID_CLI/solicitudes" "" "$TOKEN"
probar "prestamos del cliente -> 200" 200 GET "/clientes/$ID_CLI/prestamos" "" "$TOKEN"

echo "=== 3. Solicitudes de prestamo ==="
probar "listar solicitudes -> 200" 200 GET "/solicitudes?pagina=0&tamano=5" "" "$TOKEN"
probar "filtrar por EN_PROCESO -> 200" 200 GET "/solicitudes?estado=EN_PROCESO" "" "$TOKEN"
probar "estado invalido -> 400" 400 GET "/solicitudes?estado=INVENTADO" "" "$TOKEN"
probar "simulacion -> 200" 200 POST /solicitudes/simulacion "{\"clienteId\":$ID_CLI,\"monto\":100000.00,\"plazoMeses\":12,\"tasaInteresAnual\":12.00,\"ingresoMensual\":30000.00}" "$TOKEN"
echo "        -> $(head -c 200 /tmp/resp.json)"
probar "monto fuera de rango -> 400" 400 POST /solicitudes "{\"clienteId\":$ID_CLI,\"montoSolicitado\":10.00,\"plazoMeses\":12,\"tasaInteresAnual\":12.00,\"tipoPrestamo\":\"PERSONAL\",\"destino\":\"Capital de trabajo\",\"ingresoMensualDeclarado\":9000.00}" "$TOKEN"
probar "crear solicitud -> 201" 201 POST /solicitudes "{\"clienteId\":$ID_CLI,\"montoSolicitado\":120000.00,\"plazoMeses\":24,\"tasaInteresAnual\":13.50,\"tipoPrestamo\":\"PERSONAL\",\"destino\":\"Remodelacion de vivienda\",\"ingresoMensualDeclarado\":18000.00,\"observaciones\":\"Prueba automatizada\"}" "$TOKEN"
ID_SOL=$(jsonval id)
echo "        -> solicitud id=$ID_SOL numero=$(jsonstr numeroSolicitud)"
numero_oficial "numero de solicitud SC valido" "$(jsonstr numeroSolicitud)" SC
probar "rechazo con motivo corto -> 400" 400 POST "/solicitudes/$ID_SOL/rechazar" '{"motivo":"no"}' "$TOKEN"
probar "aprobar solicitud -> 200" 200 POST "/solicitudes/$ID_SOL/aprobar" '{"montoAprobado":100000.00,"plazoAprobadoMeses":24,"tasaAprobada":13.50,"motivo":"Capacidad de pago verificada"}' "$TOKEN"
probar "aprobar una ya resuelta -> 409" 409 POST "/solicitudes/$ID_SOL/aprobar" '{"montoAprobado":100000.00}' "$TOKEN"
probar "rechazar una ya resuelta -> 409" 409 POST "/solicitudes/$ID_SOL/rechazar" '{"motivo":"Ya fue resuelta previamente"}' "$TOKEN"

echo "=== 4. Prestamos aprobados y pagos ==="
probar "listar prestamos del cliente -> 200" 200 GET "/prestamos?clienteId=$ID_CLI" "" "$TOKEN"
ID_PRE=$(jsonval id)
echo "        -> prestamo id=$ID_PRE cuota=$(jsonval cuotaMensual) saldo=$(jsonval saldoPendiente)"
numero_oficial "numero de prestamo PR valido" "$(jsonstr numeroPrestamo)" PR
probar "detalle del prestamo -> 200" 200 GET "/prestamos/$ID_PRE" "" "$TOKEN"
probar "plan de amortizacion -> 200" 200 GET "/prestamos/$ID_PRE/amortizacion" "" "$TOKEN"
echo "        -> $(grep -o '"numero":' /tmp/resp.json | wc -l) cuotas en el plan"
probar "pagos del prestamo -> 200" 200 GET "/prestamos/$ID_PRE/pagos" "" "$TOKEN"
probar "pago de monto 0 -> 400" 400 POST /pagos "{\"prestamoId\":$ID_PRE,\"monto\":0}" "$TOKEN"
probar "pago mayor al saldo -> 409" 409 POST /pagos "{\"prestamoId\":$ID_PRE,\"monto\":99999999.00}" "$TOKEN"
probar "registrar pago -> 201" 201 POST /pagos "{\"prestamoId\":$ID_PRE,\"monto\":5000.00,\"observaciones\":\"Pago de prueba automatizada\"}" "$TOKEN"
echo "        -> recibo=$(jsonstr numeroRecibo) anterior=$(jsonval saldoAnterior) posterior=$(jsonval saldoPosterior)"
numero_oficial "numero de recibo RC valido" "$(jsonstr numeroRecibo)" RC
probar "pago sobre prestamo inexistente -> 404" 404 POST /pagos '{"prestamoId":999999,"monto":100.00}' "$TOKEN"
probar "listar pagos -> 200" 200 GET "/pagos?pagina=0&tamano=5" "" "$TOKEN"

echo "=== 5. Resumen y auditoria ==="
probar "resumen del tablero -> 200" 200 GET /resumen "" "$TOKEN"
echo "        -> $(head -c 280 /tmp/resp.json)"
# Se valida la forma de las series, que es fija aunque cambien los datos.
TIPOS=$(grep -oE '"tipoPrestamo":"[A-Z_]+"' /tmp/resp.json | cut -d'"' -f4 | paste -sd, -)
comparar "cartera por tipo: 5 tipos en orden" "PERSONAL,HIPOTECARIO,VEHICULAR,EMPRESARIAL,EDUCATIVO" "$TIPOS"
PERIODOS=$(grep -oE '"periodo":"[0-9]{4}-[0-9]{2}"' /tmp/resp.json | cut -d'"' -f4)
N_PERIODOS=$(printf '%s\n' "$PERIODOS" | grep -c .)
if [ "$PERIODOS" = "$(printf '%s\n' "$PERIODOS" | LC_ALL=C sort -u)" ]; then ORDEN=ascendentes; else ORDEN=desordenados; fi
comparar "recaudacion mensual: 12 periodos AAAA-MM" "12 ascendentes" "$N_PERIODOS $ORDEN"
echo "        -> periodos $(printf '%s\n' "$PERIODOS" | head -1) a $(printf '%s\n' "$PERIODOS" | tail -1)"
probar "auditoria como ADMIN -> 200" 200 GET "/auditoria?pagina=0&tamano=5" "" "$TOKEN"

echo "=== 6. Autorizacion por rol ==="
TCAJ=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" -d '{"username":"cajero","contrasena":"Chn2026*Demo"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
TCON=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" -d '{"username":"consulta","contrasena":"Chn2026*Demo"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
probar "cajero NO ve auditoria -> 403" 403 GET "/auditoria" "" "$TCAJ"
probar "cajero NO crea clientes -> 403" 403 POST /clientes '{"nombre":"Xavier","apellido":"Yanes","numeroIdentificacion":"1231231231231","fechaNacimiento":"1990-01-01","direccion":"Zona 1 Guatemala","correoElectronico":"x@y.gt","telefono":"55511111"}' "$TCAJ"
probar "cajero SI consulta clientes -> 200" 200 GET /clientes "" "$TCAJ"
probar "cajero SI registra pagos -> 201" 201 POST /pagos "{\"prestamoId\":$ID_PRE,\"monto\":250.00}" "$TCAJ"
probar "consulta NO registra pagos -> 403" 403 POST /pagos "{\"prestamoId\":$ID_PRE,\"monto\":100.00}" "$TCON"
probar "consulta NO elimina clientes -> 403" 403 DELETE "/clientes/$ID_CLI" "" "$TCON"

echo "=== 7. Borrado en cascada del cliente ==="
probar "eliminar cliente (ADMIN) -> 204" 204 DELETE "/clientes/$ID_CLI" "" "$TOKEN"
probar "el cliente ya no existe -> 404" 404 GET "/clientes/$ID_CLI" "" "$TOKEN"
probar "su prestamo ya no existe -> 404" 404 GET "/prestamos/$ID_PRE" "" "$TOKEN"
probar "su solicitud ya no existe -> 404" 404 GET "/solicitudes/$ID_SOL" "" "$TOKEN"

echo "=== 8. Superficie publica ==="
code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health); [ "$code" = "200" ] && { echo "  OK    actuator/health -> 200"; ok=$((ok+1)); } || { echo "  FALLO actuator/health -> $code"; fallo=$((fallo+1)); }
code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/v3/api-docs); [ "$code" = "200" ] && { echo "  OK    OpenAPI v3/api-docs -> 200"; ok=$((ok+1)); } || { echo "  FALLO api-docs -> $code"; fallo=$((fallo+1)); }
code=$(curl -s -o /dev/null -w "%{http_code}" -L http://localhost:8081/swagger-ui.html); [ "$code" = "200" ] && { echo "  OK    Swagger UI -> 200"; ok=$((ok+1)); } || { echo "  FALLO swagger-ui -> $code"; fallo=$((fallo+1)); }
code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/); [ "$code" = "200" ] && { echo "  OK    aplicacion web (8080) -> 200"; ok=$((ok+1)); } || { echo "  FALLO web -> $code"; fallo=$((fallo+1)); }
code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/resumen); [ "$code" = "401" ] && { echo "  OK    proxy nginx /api protegido -> 401"; ok=$((ok+1)); } || { echo "  FALLO proxy nginx -> $code"; fallo=$((fallo+1)); }

echo
echo "=================================================="
printf "  RESULTADO: %s correctas, %s fallidas\n" "$ok" "$fallo"
echo "=================================================="
[ "$fallo" -eq 0 ]
