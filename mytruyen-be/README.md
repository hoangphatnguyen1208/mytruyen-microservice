# MyTruyen Backend

> Backend API for MyTruyen online novel reading platform

## 📋 Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [System Requirements](#system-requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
- [Database Migration](#database-migration)
- [Testing](#testing)
- [Project Structure](#project-structure)

## 🎯 Introduction

MyTruyen Backend is an API server for an online novel reading platform, built with FastAPI and PostgreSQL. The system supports book management, chapters, authors, genres, and semantic search using AI embeddings.

## ✨ Features

- 🔐 **Authentication & Authorization**: JWT authentication with role-based access control (USER/ADMIN)
- 📚 **Book Management**: CRUD operations for books with complete metadata
- 📖 **Chapter Management**: Create, update, delete novel chapters
- 👤 **User Management**: Registration, login, profile management
- 🏷️ **Genres & Tags**: Categorize books by genres and tags
- ✍️ **Authors**: Author information management
- 🔍 **Semantic Search**: Search using BGE-M3 embeddings and Pinecone
- 💬 **Comments**: Comment system for chapters
- 📊 **Statistics**: Track views, bookmarks, ratings
- 🔄 **Background Jobs**: Redis + ARQ for task queue
- 🐳 **Docker Support**: Containerized deployment

## 🛠 Tech Stack

### Core Framework

- **FastAPI**: Modern, fast web framework for Python
- **SQLModel**: SQL databases with Python type hints
- **Alembic**: Database migration tool
- **Pydantic**: Data validation

### Database & Cache

- **PostgreSQL**: Primary database
- **Redis**: Caching and task queue
- **AsyncPG**: Async PostgreSQL driver

### AI/ML

- **PyTorch**: Deep learning framework
- **FlagEmbedding (BGE-M3)**: Text embeddings
- **Pinecone**: Vector database for semantic search

### Security

- **bcrypt**: Password hashing
- **PyJWT**: JWT tokens
- **Passlib**: Password utilities

### Development

- **pytest**: Testing framework
- **pytest-asyncio**: Async test support
- **pytest-cov**: Code coverage
- **httpx**: HTTP client for testing

## 💻 System Requirements

- Python >= 3.12
- PostgreSQL >= 15
- Redis >= 7
- Docker & Docker Compose (optional)

## 📦 Installation

### 1. Clone repository

```bash
git clone <repository-url>
cd mytruyen-be
```

### 2. Install dependencies

Using `uv` (recommended):

```bash
uv sync
```

Or using `pip`:

```bash
pip install -e .
```

### 3. Install development dependencies

```bash
uv sync
```

## 🚀 Running the Application

### Development (Local)

```bash
# Run migrations
alembic upgrade head

# Initialize sample data (optional)
python -m app.initial_data

# Start server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Production (Docker)

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

Services will run at:

- **API Server**: http://localhost:8000
- **PostgreSQL**: localhost:5433
- **Redis**: localhost:6379

## 📖 API Documentation

After starting the server, access:

- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

### API Versions

The project supports 2 API versions:

- **v1**: `/api/v1/*`
- **v2**: `/api/v2/*`

## 🔄 Database Migration

### Create new migration

```bash
alembic revision --autogenerate -m "description"
```

### Apply migrations

```bash
alembic upgrade head
```

### Rollback migration

```bash
alembic downgrade -1
```

### View migration history

```bash
alembic history
```

## 🤖 Embedding & Vector Search Setup

### Overview

The system uses BGE-M3 embeddings and Pinecone for semantic search functionality. The `embedding_data.py` script handles:

- Fetching chapter data from PostgreSQL
- Splitting text into chunks with overlap
- Generating hybrid embeddings (dense + sparse)
- Uploading vectors to Pinecone
- Semantic search and reranking

### Prerequisites

Before running embeddings:

1. **Pinecone Account**: Create account at [pinecone.io](https://www.pinecone.io/)
2. **API Key**: Get your Pinecone API key
3. **Model Download**: BGE-M3 model will auto-download (~2.3GB)
4. **Database**: Ensure chapters data exists in PostgreSQL

### Configuration

Add to your `.env` file:

```env
PINECONE_API_KEY=your_pinecone_api_key_here
```

### Running Embedding Process

```bash
# Embed ALL chapters from database and upload to Pinecone
python -m app.embedding_data

# The script will:
# 1. Create Pinecone index if not exists
# 2. Connect to index
# 3. Fetch all published chapters from database
# 4. Split into chunks with overlap
# 5. Generate BGE-M3 embeddings
# 6. Upload to Pinecone in batches
```

## 🧪 Testing

### Run all tests

```bash
pytest
```

### Run with coverage

```bash
pytest --cov=app --cov-report=html
```

### View coverage report

```bash
# HTML report will be generated in htmlcov/ directory
# Open htmlcov/index.html in browser
```

## 📁 Project Structure

```
mytruyen-be/
├── alembic/                # Database migrations
│   ├── versions/          # Migration scripts
│   └── env.py             # Alembic config
├── app/
│   ├── api/               # API routes
│   │   ├── v1/           # API version 1
│   │   ├── v2/           # API version 2
│   │   ├── deps.py       # Dependencies
│   │   └── main.py       # Router configuration
│   ├── core/              # Core functionality
│   │   ├── config.py     # App configuration
│   │   ├── db.py         # Database connection
│   │   └── security.py   # Security utilities
│   ├── crud/              # CRUD operations
│   ├── schema/            # Pydantic schemas
│   ├── utilities/         # Utility functions
│   ├── models.py          # SQLModel models
│   ├── main.py           # FastAPI app
│   └── initial_data.py   # Data seeding
├── tests/                 # Test suite
│   ├── api/              # API tests
│   └── conftest.py       # Test configuration
├── models/                # ML models (BGE-M3)
├── docker-compose.yml     # Docker compose config
├── Dockerfile            # Docker image
├── pyproject.toml        # Project dependencies
├── pytest.ini            # Pytest configuration
└── alembic.ini           # Alembic configuration
```

## 📊 Database Schema

### Main Models

- **User**: Users (USER/ADMIN roles)
- **Book**: Books/Novels
- **Chapter**: Novel chapters
- **ChapterContent**: Chapter content
- **Author**: Authors
- **Genre**: Genres
- **Tag**: Tags (categorization)
- **BookStatus**: Book status
- **Comment**: Comments

## 🤝 Contributing

All contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Create a Pull Request

## 📝 License

[Add your license here]

## 👥 Authors

[Add author information]

## 📞 Contact

[Add contact information]

---

Made with ❤️ using FastAPI
