import { useNavigate } from 'react-router-dom';

import { Boton, EstadoVacio } from '../componentes';
import { accion } from '../../dominio/acciones';

const ACCION_VOLVER = accion('volver');

export function NoEncontrado() {
  const navegar = useNavigate();

  return (
    <section className="chn-anim-escalar" data-captura="no-encontrado">
      <EstadoVacio
        icono="alerta"
        titulo="Página no encontrada"
        mensaje="La dirección que abrió no corresponde a ninguna pantalla del sistema. Puede que el enlace esté incompleto o que el registro ya no exista."
        accion={
          <Boton
            variante={ACCION_VOLVER.variante}
            iconoIzquierda={ACCION_VOLVER.icono}
            onClick={() => navegar('/', { replace: true })}
          >
            Regresar al tablero
          </Boton>
        }
      />
    </section>
  );
}

export default NoEncontrado;
