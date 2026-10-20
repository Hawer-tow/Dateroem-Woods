def auth_header(authorization: str | None) -> dict | None:
    return {"Authorization": authorization} if authorization else None
