import logging
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app.core.backend_client import PROBLEM
from app.core.request_context import incoming_authorization
from app.routers import ai_chat, auth, bulk_orders, deliveries, expenses, finance, health, insights, machines_vehicles, marketing, orders, products, settings, shipping, showcase, sms_queue, transactions, trees, trips, uploads, users, workers

# The automatic API description pages (/docs, /redoc, /openapi.json) are
# turned off: this server is reachable from the internet, and those pages
# would list every staff route and its request shape to anyone. The app
# never needs them.
app = FastAPI(title="Dateroem Mobile Server", docs_url=None, redoc_url=None, openapi_url=None)

# Anything this server didn't expect: logged here in full, answered with a
# calm sentence (18 October 2026). A request with fields that don't fit is
# answered in plain words too, not with the field-by-field technical list.
@app.exception_handler(RequestValidationError)
async def _invalid_request(request: Request, exc: RequestValidationError):
    logging.getLogger("dateroem.requests").info("invalid request to %s: %s", request.url.path, exc.errors()[:3])
    return JSONResponse(status_code=422, content={"detail": "Some of the details sent aren't valid. Please check them and try again."})


@app.exception_handler(Exception)
async def _unexpected(request: Request, exc: Exception):
    logging.getLogger("dateroem.errors").exception("unexpected error on %s", request.url.path)
    return JSONResponse(status_code=500, content={"detail": PROBLEM})



# "allows only one user per account per mobile phone" - every backend call
# made while handling a phone's request carries that phone's sign-in token
# (see core/request_context.py and backend_client._headers), so the backend
# can check the token belongs to an approved phone and take the acting user
# from it rather than from anything the phone claims.
@app.middleware("http")
async def remember_authorization(request: Request, call_next):
    marker = incoming_authorization.set(request.headers.get("authorization"))
    try:
        return await call_next(request)
    finally:
        incoming_authorization.reset(marker)


# "allow compression in transit for future traffic purposes" - shrinks
# the JSON list responses (products, trips, expenses, ...) that only grow
# as the catalog/history does.
app.add_middleware(GZipMiddleware, minimum_size=500)

# Shared across frontend and mobileServer (fathersProject/media, not a
# per-service folder) - same files either serves, no sync step. "cant seem
# to get the images in the mobile app" - mobileServer never mounted this
# itself, so backend_client.py's rewritten media/ paths (see there) had
# nothing to resolve against.
media_dir = Path(__file__).resolve().parent.parent.parent / "media"
media_dir.mkdir(exist_ok=True)
app.mount("/media", StaticFiles(directory=media_dir), name="media")

app.include_router(health.router)
app.include_router(auth.router)
app.include_router(ai_chat.router)
app.include_router(uploads.router)
app.include_router(products.router)
app.include_router(marketing.router)
app.include_router(expenses.router)
app.include_router(transactions.router)
app.include_router(finance.router)
app.include_router(bulk_orders.router)
app.include_router(deliveries.router)
app.include_router(trips.router)
app.include_router(shipping.router)
app.include_router(trees.router)
app.include_router(insights.router)
app.include_router(machines_vehicles.router)
app.include_router(sms_queue.router)
app.include_router(orders.router)
app.include_router(users.router)
app.include_router(showcase.router)
app.include_router(settings.router)
app.include_router(workers.router)
