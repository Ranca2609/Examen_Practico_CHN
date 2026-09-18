import { useCallback, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import {
  Alerta,
  BarraProgreso,
  Boton,
  CampoFormulario,
  CampoNumero,
  CampoRangoFechas,
  CampoSelect,
  CampoTexto,
  Cargando,
  EncabezadoPagina,
  EstadoVacio,
  Etiqueta,
  Paginador,
  PanelFiltros,
  Tabla,
  Tarjeta,
  TarjetaIndicador,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto';
import { useOpcionesClientes } from '../../aplicacion/hooks/useClientes';
import { usePrestamos } from '../../aplicacion/hooks/usePrestamos';
import { useResumen } from '../../aplicacion/hooks/useResumen';
import { accion } from '../../dominio/acciones';
import {
  etiquetaEstadoPrestamo,
  opcionesEstadoPrestamo,
  tonoEstadoPrestamo,
} from '../../dominio/catalogos';
import { fecha, moneda, monedaCorta, numero } from '../../dominio/formato';
import './Prestamos.css';

const ACCION_VER = accion('ver');
const ACCION_PAGAR = accion('pagar');
const ACCION_LIMPIAR = accion('limpiar');
const ACCION_REFRESCAR = accion('refrescar');

const COLUMNAS = [
  {
    clave: 'numeroPrestamo',
    encabezado: 'No. de préstamo',
    ancho: '11rem',
    codigo: true,
    render: (fila) => (
      <Link className="chn-prestamos__enlace" to={`/prestamos/${fila.id}`}>
        {fila.numeroPrestamo}
      </Link>
    ),
  },
  {
    clave: 'nombreCliente',
    encabezado: 'Cliente',
    render: (fila) => (
      <span className="chn-prestamos__cliente">
        <span className="chn-prestamos__cliente-nombre">{fila.nombreCliente}</span>
        <span className="chn-prestamos__cliente-dpi">DPI {fila.identificacionCliente}</span>
      </span>
    ),
  },
  {
    clave: 'montoAprobado',
    encabezado: 'Monto aprobado',
    alineacion: 'derecha',
    ancho: '8.5rem',
    render: (fila) => moneda(fila.montoAprobado),
  },
  {
    clave: 'cuotaMensual',
    encabezado: 'Cuota mensual',
    alineacion: 'derecha',
    ancho: '8.5rem',
    render: (fila) => moneda(fila.cuotaMensual),
  },
  {
    clave: 'montoTotalAPagar',
    encabezado: 'Total a pagar',
    alineacion: 'derecha',
    ancho: '8.5rem',
    render: (fila) => moneda(fila.montoTotalAPagar),
  },
  {
    clave: 'saldoPendiente',
    encabezado: 'Saldo pendiente',
    alineacion: 'derecha',
    ancho: '9rem',
    render: (fila) => (
      <strong className="chn-prestamos__saldo">{moneda(fila.saldoPendiente)}</strong>
    ),
  },
  {
    clave: 'porcentajePagado',
    encabezado: 'Avance de pago',
    ancho: '11rem',
    render: (fila) => (
      <BarraProgreso
        valor={Number(fila.porcentajePagado ?? 0)}
        maximo={100}
        tono={Number(fila.porcentajePagado ?? 0) > 50 ? 'exito' : 'info'}
        etiqueta="Pagado"
      />
    ),
  },
  {
    clave: 'estado',
    encabezado: 'Estado',
    alineacion: 'centro',
    ancho: '7.5rem',
    render: (fila) => (
      <Etiqueta tono={tonoEstadoPrestamo(fila.estado)}>
        {etiquetaEstadoPrestamo(fila.estado)}
      </Etiqueta>
    ),
  },
  {
    clave: 'fechaDesembolso',
    encabezado: 'Desembolso',
    ancho: '7rem',
    render: (fila) => fecha(fila.fechaDesembolso),
  },
];

// La etiqueta del CampoFormulario nombra el criterio; cada extremo necesita su propio aria-label.
function ParejaImportes({
  idMinimo,
  idMaximo,
  nombreMinimo,
  nombreMaximo,
  valorMinimo,
  valorMaximo,
  onCambio,
  etiquetaMinimo,
  etiquetaMaximo,
  capturaMinimo,
  capturaMaximo,
}) {
  return (
    <div className="chn-prestamos__pareja">
      <CampoNumero
        id={idMinimo}
        nombre={nombreMinimo}
        valor={valorMinimo}
        onChange={onCambio}
        prefijo="Q"
        paso={0.01}
        min={0}
        placeholder="0.00"
        aria-label={etiquetaMinimo}
        data-captura={capturaMinimo}
      />
      <span className="chn-prestamos__pareja-union" aria-hidden="true">
        a
      </span>
      <CampoNumero
        id={idMaximo}
        nombre={nombreMaximo}
        valor={valorMaximo}
        onChange={onCambio}
        prefijo="Q"
        paso={0.01}
        min={0}
        placeholder="0.00"
        aria-label={etiquetaMaximo}
        data-captura={capturaMaximo}
      />
    </div>
  );
}

export function Prestamos() {
  const navegar = useNavigate();
  const { tienePermiso } = useAutenticacion();

  const puedeRegistrarPago = tienePermiso('REGISTRAR_PAGO');

  const {
    prestamos,
    cargando,
    error,
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
    recargar,
  } = usePrestamos({ tamanoInicial: 10 });

  const { opciones: opcionesCliente, cargando: cargandoClientes } = useOpcionesClientes();
  const { resumen, cargando: cargandoResumen } = useResumen();

  const opcionesEstado = useMemo(() => opcionesEstadoPrestamo(), []);

  const cambiarCampo = useCallback(
    (evento) => establecerFiltro(evento.target.name, evento.target.value),
    [establecerFiltro],
  );

  const indicador = (valor) => (cargandoResumen ? '…' : monedaCorta(valor));

  const accionesFila = (fila) => (
    <span className="chn-prestamos__acciones">
      <Boton
        variante={ACCION_VER.variante}
        tono="suave"
        tamano="sm"
        soloIcono
        iconoIzquierda={ACCION_VER.icono}
        title="Ver el detalle"
        aria-label={`Ver el detalle del préstamo ${fila.numeroPrestamo}`}
        data-captura="prestamo-accion-ver"
        onClick={() => navegar(`/prestamos/${fila.id}`)}
      />
      {puedeRegistrarPago && fila.estado === 'VIGENTE' && (
        <Boton
          variante={ACCION_PAGAR.variante}
          tono="suave"
          tamano="sm"
          soloIcono
          iconoIzquierda={ACCION_PAGAR.icono}
          title="Registrar un pago"
          aria-label={`Registrar un pago del préstamo ${fila.numeroPrestamo}`}
          data-captura="prestamo-accion-pago"
          onClick={() => navegar('/pagos', { state: { prestamoId: fila.id } })}
        />
      )}
    </span>
  );

  const hayFiltros = filtrosActivos > 0;
  const cargaInicial = cargando && prestamos.length === 0;
  const sinResultados = !cargando && !error && prestamos.length === 0;

  const resumenEncontrados = `${numero(totalElementos)} ${
    totalElementos === 1 ? 'préstamo encontrado' : 'préstamos encontrados'
  }`;

  return (
    <section className="chn-prestamos">
      <EncabezadoPagina
        titulo="Préstamos aprobados"
        descripcion="Consulte la cartera desembolsada, el avance de pago de cada préstamo y el saldo que falta por recuperar."
      />

      {/* Totales de toda la cartera (/resumen): no dependen de los filtros ni de la página. */}
      <div className="chn-prestamos__indicadores">
        <TarjetaIndicador
          className="chn-anim-subir chn-anim-escalonado"
          style={{ '--indice': '0' }}
          titulo="Cartera aprobada"
          valor={indicador(resumen?.montoTotalAprobado)}
          detalle="Monto aprobado en toda la cartera, no solo en esta página"
          icono="dinero"
          tono="info"
        />
        <TarjetaIndicador
          className="chn-anim-subir chn-anim-escalonado"
          style={{ '--indice': '1' }}
          titulo="Saldo por recuperar"
          valor={indicador(resumen?.saldoPendienteTotal)}
          detalle="Pendiente de cobro en toda la cartera, no solo en esta página"
          icono="alerta"
          tono="advertencia"
        />
        <TarjetaIndicador
          className="chn-anim-subir chn-anim-escalonado"
          style={{ '--indice': '2' }}
          titulo="Total recuperado"
          valor={indicador(resumen?.totalRecuperado)}
          detalle="Pagos aplicados en toda la cartera, no solo en esta página"
          icono="check"
          tono="exito"
        />
      </div>

      {error && (
        <Alerta tono="peligro" titulo="No se pudo cargar la cartera de préstamos">
          {error.mensaje ?? 'Intente nuevamente en unos momentos.'}
          <div className="chn-prestamos__reintento">
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
        titulo="Filtros de la cartera"
        cantidadActivos={filtrosActivos}
        onLimpiar={limpiarFiltros}
        abiertoInicial={hayFiltros}
        resumen={resumenEncontrados}
        chips={chips}
      >
        <CampoFormulario
          etiqueta="Búsqueda"
          htmlFor="filtro-prestamo-busqueda"
          ayuda="Número de préstamo, de solicitud o cliente"
        >
          <CampoTexto
            id="filtro-prestamo-busqueda"
            nombre="busqueda"
            valor={filtros.busqueda}
            onChange={cambiarCampo}
            placeholder="Escriba para buscar"
            data-captura="filtro-prestamo-busqueda"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Estado" htmlFor="filtro-prestamo-estado">
          <CampoSelect
            id="filtro-prestamo-estado"
            nombre="estado"
            valor={filtros.estado}
            onChange={cambiarCampo}
            opciones={opcionesEstado}
            placeholder="Todos los estados"
            data-captura="prestamos-filtro-estado"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Cliente" htmlFor="filtro-prestamo-cliente">
          <CampoSelect
            id="filtro-prestamo-cliente"
            nombre="clienteId"
            valor={filtros.clienteId}
            onChange={cambiarCampo}
            opciones={opcionesCliente}
            placeholder="Todos los clientes"
            deshabilitado={cargandoClientes}
            buscable
            data-captura="filtro-prestamo-cliente"
          />
        </CampoFormulario>

        <CampoFormulario
          etiqueta="Monto aprobado"
          htmlFor="filtro-prestamo-monto-min"
          ayuda="Desde y hasta, en quetzales"
        >
          <ParejaImportes
            idMinimo="filtro-prestamo-monto-min"
            idMaximo="filtro-prestamo-monto-max"
            nombreMinimo="montoMinimo"
            nombreMaximo="montoMaximo"
            valorMinimo={filtros.montoMinimo}
            valorMaximo={filtros.montoMaximo}
            onCambio={cambiarCampo}
            etiquetaMinimo="Monto aprobado desde"
            etiquetaMaximo="Monto aprobado hasta"
            capturaMinimo="filtro-prestamo-monto-min"
            capturaMaximo="filtro-prestamo-monto-max"
          />
        </CampoFormulario>

        <CampoFormulario
          etiqueta="Saldo pendiente"
          htmlFor="filtro-prestamo-saldo-min"
          ayuda="Desde y hasta, en quetzales"
        >
          <ParejaImportes
            idMinimo="filtro-prestamo-saldo-min"
            idMaximo="filtro-prestamo-saldo-max"
            nombreMinimo="saldoMinimo"
            nombreMaximo="saldoMaximo"
            valorMinimo={filtros.saldoMinimo}
            valorMaximo={filtros.saldoMaximo}
            onCambio={cambiarCampo}
            etiquetaMinimo="Saldo pendiente desde"
            etiquetaMaximo="Saldo pendiente hasta"
            capturaMinimo="filtro-prestamo-saldo-min"
            capturaMaximo="filtro-prestamo-saldo-max"
          />
        </CampoFormulario>

        {/* Dos celdas de la rejilla: en una sola el rango de fechas se parte en dos líneas. */}
        <div className="chn-prestamos__filtro-ancho">
          <CampoFormulario
            etiqueta="Fecha de desembolso"
            htmlFor="filtro-prestamo-desembolso-desde"
          >
            <CampoRangoFechas
              idDesde="filtro-prestamo-desembolso-desde"
              idHasta="filtro-prestamo-desembolso-hasta"
              nombreDesde="desembolsoDesde"
              nombreHasta="desembolsoHasta"
              valorDesde={filtros.desembolsoDesde}
              valorHasta={filtros.desembolsoHasta}
              onChangeDesde={cambiarCampo}
              onChangeHasta={cambiarCampo}
              capturaDesde="filtro-prestamo-desembolso-desde"
              capturaHasta="filtro-prestamo-desembolso-hasta"
            />
          </CampoFormulario>
        </div>

        <div className="chn-prestamos__filtro-ancho">
          <CampoFormulario
            etiqueta="Fecha de vencimiento"
            htmlFor="filtro-prestamo-vencimiento-desde"
          >
            <CampoRangoFechas
              idDesde="filtro-prestamo-vencimiento-desde"
              idHasta="filtro-prestamo-vencimiento-hasta"
              nombreDesde="vencimientoDesde"
              nombreHasta="vencimientoHasta"
              valorDesde={filtros.vencimientoDesde}
              valorHasta={filtros.vencimientoHasta}
              onChangeDesde={cambiarCampo}
              onChangeHasta={cambiarCampo}
              capturaDesde="filtro-prestamo-vencimiento-desde"
              capturaHasta="filtro-prestamo-vencimiento-hasta"
            />
          </CampoFormulario>
        </div>
      </PanelFiltros>

      <Tarjeta titulo="Listado de préstamos" sinPadding>
        <div data-captura="prestamos-tabla">
          {cargaInicial ? (
            <Cargando texto="Cargando préstamos..." altura="240px" />
          ) : sinResultados ? (
            <EstadoVacio
              icono={hayFiltros ? 'filtro' : 'prestamos'}
              titulo={hayFiltros ? 'Sin coincidencias' : 'Sin préstamos'}
              mensaje={
                hayFiltros
                  ? 'Ningún préstamo coincide con los filtros aplicados. Ajuste los criterios o límpielos para ver toda la cartera.'
                  : 'Todavía no hay préstamos desembolsados. Un préstamo se crea al aprobar una solicitud de crédito.'
              }
              accion={
                hayFiltros ? (
                  <Boton
                    variante={ACCION_LIMPIAR.variante}
                    tono="suave"
                    iconoIzquierda={ACCION_LIMPIAR.icono}
                    onClick={limpiarFiltros}
                  >
                    Limpiar filtros
                  </Boton>
                ) : null
              }
            />
          ) : (
            <>
              <Tabla
                columnas={COLUMNAS}
                datos={prestamos}
                claveFila={(fila) => fila.id}
                cargando={cargando}
                mensajeVacio="No hay préstamos para mostrar."
                acciones={accionesFila}
                descripcion="Listado de préstamos aprobados con su saldo pendiente y avance de pago"
              />
              <Paginador
                pagina={pagina}
                totalPaginas={totalPaginas}
                totalElementos={totalElementos}
                tamano={tamano}
                onCambiarPagina={setPagina}
                onCambiarTamano={cambiarTamano}
                data-captura="prestamos-paginador"
              />
            </>
          )}
        </div>
      </Tarjeta>
    </section>
  );
}

export default Prestamos;
