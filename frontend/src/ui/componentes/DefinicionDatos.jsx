import './DefinicionDatos.css';

function DefinicionDatos({ datos = [], columnas = 2, className = '', ...resto }) {
  return (
    <dl
      className={`chn-definicion chn-definicion--col-${columnas} ${className}`.trim()}
      {...resto}
    >
      {datos.map((dato, indice) => (
        <div className="chn-definicion__par" key={`${dato.etiqueta}-${indice}`}>
          <dt className="chn-definicion__etiqueta">{dato.etiqueta}</dt>
          {/* Un valor vacio se muestra como guion para no dejar huecos ambiguos. */}
          <dd className="chn-definicion__valor">
            {dato.valor === null || dato.valor === undefined || dato.valor === '' ? '—' : dato.valor}
          </dd>
        </div>
      ))}
    </dl>
  );
}

export default DefinicionDatos;
