# Túnel SSH hacia la EC2 de HidroGest: la BD real escucha solo en 127.0.0.1 del servidor, así que
# el servicio la alcanza en hidrogest-tunnel:3306 sin abrir el puerto 3306 en el Security Group.
FROM alpine:3.20
RUN apk add --no-cache openssh-client
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 3306
HEALTHCHECK --interval=10s --timeout=5s --retries=6 --start-period=15s CMD nc -z 127.0.0.1 3306 || exit 1
ENTRYPOINT ["/entrypoint.sh"]
