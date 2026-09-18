import { LIMITES } from './catalogos.js';

// Deliberadamente simple, como en el backend: atrapa errores de captura sin rechazar correos válidos.
const PATRON_CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PATRON_DIGITOS = /^[0-9]+$/;

function estaVacio(valor) {
  return valor === null || valor === undefined || String(valor).trim() === '';
}

function aNumero(valor) {
  if (estaVacio(valor)) return null;
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : null;
}

// Cada validador recibe (valor, valores) y devuelve el mensaje o null. Salvo requerido,
// todos dejan pasar el vacío: la presencia se exige combinándolos con requerido.
export function requerido(valor) {
  return estaVacio(valor) ? 'Este campo es obligatorio.' : null;
}

export function longitud(minimo, maximo) {
  return (valor) => {
    if (estaVacio(valor)) return null;

    const largo = String(valor).trim().length;
    if (largo < minimo) return `Debe tener al menos ${minimo} caracteres.`;
    if (largo > maximo) return `No puede exceder ${maximo} caracteres.`;
    return null;
  };
}

export function dpi(valor) {
  if (estaVacio(valor)) return null;

  const limpio = String(valor).trim();
  if (limpio.length !== LIMITES.DIGITOS_DPI || !PATRON_DIGITOS.test(limpio)) {
    return `El DPI debe contener exactamente ${LIMITES.DIGITOS_DPI} dígitos numéricos.`;
  }
  return null;
}

export function telefono(valor) {
  if (estaVacio(valor)) return null;

  const limpio = String(valor).trim();
  if (limpio.length !== LIMITES.DIGITOS_TELEFONO || !PATRON_DIGITOS.test(limpio)) {
    return `El teléfono debe contener exactamente ${LIMITES.DIGITOS_TELEFONO} dígitos numéricos.`;
  }
  return null;
}

export function correo(valor) {
  if (estaVacio(valor)) return null;

  const limpio = String(valor).trim();
  if (limpio.length > 120) return 'El correo no puede exceder 120 caracteres.';
  if (!PATRON_CORREO.test(limpio)) return 'El correo electrónico no tiene un formato válido.';
  return null;
}

// La edad se calcula por año/mes/día para no depender de la duración del año ni de los bisiestos.
export function fechaNacimientoMayorDeEdad(valor) {
  if (estaVacio(valor)) return null;

  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(valor).trim());
  if (!partes) return 'La fecha de nacimiento no es válida.';

  const nacimiento = new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
  if (Number.isNaN(nacimiento.getTime())) return 'La fecha de nacimiento no es válida.';

  const hoy = new Date();
  const inicioHoy = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  if (nacimiento >= inicioHoy) return 'La fecha de nacimiento debe ser anterior a hoy.';

  let edad = inicioHoy.getFullYear() - nacimiento.getFullYear();
  const cumplioEsteAnio =
    inicioHoy.getMonth() > nacimiento.getMonth() ||
    (inicioHoy.getMonth() === nacimiento.getMonth() && inicioHoy.getDate() >= nacimiento.getDate());
  if (!cumplioEsteAnio) edad -= 1;

  if (edad < LIMITES.EDAD_MINIMA) {
    return `El cliente debe ser mayor de edad (${LIMITES.EDAD_MINIMA} años cumplidos).`;
  }
  if (edad > 120) return 'Verifique la fecha de nacimiento.';

  return null;
}

export function montoEnRango(minimo = LIMITES.MONTO_MINIMO, maximo = LIMITES.MONTO_MAXIMO) {
  return (valor) => {
    if (estaVacio(valor)) return null;

    const numero = aNumero(valor);
    if (numero === null) return 'Ingrese un monto válido.';
    if (numero < minimo || numero > maximo) {
      return `El monto debe estar entre Q ${minimo.toLocaleString('es-GT')} y Q ${maximo.toLocaleString('es-GT')}.`;
    }
    // Centavos como máximo, igual que en el backend.
    if (Math.round(numero * 100) !== Number((numero * 100).toFixed(4))) {
      return 'El monto admite un máximo de 2 decimales.';
    }
    return null;
  };
}

export function plazoEnRango(minimo = LIMITES.PLAZO_MINIMO, maximo = LIMITES.PLAZO_MAXIMO) {
  return (valor) => {
    if (estaVacio(valor)) return null;

    const numero = aNumero(valor);
    if (numero === null || !Number.isInteger(numero)) {
      return 'El plazo debe ser un número entero de meses.';
    }
    if (numero < minimo || numero > maximo) {
      return `El plazo debe estar entre ${minimo} y ${maximo} meses.`;
    }
    return null;
  };
}

export function tasaEnRango(minimo = LIMITES.TASA_MINIMA, maximo = LIMITES.TASA_MAXIMA) {
  return (valor) => {
    if (estaVacio(valor)) return null;

    const numero = aNumero(valor);
    if (numero === null) return 'Ingrese una tasa válida.';
    if (numero < minimo || numero > maximo) {
      return `La tasa debe estar entre ${minimo} % y ${maximo} %.`;
    }
    return null;
  };
}

export function numeroPositivo(valor) {
  if (estaVacio(valor)) return null;

  const numero = aNumero(valor);
  if (numero === null) return 'Ingrese un número válido.';
  if (numero <= 0) return 'El valor debe ser mayor que cero.';
  return null;
}

export function identificadorValido(valor) {
  if (estaVacio(valor)) return null;

  const numero = aNumero(valor);
  if (numero === null || !Number.isInteger(numero) || numero <= 0) {
    return 'Seleccione una opción válida.';
  }
  return null;
}

export function maximoCaracteres(maximo) {
  return (valor) => {
    if (estaVacio(valor)) return null;
    return String(valor).trim().length > maximo ? `No puede exceder ${maximo} caracteres.` : null;
  };
}

// reglas: campo -> validador o arreglo de validadores. Solo se reporta el primer error de cada campo.
export function validarFormulario(valores = {}, reglas = {}) {
  const errores = {};

  Object.entries(reglas).forEach(([campo, regla]) => {
    const validadores = Array.isArray(regla) ? regla : [regla];

    for (const validador of validadores) {
      if (typeof validador !== 'function') continue;

      const error = validador(valores[campo], valores);
      if (error) {
        errores[campo] = error;
        break;
      }
    }
  });

  return errores;
}

export function sinErrores(errores) {
  return !errores || Object.keys(errores).length === 0;
}
