import logging
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app.core.backend_client import PROBLEM
from app.core.config import settings
from app.routers import ai_chat, auth, health, help, heavy_orders, marketing, orders, products, reviews, shipping

# The automatic API description pages (/docs, /redoc, /openapi.json) are
# turned off: this server faces the public internet, and those pages would
# hand an attacker the full list of routes and request shapes. Nothing the
# website needs depends on them.
app = FastAPI(title="Dateroem Frontend Service", docs_url=None, redoc_url=None, openapi_url=None)

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


app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)
# "allow compression in transit for future traffic purposes" - JSON
# product/campaign listings compress well and only grow with catalog
# size; images/video are already-compressed formats so gzip barely
# touches them (a few % at most, never worse in practice), which is why
# the real fix for "heavy in the presence of many products" is the
# upload-time resize/re-encode in uploads.py, not this.
app.add_middleware(GZipMiddleware, minimum_size=500)

# Shared across frontend and mobileServer (fathersProject/media, not a
# per-service folder) - mobileServer writes uploaded product photos here,
# frontend serves them, so both clients see the same files without a sync
# step.
media_dir = Path(__file__).resolve().parent.parent.parent / "media"
media_dir.mkdir(exist_ok=True)
app.mount("/media", StaticFiles(directory=media_dir), name="media")

app.include_router(health.router)
app.include_router(auth.router)
app.include_router(products.router)
app.include_router(marketing.router)
app.include_router(orders.router)
app.include_router(shipping.router)
app.include_router(help.router)
app.include_router(heavy_orders.router)
app.include_router(ai_chat.router)
app.include_router(reviews.router)
