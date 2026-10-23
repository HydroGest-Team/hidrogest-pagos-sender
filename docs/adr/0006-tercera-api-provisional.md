# ADR-0006 · Tercera API con contrato provisional y mock local

**Estado:** aceptado (temporal) · 2026-10-23

## Contexto
El docente entregó solo los contratos y simuladores del Banco y del ERP. La tercera API no existe
todavía.

## Decisión
- `third-payments-api.yaml` es un contrato **propio y provisional**, con estilo distinto a los otros
  dos (monto como texto, `period`, `reference`), para que el adaptador no sea una copia del banco.
- En local la atiende `docker/third-mock/mock_third_api.py`: un servidor HTTP real con los mismos
  escenarios de fallo (`X-Simulation-Scenario`), idempotencia y `409` que los simuladores del docente.
- Todo lo del destino vive en `infrastructure/destinos/tercero/`; se puede desactivar con
  `DESTINOS_ACTIVOS=BANCO,ERP`.

## Cuando llegue la API real
1. Reemplazar `src/main/resources/openapi/third-payments-api.yaml` y `docs/third-payments-api.yaml`.
2. Ajustar solo el paquete `destinos/tercero` (cliente, mapper, rutas JSON del decodificador).
3. Configurar `THIRD_BASE_URL` y `THIRD_API_KEY`. Para no reenviar el historial, poner
   `pagos.desde.TERCERO` con la fecha de corte; el barrido de consistencia hará la puesta al día.
