import Boton from './Boton.jsx';
import Icono from './Icono.jsx';
import Modal from './Modal.jsx';
import './DialogoConfirmacion.css';

// La variante decide icono y color para anticipar la consecuencia antes de leer el mensaje.
const ICONO_POR_VARIANTE = {
  peligro: 'alerta',
  advertencia: 'alerta',
  exito: 'check',
  info: 'info',
  primario: 'info',
  secundario: 'info',
  neutro: 'info',
};

function DialogoConfirmacion({
  abierto,
  titulo = 'Confirmar acción',
  mensaje,
  textoConfirmar = 'Confirmar',
  textoCancelar = 'Cancelar',
  variante = 'peligro',
  cargando = false,
  onConfirmar,
  onCancelar,
}) {
  const icono = ICONO_POR_VARIANTE[variante] || 'info';

  return (
    <Modal
      abierto={abierto}
      titulo={titulo}
      ancho="sm"
      onCerrar={cargando ? () => {} : onCancelar}
      data-captura="dialogo-confirmacion"
      pie={
        <>
          <Boton
            variante="neutro"
            tono="suave"
            iconoIzquierda="cerrar"
            onClick={onCancelar}
            deshabilitado={cargando}
            data-captura="dialogo-cancelar"
          >
            {textoCancelar}
          </Boton>
          <Boton
            variante={variante}
            onClick={onConfirmar}
            cargando={cargando}
            data-captura="dialogo-aceptar"
          >
            {textoConfirmar}
          </Boton>
        </>
      }
    >
      <div className={`chn-dialogo chn-dialogo--${variante}`}>
        <span className="chn-dialogo__icono" aria-hidden="true">
          <Icono nombre={icono} tamano={28} />
        </span>
        <p className="chn-dialogo__mensaje">{mensaje}</p>
      </div>
    </Modal>
  );
}

export default DialogoConfirmacion;
