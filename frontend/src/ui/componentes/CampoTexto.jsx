import { usarContextoCampo } from './contextoCampo.js';
import './base.css';
import './CampoTexto.css';

function CampoTexto({
  id,
  nombre,
  valor,
  onChange,
  tipo = 'text',
  placeholder,
  error,
  maxLength,
  autoComplete,
  deshabilitado = false,
  className = '',
  'aria-describedby': descritoExterno,
  ...resto
}) {
  const { descrito, invalido } = usarContextoCampo();

  return (
    <input
      id={id}
      name={nombre}
      type={tipo}
      // Se normaliza a cadena vacia para mantener el input siempre controlado.
      value={valor ?? ''}
      onChange={onChange}
      placeholder={placeholder}
      maxLength={maxLength}
      autoComplete={autoComplete}
      disabled={deshabilitado}
      aria-describedby={descritoExterno || descrito}
      aria-invalid={error || invalido ? true : undefined}
      className={`chn-control chn-campo-texto ${error ? 'chn-control--error' : ''} ${className}`.trim()}
      {...resto}
    />
  );
}

export default CampoTexto;
