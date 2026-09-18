import './Tarjeta.css';

function Tarjeta({
  titulo,
  subtitulo,
  acciones,
  children,
  sinPadding = false,
  elevable = false,
  className = '',
  ...resto
}) {
  const tieneEncabezado = Boolean(titulo || subtitulo || acciones);
  const clases = ['chn-tarjeta', elevable ? 'chn-tarjeta--elevable' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <section className={clases} {...resto}>
      {tieneEncabezado ? (
        <header className="chn-tarjeta__encabezado">
          <div className="chn-tarjeta__titulos">
            {titulo ? <h2 className="chn-tarjeta__titulo">{titulo}</h2> : null}
            {subtitulo ? <p className="chn-tarjeta__subtitulo">{subtitulo}</p> : null}
          </div>
          {acciones ? <div className="chn-tarjeta__acciones">{acciones}</div> : null}
        </header>
      ) : null}

      <div className={`chn-tarjeta__cuerpo ${sinPadding ? 'chn-tarjeta__cuerpo--sin-padding' : ''}`.trim()}>
        {children}
      </div>
    </section>
  );
}

export default Tarjeta;
