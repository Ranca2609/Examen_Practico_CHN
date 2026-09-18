import { useId, useState } from 'react';
import Boton from './Boton.jsx';
import Cargando from './Cargando.jsx';
import EstadoVacio from './EstadoVacio.jsx';
import Tabla from './Tabla.jsx';
import './FiguraGrafica.css';

function FiguraGrafica({
  titulo,
  vacio = false,
  cargando = false,
  tituloVacio = 'Sin datos para mostrar',
  mensajeVacio,
  iconoVacio = 'grafico',
  tabla,
  children,
  className = '',
  ...resto
}) {
  const [verTabla, setVerTabla] = useState(false);
  const idContenido = useId();

  const esperandoDatos = cargando && vacio;
  // Al recargar con datos previos la grafica se atenua en lugar de parpadear.
  const clases = ['chn-grafica', cargando && !vacio ? 'chn-grafica--recargando' : '', className]
    .filter(Boolean)
    .join(' ');

  let contenido = children;
  if (esperandoDatos) {
    contenido = <Cargando texto="Cargando datos..." altura={200} />;
  } else if (vacio) {
    contenido = <EstadoVacio titulo={tituloVacio} mensaje={mensajeVacio} icono={iconoVacio} />;
  } else if (verTabla && tabla) {
    contenido = (
      <Tabla
        columnas={tabla.columnas}
        datos={tabla.filas}
        claveFila={tabla.claveFila}
        esFilaActiva={tabla.esFilaActiva}
        descripcion={`${titulo}: datos de la gráfica`}
        className="chn-grafica__tabla"
      />
    );
  }

  const textoConmutador = verTabla ? 'Ver gráfica' : 'Ver tabla';

  return (
    <figure className={clases} aria-busy={cargando || undefined} {...resto}>
      <figcaption className="chn-oculto-visual">{titulo}</figcaption>

      {!vacio && tabla ? (
        <div className="chn-grafica__herramientas">
          <Boton
            variante="texto"
            tamano="sm"
            iconoIzquierda={verTabla ? 'grafico' : 'tabla'}
            aria-controls={idContenido}
            aria-label={`${textoConmutador}: ${titulo}`}
            onClick={() => setVerTabla((valor) => !valor)}
          >
            {textoConmutador}
          </Boton>
        </div>
      ) : null}

      <div id={idContenido} className="chn-grafica__contenido">
        {contenido}
      </div>
    </figure>
  );
}

export default FiguraGrafica;
