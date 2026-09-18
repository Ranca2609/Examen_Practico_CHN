import { useId } from 'react';
import CampoSelect from './CampoSelect.jsx';
import Icono from './Icono.jsx';
import './base.css';
import './Paginador.css';

const TAMANOS = [10, 25, 50];

// Paginas visibles en base 1 con elipsis: 1 ... 4 5 6 ... 20
function construirSecuencia(actual, total) {
  const secuencia = [];
  for (let numero = 1; numero <= total; numero += 1) {
    const esExtremo = numero === 1 || numero === total;
    const esVecino = Math.abs(numero - actual) <= 1;
    if (esExtremo || esVecino) {
      secuencia.push(numero);
    } else if (secuencia[secuencia.length - 1] !== '...') {
      secuencia.push('...');
    }
  }
  return secuencia;
}

function Paginador({
  pagina = 0,
  totalPaginas = 0,
  totalElementos = 0,
  tamano = 10,
  onCambiarPagina,
  onCambiarTamano,
  indiceBase = 0,
  className = '',
  ...resto
}) {
  const idTamano = useId();

  // La API pagina en base 0 (Spring Data); aqui se trabaja en base 1.
  const paginaVisible = Number(pagina) - indiceBase + 1;
  const paginas = Math.max(0, Number(totalPaginas) || 0);
  const desde = totalElementos === 0 ? 0 : (paginaVisible - 1) * tamano + 1;
  const hasta = Math.min(paginaVisible * tamano, totalElementos);

  const irA = (numeroVisible) => {
    if (!onCambiarPagina) return;
    if (numeroVisible < 1 || numeroVisible > paginas || numeroVisible === paginaVisible) return;
    onCambiarPagina(numeroVisible - 1 + indiceBase);
  };

  const enPrimera = paginaVisible <= 1;
  const enUltima = paginaVisible >= paginas;

  return (
    <nav className={`chn-paginador ${className}`.trim()} aria-label="Paginación de resultados" {...resto}>
      <p className="chn-paginador__resumen" aria-live="polite">
        {totalElementos === 0
          ? 'Sin registros'
          : `Mostrando ${desde}–${hasta} de ${totalElementos} registros`}
      </p>

      <div className="chn-paginador__controles">
        <button
          type="button"
          className="chn-paginador__boton"
          onClick={() => irA(paginaVisible - 1)}
          disabled={enPrimera}
          aria-label="Página anterior"
        >
          <Icono nombre="flechaIzquierda" tamano={16} />
        </button>

        <ul className="chn-paginador__numeros">
          {construirSecuencia(paginaVisible, paginas).map((elemento, indice) =>
            elemento === '...' ? (
              <li key={`elipsis-${indice}`} className="chn-paginador__elipsis" aria-hidden="true">
                …
              </li>
            ) : (
              <li key={`pagina-${elemento}`}>
                <button
                  type="button"
                  className={`chn-paginador__boton ${
                    elemento === paginaVisible ? 'chn-paginador__boton--activo' : ''
                  }`.trim()}
                  onClick={() => irA(elemento)}
                  aria-label={`Página ${elemento}`}
                  aria-current={elemento === paginaVisible ? 'page' : undefined}
                >
                  {elemento}
                </button>
              </li>
            ),
          )}
        </ul>

        <button
          type="button"
          className="chn-paginador__boton"
          onClick={() => irA(paginaVisible + 1)}
          disabled={enUltima}
          aria-label="Página siguiente"
        >
          <Icono nombre="flechaDerecha" tamano={16} />
        </button>
      </div>

      {onCambiarTamano ? (
        <div className="chn-paginador__tamano">
          <label className="chn-paginador__etiqueta" htmlFor={idTamano}>
            Registros por página
          </label>
          {/* CampoSelect trabaja con cadenas: el tamano se convierte a numero al salir. */}
          <CampoSelect
            id={idTamano}
            nombre="tamano"
            valor={String(tamano)}
            onChange={(evento) => onCambiarTamano(Number(evento.target.value))}
            opciones={TAMANOS.map((opcion) => ({
              valor: String(opcion),
              etiqueta: String(opcion),
            }))}
            className="chn-paginador__select"
          />
        </div>
      ) : null}
    </nav>
  );
}

export default Paginador;
