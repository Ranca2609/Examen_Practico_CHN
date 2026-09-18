import { useCallback, useRef, useState } from 'react';
import { validarFormulario } from '../../dominio/validaciones.js';

// Clave de los errores que no corresponden a ningún campo del formulario.
export const ERROR_GENERAL = '_general';

// alEnviar debe lanzar el error normalizado para que se marquen los campos.
export function useFormulario({ valoresIniciales = {}, reglas = {}, alEnviar } = {}) {
  const [valores, setValores] = useState(valoresIniciales);
  const [errores, setErrores] = useState({});
  const [enviando, setEnviando] = useState(false);

  // Ref para que reiniciar() no dependa de la identidad del objeto recibido entre renders.
  const iniciales = useRef(valoresIniciales);

  // Al editar un campo se borra su error: el usuario ya lo está corrigiendo.
  const establecerValor = useCallback((nombre, valor) => {
    setValores((actuales) => ({ ...actuales, [nombre]: valor }));
    setErrores((actuales) => {
      if (!actuales[nombre] && !actuales[ERROR_GENERAL]) return actuales;

      const siguiente = { ...actuales };
      delete siguiente[nombre];
      delete siguiente[ERROR_GENERAL];
      return siguiente;
    });
  }, []);

  // Acepta un evento de input o (nombre, valor) para componentes propios.
  const manejarCambio = useCallback(
    (eventoONombre, valorOpcional) => {
      if (typeof eventoONombre === 'string') {
        establecerValor(eventoONombre, valorOpcional);
        return;
      }

      const objetivo = eventoONombre?.target;
      if (!objetivo?.name) return;

      const valor = objetivo.type === 'checkbox' ? objetivo.checked : objetivo.value;
      establecerValor(objetivo.name, valor);
    },
    [establecerValor],
  );

  const establecerValores = useCallback((nuevos) => {
    setValores((actuales) => ({ ...actuales, ...nuevos }));
  }, []);

  const establecerErrores = useCallback((nuevos) => {
    setErrores(nuevos ?? {});
  }, []);

  const reiniciar = useCallback((nuevosIniciales) => {
    if (nuevosIniciales) iniciales.current = nuevosIniciales;

    setValores(iniciales.current);
    setErrores({});
    setEnviando(false);
  }, []);

  // Resuelve true solo si alEnviar terminó sin error.
  const manejarEnviar = useCallback(
    async (evento) => {
      evento?.preventDefault?.();

      // Ignora el doble clic.
      if (enviando) return false;

      const erroresValidacion = validarFormulario(valores, reglas);
      if (Object.keys(erroresValidacion).length > 0) {
        setErrores(erroresValidacion);
        return false;
      }

      setErrores({});

      if (typeof alEnviar !== 'function') return false;

      setEnviando(true);
      try {
        await alEnviar(valores);
        return true;
      } catch (error) {
        const erroresServidor = {};

        if (Array.isArray(error?.errores)) {
          error.errores.forEach(({ campo, mensaje }) => {
            if (campo && campo in valores) {
              erroresServidor[campo] = mensaje;
            } else if (mensaje) {
              // Campo que este formulario no pinta: se muestra arriba.
              erroresServidor[ERROR_GENERAL] = mensaje;
            }
          });
        }

        // Las reglas de negocio rechazadas no traen campo: van como mensaje general.
        if (Object.keys(erroresServidor).length === 0 && error?.mensaje) {
          erroresServidor[ERROR_GENERAL] = error.mensaje;
        }

        setErrores(erroresServidor);
        return false;
      } finally {
        setEnviando(false);
      }
    },
    [alEnviar, enviando, reglas, valores],
  );

  return {
    valores,
    errores,
    enviando,
    manejarCambio,
    establecerValor,
    establecerValores,
    establecerErrores,
    manejarEnviar,
    reiniciar,
  };
}

export default useFormulario;
