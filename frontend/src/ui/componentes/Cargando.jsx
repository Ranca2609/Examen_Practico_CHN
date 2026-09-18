import './base.css';
import './Cargando.css';

function Cargando({ texto = 'Cargando...', altura, className = '', ...resto }) {
  const estilo = altura ? { minHeight: typeof altura === 'number' ? `${altura}px` : altura } : undefined;

  return (
    <div
      className={`chn-cargando ${className}`.trim()}
      style={estilo}
      role="status"
      aria-live="polite"
      {...resto}
    >
      <span className="chn-cargando__spinner" aria-hidden="true" />
      <span className="chn-cargando__texto">{texto}</span>
    </div>
  );
}

export default Cargando;
