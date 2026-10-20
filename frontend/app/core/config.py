from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    backend_base_url: str = "http://localhost:8080"

    # Must match the Java backend's own app.media.base-url (same env var
    # name, MEDIA_BASE_URL) - backend_client.py rewrites any URL starting
    # with this prefix to a website-relative /media/ path before it ever
    # reaches the browser. See backend_client.py for why.
    media_base_url: str = "http://127.0.0.1:8000/media/"

    db_host: str = "localhost"
    # Moved off 3306 - something outside this project parks a phantom
    # listener there (survives even a full reboot), so MySQL itself now
    # runs on 3309 (see mysqld.cnf and the Java backend's datasource url).
    db_port: int = 3309
    db_name: str = "dateroem"
    db_user: str = "dateroem_frontend"
    db_password: str = ""

    redis_url: str = "redis://localhost:6379/0"

    # The business mailbox (dateroemwoodsales@gmail.com). The "+support"
    # part still lands in that same inbox, and a Gmail filter on it files
    # these messages under the Customers label. support_name is what the
    # Help page shows on the email button (the address is shown under it).
    support_email: str = "dateroemwoodsales+support@gmail.com"
    support_name: str = "Dateroem Wood Sales Support"
    # The bulk (heavy) orders desk: the same mailbox's "+orders" address,
    # which a Gmail filter keeps apart from support. The Bulk Orders page
    # shows it and its email window writes to it.
    orders_email: str = "dateroemwoodsales+orders@gmail.com"
    orders_name: str = "Dateroem Wood Sales Orders"
    # The company phone number (since 9 October 2026; it was 0700878260),
    # used for calls and WhatsApp. Shown on the Help page, the Bulk Orders
    # page and every page's footer, and given in the chat helper's
    # fallback answer. To change it: SUPPORT_PHONE / SUPPORT_WHATSAPP in
    # frontend/.env (or the hosted server's website-server.env) override
    # these, then restart the website server - see "Changing the company
    # phone number" in the Configuration and Secrets Reference.
    support_phone: str = "+254182995011"
    support_whatsapp: str = "+254182995011"
    # The business's TikTok and Instagram pages (full https links), shown in
    # every page's footer; left empty, that link is simply not shown.
    support_tiktok: str = "https://www.tiktok.com/@dateroem.woods"
    support_instagram: str = "https://www.instagram.com/dateroem.woods/"

    # Vite's and CRA's default dev ports - update/extend once the real
    # website's dev and production origins are known.
    # The Help page's "Send us an email" form sends through this Gmail
    # account (an App Password, never the account password). Both come from
    # frontend/.env; while either is empty the form is not shown.
    smtp_user: str = ""
    smtp_app_password: str = ""
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 465

    cors_origins: list[str] =["http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:3000"]


settings = Settings()
