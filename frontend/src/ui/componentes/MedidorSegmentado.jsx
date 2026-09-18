import { aNumero } from './graficas.js';
import { numero } from '../../dominio/formato.js';
import './MedidorSegmentado.css';

function MedidorSegmentado({
  etiqueta,
  valorTexto,
  segmentos = [],
  formatoValor = numero,
  className = '',
  ...resto
}) {
  const partes = (Array.isArray(segmentos) ? segmentos : []).map((segmento, indice) => ({
    ...segmento,
    clave: segmento.clave ?? indice,
    valor: Math.max(aNumero(segmento.valor), 0),
  }));
  const total = partes.reduce((suma, parte) => suma + parte.valor, 0);

  return (
    <div className={`chn-medidor ${className}`.trim()} {...resto}>
      {etiqueta || valorTexto ? (
        <div className="chn-medidor__cabecera">
          {etiqueta ? <span className="chn-medidor__etiqueta">{etiqueta}</span> : null}
          {valorTexto ? <span className="chn-medidor__valor">{valorTexto}</span> : null}
        </div>
      ) : null}

      {/* Tramos por flex-grow; el minimo de 3px del CSS evita que una parte pequena desaparezca. */}
      <div className="chn-medidor__pista chn-anim-revelar" aria-hidden="true">
        {total > 0 ? (
          partes
            .filter((parte) => parte.valor > 0)
            .map((parte) => (
              <span
                key={parte.clave}
                className="chn-medidor__tramo"
                style={{ flexGrow: parte.valor, backgroundColor: parte.color }}
              />
            ))
        ) : (
          <span className="chn-medidor__tramo chn-medidor__tramo--vacio" />
        )}
      </div>

      <ul className="chn-medidor__extremos">
        {partes.map((parte) => (
          <li key={parte.clave} className="chn-medidor__extremo">
            <span className="chn-medidor__rotulo">
              <span className="chn-medidor__muestra" style={{ backgroundColor: parte.color }} aria-hidden="true" />
              {parte.rotulo}
            </span>
            <span className="chn-medidor__monto">{formatoValor(parte.valor)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default MedidorSegmentado;
