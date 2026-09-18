import Boton from './Boton.jsx';
import CampoFecha from './CampoFecha.jsx';
import { comparaIso, esIsoValido } from '../../dominio/fechas.js';
import './CampoRangoFechas.css';

// Cruza los limites de ambos extremos para que el rango no se pueda invertir desde la interfaz.
function limite(propio, delRango, quedarseConElMayor) {
  const valido = esIsoValido(delRango) ? delRango : null;
  if (!valido) return propio;
  if (!propio) return valido;
  const orden = comparaIso(valido, propio);
  if (quedarseConElMayor) return orden > 0 ? valido : propio;
  return orden < 0 ? valido : propio;
}

function CampoRangoFechas({
  idDesde,
  idHasta,
  nombreDesde,
  nombreHasta,
  valorDesde,
  valorHasta,
  onChangeDesde,
  onChangeHasta,
  min,
  max,
  deshabilitado = false,
  alBuscar,
  etiquetaBuscar = 'Buscar',
  capturaDesde,
  capturaHasta,
  className = '',
  ...resto
}) {
  return (
    <div className={`chn-rango-fechas ${className}`.trim()} {...resto}>
      <div className="chn-rango-fechas__campo">
        <CampoFecha
          id={idDesde}
          nombre={nombreDesde}
          valor={valorDesde}
          onChange={onChangeDesde}
          min={min}
          max={limite(max, valorHasta, false)}
          deshabilitado={deshabilitado}
          data-captura={capturaDesde}
        />
      </div>

      <span className="chn-rango-fechas__union">al</span>

      <div className="chn-rango-fechas__campo">
        <CampoFecha
          id={idHasta}
          nombre={nombreHasta}
          valor={valorHasta}
          onChange={onChangeHasta}
          min={limite(min, valorDesde, true)}
          max={max}
          deshabilitado={deshabilitado}
          data-captura={capturaHasta}
        />
      </div>

      {alBuscar ? (
        <Boton
          variante="primario"
          iconoIzquierda="buscar"
          onClick={alBuscar}
          deshabilitado={deshabilitado}
          className="chn-rango-fechas__buscar"
        >
          {etiquetaBuscar}
        </Boton>
      ) : null}
    </div>
  );
}

export default CampoRangoFechas;
