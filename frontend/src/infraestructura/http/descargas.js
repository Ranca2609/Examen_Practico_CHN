const NOMBRE_RESPALDO = 'descarga';

// Axios nuevo entrega AxiosHeaders con get(); el antiguo, un objeto plano en minúsculas.
function leerCabecera(cabeceras, nombre) {
  if (!cabeceras) return '';

  if (typeof cabeceras.get === 'function') return cabeceras.get(nombre) ?? '';

  const clave = Object.keys(cabeceras).find((actual) => actual.toLowerCase() === nombre);
  return clave ? cabeceras[clave] ?? '' : '';
}

// El nombre viene de una cabecera: sin separadores de ruta ni puntos iniciales, nada de "../".
function sanearNombre(nombre) {
  return String(nombre)
    .replace(/[\\/]/g, '')
    .replace(/^\.+/, '')
    .trim();
}

// Se prefiere filename* porque declara la codificación y conserva tildes; filename queda de respaldo.
export function nombreDesdeCabecera(cabeceras, respaldo = NOMBRE_RESPALDO) {
  const disposicion = String(leerCabecera(cabeceras, 'content-disposition') || '');
  if (!disposicion) return respaldo;

  // filename*=UTF-8''plan-amortizaci%C3%B3n.pdf  (juego de caracteres e idioma delante)
  const extendido = /filename\*\s*=\s*[^']*'[^']*'([^;]+)/i.exec(disposicion);
  if (extendido) {
    const valor = extendido[1].trim().replace(/^"|"$/g, '');
    let decodificado = valor;
    try {
      decodificado = decodeURIComponent(valor);
    } catch {
      // Porcentajes mal formados: mejor el valor tal cual que fallar la descarga.
    }
    return sanearNombre(decodificado) || respaldo;
  }

  // filename="plan-amortizacion.pdf"
  const simple = /filename\s*=\s*"?([^";]+)"?/i.exec(disposicion);
  if (simple) return sanearNombre(simple[1]) || respaldo;

  return respaldo;
}

// Un <a href> directo a la API no llevaría el token: el Blob llega por clienteHttp y se entrega
// desde memoria. El enlace se añade al documento porque Firefox ignora el clic en un nodo suelto.
export function guardarArchivo(blob, nombre) {
  const url = URL.createObjectURL(blob);

  try {
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = sanearNombre(nombre) || NOMBRE_RESPALDO;
    enlace.rel = 'noopener';
    enlace.style.display = 'none';

    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
  } finally {
    // Se revoca siempre (si no, el archivo queda en memoria), pero en el siguiente ciclo:
    // algunos navegadores leen el blob después de procesar el clic.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
