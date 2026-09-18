import Icono from './Icono.jsx';
import './Alerta.css';

const ICONO_POR_TONO = {
  info: 'info',
  exito: 'check',
  peligro: 'alerta',
  advertencia: 'alerta',
  neutro: 'info',
};

function Alerta({ tono = 'info', titulo, children, onCerrar, className = '', ...resto }) {
  const urgente = tono === 'peligro' || tono === 'advertencia';

  return (
    <div
      className={`chn-alerta chn-alerta--${tono} ${className}`.trim()}
      role={urgente ? 'alert' : 'status'}
      {...resto}
    >
      <span className="chn-alerta__icono">
        <Icono nombre={ICONO_POR_TONO[tono] || 'info'} tamano={20} />
      </span>

      <div className="chn-alerta__cuerpo">
        {titulo ? <p className="chn-alerta__titulo">{titulo}</p> : null}
        {children ? <div className="chn-alerta__detalle">{children}</div> : null}
      </div>

      {onCerrar ? (
        <button type="button" className="chn-alerta__cerrar" onClick={onCerrar} aria-label="Cerrar aviso">
          <Icono nombre="cerrar" tamano={16} />
        </button>
      ) : null}
    </div>
  );
}

export default Alerta;
