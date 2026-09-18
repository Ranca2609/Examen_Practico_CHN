import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';

import {
  Alerta,
  Boton,
  CampoFormulario,
  CampoNumero,
  CampoTextarea,
  Cargando,
  DefinicionDatos,
  EncabezadoPagina,
  EstadoVacio,
  Etiqueta,
  Icono,
  Modal,
  Tarjeta,
} from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto.jsx';
import { useSolicitud } from '../../aplicacion/hooks/useSolicitudes.js';
import { useFormulario, ERROR_GENERAL } from '../../aplicacion/hooks/useFormulario.js';
import * as adaptadorPrestamos from '../../infraestructura/http/adaptadorPrestamos.js';
import { accion } from '../../dominio/acciones.js';
import {
  etiquetaEstadoSolicitud,
  etiquetaTipoPrestamo,
  LIMITES,
  tonoEstadoSolicitud,
} from '../../dominio/catalogos.js';
import * as formato from '../../dominio/formato.js';
import { calcularCuota } from '../../dominio/amortizacion.js';
import {
  longitud,
  maximoCaracteres,
  montoEnRango,
  plazoEnRango,
  requerido,
  tasaEnRango,
} from '../../dominio/validaciones.js';
import './SolicitudDetalle.css';

// Mismos límites que valida el backend.
const MOTIVO_MINIMO = 10;
const MOTIVO_MAXIMO = 500;

const ACCION_APROBAR = accion('aprobar');
const ACCION_RECHAZAR = accion('rechazar');
const ACCION_CANCELAR = accion('cancelar');
const ACCION_VOLVER = accion('volver');

function textoResolucion(estado, resolucion) {
  const cierre =
    'La resolución quedó registrada en la bitácora de auditoría y la solicitud ya no admite cambios.';

  if (estado === 'APROBADA') {
    return `Se aprobaron ${formato.moneda(resolucion?.montoAprobado)} a ${formato.plazo(
      resolucion?.plazoAprobadoMeses,
    )} con una tasa anual de ${formato.porcentaje(
      resolucion?.tasaAprobada,
    )}; el préstamo quedó generado con su plan de pagos. ${cierre}`;
  }

  const motivo = resolucion?.motivo?.trim();
  return `Motivo del rechazo: ${motivo || 'no se registró un motivo'}. ${cierre}`;
}

export function SolicitudDetalle() {
  const { id } = useParams();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const { tienePermiso } = useAutenticacion();

  const { solicitud, cargando, error, procesando, aprobar, rechazar } = useSolicitud(id);

  const [modalAprobar, setModalAprobar] = useState(false);
  const [modalRechazar, setModalRechazar] = useState(false);
  const [prestamoGenerado, setPrestamoGenerado] = useState(null);

  // Evita que la acción del state de la ruta reabra el modal en cada recarga del detalle.
  const accionAplicada = useRef(false);

  const puedeGestionar = tienePermiso('GESTIONAR_SOLICITUD');
  const enProceso = solicitud?.estado === 'EN_PROCESO';
  const resuelta = Boolean(solicitud && !enProceso);
  const aprobada = solicitud?.estado === 'APROBADA';

  // Regla de negocio: el monto aprobado nunca excede el solicitado.
  const reglasAprobacion = useMemo(
    () => ({
      montoAprobado: [
        requerido,
        montoEnRango(0.01, Number(solicitud?.montoSolicitado ?? LIMITES.MONTO_MAXIMO)),
      ],
      plazoAprobadoMeses: [requerido, plazoEnRango()],
      tasaAprobada: [requerido, tasaEnRango()],
      motivo: maximoCaracteres(MOTIVO_MAXIMO),
    }),
    [solicitud?.montoSolicitado],
  );

  const formAprobacion = useFormulario({
    valoresIniciales: { montoAprobado: '', plazoAprobadoMeses: '', tasaAprobada: '', motivo: '' },
    reglas: reglasAprobacion,
    alEnviar: async (datos) => {
      await aprobar({
        montoAprobado: Number(datos.montoAprobado),
        plazoAprobadoMeses: Number(datos.plazoAprobadoMeses),
        tasaAprobada: Number(datos.tasaAprobada),
        motivo: datos.motivo.trim() || null,
      });
      setModalAprobar(false);
    },
  });

  const reglasRechazo = useMemo(
    () => ({ motivo: [requerido, longitud(MOTIVO_MINIMO, MOTIVO_MAXIMO)] }),
    [],
  );

  const formRechazo = useFormulario({
    valoresIniciales: { motivo: '' },
    reglas: reglasRechazo,
    alEnviar: async (datos) => {
      await rechazar({ motivo: datos.motivo.trim() });
      setModalRechazar(false);
    },
  });

  const abrirAprobacion = () => {
    formAprobacion.reiniciar({
      montoAprobado: String(solicitud.montoSolicitado ?? ''),
      plazoAprobadoMeses: String(solicitud.plazoMeses ?? ''),
      tasaAprobada: String(solicitud.tasaInteresAnual ?? ''),
      motivo: '',
    });
    setModalAprobar(true);
  };

  const abrirRechazo = () => {
    formRechazo.reiniciar({ motivo: '' });
    setModalRechazar(true);
  };

  // Acción elegida en el listado: llega en state.accion ('aprobar' | 'rechazar').
  useEffect(() => {
    const accion = ubicacion.state?.accion;
    if (!accion || accionAplicada.current) return;
    if (!solicitud || !enProceso || !puedeGestionar) return;

    accionAplicada.current = true;
    if (accion === 'aprobar') abrirAprobacion();
    if (accion === 'rechazar') abrirRechazo();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [solicitud, enProceso, puedeGestionar, ubicacion.state]);

  // La solicitud no trae el id del préstamo: se busca entre los del cliente por solicitudId.
  useEffect(() => {
    if (!solicitud || solicitud.estado !== 'APROBADA') {
      setPrestamoGenerado(null);
      return undefined;
    }

    let activo = true;
    adaptadorPrestamos
      .listar({ clienteId: solicitud.clienteId, pagina: 0, tamano: 100 })
      .then((respuesta) => {
        if (!activo) return;
        const encontrado = (respuesta?.contenido ?? []).find(
          (prestamo) => String(prestamo.solicitudId) === String(solicitud.id),
        );
        setPrestamoGenerado(encontrado ?? null);
      })
      // Dato complementario: si falla, el expediente sigue siendo válido.
      .catch(() => {
        if (activo) setPrestamoGenerado(null);
      });

    return () => {
      activo = false;
    };
  }, [solicitud]);

  const cuotaAprobacion = calcularCuota(
    formAprobacion.valores.montoAprobado,
    formAprobacion.valores.plazoAprobadoMeses,
    formAprobacion.valores.tasaAprobada,
  );

  const caracteresMotivo = formRechazo.valores.motivo.trim().length;
  const motivoInsuficiente = caracteresMotivo > 0 && caracteresMotivo < MOTIVO_MINIMO;

  if (cargando) {
    return <Cargando texto="Cargando la solicitud..." altura="320px" />;
  }

  if (error) {
    return (
      <section>
        <EncabezadoPagina
          titulo="Solicitud de préstamo"
          migas={[{ etiqueta: 'Solicitudes', a: '/solicitudes' }, { etiqueta: 'Detalle' }]}
        />
        <Alerta tono="peligro" titulo="No se pudo cargar la solicitud">
          {error.mensaje}
        </Alerta>
        <Boton
          variante={ACCION_VOLVER.variante}
          iconoIzquierda={ACCION_VOLVER.icono}
          onClick={() => navegar('/solicitudes')}
        >
          Volver al listado
        </Boton>
      </section>
    );
  }

  if (!solicitud) {
    return (
      <section>
        <EncabezadoPagina
          titulo="Solicitud de préstamo"
          migas={[{ etiqueta: 'Solicitudes', a: '/solicitudes' }, { etiqueta: 'Detalle' }]}
        />
        <EstadoVacio
          icono="solicitudes"
          titulo="Solicitud no encontrada"
          mensaje="La solicitud indicada no existe o fue retirada del sistema."
          accion={
            <Boton
              variante={ACCION_VOLVER.variante}
              iconoIzquierda={ACCION_VOLVER.icono}
              onClick={() => navegar('/solicitudes')}
            >
              Volver al listado
            </Boton>
          }
        />
      </section>
    );
  }

  const { resolucion } = solicitud;

  return (
    <section data-captura="solicitud-detalle">
      <EncabezadoPagina
        titulo={`Solicitud ${solicitud.numeroSolicitud}`}
        descripcion={`Registrada el ${formato.fecha(solicitud.fechaSolicitud)} para ${solicitud.nombreCliente}.`}
        migas={[
          { etiqueta: 'Solicitudes', a: '/solicitudes' },
          { etiqueta: solicitud.numeroSolicitud },
        ]}
        acciones={
          <div className="chn-solicitud-detalle__acciones">
            <Etiqueta tono={tonoEstadoSolicitud(solicitud.estado)}>
              {etiquetaEstadoSolicitud(solicitud.estado)}
            </Etiqueta>

            {enProceso && puedeGestionar && (
              <>
                <Boton
                  variante={ACCION_APROBAR.variante}
                  iconoIzquierda={ACCION_APROBAR.icono}
                  onClick={abrirAprobacion}
                  data-captura="solicitud-aprobar"
                >
                  Aprobar
                </Boton>
                <Boton
                  variante={ACCION_RECHAZAR.variante}
                  iconoIzquierda={ACCION_RECHAZAR.icono}
                  onClick={abrirRechazo}
                  data-captura="solicitud-rechazar"
                >
                  Rechazar
                </Boton>
              </>
            )}
          </div>
        }
      />

      {enProceso && !puedeGestionar && (
        <Alerta tono="advertencia" titulo="Solo consulta">
          Su rol permite consultar la solicitud, pero no resolverla. Comuníquese con un analista de crédito.
        </Alerta>
      )}

      <div className="chn-solicitud-detalle__rejilla">
        <div className="chn-solicitud-detalle__columna">
          <Tarjeta
            className="chn-anim-subir chn-anim-escalonado"
            style={{ '--indice': '0' }}
            titulo="Condiciones solicitadas"
            subtitulo="Tal como las pidió el cliente"
          >
            <DefinicionDatos
              columnas={2}
              datos={[
                { etiqueta: 'Monto solicitado', valor: formato.moneda(solicitud.montoSolicitado) },
                { etiqueta: 'Plazo', valor: formato.plazo(solicitud.plazoMeses) },
                { etiqueta: 'Tasa de interés anual', valor: formato.porcentaje(solicitud.tasaInteresAnual) },
                { etiqueta: 'Tipo de préstamo', valor: etiquetaTipoPrestamo(solicitud.tipoPrestamo) },
                {
                  etiqueta: 'Ingreso mensual declarado',
                  valor: formato.moneda(solicitud.ingresoMensualDeclarado),
                },
                { etiqueta: 'Fecha de solicitud', valor: formato.fechaHora(solicitud.fechaSolicitud) },
                { etiqueta: 'Destino del préstamo', valor: solicitud.destino },
                { etiqueta: 'Observaciones', valor: solicitud.observaciones },
              ]}
            />
          </Tarjeta>

          {resuelta && (
            <div
              data-captura="solicitud-resolucion"
              className="chn-anim-subir chn-anim-escalonado"
              style={{ '--indice': '2' }}
            >
              <Tarjeta
                titulo="Resolución"
                subtitulo={`Solicitud ${etiquetaEstadoSolicitud(solicitud.estado).toLowerCase()}`}
              >
                <Alerta
                  tono={aprobada ? 'exito' : 'peligro'}
                  titulo={aprobada ? 'Solicitud aprobada' : 'Solicitud rechazada'}
                >
                  {textoResolucion(solicitud.estado, resolucion)}
                </Alerta>

                <DefinicionDatos
                  className="chn-solicitud-detalle__resolucion-datos"
                  columnas={2}
                  datos={[
                    { etiqueta: 'Fecha de resolución', valor: formato.fechaHora(resolucion?.fechaResolucion) },
                    { etiqueta: 'Resuelta por', valor: resolucion?.usuarioResolucion },
                    {
                      etiqueta: 'Monto aprobado',
                      valor: resolucion?.montoAprobado ? formato.moneda(resolucion.montoAprobado) : null,
                    },
                    {
                      etiqueta: 'Plazo aprobado',
                      valor: resolucion?.plazoAprobadoMeses ? formato.plazo(resolucion.plazoAprobadoMeses) : null,
                    },
                    {
                      etiqueta: 'Tasa aprobada',
                      valor: resolucion?.tasaAprobada ? formato.porcentaje(resolucion.tasaAprobada) : null,
                    },
                    { etiqueta: 'Motivo', valor: resolucion?.motivo },
                  ]}
                />

                {solicitud.estado === 'APROBADA' && prestamoGenerado && (
                  <div className="chn-solicitud-detalle__prestamo">
                    <p className="chn-solicitud-detalle__prestamo-texto">
                      Préstamo generado:{' '}
                      <span className="chn-solicitud-detalle__prestamo-numero">
                        {prestamoGenerado.numeroPrestamo}
                      </span>
                      {' · Cuota mensual de '}
                      {formato.moneda(prestamoGenerado.cuotaMensual)}
                    </p>
                    <Link className="chn-solicitud-detalle__enlace" to={`/prestamos/${prestamoGenerado.id}`}>
                      Ver el préstamo
                      <Icono nombre="flechaDerecha" tamano={16} />
                    </Link>
                  </div>
                )}
              </Tarjeta>
            </div>
          )}
        </div>

        <div className="chn-solicitud-detalle__columna">
          <Tarjeta
            className="chn-anim-subir chn-anim-escalonado"
            style={{ '--indice': '1' }}
            titulo="Datos del cliente"
          >
            <DefinicionDatos
              columnas={1}
              datos={[
                { etiqueta: 'Nombre completo', valor: solicitud.nombreCliente },
                { etiqueta: 'DPI', valor: solicitud.identificacionCliente },
              ]}
            />
            <Link className="chn-solicitud-detalle__enlace" to="/clientes">
              Ver la ficha del cliente
              <Icono nombre="flechaDerecha" tamano={16} />
            </Link>
          </Tarjeta>
        </div>
      </div>

      <Modal
        abierto={modalAprobar}
        titulo={`Aprobar la solicitud ${solicitud.numeroSolicitud}`}
        onCerrar={() => setModalAprobar(false)}
        ancho="md"
        data-captura="form-aprobacion"
        pie={
          <>
            <Boton
              variante={ACCION_CANCELAR.variante}
              tono="suave"
              iconoIzquierda={ACCION_CANCELAR.icono}
              onClick={() => setModalAprobar(false)}
              deshabilitado={procesando}
            >
              Cancelar
            </Boton>
            <Boton
              variante={ACCION_APROBAR.variante}
              iconoIzquierda={ACCION_APROBAR.icono}
              cargando={formAprobacion.enviando || procesando}
              onClick={formAprobacion.manejarEnviar}
              data-captura="form-aprobacion-confirmar"
            >
              Confirmar aprobación
            </Boton>
          </>
        }
      >
        <Alerta tono="advertencia" titulo="Esta acción genera el préstamo">
          Al aprobar se creará automáticamente el préstamo con su plan de pagos y la solicitud
          no podrá modificarse.
        </Alerta>

        {formAprobacion.errores[ERROR_GENERAL] && (
          <Alerta tono="peligro" titulo="No se pudo aprobar">
            {formAprobacion.errores[ERROR_GENERAL]}
          </Alerta>
        )}

        <form className="chn-resolucion-formulario" onSubmit={formAprobacion.manejarEnviar} noValidate>
          <CampoFormulario
            etiqueta="Monto aprobado"
            htmlFor="aprobacion-monto"
            requerido
            error={formAprobacion.errores.montoAprobado}
            ayuda={`No puede exceder el monto solicitado: ${formato.moneda(solicitud.montoSolicitado)}.`}
          >
            <CampoNumero
              id="aprobacion-monto"
              nombre="montoAprobado"
              valor={formAprobacion.valores.montoAprobado}
              onChange={formAprobacion.manejarCambio}
              min={0.01}
              max={Number(solicitud.montoSolicitado)}
              paso="0.01"
              prefijo="Q"
              error={formAprobacion.errores.montoAprobado}
              data-captura="form-aprobacion-monto"
            />
          </CampoFormulario>

          <CampoFormulario
            etiqueta="Plazo aprobado"
            htmlFor="aprobacion-plazo"
            requerido
            error={formAprobacion.errores.plazoAprobadoMeses}
            ayuda={`En meses, de ${LIMITES.PLAZO_MINIMO} a ${LIMITES.PLAZO_MAXIMO}.`}
          >
            <CampoNumero
              id="aprobacion-plazo"
              nombre="plazoAprobadoMeses"
              valor={formAprobacion.valores.plazoAprobadoMeses}
              onChange={formAprobacion.manejarCambio}
              min={LIMITES.PLAZO_MINIMO}
              max={LIMITES.PLAZO_MAXIMO}
              paso="1"
              sufijo="meses"
              error={formAprobacion.errores.plazoAprobadoMeses}
              data-captura="form-aprobacion-plazo"
            />
          </CampoFormulario>

          <CampoFormulario
            etiqueta="Tasa aprobada"
            htmlFor="aprobacion-tasa"
            requerido
            error={formAprobacion.errores.tasaAprobada}
            ayuda={`Solicitada: ${formato.porcentaje(solicitud.tasaInteresAnual)}.`}
          >
            <CampoNumero
              id="aprobacion-tasa"
              nombre="tasaAprobada"
              valor={formAprobacion.valores.tasaAprobada}
              onChange={formAprobacion.manejarCambio}
              min={LIMITES.TASA_MINIMA}
              max={LIMITES.TASA_MAXIMA}
              paso="0.01"
              sufijo="%"
              error={formAprobacion.errores.tasaAprobada}
              data-captura="form-aprobacion-tasa"
            />
          </CampoFormulario>

          <div className="chn-resolucion-previa">
            <p className="chn-resolucion-previa__rotulo">Cuota mensual resultante</p>
            <span className="chn-resolucion-previa__valor">
              {cuotaAprobacion > 0 ? formato.moneda(cuotaAprobacion) : formato.SIN_DATO}
            </span>
          </div>

          <div className="chn-resolucion-formulario__ancho-completo">
            <CampoFormulario
              etiqueta="Motivo u observaciones (opcional)"
              htmlFor="aprobacion-motivo"
              error={formAprobacion.errores.motivo}
              ayuda="Justificación de la aprobación; queda en la resolución."
            >
              <CampoTextarea
                id="aprobacion-motivo"
                nombre="motivo"
                valor={formAprobacion.valores.motivo}
                onChange={formAprobacion.manejarCambio}
                filas={3}
                maxLength={MOTIVO_MAXIMO}
                placeholder="Ejemplo: Capacidad de pago suficiente y garantía verificada"
                error={formAprobacion.errores.motivo}
                data-captura="form-aprobacion-motivo"
              />
            </CampoFormulario>
          </div>
        </form>
      </Modal>

      <Modal
        abierto={modalRechazar}
        titulo={`Rechazar la solicitud ${solicitud.numeroSolicitud}`}
        onCerrar={() => setModalRechazar(false)}
        ancho="md"
        data-captura="form-rechazo"
        pie={
          <>
            <Boton
              variante={ACCION_CANCELAR.variante}
              tono="suave"
              iconoIzquierda={ACCION_CANCELAR.icono}
              onClick={() => setModalRechazar(false)}
              deshabilitado={procesando}
            >
              Cancelar
            </Boton>
            <Boton
              variante={ACCION_RECHAZAR.variante}
              iconoIzquierda={ACCION_RECHAZAR.icono}
              cargando={formRechazo.enviando || procesando}
              onClick={formRechazo.manejarEnviar}
              data-captura="form-rechazo-confirmar"
            >
              Confirmar rechazo
            </Boton>
          </>
        }
      >
        <Alerta tono="advertencia" titulo="El rechazo es definitivo">
          La solicitud quedará cerrada y el motivo se conservará como sustento auditable de la decisión.
        </Alerta>

        {formRechazo.errores[ERROR_GENERAL] && (
          <Alerta tono="peligro" titulo="No se pudo rechazar">
            {formRechazo.errores[ERROR_GENERAL]}
          </Alerta>
        )}

        <form onSubmit={formRechazo.manejarEnviar} noValidate>
          <CampoFormulario
            etiqueta="Motivo del rechazo"
            htmlFor="rechazo-motivo"
            requerido
            error={formRechazo.errores.motivo}
            ayuda={`Explique la razón de la decisión: mínimo ${MOTIVO_MINIMO} caracteres.`}
          >
            <CampoTextarea
              id="rechazo-motivo"
              nombre="motivo"
              valor={formRechazo.valores.motivo}
              onChange={formRechazo.manejarCambio}
              filas={4}
              maxLength={MOTIVO_MAXIMO}
              placeholder="Ejemplo: La cuota mensual excede el 40 % del ingreso declarado"
              error={formRechazo.errores.motivo}
              data-captura="form-rechazo-motivo"
            />
          </CampoFormulario>

          <span
            className={`chn-resolucion-contador ${
              motivoInsuficiente ? 'chn-resolucion-contador--insuficiente' : ''
            }`.trim()}
            aria-live="polite"
          >
            {motivoInsuficiente
              ? `Faltan ${MOTIVO_MINIMO - caracteresMotivo} caracteres para alcanzar el mínimo.`
              : `${caracteresMotivo} de ${MOTIVO_MAXIMO} caracteres (mínimo ${MOTIVO_MINIMO}).`}
          </span>
        </form>
      </Modal>
    </section>
  );
}

export default SolicitudDetalle;
