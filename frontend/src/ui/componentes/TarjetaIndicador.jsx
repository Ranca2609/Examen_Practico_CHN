import { useId } from 'react';
import Icono from './Icono.jsx';
import './TarjetaIndicador.css';

function TarjetaIndicador({ titulo, valor, detalle, icono, tono = 'primario', className = '', ...resto }) {
  const idTitulo = useId();

  return (
    <article
      className={`chn-indicador chn-indicador--${tono} ${className}`.trim()}
      aria-labelledby={idTitulo}
      {...resto}
    >
      {icono ? (
        <span className="chn-indicador__icono">
          {typeof icono === 'string' ? <Icono nombre={icono} tamano={22} /> : icono}
        </span>
      ) : null}

      <div className="chn-indicador__contenido">
        <p className="chn-indicador__titulo" id={idTitulo}>
          {titulo}
        </p>
        <p className="chn-indicador__valor">{valor}</p>
        {detalle ? <p className="chn-indicador__detalle">{detalle}</p> : null}
      </div>
    </article>
  );
}

export default TarjetaIndicador;
