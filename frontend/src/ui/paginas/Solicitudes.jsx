import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import {
  Alerta,
  Boton,
  CampoFormulario,
  CampoNumero,
  CampoRangoFechas,
  CampoSelect,
  CampoTexto,
  EncabezadoPagina,
  EstadoVacio,
  Etiqueta,
  Paginador,
  PanelFiltros,
  Tabla,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto.jsx';
import { useSolicitudes } from '../../aplicacion/hooks/useSolicitudes.js';
import { useOpcionesClientes } from '../../aplicacion/hooks/useClientes.js';
import * as adaptadorSolicitudes from '../../infraestructura/http/adaptadorSolicitudes.js';
import { accion } from '../../dominio/acciones.js';
import {
  etiquetaEstadoSolicitud,
  etiquetaTipoPrestamo,
  LIMITES,
  opcionesEstadoSolicitud,
  opcionesTipoPrestamo,
  tonoEstadoSolicitud,
} from '../../dominio/catalogos.js';
import * as formato from '../../dominio/formato.js';
import './Solicitudes.css';

const ESTADOS_RESUMEN = ['EN_PROCESO', 'APROBADA', 'RECHAZADA'];

// Valor '' significa "sin filtrar".
const OPCION_TODOS_ESTADOS = { valor: '', etiqueta: 'Todos los estados' };
const OPCION_TODOS_CLIENTES = { valor: '', etiqueta: 'Todos los clientes' };
const OPCION_TODOS_TIPOS = { valor: '', etiqueta: 'Todos los tipos' };

const ACCION_CREAR = accion('crear');
const ACCION_VER = accion('ver');
const ACCION_APROBAR = accion('aprobar');
const ACCION_RECHAZAR = accion('rechazar');
const ACCION_LIMPIAR = accion('limpiar');
const ACCION_REFRESCAR = accion('refrescar');

const COLUMNAS = [
  {
    clave: 'numeroSolicitud',
    encabezado: 'No. de solicitud',
    ancho: '11rem',
    codigo: true,
    render: (fila) => <span className="chn-solicitudes-numero">{fila.numeroSolicitud}</span>,
  },
  {
    clave: 'nombreCliente',
    encabezado: 'Cliente',
    render: (fila) => (
      <span className="chn-solicitudes-cliente">
        <span className="chn-solicitudes-cliente__nombre">{fila.nombreCliente}</span>
        <span className="chn-solicitudes-cliente__dpi">{`DPI ${fila.identificacionCliente}`}</span>
      </span>
    ),
  },
  {
    clave: 'tipoPrestamo',
    encabezado: 'Tipo',
    ancho: '8rem',
    render: (fila) => etiquetaTipoPrestamo(fila.tipoPrestamo),
  },
  {
    clave: 'montoSolicitado',
    encabezado: 'Monto solicitado',
    alineacion: 'derecha',
    ancho: '10rem',
    render: (fila) => formato.moneda(fila.montoSolicitado),
  },
  {
    clave: 'plazoMeses',
    encabezado: 'Plazo',
    alineacion: 'derecha',
    ancho: '7rem',
    render: (fila) => `${fila.plazoMeses} meses`,
  },
  {
    clave: 'tasaInteresAnual',
    encabezado: 'Tasa',
    alineacion: 'derecha',
    ancho: '6.5rem',
    render: (fila) => formato.porcentaje(fila.tasaInteresAnual),
  },
  {
    clave: 'estado',
    encabezado: 'Estado',
    alineacion: 'centro',
    ancho: '9rem',
    render: (fila) => (
      <Etiqueta tono={tonoEstadoSolicitud(fila.estado)}>{etiquetaEstadoSolicitud(fila.estado)}</Etiqueta>
    ),
  },
  {
    clave: 'fechaSolicitud',
    encabezado: 'Fecha',
    alineacion: 'derecha',
    ancho: '7rem',
    render: (fila) => formato.fecha(fila.fechaSolicitud),
  },
];

function resumenDeTotal(total) {
  return total === 1
    ? '1 solicitud encontrada'
    : `${formato.numero(total)} solicitudes encontradas`;
}

export function Solicitudes() {
  const navegar = useNavigate();
  const { tienePermiso } = useAutenticacion();

  const {
    solicitudes,
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
  } = useSolicitudes();

  const { opciones: opcionesClientes, cargando: cargandoClientes } = useOpcionesClientes();

  const [conteos, setConteos] = useState({});

  const puedeGestionar = tienePermiso('GESTIONAR_SOLICITUD');

  // Ref y no dependencia: el conteo se recalcula una vez por consulta, no en cada tecla.
  const filtrosRef = useRef(filtros);
  filtrosRef.current = filtros;

  // tamano=1 porque solo interesa totalElementos; /resumen no sirve porque ignora los filtros.
  useEffect(() => {
    if (cargando) return undefined;

    let activo = true;

    // Cada estado se cuenta dentro del resto de criterios, sin el propio estado.
    const base = { ...filtrosRef.current };
    delete base.estado;

    Promise.all(
      ESTADOS_RESUMEN.map((estadoResumen) =>
        adaptadorSolicitudes
          .listar({ ...base, estado: estadoResumen, pagina: 0, tamano: 1 })
          .then((respuesta) => [estadoResumen, respuesta?.totalElementos ?? 0])
          // Informativo: si falla se muestra "—" sin aviso; el listado ya reporta sus errores.
          .catch(() => [estadoResumen, null]),
      ),
    ).then((pares) => {
      if (activo) setConteos(Object.fromEntries(pares));
    });

    return () => {
      activo = false;
    };
  }, [cargando, solicitudes]);

  const opcionesEstado = useMemo(() => [OPCION_TODOS_ESTADOS, ...opcionesEstadoSolicitud()], []);
  const opcionesTipo = useMemo(() => [OPCION_TODOS_TIPOS, ...opcionesTipoPrestamo()], []);

  const opcionesCliente = useMemo(
    () => [OPCION_TODOS_CLIENTES, ...opcionesClientes],
    [opcionesClientes],
  );

  const alCambiar = (clave) => (evento) => establecerFiltro(clave, evento.target.value);

  const alternarEstado = (valor) =>
    establecerFiltro('estado', filtros.estado === valor ? '' : valor);

  // La acción viaja en el state para que el detalle abra directamente su modal.
  const irAlDetalle = (solicitud, accionPreseleccionada) =>
    navegar(
      `/solicitudes/${solicitud.id}`,
      accionPreseleccionada ? { state: { accion: accionPreseleccionada } } : undefined,
    );

  const accionesFila = (fila) => (
    <span className="chn-solicitudes-acciones">
      <Boton
        variante={ACCION_VER.variante}
        tono="suave"
        tamano="sm"
        soloIcono
        iconoIzquierda={ACCION_VER.icono}
        onClick={() => irAlDetalle(fila)}
        aria-label={`Ver el detalle de la solicitud ${fila.numeroSolicitud}`}
        title="Ver el detalle"
      />

      {fila.estado === 'EN_PROCESO' && puedeGestionar ? (
        <>
          <Boton
            variante={ACCION_APROBAR.variante}
            tono="suave"
            tamano="sm"
            soloIcono
            iconoIzquierda={ACCION_APROBAR.icono}
            onClick={() => irAlDetalle(fila, 'aprobar')}
            aria-label={`Aprobar la solicitud ${fila.numeroSolicitud}`}
            title="Aprobar"
          />
          <Boton
            variante={ACCION_RECHAZAR.variante}
            tono="suave"
            tamano="sm"
            soloIcono
            iconoIzquierda={ACCION_RECHAZAR.icono}
            onClick={() => irAlDetalle(fila, 'rechazar')}
            aria-label={`Rechazar la solicitud ${fila.numeroSolicitud}`}
            title="Rechazar"
          />
        </>
      ) : null}
    </span>
  );

  const hayFiltros = filtrosActivos > 0;
  const sinResultados = !cargando && !error && solicitudes.length === 0;

  return (
    <section className="chn-solicitudes">
      <EncabezadoPagina
        titulo="Solicitudes de préstamo"
        descripcion="Consulte, filtre y resuelva las solicitudes de crédito registradas en el sistema."
        acciones={
          puedeGestionar ? (
            <Boton
              variante={ACCION_CREAR.variante}
              iconoIzquierda={ACCION_CREAR.icono}
              data-captura="solicitudes-nueva"
              onClick={() => navegar('/solicitudes/nueva')}
            >
              Nueva solicitud
            </Boton>
          ) : null
        }
      />

      {/* PanelFiltros solo lee abiertoInicial al montar. */}
      <PanelFiltros
        cantidadActivos={filtrosActivos}
        onLimpiar={limpiarFiltros}
        abiertoInicial={hayFiltros}
        chips={chips}
        resumen={<span aria-live="polite">{resumenDeTotal(totalElementos)}</span>}
      >
        <div className="chn-solicitudes-filtro--ancho">
          <CampoFormulario
            etiqueta="Búsqueda"
            htmlFor="filtro-solicitud-busqueda"
            ayuda="Número de solicitud, cliente o destino."
          >
            <CampoTexto
              id="filtro-solicitud-busqueda"
              nombre="busqueda"
              valor={filtros.busqueda}
              onChange={alCambiar('busqueda')}
              placeholder="Ejemplo: SC-001-2026 o Remodelación"
              autoComplete="off"
              data-captura="filtro-solicitud-busqueda"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario etiqueta="Estado" htmlFor="solicitudes-filtro-estado">
          <CampoSelect
            id="solicitudes-filtro-estado"
            nombre="estado"
            valor={filtros.estado}
            onChange={alCambiar('estado')}
            opciones={opcionesEstado}
            data-captura="solicitudes-filtro-estado"
          />
        </CampoFormulario>

        <CampoFormulario
          etiqueta="Cliente"
          htmlFor="solicitudes-filtro-cliente"
          ayuda={cargandoClientes ? 'Cargando el catálogo de clientes...' : undefined}
        >
          <CampoSelect
            id="solicitudes-filtro-cliente"
            nombre="clienteId"
            valor={String(filtros.clienteId ?? '')}
            onChange={alCambiar('clienteId')}
            opciones={opcionesCliente}
            deshabilitado={cargandoClientes}
            buscable
            data-captura="solicitudes-filtro-cliente"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Tipo de préstamo" htmlFor="filtro-solicitud-tipo">
          <CampoSelect
            id="filtro-solicitud-tipo"
            nombre="tipoPrestamo"
            valor={filtros.tipoPrestamo}
            onChange={alCambiar('tipoPrestamo')}
            opciones={opcionesTipo}
            data-captura="filtro-solicitud-tipo"
          />
        </CampoFormulario>

        <div className="chn-solicitudes-filtro--ancho">
          <CampoFormulario
            etiqueta="Monto solicitado"
            htmlFor="filtro-solicitud-monto-min"
            ayuda="En quetzales; deje vacío el extremo que no quiera acotar."
          >
            <div className="chn-solicitudes-par">
              <CampoNumero
                id="filtro-solicitud-monto-min"
                nombre="montoMinimo"
                valor={filtros.montoMinimo}
                onChange={alCambiar('montoMinimo')}
                min={0}
                paso="0.01"
                prefijo="Q"
                aria-label="Monto solicitado desde"
                data-captura="filtro-solicitud-monto-min"
              />
              <span className="chn-solicitudes-par__union">a</span>
              <CampoNumero
                id="filtro-solicitud-monto-max"
                nombre="montoMaximo"
                valor={filtros.montoMaximo}
                onChange={alCambiar('montoMaximo')}
                min={0}
                paso="0.01"
                prefijo="Q"
                aria-label="Monto solicitado hasta"
                data-captura="filtro-solicitud-monto-max"
              />
            </div>
          </CampoFormulario>
        </div>

        <div className="chn-solicitudes-filtro--ancho">
          <CampoFormulario
            etiqueta="Plazo"
            htmlFor="filtro-solicitud-plazo-min"
            ayuda={`En meses, de ${LIMITES.PLAZO_MINIMO} a ${LIMITES.PLAZO_MAXIMO}.`}
          >
            <div className="chn-solicitudes-par">
              <CampoNumero
                id="filtro-solicitud-plazo-min"
                nombre="plazoMinimo"
                valor={filtros.plazoMinimo}
                onChange={alCambiar('plazoMinimo')}
                min={1}
                paso="1"
                sufijo="meses"
                aria-label="Plazo desde"
                data-captura="filtro-solicitud-plazo-min"
              />
              <span className="chn-solicitudes-par__union">a</span>
              <CampoNumero
                id="filtro-solicitud-plazo-max"
                nombre="plazoMaximo"
                valor={filtros.plazoMaximo}
                onChange={alCambiar('plazoMaximo')}
                min={1}
                paso="1"
                sufijo="meses"
                aria-label="Plazo hasta"
                data-captura="filtro-solicitud-plazo-max"
              />
            </div>
          </CampoFormulario>
        </div>

        <div className="chn-solicitudes-filtro--ancho">
          <CampoFormulario
            etiqueta="Fecha de solicitud"
            htmlFor="filtro-solicitud-fecha-desde"
            ayuda="Periodo en que se registró la solicitud."
          >
            <CampoRangoFechas
              idDesde="filtro-solicitud-fecha-desde"
              idHasta="filtro-solicitud-fecha-hasta"
              nombreDesde="fechaDesde"
              nombreHasta="fechaHasta"
              valorDesde={filtros.fechaDesde}
              valorHasta={filtros.fechaHasta}
              onChangeDesde={alCambiar('fechaDesde')}
              onChangeHasta={alCambiar('fechaHasta')}
              max={formato.hoyParaInput()}
              capturaDesde="filtro-solicitud-fecha-desde"
              capturaHasta="filtro-solicitud-fecha-hasta"
            />
          </CampoFormulario>
        </div>
      </PanelFiltros>

      {error ? (
        <Alerta tono="peligro" titulo="No se pudo cargar el listado">
          {error.mensaje}
          <div className="chn-solicitudes-reintento">
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
      ) : null}

      <div className="chn-solicitudes-resumen">
        <p className="chn-solicitudes-resumen__titulo">Solicitudes por estado</p>
        {ESTADOS_RESUMEN.map((estadoResumen) => {
          const activo = filtros.estado === estadoResumen;
          const nombreEstado = etiquetaEstadoSolicitud(estadoResumen);

          return (
            <button
              key={estadoResumen}
              type="button"
              className="chn-solicitudes-resumen__filtro"
              aria-pressed={activo}
              onClick={() => alternarEstado(estadoResumen)}
              title={
                activo
                  ? `Quitar el filtro de estado ${nombreEstado}`
                  : `Ver solo las solicitudes en estado ${nombreEstado}`
              }
            >
              <Etiqueta tono={tonoEstadoSolicitud(estadoResumen)}>
                {`${nombreEstado}: ${conteos[estadoResumen] ?? formato.SIN_DATO}`}
              </Etiqueta>
            </button>
          );
        })}
      </div>

      {sinResultados ? (
        <EstadoVacio
          icono={hayFiltros ? 'buscar' : 'solicitudes'}
          titulo={hayFiltros ? 'Sin resultados' : 'Aún no hay solicitudes'}
          mensaje={
            hayFiltros
              ? 'Ninguna solicitud coincide con los filtros aplicados. Ajuste los criterios o límpielos para ver todo el listado.'
              : 'Todavía no se han registrado solicitudes de crédito.'
          }
          accion={
            hayFiltros ? (
              <Boton
                variante={ACCION_LIMPIAR.variante}
                iconoIzquierda={ACCION_LIMPIAR.icono}
                onClick={limpiarFiltros}
              >
                Limpiar filtros
              </Boton>
            ) : puedeGestionar ? (
              <Boton
                variante={ACCION_CREAR.variante}
                iconoIzquierda={ACCION_CREAR.icono}
                onClick={() => navegar('/solicitudes/nueva')}
              >
                Registrar la primera solicitud
              </Boton>
            ) : null
          }
        />
      ) : (
        <>
          <Tabla
            columnas={COLUMNAS}
            datos={solicitudes}
            claveFila={(fila) => fila.id}
            cargando={cargando}
            mensajeVacio="No hay solicitudes que coincidan con los filtros."
            acciones={accionesFila}
            descripcion="Listado de solicitudes de préstamo"
            data-captura="solicitudes-tabla"
          />

          <Paginador
            pagina={pagina}
            totalPaginas={totalPaginas}
            totalElementos={totalElementos}
            tamano={tamano}
            onCambiarPagina={setPagina}
            onCambiarTamano={cambiarTamano}
          />
        </>
      )}
    </section>
  );
}

export default Solicitudes;
