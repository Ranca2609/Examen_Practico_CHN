// Uso: docker compose --profile herramientas run --rm capturas bash -lc "cd /tmp/capturas && cp /trabajo/diagnostico.mjs . && node diagnostico.mjs"
import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const USUARIO = process.env.USUARIO || 'admin';
const CONTRASENA = process.env.CONTRASENA || 'Chn2026*Demo';

const RUTAS = ['/', '/clientes', '/solicitudes', '/solicitudes/nueva', '/prestamos', '/pagos', '/auditoria'];

const navegador = await chromium.launch();
const page = await navegador.newPage({ viewport: { width: 1440, height: 900 } });

page.on('console', (m) => {
  if (m.type() === 'error') console.log(`   [consola] ${m.text().slice(0, 160)}`);
});
page.on('requestfailed', (r) => console.log(`   [red] FALLÓ ${r.url()}`));
page.on('response', (r) => {
  if (r.url().includes('/api/') && r.status() >= 400) {
    console.log(`   [api] ${r.status()} ${r.url()}`);
  }
});

await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
await page.locator('[data-captura="login-usuario"]').fill(USUARIO);
await page.locator('[data-captura="login-contrasena"]').fill(CONTRASENA);
await page.locator('[data-captura="login-enviar"]').click();
await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 20000 });
console.log('Sesión iniciada.\n');

for (const ruta of RUTAS) {
  await page.goto(`${BASE_URL}${ruta}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1800);
  const info = await page.evaluate(() =>
    Array.from(document.querySelectorAll('[data-captura]')).map((e) => {
      const n = e.getAttribute('data-captura');
      // CampoSelect es un combobox propio: la selección vive en data-valor, que es lo que valida el guion.
      let extra = '';
      if (e.tagName === 'SELECT') {
        extra = ` opciones=${e.querySelectorAll('option').length}`;
      } else if (e.getAttribute('role') === 'combobox') {
        extra = ` combobox valor='${e.getAttribute('data-valor') ?? ''}'`;
      }
      return `${n} <${e.tagName.toLowerCase()}${extra}>`;
    }),
  );
  console.log(`== ${ruta} (${info.length} anclas)`);
  info.forEach((i) => console.log(`   ${i}`));
  console.log('');
}

await navegador.close();
