import Icono from './Icono.jsx';
import './EstadoVacio.css';

function EstadoVacio({ titulo = 'Sin registros', mensaje, icono = 'info', accion, className = '', ...resto }) {
  return (
    <div className={`chn-estado-vacio ${className}`.trim()} {...resto}>
      <span className="chn-estado-vacio__icono">
        {typeof icono === 'string' ? <Icono nombre={icono} tamano={28} /> : icono}
      </span>
      <p className="chn-estado-vacio__titulo">{titulo}</p>
      {mensaje ? <p className="chn-estado-vacio__mensaje">{mensaje}</p> : null}
      {accion ? <div className="chn-estado-vacio__accion">{accion}</div> : null}
    </div>
  );
}

export default EstadoVacio;
