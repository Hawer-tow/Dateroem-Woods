ADMIN_ROLES = {"Admin", "SuperAdmin"}


class Session:
    """Holds the signed-in staff member for the lifetime of the app - a
    single global instance, not persisted to disk (re-login every launch,
    deliberately: this is the "serious allowances" tool, not something
    that should silently stay signed in on a shared machine)."""

    def __init__(self):
        self.token: str | None = None
        self.user_id: int | None = None
        self.user_name: str | None = None
        self.name: str | None = None
        self.role_id: int | None = None
        self.role_name: str | None = None
        # The account's colour theme - "forest", "dark" or "light" (Settings).
        self.theme: str = "forest"

    @property
    def is_authenticated(self) -> bool:
        return self.token is not None

    @property
    def is_admin(self) -> bool:
        return self.role_name in ADMIN_ROLES

    def apply_login(self, data: dict):
        self.token = data["token"]
        self.user_id = data["userId"]
        self.user_name = data["userName"]
        self.name = data["name"]
        self.role_id = data["roleId"]
        self.role_name = data["roleName"]
        self.theme = data.get("theme") or "forest"

    def clear(self):
        self.__init__()
