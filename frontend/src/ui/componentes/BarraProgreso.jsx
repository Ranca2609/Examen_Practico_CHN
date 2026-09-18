import './BarraProgreso.css';

function BarraProgreso({ valor = 0, maximo = 100, tono = 'primario', etiqueta, className = '', ...resto }) {
  const total = Number(maximo) > 0 ? Number(maximo) : 100;
  const actual = Number.isFinite(Number(valor)) ? Number(valor) : 0;
  // Acotado a 0-100: un dato fuera de rango no debe desbordar la barra.
  const porcentaje = Math.min(100, Math.max(0, (actual / total) * 100));
  const porcentajeTexto = `${porcentaje.toFixed(porcentaje % 1 === 0 ? 0 : 1)} %`;

  return (
    <div className={`chn-progreso ${className}`.trim()} {...resto}>
      {etiqueta ? (
        <div className="chn-progreso__cabecera">
          <span className="chn-progreso__etiqueta">{etiqueta}</span>
          <span className="chn-progreso__valor">{porcentajeTexto}</span>
        </div>
      ) : null}

      <div
        className={`chn-progreso__pista chn-progreso__pista--${tono}`}
        role="progressbar"
        aria-valuenow={Math.round(porcentaje)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuetext={porcentajeTexto}
        aria-label={etiqueta ? undefined : 'Avance'}
      >
        <div className="chn-progreso__relleno" style={{ width: `${porcentaje}%` }} />
      </div>
    </div>
  );
}

export default BarraProgreso;
