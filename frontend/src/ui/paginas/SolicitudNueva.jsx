import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import {
  Alerta,
  BarraProgreso,
  Boton,
  CampoFormulario,
  CampoNumero,
  CampoSelect,
  CampoTextarea,
  EncabezadoPagina,
  Etiqueta,
  Modal,
  Tabla,
  Tarjeta,
} from '../componentes';
import { useNotificaciones } from '../../aplicacion/NotificacionContexto.jsx';
import { useFormulario, ERROR_GENERAL } from '../../aplicacion/hooks/useFormulario.js';
import { useSimulacion } from '../../aplicacion/hooks/useSolicitudes.js';
import { useOpcionesClientes } from '../../aplicacion/hooks/useClientes.js';
import * as adaptadorSolicitudes from '../../infraestructura/http/adaptadorSolicitudes.js';
import { accion } from '../../dominio/acciones.js';
import { LIMITES, opcionesTipoPrestamo } from '../../dominio/catalogos.js';
import * as formato from '../../dominio/formato.js';
import { calcularCuota, porcentajeComprometido } from '../../dominio/amortizacion.js';
import {
  identificadorValido,
  longitud,
  maximoCaracteres,
  montoEnRango,
  numeroPositivo,
  plazoEnRango,
  requerido,
  sinErrores,
  tasaEnRango,
  validarFormulario,
} from '../../dominio/validaciones.js';
import './SolicitudNueva.css';

const TASA_SUGERIDA = '12.50';

const CUOTAS_VISTA_PREVIA = 12;

const ACCION_CANCELAR = accion('cancelar');
const ACCION_SIMULAR = accion('simular');
const ACCION_GUARDAR = accion('guardar');
const ACCION_VER = accion('ver');

// Numéricos como texto: alimentan inputs controlados.
const VALORES_INICIALES = {
  clienteId: '',
  tipoPrestamo: '',
  montoSolicitado: '',
  plazoMeses: '',
  tasaInteresAnual: TASA_SUGERIDA,
  ingresoMensualDeclarado: '',
  destino: '',
  observaciones: '',
};

// Replican los rangos del backend para ahorrar una petición; el servidor tiene la última palabra.
const REGLAS = {
  clienteId: [requerido, identificadorValido],
  tipoPrestamo: requerido,
  montoSolicitado: [requerido, montoEnRango()],
  plazoMeses: [requerido, plazoEnRango()],
  tasaInteresAnual: [requerido, tasaEnRango()],
  ingresoMensualDeclarado: [requerido, numeroPositivo],
  destino: [requerido, longitud(5, 200)],
  observaciones: maximoCaracteres(500),
};

// Para simular no se exige el destino.
const REGLAS_SIMULACION = {
  clienteId: REGLAS.clienteId,
  montoSolicitado: REGLAS.montoSolicitado,
  plazoMeses: REGLAS.plazoMeses,
  tasaInteresAnual: REGLAS.tasaInteresAnual,
  ingresoMensualDeclarado: REGLAS.ingresoMensualDeclarado,
};

// Cambiar cualquiera de estos campos invalida la simulación del servidor.
const CAMPOS_SIMULACION = Object.keys(REGLAS_SIMULACION);

const COLUMNAS_PLAN = [
  { clave: 'numero', encabezado: 'No.', alineacion: 'centro', ancho: '4rem' },
  {
    clave: 'saldoInicial',
    encabezado: 'Saldo inicial',
    alineacion: 'derecha',
    render: (fila) => formato.moneda(fila.saldoInicial),
  },
  { clave: 'cuota', encabezado: 'Cuota', alineacion: 'derecha', render: (fila) => formato.moneda(fila.cuota) },
  {
    clave: 'abonoCapital',
    encabezado: 'Capital',
    alineacion: 'derecha',
    render: (fila) => formato.moneda(fila.abonoCapital),
  },
  {
    clave: 'abonoInteres',
    encabezado: 'Interés',
    alineacion: 'derecha',
    render: (fila) => formato.moneda(fila.abonoInteres),
  },
  {
    clave: 'saldoFinal',
    encabezado: 'Saldo final',
    alineacion: 'derecha',
    render: (fila) => formato.moneda(fila.saldoFinal),
  },
];

export function SolicitudNueva() {
  const navegar = useNavigate();
  const { notificar } = useNotificaciones();

  const { opciones: opcionesClientes, cargando: cargandoClientes } = useOpcionesClientes();
  const { resultado: simulacion, simulando, simular, limpiar } = useSimulacion();

  const [planVisible, setPlanVisible] = useState(false);
  const [modalPlanAbierto, setModalPlanAbierto] = useState(false);

  // Clave del panel: cada resultado del servidor lo remonta y la animación avisa del cambio;
  // mientras se escribe no cambia, así la estimación local no se anima en cada tecla.
  const [versionSimulacion, setVersionSimulacion] = useState(0);

  const { valores, errores, enviando, manejarCambio, establecerErrores, manejarEnviar } = useFormulario({
    valoresIniciales: VALORES_INICIALES,
    reglas: REGLAS,
    alEnviar: async (datos) => {
      const creada = await adaptadorSolicitudes.crear({
        clienteId: Number(datos.clienteId),
        montoSolicitado: Number(datos.montoSolicitado),
        plazoMeses: Number(datos.plazoMeses),
        tasaInteresAnual: Number(datos.tasaInteresAnual),
        tipoPrestamo: datos.tipoPrestamo,
        destino: datos.destino.trim(),
        ingresoMensualDeclarado: Number(datos.ingresoMensualDeclarado),
        observaciones: datos.observaciones.trim() || null,
      });

      notificar({
        tono: 'exito',
        titulo: 'Solicitud registrada',
        mensaje: `Se asignó el número ${creada.numeroSolicitud}. Queda en estado "En proceso".`,
      });
      navegar(`/solicitudes/${creada.id}`);
    },
  });

  const cambiar = (evento) => {
    manejarCambio(evento);

    if (CAMPOS_SIMULACION.includes(evento?.target?.name)) {
      limpiar();
      setPlanVisible(false);
    }
  };

  const cuotaEstimada = useMemo(
    () => calcularCuota(valores.montoSolicitado, valores.plazoMeses, valores.tasaInteresAnual),
    [valores.montoSolicitado, valores.plazoMeses, valores.tasaInteresAnual],
  );

  const porcentajeEstimado = useMemo(
    () => porcentajeComprometido(cuotaEstimada, valores.ingresoMensualDeclarado),
    [cuotaEstimada, valores.ingresoMensualDeclarado],
  );

  const esDelServidor = Boolean(simulacion);
  const plan = simulacion?.plan ?? null;
  const evaluacion = simulacion?.evaluacion ?? null;

  const cuotaMostrada = esDelServidor ? plan?.cuotaMensual ?? 0 : cuotaEstimada;
  const porcentajeMostrado = esDelServidor ? evaluacion?.porcentajeComprometido : porcentajeEstimado;

  const cuotasVistaPrevia = useMemo(() => (plan?.cuotas ?? []).slice(0, CUOTAS_VISTA_PREVIA), [plan]);

  const alSimular = async () => {
    const encontrados = validarFormulario(valores, REGLAS_SIMULACION);
    if (!sinErrores(encontrados)) {
      establecerErrores({ ...errores, ...encontrados });
      notificar({
        tono: 'advertencia',
        mensaje: 'Complete los datos del préstamo (cliente, monto, plazo, tasa e ingreso) para simular.',
      });
      return;
    }

    try {
      await simular({
        clienteId: Number(valores.clienteId),
        monto: Number(valores.montoSolicitado),
        plazoMeses: Number(valores.plazoMeses),
        tasaInteresAnual: Number(valores.tasaInteresAnual),
        ingresoMensual: Number(valores.ingresoMensualDeclarado),
      });
      setVersionSimulacion((version) => version + 1);
      setPlanVisible(true);
    } catch {
      // useSimulacion ya notificó el error; aquí solo se evita el rechazo sin gestionar.
      setPlanVisible(false);
    }
  };

  const excedeEndeudamiento =
    porcentajeMostrado !== null &&
    porcentajeMostrado !== undefined &&
    porcentajeMostrado > LIMITES.PORCENTAJE_ENDEUDAMIENTO_MAXIMO;

  return (
    <section>
      <EncabezadoPagina
        titulo="Nueva solicitud de préstamo"
        descripcion="Registre las condiciones que solicita el cliente. Puede simular la cuota y evaluar su capacidad de pago antes de enviar."
        migas={[{ etiqueta: 'Solicitudes', a: '/solicitudes' }, { etiqueta: 'Nueva' }]}
      />

      <div className="chn-solicitud-nueva">
        <Tarjeta titulo="Datos de la solicitud" subtitulo="Los campos marcados con * son obligatorios">
          {errores[ERROR_GENERAL] && (
            <Alerta tono="peligro" titulo="No se pudo registrar la solicitud">
              {errores[ERROR_GENERAL]}
            </Alerta>
          )}

          <form className="chn-solicitud-nueva__rejilla" onSubmit={manejarEnviar} data-captura="form-solicitud" noValidate>
            <div className="chn-solicitud-nueva__ancho-completo">
              <CampoFormulario
                etiqueta="Cliente"
                htmlFor="solicitud-cliente"
                requerido
                error={errores.clienteId}
                ayuda={
                  cargandoClientes
                    ? 'Cargando el catálogo de clientes...'
                    : `${opcionesClientes.length} cliente(s) en el catálogo. Escriba para filtrar.`
                }
              >
                <CampoSelect
                  id="solicitud-cliente"
                  nombre="clienteId"
                  valor={valores.clienteId}
                  onChange={cambiar}
                  opciones={opcionesClientes}
                  placeholder="Seleccione un cliente"
                  error={errores.clienteId}
                  deshabilitado={cargandoClientes}
                  buscable
                  data-captura="form-solicitud-cliente"
                />
              </CampoFormulario>
            </div>

            <CampoFormulario
              etiqueta="Tipo de préstamo"
              htmlFor="solicitud-tipo"
              requerido
              error={errores.tipoPrestamo}
            >
              <CampoSelect
                id="solicitud-tipo"
                nombre="tipoPrestamo"
                valor={valores.tipoPrestamo}
                onChange={cambiar}
                opciones={opcionesTipoPrestamo()}
                placeholder="Seleccione el tipo"
                error={errores.tipoPrestamo}
                data-captura="form-solicitud-tipo"
              />
            </CampoFormulario>

            <CampoFormulario
              etiqueta="Monto solicitado"
              htmlFor="solicitud-monto"
              requerido
              error={errores.montoSolicitado}
              ayuda={`En quetzales, de ${formato.moneda(LIMITES.MONTO_MINIMO)} a ${formato.moneda(LIMITES.MONTO_MAXIMO)}.`}
            >
              <CampoNumero
                id="solicitud-monto"
                nombre="montoSolicitado"
                valor={valores.montoSolicitado}
                onChange={cambiar}
                min={LIMITES.MONTO_MINIMO}
                max={LIMITES.MONTO_MAXIMO}
                paso="0.01"
                prefijo="Q"
                error={errores.montoSolicitado}
                data-captura="form-solicitud-monto"
              />
            </CampoFormulario>

            <CampoFormulario
              etiqueta="Plazo"
              htmlFor="solicitud-plazo"
              requerido
              error={errores.plazoMeses}
              ayuda={`En meses, de ${LIMITES.PLAZO_MINIMO} a ${LIMITES.PLAZO_MAXIMO}.`}
            >
              <CampoNumero
                id="solicitud-plazo"
                nombre="plazoMeses"
                valor={valores.plazoMeses}
                onChange={cambiar}
                min={LIMITES.PLAZO_MINIMO}
                max={LIMITES.PLAZO_MAXIMO}
                paso="1"
                sufijo="meses"
                error={errores.plazoMeses}
                data-captura="form-solicitud-plazo"
              />
            </CampoFormulario>

            <CampoFormulario
              etiqueta="Tasa de interés anual"
              htmlFor="solicitud-tasa"
              requerido
              error={errores.tasaInteresAnual}
              ayuda={`De ${LIMITES.TASA_MINIMA} % a ${LIMITES.TASA_MAXIMA} %. Tasa sugerida: ${TASA_SUGERIDA} %.`}
            >
              <CampoNumero
                id="solicitud-tasa"
                nombre="tasaInteresAnual"
                valor={valores.tasaInteresAnual}
                onChange={cambiar}
                min={LIMITES.TASA_MINIMA}
                max={LIMITES.TASA_MAXIMA}
                paso="0.01"
                sufijo="%"
                error={errores.tasaInteresAnual}
                data-captura="form-solicitud-tasa"
              />
            </CampoFormulario>

            <CampoFormulario
              etiqueta="Ingreso mensual declarado"
              htmlFor="solicitud-ingreso"
              requerido
              error={errores.ingresoMensualDeclarado}
              ayuda={`Sirve para evaluar la capacidad de pago; se recomienda no comprometer más del ${LIMITES.PORCENTAJE_ENDEUDAMIENTO_MAXIMO} % del ingreso.`}
            >
              <CampoNumero
                id="solicitud-ingreso"
                nombre="ingresoMensualDeclarado"
                valor={valores.ingresoMensualDeclarado}
                onChange={cambiar}
                min={0.01}
                paso="0.01"
                prefijo="Q"
                error={errores.ingresoMensualDeclarado}
                data-captura="form-solicitud-ingreso"
              />
            </CampoFormulario>

            <div className="chn-solicitud-nueva__ancho-completo">
              <CampoFormulario
                etiqueta="Destino del préstamo"
                htmlFor="solicitud-destino"
                requerido
                error={errores.destino}
                ayuda="Finalidad del financiamiento: entre 5 y 200 caracteres."
              >
                <CampoTextarea
                  id="solicitud-destino"
                  nombre="destino"
                  valor={valores.destino}
                  onChange={cambiar}
                  filas={2}
                  maxLength={200}
                  placeholder="Ejemplo: Remodelación de vivienda familiar"
                  error={errores.destino}
                  data-captura="form-solicitud-destino"
                />
              </CampoFormulario>
            </div>

            <div className="chn-solicitud-nueva__ancho-completo">
              <CampoFormulario
                etiqueta="Observaciones (opcional)"
                htmlFor="solicitud-observaciones"
                error={errores.observaciones}
                ayuda="Notas del asesor que captura la solicitud."
              >
                <CampoTextarea
                  id="solicitud-observaciones"
                  nombre="observaciones"
                  valor={valores.observaciones}
                  onChange={cambiar}
                  filas={3}
                  maxLength={500}
                  placeholder="Ejemplo: Cliente con historial crediticio favorable"
                  error={errores.observaciones}
                  data-captura="form-solicitud-observaciones"
                />
              </CampoFormulario>
            </div>

            <div className="chn-solicitud-nueva__acciones chn-solicitud-nueva__ancho-completo">
              <Boton
                variante={ACCION_CANCELAR.variante}
                tono="suave"
                iconoIzquierda={ACCION_CANCELAR.icono}
                onClick={() => navegar('/solicitudes')}
              >
                Cancelar
              </Boton>
              <Boton
                variante={ACCION_SIMULAR.variante}
                iconoIzquierda={ACCION_SIMULAR.icono}
                cargando={simulando}
                onClick={alSimular}
                data-captura="form-solicitud-simular"
              >
                Simular
              </Boton>
              <Boton
                tipo="submit"
                variante={ACCION_GUARDAR.variante}
                iconoIzquierda={ACCION_GUARDAR.icono}
                cargando={enviando}
                data-captura="form-solicitud-enviar"
              >
                Enviar solicitud
              </Boton>
            </div>
          </form>
        </Tarjeta>

        <div className="chn-solicitud-nueva__panel" data-captura="panel-simulacion">
          <Tarjeta
            key={`simulacion-${versionSimulacion}`}
            className={esDelServidor ? 'chn-anim-subir' : ''}
            titulo="Simulación del préstamo"
            subtitulo={esDelServidor ? 'Plan calculado por el servidor' : 'Estimación calculada en este navegador'}
          >
            <div className="chn-simulacion-cuota">
              <p className="chn-simulacion-cuota__rotulo">Cuota mensual</p>
              <span className="chn-simulacion-cuota__valor">
                {cuotaMostrada > 0 ? formato.moneda(cuotaMostrada) : formato.SIN_DATO}
              </span>
              <span className="chn-simulacion-cuota__origen">
                {esDelServidor
                  ? 'Cálculo oficial del servidor'
                  : 'Estimación local: pulse «Simular» para el cálculo del servidor'}
              </span>
            </div>

            {esDelServidor && (
              <div className="chn-simulacion-totales">
                <div className="chn-simulacion-totales__item">
                  <span className="chn-simulacion-totales__etiqueta">Total de intereses</span>
                  <span className="chn-simulacion-totales__valor">{formato.moneda(plan.totalIntereses)}</span>
                </div>
                <div className="chn-simulacion-totales__item">
                  <span className="chn-simulacion-totales__etiqueta">Monto total a pagar</span>
                  <span className="chn-simulacion-totales__valor">{formato.moneda(plan.montoTotal)}</span>
                </div>
              </div>
            )}

            {porcentajeMostrado !== null && porcentajeMostrado !== undefined && (
              <div className="chn-simulacion-evaluacion">
                <div className="chn-simulacion-evaluacion__encabezado">
                  <h3 className="chn-simulacion-evaluacion__titulo">Capacidad de pago</h3>
                  {esDelServidor && (
                    <Etiqueta tono={evaluacion.recomendado ? 'exito' : 'peligro'}>
                      {evaluacion.recomendado ? 'Recomendado' : 'No recomendado'}
                    </Etiqueta>
                  )}
                </div>

                <BarraProgreso
                  valor={porcentajeMostrado}
                  maximo={100}
                  tono={excedeEndeudamiento ? 'peligro' : 'exito'}
                  etiqueta={`${formato.porcentaje(porcentajeMostrado)} del ingreso mensual comprometido`}
                />

                {esDelServidor && evaluacion.observacion && (
                  <p className="chn-simulacion-evaluacion__observacion">{evaluacion.observacion}</p>
                )}

                {esDelServidor && (
                  <p className="chn-simulacion-evaluacion__vigentes">
                    Préstamos vigentes del cliente: <strong>{formato.numero(evaluacion.prestamosVigentes)}</strong>
                  </p>
                )}
              </div>
            )}

            {esDelServidor && cuotasVistaPrevia.length > 0 && (
              <div className="chn-simulacion-plan">
                <div className="chn-simulacion-plan__encabezado">
                  <h3 className="chn-simulacion-plan__titulo">Plan de amortización</h3>
                  <Boton
                    variante="texto"
                    tamano="sm"
                    onClick={() => setPlanVisible((visible) => !visible)}
                    aria-expanded={planVisible}
                  >
                    {planVisible ? 'Ocultar cuotas' : 'Ver primeras cuotas'}
                  </Boton>
                </div>

                {planVisible && (
                  <>
                    <Tabla
                      columnas={COLUMNAS_PLAN}
                      datos={cuotasVistaPrevia}
                      claveFila={(fila) => fila.numero}
                      descripcion="Primeras cuotas del plan de amortización simulado"
                    />
                    <p className="chn-simulacion-plan__nota">
                      {`Mostrando ${cuotasVistaPrevia.length} de ${plan.cuotas.length} cuotas.`}
                    </p>
                    <Boton
                      variante={ACCION_VER.variante}
                      tono="suave"
                      tamano="sm"
                      iconoIzquierda={ACCION_VER.icono}
                      onClick={() => setModalPlanAbierto(true)}
                    >
                      Ver plan completo
                    </Boton>
                  </>
                )}
              </div>
            )}
          </Tarjeta>
        </div>
      </div>

      <Modal
        abierto={modalPlanAbierto}
        titulo="Plan de amortización completo"
        onCerrar={() => setModalPlanAbierto(false)}
        ancho="lg"
        pie={
          <Boton
            variante={ACCION_CANCELAR.variante}
            tono="suave"
            onClick={() => setModalPlanAbierto(false)}
          >
            Cerrar
          </Boton>
        }
      >
        <div className="chn-simulacion-plan-completo">
          <Tabla
            columnas={COLUMNAS_PLAN}
            datos={plan?.cuotas ?? []}
            claveFila={(fila) => fila.numero}
            descripcion="Plan de amortización completo del préstamo simulado"
          />
        </div>
      </Modal>
    </section>
  );
}

export default SolicitudNueva;
