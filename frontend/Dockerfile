# Stage 1: Build the Angular application
FROM node:20 AS builder
WORKDIR /app
COPY package*.json ./
# `ci` installs exactly what package-lock.json says, and fails if it does not match package.json
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve the application using Nginx
# 1.28 or later: nginx.conf's upstream re-resolves the backend's address (`server ... resolve`)
FROM nginx:1.28-alpine
# Copy the built files
# Angular 17 'application' builder outputs to dist/rexchess/browser
# If using older builders, it might be just dist/rexchess
COPY --from=builder /app/dist/rexchess /tmp/dist
RUN if [ -d "/tmp/dist/browser" ]; then \
      cp -r /tmp/dist/browser/* /usr/share/nginx/html/; \
    else \
      cp -r /tmp/dist/* /usr/share/nginx/html/; \
    fi
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-snippets/ /etc/nginx/snippets/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
