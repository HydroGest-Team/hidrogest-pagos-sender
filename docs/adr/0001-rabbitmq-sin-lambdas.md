# ADR-0001 · RabbitMQ como cola y sin Lambdas

**Estado:** aceptado · 2026-10-23

## Contexto
El diagrama de la clase dibuja una cola con icono parecido a SQS y una Lambda que marca el flag
"enviado" en MySQL. La diapositiva "Las 4 features de la Fase 2" define la Feature 3 como
"Spring Boot + RabbitMQ · productor / consumidor". HidroGest no tiene columna de flag y su BD es
de solo lectura para este servicio.

## Decisión
- La cola es **RabbitMQ** (Spring AMQP), no SQS.
- **No se construye ninguna Lambda.** El consumer escribe el resultado de cada envío directamente
  en DynamoDB (`pagos_entregas` y `pagos_intentos`).

## Consecuencias
- Un solo despliegue (el microservicio) y una sola tecnología de mensajería que se corre en local
  con Docker Compose.
- El "flag enviado" vive en DynamoDB por (pago, destino), lo que además permite saber **a qué
  destino** le falta cada pago (requisito central de la feature).
