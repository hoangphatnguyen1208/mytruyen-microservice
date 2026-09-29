FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"
COPY pyproject.toml uv.lock ./
COPY app ./app
RUN pip install --no-cache-dir uv==0.11.15 \
    && uv sync --frozen --no-dev --no-editable \
    && useradd --uid 10001 --create-home app
USER app
EXPOSE 8003
ENV SEARCH_PORT=8003
CMD ["sh", "-c", "exec fastapi run app/main.py --host 0.0.0.0 --port \"$SEARCH_PORT\""]
