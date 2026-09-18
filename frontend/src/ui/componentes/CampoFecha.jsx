import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Icono from './Icono.jsx';
import { usarContextoCampo } from './contextoCampo.js';
import {
  DIAS_CORTOS,
  MESES_CORTOS,
  MESES_LARGOS,
  aIso,
  comparaIso,
  dentroDeRango,
  desdeIso,
  diasDelMes,
  enmascararFecha,
  esIsoValido,
  hoyIso,
  isoAVisible,
  rejillaDelMes,
  visibleAIso,
} from '../../dominio/fechas.js';
import './base.css';
import './CampoFecha.css';

const ANIO_INICIAL_LISTA = 1930;
const ANIOS_FUTUROS_LISTA = 10;

// Los estimados solo sirven en el primer calculo, antes de que el panel exista y se pueda medir.
const SEPARACION_PANEL = 4;
const MARGEN_VENTANA = 8;
const ALTO_ESTIMADO_PANEL = 300;
const ANCHO_ESTIMADO_PANEL = 380;

function referencia(iso) {
  const partes = desdeIso(iso) || desdeIso(hoyIso());
  if (partes) return partes;
  // Ultimo recurso, para que el panel nunca se quede sin mes que mostrar.
  const ahora = new Date();
  return { anio: ahora.getFullYear(), mes0: ahora.getMonth(), dia: ahora.getDate() };
}

function acercarAlRango(iso, min, max) {
  if (!esIsoValido(iso)) return iso;
  if (esIsoValido(min) && comparaIso(iso, min) < 0) return min;
  if (esIsoValido(max) && comparaIso(iso, max) > 0) return max;
  return iso;
}

function CampoFecha({
  id,
  nombre,
  valor,
  onChange,
  min,
  max,
  error,
  deshabilitado = false,
  placeholder = 'dd/mm/aaaa',
  className = '',
  'aria-describedby': descritoExterno,
  ...resto
}) {
  const { descrito, invalido } = usarContextoCampo();
  const idPanel = useId();

  const contenedorRef = useRef(null);
  const entradaRef = useRef(null);
  const panelRef = useRef(null);
  const listaAniosRef = useRef(null);
  const temporizadorFocoRef = useRef(0);
  const moverFocoRef = useRef(false);

  const [texto, setTexto] = useState(() => isoAVisible(valor));
  const [valorVisto, setValorVisto] = useState(valor);
  const [abierto, setAbierto] = useState(false);
  const [vista, setVista] = useState(() => {
    const base = referencia(valor);
    return { anio: base.anio, mes0: base.mes0 };
  });
  const [diaEnfocado, setDiaEnfocado] = useState(() => referencia(valor).dia);
  const [posicion, setPosicion] = useState(null);

  // Resincroniza el texto cuando el valor cambia desde fuera; ajuste en render (patron de React), sin efecto.
  if (valor !== valorVisto) {
    setValorVisto(valor);
    setTexto(isoAVisible(valor));
  }

  const hoy = useMemo(() => hoyIso(), []);

  const anios = useMemo(() => {
    const ultimo = new Date().getFullYear() + ANIOS_FUTUROS_LISTA;
    const lista = [];
    for (let anio = ANIO_INICIAL_LISTA; anio <= ultimo; anio += 1) lista.push(anio);
    return lista;
  }, []);

  const semanas = useMemo(() => {
    const celdas = rejillaDelMes(vista.anio, vista.mes0) || [];
    const filas = [];
    for (let i = 0; i < celdas.length; i += 7) filas.push(celdas.slice(i, i + 7));
    return filas;
  }, [vista.anio, vista.mes0]);

  // Forma de evento { target: { name, value } }: es lo que leen los formularios con useFormulario.
  const emitir = useCallback(
    (iso) => {
      if (onChange) onChange({ target: { name: nombre, value: iso } });
    },
    [onChange, nombre],
  );

  const cerrar = useCallback((devolverFoco) => {
    setAbierto(false);
    if (devolverFoco) entradaRef.current?.focus();
  }, []);

  const abrir = useCallback(
    (conFocoEnRejilla = false) => {
      if (deshabilitado) return;
      const isoTexto = visibleAIso(texto);
      let partida = hoyIso();
      if (esIsoValido(valor)) partida = valor;
      if (isoTexto && esIsoValido(isoTexto)) partida = isoTexto;
      const base = referencia(acercarAlRango(partida, min, max));
      setVista({ anio: base.anio, mes0: base.mes0 });
      setDiaEnfocado(base.dia);
      if (conFocoEnRejilla) moverFocoRef.current = true;
      setAbierto(true);
    },
    [deshabilitado, texto, valor, min, max],
  );

  const normalizar = useCallback(() => {
    if (texto === '') return;
    const iso = visibleAIso(texto);
    if (!iso || !esIsoValido(iso) || !dentroDeRango(iso, min, max)) {
      setTexto(isoAVisible(valor));
    }
  }, [texto, valor, min, max]);

  // El campo y su panel viven en ramas distintas del DOM (portal), asi que la
  // salida de foco se comprueba con el elemento activo real, no con relatedTarget.
  const alPerderFoco = useCallback(() => {
    window.clearTimeout(temporizadorFocoRef.current);
    temporizadorFocoRef.current = window.setTimeout(() => {
      const activo = document.activeElement;
      if (contenedorRef.current?.contains(activo)) return;
      if (panelRef.current?.contains(activo)) return;
      normalizar();
      setAbierto(false);
    }, 0);
  }, [normalizar]);

  useEffect(() => () => window.clearTimeout(temporizadorFocoRef.current), []);

  const alEscribir = (evento) => {
    const crudo = evento.target.value;

    // Un valor pegado en formato ISO se convierte de una vez.
    if (crudo.includes('-')) {
      const pegado = visibleAIso(crudo);
      if (pegado && esIsoValido(pegado)) {
        setTexto(isoAVisible(pegado));
        if (dentroDeRango(pegado, min, max)) emitir(pegado);
        return;
      }
    }

    const enmascarado = enmascararFecha(crudo);
    setTexto(enmascarado);

    if (enmascarado === '') {
      emitir('');
      return;
    }
    const iso = visibleAIso(enmascarado);
    // Un texto a medio escribir no emite nada: no se pierde lo tecleado.
    if (iso && esIsoValido(iso) && dentroDeRango(iso, min, max)) emitir(iso);
  };

  const enfocarRejilla = useCallback(() => {
    panelRef.current?.querySelector('[data-dia-enfocado="true"]')?.focus();
  }, []);

  const alTeclearEntrada = (evento) => {
    if (evento.key === 'ArrowDown' || evento.key === 'Enter') {
      evento.preventDefault();
      if (!abierto) abrir(evento.key === 'ArrowDown');
      else enfocarRejilla();
    }
  };

  // stopPropagation: Escape cierra solo el calendario, no el Modal que lo aloja.
  const alTeclearCampo = (evento) => {
    if (evento.key !== 'Escape' || !abierto) return;
    evento.preventDefault();
    evento.stopPropagation();
    cerrar(true);
  };

  const recalcular = useCallback(() => {
    const campo = contenedorRef.current;
    if (!campo) return;
    const caja = campo.getBoundingClientRect();
    const panel = panelRef.current;
    const alto = panel ? panel.offsetHeight : ALTO_ESTIMADO_PANEL;
    const ancho = panel ? panel.offsetWidth : ANCHO_ESTIMADO_PANEL;

    const cabeAbajo = window.innerHeight - caja.bottom >= alto + MARGEN_VENTANA;
    const arriba = !cabeAbajo && caja.top >= alto + MARGEN_VENTANA;
    const superior = arriba ? caja.top - alto - SEPARACION_PANEL : caja.bottom + SEPARACION_PANEL;

    let izquierda = caja.left;
    if (izquierda + ancho > window.innerWidth - MARGEN_VENTANA) {
      izquierda = caja.right - ancho;
    }
    izquierda = Math.max(MARGEN_VENTANA, izquierda);

    setPosicion((previa) =>
      previa && previa.superior === superior && previa.izquierda === izquierda && previa.arriba === arriba
        ? previa
        : { superior, izquierda, arriba },
    );
  }, []);

  useLayoutEffect(() => {
    if (!abierto) return undefined;
    recalcular();
    window.addEventListener('scroll', recalcular, { passive: true, capture: true });
    window.addEventListener('resize', recalcular, { passive: true });
    return () => {
      window.removeEventListener('scroll', recalcular, { capture: true });
      window.removeEventListener('resize', recalcular);
    };
  }, [abierto, recalcular]);

  useEffect(() => {
    if (!abierto) return undefined;
    const alPulsarFuera = (evento) => {
      if (contenedorRef.current?.contains(evento.target)) return;
      if (panelRef.current?.contains(evento.target)) return;
      setAbierto(false);
    };
    document.addEventListener('mousedown', alPulsarFuera);
    document.addEventListener('touchstart', alPulsarFuera, { passive: true });
    return () => {
      document.removeEventListener('mousedown', alPulsarFuera);
      document.removeEventListener('touchstart', alPulsarFuera);
    };
  }, [abierto]);

  useEffect(() => {
    if (!abierto) return;
    const activo = listaAniosRef.current?.querySelector('[data-activo="true"]');
    if (activo) activo.scrollIntoView({ block: 'center', inline: 'nearest' });
  }, [abierto, vista.anio]);

  // Foco itinerante: solo se mueve tras una orden explicita del teclado.
  useEffect(() => {
    if (!abierto || !moverFocoRef.current) return;
    moverFocoRef.current = false;
    panelRef.current?.querySelector('[data-dia-enfocado="true"]')?.focus();
  }, [abierto, diaEnfocado, vista]);

  const diaInhabilitado = (dia) => !dentroDeRango(aIso(vista.anio, vista.mes0, dia), min, max);

  // Un mes o un anio se inhabilitan solo si NINGUNO de sus dias cae en [min, max].
  const mesInhabilitado = (anio, mes0) => {
    const primero = aIso(anio, mes0, 1);
    const ultimo = aIso(anio, mes0, diasDelMes(anio, mes0));
    if (esIsoValido(min) && comparaIso(ultimo, min) < 0) return true;
    if (esIsoValido(max) && comparaIso(primero, max) > 0) return true;
    return false;
  };

  const anioInhabilitado = (anio) => {
    if (esIsoValido(min) && comparaIso(aIso(anio, 11, 31), min) < 0) return true;
    if (esIsoValido(max) && comparaIso(aIso(anio, 0, 1), max) > 0) return true;
    return false;
  };

  const aplicarFoco = (anio, mes0, dia) => {
    const dias = diasDelMes(anio, mes0) || 28;
    const elegido = Math.min(Math.max(dia, 1), dias);
    if (!dentroDeRango(aIso(anio, mes0, elegido), min, max)) return;
    moverFocoRef.current = true;
    setVista((previa) => (previa.anio === anio && previa.mes0 === mes0 ? previa : { anio, mes0 }));
    setDiaEnfocado(elegido);
  };

  const desplazarDias = (cantidad) => {
    const fecha = new Date(Date.UTC(vista.anio, vista.mes0, diaEnfocado));
    fecha.setUTCDate(fecha.getUTCDate() + cantidad);
    aplicarFoco(fecha.getUTCFullYear(), fecha.getUTCMonth(), fecha.getUTCDate());
  };

  const desplazarMeses = (cantidad) => {
    const total = vista.mes0 + cantidad;
    const anio = vista.anio + Math.floor(total / 12);
    const mes0 = ((total % 12) + 12) % 12;
    aplicarFoco(anio, mes0, diaEnfocado);
  };

  const elegirAnio = (anio) => {
    setVista((previa) => ({ anio, mes0: previa.mes0 }));
    setDiaEnfocado((previo) => Math.min(previo, diasDelMes(anio, vista.mes0) || previo));
  };

  const elegirMes = (mes0) => {
    setVista((previa) => ({ anio: previa.anio, mes0 }));
    setDiaEnfocado((previo) => Math.min(previo, diasDelMes(vista.anio, mes0) || previo));
  };

  const elegirDia = (dia) => {
    const iso = aIso(vista.anio, vista.mes0, dia);
    if (!dentroDeRango(iso, min, max)) return;
    setDiaEnfocado(dia);
    setTexto(isoAVisible(iso));
    emitir(iso);
    cerrar(true);
  };

  const alTeclearPanel = (evento) => {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      cerrar(true);
      return;
    }

    // Las flechas solo aplican a la rejilla de dias; Enter y espacio los resuelve el <button> nativo.
    const enDias = evento.target instanceof Element && evento.target.closest('[data-zona="dias"]');
    if (!enDias) return;

    switch (evento.key) {
      case 'ArrowLeft':
        evento.preventDefault();
        desplazarDias(-1);
        break;
      case 'ArrowRight':
        evento.preventDefault();
        desplazarDias(1);
        break;
      case 'ArrowUp':
        evento.preventDefault();
        desplazarDias(-7);
        break;
      case 'ArrowDown':
        evento.preventDefault();
        desplazarDias(7);
        break;
      case 'PageUp':
        evento.preventDefault();
        desplazarMeses(-1);
        break;
      case 'PageDown':
        evento.preventDefault();
        desplazarMeses(1);
        break;
      case 'Home':
        evento.preventDefault();
        aplicarFoco(vista.anio, vista.mes0, 1);
        break;
      case 'End':
        evento.preventDefault();
        aplicarFoco(vista.anio, vista.mes0, diasDelMes(vista.anio, vista.mes0));
        break;
      default:
        break;
    }
  };

  const clasesCampo = ['chn-fecha', deshabilitado ? 'chn-fecha--deshabilitado' : '', className]
    .filter(Boolean)
    .join(' ');

  // Se monta en un portal en body para que el overflow de un Modal o una tarjeta no lo recorte.
  const panel = (
    <div
      className={`chn-calendario ${posicion && posicion.arriba ? 'chn-calendario--arriba' : ''}`.trim()}
      id={idPanel}
      role="dialog"
      aria-modal="false"
      aria-label="Calendario"
      ref={panelRef}
      style={{ top: `${posicion ? posicion.superior : 0}px`, left: `${posicion ? posicion.izquierda : 0}px` }}
      onKeyDown={alTeclearPanel}
      onBlur={alPerderFoco}
    >
      <div className="chn-calendario__columnas">
        <div className="chn-calendario__anios" ref={listaAniosRef} role="group" aria-label="Año">
          {anios.map((anio) => (
            <button
              key={anio}
              type="button"
              className={`chn-calendario__opcion ${anio === vista.anio ? 'chn-calendario__opcion--activa' : ''}`.trim()}
              data-activo={anio === vista.anio ? 'true' : undefined}
              aria-pressed={anio === vista.anio}
              disabled={anioInhabilitado(anio)}
              tabIndex={-1}
              onClick={() => elegirAnio(anio)}
            >
              {anio}
            </button>
          ))}
        </div>

        <div className="chn-calendario__meses" role="group" aria-label="Mes">
          {MESES_CORTOS.map((mes, indice) => (
            <button
              key={mes}
              type="button"
              className={`chn-calendario__opcion ${indice === vista.mes0 ? 'chn-calendario__opcion--activa' : ''}`.trim()}
              aria-pressed={indice === vista.mes0}
              aria-label={MESES_LARGOS[indice]}
              disabled={mesInhabilitado(vista.anio, indice)}
              tabIndex={-1}
              onClick={() => elegirMes(indice)}
            >
              {mes}
            </button>
          ))}
        </div>

        <div className="chn-calendario__dias" data-zona="dias">
          <div
            className="chn-calendario__rejilla"
            role="grid"
            aria-label={`${MESES_LARGOS[vista.mes0]} de ${vista.anio}`}
          >
            <div className="chn-calendario__fila chn-calendario__fila--cabecera" role="row">
              {DIAS_CORTOS.map((dia) => (
                <span key={dia} role="columnheader" className="chn-calendario__nombre-dia">
                  {dia}
                </span>
              ))}
            </div>

            {semanas.map((semana, indiceSemana) => (
              <div className="chn-calendario__fila" role="row" key={`semana-${vista.anio}-${vista.mes0}-${indiceSemana}`}>
                {semana.map((dia, indiceDia) =>
                  dia === null ? (
                    <span
                      key={`hueco-${indiceSemana}-${indiceDia}`}
                      className="chn-calendario__hueco"
                      role="gridcell"
                      aria-hidden="true"
                    />
                  ) : (
                    <button
                      key={`dia-${dia}`}
                      type="button"
                      role="gridcell"
                      className={[
                        'chn-calendario__dia',
                        valor && aIso(vista.anio, vista.mes0, dia) === valor ? 'chn-calendario__dia--elegido' : '',
                        aIso(vista.anio, vista.mes0, dia) === hoy ? 'chn-calendario__dia--hoy' : '',
                      ]
                        .filter(Boolean)
                        .join(' ')}
                      data-dia={dia}
                      data-dia-enfocado={dia === diaEnfocado ? 'true' : undefined}
                      tabIndex={dia === diaEnfocado ? 0 : -1}
                      aria-selected={Boolean(valor) && aIso(vista.anio, vista.mes0, dia) === valor}
                      aria-current={aIso(vista.anio, vista.mes0, dia) === hoy ? 'date' : undefined}
                      aria-label={`${dia} de ${MESES_LARGOS[vista.mes0]} de ${vista.anio}`}
                      disabled={diaInhabilitado(dia)}
                      onClick={() => elegirDia(dia)}
                    >
                      {dia}
                    </button>
                  ),
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className={clasesCampo} ref={contenedorRef} onKeyDown={alTeclearCampo}>
      <input
        {...resto}
        id={id}
        name={nombre}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        ref={entradaRef}
        value={texto}
        placeholder={placeholder}
        maxLength={10}
        disabled={deshabilitado}
        onChange={alEscribir}
        onClick={() => {
          if (!abierto) abrir(false);
        }}
        onKeyDown={alTeclearEntrada}
        onBlur={alPerderFoco}
        aria-describedby={descritoExterno || descrito}
        aria-invalid={error || invalido ? true : undefined}
        className={`chn-control chn-fecha__entrada ${error ? 'chn-control--error' : ''}`.trim()}
      />

      <button
        type="button"
        className="chn-fecha__boton"
        aria-label="Abrir calendario"
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-controls={abierto ? idPanel : undefined}
        disabled={deshabilitado}
        onBlur={alPerderFoco}
        onClick={() => (abierto ? cerrar(true) : abrir(false))}
      >
        <Icono nombre="calendario" tamano={18} />
      </button>

      {abierto ? createPortal(panel, document.body) : null}
    </div>
  );
}

export default CampoFecha;
