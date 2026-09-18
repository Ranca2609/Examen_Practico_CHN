import { useMemo } from 'react';
import { ContextoCampo } from './contextoCampo.js';
import './base.css';
import './CampoFormulario.css';

function CampoFormulario({ etiqueta, htmlFor, requerido = false, error, ayuda, children }) {
  const idAyuda = ayuda && htmlFor ? `${htmlFor}-ayuda` : undefined;
  const idError = error && htmlFor ? `${htmlFor}-error` : undefined;

  const contexto = useMemo(
    () => ({
      descrito: [idAyuda, idError].filter(Boolean).join(' ') || undefined,
      invalido: Boolean(error),
    }),
    [idAyuda, idError, error],
  );

  return (
    <div className={`chn-campo ${error ? 'chn-campo--con-error' : ''}`.trim()}>
      {etiqueta ? (
        <label className="chn-campo__etiqueta" htmlFor={htmlFor}>
          {etiqueta}
          {requerido ? (
            <span className="chn-campo__requerido" aria-hidden="true">
              *
            </span>
          ) : null}
          {requerido ? <span className="chn-oculto-visual">(obligatorio)</span> : null}
        </label>
      ) : null}

      <ContextoCampo.Provider value={contexto}>{children}</ContextoCampo.Provider>

      {ayuda ? (
        <p className="chn-campo__ayuda" id={idAyuda}>
          {ayuda}
        </p>
      ) : null}

      {/* role="alert": el lector de pantalla anuncia el error en cuanto aparece. */}
      {error ? (
        <p className="chn-campo__error" id={idError} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export default CampoFormulario;
