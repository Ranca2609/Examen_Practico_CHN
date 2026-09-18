import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';

import {
  Alerta,
  Boton,
  Cargando,
  EncabezadoPagina,
  Etiqueta,
  GraficaBarras,
  GraficaColumnas,
  GraficaDona,
  MedidorSegmentado,
  Tabla,
  Tarjeta,
  TarjetaIndicador,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto.jsx';
import { useResumen } from '../../aplicacion/hooks/useResumen.js';
import { useSolicitudes } from '../../aplicacion/hooks/useSolicitudes.js';
import { accion } from '../../dominio/acciones.js';
import {
  TIPOS_PRESTAMO,
  etiquetaEstadoSolicitud,
  etiquetaTipoPrestamo,
  tonoEstadoSolicitud,
} from '../../dominio/catalogos.js';
import { MESES_CORTOS, MESES_LARGOS } from '../../dominio/fechas.js';
import {
  fecha as formatoFecha,
  moneda,
  monedaCorta,
  numero as formatoNumero,
  porcentaje as formatoPorcentaje,
} from '../../dominio/formato.js';
import './Tablero.css';

const ULTIMAS_SOLICITUDES = 5;

const ACCION_CREAR = accion('crear');
const ACCION_REFRESCAR = accion('refrescar');

const textoDeError = (error, respaldo) =>
  typeof error === 'string' ? error : (error?.mensaje ?? respaldo);

function porcentajeDe(parte, total) {
  if (!total || total <= 0) return 0;
  return Math.min(100, (parte / total) * 100);
}

// Los importes del backend pueden llegar como number o como texto.
function aNumero(valor) {
  const convertido = Number(valor);
  return Number.isFinite(convertido) ? convertido : 0;
}

const conteo = (cantidad, singular, plural) =>
  `${formatoNumero(cantidad)} ${cantidad === 1 ? singular : plural}`;

const capitalizar = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1);

// Tokens propios de la dona: mismo significado que los tonos de estado, con la luminosidad
// separada para que los segmentos se distingan también con daltonismo.
const COLOR_POR_TONO = {
  exito: 'var(--color-grafica-estado-exito)',
  advertencia: 'var(--color-grafica-estado-advertencia)',
  peligro: 'var(--color-grafica-estado-peligro)',
  info: 'var(--color-info)',
  neutro: 'var(--color-neutro)',
};

// El orden define el anillo, en sentido horario desde las 12.
const ESTADOS_EN_GRAFICA = [
  { estado: 'APROBADA', campo: 'solicitudesAprobadas' },
  { estado: 'EN_PROCESO', campo: 'solicitudesEnProceso' },
  { estado: 'RECHAZADA', campo: 'solicitudesRechazadas' },
];

// Orden del enum TipoPrestamo: desempata los tipos con el mismo monto.
const ORDEN_TIPOS = Object.keys(TIPOS_PRESTAMO);

function datosEstados(resumen) {
  return ESTADOS_EN_GRAFICA.map(({ estado, campo }) => ({
    clave: estado,
    rotulo: etiquetaEstadoSolicitud(estado),
    valor: aNumero(resumen[campo]),
    color: COLOR_POR_TONO[tonoEstadoSolicitud(estado)] ?? COLOR_POR_TONO.neutro,
  }));
}

// El backend entrega 12 meses que terminan en el mes en curso (America/Guatemala): se resalta el último.
function datosRecaudacion(meses) {
  return meses.map((mes, indice) => {
    const numeroMes = aNumero(mes.mes);
    const nombreMes = MESES_LARGOS[numeroMes - 1];
    return {
      clave: mes.periodo ?? `${mes.anio}-${mes.mes}`,
      rotulo: MESES_CORTOS[numeroMes - 1] ?? String(mes.periodo ?? ''),
      grupo: mes.anio,
      titulo: nombreMes ? `${capitalizar(nombreMes)} de ${mes.anio}` : String(mes.periodo ?? ''),
      valor: aNumero(mes.monto),
      resaltado: indice === meses.length - 1,
      detalles: [{ etiqueta: 'Pagos registrados', valor: formatoNumero(aNumero(mes.cantidadPagos)) }],
    };
  });
}

function datosCarteraPorTipo(tipos) {
  return tipos
    .map((tipo) => {
      const cantidad = aNumero(tipo.cantidadPrestamos);
      return {
        clave: tipo.tipoPrestamo,
        rotulo: etiquetaTipoPrestamo(tipo.tipoPrestamo),
        valor: aNumero(tipo.montoAprobado),
        nota: cantidad === 0 ? 'Sin préstamos' : conteo(cantidad, 'préstamo', 'préstamos'),
        detalles: [
          { etiqueta: 'Préstamos', valor: formatoNumero(cantidad) },
          { etiqueta: 'Saldo pendiente', valor: moneda(aNumero(tipo.saldoPendiente)) },
          { etiqueta: 'Total recuperado', valor: moneda(aNumero(tipo.totalRecuperado)) },
        ],
      };
    })
    .sort(
      (a, b) => b.valor - a.valor || ORDEN_TIPOS.indexOf(a.clave) - ORDEN_TIPOS.indexOf(b.clave),
    );
}

const COLUMNAS_ULTIMAS = [
  { clave: 'numeroSolicitud', encabezado: 'Solicitud', ancho: '11rem', codigo: true },
  { clave: 'nombreCliente', encabezado: 'Cliente' },
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

export function Tablero() {
  const navegar = useNavigate();
  const { usuario, tienePermiso } = useAutenticacion();
  const { resumen, cargando, error, recargar } = useResumen();
  const {
    solicitudes,
    cargando: cargandoSolicitudes,
    error: errorSolicitudes,
  } = useSolicitudes({ tamanoInicial: ULTIMAS_SOLICITUDES });

  const puedeCrearCliente = tienePermiso('CREAR_CLIENTE');
  const puedeGestionarSolicitud = tienePermiso('GESTIONAR_SOLICITUD');

  // Tono suave en "Nuevo cliente": la acción principal del tablero es la solicitud.
  const acciones =
    !puedeCrearCliente && !puedeGestionarSolicitud ? null : (
      <>
        {puedeCrearCliente ? (
          <Boton
            variante={ACCION_CREAR.variante}
            tono="suave"
            iconoIzquierda={ACCION_CREAR.icono}
            onClick={() => navegar('/clientes', { state: { nuevo: true } })}
            data-captura="tablero-nuevo-cliente"
          >
            Nuevo cliente
          </Boton>
        ) : null}
        {puedeGestionarSolicitud ? (
          <Boton
            variante={ACCION_CREAR.variante}
            iconoIzquierda={ACCION_CREAR.icono}
            onClick={() => navegar('/solicitudes/nueva')}
            data-captura="tablero-nueva-solicitud"
          >
            Nueva solicitud
          </Boton>
        ) : null}
      </>
    );

  const estados = useMemo(() => (resumen ? datosEstados(resumen) : []), [resumen]);
  const recaudacion = useMemo(
    () => datosRecaudacion(resumen?.recaudacionMensual ?? []),
    [resumen],
  );
  const carteraPorTipo = useMemo(
    () => datosCarteraPorTipo(resumen?.carteraPorTipo ?? []),
    [resumen],
  );

  const totalSolicitudes = estados.reduce((suma, fila) => suma + fila.valor, 0);
  const totalRecaudado = recaudacion.reduce((suma, mes) => suma + mes.valor, 0);

  // Lo recuperado incluye intereses: se compara contra recuperado + saldo, no contra el
  // capital aprobado, que sobrestimaría el avance.
  const totalRecuperado = aNumero(resumen?.totalRecuperado);
  const saldoPendiente = aNumero(resumen?.saldoPendienteTotal);
  const porcentajeRecuperado = porcentajeDe(totalRecuperado, totalRecuperado + saldoPendiente);

  return (
    <div className="chn-tablero">
      <EncabezadoPagina
        titulo="Tablero"
        descripcion={
          usuario
            ? `Bienvenido(a), ${usuario.nombreCompleto}. Este es el estado actual de la cartera de préstamos.`
            : 'Estado actual de la cartera de préstamos.'
        }
        acciones={acciones}
      />

      {error ? (
        <Alerta tono="peligro" titulo="No se pudo cargar el resumen">
          {textoDeError(error, 'Intente nuevamente en unos momentos.')}
          <div className="chn-tablero__reintento">
            <Boton
              variante={ACCION_REFRESCAR.variante}
              tamano="sm"
              iconoIzquierda={ACCION_REFRESCAR.icono}
              onClick={() => recargar?.()}
            >
              Reintentar
            </Boton>
          </div>
        </Alerta>
      ) : null}

      {cargando && !resumen ? <Cargando texto="Cargando indicadores..." altura="220px" /> : null}

      {/* Solo con respuesta real: así un estado vacío significa "sin datos" y no "falló". */}
      {resumen ? (
        <>
          <section className="chn-tablero__indicadores" data-captura="tablero-indicadores">
            <TarjetaIndicador
              titulo="Clientes registrados"
              valor={formatoNumero(resumen.totalClientes)}
              detalle="Expedientes activos en el sistema"
              icono="clientes"
              tono="info"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 0 }}
              data-captura="indicador-clientes"
            />
            <TarjetaIndicador
              titulo="Solicitudes en proceso"
              valor={formatoNumero(resumen.solicitudesEnProceso)}
              detalle="Pendientes de resolución"
              icono="solicitudes"
              tono="advertencia"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 1 }}
              data-captura="indicador-solicitudes"
            />
            <TarjetaIndicador
              titulo="Préstamos vigentes"
              valor={formatoNumero(resumen.prestamosVigentes)}
              detalle={`${formatoNumero(resumen.prestamosLiquidados)} liquidados`}
              icono="prestamos"
              tono="primario"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 2 }}
              data-captura="indicador-prestamos"
            />
            <TarjetaIndicador
              titulo="Saldo pendiente total"
              valor={monedaCorta(resumen.saldoPendienteTotal)}
              detalle="Capital e intereses por recuperar"
              icono="dinero"
              tono="peligro"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 3 }}
              data-captura="indicador-saldo"
            />
          </section>

          <section className="chn-tablero__paneles">
            <Tarjeta
              titulo="Solicitudes por estado"
              subtitulo={`${formatoNumero(totalSolicitudes)} ${
                totalSolicitudes === 1 ? 'solicitud registrada' : 'solicitudes registradas'
              }`}
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 4 }}
              data-captura="tablero-grafica-estados"
            >
              <GraficaDona
                titulo="Solicitudes por estado"
                datos={estados}
                unidad={['solicitud', 'solicitudes']}
                nombreSerie="Solicitudes"
                encabezadoCategoria="Estado"
                cargando={cargando}
                tituloVacio="Sin solicitudes"
                mensajeVacio="Todavía no se ha registrado ninguna solicitud de crédito."
              />
            </Tarjeta>

            <Tarjeta
              titulo="Cartera"
              subtitulo="Montos acumulados en quetzales"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 5 }}
              data-captura="tablero-cartera"
            >
              <div className="chn-tablero__cartera">
                <dl className="chn-tablero__montos">
                  <div>
                    <dt>Monto total aprobado</dt>
                    <dd>{moneda(resumen.montoTotalAprobado)}</dd>
                  </div>
                  <div>
                    <dt>Total recuperado</dt>
                    <dd>{moneda(resumen.totalRecuperado)}</dd>
                  </div>
                  <div>
                    <dt>Saldo pendiente</dt>
                    <dd>{moneda(resumen.saldoPendienteTotal)}</dd>
                  </div>
                </dl>
                <MedidorSegmentado
                  etiqueta="Recuperado del total a pagar (capital + intereses)"
                  valorTexto={formatoPorcentaje(porcentajeRecuperado, 1)}
                  formatoValor={monedaCorta}
                  segmentos={[
                    {
                      clave: 'recuperado',
                      rotulo: 'Recuperado',
                      valor: totalRecuperado,
                      color: 'var(--color-grafica-serie)',
                    },
                    {
                      clave: 'pendiente',
                      rotulo: 'Pendiente de cobro',
                      valor: saldoPendiente,
                      color: 'var(--color-grafica-pista)',
                    },
                  ]}
                />
              </div>
            </Tarjeta>
          </section>

          <section className="chn-tablero__paneles">
            <Tarjeta
              titulo="Recaudación mensual"
              subtitulo="Pagos en efectivo de los últimos 12 meses"
              acciones={
                <div className="chn-tablero__total">
                  <span className="chn-tablero__total-etiqueta">Total del período</span>
                  <span className="chn-tablero__total-valor">{moneda(totalRecaudado)}</span>
                </div>
              }
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 6 }}
              data-captura="tablero-grafica-recaudacion"
            >
              <GraficaColumnas
                titulo="Recaudación mensual"
                datos={recaudacion}
                nombreSerie="Monto recaudado"
                encabezadoCategoria="Mes"
                rotuloResaltado="Mes en curso"
                formatoValor={moneda}
                formatoEtiqueta={monedaCorta}
                formatoEje={monedaCorta}
                pasoMinimoEje={1}
                cargando={cargando}
                tituloVacio="Sin pagos en el período"
                mensajeVacio="No se registraron pagos en los últimos 12 meses."
              />
            </Tarjeta>

            <Tarjeta
              titulo="Cartera por tipo de préstamo"
              subtitulo="Monto aprobado por tipo, de mayor a menor"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': 7 }}
              data-captura="tablero-grafica-tipos"
            >
              <GraficaBarras
                titulo="Monto aprobado por tipo de préstamo"
                datos={carteraPorTipo}
                nombreSerie="Monto aprobado"
                encabezadoCategoria="Tipo de préstamo"
                formatoValor={moneda}
                formatoEtiqueta={monedaCorta}
                cargando={cargando}
                tituloVacio="Sin préstamos aprobados"
                mensajeVacio="Cuando se apruebe una solicitud, su préstamo aparecerá aquí."
              />
            </Tarjeta>
          </section>
        </>
      ) : null}

      <Tarjeta
        titulo="Últimas solicitudes"
        subtitulo="Las cinco solicitudes más recientes"
        sinPadding
        data-captura="tablero-solicitudes-recientes"
        acciones={
          <Boton variante="texto" tamano="sm" iconoIzquierda="flechaDerecha" onClick={() => navegar('/solicitudes')}>
            Ver todas
          </Boton>
        }
      >
        {errorSolicitudes ? (
          <div className="chn-tablero__aviso">
            <Alerta tono="peligro" titulo="No se pudieron cargar las solicitudes">
              {textoDeError(errorSolicitudes, 'Intente nuevamente en unos momentos.')}
            </Alerta>
          </div>
        ) : (
          <Tabla
            columnas={COLUMNAS_ULTIMAS}
            datos={solicitudes}
            cargando={cargandoSolicitudes}
            descripcion="Últimas solicitudes de crédito recibidas"
            mensajeVacio="Todavía no se han registrado solicitudes de crédito."
          />
        )}
      </Tarjeta>
    </div>
  );
}

export default Tablero;
