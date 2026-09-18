import './Etiqueta.css';

function Etiqueta({ children, tono = 'neutro', className = '', ...resto }) {
  return (
    <span className={`chn-etiqueta chn-etiqueta--${tono} ${className}`.trim()} {...resto}>
      <span className="chn-etiqueta__punto" aria-hidden="true" />
      {children}
    </span>
  );
}

export default Etiqueta;
