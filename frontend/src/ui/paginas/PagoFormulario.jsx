import { useEffect, useMemo, useState } from 'react';

import {
  Alerta,
  Boton,
  CampoFormulario,
  CampoNumero,
  CampoSelect,
  CampoTextarea,
  Icono,
} from '../componentes';
import { accion } from '../../dominio/acciones';
import { moneda } from '../../dominio/formato';
import {
  identificadorValido,
  maximoCaracteres,
  numeroPositivo,
  requerido,
  sinErrores,
  validarFormulario,
} from '../../dominio/validaciones';
import './PagoFormulario.css';

// Mismo límite que valida el backend.
const MAXIMO_OBSERVACIONES = 300;

// Evita rechazar el pago del saldo exacto por el redondeo de coma flotante.
const TOLERANCIA = 0.005;

const ACCION_GUARDAR = accion('guardar');
const ACCION_CANCELAR = accion('cancelar');

const conMensaje = (validador, mensaje) => (valor, valores) =>
  validador(valor, valores) ? mensaje : null;

const noExcedeSaldo = (saldo) => (valor) => {
  const numero = Number(valor);
  if (!Number.isFinite(numero) || saldo === null) return null;
  return numero > saldo + TOLERANCIA
    ? `El monto no puede exceder el saldo pendiente de ${moneda(saldo)}.`
    : null;
};

export function PagoFormulario({
  prestamoIdInicial,
  prestamos = [],
  alRegistrar,
  alCancelar,
  cargandoPrestamos = false,
}) {
  const [prestamoId, setPrestamoId] = useState(
    prestamoIdInicial ? String(prestamoIdInicial) : '',
  );
  const [monto, setMonto] = useState('');
  const [observaciones, setObservaciones] = useState('');
  const [errores, setErrores] = useState({});
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (prestamoIdInicial) setPrestamoId(String(prestamoIdInicial));
  }, [prestamoIdInicial]);

  const listado = Array.isArray(prestamos) ? prestamos : [];

  // Regla de negocio: un préstamo liquidado ya no recibe pagos.
  const vigentes = useMemo(
    () => listado.filter((prestamo) => prestamo.estado === 'VIGENTE'),
    [listado],
  );

  const seleccionado = useMemo(
    () => listado.find((prestamo) => String(prestamo.id) === String(prestamoId)) ?? null,
    [listado, prestamoId],
  );

  const fijo = Boolean(prestamoIdInicial);
  const opciones = useMemo(() => {
    const origen = fijo && seleccionado ? [seleccionado] : vigentes;
    return origen.map((prestamo) => ({
      valor: String(prestamo.id),
      etiqueta: `${prestamo.numeroPrestamo} — ${prestamo.nombreCliente} — Saldo: ${moneda(prestamo.saldoPendiente)}`,
    }));
  }, [fijo, seleccionado, vigentes]);

  const saldo = seleccionado ? Number(seleccionado.saldoPendiente) : null;
  // En el último pago el saldo puede ser menor que la cuota: se propone lo que falta.
  const cuotaSugerida =
    seleccionado && saldo !== null ? Math.min(Number(seleccionado.cuotaMensual), saldo) : null;

  // Solo al cambiar de préstamo: si dependiera de la cuota, un refresco del listado
  // pisaría el monto que ya escribió el cajero.
  const idSeleccionado = seleccionado?.id;
  useEffect(() => {
    setMonto(cuotaSugerida !== null ? cuotaSugerida.toFixed(2) : '');
    setErrores((previos) => ({ ...previos, monto: undefined }));
  }, [idSeleccionado]);

  const montoNumero = Number(monto);
  const montoUtil = monto !== '' && Number.isFinite(montoNumero) && montoNumero > 0;
  const saldoResultante =
    saldo !== null && montoUtil ? Math.max(saldo - montoNumero, 0) : saldo;

  // Contra un centavo y no contra cero exacto, por el redondeo de los decimales.
  const quedaLiquidado = montoUtil && saldoResultante !== null && saldoResultante < 0.01;

  const reglas = useMemo(
    () => ({
      prestamoId: [
        conMensaje(requerido, 'Seleccione el préstamo al que se aplicará el pago.'),
        identificadorValido,
      ],
      monto: [
        conMensaje(requerido, 'Ingrese el monto recibido.'),
        conMensaje(numeroPositivo, 'El monto debe ser mayor que cero.'),
        noExcedeSaldo(saldo),
      ],
      observaciones: [maximoCaracteres(MAXIMO_OBSERVACIONES)],
    }),
    [saldo],
  );

  const usarMonto = (valor) => {
    if (valor === null) return;
    setMonto(valor.toFixed(2));
    setErrores((previos) => ({ ...previos, monto: undefined }));
  };

  const enviar = async (evento) => {
    evento.preventDefault();

    const valores = { prestamoId, monto, observaciones };
    const encontrados = validarFormulario(valores, reglas);
    setErrores(encontrados);
    if (!sinErrores(encontrados)) return;

    setGuardando(true);
    try {
      await alRegistrar?.({
        prestamoId: Number(prestamoId),
        monto: Number(montoNumero.toFixed(2)),
        observaciones: observaciones.trim() || null,
      });
    } catch {
      // La página que registra el pago ya notifica el error; aquí solo se libera el botón.
    } finally {
      setGuardando(false);
    }
  };

  const sinPrestamosDisponibles = !cargandoPrestamos && opciones.length === 0;

  return (
    <form className="chn-pago-formulario" data-captura="form-pago" onSubmit={enviar} noValidate>
      {sinPrestamosDisponibles && (
        <Alerta tono="advertencia" titulo="No hay préstamos vigentes">
          Solo se pueden registrar pagos de préstamos vigentes. No se encontró ninguno
          disponible en este momento.
        </Alerta>
      )}

      <CampoFormulario
        etiqueta="Préstamo"
        htmlFor="form-pago-prestamo"
        requerido
        error={errores.prestamoId}
        ayuda={fijo ? 'El préstamo viene seleccionado desde la pantalla anterior.' : undefined}
      >
        <CampoSelect
          id="form-pago-prestamo"
          nombre="prestamoId"
          valor={prestamoId}
          onChange={(evento) => setPrestamoId(evento.target.value)}
          opciones={opciones}
          placeholder={cargandoPrestamos ? 'Cargando préstamos...' : 'Seleccione un préstamo'}
          error={errores.prestamoId}
          deshabilitado={fijo || cargandoPrestamos}
          /* Siempre buscable, sin umbral de opciones: la lista crece con la cartera. */
          buscable
          data-captura="form-pago-prestamo"
        />
      </CampoFormulario>

      <CampoFormulario
        etiqueta="Monto del pago (Q)"
        htmlFor="form-pago-monto"
        requerido
        error={errores.monto}
        ayuda={
          saldo !== null
            ? `Se propone la cuota mensual de ${moneda(cuotaSugerida)}; puede cambiarla. Saldo disponible: ${moneda(saldo)}.`
            : 'Seleccione primero el préstamo para conocer la cuota y el saldo disponible.'
        }
      >
        {/* Hijo directo a propósito: CampoFormulario inyecta aria-describedby y aria-invalid. */}
        <CampoNumero
          id="form-pago-monto"
          nombre="monto"
          valor={monto}
          onChange={(evento) => setMonto(evento.target.value)}
          min={0.01}
          max={saldo ?? undefined}
          paso={0.01}
          prefijo="Q"
          error={errores.monto}
          deshabilitado={!seleccionado}
          data-captura="form-pago-monto"
        />
      </CampoFormulario>

      <div className="chn-pago-formulario__atajo">
        <Boton
          variante="secundario"
          tono="suave"
          tamano="sm"
          iconoIzquierda="calendario"
          deshabilitado={cuotaSugerida === null}
          onClick={() => usarMonto(cuotaSugerida)}
          data-captura="form-pago-cuota"
        >
          Pagar cuota mensual
        </Boton>
        <Boton
          variante="secundario"
          tono="suave"
          tamano="sm"
          iconoIzquierda="dinero"
          deshabilitado={saldo === null}
          onClick={() => usarMonto(saldo)}
          data-captura="form-pago-saldo-total"
        >
          Pagar saldo total
        </Boton>
      </div>

      {seleccionado && (
        <div
          className={`chn-pago-formulario__resumen chn-anim-subir${
            quedaLiquidado ? ' chn-pago-formulario__resumen--liquidado' : ''
          }`}
          aria-live="polite"
        >
          <div className="chn-pago-formulario__dato">
            <span className="chn-pago-formulario__etiqueta">Saldo actual</span>
            <span className="chn-pago-formulario__valor">{moneda(saldo)}</span>
          </div>
          <div className="chn-pago-formulario__dato">
            <span className="chn-pago-formulario__etiqueta">Monto a pagar</span>
            <span className="chn-pago-formulario__valor chn-pago-formulario__valor--pago">
              {montoUtil ? moneda(montoNumero) : moneda(0)}
            </span>
          </div>
          <div className="chn-pago-formulario__dato chn-pago-formulario__dato--resultado">
            <span className="chn-pago-formulario__etiqueta">Saldo resultante</span>
            <span
              className={`chn-pago-formulario__valor chn-pago-formulario__valor--resultado${
                quedaLiquidado ? ' chn-pago-formulario__valor--liquidado' : ''
              }`}
            >
              {moneda(saldoResultante)}
            </span>
          </div>
          {quedaLiquidado && (
            <p className="chn-pago-formulario__aviso chn-anim-aparecer">
              <span className="chn-pago-formulario__aviso-icono" aria-hidden="true">
                <Icono nombre="exito" tamano={16} />
              </span>
              El saldo queda en cero: con este pago el préstamo quedará liquidado y ya
              no admitirá más abonos.
            </p>
          )}
        </div>
      )}

      <CampoFormulario
        etiqueta="Observaciones"
        htmlFor="form-pago-observaciones"
        error={errores.observaciones}
        ayuda="Opcional. Referencia del depósito, número de boleta u observación del cajero."
      >
        <CampoTextarea
          id="form-pago-observaciones"
          nombre="observaciones"
          valor={observaciones}
          onChange={(evento) => setObservaciones(evento.target.value)}
          filas={3}
          maxLength={MAXIMO_OBSERVACIONES}
          placeholder="Ejemplo: pago recibido en ventanilla, boleta 004512."
          error={errores.observaciones}
          data-captura="form-pago-observaciones"
        />
      </CampoFormulario>

      <div className="chn-pago-formulario__acciones">
        <Boton
          variante={ACCION_CANCELAR.variante}
          iconoIzquierda={ACCION_CANCELAR.icono}
          onClick={alCancelar}
          deshabilitado={guardando}
        >
          Cancelar
        </Boton>
        <Boton
          tipo="submit"
          variante={ACCION_GUARDAR.variante}
          iconoIzquierda={ACCION_GUARDAR.icono}
          cargando={guardando}
          deshabilitado={!seleccionado}
          data-captura="form-pago-guardar"
        >
          Registrar pago
        </Boton>
      </div>
    </form>
  );
}

export default PagoFormulario;
