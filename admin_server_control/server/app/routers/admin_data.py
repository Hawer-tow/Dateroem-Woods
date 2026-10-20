from fastapi import APIRouter, Header, Query

from app.core import backend_client
from app.core.auth_header import auth_header

router = APIRouter(prefix="/admin", tags=["admin"])


@router.get("/tables")
async def list_tables(authorization: str | None = Header(default=None)):
    """Every real table in the database, straight from information_schema -
    "one should be able to even see the databases and the data in them"."""
    return await backend_client.get("/api/admin/tables", headers=auth_header(authorization))


@router.get("/tables/{table_name}")
async def browse_table(
    table_name: str,
    limit: int | None = Query(default=None, description="Row limit - omit for every row"),
    offset: int = 0,
    authorization: str | None = Header(default=None),
):
    """"specifying all, only 50 etc" - limit omitted means all rows."""
    params: dict = {"offset": offset}
    if limit is not None:
        params["limit"] = limit
    return await backend_client.get(f"/api/admin/tables/{table_name}", params=params, headers=auth_header(authorization))
