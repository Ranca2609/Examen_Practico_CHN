import { usarContextoCampo } from './contextoCampo.js';
import './base.css';
import './CampoNumero.css';

// Prefijo y sufijo son aria-hidden: la unidad tiene que figurar tambien en la etiqueta o la ayuda.
function CampoNumero({
  id,
  nombre,
  valor,
  onChange,
  min,
  max,
  paso,
  prefijo,
  sufijo,
  error,
  deshabilitado = false,
  className = '',
  'aria-describedby': descritoExterno,
  ...resto
}) {
  const { descrito, invalido } = usarContextoCampo();
  const clasesGrupo = [
    'chn-campo-numero',
    error ? 'chn-campo-numero--error' : '',
    deshabilitado ? 'chn-campo-numero--deshabilitado' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={clasesGrupo}>
      {prefijo ? (
        <span className="chn-campo-numero__adorno" aria-hidden="true">
          {prefijo}
        </span>
      ) : null}

      <input
        id={id}
        name={nombre}
        type="number"
        inputMode="decimal"
        value={valor ?? ''}
        onChange={onChange}
        min={min}
        max={max}
        step={paso}
        disabled={deshabilitado}
        aria-describedby={descritoExterno || descrito}
        aria-invalid={error || invalido ? true : undefined}
        className={`chn-control chn-campo-numero__control ${className}`.trim()}
        {...resto}
      />

      {sufijo ? (
        <span className="chn-campo-numero__adorno chn-campo-numero__adorno--derecha" aria-hidden="true">
          {sufijo}
        </span>
      ) : null}
    </div>
  );
}

export default CampoNumero;
