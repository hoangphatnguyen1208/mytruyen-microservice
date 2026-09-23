from pydantic import Field, HttpUrl, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(extra="ignore")
    meili_url: HttpUrl = "http://localhost:7700"
    meili_master_key: SecretStr = SecretStr("")
    meili_index: str = Field(default="books", pattern=r"^[a-zA-Z0-9_-]+$", max_length=100)
    catalog_url: HttpUrl = "http://localhost:8082"
    request_timeout: float = Field(default=5.0, gt=0, le=30)
