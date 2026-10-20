from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    backend_base_url: str = "http://localhost:8080"
    # The backend's own media address (its app.media.base-url) - rewritten to
    # this server's media/ path in every response, so pictures load on any
    # computer the desktop app runs on, not only this one.
    media_base_url: str = "http://127.0.0.1:8000/media/"
    # Desktop app copies older than this are refused at sign-in and asked to
    # install the new one ("allow one to share it as an application to other
    # admins" - old shared copies don't linger).
    min_desktop_version: str = "1.1"


settings = Settings()
