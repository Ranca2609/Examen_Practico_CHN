# Generador de capturas del Manual de Usuario

Las imágenes del manual **no son maquetas**: se obtienen recorriendo el sistema
en ejecución con Playwright. El guion inicia sesión, ejecuta cada flujo con
datos de prueba, inyecta en la página una capa con círculos numerados y una
leyenda de pasos, y fotografía la pantalla.

## Uso recomendado (sin instalar nada)

Con el sistema levantado (`docker compose up -d`):

```bash
docker compose --profile herramientas run --rm capturas
```

Las imágenes se escriben en `docs/img/` y el resumen de la ejecución en
`docs/img/informe-capturas.json` (incluye los avisos de anclas no encontradas).

## Uso local

Requiere Node 20 o superior:

```bash
cd tools/capturas
npm install
npx playwright install chromium
BASE_URL=http://localhost:8080 USUARIO=admin CONTRASENA='Chn2026*Demo' SALIDA=../../docs/img npm run capturar
```

## Cómo se anclan las anotaciones

Cada elemento señalable de la interfaz lleva un atributo `data-captura="..."`
(por ejemplo `login-enviar`, `clientes-nuevo`, `form-pago-monto`). El guion
localiza ese atributo, dibuja el recuadro y coloca el número del paso.
Si un atributo cambia de nombre, la captura correspondiente se omite y el
aviso queda registrado en el informe, sin interrumpir el resto del proceso.
