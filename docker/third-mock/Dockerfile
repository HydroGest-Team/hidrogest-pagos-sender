FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir gunicorn flask

COPY mock_third_api.py .

EXPOSE 8089

HEALTHCHECK --interval=10s --timeout=10s --retries=10 --start-period=30s \
    CMD curl -f http://localhost:8089/third/v1/health || exit 1

CMD ["gunicorn", "--bind", "0.0.0.0:8089", "--workers", "1", "--threads", "4", "mock_third_api:app"]