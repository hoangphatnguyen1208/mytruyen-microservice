# MyTruyen Microservices

A collection of microservices for the MyTruyen story-reading platform. The repository contains Java (Gradle) services and a Python service (FastAPI).

Purpose: provide a working microservice reference architecture for account management, book/chapter management, and an API gateway.

Services included
- **auth-service** — authentication service (Java, Spring Boot, Gradle)
- **user-service** — user management service (Java, Spring Boot, Gradle)
- **mytruyen-gateway** — API gateway and central configuration (Java, Gradle)
- **book-service** — book and chapter service (Python, FastAPI, pyproject.toml)

Overview
- Each service runs independently and communicates over HTTP/REST.
- The gateway aggregates endpoints and handles routing and basic security configuration.

Prerequisites
- Java 11+ (or the version required by the services)
- Gradle (or use the included `gradlew` / `gradlew.bat` wrappers)
- Python 3.8+
- Docker (optional, for containerized runs)

Quick start

Run Java services locally (Linux/macOS):

```bash
cd auth-service
./gradlew bootRun

cd ../user-service
./gradlew bootRun

cd ../mytruyen-gateway
./gradlew bootRun
```

On Windows use `gradlew.bat`:

```powershell
cd auth-service
.\gradlew.bat bootRun
```

Run the Python service (`book-service`)

Using Poetry (if available):

```bash
cd book-service
poetry install
poetry run python -m app.main
```

Using virtualenv + pip:

```bash
cd book-service
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt  # or `pip install .` if packaged
python -m app.main
```

Configuration and environment variables
- Services typically require configuration for database connections, credentials, and secrets (e.g. JWT_SECRET). Check each service's `src/main/resources/application.yaml` (Java services) and the `book-service/app` configuration for required variables.

Docker and deployment
- Some services include Dockerfile(s) and may include a `docker-compose.yml` at the service level. Build images per service and orchestrate with your preferred tooling.

Testing
- `book-service` contains `pytest` tests (see `pytest.ini` and `tests/`).
- Java services use Gradle tests: `./gradlew test`.

Contributing
- Fork the repository and create a branch named `feature/<description>`.
- Open a Pull Request with a clear description and reproduction steps if applicable.

Developer notes
- Gateway configuration: mytruyen-gateway/src/main/resources/application.yaml
- Python application code: book-service/app

Contact
- Open an issue in this repository for bugs or feature requests.

---

This README provides essential instructions to get started. Would you like me to add a sample `docker-compose.yml` or an API reference section next?

Docker Compose
-
You can build and run all services together using the top-level `docker-compose.yml` added to the repository root.

Build images and start services:

```bash
cd <repo-root>
docker compose build
docker compose up
```

Run in detached mode:

```bash
docker compose up -d --build
```

Environment variables
- Place runtime secrets and connection strings in a `.env` file at the repository root. Example `.env`:

```
AUTH_DATABASE_URL=postgresql://user:pass@db:5432/authdb
USER_DATABASE_URL=postgresql://user:pass@db:5432/userdb
BOOK_DATABASE_URL=postgresql://user:pass@db:5432/bookdb
JWT_SECRET=replace_with_a_secure_secret
```

Notes
- The Java services are built with Gradle inside the Docker build and expose container port `8080`.
- `book-service` exposes container port `8000` (FastAPI).
- Adjust ports and environment variables in `docker-compose.yml` as needed for your deployment.
