import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import {
  Alerta,
  Boton,
  CampoFormulario,
  CampoNumero,
  CampoRangoFechas,
  CampoSelect,
  CampoTexto,
  Cargando,
  EncabezadoPagina,
  EstadoVacio,
  Modal,
  Paginador,
  PanelFiltros,
  Tabla,
  Tarjeta,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto';
import { useOpcionesClientes } from '../../aplicacion/hooks/useClientes';
import { usePagos } from '../../aplicacion/hooks/usePagos';
import { useOpcionesPrestamos } from '../../aplicacion/hooks/usePrestamos';
import { accion } from '../../dominio/acciones';
import { etiquetaFormaPago } from '../../dominio/catalogos';
import { fechaHora, moneda, numero } from '../../dominio/formato';
import PagoFormulario from './PagoFormulario.jsx';
import './Pagos.css';

const ACCION_PAGAR = accion('pagar');
const ACCION_VER = accion('ver');
const ACCION_IMPRIMIR = accion('imprimir');
const ACCION_LIMPIAR = accion('limpiar');
const ACCION_CANCELAR = accion('cancelar');
const ACCION_REFRESCAR = accion('refrescar');

const COLUMNAS = [
  {
    clave: 'numeroRecibo',
    encabezado: 'No. de recibo',
    ancho: '11rem',
    codigo: true,
    render: (fila) => <span className="chn-pagos__recibo">{fila.numeroRecibo}</span>,
  },
  {
    clave: 'fechaPago',
    encabezado: 'Fecha y hora',
    ancho: '9.5rem',
    render: (fila) => fechaHora(fila.fechaPago),
  },
  // Sin ancho mínimo el nombre se parte en una línea por palabra junto a los números.
  { clave: 'nombreCliente', encabezado: 'Cliente', ancho: '12rem' },
  {
    clave: 'numeroPrestamo',
    encabezado: 'No. de préstamo',
    ancho: '11rem',
    codigo: true,
    render: (fila) => (
      <Link className="chn-pagos__enlace" to={`/prestamos/${fila.prestamoId}`}>
        {fila.numeroPrestamo}
      </Link>
    ),
  },
  {
    clave: 'monto',
    encabezado: 'Monto',
    alineacion: 'derecha',
    ancho: '8rem',
    render: (fila) => <strong className="chn-pagos__monto">{moneda(fila.monto)}</strong>,
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
    clave: 'usuarioRegistro',
    encabezado: 'Registrado por',
    ancho: '9rem',
    render: (fila) => fila.usuarioRegistro ?? '—',
  },
];

function textoResumen(total) {
  const cantidad = Number(total) || 0;
  return cantidad === 1 ? '1 registro encontrado' : `${numero(cantidad)} registros encontrados`;
}

export function Pagos() {
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const { tienePermiso } = useAutenticacion();

  const puedeRegistrarPago = tienePermiso('REGISTRAR_PAGO');

  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [prestamoPreseleccionado, setPrestamoPreseleccionado] = useState(null);
  const [comprobanteVisible, setComprobanteVisible] = useState(null);

  // usePagos ya notifica el resultado con un toast; la página no vuelve a hacerlo.
  const {
    pagos,
    cargando,
    error,
    comprobante,
    limpiarComprobante,
    filtros,
    establecerFiltro,
    limpiarFiltros,
    filtrosActivos,
    chips,
    pagina,
    tamano,
    totalElementos,
    totalPaginas,
    setPagina,
    cambiarTamano,
    registrar,
    recargar,
  } = usePagos({ tamanoInicial: 10 });

  const { clientes, opciones: opcionesCliente, cargando: cargandoClientes } =
    useOpcionesClientes();
  // Todos, no solo vigentes: el filtro debe alcanzar pagos de préstamos ya liquidados.
  const {
    prestamos,
    opciones: opcionesPrestamo,
    cargando: cargandoPrestamos,
  } = useOpcionesPrestamos({ soloVigentes: false });

  const [panelAbiertoInicial] = useState(() => filtrosActivos > 0);

  // Se limpia el state de la ruta para que recargar no reabra el formulario.
  useEffect(() => {
    const idPrestamo = ubicacion.state?.prestamoId;
    if (!idPrestamo) return;

    setPrestamoPreseleccionado(idPrestamo);
    setFormularioAbierto(true);
    navegar('/pagos', { replace: true, state: null });
  }, [ubicacion.state, navegar]);

  useEffect(() => {
    if (comprobante) setComprobanteVisible(comprobante);
  }, [comprobante]);

  const cerrarFormulario = () => {
    setFormularioAbierto(false);
    setPrestamoPreseleccionado(null);
  };

  const abrirFormulario = () => {
    setPrestamoPreseleccionado(null);
    setFormularioAbierto(true);
  };

  const cerrarComprobante = () => {
    setComprobanteVisible(null);
    limpiarComprobante();
  };

  // Sin try/catch: el error debe llegar a PagoFormulario para mantener el modal abierto.
  const registrarPago = async (datos) => {
    await registrar(datos);
    cerrarFormulario();
  };

  // El pago no trae el DPI; se toma del catálogo de clientes.
  const dpiDelComprobante = useMemo(() => {
    if (!comprobanteVisible) return null;
    const cliente = clientes.find((registro) => registro.id === comprobanteVisible.clienteId);
    return cliente?.numeroIdentificacion ?? null;
  }, [comprobanteVisible, clientes]);

  const accionesFila = (fila) => (
    <Boton
      variante={ACCION_VER.variante}
      tono="suave"
      tamano="sm"
      soloIcono
      iconoIzquierda={ACCION_VER.icono}
      aria-label={`Ver el comprobante del recibo ${fila.numeroRecibo}`}
      title="Ver comprobante"
      data-captura="pago-accion-comprobante"
      onClick={() => setComprobanteVisible(fila)}
    />
  );

  const cargaInicial = cargando && pagos.length === 0;
  const sinResultados = !cargando && !error && pagos.length === 0;

  return (
    <section className="chn-pagos">
      <EncabezadoPagina
        titulo="Pagos en efectivo"
        descripcion="Registre los abonos recibidos en ventanilla y consulte el historial de pagos con su comprobante."
        acciones={
          puedeRegistrarPago && (
            <Boton
              variante={ACCION_PAGAR.variante}
              iconoIzquierda={ACCION_PAGAR.icono}
              data-captura="pagos-nuevo"
              onClick={abrirFormulario}
            >
              Registrar pago
            </Boton>
          )
        }
      />

      {error && (
        <Alerta tono="peligro" titulo="No se pudo cargar el historial de pagos">
          {error.mensaje ?? 'Intente nuevamente en unos momentos.'}
          <div className="chn-pagos__reintento">
            <Boton
              variante={ACCION_REFRESCAR.variante}
              tamano="sm"
              iconoIzquierda={ACCION_REFRESCAR.icono}
              onClick={recargar}
            >
              Reintentar
            </Boton>
          </div>
        </Alerta>
      )}

      <PanelFiltros
        cantidadActivos={filtrosActivos}
        onLimpiar={limpiarFiltros}
        abiertoInicial={panelAbiertoInicial}
        chips={chips}
        resumen={textoResumen(totalElementos)}
      >
        <div className="chn-pagos__filtro chn-pagos__filtro--ancho">
          <CampoFormulario
            etiqueta="Búsqueda"
            htmlFor="filtro-pago-busqueda"
            ayuda="Número de recibo, de préstamo o cliente."
          >
            <CampoTexto
              id="filtro-pago-busqueda"
              nombre="busqueda"
              valor={filtros.busqueda}
              onChange={(evento) => establecerFiltro('busqueda', evento.target.value)}
              placeholder="Ejemplo: RC-001-2026-000012-1"
              data-captura="filtro-pago-busqueda"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario etiqueta="Cliente" htmlFor="filtro-pago-cliente">
          <CampoSelect
            id="filtro-pago-cliente"
            nombre="clienteId"
            valor={filtros.clienteId}
            onChange={(evento) => establecerFiltro('clienteId', evento.target.value)}
            opciones={opcionesCliente}
            placeholder="Todos los clientes"
            deshabilitado={cargandoClientes}
            /* Siempre buscable, sin umbral de opciones: la lista crece con la cartera. */
            buscable
            data-captura="filtro-pago-cliente"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Préstamo" htmlFor="filtro-pago-prestamo">
          <CampoSelect
            id="filtro-pago-prestamo"
            nombre="prestamoId"
            valor={filtros.prestamoId}
            onChange={(evento) => establecerFiltro('prestamoId', evento.target.value)}
            opciones={opcionesPrestamo}
            placeholder="Todos los préstamos"
            deshabilitado={cargandoPrestamos}
            buscable
            data-captura="filtro-pago-prestamo"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Monto desde (Q)" htmlFor="filtro-pago-monto-min">
          <CampoNumero
            id="filtro-pago-monto-min"
            nombre="montoMinimo"
            valor={filtros.montoMinimo}
            onChange={(evento) => establecerFiltro('montoMinimo', evento.target.value)}
            min={0}
            paso={0.01}
            prefijo="Q"
            data-captura="filtro-pago-monto-min"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Monto hasta (Q)" htmlFor="filtro-pago-monto-max">
          <CampoNumero
            id="filtro-pago-monto-max"
            nombre="montoMaximo"
            valor={filtros.montoMaximo}
            onChange={(evento) => establecerFiltro('montoMaximo', evento.target.value)}
            min={0}
            paso={0.01}
            prefijo="Q"
            data-captura="filtro-pago-monto-max"
          />
        </CampoFormulario>

        <div className="chn-pagos__filtro chn-pagos__filtro--ancho">
          <CampoFormulario etiqueta="Fecha de pago" htmlFor="filtro-pago-fecha-desde">
            <CampoRangoFechas
              idDesde="filtro-pago-fecha-desde"
              idHasta="filtro-pago-fecha-hasta"
              nombreDesde="fechaDesde"
              nombreHasta="fechaHasta"
              valorDesde={filtros.fechaDesde}
              valorHasta={filtros.fechaHasta}
              onChangeDesde={(evento) => establecerFiltro('fechaDesde', evento.target.value)}
              onChangeHasta={(evento) => establecerFiltro('fechaHasta', evento.target.value)}
              capturaDesde="filtro-pago-fecha-desde"
              capturaHasta="filtro-pago-fecha-hasta"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario
          etiqueta="Registrado por"
          htmlFor="filtro-pago-usuario"
          ayuda="Usuario de la ventanilla que aplicó el pago."
        >
          <CampoTexto
            id="filtro-pago-usuario"
            nombre="usuarioRegistro"
            valor={filtros.usuarioRegistro}
            onChange={(evento) => establecerFiltro('usuarioRegistro', evento.target.value)}
            placeholder="Ejemplo: cajero1"
            data-captura="filtro-pago-usuario"
          />
        </CampoFormulario>
      </PanelFiltros>

      <Tarjeta titulo="Historial de pagos" sinPadding>
        <div data-captura="pagos-tabla">
          {cargaInicial ? (
            <Cargando texto="Cargando pagos..." altura="240px" />
          ) : sinResultados ? (
            <EstadoVacio
              icono="pagos"
              titulo={filtrosActivos > 0 ? 'Sin resultados' : 'Sin pagos registrados'}
              mensaje={
                filtrosActivos > 0
                  ? 'Ningún pago coincide con los filtros aplicados. Ajuste los criterios o límpielos para ver todo el historial.'
                  : 'Todavía no se ha registrado ningún pago. Use "Registrar pago" para aplicar el primer abono.'
              }
              accion={
                filtrosActivos > 0 ? (
                  <Boton
                    variante={ACCION_LIMPIAR.variante}
                    iconoIzquierda={ACCION_LIMPIAR.icono}
                    onClick={limpiarFiltros}
                  >
                    Limpiar filtros
                  </Boton>
                ) : (
                  puedeRegistrarPago && (
                    <Boton
                      variante={ACCION_PAGAR.variante}
                      iconoIzquierda={ACCION_PAGAR.icono}
                      onClick={abrirFormulario}
                    >
                      Registrar pago
                    </Boton>
                  )
                )
              }
            />
          ) : (
            <>
              <Tabla
                columnas={COLUMNAS}
                datos={pagos}
                claveFila={(fila) => fila.id}
                cargando={cargando}
                mensajeVacio="No hay pagos para mostrar."
                acciones={accionesFila}
                descripcion="Historial de pagos en efectivo aplicados a los préstamos"
              />
              <Paginador
                pagina={pagina}
                totalPaginas={totalPaginas}
                totalElementos={totalElementos}
                tamano={tamano}
                onCambiarPagina={setPagina}
                onCambiarTamano={cambiarTamano}
                data-captura="pagos-paginador"
              />
            </>
          )}
        </div>
      </Tarjeta>

      <Modal
        abierto={formularioAbierto}
        titulo="Registrar pago en efectivo"
        onCerrar={cerrarFormulario}
        ancho="md"
      >
        <PagoFormulario
          prestamoIdInicial={prestamoPreseleccionado}
          prestamos={prestamos}
          cargandoPrestamos={cargandoPrestamos}
          alRegistrar={registrarPago}
          alCancelar={cerrarFormulario}
        />
      </Modal>

      <Modal
        abierto={Boolean(comprobanteVisible)}
        titulo="Comprobante de pago"
        onCerrar={cerrarComprobante}
        ancho="sm"
        pie={
          <div className="chn-pagos__pie-modal">
            <Boton
              variante={ACCION_CANCELAR.variante}
              iconoIzquierda={ACCION_CANCELAR.icono}
              onClick={cerrarComprobante}
            >
              Cerrar
            </Boton>
            <Boton
              variante={ACCION_IMPRIMIR.variante}
              iconoIzquierda={ACCION_IMPRIMIR.icono}
              onClick={() => window.print()}
            >
              Imprimir
            </Boton>
          </div>
        }
      >
        {/* Sin clase de animación: el Modal ya escala al entrar y dos escalas encadenadas rebotan. */}
        {comprobanteVisible && (
          <article className="chn-comprobante" data-captura="pago-comprobante">
            <header className="chn-comprobante__encabezado">
              <p className="chn-comprobante__institucion">
                Crédito Hipotecario Nacional de Guatemala
              </p>
              <h3 className="chn-comprobante__titulo">Comprobante de pago</h3>
              <p className="chn-comprobante__recibo">
                Recibo No. {comprobanteVisible.numeroRecibo}
              </p>
            </header>

            <dl className="chn-comprobante__datos">
              <div className="chn-comprobante__fila">
                <dt>Fecha y hora</dt>
                <dd>{fechaHora(comprobanteVisible.fechaPago)}</dd>
              </div>
              <div className="chn-comprobante__fila">
                <dt>Cliente</dt>
                <dd>{comprobanteVisible.nombreCliente}</dd>
              </div>
              {dpiDelComprobante && (
                <div className="chn-comprobante__fila">
                  <dt>DPI</dt>
                  <dd>{dpiDelComprobante}</dd>
                </div>
              )}
              <div className="chn-comprobante__fila">
                <dt>No. de préstamo</dt>
                <dd>{comprobanteVisible.numeroPrestamo}</dd>
              </div>
              <div className="chn-comprobante__fila">
                <dt>Forma de pago</dt>
                <dd>{etiquetaFormaPago(comprobanteVisible.formaPago)}</dd>
              </div>
            </dl>

            <p className="chn-comprobante__monto">
              <span className="chn-comprobante__monto-etiqueta">Monto recibido</span>
              <span className="chn-comprobante__monto-valor">
                {moneda(comprobanteVisible.monto)}
              </span>
            </p>

            <dl className="chn-comprobante__datos">
              <div className="chn-comprobante__fila">
                <dt>Saldo anterior</dt>
                <dd>{moneda(comprobanteVisible.saldoAnterior)}</dd>
              </div>
              <div className="chn-comprobante__fila chn-comprobante__fila--destacada">
                <dt>Saldo resultante</dt>
                <dd>{moneda(comprobanteVisible.saldoPosterior)}</dd>
              </div>
              <div className="chn-comprobante__fila">
                <dt>Registrado por</dt>
                <dd>{comprobanteVisible.usuarioRegistro ?? '—'}</dd>
              </div>
              {comprobanteVisible.observaciones && (
                <div className="chn-comprobante__fila">
                  <dt>Observaciones</dt>
                  <dd>{comprobanteVisible.observaciones}</dd>
                </div>
              )}
            </dl>

            <footer className="chn-comprobante__pie">
              <p>
                Documento generado por el Sistema de Gestión de Préstamos. Conserve este
                comprobante como respaldo de su pago.
              </p>
            </footer>
          </article>
        )}
      </Modal>
    </section>
  );
}

export default Pagos;
