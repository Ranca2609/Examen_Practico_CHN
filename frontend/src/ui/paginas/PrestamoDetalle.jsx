import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import {
  Alerta,
  BarraProgreso,
  Boton,
  Cargando,
  DefinicionDatos,
  EncabezadoPagina,
  EstadoVacio,
  Etiqueta,
  Modal,
  Tabla,
  Tarjeta,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto';
import { useAvisoDeError, useNotificaciones } from '../../aplicacion/NotificacionContexto.jsx';
import { usePagos } from '../../aplicacion/hooks/usePagos';
import { usePrestamo } from '../../aplicacion/hooks/usePrestamos';
import * as adaptadorPrestamos from '../../infraestructura/http/adaptadorPrestamos.js';
import { accion } from '../../dominio/acciones';
import {
  etiquetaEstadoPrestamo,
  etiquetaFormaPago,
  tonoEstadoPrestamo,
} from '../../dominio/catalogos';
import { fecha, fechaHora, moneda, plazo, porcentaje } from '../../dominio/formato';
import PagoFormulario from './PagoFormulario.jsx';
import './PrestamoDetalle.css';

const ACCION_PAGAR = accion('pagar');
const ACCION_IMPRIMIR = accion('imprimir');
const ACCION_DESCARGAR = accion('descargar');
const ACCION_VOLVER = accion('volver');
const ACCION_REFRESCAR = accion('refrescar');

// captura: sufijo de data-captura que usan las pruebas ("excel", no "xlsx").
// soloConFilas: un Excel vacío no aporta; el PDF vacío sirve de constancia sin movimientos.
const FORMATOS_REPORTE = [
  { clave: 'pdf', etiqueta: 'PDF', captura: 'pdf', soloConFilas: false },
  { clave: 'xlsx', etiqueta: 'Excel', captura: 'excel', soloConFilas: true },
];

const AVANCE_FAVORABLE = 50;

const PESTANAS = [
  { id: 'amortizacion', etiqueta: 'Plan de amortización' },
  { id: 'pagos', etiqueta: 'Historial de pagos' },
];

// La fila esTotal deja en blanco los saldos: no son importes sumables.
const COLUMNAS_AMORTIZACION = [
  {
    clave: 'numero',
    encabezado: 'No.',
    alineacion: 'centro',
    ancho: '4.5rem',
    render: (fila) => (fila.esTotal ? <strong>Totales</strong> : fila.numero),
  },
  {
    clave: 'saldoInicial',
    encabezado: 'Saldo inicial',
    alineacion: 'derecha',
    render: (fila) => (fila.esTotal ? '' : moneda(fila.saldoInicial)),
  },
  {
    clave: 'cuota',
    encabezado: 'Cuota',
    alineacion: 'derecha',
    render: (fila) => (fila.esTotal ? <strong>{moneda(fila.cuota)}</strong> : moneda(fila.cuota)),
  },
  {
    clave: 'abonoCapital',
    encabezado: 'Abono a capital',
    alineacion: 'derecha',
    render: (fila) =>
      fila.esTotal ? <strong>{moneda(fila.abonoCapital)}</strong> : moneda(fila.abonoCapital),
  },
  {
    clave: 'abonoInteres',
    encabezado: 'Abono a intereses',
    alineacion: 'derecha',
    render: (fila) =>
      fila.esTotal ? <strong>{moneda(fila.abonoInteres)}</strong> : moneda(fila.abonoInteres),
  },
  {
    clave: 'saldoFinal',
    encabezado: 'Saldo final',
    alineacion: 'derecha',
    render: (fila) => (fila.esTotal ? '' : moneda(fila.saldoFinal)),
  },
];

const COLUMNAS_PAGOS = [
  {
    clave: 'numeroRecibo',
    encabezado: 'No. de recibo',
    ancho: '11rem',
    codigo: true,
    render: (fila) => <span className="chn-prestamo-detalle__recibo">{fila.numeroRecibo}</span>,
  },
  {
    clave: 'fechaPago',
    encabezado: 'Fecha y hora',
    ancho: '9.5rem',
    render: (fila) => fechaHora(fila.fechaPago),
  },
  {
    clave: 'monto',
    encabezado: 'Monto',
    alineacion: 'derecha',
    ancho: '8rem',
    render: (fila) => <strong className="chn-prestamo-detalle__abono">{moneda(fila.monto)}</strong>,
  },
  {
    clave: 'saldoAnterior',
    encabezado: 'Saldo anterior',
    alineacion: 'derecha',
    ancho: '8.5rem',
    render: (fila) => moneda(fila.saldoAnterior),
  },
  {
    clave: 'saldoPosterior',
    encabezado: 'Saldo posterior',
    alineacion: 'derecha',
    ancho: '8.5rem',
    render: (fila) => moneda(fila.saldoPosterior),
  },
  {
    clave: 'formaPago',
    encabezado: 'Forma de pago',
    ancho: '8rem',
    render: (fila) => etiquetaFormaPago(fila.formaPago),
  },
  {
    clave: 'usuarioRegistro',
    encabezado: 'Registrado por',
    ancho: '9rem',
    render: (fila) => fila.usuarioRegistro ?? '—',
  },
  {
    clave: 'observaciones',
    encabezado: 'Observaciones',
    render: (fila) =>
      fila.observaciones ? (
        <span className="chn-prestamo-detalle__observacion" title={fila.observaciones}>
          {fila.observaciones}
        </span>
      ) : (
        '—'
      ),
  },
];

export function PrestamoDetalle() {
  const { id } = useParams();
  const navegar = useNavigate();
  const { tienePermiso } = useAutenticacion();
  const { notificar } = useNotificaciones();
  const avisarError = useAvisoDeError();

  const puedeRegistrarPago = tienePermiso('REGISTRAR_PAGO');

  const [pestanaActiva, setPestanaActiva] = useState('amortizacion');
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const referenciasPestanas = useRef({});

  // Lista y no booleano: PDF y Excel son peticiones distintas y una no debe bloquear a la otra.
  const [descargasEnCurso, setDescargasEnCurso] = useState([]);

  const [indicador, setIndicador] = useState({ izquierda: 0, ancho: 0 });
  // Estado y no ref: la medida debe rehacerse cuando la lista aparece en el DOM.
  const [listaPestanas, setListaPestanas] = useState(null);

  const { prestamo, amortizacion, pagos, cargando, error, recargar } = usePrestamo(id);

  // Solo el registro (dueño de la escritura y sus avisos); el listado viene de usePrestamo.
  const { registrar } = usePagos({ prestamoInicial: id });

  const filasAmortizacion = useMemo(() => {
    const cuotas = Array.isArray(amortizacion?.cuotas) ? amortizacion.cuotas : [];
    if (cuotas.length === 0) return [];

    const totales = cuotas.reduce(
      (acumulado, cuota) => ({
        cuota: acumulado.cuota + Number(cuota.cuota ?? 0),
        abonoCapital: acumulado.abonoCapital + Number(cuota.abonoCapital ?? 0),
        abonoInteres: acumulado.abonoInteres + Number(cuota.abonoInteres ?? 0),
      }),
      { cuota: 0, abonoCapital: 0, abonoInteres: 0 },
    );

    return [...cuotas, { ...totales, numero: 'totales', esTotal: true }];
  }, [amortizacion]);

  // Se mide en el DOM porque el ancho de cada pestaña depende de su texto.
  useLayoutEffect(() => {
    if (!listaPestanas) return undefined;

    const medir = () => {
      const nodo = referenciasPestanas.current[pestanaActiva];
      if (!nodo) return;
      setIndicador({ izquierda: nodo.offsetLeft, ancho: nodo.offsetWidth });
    };

    medir();
    window.addEventListener('resize', medir, { passive: true });
    return () => window.removeEventListener('resize', medir);
  }, [pestanaActiva, listaPestanas]);

  // Patrón ARIA de tabs: flechas, Home y End con foco itinerante.
  const navegarPestanas = (evento) => {
    const actual = PESTANAS.findIndex((pestana) => pestana.id === pestanaActiva);
    let destino = null;

    if (evento.key === 'ArrowRight') destino = (actual + 1) % PESTANAS.length;
    else if (evento.key === 'ArrowLeft') destino = (actual - 1 + PESTANAS.length) % PESTANAS.length;
    else if (evento.key === 'Home') destino = 0;
    else if (evento.key === 'End') destino = PESTANAS.length - 1;
    if (destino === null) return;

    evento.preventDefault();
    const idDestino = PESTANAS[destino].id;
    setPestanaActiva(idDestino);
    referenciasPestanas.current[idDestino]?.focus();
  };

  // El adaptador guarda el archivo (conoce el nombre de la cabecera); aquí solo spinner y aviso.
  const descargarReporte = async (clave, pedirArchivo) => {
    setDescargasEnCurso((actuales) => [...actuales, clave]);

    try {
      const nombre = await pedirArchivo();
      notificar({
        tono: 'exito',
        titulo: 'Descarga completada',
        mensaje: `Se descargó el archivo ${nombre}`,
      });
    } catch (fallo) {
      avisarError(fallo, 'No se pudo generar el reporte');
    } finally {
      setDescargasEnCurso((actuales) => actuales.filter((activa) => activa !== clave));
    }
  };

  // Sin try/catch: si falla, el error llega al formulario y el modal sigue abierto.
  const registrarAbono = async (datos) => {
    await registrar(datos);
    setFormularioAbierto(false);
    recargar();
    setPestanaActiva('pagos');
  };

  if (cargando && !prestamo) {
    return <Cargando texto="Cargando el préstamo..." altura="320px" />;
  }

  if (error || !prestamo) {
    return (
      <section className="chn-prestamo-detalle">
        <Alerta tono="peligro" titulo="No se pudo cargar el préstamo">
          {error?.mensaje ?? 'El préstamo solicitado no existe o no está disponible.'}
        </Alerta>
        <div className="chn-prestamo-detalle__reintento">
          <Boton
            variante={ACCION_VOLVER.variante}
            iconoIzquierda={ACCION_VOLVER.icono}
            onClick={() => navegar('/prestamos')}
          >
            Volver a préstamos
          </Boton>
          {error && (
            <Boton
              variante={ACCION_REFRESCAR.variante}
              iconoIzquierda={ACCION_REFRESCAR.icono}
              onClick={recargar}
            >
              Reintentar
            </Boton>
          )}
        </div>
      </section>
    );
  }

  const vigente = prestamo.estado === 'VIGENTE';
  const puedeAbonar = vigente && puedeRegistrarPago;
  const listaPagos = Array.isArray(pagos) ? pagos : [];

  const avance = Number(prestamo.porcentajePagado ?? 0);
  const tonoAvance = avance > AVANCE_FAVORABLE ? 'exito' : 'info';

  const totalIntereses =
    Number(prestamo.montoTotalAPagar ?? 0) - Number(prestamo.montoAprobado ?? 0);

  const datosCliente = [
    { etiqueta: 'Nombre', valor: prestamo.nombreCliente },
    { etiqueta: 'DPI', valor: prestamo.identificacionCliente },
    {
      etiqueta: 'Expediente',
      valor: (
        <Link to="/clientes" state={{ clienteId: prestamo.clienteId }}>
          Ver en clientes
        </Link>
      ),
    },
  ];

  const datosCondiciones = [
    { etiqueta: 'Monto aprobado', valor: moneda(prestamo.montoAprobado) },
    { etiqueta: 'Plazo', valor: plazo(prestamo.plazoMeses) },
    { etiqueta: 'Tasa de interés anual', valor: porcentaje(prestamo.tasaInteresAnual) },
    { etiqueta: 'Cuota mensual', valor: moneda(prestamo.cuotaMensual) },
    { etiqueta: 'Total a pagar', valor: moneda(prestamo.montoTotalAPagar) },
    { etiqueta: 'Total de intereses', valor: moneda(totalIntereses) },
    { etiqueta: 'Fecha de desembolso', valor: fecha(prestamo.fechaDesembolso) },
    { etiqueta: 'Fecha de vencimiento', valor: fecha(prestamo.fechaVencimiento) },
    {
      etiqueta: 'Solicitud de origen',
      valor: <Link to={`/solicitudes/${prestamo.solicitudId}`}>{prestamo.numeroSolicitud}</Link>,
    },
  ];

  // Tono suave a propósito: la acción principal de la pantalla es registrar el pago, no exportar.
  const botonesDeDescarga = ({ prefijo, descripcion, pedir, hayFilas, motivoSinDatos }) =>
    FORMATOS_REPORTE.map(({ clave, etiqueta, captura, soloConFilas }) => {
      const sinDatos = soloConFilas && !hayFilas;
      const nombreAccesible = `Descargar ${descripcion} en ${etiqueta}`;

      return (
        <Boton
          key={clave}
          variante={ACCION_DESCARGAR.variante}
          tono="suave"
          iconoIzquierda={ACCION_DESCARGAR.icono}
          cargando={descargasEnCurso.includes(`${prefijo}-${clave}`)}
          deshabilitado={sinDatos}
          title={sinDatos ? motivoSinDatos : nombreAccesible}
          aria-label={nombreAccesible}
          data-captura={`${prefijo}-descargar-${captura}`}
          onClick={() => descargarReporte(`${prefijo}-${clave}`, () => pedir(clave))}
        >
          {etiqueta}
        </Boton>
      );
    });

  // Solo se monta la pestaña activa para que su entrada se anime en cada cambio.
  const contenidoPestanas = {
    amortizacion: (
      <>
        <div className="chn-prestamo-detalle__panel-encabezado">
          <div>
            <h3 className="chn-prestamo-detalle__panel-titulo">Plan de amortización</h3>
            <p className="chn-prestamo-detalle__panel-nota">
              Cuotas niveladas sobre saldos. Es el plan pactado al desembolsar, por lo
              que no cambia con los abonos realizados.
            </p>
          </div>
          <div className="chn-prestamo-detalle__panel-acciones">
            <Boton
              variante={ACCION_IMPRIMIR.variante}
              tono="suave"
              iconoIzquierda={ACCION_IMPRIMIR.icono}
              onClick={() => window.print()}
            >
              Imprimir
            </Boton>

            {botonesDeDescarga({
              prefijo: 'prestamo',
              descripcion: 'el plan de amortización',
              pedir: (formato) =>
                adaptadorPrestamos.descargarAmortizacion(prestamo.id, formato),
              hayFilas: filasAmortizacion.length > 0,
              motivoSinDatos:
                'No se pudo obtener el plan de amortización, así que no hay filas que exportar.',
            })}
          </div>
        </div>

        <div data-captura="tabla-amortizacion">
          {filasAmortizacion.length === 0 ? (
            <EstadoVacio
              icono="calculadora"
              titulo="Plan no disponible"
              mensaje="No fue posible obtener el plan de amortización de este préstamo."
            />
          ) : (
            <Tabla
              columnas={COLUMNAS_AMORTIZACION}
              datos={filasAmortizacion}
              claveFila={(fila) => (fila.esTotal ? 'totales' : `cuota-${fila.numero}`)}
              mensajeVacio="No hay cuotas para mostrar."
              descripcion={`Plan de amortización del préstamo ${prestamo.numeroPrestamo}`}
            />
          )}
        </div>
      </>
    ),
    pagos: (
      <>
        <div className="chn-prestamo-detalle__panel-encabezado">
          <div>
            <h3 className="chn-prestamo-detalle__panel-titulo">Historial de pagos</h3>
            <p className="chn-prestamo-detalle__panel-nota">
              Abonos aplicados al préstamo, del más reciente al más antiguo.
            </p>
          </div>

          <div className="chn-prestamo-detalle__panel-acciones">
            {botonesDeDescarga({
              prefijo: 'pagos',
              descripcion: 'el historial de pagos',
              pedir: (formato) => adaptadorPrestamos.descargarPagos(prestamo.id, formato),
              hayFilas: listaPagos.length > 0,
              motivoSinDatos:
                'Este préstamo aún no registra pagos: no hay filas que exportar a Excel.',
            })}
          </div>
        </div>

        <div data-captura="tabla-pagos">
          {listaPagos.length === 0 ? (
            <EstadoVacio
              icono="pagos"
              titulo="Sin pagos"
              mensaje="Este préstamo aún no registra pagos"
              accion={
                puedeAbonar ? (
                  <Boton
                    variante={ACCION_PAGAR.variante}
                    iconoIzquierda={ACCION_PAGAR.icono}
                    onClick={() => setFormularioAbierto(true)}
                  >
                    Registrar pago
                  </Boton>
                ) : null
              }
            />
          ) : (
            <Tabla
              columnas={COLUMNAS_PAGOS}
              datos={listaPagos}
              claveFila={(fila) => fila.id}
              mensajeVacio="Este préstamo aún no registra pagos"
              descripcion={`Pagos aplicados al préstamo ${prestamo.numeroPrestamo}`}
            />
          )}
        </div>
      </>
    ),
  };

  return (
    <section className="chn-prestamo-detalle" data-captura="prestamo-detalle">
      <EncabezadoPagina
        titulo={`Préstamo ${prestamo.numeroPrestamo}`}
        migas={[{ etiqueta: 'Préstamos', a: '/prestamos' }, { etiqueta: prestamo.numeroPrestamo }]}
        descripcion={
          <span className="chn-prestamo-detalle__subtitulo">
            <Etiqueta tono={tonoEstadoPrestamo(prestamo.estado)}>
              {etiquetaEstadoPrestamo(prestamo.estado)}
            </Etiqueta>
            <span>
              {prestamo.nombreCliente} · DPI {prestamo.identificacionCliente}
            </span>
          </span>
        }
      />

      {!vigente && (
        <Alerta tono="exito" titulo="Préstamo liquidado">
          Este préstamo fue liquidado en su totalidad: el saldo pendiente es cero y no
          admite nuevos pagos. El plan de amortización y el historial quedan como respaldo.
        </Alerta>
      )}

      <div className="chn-prestamo-detalle__superior">
        <div
          className="chn-prestamo-detalle__saldo chn-anim-subir chn-anim-escalonado"
          style={{ '--indice': '0' }}
          data-captura="prestamo-saldo"
        >
          <p className="chn-prestamo-detalle__saldo-etiqueta">Saldo pendiente</p>
          <p className="chn-prestamo-detalle__saldo-valor">{moneda(prestamo.saldoPendiente)}</p>

          <BarraProgreso
            className="chn-prestamo-detalle__saldo-avance"
            valor={avance}
            maximo={100}
            tono={tonoAvance}
            etiqueta="Avance de pago"
          />

          <dl className="chn-prestamo-detalle__saldo-datos">
            <div>
              <dt>Total pagado</dt>
              <dd>{moneda(prestamo.totalPagado)}</dd>
            </div>
            <div>
              <dt>Monto total a pagar</dt>
              <dd>{moneda(prestamo.montoTotalAPagar)}</dd>
            </div>
          </dl>

          {puedeAbonar && (
            <Boton
              variante={ACCION_PAGAR.variante}
              ancho="completo"
              iconoIzquierda={ACCION_PAGAR.icono}
              data-captura="prestamo-registrar-pago"
              onClick={() => setFormularioAbierto(true)}
            >
              Registrar pago
            </Boton>
          )}
        </div>

        <div className="chn-prestamo-detalle__fichas">
          <Tarjeta
            titulo="Datos del cliente"
            className="chn-anim-subir chn-anim-escalonado"
            style={{ '--indice': '1' }}
          >
            <DefinicionDatos datos={datosCliente} columnas={1} />
          </Tarjeta>
          <Tarjeta
            titulo="Condiciones del préstamo"
            className="chn-anim-subir chn-anim-escalonado"
            style={{ '--indice': '2' }}
          >
            <DefinicionDatos datos={datosCondiciones} columnas={2} />
          </Tarjeta>
        </div>
      </div>

      <Tarjeta
        sinPadding
        className="chn-anim-subir chn-anim-escalonado"
        style={{ '--indice': '3' }}
      >
        <div className="chn-prestamo-detalle__pestanas" data-captura="prestamo-pestanas">
          <div
            role="tablist"
            aria-label="Secciones del préstamo"
            className="chn-prestamo-detalle__tablist"
            ref={setListaPestanas}
            onKeyDown={navegarPestanas}
          >
            {PESTANAS.map((pestana) => (
              <button
                key={pestana.id}
                type="button"
                role="tab"
                id={`pestana-${pestana.id}`}
                aria-selected={pestanaActiva === pestana.id}
                aria-controls={`panel-${pestana.id}`}
                tabIndex={pestanaActiva === pestana.id ? 0 : -1}
                ref={(nodo) => {
                  referenciasPestanas.current[pestana.id] = nodo;
                }}
                className={`chn-prestamo-detalle__pestana ${
                  pestanaActiva === pestana.id ? 'chn-prestamo-detalle__pestana--activa' : ''
                }`.trim()}
                onClick={() => setPestanaActiva(pestana.id)}
              >
                {pestana.etiqueta}
              </button>
            ))}

            {/* Decorativo: el estado ya lo expone aria-selected. */}
            <span
              className="chn-prestamo-detalle__indicador"
              aria-hidden="true"
              style={{
                width: `${indicador.ancho}px`,
                transform: `translateX(${indicador.izquierda}px)`,
                opacity: indicador.ancho > 0 ? 1 : 0,
              }}
            />
          </div>
        </div>

        {PESTANAS.map((pestana) => (
          <div
            key={pestana.id}
            role="tabpanel"
            id={`panel-${pestana.id}`}
            aria-labelledby={`pestana-${pestana.id}`}
            tabIndex={0}
            hidden={pestanaActiva !== pestana.id}
            className="chn-prestamo-detalle__panel"
          >
            {pestanaActiva === pestana.id ? (
              <div className="chn-anim-aparecer">{contenidoPestanas[pestana.id]}</div>
            ) : null}
          </div>
        ))}
      </Tarjeta>

      <Modal
        abierto={formularioAbierto}
        titulo={`Registrar pago — ${prestamo.numeroPrestamo}`}
        onCerrar={() => setFormularioAbierto(false)}
        ancho="md"
      >
        <PagoFormulario
          prestamoIdInicial={prestamo.id}
          prestamos={[prestamo]}
          alRegistrar={registrarAbono}
          alCancelar={() => setFormularioAbierto(false)}
        />
      </Modal>
    </section>
  );
}

export default PrestamoDetalle;
