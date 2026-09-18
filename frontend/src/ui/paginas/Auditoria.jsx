import { useMemo, useState } from 'react';

import {
  Alerta,
  Boton,
  CampoFormulario,
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
} from '../componentes';
import { useAuditoria } from '../../aplicacion/hooks/useAuditoria';
import { accion } from '../../dominio/acciones';
import {
  etiquetaAccionAuditoria,
  etiquetaEntidadAuditoria,
  opcionesAccionAuditoria,
  opcionesEntidadAuditoria,
  tonoAccionAuditoria,
} from '../../dominio/catalogos';
import { fechaHora, numero, recortar } from '../../dominio/formato';
import './Auditoria.css';

const ACCION_REFRESCAR = accion('refrescar');
const ACCION_LIMPIAR = accion('limpiar');

const OPCIONES_ACCION = opcionesAccionAuditoria();
const OPCIONES_ENTIDAD = opcionesEntidadAuditoria();

const COLUMNAS = [
  {
    clave: 'fechaHora',
    encabezado: 'Fecha y hora',
    ancho: '10rem',
    render: (fila) => fechaHora(fila.fechaHora),
  },
  {
    clave: 'usuario',
    encabezado: 'Usuario',
    ancho: '9rem',
    render: (fila) => <span className="chn-auditoria__usuario">{fila.usuario ?? 'anónimo'}</span>,
  },
  {
    clave: 'accion',
    encabezado: 'Acción',
    ancho: '12rem',
    render: (fila) => (
      <Etiqueta tono={tonoAccionAuditoria(fila.accion)}>
        {etiquetaAccionAuditoria(fila.accion)}
      </Etiqueta>
    ),
  },
  {
    clave: 'entidad',
    encabezado: 'Entidad',
    ancho: '8rem',
    render: (fila) => etiquetaEntidadAuditoria(fila.entidad),
  },
  {
    clave: 'entidadId',
    encabezado: 'ID',
    alineacion: 'derecha',
    ancho: '5rem',
    render: (fila) =>
      fila.entidadId === null || fila.entidadId === undefined ? '—' : fila.entidadId,
  },
  {
    clave: 'direccionIp',
    encabezado: 'Dirección IP',
    ancho: '9rem',
    render: (fila) => <span className="chn-auditoria__ip">{fila.direccionIp ?? '—'}</span>,
  },
  {
    clave: 'detalle',
    encabezado: 'Detalle',
    render: (fila) =>
      fila.detalle ? (
        <span className="chn-auditoria__detalle" title={fila.detalle}>
          {recortar(fila.detalle, 70)}
        </span>
      ) : (
        '—'
      ),
  },
];

// Tolera los nombres alternativos con que el backend puede serializar cada campo.
function normalizar(registro, indice) {
  return {
    id: registro.id ?? `registro-${indice}`,
    fechaHora: registro.fechaHora ?? registro.fecha ?? registro.marcaTiempo,
    usuario: registro.usuario ?? registro.username ?? registro.nombreUsuario,
    accion: registro.accion ?? registro.operacion,
    entidad: registro.entidad ?? registro.tipoEntidad,
    entidadId: registro.entidadId ?? registro.idEntidad,
    direccionIp: registro.direccionIp ?? registro.ip,
    detalle: registro.detalle ?? registro.descripcion,
  };
}

function textoResumen(total) {
  const cantidad = Number(total) || 0;
  return cantidad === 1 ? '1 registro encontrado' : `${numero(cantidad)} registros encontrados`;
}

export function Auditoria() {
  const {
    registros,
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
  } = useAuditoria({ tamanoInicial: 20 });

  // Se evalúa solo al montar: abre el panel si la pantalla llega con filtros ya aplicados.
  const [panelAbiertoInicial] = useState(() => filtrosActivos > 0);

  const listado = useMemo(
    () => (Array.isArray(registros) ? registros.map(normalizar) : []),
    [registros],
  );

  const cargaInicial = cargando && listado.length === 0;
  const sinResultados = !cargando && !error && listado.length === 0;

  return (
    <section className="chn-auditoria">
      <EncabezadoPagina
        titulo="Bitácora de auditoría"
        descripcion="Registro histórico de las operaciones sensibles del sistema. Disponible únicamente para el rol Administrador."
        acciones={
          <Boton
            variante={ACCION_REFRESCAR.variante}
            iconoIzquierda={ACCION_REFRESCAR.icono}
            onClick={recargar}
          >
            Actualizar
          </Boton>
        }
      />

      <Alerta tono="info" titulo="Registro inmutable">
        La bitácora se escribe automáticamente desde el servidor y no puede modificarse ni
        borrarse desde la aplicación. Conserva la creación, modificación y eliminación de
        clientes, la resolución de solicitudes, el registro de pagos y los accesos al
        sistema, junto con el usuario responsable y la dirección IP de origen.
      </Alerta>

      {error && (
        <Alerta tono="peligro" titulo="No se pudo cargar la bitácora">
          {error.mensaje ?? 'Intente nuevamente en unos momentos.'}
        </Alerta>
      )}

      <PanelFiltros
        cantidadActivos={filtrosActivos}
        onLimpiar={limpiarFiltros}
        abiertoInicial={panelAbiertoInicial}
        chips={chips}
        resumen={textoResumen(totalElementos)}
      >
        <div className="chn-auditoria__filtro chn-auditoria__filtro--ancho">
          <CampoFormulario
            etiqueta="Búsqueda"
            htmlFor="filtro-auditoria-busqueda"
            ayuda="Usuario, detalle o identificador afectado."
          >
            <CampoTexto
              id="filtro-auditoria-busqueda"
              nombre="busqueda"
              valor={filtros.busqueda}
              onChange={(evento) => establecerFiltro('busqueda', evento.target.value)}
              placeholder="Ejemplo: cliente 14"
              data-captura="filtro-auditoria-busqueda"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario etiqueta="Usuario" htmlFor="filtro-auditoria-usuario">
          <CampoTexto
            id="filtro-auditoria-usuario"
            nombre="usuario"
            valor={filtros.usuario}
            onChange={(evento) => establecerFiltro('usuario', evento.target.value)}
            placeholder="Ejemplo: admin"
            data-captura="filtro-auditoria-usuario"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Acción" htmlFor="filtro-auditoria-accion">
          <CampoSelect
            id="filtro-auditoria-accion"
            nombre="accion"
            valor={filtros.accion}
            onChange={(evento) => establecerFiltro('accion', evento.target.value)}
            opciones={OPCIONES_ACCION}
            placeholder="Todas las acciones"
            data-captura="filtro-auditoria-accion"
          />
        </CampoFormulario>

        <CampoFormulario etiqueta="Entidad" htmlFor="filtro-auditoria-entidad">
          <CampoSelect
            id="filtro-auditoria-entidad"
            nombre="entidad"
            valor={filtros.entidad}
            onChange={(evento) => establecerFiltro('entidad', evento.target.value)}
            opciones={OPCIONES_ENTIDAD}
            placeholder="Todas las entidades"
            data-captura="filtro-auditoria-entidad"
          />
        </CampoFormulario>

        <div className="chn-auditoria__filtro chn-auditoria__filtro--ancho">
          <CampoFormulario etiqueta="Fecha del registro" htmlFor="filtro-auditoria-fecha-desde">
            <CampoRangoFechas
              idDesde="filtro-auditoria-fecha-desde"
              idHasta="filtro-auditoria-fecha-hasta"
              nombreDesde="fechaDesde"
              nombreHasta="fechaHasta"
              valorDesde={filtros.fechaDesde}
              valorHasta={filtros.fechaHasta}
              onChangeDesde={(evento) => establecerFiltro('fechaDesde', evento.target.value)}
              onChangeHasta={(evento) => establecerFiltro('fechaHasta', evento.target.value)}
              capturaDesde="filtro-auditoria-fecha-desde"
              capturaHasta="filtro-auditoria-fecha-hasta"
            />
          </CampoFormulario>
        </div>
      </PanelFiltros>

      <Tarjeta titulo="Operaciones registradas" sinPadding>
        <div data-captura="auditoria-tabla">
          {cargaInicial ? (
            <Cargando texto="Cargando la bitácora..." altura="240px" />
          ) : sinResultados ? (
            <EstadoVacio
              icono="auditoria"
              titulo={filtrosActivos > 0 ? 'Sin resultados' : 'Sin registros'}
              mensaje={
                filtrosActivos > 0
                  ? 'Ninguna operación coincide con los filtros aplicados. Ajuste los criterios o límpielos para ver toda la bitácora.'
                  : 'Todavía no se ha registrado ninguna operación auditable en el sistema.'
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
                ) : undefined
              }
            />
          ) : (
            <>
              <Tabla
                columnas={COLUMNAS}
                datos={listado}
                claveFila={(fila) => fila.id}
                cargando={cargando}
                mensajeVacio="No hay registros de auditoría para mostrar."
                descripcion="Bitácora de operaciones sensibles del sistema"
              />
              <Paginador
                pagina={pagina}
                totalPaginas={totalPaginas}
                totalElementos={totalElementos}
                tamano={tamano}
                onCambiarPagina={setPagina}
                onCambiarTamano={cambiarTamano}
                data-captura="auditoria-paginador"
              />
            </>
          )}
        </div>
      </Tarjeta>
    </section>
  );
}

export default Auditoria;
