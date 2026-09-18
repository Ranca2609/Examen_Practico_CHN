import Cargando from './Cargando.jsx';
import EstadoVacio from './EstadoVacio.jsx';
import './base.css';
import './Tabla.css';

const FILAS_ESQUELETO = 5;
// Tope del retardo escalonado: mas alla de 10 filas la espera se notaria.
const TOPE_ESCALONADO = 10;

function claseAlineacion(alineacion) {
  if (alineacion === 'derecha') return 'chn-tabla__celda--derecha';
  if (alineacion === 'centro') return 'chn-tabla__celda--centro';
  return '';
}

// Por omision, derecha = importes, IDs y fechas: cifras de ancho fijo para que cuadren.
function esColumnaNumerica(columna) {
  if (typeof columna.numerica === 'boolean') return columna.numerica;
  return columna.alineacion === 'derecha';
}

function claseCelda(columna) {
  return [
    claseAlineacion(columna.alineacion),
    esColumnaNumerica(columna) ? 'chn-tabla__celda--numerica' : '',
    columna.codigo ? 'chn-tabla__celda--codigo' : '',
  ]
    .filter(Boolean)
    .join(' ');
}

function Tabla({
  columnas = [],
  datos = [],
  claveFila = (fila) => fila.id,
  cargando = false,
  mensajeVacio = 'No se encontraron registros con los criterios indicados.',
  acciones,
  esFilaActiva,
  descripcion = 'Tabla de datos',
  className = '',
  ...resto
}) {
  // Se tolera recibir null mientras la consulta a la API no ha respondido.
  const filas = Array.isArray(datos) ? datos : [];
  const columnasSeguras = Array.isArray(columnas) ? columnas : [];
  const totalColumnas = columnasSeguras.length + (acciones ? 1 : 0);
  const sinDatos = !cargando && filas.length === 0;

  return (
    <div className={`chn-tabla-envoltorio ${className}`.trim()}>
      <table className="chn-tabla" {...resto}>
        <caption className="chn-oculto-visual">{descripcion}</caption>

        <thead className="chn-tabla__encabezado">
          <tr>
            {columnasSeguras.map((columna) => (
              <th
                key={columna.clave}
                scope="col"
                className={claseAlineacion(columna.alineacion)}
                style={columna.ancho ? { width: columna.ancho } : undefined}
              >
                {columna.encabezado}
              </th>
            ))}
            {acciones ? (
              <th scope="col" className="chn-tabla__celda--derecha chn-tabla__columna-acciones">
                Acciones
              </th>
            ) : null}
          </tr>
        </thead>

        <tbody>
          {/* Filas esqueleto: conservan la altura de la tabla mientras carga. */}
          {cargando
            ? Array.from({ length: FILAS_ESQUELETO }).map((_, indiceFila) => (
                <tr key={`esqueleto-${indiceFila}`} className="chn-tabla__fila">
                  {Array.from({ length: totalColumnas || 1 }).map((__, indiceCelda) => (
                    <td key={`esqueleto-celda-${indiceCelda}`}>
                      <span className="chn-esqueleto chn-tabla__esqueleto" />
                    </td>
                  ))}
                </tr>
              ))
            : null}

          {/* Estado vacio en una sola celda a todo el ancho. */}
          {sinDatos ? (
            <tr>
              <td colSpan={totalColumnas || 1} className="chn-tabla__vacio">
                <EstadoVacio titulo="Sin resultados" mensaje={mensajeVacio} icono="buscar" />
              </td>
            </tr>
          ) : null}

          {!cargando
            ? filas.map((fila, indice) => {
                const activa = Boolean(esFilaActiva && esFilaActiva(fila, indice));
                return (
                  <tr
                    key={claveFila(fila, indice)}
                    // La animacion la declara Tabla.css; .chn-anim-escalonado solo aporta el retardo.
                    className={`chn-tabla__fila chn-anim-escalonado ${
                      activa ? 'chn-tabla__fila--activa' : ''
                    }`.trim()}
                    style={{ '--indice': String(Math.min(indice, TOPE_ESCALONADO)) }}
                    aria-current={activa ? 'true' : undefined}
                  >
                    {columnasSeguras.map((columna) => (
                      <td key={columna.clave} className={claseCelda(columna)}>
                        {columna.render ? columna.render(fila, indice) : fila[columna.clave]}
                      </td>
                    ))}
                    {acciones ? (
                      <td className="chn-tabla__celda--derecha chn-tabla__columna-acciones">
                        <div className="chn-tabla__acciones">{acciones(fila, indice)}</div>
                      </td>
                    ) : null}
                  </tr>
                );
              })
            : null}
        </tbody>
      </table>

      {/* Mensaje accesible mientras se consulta la API. */}
      {cargando ? (
        <div className="chn-oculto-visual">
          <Cargando texto="Cargando registros..." />
        </div>
      ) : null}
    </div>
  );
}

export default Tabla;
