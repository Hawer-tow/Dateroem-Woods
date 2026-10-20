from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    backend_base_url: str = "http://localhost:8080"
    # Matches the Java backend's app.media.base-url default - used to
    # recognize/strip the absolute, loopback-only URL it bakes into every
    # image field (see _rewrite_media_urls in backend_client.py).
    media_base_url: str = "http://127.0.0.1:8000/media/"

    db_host: str = "localhost"
    # Moved off 3306 - something outside this project parks a phantom
    # listener there (survives even a full reboot), so MySQL itself now
    # runs on 3309 (see mysqld.cnf and the Java backend's datasource url).
    db_port: int = 3309
    db_name: str = "dateroem"
    db_user: str = "dateroem_mobile"
    db_password: str = ""

    redis_url: str = "redis://localhost:6379/1"


settings = Settings()
