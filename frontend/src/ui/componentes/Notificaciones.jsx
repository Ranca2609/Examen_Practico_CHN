import { useCallback, useEffect, useRef, useState } from 'react';
import { useNotificaciones } from '../../aplicacion/NotificacionContexto.jsx';
import Icono from './Icono.jsx';
import './base.css';
import './Notificaciones.css';

const ICONO_POR_TONO = {
  exito: 'check',
  peligro: 'alerta',
  advertencia: 'alerta',
  info: 'info',
};

const TITULO_POR_TONO = {
  exito: 'Operación exitosa',
  peligro: 'No se pudo completar',
  advertencia: 'Atención',
  info: 'Información',
};

// ms que el aviso sigue visible mientras anima su salida.
const DURACION_SALIDA = 180;

function Notificaciones() {
  const { notificaciones: avisos = [], descartar } = useNotificaciones();

  const [saliendo, establecerSaliendo] = useState(() => new Set());
  // Temporizadores que anticipan el cierre automatico (solo animan).
  const temporizadoresAviso = useRef(new Map());
  // Temporizadores del cierre a mano (animan y despues descartan).
  const temporizadoresSalida = useRef(new Map());

  const marcarSaliendo = useCallback((id) => {
    establecerSaliendo((actuales) => {
      if (actuales.has(id)) return actuales;
      const siguientes = new Set(actuales);
      siguientes.add(id);
      return siguientes;
    });
  }, []);

  const olvidarSaliendo = useCallback((id) => {
    establecerSaliendo((actuales) => {
      if (!actuales.has(id)) return actuales;
      const siguientes = new Set(actuales);
      siguientes.delete(id);
      return siguientes;
    });
  }, []);

  const cerrar = useCallback(
    (id) => {
      if (temporizadoresSalida.current.has(id)) return;

      const anticipado = temporizadoresAviso.current.get(id);
      if (anticipado) {
        window.clearTimeout(anticipado);
        temporizadoresAviso.current.delete(id);
      }

      marcarSaliendo(id);

      const temporizador = window.setTimeout(() => {
        temporizadoresSalida.current.delete(id);
        olvidarSaliendo(id);
        if (descartar) descartar(id);
      }, DURACION_SALIDA);

      temporizadoresSalida.current.set(id, temporizador);
    },
    [descartar, marcarSaliendo, olvidarSaliendo],
  );

  // El retiro por tiempo lo hace el contexto; aqui solo se anticipa la animacion
  // de salida para que el aviso no desaparezca de golpe.
  useEffect(() => {
    const vigentes = new Set(avisos.map((aviso) => aviso.id));

    avisos.forEach((aviso) => {
      const duracion = Number(aviso.duracion) || 0;
      if (duracion <= DURACION_SALIDA) return; // aviso fijo o demasiado breve
      if (temporizadoresAviso.current.has(aviso.id)) return;
      if (temporizadoresSalida.current.has(aviso.id)) return; // se cerro a mano

      const temporizador = window.setTimeout(
        () => marcarSaliendo(aviso.id),
        duracion - DURACION_SALIDA,
      );
      temporizadoresAviso.current.set(aviso.id, temporizador);
    });

    // El contexto pudo retirarlo antes (cierre externo o recorte de la cola al llegar el quinto).
    temporizadoresAviso.current.forEach((temporizador, id) => {
      if (vigentes.has(id)) return;
      window.clearTimeout(temporizador);
      temporizadoresAviso.current.delete(id);
      olvidarSaliendo(id);
    });
  }, [avisos, marcarSaliendo, olvidarSaliendo]);

  useEffect(() => {
    const aviso = temporizadoresAviso.current;
    const salida = temporizadoresSalida.current;

    return () => {
      aviso.forEach((temporizador) => window.clearTimeout(temporizador));
      aviso.clear();
      salida.forEach((temporizador) => window.clearTimeout(temporizador));
      salida.clear();
    };
  }, []);

  return (
    <div className="chn-notificaciones" aria-live="polite" aria-atomic="false">
      {avisos.map((aviso, indice) => {
        const tono = ICONO_POR_TONO[aviso.tono] ? aviso.tono : 'info';
        const duracion = Number(aviso.duracion) || 0;
        const seVa = saliendo.has(aviso.id);
        const clases = [
          'chn-notificacion',
          `chn-notificacion--${tono}`,
          seVa ? 'chn-notificacion--saliendo' : '',
        ]
          .filter(Boolean)
          .join(' ');

        return (
          <article className={clases} key={aviso.id ?? `aviso-${indice}`}>
            <span className="chn-notificacion__icono" aria-hidden="true">
              <Icono nombre={ICONO_POR_TONO[tono]} tamano={18} />
            </span>

            <div className="chn-notificacion__cuerpo">
              <p className="chn-notificacion__titulo">{aviso.titulo || TITULO_POR_TONO[tono]}</p>
              <p className="chn-notificacion__mensaje">{aviso.mensaje}</p>
            </div>

            {descartar ? (
              <button
                type="button"
                className="chn-notificacion__cerrar"
                onClick={() => cerrar(aviso.id)}
                aria-label="Cerrar notificación"
              >
                <Icono nombre="cerrar" tamano={14} />
              </button>
            ) : null}

            {/* Sin duracion el aviso es fijo y no lleva barra de vida. */}
            {duracion > 0 && !seVa ? (
              <span
                className="chn-notificacion__vida"
                style={{ '--duracion': `${duracion}ms` }}
                aria-hidden="true"
              />
            ) : null}
          </article>
        );
      })}
    </div>
  );
}

export default Notificaciones;
