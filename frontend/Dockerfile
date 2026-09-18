FROM node:20-alpine AS construccion

WORKDIR /app

# Manifiestos primero: si no cambian, Docker reutiliza la capa de dependencias.
COPY package*.json ./

# npm ci exige package-lock.json; sin lock se cae a npm install.
RUN npm ci || npm install

COPY . .

RUN npm run build

FROM nginx:1.27-alpine AS servidor

LABEL org.opencontainers.image.title="CHN - Frontend de gestión de préstamos"
LABEL org.opencontainers.image.description="Interfaz React servida por nginx"

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=construccion /app/dist /usr/share/nginx/html

# Los workers ya corren como el usuario sin privilegios nginx; el maestro queda como root
# solo por el puerto 80 (nginx-unprivileged en el 8080 evitaría incluso eso).

EXPOSE 80

# 127.0.0.1 y no localhost: en el contenedor localhost resuelve a ::1 y nginx solo escucha en IPv4.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/salud || exit 1

CMD ["nginx", "-g", "daemon off;"]
