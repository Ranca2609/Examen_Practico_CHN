import { useMemo } from 'react';

import { Alerta, Boton, CampoFecha, CampoFormulario, CampoTexto, CampoTextarea, Modal } from '../componentes';
import { ERROR_GENERAL, useFormulario } from '../../aplicacion/hooks/useFormulario.js';
import { accion } from '../../dominio/acciones.js';
import { hoyIso, sumarMeses } from '../../dominio/fechas.js';
import {
  correo as validarCorreo,
  dpi as validarDpi,
  fechaNacimientoMayorDeEdad,
  longitud,
  requerido,
  telefono as validarTelefono,
} from '../../dominio/validaciones.js';
import './Clientes.css';

// El botón de guardar vive en el pie del modal, fuera del <form>, y lo envía por id.
const ID_FORMULARIO = 'chn-form-cliente';

const EDAD_MINIMA = 18;

const ACCION_GUARDAR = accion('guardar');
const ACCION_CANCELAR = accion('cancelar');

// Vía dominio/fechas.js y no Date.toISOString, que en UTC-6 desfasa el día.
// fechaNacimientoMayorDeEdad se mantiene porque la fecha también puede escribirse a mano.
function fechaMaximaNacimiento() {
  return sumarMeses(hoyIso(), -12 * EDAD_MINIMA);
}

function soloDigitos(valor) {
  return String(valor ?? '').replace(/\D/g, '');
}

export function ClienteFormulario({ abierto, cliente, onCerrar, onGuardar }) {
  const esAlta = !cliente;

  const reglas = useMemo(() => {
    const comunes = {
      nombre: [requerido, longitud(2, 60)],
      apellido: [requerido, longitud(2, 60)],
      direccion: [requerido, longitud(5, 200)],
      correoElectronico: [requerido, validarCorreo],
      telefono: [requerido, validarTelefono],
    };

    // El PUT no acepta DPI ni fecha de nacimiento: en edición no se validan.
    if (!esAlta) return comunes;

    return {
      ...comunes,
      numeroIdentificacion: [requerido, validarDpi],
      fechaNacimiento: [requerido, fechaNacimientoMayorDeEdad],
    };
  }, [esAlta]);

  const { valores, errores, enviando, manejarCambio, establecerValor, manejarEnviar } = useFormulario({
    valoresIniciales: {
      nombre: cliente?.nombre ?? '',
      apellido: cliente?.apellido ?? '',
      numeroIdentificacion: cliente?.numeroIdentificacion ?? '',
      fechaNacimiento: cliente?.fechaNacimiento ?? '',
      direccion: cliente?.direccion ?? '',
      correoElectronico: cliente?.correoElectronico ?? '',
      telefono: cliente?.telefono ?? '',
    },
    reglas,
    // El error se propaga a propósito: useFormulario reparte los mensajes por campo.
    alEnviar: (datos) =>
      onGuardar({
        nombre: datos.nombre.trim(),
        apellido: datos.apellido.trim(),
        numeroIdentificacion: datos.numeroIdentificacion,
        fechaNacimiento: datos.fechaNacimiento,
        direccion: datos.direccion.trim(),
        correoElectronico: datos.correoElectronico.trim().toLowerCase(),
        telefono: datos.telefono,
      }),
  });

  const alCambiarDigitos = (evento, maximo) =>
    establecerValor(evento.target.name, soloDigitos(evento.target.value).slice(0, maximo));

  return (
    <Modal
      abierto={abierto}
      titulo={esAlta ? 'Nuevo cliente' : `Editar a ${cliente.nombreCompleto}`}
      onCerrar={enviando ? () => {} : onCerrar}
      ancho="md"
      pie={
        <>
          <Boton
            variante={ACCION_CANCELAR.variante}
            iconoIzquierda={ACCION_CANCELAR.icono}
            onClick={onCerrar}
            deshabilitado={enviando}
            data-captura="form-cliente-cancelar"
          >
            Cancelar
          </Boton>
          <Boton
            tipo="submit"
            form={ID_FORMULARIO}
            variante={ACCION_GUARDAR.variante}
            iconoIzquierda={ACCION_GUARDAR.icono}
            cargando={enviando}
            data-captura="form-cliente-guardar"
          >
            {esAlta ? 'Registrar cliente' : 'Guardar cambios'}
          </Boton>
        </>
      }
    >
      {errores[ERROR_GENERAL] ? (
        <Alerta tono="peligro" titulo="No se pudo guardar el cliente">
          {errores[ERROR_GENERAL]}
        </Alerta>
      ) : null}

      <form
        id={ID_FORMULARIO}
        className="chn-form-cliente"
        data-captura="form-cliente"
        onSubmit={manejarEnviar}
        noValidate
      >
        <div className="chn-form-cliente__rejilla">
          <CampoFormulario etiqueta="Nombre" htmlFor="cliente-nombre" requerido error={errores.nombre}>
            <CampoTexto
              id="cliente-nombre"
              nombre="nombre"
              valor={valores.nombre}
              onChange={manejarCambio}
              placeholder="Nombres del cliente"
              maxLength={60}
              autoComplete="off"
              error={errores.nombre}
              data-captura="form-cliente-nombre"
            />
          </CampoFormulario>

          <CampoFormulario etiqueta="Apellido" htmlFor="cliente-apellido" requerido error={errores.apellido}>
            <CampoTexto
              id="cliente-apellido"
              nombre="apellido"
              valor={valores.apellido}
              onChange={manejarCambio}
              placeholder="Apellidos del cliente"
              maxLength={60}
              autoComplete="off"
              error={errores.apellido}
              data-captura="form-cliente-apellido"
            />
          </CampoFormulario>

          <CampoFormulario
            etiqueta="Número de DPI"
            htmlFor="cliente-dpi"
            requerido={esAlta}
            error={errores.numeroIdentificacion}
            ayuda={
              esAlta
                ? '13 dígitos, sin guiones ni espacios (por ejemplo 2547896320101).'
                : 'El DPI no se puede modificar después del registro.'
            }
          >
            <CampoTexto
              id="cliente-dpi"
              nombre="numeroIdentificacion"
              valor={valores.numeroIdentificacion}
              onChange={(evento) => alCambiarDigitos(evento, 13)}
              placeholder="2547896320101"
              inputMode="numeric"
              maxLength={13}
              autoComplete="off"
              deshabilitado={!esAlta}
              error={errores.numeroIdentificacion}
              data-captura="form-cliente-dpi"
            />
          </CampoFormulario>

          <CampoFormulario
            etiqueta="Fecha de nacimiento"
            htmlFor="cliente-nacimiento"
            requerido={esAlta}
            error={errores.fechaNacimiento}
            ayuda={
              esAlta
                ? `El cliente debe ser mayor de edad (${EDAD_MINIMA} años cumplidos).`
                : 'La fecha de nacimiento no se puede modificar después del registro.'
            }
          >
            <CampoFecha
              id="cliente-nacimiento"
              nombre="fechaNacimiento"
              valor={valores.fechaNacimiento}
              onChange={manejarCambio}
              min="1900-01-01"
              max={fechaMaximaNacimiento()}
              deshabilitado={!esAlta}
              error={errores.fechaNacimiento}
              data-captura="form-cliente-nacimiento"
            />
          </CampoFormulario>

          <CampoFormulario
            etiqueta="Correo electrónico"
            htmlFor="cliente-correo"
            requerido
            error={errores.correoElectronico}
            ayuda="Se utiliza para notificar la resolución de las solicitudes."
          >
            <CampoTexto
              id="cliente-correo"
              nombre="correoElectronico"
              tipo="email"
              valor={valores.correoElectronico}
              onChange={manejarCambio}
              placeholder="nombre.apellido@correo.com"
              maxLength={120}
              autoComplete="off"
              error={errores.correoElectronico}
              data-captura="form-cliente-correo"
            />
          </CampoFormulario>

          <CampoFormulario
            etiqueta="Teléfono"
            htmlFor="cliente-telefono"
            requerido
            error={errores.telefono}
            ayuda="8 dígitos, sin guiones ni espacios (por ejemplo 55481290)."
          >
            <CampoTexto
              id="cliente-telefono"
              nombre="telefono"
              valor={valores.telefono}
              onChange={(evento) => alCambiarDigitos(evento, 8)}
              placeholder="55481290"
              inputMode="numeric"
              maxLength={8}
              autoComplete="off"
              error={errores.telefono}
              data-captura="form-cliente-telefono"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario
          etiqueta="Dirección"
          htmlFor="cliente-direccion"
          requerido
          error={errores.direccion}
          ayuda="Dirección exacta de residencia: calle o avenida, número, zona y municipio."
        >
          <CampoTextarea
            id="cliente-direccion"
            nombre="direccion"
            valor={valores.direccion}
            onChange={manejarCambio}
            filas={3}
            maxLength={200}
            placeholder="5a. avenida 12-45, zona 1, Ciudad de Guatemala"
            error={errores.direccion}
            data-captura="form-cliente-direccion"
          />
        </CampoFormulario>
      </form>
    </Modal>
  );
}

export default ClienteFormulario;
