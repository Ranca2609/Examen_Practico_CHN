import { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { Alerta, Boton, CampoFormulario, CampoTexto, Icono } from '../componentes';
import { useAutenticacion } from '../../aplicacion/AutenticacionContexto.jsx';
import { ERROR_GENERAL, useFormulario } from '../../aplicacion/hooks/useFormulario.js';
import { longitud, requerido } from '../../dominio/validaciones.js';
import './Login.css';

// Cuentas sembradas por el backend solo para la evaluación; no deben existir en producción.
const USUARIOS_DEMO = [
  { usuario: 'admin', rol: 'Administrador' },
  { usuario: 'analista', rol: 'Analista de crédito' },
  { usuario: 'cajero', rol: 'Cajero' },
  { usuario: 'consulta', rol: 'Consulta' },
];

const CONTRASENA_DEMO = 'Chn2026*Demo';

const VENTAJAS = [
  {
    icono: 'clientes',
    titulo: 'Expediente único del cliente',
    texto: 'Registro con validación de DPI y mayoría de edad, sin duplicados.',
  },
  {
    icono: 'calculadora',
    titulo: 'Evaluación y amortización',
    texto: 'Capacidad de pago y plan de cuotas calculados antes de aprobar.',
  },
  {
    icono: 'auditoria',
    titulo: 'Control y trazabilidad',
    texto: 'Pagos, saldos y bitácora de auditoría por usuario y rol.',
  },
];

export function Login() {
  const { autenticado, iniciarSesion } = useAutenticacion();
  const ubicacion = useLocation();
  const [verContrasena, setVerContrasena] = useState(false);

  const { valores, errores, enviando, manejarCambio, manejarEnviar, establecerErrores } = useFormulario({
    valoresIniciales: { username: '', contrasena: '' },
    reglas: {
      username: [requerido, longitud(3, 60)],
      contrasena: [requerido, longitud(8, 72)],
    },
    // El error se propaga a useFormulario (ERROR_GENERAL); el backend no revela qué credencial falló.
    alEnviar: (datos) => iniciarSesion({ username: datos.username.trim(), contrasena: datos.contrasena }),
  });

  if (autenticado) {
    return <Navigate to={ubicacion.state?.desde?.pathname || '/'} replace />;
  }

  return (
    <div className="chn-login">
      <aside className="chn-login__marca">
        <div className="chn-login__logo" aria-hidden="true">
          CHN
        </div>
        <div>
          <p className="chn-login__institucion">Crédito Hipotecario Nacional de Guatemala</p>
          <h1 className="chn-login__titulo">Sistema de Gestión de Préstamos</h1>
          <p className="chn-login__bajada">
            Administración de clientes, solicitudes de crédito, desembolsos y recuperación de cartera
            en una sola plataforma.
          </p>
        </div>
        <ul className="chn-login__ventajas">
          {VENTAJAS.map((ventaja) => (
            <li key={ventaja.titulo} className="chn-login__ventaja">
              <span className="chn-login__ventaja-icono" aria-hidden="true">
                <Icono nombre={ventaja.icono} tamano={20} />
              </span>
              <span>
                <strong>{ventaja.titulo}</strong>
                <span className="chn-login__ventaja-texto">{ventaja.texto}</span>
              </span>
            </li>
          ))}
        </ul>
      </aside>

      <main className="chn-login__acceso">
        <section className="chn-login__tarjeta" data-captura="login-tarjeta">
          <header className="chn-login__tarjeta-encabezado">
            <h2>Iniciar sesión</h2>
            <p>Utilice las credenciales asignadas por el administrador del sistema.</p>
          </header>

          {errores[ERROR_GENERAL] ? (
            <Alerta tono="peligro" titulo="No se pudo iniciar sesión" onCerrar={() => establecerErrores({})}>
              {errores[ERROR_GENERAL]}
            </Alerta>
          ) : null}

          <form className="chn-login__formulario" onSubmit={manejarEnviar} noValidate>
            <CampoFormulario etiqueta="Usuario" htmlFor="login-usuario" requerido error={errores.username}>
              <CampoTexto
                id="login-usuario"
                nombre="username"
                valor={valores.username}
                onChange={manejarCambio}
                placeholder="Por ejemplo: analista"
                autoComplete="username"
                maxLength={60}
                error={errores.username}
                data-captura="login-usuario"
              />
            </CampoFormulario>

            <CampoFormulario
              etiqueta="Contraseña"
              htmlFor="login-contrasena"
              requerido
              error={errores.contrasena}
            >
              <div className="chn-login__campo-contrasena">
                <CampoTexto
                  id="login-contrasena"
                  nombre="contrasena"
                  tipo={verContrasena ? 'text' : 'password'}
                  valor={valores.contrasena}
                  onChange={manejarCambio}
                  placeholder="Mínimo 8 caracteres"
                  autoComplete="current-password"
                  maxLength={72}
                  error={errores.contrasena}
                  data-captura="login-contrasena"
                />
                <button
                  type="button"
                  className="chn-login__ojo"
                  aria-label={verContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-pressed={verContrasena}
                  onClick={() => setVerContrasena((visible) => !visible)}
                >
                  <Icono nombre="ver" tamano={18} />
                </button>
              </div>
            </CampoFormulario>

            <Boton tipo="submit" ancho="completo" cargando={enviando} data-captura="login-enviar">
              Ingresar
            </Boton>
          </form>

          <div className="chn-login__demo" data-captura="login-credenciales">
            <p className="chn-login__demo-titulo">
              <Icono nombre="info" tamano={16} /> Credenciales de demostración
            </p>
            <ul className="chn-login__demo-lista">
              {USUARIOS_DEMO.map((cuenta) => (
                <li key={cuenta.usuario}>
                  <code>{cuenta.usuario}</code>
                  <span>{cuenta.rol}</span>
                </li>
              ))}
            </ul>
            <p className="chn-login__demo-clave">
              Contraseña de las cuatro cuentas: <code>{CONTRASENA_DEMO}</code>
            </p>
            <p className="chn-login__demo-nota">
              Cuentas creadas únicamente para la evaluación del sistema. Deben eliminarse o cambiar su
              contraseña antes de un despliegue en producción.
            </p>
          </div>
        </section>

        <footer className="chn-login__pie">
          Crédito Hipotecario Nacional de Guatemala · {new Date().getFullYear()}
        </footer>
      </main>
    </div>
  );
}

export default Login;
