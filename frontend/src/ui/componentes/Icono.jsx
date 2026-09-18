import './Icono.css';

const TRAZOS = {
  tablero: ['M4 4h7v7H4z', 'M13 4h7v5h-7z', 'M13 13h7v7h-7z', 'M4 15h7v5H4z'],
  clientes: [
    'M10 5.5a3 3 0 1 1 0 6 3 3 0 0 1 0-6z',
    'M16 19.5v-1.4a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.1v1.4',
    'M15.6 7.2a2.5 2.5 0 0 1 0 4.6',
    'M20 19.5v-1.2a3.3 3.3 0 0 0-2.5-3.2',
  ],
  solicitudes: [
    'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z',
    'M14 3v5h5',
    'M9 13h6',
    'M9 17h4',
  ],
  prestamos: ['M3 10 12 4l9 6', 'M5 10v9', 'M19 10v9', 'M9 19v-6', 'M15 19v-6', 'M3 20h18'],
  pagos: ['M3 6h18v12H3z', 'M3 10h18', 'M6.5 14h3'],
  auditoria: [
    'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2',
    'M9 3.5h6V6H9z',
    'M8.8 14l2 2 4.2-4.2',
  ],
  salir: ['M16 17l5-5-5-5', 'M21 12H9', 'M12 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h6'],
  buscar: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'M20 20l-4.2-4.2'],
  mas: ['M12 5v14', 'M5 12h14'],
  editar: ['M4 20h4L18.5 9.5a2.12 2.12 0 0 0-3-3L5 17v3z', 'M14.5 6.5l3 3'],
  eliminar: ['M4 7h16', 'M10 4h4', 'M10 11v6', 'M14 11v6', 'M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13'],
  ver: ['M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12z', 'M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z'],
  cerrar: ['M6 6l12 12', 'M18 6L6 18'],
  check: ['M5 13l4 4L19 7'],
  alerta: ['M12 4 2.8 20h18.4L12 4z', 'M12 10v4', 'M12 17h.01'],
  info: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 11v6', 'M12 8h.01'],
  dinero: [
    'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z',
    'M12 6.5v11',
    'M14.6 9.4a2.6 2 0 0 0-2.6-1.4c-1.4 0-2.6.8-2.6 2s1.2 1.8 2.6 2 2.6.8 2.6 2-1.2 2-2.6 2a2.6 2 0 0 1-2.6-1.4',
  ],
  calendario: ['M5 6h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z', 'M8 3.5v4', 'M16 3.5v4', 'M4 11h16'],
  usuario: ['M12 4a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z', 'M5 20v-1a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v1'],
  imprimir: [
    'M7 9V4h10v5',
    'M7 18H5.5A1.5 1.5 0 0 1 4 16.5V11a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v5.5A1.5 1.5 0 0 1 18.5 18H17',
    'M7 14h10v6H7z',
  ],
  flechaIzquierda: ['M14 6l-6 6 6 6'],
  flechaDerecha: ['M10 6l6 6-6 6'],
  descargar: ['M12 4v10', 'M8 11l4 4 4-4', 'M5 19h14'],
  calculadora: [
    'M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z',
    'M8 7h8v3H8z',
    'M9 14h.01',
    'M12 14h.01',
    'M15 14h.01',
    'M9 17.5h.01',
    'M12 17.5h.01',
    'M15 17.5h.01',
  ],
  menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  filtro: ['M4 5h16l-6 7v6l-4 2v-8L4 5z'],
  refrescar: ['M20 12a8 8 0 1 1-2.6-5.9', 'M20 4v4h-4'],
  // Escoba de frente y con pocos trazos: a 18px una en diagonal se vuelve ilegible.
  limpiar: ['M12 3v7.5', 'M9 10.5h6l3.5 9.5h-13z', 'M7 16h10'],
  chevronAbajo: ['M6 9.5l6 6 6-6'],
  chevronArriba: ['M6 14.5l6-6 6 6'],
  chevronIzquierda: ['M14.5 6l-6 6 6 6'],
  chevronDerecha: ['M9.5 6l6 6-6 6'],
  exito: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M8 12.2l2.6 2.6L16 9.4'],
  reloj: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 7.5V12l3.2 2'],
  etiqueta: [
    'M20 13.2l-6.8 6.8a2 2 0 0 1-2.8 0l-6.6-6.6a2 2 0 0 1-.6-1.4V5a1 1 0 0 1 1-1h6.9a2 2 0 0 1 1.5.6l6.8 6.8a1.3 1.3 0 0 1 0 1.8z',
    'M8 8h.01',
  ],
  grafico: ['M4 20h16', 'M7 20v-6', 'M12 20V7', 'M17 20v-9'],
  tabla: ['M4 5h16v14H4z', 'M4 10h16', 'M4 14.5h16', 'M10 10v9'],
};

function Icono({ nombre, tamano = 18, color, className = '', ...resto }) {
  const trazos = TRAZOS[nombre];
  // Un nombre equivocado no debe romper la pantalla.
  if (!trazos) return null;

  return (
    <svg
      className={`chn-icono ${className}`.trim()}
      width={tamano}
      height={tamano}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color || 'currentColor'}
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...resto}
    >
      {trazos.map((trazo) => (
        <path key={trazo} d={trazo} />
      ))}
    </svg>
  );
}

export const NOMBRES_ICONO = Object.keys(TRAZOS);

export default Icono;
