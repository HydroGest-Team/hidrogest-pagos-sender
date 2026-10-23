import hashlib
import json
import logging
import os
import random
import re
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone
from flask import Flask, jsonify, request

# Configure logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

app = Flask(__name__)

PORT = int(os.environ.get("PORT", "8089"))
API_KEY = os.environ.get("THIRD_API_KEY", "dev-key")
TEAM_OF_KEY = os.environ.get("THIRD_TEAM_CODE", "EQUIPO_A")
DEFAULT_SCENARIO = os.environ.get("THIRD_DEFAULT_SCENARIO", "SUCCESS").upper()
ALLOW_SCENARIO_HEADER = os.environ.get("THIRD_ALLOW_SCENARIO_HEADER", "true").lower() == "true"
MAX_AMOUNT_CENTS = 2_500_000
SCENARIOS = {"AUTO", "SUCCESS", "SLOW", "TIMEOUT", "LOST_ACK", "ERROR_500", "ERROR_503", "RATE_LIMIT"}
TEAMS = {"EQUIPO_A", "EQUIPO_B", "EQUIPO_C", "EQUIPO_D"}
KEY_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
AMOUNT_RE = re.compile(r"^[0-9]{1,8}\.[0-9]{2}$")
PERIOD_RE = re.compile(r"^[0-9]{4}-(0[1-9]|1[0-2])$")

_lock = threading.Lock()
_store = {}  # (team, key) -> {"hash": str, "receipt": dict}


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def body_hash(body: dict) -> str:
    return hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def pick_auto():
    r = random.random()
    if r < 0.60:
        return "SUCCESS"
    if r < 0.70:
        return "SLOW"
    if r < 0.78:
        return "TIMEOUT"
    if r < 0.86:
        return "LOST_ACK"
    if r < 0.92:
        return "ERROR_500"
    if r < 0.96:
        return "ERROR_503"
    return "RATE_LIMIT"


def validate_payload(b):
    p = []
    if not isinstance(b, dict):
        return ["El body debe ser un objeto"]
    for f in ("teamCode", "reference", "customer", "amount", "currency", "paidAt", "method", "period"):
        if f not in b:
            p.append(f"Falta {f}")
    if p:
        return p
    if b["teamCode"] not in TEAMS:
        p.append("teamCode inválido")
    if not isinstance(b["reference"], str) or not 0 < len(b["reference"]) <= 30:
        p.append("reference inválida (1-30)")
    c = b["customer"]
    if not isinstance(c, dict) or not isinstance(c.get("code"), str) or not 0 < len(c["code"]) <= 50:
        p.append("customer.code inválido")
    if not isinstance(b["amount"], str) or not AMOUNT_RE.match(b["amount"]) or int(b["amount"].replace(".", "")) <= 0:
        p.append("amount debe ser texto con 2 decimales, ej. '150.75'")
    if b["currency"] != "GTQ":
        p.append("currency debe ser GTQ")
    if b["method"] not in ("CASH", "CARD"):
        p.append("method debe ser CASH o CARD")
    if not isinstance(b["period"], str) or not PERIOD_RE.match(b["period"]):
        p.append("period debe ser YYYY-MM")
    try:
        datetime.fromisoformat(str(b["paidAt"]).replace("Z", "+00:00"))
    except ValueError:
        p.append("paidAt no es date-time ISO-8601")
    return p


def error_response(status, code, message, corr, retryable=False, extra=None, more=None):
    body = {"status": status, "code": code, "message": message, "retryable": retryable,
            "correlationId": corr, "timestamp": now_iso()}
    if more:
        body.update(more)
    resp = jsonify(body)
    resp.status_code = status
    resp.headers["X-Correlation-Id"] = corr
    if extra:
        for k, v in extra.items():
            resp.headers[k] = str(v)
    return resp


@app.before_request
def log_request_info():
    logger.debug(f"Request: {request.method} {request.path}")
    logger.debug(f"Headers: {dict(request.headers)}")
    logger.debug(f"Body: {request.get_data()}")


@app.route("/third/v1/health", methods=["GET"])
def health():
    return jsonify({"status": "UP", "service": "third-payments-mock", "time": now_iso()})


@app.route("/third/v1/payments/<idempotency_key>", methods=["GET"])
def get_payment(idempotency_key):
    corr = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    if request.headers.get("x-api-key") != API_KEY:
        return error_response(403, "UNAUTHORIZED", "API key inválida", corr)
    with _lock:
        rec = _store.get((TEAM_OF_KEY, idempotency_key))
    if rec:
        return jsonify(rec["receipt"])
    return error_response(404, "PAYMENT_NOT_FOUND",
                          "No existe un pago con esa Idempotency-Key para el equipo", corr)


@app.route("/third/v1/payments", methods=["POST"])
def send_payment():
    corr = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    
    # Auth
    if request.headers.get("x-api-key") != API_KEY:
        return error_response(403, "UNAUTHORIZED", "API key inválida", corr)
    
    # Scenario
    scenario = DEFAULT_SCENARIO
    hdr = (request.headers.get("X-Simulation-Scenario") or "").upper()
    if hdr and ALLOW_SCENARIO_HEADER:
        if hdr not in SCENARIOS:
            return error_response(400, "VALIDATION_ERROR", f"Escenario inválido: {hdr}", corr)
        scenario = hdr
    if scenario == "AUTO":
        scenario = pick_auto()
    
    # Idempotency-Key
    key = request.headers.get("Idempotency-Key", "")
    if not KEY_RE.match(key):
        return error_response(400, "VALIDATION_ERROR",
                              "Idempotency-Key requerida (8-64, A-Za-z0-9_-)", corr)
    
    # Parse JSON
    logger.debug(f"Content-Type: {request.content_type}")
    logger.debug(f"Raw data: {request.get_data()}")
    try:
        body = request.get_json(force=True) or {}
        logger.debug(f"Parsed body: {body}")
    except Exception as e:
        logger.error(f"JSON parse error: {e}")
        return error_response(400, "VALIDATION_ERROR", "JSON inválido", corr)
    
    # Validation
    problems = validate_payload(body)
    if problems:
        logger.debug(f"Validation problems: {problems}")
        return error_response(400, "VALIDATION_ERROR", "; ".join(problems), corr)
    if body["teamCode"] != TEAM_OF_KEY:
        return error_response(403, "TEAM_MISMATCH", "teamCode no corresponde a la API key", corr)
    
    cents = int(body["amount"].replace(".", ""))
    if cents > MAX_AMOUNT_CENTS:
        return error_response(422, "AMOUNT_LIMIT_EXCEEDED", "Monto máximo 25000.00", corr)
    paid = datetime.fromisoformat(body["paidAt"].replace("Z", "+00:00"))
    if paid.tzinfo is None:
        return error_response(400, "VALIDATION_ERROR", "paidAt debe incluir zona horaria", corr)
    if paid > datetime.now(timezone.utc) + timedelta(minutes=5):
        return error_response(422, "PAYMENT_DATE_IN_FUTURE", "paidAt está en el futuro", corr)
    
    # Scenarios without registration
    if scenario == "RATE_LIMIT":
        return error_response(429, "RATE_LIMITED", "Demasiadas peticiones", corr, True, {"Retry-After": 1})
    if scenario == "ERROR_503":
        return error_response(503, "SERVICE_UNAVAILABLE", "Servicio no disponible", corr, True, {"Retry-After": 2})
    if scenario == "ERROR_500":
        return error_response(500, "INTERNAL_ERROR", "Error interno", corr, True)
    if scenario == "TIMEOUT":
        time.sleep(random.uniform(4, 8))
        return error_response(504, "INTERNAL_ERROR", "Endpoint request timed out", corr, True)
    if scenario == "SLOW":
        time.sleep(random.uniform(1.5, 2.5))
    
    # Idempotency + registration
    h = body_hash(body)
    with _lock:
        rec = _store.get((body["teamCode"], key))
        if rec:
            if rec["hash"] != h:
                return error_response(409, "IDEMPOTENCY_KEY_REUSED",
                                      "La llave ya se usó con datos distintos", corr)
            return error_response(409, "DUPLICATE_PAYMENT", "Pago ya registrado", corr,
                                  more={"originalConfirmation": rec["receipt"]})
        receipt = {
            "confirmationId": f"THR-{datetime.now(timezone.utc):%Y%m%d}-{uuid.uuid4().hex[:6].upper()}",
            "idempotencyKey": key, "teamCode": body["teamCode"], "status": "ACCEPTED",
            "amount": body["amount"], "receivedAt": now_iso(),
        }
        _store[(body["teamCode"], key)] = {"hash": h, "receipt": receipt}
    if scenario == "LOST_ACK":
        time.sleep(random.uniform(4, 8))
    
    resp = jsonify(receipt)
    resp.status_code = 201
    resp.headers["X-Correlation-Id"] = corr
    return resp


if __name__ == "__main__":
    print(f"Mock tercera API en http://localhost:{PORT} | equipo={TEAM_OF_KEY} | "
          f"escenario por defecto={DEFAULT_SCENARIO} | header habilitado={ALLOW_SCENARIO_HEADER}")
    app.run(host="0.0.0.0", port=PORT, threaded=True)