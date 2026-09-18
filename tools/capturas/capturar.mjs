// Uso: docker compose --profile herramientas run --rm capturas
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const USUARIO = process.env.USUARIO || 'admin';
const CONTRASENA = process.env.CONTRASENA || 'Chn2026*Demo';
const SALIDA = process.env.SALIDA || '/salida';

// Sufijo por ejecución: DPI y correo son únicos y el guion se puede repetir sobre la misma base.
const SUFIJO = String(Date.now()).slice(-6);

const avisos = [];
const capturadas = [];

const CSS_ANOTACIONES = `
#chn-capa-anotaciones { position: fixed; inset: 0; z-index: 2147483000; pointer-events: none;
  font-family: "Segoe UI", system-ui, sans-serif; }
.chn-marca { position: absolute; width: 30px; height: 30px; border-radius: 50%;
  background: #D92B1C; color: #fff; font-size: 16px; font-weight: 700; line-height: 30px;
  text-align: center; box-shadow: 0 0 0 3px rgba(255,255,255,.95), 0 2px 8px rgba(0,0,0,.35); }
.chn-recuadro { position: absolute; border: 3px solid #D92B1C; border-radius: 6px;
  box-shadow: 0 0 0 3px rgba(217,43,28,.18); }
.chn-leyenda { position: absolute; max-width: 430px; background: rgba(255,255,255,.98);
  border: 1px solid #C9D2DD; border-left: 6px solid #D92B1C; border-radius: 8px;
  padding: 14px 16px; box-shadow: 0 8px 26px rgba(0,0,0,.22); }
.chn-leyenda h4 { margin: 0 0 8px; font-size: 13px; text-transform: uppercase;
  letter-spacing: .06em; color: #0B4F8A; }
/* list-style explícito: el reset global de la aplicación quita los marcadores,
   y en la leyenda el número es justamente lo que une el texto con el círculo. */
.chn-leyenda ol { margin: 0; padding-left: 24px; list-style: decimal outside; }
.chn-leyenda li { font-size: 13.5px; line-height: 1.5; color: #1A1D21; margin-bottom: 3px;
  list-style: decimal outside; }
.chn-leyenda li::marker { color: #D92B1C; font-weight: 700; }
`;

async function limpiarAnotaciones(page) {
  await page.evaluate(() => {
    document.getElementById('chn-capa-anotaciones')?.remove();
  });
}

async function anotar(page, pasos, leyenda = 'abajo-derecha', titulo = 'Pasos a seguir') {
  await limpiarAnotaciones(page);
  const faltantes = await page.evaluate(
    ({ pasos, leyenda, titulo, css }) => {
      const estilo = document.getElementById('chn-estilo-anotaciones') || document.createElement('style');
      estilo.id = 'chn-estilo-anotaciones';
      estilo.textContent = css;
      document.head.appendChild(estilo);

      const capa = document.createElement('div');
      capa.id = 'chn-capa-anotaciones';
      document.body.appendChild(capa);

      const ausentes = [];
      const textos = [];

      pasos.forEach((paso, i) => {
        const numero = i + 1;
        textos.push(paso.texto);
        const sel = paso.sel || `[data-captura="${paso.captura}"]`;
        const el = document.querySelector(sel);
        if (!el) { ausentes.push(sel); return; }
        const r = el.getBoundingClientRect();
        // Un ancla dentro de un contenedor plegado conserva el ancho pero no el alto:
        // sin este filtro el recuadro se dibujaría sobre otro elemento.
        const estilo = window.getComputedStyle(el);
        const invisible = estilo.visibility === 'hidden'
          || estilo.display === 'none'
          || Number(estilo.opacity) === 0
          || el.offsetParent === null;
        if (r.width < 4 || r.height < 4 || invisible) {
          ausentes.push(sel + ' (no visible)');
          return;
        }

        if (paso.recuadro !== false) {
          const caja = document.createElement('div');
          caja.className = 'chn-recuadro';
          caja.style.left = `${r.left - 4}px`;
          caja.style.top = `${r.top - 4}px`;
          caja.style.width = `${r.width + 8}px`;
          caja.style.height = `${r.height + 8}px`;
          capa.appendChild(caja);
        }

        const pos = paso.posicion || 'izquierda';
        let x = r.left - 38;
        let y = r.top + r.height / 2 - 15;
        if (pos === 'derecha') { x = r.right + 10; }
        else if (pos === 'arriba') { x = r.left + r.width / 2 - 15; y = r.top - 38; }
        else if (pos === 'abajo') { x = r.left + r.width / 2 - 15; y = r.bottom + 10; }
        else if (pos === 'centro') { x = r.left + r.width / 2 - 15; y = r.top + r.height / 2 - 15; }
        else if (pos === 'esquina') { x = r.left - 15; y = r.top - 15; }
        x = Math.min(Math.max(x, 4), window.innerWidth - 34);
        y = Math.min(Math.max(y, 4), window.innerHeight - 34);

        const marca = document.createElement('div');
        marca.className = 'chn-marca';
        marca.style.left = `${x}px`;
        marca.style.top = `${y}px`;
        marca.textContent = String(numero);
        capa.appendChild(marca);
      });

      if (leyenda && textos.length) {
        const panel = document.createElement('div');
        panel.className = 'chn-leyenda';
        panel.innerHTML =
          `<h4>${titulo}</h4><ol>${textos.map((t) => `<li>${t}</li>`).join('')}</ol>`;
        capa.appendChild(panel);
        // Se posiciona después de medirlo para que no salga del lienzo.
        const alto = panel.offsetHeight;
        const ancho = panel.offsetWidth;
        const arriba = leyenda.startsWith('arriba');
        const izquierda = leyenda.endsWith('izquierda');
        panel.style.top = arriba ? '16px' : `${window.innerHeight - alto - 16}px`;
        panel.style.left = izquierda ? '16px' : `${window.innerWidth - ancho - 16}px`;
      }
      return ausentes;
    },
    { pasos, leyenda, titulo, css: CSS_ANOTACIONES },
  );
  if (faltantes.length) avisos.push(`Anclas no encontradas: ${faltantes.join(', ')}`);
}

async function capturar(page, nombre) {
  const ruta = `${SALIDA}/${nombre}.png`;
  await page.screenshot({ path: ruta });
  await limpiarAnotaciones(page);
  capturadas.push(`${nombre}.png`);
  console.log(`  ✓ ${nombre}.png`);
}

const cap = (nombre) => `[data-captura="${nombre}"]`;

async function irA(page, ruta) {
  await page.goto(`${BASE_URL}${ruta}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(700);
}

async function cerrarNotificaciones(page) {
  await page.evaluate(() => {
    document
      .querySelectorAll('.chn-notificaciones, [class*="notificacion"][class*="contenedor"]')
      .forEach((n) => { n.style.display = 'none'; });
  });
}

async function escribir(page, captura, valor) {
  const campo = page.locator(`${cap(captura)} input, ${cap(captura)} textarea, ${cap(captura)}`).first();
  await campo.fill(String(valor));
}

// CampoSelect no es un <select> nativo, así que selectOption() no sirve: hay que abrir el panel y pulsar la fila.
function disparadorDe(page, captura) {
  return page.locator(cap(captura)).first();
}

// El panel vive en un portal fuera del disparador; aria-controls evita confundirlo con el de otro campo.
async function panelDe(page, captura) {
  const idLista = await disparadorDe(page, captura).getAttribute('aria-controls');
  // El selector por atributo (y no #id) evita tener que escapar el id.
  return page.locator(idLista ? `.chn-select__panel[id="${idLista}"]` : '.chn-select__panel');
}

async function abrirSelect(page, captura) {
  const disparador = disparadorDe(page, captura);
  await disparador.waitFor();
  if ((await disparador.getAttribute('aria-expanded')) !== 'true') {
    // click() espera a que se habilite: el disparador llega deshabilitado mientras carga el catálogo.
    await disparador.click();
  }
  const panel = await panelDe(page, captura);
  await panel.waitFor();
  return { disparador, panel };
}

// Se compara data-valor y no el texto visible, que depende del idioma y el formato.
async function esperarValor(page, captura, valor) {
  await page.waitForFunction(
    ({ sel, esperado }) => document.querySelector(sel)?.getAttribute('data-valor') === esperado,
    { sel: cap(captura), esperado: String(valor) },
    { timeout: 20000 },
  );
}

async function elegir(page, captura, valor) {
  const { panel } = await abrirSelect(page, captura);
  await panel.locator(`.chn-select__opcion[data-valor="${valor}"]`).first().click();
  await panel.waitFor({ state: 'hidden' });
  await esperarValor(page, captura, valor);
}

async function elegirPrimeraOpcion(page, captura) {
  const { panel } = await abrirSelect(page, captura);
  // data-valor vacío corresponde al placeholder y a la opción «Todos».
  const primera = panel.locator('.chn-select__opcion:not([data-valor=""])').first();
  await primera.waitFor({ timeout: 20000 });
  const valor = await primera.getAttribute('data-valor');
  await primera.click();
  await panel.waitFor({ state: 'hidden' });
  await esperarValor(page, captura, valor);
  return valor;
}

// El botón del calendario es hermano del input, no descendiente del ancla.
async function abrirCalendario(page, captura) {
  await page.locator(`${cap(captura)} ~ button, .chn-fecha:has(${cap(captura)}) button`).first().click();
  await page.locator('.chn-calendario').waitFor();
  await page.waitForTimeout(500);
}

async function abrirFiltros(page) {
  const alternar = page.locator(cap('filtros-alternar'));
  await alternar.waitFor();
  if ((await alternar.getAttribute('aria-expanded')) !== 'true') {
    await alternar.click();
    await page.waitForTimeout(500);
  }
}

// Una pantalla que falla queda como aviso y no aborta el resto del guion.
async function paso(nombre, fn) {
  try {
    console.log(`▶ ${nombre}`);
    await fn();
  } catch (e) {
    const msg = `[${nombre}] ${e.message.split('\n')[0]}`;
    avisos.push(msg);
    console.warn(`  ✗ ${msg}`);
  }
}

async function main() {
  await mkdir(SALIDA, { recursive: true });
  const navegador = await chromium.launch({ args: ['--font-render-hinting=none'] });
  const contexto = await navegador.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'es-GT',
    timezoneId: 'America/Guatemala',
    reducedMotion: 'reduce',
  });
  const page = await contexto.newPage();
  page.setDefaultTimeout(20000);

  await paso('01 Inicio de sesión', async () => {
    await irA(page, '/login');
    await escribir(page, 'login-usuario', USUARIO);
    await escribir(page, 'login-contrasena', CONTRASENA);
    await anotar(page, [
      { captura: 'login-usuario', texto: 'Escriba su usuario asignado.', posicion: 'izquierda' },
      { captura: 'login-contrasena', texto: 'Escriba su contraseña.', posicion: 'izquierda' },
      { captura: 'login-enviar', texto: 'Pulse «Ingresar» para entrar al sistema.', posicion: 'derecha' },
      { captura: 'login-credenciales', texto: 'Usuarios de demostración disponibles.', posicion: 'derecha' },
    ], 'arriba-derecha', 'Iniciar sesión');
    await capturar(page, '01-inicio-sesion');
  });

  await escribir(page, 'login-usuario', USUARIO);
  await escribir(page, 'login-contrasena', CONTRASENA);
  await page.locator(cap('login-enviar')).click();
  await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 20000 });
  await page.waitForTimeout(1200);

  // Las marcas nuevas van al final para no cambiar los números 1 a 4 que cita el manual.
  await paso('02 Tablero', async () => {
    await irA(page, '/');
    // La figura de la dona existe también en estado vacío, así que la espera no se cuelga sin datos.
    await page.locator(`${cap('tablero-grafica-estados')} .chn-grafica`).first().waitFor();
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'tablero-indicadores', texto: 'Indicadores generales de la cartera.', posicion: 'esquina' },
      { captura: 'nav-clientes', texto: 'Menú de navegación entre módulos.', posicion: 'derecha' },
      { captura: 'barra-usuario', texto: 'Usuario y rol con el que trabaja.', posicion: 'abajo' },
      { captura: 'boton-salir', texto: 'Cierre la sesión al terminar.', posicion: 'abajo' },
      { captura: 'tablero-grafica-estados', texto: 'Solicitudes por estado: el total al centro y el detalle en la leyenda.', posicion: 'esquina' },
      { captura: 'tablero-cartera', texto: 'Cartera: montos acumulados y porcentaje recuperado.', posicion: 'esquina' },
    ], 'abajo-derecha', 'Pantalla principal');
    await capturar(page, '02-tablero');
  });

  await paso('25 Gráficas del tablero', async () => {
    await irA(page, '/');
    const recaudacion = cap('tablero-grafica-recaudacion');
    // Se espera la figura y no las columnas: sin pagos en 12 meses sale el estado vacío.
    await page.locator(`${recaudacion} .chn-grafica`).first().waitFor();
    await page.locator(`${cap('tablero-grafica-tipos')} .chn-grafica`).first().waitFor();
    await cerrarNotificaciones(page);

    // La barra superior es fija: se descuenta su alto para que no tape la fila.
    await page.evaluate((sel) => {
      const tarjeta = document.querySelector(sel);
      const barra = document.querySelector('.chn-superior');
      const tope = barra ? barra.getBoundingClientRect().bottom : 0;
      window.scrollBy(0, tarjeta.getBoundingClientRect().top - tope - 24);
    }, recaudacion);
    await page.waitForTimeout(400);

    // Las columnas en cero no se dibujan: la zona se localiza por posición horizontal, no por índice.
    const indiceMayor = await page.evaluate((sel) => {
      const tarjeta = document.querySelector(sel);
      const columnas = [...tarjeta.querySelectorAll('.chn-grafica-columnas__columna')];
      if (!columnas.length) return -1;
      const mayor = columnas.reduce((a, b) =>
        (b.getBoundingClientRect().top < a.getBoundingClientRect().top ? b : a));
      const caja = mayor.getBoundingClientRect();
      const centro = caja.left + caja.width / 2;
      return [...tarjeta.querySelectorAll('.chn-grafica__zona')].findIndex((zona) => {
        const banda = zona.getBoundingClientRect();
        return centro >= banda.left && centro <= banda.right;
      });
    }, recaudacion);
    if (indiceMayor >= 0) {
      await page.locator(`${recaudacion} .chn-grafica__zona`).nth(indiceMayor).hover();
      await page.locator('.chn-tooltip-grafica').first().waitFor();
      await page.waitForTimeout(300);
    }

    await anotar(page, [
      { captura: 'tablero-grafica-recaudacion', texto: 'Recaudación mensual: lo cobrado en cada uno de los últimos 12 meses.', posicion: 'esquina' },
      // Abajo: encima de la última columna está el botón «Ver tabla» (marca 4).
      { sel: `${recaudacion} .chn-grafica__zona:last-of-type`, texto: 'El último mes, con el nombre en negrita, es el mes en curso.', posicion: 'abajo' },
      { sel: '.chn-tooltip-grafica', texto: 'Pase el cursor sobre un mes (o use las flechas del teclado) para ver su detalle.', posicion: 'arriba' },
      { sel: `${recaudacion} .chn-grafica__herramientas button`, texto: '«Ver tabla» muestra las mismas cifras en una tabla.', posicion: 'izquierda' },
      { captura: 'tablero-grafica-tipos', texto: 'Cartera por tipo de préstamo: monto aprobado, de mayor a menor.', posicion: 'esquina' },
    ], 'abajo-derecha', 'Gráficas del tablero');
    await capturar(page, '25-tablero-graficas');

    // El puntero y el desplazamiento no deben afectar a las capturas siguientes.
    await page.mouse.move(0, 0);
    await page.evaluate(() => window.scrollTo(0, 0));
  });

  await paso('03 Listado de clientes', async () => {
    await irA(page, '/clientes');
    await page.locator(`${cap('clientes-tabla')} tbody tr`).first().waitFor();
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'nav-clientes', texto: 'Ingrese al módulo «Clientes».', posicion: 'derecha' },
      { captura: 'clientes-buscar', texto: 'Busque por nombre, DPI o correo electrónico.', posicion: 'abajo' },
      { captura: 'clientes-nuevo', texto: 'Pulse «Nuevo cliente» para registrar uno.', posicion: 'abajo' },
      { captura: 'clientes-tabla', texto: 'Listado con la información de contacto.', posicion: 'esquina' },
      { captura: 'cliente-accion-editar', texto: 'Editar / eliminar el cliente de la fila.', posicion: 'izquierda' },
    ], 'abajo-izquierda', 'Gestión de clientes');
    await capturar(page, '03-clientes-listado');
  });

  await paso('04 Registro de cliente', async () => {
    await irA(page, '/clientes');
    await page.locator(cap('clientes-nuevo')).click();
    await page.locator(cap('form-cliente')).waitFor();
    await escribir(page, 'form-cliente-nombre', 'María José');
    await escribir(page, 'form-cliente-apellido', 'Ramírez Coronado');
    await escribir(page, 'form-cliente-dpi', `2${SUFIJO}98110${String(SUFIJO).slice(-1)}`.slice(0, 13).padEnd(13, '4'));
    // Campo de texto con máscara dd/mm/aaaa, no un input date nativo.
    await escribir(page, 'form-cliente-nacimiento', '15/06/1992');
    await escribir(page, 'form-cliente-direccion', '12 Calle 5-40, Zona 10, Ciudad de Guatemala');
    await escribir(page, 'form-cliente-correo', `maria.ramirez.${SUFIJO}@correo.gt`);
    await escribir(page, 'form-cliente-telefono', '55418823');
    await page.waitForTimeout(300);

    // Captura 22 (selector de fecha): se toma aquí porque es donde el usuario lo ve por primera vez.
    await abrirCalendario(page, 'form-cliente-nacimiento');
    await anotar(page, [
      { captura: 'form-cliente-nacimiento', texto: 'Escriba la fecha o pulse el icono del calendario.', posicion: 'izquierda' },
      { sel: '.chn-calendario__anios', texto: 'Elija el año en la lista lateral.', posicion: 'izquierda' },
      { sel: '.chn-calendario__meses', texto: 'Elija el mes.', posicion: 'arriba' },
      { sel: '.chn-calendario__dias', texto: 'Pulse el día: el campo se llena y el panel se cierra.', posicion: 'derecha' },
    ], 'abajo-izquierda', 'Elegir una fecha');
    await capturar(page, '22-selector-fecha');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);

    await anotar(page, [
      { captura: 'form-cliente-nombre', texto: 'Nombre y apellido del cliente.', posicion: 'izquierda' },
      { captura: 'form-cliente-dpi', texto: 'Número de identificación (DPI): 13 dígitos.', posicion: 'izquierda' },
      { captura: 'form-cliente-nacimiento', texto: 'Fecha de nacimiento: debe ser mayor de edad.', posicion: 'derecha' },
      { captura: 'form-cliente-correo', texto: 'Correo electrónico y teléfono de 8 dígitos.', posicion: 'izquierda' },
      { captura: 'form-cliente-guardar', texto: 'Pulse «Guardar» para registrar al cliente.', posicion: 'arriba' },
    ], 'abajo-izquierda', 'Registrar un nuevo cliente');
    await capturar(page, '04-cliente-nuevo');
    await page.locator(cap('form-cliente-guardar')).click();

    // Captura 23: el aviso se desvanece a los 5 s, por eso va de inmediato.
    await page.waitForTimeout(900);
    await anotar(page, [
      { sel: '.chn-notificacion', texto: 'El aviso confirma en verde que la operación se guardó.', posicion: 'izquierda' },
    ], 'abajo-izquierda', 'Mensajes del sistema');
    await capturar(page, '23-aviso-exito');
    await page.waitForTimeout(900);
  });

  await paso('05 Ficha del cliente', async () => {
    await irA(page, '/clientes');
    await page.locator(cap('cliente-accion-ver')).first().click();
    await page.waitForTimeout(1500);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'cliente-accion-ver', texto: 'Pulse el icono «Ver» de la fila del cliente.', posicion: 'izquierda' },
    ], 'abajo-derecha', 'Consultar la ficha completa');
    await capturar(page, '05-cliente-ficha');
    await page.keyboard.press('Escape');
  });

  await paso('06 Eliminar cliente', async () => {
    await irA(page, '/clientes');
    const filas = page.locator(`${cap('clientes-tabla')} tbody tr`);
    await filas.first().waitFor();
    const ultima = (await filas.count()) - 1;
    await filas.nth(ultima).locator(cap('cliente-accion-eliminar')).click();
    await page.locator(cap('dialogo-confirmacion')).waitFor();
    await page.waitForTimeout(400);
    await anotar(page, [
      { captura: 'dialogo-confirmacion', texto: 'Lea la advertencia: se eliminan también las solicitudes, préstamos y pagos del cliente.', posicion: 'esquina' },
      { captura: 'dialogo-aceptar', texto: 'Confirme solo si está seguro.', posicion: 'arriba' },
      { captura: 'dialogo-cancelar', texto: 'Cancele para conservar el registro.', posicion: 'abajo' },
    ], 'arriba-derecha', 'Eliminar un cliente');
    await capturar(page, '06-cliente-eliminar');
    await page.locator(cap('dialogo-cancelar')).click();
  });

  await paso('07 Listado de solicitudes', async () => {
    await irA(page, '/solicitudes');
    await page.locator(`${cap('solicitudes-tabla')} tbody tr`).first().waitFor();
    await abrirFiltros(page);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'nav-solicitudes', texto: 'Ingrese al módulo «Solicitudes».', posicion: 'derecha' },
      { captura: 'solicitudes-filtro-estado', texto: 'Filtre por estado: en proceso, aprobada o rechazada.', posicion: 'abajo' },
      { captura: 'solicitudes-filtro-cliente', texto: 'Filtre las solicitudes de un cliente.', posicion: 'abajo' },
      { captura: 'solicitudes-nueva', texto: 'Cree una nueva solicitud de préstamo.', posicion: 'abajo' },
      { captura: 'solicitudes-tabla', texto: 'Estado actual de cada solicitud.', posicion: 'esquina' },
    ], 'abajo-izquierda', 'Solicitudes de préstamo');
    await capturar(page, '07-solicitudes-listado');
  });

  let idSolicitudNueva = null;
  await paso('08 Nueva solicitud', async () => {
    await irA(page, '/solicitudes/nueva');
    await page.locator(cap('form-solicitud')).waitFor();
    await elegirPrimeraOpcion(page, 'form-solicitud-cliente');
    await elegir(page, 'form-solicitud-tipo', 'PERSONAL');
    await escribir(page, 'form-solicitud-monto', '85000');
    await escribir(page, 'form-solicitud-plazo', '36');
    await escribir(page, 'form-solicitud-tasa', '13.50');
    await escribir(page, 'form-solicitud-ingreso', '9500');
    await escribir(page, 'form-solicitud-destino', 'Remodelación de vivienda y compra de mobiliario');
    await page.locator(cap('form-solicitud-simular')).click();
    await page.locator(cap('panel-simulacion')).waitFor();
    await page.waitForTimeout(1200);
    await cerrarNotificaciones(page);
    // Viewport más alto solo aquí: la cuota y el botón de envío deben caber en la misma imagen.
    await page.setViewportSize({ width: 1440, height: 1180 });
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(500);
    await anotar(page, [
      { captura: 'form-solicitud-cliente', texto: 'Seleccione el cliente solicitante.', posicion: 'izquierda' },
      { captura: 'form-solicitud-monto', texto: 'Monto solicitado y plazo deseado en meses.', posicion: 'izquierda' },
      { captura: 'form-solicitud-tasa', texto: 'Tasa de interés anual e ingreso mensual declarado.', posicion: 'izquierda' },
      { captura: 'form-solicitud-simular', texto: 'Pulse «Simular» para calcular la cuota.', posicion: 'abajo' },
      { captura: 'panel-simulacion', texto: 'Cuota mensual, intereses y capacidad de pago.', posicion: 'esquina' },
      { captura: 'form-solicitud-enviar', texto: 'Envíe la solicitud: queda «En proceso».', posicion: 'abajo' },
    ], 'abajo-izquierda', 'Solicitar un préstamo');
    await capturar(page, '08-solicitud-nueva');
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.locator(cap('form-solicitud-enviar')).click();
    await page.waitForURL(/\/solicitudes\/\d+/, { timeout: 20000 });
    idSolicitudNueva = page.url().split('/').pop();
    await page.waitForTimeout(1200);
  });

  await paso('09 Detalle de solicitud', async () => {
    if (idSolicitudNueva) await irA(page, `/solicitudes/${idSolicitudNueva}`);
    await page.locator(cap('solicitud-detalle')).waitFor();
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'solicitud-detalle', texto: 'Condiciones solicitadas y datos del cliente.', posicion: 'esquina' },
      { captura: 'solicitud-aprobar', texto: 'Apruebe la solicitud: se genera el préstamo.', posicion: 'abajo' },
      { captura: 'solicitud-rechazar', texto: 'Rechace la solicitud indicando el motivo.', posicion: 'abajo' },
    ], 'abajo-derecha', 'Resolver una solicitud');
    await capturar(page, '09-solicitud-detalle');
  });

  // El rechazo solo se muestra y se cancela: la misma solicitud se aprueba en el paso 11.
  await paso('10 Rechazo de solicitud', async () => {
    await page.locator(cap('solicitud-rechazar')).click();
    await page.locator(cap('form-rechazo')).waitFor();
    await escribir(page, 'form-rechazo-motivo',
      'El ingreso mensual declarado no cubre la cuota resultante según la política de endeudamiento del 40%.');
    await page.waitForTimeout(400);
    await anotar(page, [
      { captura: 'form-rechazo-motivo', texto: 'Escriba el motivo del rechazo (mínimo 10 caracteres).', posicion: 'izquierda' },
      { captura: 'form-rechazo-confirmar', texto: 'Confirme el rechazo: queda registrado con su usuario y fecha.', posicion: 'arriba' },
    ], 'arriba-derecha', 'Rechazar una solicitud');
    await capturar(page, '10-solicitud-rechazo');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(500);
  });

  await paso('11 Aprobación de solicitud', async () => {
    await page.locator(cap('solicitud-aprobar')).click();
    await page.locator(cap('form-aprobacion')).waitFor();
    await page.waitForTimeout(500);
    await anotar(page, [
      { captura: 'form-aprobacion-monto', texto: 'Ajuste el monto aprobado (no puede exceder el solicitado).', posicion: 'izquierda' },
      { captura: 'form-aprobacion-plazo', texto: 'Confirme el plazo y la tasa aprobados.', posicion: 'izquierda' },
      { captura: 'form-aprobacion-motivo', texto: 'Registre las observaciones de la aprobación.', posicion: 'izquierda' },
      { captura: 'form-aprobacion-confirmar', texto: 'Al confirmar se crea el préstamo y su plan de pagos.', posicion: 'arriba' },
    ], 'arriba-derecha', 'Aprobar una solicitud');
    await capturar(page, '11-solicitud-aprobacion');
    await page.locator(cap('form-aprobacion-confirmar')).click();
    await page.waitForTimeout(2000);
  });

  await paso('12 Solicitud resuelta', async () => {
    await cerrarNotificaciones(page);
    await page.locator(cap('solicitud-resolucion')).waitFor();
    await anotar(page, [
      { captura: 'solicitud-resolucion', texto: 'Detalle de la aprobación: fecha, usuario, monto, plazo y tasa.', posicion: 'esquina' },
    ], 'abajo-derecha', 'Resultado de la resolución');
    await capturar(page, '12-solicitud-resuelta');
  });

  await paso('13 Préstamos aprobados', async () => {
    await irA(page, '/prestamos');
    await page.locator(`${cap('prestamos-tabla')} tbody tr`).first().waitFor();
    await abrirFiltros(page);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'nav-prestamos', texto: 'Ingrese al módulo «Préstamos».', posicion: 'derecha' },
      { captura: 'prestamos-filtro-estado', texto: 'Filtre por estado, saldo pendiente o fechas.', posicion: 'abajo' },
      { captura: 'prestamos-tabla', texto: 'Saldo pendiente y avance de pago de cada préstamo.', posicion: 'esquina' },
    ], 'abajo-izquierda', 'Préstamos aprobados y su estado de pago');
    await capturar(page, '13-prestamos-listado');
  });

  let idPrestamo = null;
  await paso('14 Detalle del préstamo', async () => {
    await irA(page, '/prestamos');
    await page.locator(`${cap('prestamos-tabla')} tbody tr`).first().waitFor();
    await page.locator(`${cap('prestamos-tabla')} tbody tr a`).first().click();
    await page.waitForURL(/\/prestamos\/\d+/, { timeout: 20000 });
    idPrestamo = page.url().split('/').pop();
    await page.locator(cap('prestamo-detalle')).waitFor();
    await page.waitForTimeout(900);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'prestamo-saldo', texto: 'Saldo pendiente calculado y avance de pago.', posicion: 'esquina' },
      { captura: 'prestamo-pestanas', texto: 'Alterne entre el plan de amortización y el historial de pagos.', posicion: 'abajo' },
      { captura: 'prestamo-registrar-pago', texto: 'Registre un pago en efectivo del cliente.', posicion: 'abajo' },
    ], 'abajo-derecha', 'Consultar un préstamo aprobado');
    await capturar(page, '14-prestamo-detalle');
  });

  await paso('15 Plan de amortización', async () => {
    await page.locator(cap('tabla-amortizacion')).waitFor();
    await page.locator(cap('tabla-amortizacion')).scrollIntoViewIfNeeded();
    await page.waitForTimeout(500);
    await anotar(page, [
      { captura: 'tabla-amortizacion', texto: 'Cuota por cuota: abono a capital, intereses y saldo.', posicion: 'esquina' },
    ], 'arriba-derecha', 'Plan de amortización');
    await capturar(page, '15-prestamo-amortizacion');
    await page.evaluate(() => window.scrollTo(0, 0));
  });

  await paso('16 Registrar pago', async () => {
    await irA(page, '/pagos');
    await page.locator(cap('pagos-nuevo')).click();
    await page.locator(cap('form-pago')).waitFor();
    await elegirPrimeraOpcion(page, 'form-pago-prestamo');
    // El monto se deja con la cuota que propone el formulario al elegir el préstamo.
    await escribir(page, 'form-pago-observaciones', 'Pago en efectivo recibido en ventanilla, agencia central.');
    await page.waitForTimeout(500);
    await anotar(page, [
      { captura: 'form-pago-prestamo', texto: 'Seleccione el préstamo: se muestra su saldo actual.', posicion: 'izquierda' },
      { captura: 'form-pago-monto', texto: 'Se propone la cuota mensual; puede cambiar el monto.', posicion: 'izquierda' },
      { captura: 'form-pago-cuota', texto: 'Atajos: cuota mensual o saldo total.', posicion: 'izquierda' },
      { captura: 'form-pago-observaciones', texto: 'Observaciones opcionales del pago.', posicion: 'izquierda' },
      { captura: 'form-pago-guardar', texto: 'Guarde el pago: se recalcula el saldo pendiente.', posicion: 'arriba' },
    ], 'arriba-derecha', 'Registrar un pago en efectivo');
    await capturar(page, '16-pago-registrar');
    await page.locator(cap('form-pago-guardar')).click();
    await page.waitForTimeout(2200);
  });

  await paso('17 Comprobante de pago', async () => {
    const comprobante = page.locator(cap('pago-comprobante'));
    if (!(await comprobante.count())) {
      await irA(page, '/pagos');
      await page.locator(`${cap('pagos-tabla')} tbody tr`).first().waitFor();
      await page.locator(`${cap('pagos-tabla')} tbody tr button`).first().click();
      await comprobante.waitFor();
    }
    await page.waitForTimeout(700);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'pago-comprobante', texto: 'Comprobante con número de recibo y saldo resultante. Puede imprimirse.', posicion: 'esquina' },
    ], 'abajo-derecha', 'Comprobante del pago');
    await capturar(page, '17-pago-comprobante');
    await page.keyboard.press('Escape');
  });

  await paso('18 Listado de pagos', async () => {
    await irA(page, '/pagos');
    await page.locator(`${cap('pagos-tabla')} tbody tr`).first().waitFor();
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'nav-pagos', texto: 'Ingrese al módulo «Pagos».', posicion: 'derecha' },
      { captura: 'pagos-nuevo', texto: 'Registre un nuevo pago en efectivo.', posicion: 'abajo' },
      { captura: 'pagos-tabla', texto: 'Historial de pagos con saldo anterior y posterior.', posicion: 'esquina' },
    ], 'abajo-izquierda', 'Pagos registrados');
    await capturar(page, '18-pagos-listado');
  });

  await paso('19 Historial del préstamo', async () => {
    if (idPrestamo) await irA(page, `/prestamos/${idPrestamo}`);
    const pestanas = page.locator(`${cap('prestamo-pestanas')} [role="tab"]`);
    await pestanas.nth(1).click();
    await page.waitForTimeout(800);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'tabla-pagos', texto: 'Pagos aplicados al préstamo y su efecto en el saldo.', posicion: 'esquina' },
    ], 'arriba-derecha', 'Historial de pagos del préstamo');
    await capturar(page, '19-prestamo-historial');
  });

  await paso('20 Bitácora de auditoría', async () => {
    await irA(page, '/auditoria');
    await page.locator(`${cap('auditoria-tabla')} tbody tr`).first().waitFor();
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'nav-auditoria', texto: 'Disponible solo para el rol Administrador.', posicion: 'derecha' },
      { captura: 'auditoria-tabla', texto: 'Cada operación sensible queda registrada con usuario, IP y fecha.', posicion: 'esquina' },
    ], 'abajo-izquierda', 'Bitácora de auditoría');
    await capturar(page, '20-auditoria');
  });

  await paso('24 Filtros de búsqueda', async () => {
    await irA(page, '/clientes');
    await page.locator(`${cap('clientes-tabla')} tbody tr`).first().waitFor();
    await abrirFiltros(page);
    // Dos criterios para que aparezcan las pastillas y el contador.
    await escribir(page, 'filtro-nacimiento-desde', '01/01/1985');
    await escribir(page, 'filtro-nacimiento-hasta', '31/12/1995');
    await elegir(page, 'filtro-cliente-activo', 'true');
    await page.waitForTimeout(1200);
    await cerrarNotificaciones(page);
    await anotar(page, [
      { captura: 'filtros-alternar', texto: 'Despliegue «Filtros de búsqueda».', posicion: 'derecha' },
      { captura: 'filtro-nacimiento-desde', texto: 'Acote por rango de fechas.', posicion: 'izquierda' },
      { captura: 'filtro-cliente-activo', texto: 'Combine todos los criterios que necesite.', posicion: 'izquierda' },
      { captura: 'filtros-limpiar', texto: 'Quite todos los filtros de una vez.', posicion: 'abajo' },
    ], 'abajo-derecha', 'Filtrar un listado');
    await capturar(page, '24-filtros-busqueda');
    await page.locator(cap('filtros-limpiar')).click();
    await page.waitForTimeout(500);
  });

  await paso('21 Vista móvil', async () => {
    const movil = await contexto.newPage();
    await movil.setViewportSize({ width: 414, height: 896 });
    await movil.goto(`${BASE_URL}/clientes`, { waitUntil: 'networkidle' });
    await movil.waitForTimeout(1500);
    await movil.screenshot({ path: `${SALIDA}/21-vista-movil.png` });
    capturadas.push('21-vista-movil.png');
    console.log('  ✓ 21-vista-movil.png');
    await movil.close();
  });

  await navegador.close();

  const informe = {
    generado: new Date().toISOString(),
    baseUrl: BASE_URL,
    capturas: capturadas,
    avisos,
  };
  await writeFile(`${SALIDA}/informe-capturas.json`, JSON.stringify(informe, null, 2), 'utf8');

  console.log(`\n${capturadas.length} capturas generadas en ${SALIDA}`);
  if (avisos.length) {
    console.log(`\n${avisos.length} aviso(s):`);
    avisos.forEach((a) => console.log(`  - ${a}`));
  }
}

main().catch((e) => {
  console.error('Fallo irrecuperable:', e);
  process.exit(1);
});
