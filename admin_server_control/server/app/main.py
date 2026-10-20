import logging
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app.core.backend_client import PROBLEM
from app.core.request_context import incoming_authorization
from app.routers import (
    admin_data,
    auth,
    computers,
    deliveries,
    devices,
    expenses,
    faqs,
    finance,
    health,
    insights,
    machines_vehicles,
    marketing,
    orders,
    products,
    reviews,
    roles,
    shipping,
    showcase,
    sms_queue,
    transactions,
    trees,
    trips,
    uploads,
    users,
)

# The automatic API description pages are turned off here too. The public
# router already answers /admin-api/docs* with 404, but turning them off in
# the app as well means they stay hidden however the server is reached.
app = FastAPI(title="Dateroem Admin Server Control", docs_url=None, redoc_url=None, openapi_url=None)

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



# "a standalone application ... that can work even if the backend is not in
# the same machine" - every backend call made while handling a desktop
# request carries that request's sign-in token (see core/request_context.py
# and backend_client._headers), so the backend can check it belongs to an
# approved computer and take the acting user from it rather than from
# anything the app claims.
@app.middleware("http")
async def remember_authorization(request: Request, call_next):
    marker = incoming_authorization.set(request.headers.get("authorization"))
    try:
        return await call_next(request)
    finally:
        incoming_authorization.reset(marker)


# "allow compression in transit for future traffic purposes"
app.add_middleware(GZipMiddleware, minimum_size=500)

app.include_router(health.router)
app.include_router(auth.router)
app.include_router(admin_data.router)
app.include_router(users.router)
app.include_router(devices.router)
app.include_router(computers.router)
app.include_router(faqs.router)
app.include_router(roles.router)
app.include_router(products.router)
app.include_router(marketing.router)
app.include_router(trips.router)
app.include_router(expenses.router)
app.include_router(finance.router)
app.include_router(deliveries.router)
app.include_router(shipping.router)
app.include_router(trees.router)
app.include_router(transactions.router)
app.include_router(insights.router)
app.include_router(machines_vehicles.router)
app.include_router(sms_queue.router)
app.include_router(orders.router)
app.include_router(reviews.router)
app.include_router(showcase.router)
app.include_router(uploads.router)

# The shared media folder (fathersProject/media, the same one the website
# and mobile servers serve) - backend_client rewrites every media address to
# this server's media/ path, so product pictures load wherever the desktop
# app reaches this server from.
media_dir = Path(__file__).resolve().parent.parent.parent.parent / "media"
media_dir.mkdir(exist_ok=True)
app.mount("/media", StaticFiles(directory=media_dir), name="media")
