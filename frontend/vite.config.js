import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5173,
    host: true,
    // En desarrollo este proxy hace el papel de nginx: la API queda en el mismo origen, sin CORS.
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    // Sin sourcemaps: no se publica el código original.
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          proveedores: ['react', 'react-dom', 'react-router-dom', 'axios'],
        },
      },
    },
  },
});
