import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

const DURACION_POR_DEFECTO = 5000;

const NotificacionContexto = createContext(null);

export function NotificacionProveedor({ children }) {
  const [notificaciones, setNotificaciones] = useState([]);

  const contador = useRef(0);
  const temporizadores = useRef(new Map());

  const descartar = useCallback((id) => {
    setNotificaciones((actuales) => actuales.filter((aviso) => aviso.id !== id));

    const temporizador = temporizadores.current.get(id);
    if (temporizador) {
      clearTimeout(temporizador);
      temporizadores.current.delete(id);
    }
  }, []);

  // duracion en ms; 0 deja el aviso fijo hasta que el usuario lo cierre.
  const notificar = useCallback(
    ({ tono = 'info', titulo, mensaje, duracion = DURACION_POR_DEFECTO }) => {
      contador.current += 1;
      const id = contador.current;

      setNotificaciones((actuales) => {
        // Máximo 4 avisos visibles para no tapar la pantalla.
        const recortadas = actuales.length >= 4 ? actuales.slice(actuales.length - 3) : actuales;
        // duracion viaja en el aviso solo para que Notificaciones dibuje la barra de vida.
        return [...recortadas, { id, tono, titulo, mensaje, duracion }];
      });

      if (duracion > 0) {
        const temporizador = setTimeout(() => descartar(id), duracion);
        temporizadores.current.set(id, temporizador);
      }

      return id;
    },
    [descartar],
  );

  useEffect(
    () => () => {
      temporizadores.current.forEach((temporizador) => clearTimeout(temporizador));
      temporizadores.current.clear();
    },
    [],
  );

  const valor = useMemo(
    () => ({ notificaciones, notificar, descartar }),
    [notificaciones, notificar, descartar],
  );

  return <NotificacionContexto.Provider value={valor}>{children}</NotificacionContexto.Provider>;
}

export function useNotificaciones() {
  const contexto = useContext(NotificacionContexto);

  if (!contexto) {
    throw new Error('useNotificaciones debe usarse dentro de <NotificacionProveedor>.');
  }

  return contexto;
}

// Alias: algunas pantallas lo importan en singular.
export const useNotificacion = useNotificaciones;

// Omite el toast si hay errores por campo: useFormulario ya los muestra junto a cada campo.
export function useAvisoDeError() {
  const { notificar } = useNotificaciones();

  return useCallback(
    (error, titulo) => {
      const tieneErroresPorCampo = Array.isArray(error?.errores) && error.errores.length > 0;
      if (tieneErroresPorCampo) return;

      notificar({
        tono: 'peligro',
        titulo,
        mensaje: error?.mensaje ?? 'Ocurrió un error inesperado.',
      });
    },
    [notificar],
  );
}

export default NotificacionContexto;
