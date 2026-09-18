import { usarContextoCampo } from './contextoCampo.js';
import './base.css';
import './CampoTextarea.css';

function CampoTextarea({
  id,
  nombre,
  valor,
  onChange,
  filas = 3,
  maxLength,
  placeholder,
  error,
  deshabilitado = false,
  className = '',
  'aria-describedby': descritoExterno,
  ...resto
}) {
  const { descrito, invalido } = usarContextoCampo();
  const texto = valor ?? '';

  return (
    <div className="chn-campo-textarea">
      <textarea
        id={id}
        name={nombre}
        value={texto}
        onChange={onChange}
        rows={filas}
        maxLength={maxLength}
        placeholder={placeholder}
        disabled={deshabilitado}
        aria-describedby={descritoExterno || descrito}
        aria-invalid={error || invalido ? true : undefined}
        className={`chn-control chn-campo-textarea__control ${error ? 'chn-control--error' : ''} ${className}`.trim()}
        {...resto}
      />
      {maxLength ? (
        <span className="chn-campo-textarea__contador" aria-live="polite">
          {`${String(texto).length} / ${maxLength} caracteres`}
        </span>
      ) : null}
    </div>
  );
}

export default CampoTextarea;
