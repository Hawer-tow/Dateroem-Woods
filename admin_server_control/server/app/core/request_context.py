from contextvars import ContextVar

# The desktop app's "Authorization: Bearer ..." header for the request being
# handled, so backend_client can pass it on with every backend call without
# each route having to thread it through (same as the mobile server). Set by
# the middleware in main.py.
incoming_authorization: ContextVar[str | None] = ContextVar("incoming_authorization", default=None)
