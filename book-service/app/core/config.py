from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_ignore_empty=True,
        extra="ignore"
    )
    PROJECT_NAME: str

    API_V1_STR: str
    API_V2_STR: str
    POSTGRES_SERVER: str
    POSTGRES_PORT: int
    POSTGRES_DB: str
    POSTGRES_USER: str
    POSTGRES_PASSWORD: str
    POSTGRES_URL: str
    POSTGRES_SYNC_URL: str

    ACCESS_TOKEN_EXPIRE_MINUTES: int 
    REFRESH_TOKEN_EXPIRE_DAYS: int
    JWT_SECRET_KEY: str 
    JWT_ALGORITHM: str

    FIRST_ADMIN_EMAIL: str
    FIRST_ADMIN_PASSWORD: str

    REDIS_HOST: str
    REDIS_PORT: int
    REDIS_PASSWORD: str

    PINECONE_API_KEY: str

settings = Settings()