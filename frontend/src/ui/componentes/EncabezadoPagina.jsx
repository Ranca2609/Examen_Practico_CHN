import { Link } from 'react-router-dom';
import './EncabezadoPagina.css';

function EncabezadoPagina({ titulo, descripcion, acciones, migas = [], className = '', ...resto }) {
  return (
    <header className={`chn-encabezado ${className}`.trim()} {...resto}>
      {migas.length > 0 ? (
        <nav className="chn-encabezado__migas" aria-label="Ruta de navegación">
          <ol className="chn-encabezado__migas-lista">
            {migas.map((miga, indice) => {
              const esUltima = indice === migas.length - 1;
              return (
                <li key={`${miga.etiqueta}-${indice}`} className="chn-encabezado__miga">
                  {miga.a && !esUltima ? (
                    <Link className="chn-encabezado__enlace" to={miga.a}>
                      {miga.etiqueta}
                    </Link>
                  ) : (
                    <span aria-current={esUltima ? 'page' : undefined}>{miga.etiqueta}</span>
                  )}
                  {!esUltima ? (
                    <span className="chn-encabezado__separador" aria-hidden="true">
                      /
                    </span>
                  ) : null}
                </li>
              );
            })}
          </ol>
        </nav>
      ) : null}

      <div className="chn-encabezado__fila">
        <div className="chn-encabezado__textos">
          <h1 className="chn-encabezado__titulo">{titulo}</h1>
          {descripcion ? <p className="chn-encabezado__descripcion">{descripcion}</p> : null}
        </div>
        {acciones ? <div className="chn-encabezado__acciones">{acciones}</div> : null}
      </div>
    </header>
  );
}

export default EncabezadoPagina;
