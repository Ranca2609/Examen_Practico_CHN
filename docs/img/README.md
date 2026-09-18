# Imágenes de la documentación

Esta carpeta contiene el material gráfico del proyecto:

- **25 capturas de pantalla** (`01-…` a `25-…`) que ilustran el manual de usuario.
- **`informe-capturas.json`**, el registro de la última generación de esas capturas.
- **`diagrama-entidad-relacion.svg`**, el diagrama del modelo de datos, mantenido a mano.

---

## Las capturas no son maquetas

Ninguna imagen de esta carpeta está dibujada, retocada ni montada en una herramienta
de diseño. Todas se obtienen **recorriendo el sistema real en ejecución** con el guion
[`tools/capturas/capturar.mjs`](../../tools/capturas/capturar.mjs), que usa Playwright
para:

1. Abrir la aplicación web e **iniciar sesión** con un usuario de verdad.
2. **Ejecutar el flujo completo** contra la API y la base de datos: dar de alta un
   cliente, crear una solicitud, simular la cuota, aprobarla, registrar un pago,
   abrir el selector de fecha y aplicar filtros reales sobre un listado…
3. Inyectar en la propia página una capa de anotaciones (círculos numerados sobre los
   elementos señalados y una leyenda con los pasos).
4. Fotografiar la pantalla resultante.

Cada elemento señalable de la interfaz lleva un atributo `data-captura="…"`
(por ejemplo `login-enviar`, `clientes-nuevo`, `form-pago-monto`) y el guion se ancla a
ese atributo, no a coordenadas. Si un atributo cambia de nombre, la captura afectada se
omite, el aviso queda registrado en `informe-capturas.json` y el resto del proceso
continúa.

> **Importante:** como el guion opera sobre el sistema real, **modifica los datos de
> demostración** (crea un cliente, crea y resuelve una solicitud y registra un pago).
> Ejecútalo solo contra el entorno de demostración, nunca contra datos reales.

---

## Cómo volver a generarlas

Con el entorno levantado (`docker compose up -d --build`), desde la raíz del
repositorio:

```bash
docker compose --profile herramientas run --rm capturas
```

No hace falta instalar nada: el servicio `capturas` del `docker-compose.yml` usa la
imagen oficial de Playwright, ataca al servicio `frontend` por la red interna y escribe
el resultado directamente en esta carpeta (`docs/img/`).

Para ejecutarlo en local con Node 20 o superior (opción alternativa, detallada en
[`tools/capturas/README.md`](../../tools/capturas/README.md)):

```bash
cd tools/capturas
npm install
npx playwright install chromium
BASE_URL=http://localhost:8080 USUARIO=admin CONTRASENA='Chn2026*Demo' SALIDA=../../docs/img npm run capturar
```

---

## Las 25 capturas

Las 21 primeras existían ya y **se regeneraron con el rediseño** de la interfaz (avisos a
todo color, botones coloreados por su funcionalidad y panel de filtros en los listados).
`22`, `23` y `24` llegaron con ese rediseño, y `25` con las gráficas del tablero, que además
añadieron dos marcas a `02`. El número de archivo indica el orden en que se agregó cada
captura, no su orden en el manual ni en el recorrido: el selector de fecha (`22`) se fotografía
durante el alta de cliente, el aviso de éxito (`23`) justo después de guardarla, el panel de
filtros (`24`) al final del recorrido, ya con datos suficientes para que el filtrado se note,
y las gráficas (`25`) justo después del tablero, antes de que el recorrido agregue datos.

| Archivo | Qué muestra |
| --- | --- |
| `01-inicio-sesion.png` | Pantalla de acceso: campos de usuario y contraseña, botón «Ingresar» y el recuadro con los usuarios de demostración. |
| `02-tablero.png` | Tablero principal: indicadores generales de la cartera, menú de navegación, usuario y rol activos, cierre de sesión y la primera fila de gráficas (la dona de *Solicitudes por estado* y el panel *Cartera* con su medidor de recuperación). |
| `03-clientes-listado.png` | Listado paginado de clientes con la búsqueda rápida por nombre, DPI o correo, el panel de filtros plegado y las acciones coloreadas de cada fila. |
| `04-cliente-nuevo.png` | Formulario de alta de cliente ya relleno: nombre y apellido, DPI de 13 dígitos, fecha de nacimiento, dirección, correo y teléfono de 8 dígitos. |
| `05-cliente-ficha.png` | Ficha completa del cliente, abierta con el icono «Ver» de su fila. |
| `06-cliente-eliminar.png` | Diálogo de confirmación del borrado, con la advertencia de que arrastra las solicitudes, los préstamos y los pagos del cliente. |
| `07-solicitudes-listado.png` | Listado de solicitudes con el panel de filtros por estado (en proceso, aprobada, rechazada) y por cliente, y los conteos por estado pulsables. |
| `08-solicitud-nueva.png` | Nueva solicitud con el panel de simulación: cuota mensual del sistema francés, intereses totales y evaluación de la capacidad de pago. |
| `09-solicitud-detalle.png` | Detalle de una solicitud en proceso, con las condiciones pedidas y los botones de aprobar y rechazar. |
| `10-solicitud-rechazo.png` | Formulario de rechazo con el motivo obligatorio (entre 10 y 500 caracteres). |
| `11-solicitud-aprobacion.png` | Formulario de aprobación: monto, plazo y tasa aprobados (nunca por encima de lo solicitado) y observaciones. |
| `12-solicitud-resuelta.png` | La misma solicitud ya resuelta: fecha, usuario que resolvió y condiciones aprobadas. |
| `13-prestamos-listado.png` | Listado de préstamos con el filtro vigente/liquidado, los rangos de monto y de saldo pendiente, y el avance de pago de cada uno. |
| `14-prestamo-detalle.png` | Detalle del préstamo: saldo pendiente calculado, avance, pestañas de amortización e historial, y acceso al registro de pagos. |
| `15-prestamo-amortizacion.png` | Plan de amortización cuota por cuota: abono a capital, intereses y saldo, con la última cuota ajustada a saldo cero. |
| `16-pago-registrar.png` | Registro de un pago en efectivo: préstamo seleccionado con su saldo actual, monto y observaciones. |
| `17-pago-comprobante.png` | Comprobante del pago con el número de recibo y el saldo resultante, listo para imprimir. |
| `18-pagos-listado.png` | Listado global de pagos con el saldo anterior y el saldo posterior de cada recibo. |
| `19-prestamo-historial.png` | Pestaña de historial del préstamo: los pagos aplicados y su efecto sobre el saldo. |
| `20-auditoria.png` | Bitácora de auditoría (solo visible para el rol ADMIN) con usuario, acción, entidad, dirección IP y fecha, y su panel de filtros. |
| `21-vista-movil.png` | El listado de clientes en un viewport de 414 × 896 px, para comprobar el diseño adaptable. |
| `22-selector-fecha.png` | **(Nueva)** El calendario de tres columnas abierto sobre el formulario de alta de cliente: años desplazables, meses en rejilla de 2 × 6 y días con la semana empezando en lunes. Señala los cuatro pasos de elegir una fecha (escribir o pulsar el icono, elegir el año, elegir el mes, pulsar el día). |
| `23-aviso-exito.png` | **(Nueva)** El aviso verde de «Cliente registrado» recién guardado, con el degradado de su tono, el icono, el botón de cerrar y la barra que se vacía durante sus segundos de vida. |
| `24-filtros-busqueda.png` | **(Nueva)** El panel de filtros de clientes desplegado, con un rango de fechas de nacimiento y el estado aplicados, las dos pastillas visibles, el contador «2 filtros aplicados» y el botón «Limpiar filtros». |
| `25-tablero-graficas.png` | **(Nueva)** La segunda fila de gráficas del tablero, desplazada bajo la barra superior: *Recaudación mensual* con el tooltip de la columna más alta a la vista, el mes en curso, el botón «Ver tabla» y *Cartera por tipo de préstamo*. |

---

## `informe-capturas.json`

El guion escribe este archivo al terminar, con el resultado exacto de la **última
ejecución**:

| Campo | Contenido |
| --- | --- |
| `generado` | Marca de tiempo ISO-8601 del momento en que terminó la ejecución. |
| `baseUrl` | Dirección de la aplicación que se recorrió (por ejemplo `http://frontend:80`). |
| `capturas` | Lista de los 25 archivos PNG efectivamente escritos, en el orden del recorrido (que no es el orden numérico: `25` sale justo después de `02`, `22` y `23` durante el alta de cliente, `24` casi al final y `21` —la vista móvil— en último lugar). |
| `avisos` | Anclas `data-captura` que no se encontraron. Una lista vacía significa que el recorrido se completó sin incidencias. |

Sirve para comprobar de un vistazo si las imágenes del manual corresponden al estado
actual de la interfaz: si `avisos` no está vacío, alguna captura quedó sin generar.

---

## `diagrama-entidad-relacion.svg`

El diagrama entidad-relación es la **única pieza de esta carpeta que no se genera**:
está **escrito a mano** en SVG, elemento por elemento, y se mantiene en paralelo con la
migración [`database/migration/V1__esquema_tablas.sql`](../../database/migration/V1__esquema_tablas.sql).

- Sin dependencias: ningún generador de diagramas, ninguna librería, ninguna tipografía
  externa. Usa las fuentes del sistema y se abre en cualquier navegador o visor de SVG.
- `viewBox="0 0 1420 1260"`, así que escala sin recortes entre 900 y 1600 px de ancho.
- Muestra las 7 tablas del esquema `dbo` con el tipo Transact-SQL y los marcadores
  PK / FK / UK / NN de cada columna, las 4 relaciones en notación de pata de gallo
  (crow's foot) y la nota sobre `ON DELETE NO ACTION`.

**Si cambia el esquema, hay que actualizarlo a mano.** Conviene revisar que el alto de
cada recuadro siga cuadrando con su número de columnas: la retícula es de 26 px para la
cabecera más 18 px por columna, es decir `alto = 26 + 18 × columnas`.
