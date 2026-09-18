import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import {
  Alerta,
  BarraBusqueda,
  Boton,
  CampoFormulario,
  CampoRangoFechas,
  CampoSelect,
  Cargando,
  DefinicionDatos,
  DialogoConfirmacion,
  EncabezadoPagina,
  EstadoVacio,
  Etiqueta,
  Modal,
  Paginador,
  PanelFiltros,
  Tabla,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto.jsx';
import { useClienteDetalle, useClientes } from '../../aplicacion/hooks/useClientes.js';
import { accion } from '../../dominio/acciones.js';
import {
  etiquetaEstadoPrestamo,
  etiquetaEstadoSolicitud,
  etiquetaTipoPrestamo,
  tonoEstadoPrestamo,
  tonoEstadoSolicitud,
} from '../../dominio/catalogos.js';
import { hoyIso } from '../../dominio/fechas.js';
import { fecha as formatoFecha, moneda, numero as formatoNumero } from '../../dominio/formato.js';
import ClienteFormulario from './ClienteFormulario.jsx';
import './Clientes.css';

const textoDeError = (error, respaldo) =>
  typeof error === 'string' ? error : (error?.mensaje ?? respaldo);

const ACCION_CREAR = accion('crear');
const ACCION_VER = accion('ver');
const ACCION_EDITAR = accion('editar');
const ACCION_ELIMINAR = accion('eliminar');
const ACCION_LIMPIAR = accion('limpiar');
const ACCION_REFRESCAR = accion('refrescar');
const ACCION_CANCELAR = accion('cancelar');

// Texto y no booleano: la API espera el query param literal 'true'/'false'.
const OPCIONES_ACTIVO = [
  { valor: 'true', etiqueta: 'Solo activos' },
  { valor: 'false', etiqueta: 'Solo inactivos' },
];

function textoResumen(total) {
  return total === 1 ? '1 cliente encontrado' : `${formatoNumero(total)} clientes encontrados`;
}

const COLUMNAS_CLIENTES = [
  {
    clave: 'nombreCompleto',
    encabezado: 'Nombre completo',
    render: (fila) => (
      <div className="chn-clientes__identidad">
        <span className="chn-clientes__nombre">{fila.nombreCompleto}</span>
        <span className="chn-clientes__dpi">DPI {fila.numeroIdentificacion}</span>
      </div>
    ),
  },
  {
    clave: 'fechaNacimiento',
    encabezado: 'Fecha de nacimiento',
    ancho: '11rem',
    render: (fila) => (
      <div className="chn-clientes__nacimiento">
        <span>{formatoFecha(fila.fechaNacimiento)}</span>
        <span className="chn-clientes__edad">{fila.edad} años</span>
      </div>
    ),
  },
  { clave: 'telefono', encabezado: 'Teléfono', ancho: '8rem' },
  { clave: 'correoElectronico', encabezado: 'Correo electrónico' },
  {
    clave: 'direccion',
    encabezado: 'Dirección',
    render: (fila) => (
      <span className="chn-clientes__direccion" title={fila.direccion}>
        {fila.direccion}
      </span>
    ),
  },
  {
    clave: 'activo',
    encabezado: 'Estado',
    alineacion: 'centro',
    ancho: '8rem',
    render: (fila) => (
      <Etiqueta tono={fila.activo === false ? 'neutro' : 'exito'}>
        {fila.activo === false ? 'Inactivo' : 'Activo'}
      </Etiqueta>
    ),
  },
];

const COLUMNAS_SOLICITUDES = [
  { clave: 'numeroSolicitud', encabezado: 'Solicitud', ancho: '11rem', codigo: true },
  { clave: 'tipoPrestamo', encabezado: 'Tipo', render: (fila) => etiquetaTipoPrestamo(fila.tipoPrestamo) },
  {
    clave: 'montoSolicitado',
    encabezado: 'Monto',
    alineacion: 'derecha',
    render: (fila) => moneda(fila.montoSolicitado),
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
    ancho: '8rem',
    render: (fila) => formatoFecha(fila.fechaSolicitud),
  },
];

const COLUMNAS_PRESTAMOS = [
  {
    clave: 'numeroPrestamo',
    encabezado: 'Préstamo',
    ancho: '11rem',
    codigo: true,
    render: (fila) => (
      <Link className="chn-clientes__enlace" to={`/prestamos/${fila.id}`}>
        {fila.numeroPrestamo}
      </Link>
    ),
  },
  {
    clave: 'montoAprobado',
    encabezado: 'Aprobado',
    alineacion: 'derecha',
    render: (fila) => moneda(fila.montoAprobado),
  },
  {
    clave: 'saldoPendiente',
    encabezado: 'Saldo',
    alineacion: 'derecha',
    render: (fila) => moneda(fila.saldoPendiente),
  },
  {
    clave: 'estado',
    encabezado: 'Estado',
    alineacion: 'centro',
    ancho: '9rem',
    render: (fila) => (
      <Etiqueta tono={tonoEstadoPrestamo(fila.estado)}>{etiquetaEstadoPrestamo(fila.estado)}</Etiqueta>
    ),
  },
];

function ModalExpediente({ clienteFila, onCerrar }) {
  const { cliente, solicitudes, prestamos, cargando, error } = useClienteDetalle(clienteFila.id);
  // Pinta de inmediato con la fila del listado mientras llega el expediente completo.
  const datos = cliente ?? clienteFila;

  return (
    <Modal
      abierto
      titulo={`Expediente de ${datos.nombreCompleto}`}
      onCerrar={onCerrar}
      ancho="lg"
      pie={
        <Boton
          variante={ACCION_CANCELAR.variante}
          iconoIzquierda={ACCION_CANCELAR.icono}
          onClick={onCerrar}
        >
          Cerrar
        </Boton>
      }
    >
      <DefinicionDatos
        columnas={2}
        datos={[
          { etiqueta: 'Nombre completo', valor: datos.nombreCompleto },
          { etiqueta: 'Número de DPI', valor: datos.numeroIdentificacion },
          {
            etiqueta: 'Fecha de nacimiento',
            valor: `${formatoFecha(datos.fechaNacimiento)} (${datos.edad} años)`,
          },
          { etiqueta: 'Teléfono', valor: datos.telefono },
          { etiqueta: 'Correo electrónico', valor: datos.correoElectronico },
          { etiqueta: 'Dirección', valor: datos.direccion },
          { etiqueta: 'Registrado el', valor: formatoFecha(datos.fechaCreacion) },
          { etiqueta: 'Estado', valor: datos.activo ? 'Activo' : 'Inactivo' },
        ]}
      />

      {error ? (
        <Alerta tono="peligro" titulo="Historial no disponible">
          {textoDeError(error, 'No fue posible consultar las solicitudes y préstamos del cliente.')}
        </Alerta>
      ) : null}

      {cargando ? (
        <Cargando texto="Cargando historial del cliente..." altura="160px" />
      ) : (
        <div className="chn-clientes__historial">
          <section className="chn-anim-subir chn-anim-escalonado" style={{ '--indice': 0 }}>
            <h3 className="chn-clientes__historial-titulo">
              Solicitudes ({formatoNumero(solicitudes.length)})
            </h3>
            <Tabla
              columnas={COLUMNAS_SOLICITUDES}
              datos={solicitudes}
              descripcion="Solicitudes de crédito del cliente"
              mensajeVacio="El cliente no tiene solicitudes registradas."
            />
          </section>
          <section className="chn-anim-subir chn-anim-escalonado" style={{ '--indice': 2 }}>
            <h3 className="chn-clientes__historial-titulo">Préstamos ({formatoNumero(prestamos.length)})</h3>
            <Tabla
              columnas={COLUMNAS_PRESTAMOS}
              datos={prestamos}
              descripcion="Préstamos del cliente"
              mensajeVacio="El cliente no tiene préstamos desembolsados."
            />
          </section>
        </div>
      )}
    </Modal>
  );
}

export function Clientes() {
  const { tienePermiso } = useAutenticacion();
  const ubicacion = useLocation();
  const navegar = useNavigate();

  const {
    clientes,
    cargando,
    error,
    guardando,
    filtros,
    establecerFiltro,
    limpiarFiltros,
    filtrosActivos,
    chips,
    busqueda,
    setBusqueda,
    limpiarBusqueda,
    pagina,
    tamano,
    totalElementos,
    totalPaginas,
    setPagina,
    cambiarTamano,
    crear,
    actualizar,
    eliminar,
    recargar,
  } = useClientes();

  const [clienteEnDetalle, setClienteEnDetalle] = useState(null);
  const [clienteEnEdicion, setClienteEnEdicion] = useState(null);
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [clientePorEliminar, setClientePorEliminar] = useState(null);

  // Se evalúa solo al montar: después el estado abierto lo gobierna PanelFiltros.
  const [panelAbiertoAlMontar] = useState(() => filtrosActivos > 0);

  const puedeCrear = tienePermiso('CREAR_CLIENTE');
  const puedeEditar = tienePermiso('EDITAR_CLIENTE');
  const puedeEliminar = tienePermiso('ELIMINAR_CLIENTE');

  const alCambiarFiltro = useCallback(
    (evento) => establecerFiltro(evento.target.name, evento.target.value),
    [establecerFiltro],
  );

  const abrirAlta = () => {
    setClienteEnEdicion(null);
    setFormularioAbierto(true);
  };

  const abrirEdicion = (cliente) => {
    setClienteEnEdicion(cliente);
    setFormularioAbierto(true);
  };

  const cerrarFormulario = () => {
    setFormularioAbierto(false);
    setClienteEnEdicion(null);
  };

  // Acceso rápido del tablero; se limpia el state para que recargar o volver atrás no lo reabra.
  useEffect(() => {
    if (ubicacion.state?.nuevo && puedeCrear) {
      setClienteEnEdicion(null);
      setFormularioAbierto(true);
      navegar('/clientes', { replace: true, state: null });
    }
  }, [ubicacion.state, puedeCrear, navegar]);

  // Sin try/catch: el error debe llegar a useFormulario para pintar los errores por campo.
  const guardarCliente = async (valores) => {
    if (clienteEnEdicion) {
      await actualizar(clienteEnEdicion.id, {
        nombre: valores.nombre,
        apellido: valores.apellido,
        direccion: valores.direccion,
        correoElectronico: valores.correoElectronico,
        telefono: valores.telefono,
      });
    } else {
      await crear(valores);
    }
    cerrarFormulario();
  };

  const confirmarEliminacion = async () => {
    try {
      await eliminar(clientePorEliminar.id);
      setClientePorEliminar(null);
    } catch {
      // useClientes ya notificó el error; el diálogo sigue abierto para reintentar o cancelar.
    }
  };

  const accionesFila = (fila) => (
    <div className="chn-clientes__acciones">
      <Boton
        variante={ACCION_VER.variante}
        tono="suave"
        tamano="sm"
        soloIcono
        iconoIzquierda={ACCION_VER.icono}
        aria-label={`Ver el expediente de ${fila.nombreCompleto}`}
        title="Ver expediente"
        onClick={() => setClienteEnDetalle(fila)}
        data-captura="cliente-accion-ver"
      />
      {puedeEditar ? (
        <Boton
          variante={ACCION_EDITAR.variante}
          tono="suave"
          tamano="sm"
          soloIcono
          iconoIzquierda={ACCION_EDITAR.icono}
          aria-label={`Editar los datos de ${fila.nombreCompleto}`}
          title="Editar cliente"
          onClick={() => abrirEdicion(fila)}
          data-captura="cliente-accion-editar"
        />
      ) : null}
      {puedeEliminar ? (
        <Boton
          variante={ACCION_ELIMINAR.variante}
          tono="suave"
          tamano="sm"
          soloIcono
          iconoIzquierda={ACCION_ELIMINAR.icono}
          aria-label={`Eliminar a ${fila.nombreCompleto}`}
          title="Eliminar cliente"
          onClick={() => setClientePorEliminar(fila)}
          data-captura="cliente-accion-eliminar"
        />
      ) : null}
    </div>
  );

  const hayFiltros = filtrosActivos > 0;
  const sinRegistros = !cargando && !error && clientes.length === 0;
  const tope = hoyIso();

  return (
    <div className="chn-clientes">
      <EncabezadoPagina
        titulo="Clientes"
        descripcion="Expedientes de las personas que pueden solicitar un préstamo."
        acciones={
          puedeCrear ? (
            <Boton
              variante={ACCION_CREAR.variante}
              iconoIzquierda={ACCION_CREAR.icono}
              onClick={abrirAlta}
              data-captura="clientes-nuevo"
            >
              Nuevo cliente
            </Boton>
          ) : null
        }
      />

      {/* Escribe en el mismo criterio que el panel: lo tecleado aparece también como chip. */}
      <div className="chn-clientes__barra">
        <BarraBusqueda
          className="chn-clientes__buscador"
          valor={busqueda}
          onChange={setBusqueda}
          onLimpiar={limpiarBusqueda}
          placeholder="Buscar por nombre, DPI o correo electrónico"
          data-captura="clientes-buscar"
        />
      </div>

      <PanelFiltros
        cantidadActivos={filtrosActivos}
        onLimpiar={limpiarFiltros}
        abiertoInicial={panelAbiertoAlMontar}
        chips={chips}
        resumen={
          <span aria-live="polite">{textoResumen(totalElementos)}</span>
        }
      >
        <div className="chn-clientes__filtro-ancho">
          <CampoFormulario
            etiqueta="Fecha de nacimiento"
            htmlFor="filtro-nacimiento-desde"
            ayuda="Clientes nacidos dentro del periodo."
          >
            <CampoRangoFechas
              idDesde="filtro-nacimiento-desde"
              idHasta="filtro-nacimiento-hasta"
              nombreDesde="nacimientoDesde"
              nombreHasta="nacimientoHasta"
              valorDesde={filtros.nacimientoDesde}
              valorHasta={filtros.nacimientoHasta}
              onChangeDesde={alCambiarFiltro}
              onChangeHasta={alCambiarFiltro}
              max={tope}
              capturaDesde="filtro-nacimiento-desde"
              capturaHasta="filtro-nacimiento-hasta"
            />
          </CampoFormulario>
        </div>

        <div className="chn-clientes__filtro-ancho">
          <CampoFormulario
            etiqueta="Fecha de registro"
            htmlFor="filtro-creacion-desde"
            ayuda="Día en que se creó el expediente en el sistema."
          >
            <CampoRangoFechas
              idDesde="filtro-creacion-desde"
              idHasta="filtro-creacion-hasta"
              nombreDesde="creacionDesde"
              nombreHasta="creacionHasta"
              valorDesde={filtros.creacionDesde}
              valorHasta={filtros.creacionHasta}
              onChangeDesde={alCambiarFiltro}
              onChangeHasta={alCambiarFiltro}
              max={tope}
              capturaDesde="filtro-creacion-desde"
              capturaHasta="filtro-creacion-hasta"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario etiqueta="Estado del expediente" htmlFor="filtro-cliente-activo">
          <CampoSelect
            id="filtro-cliente-activo"
            nombre="activo"
            valor={filtros.activo}
            onChange={alCambiarFiltro}
            opciones={OPCIONES_ACTIVO}
            placeholder="Todos"
            data-captura="filtro-cliente-activo"
          />
        </CampoFormulario>
      </PanelFiltros>

      {error ? (
        <Alerta tono="peligro" titulo="No se pudo cargar el listado de clientes">
          {textoDeError(error, 'Intente nuevamente en unos momentos.')}
          <div className="chn-clientes__reintento">
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

      {sinRegistros ? (
        <EstadoVacio
          icono={hayFiltros ? 'buscar' : 'clientes'}
          titulo={hayFiltros ? 'Sin resultados' : 'Aún no hay clientes'}
          mensaje={
            hayFiltros
              ? 'Ningún cliente coincide con los filtros aplicados. Ajuste los criterios o límpielos para ver todo el padrón.'
              : 'Registre el primer cliente para poder crear solicitudes de crédito.'
          }
          accion={
            hayFiltros ? (
              <Boton
                variante={ACCION_LIMPIAR.variante}
                tono="suave"
                iconoIzquierda={ACCION_LIMPIAR.icono}
                onClick={limpiarFiltros}
                data-captura="clientes-vacio-limpiar"
              >
                Limpiar filtros
              </Boton>
            ) : puedeCrear ? (
              <Boton
                variante={ACCION_CREAR.variante}
                iconoIzquierda={ACCION_CREAR.icono}
                onClick={abrirAlta}
              >
                Nuevo cliente
              </Boton>
            ) : null
          }
        />
      ) : (
        <>
          <Tabla
            columnas={COLUMNAS_CLIENTES}
            datos={clientes}
            cargando={cargando}
            descripcion="Listado de clientes registrados"
            mensajeVacio="No hay clientes para mostrar."
            acciones={accionesFila}
            data-captura="clientes-tabla"
          />
          <Paginador
            pagina={pagina}
            totalPaginas={totalPaginas}
            totalElementos={totalElementos}
            tamano={tamano}
            onCambiarPagina={setPagina}
            onCambiarTamano={cambiarTamano}
            data-captura="clientes-paginador"
          />
        </>
      )}

      {clienteEnDetalle ? (
        <ModalExpediente clienteFila={clienteEnDetalle} onCerrar={() => setClienteEnDetalle(null)} />
      ) : null}

      {/* key: remonta el formulario en cada apertura para no arrastrar valores anteriores. */}
      {formularioAbierto ? (
        <ClienteFormulario
          key={clienteEnEdicion ? clienteEnEdicion.id : 'nuevo'}
          abierto
          cliente={clienteEnEdicion}
          onCerrar={cerrarFormulario}
          onGuardar={guardarCliente}
        />
      ) : null}

      <DialogoConfirmacion
        abierto={Boolean(clientePorEliminar)}
        titulo="Eliminar cliente"
        mensaje={
          clientePorEliminar
            ? `Se eliminará el expediente de ${clientePorEliminar.nombreCompleto} (DPI ${clientePorEliminar.numeroIdentificacion}) y, con él, TODAS sus solicitudes, préstamos y pagos registrados. Esta acción no se puede deshacer. ¿Desea continuar?`
            : ''
        }
        textoConfirmar="Sí, eliminar"
        textoCancelar="Cancelar"
        variante="peligro"
        cargando={guardando}
        onConfirmar={confirmarEliminacion}
        onCancelar={() => setClientePorEliminar(null)}
      />
    </div>
  );
}

export default Clientes;
