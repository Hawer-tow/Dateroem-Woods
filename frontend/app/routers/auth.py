from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.auth import GoogleSignInRequest, LoginRequest, PhoneCodeRequest, RegisterRequest, ThemeRequest

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register")
async def register(request: RegisterRequest):
    """Modest registration: browsing and cart-building never require an
    account, only checkout does (see orders.checkout). Registering here
    creates a Customer-role USERS row and returns a token the site stores
    to keep the visitor signed in."""
    payload = {
        "userName": request.user_name,
        "name": request.name,
        "password": request.password,
        "email": request.email,
        "phoneNo": request.phone_no,
        "phoneProof": request.phone_proof,
    }
    return await backend_client.post("/api/auth/register", json=payload)


# "push a code to the phone number they placed so that its verified" - the
# sign-up's SMS code step: send a code, then check it; a correct code gives
# the phone_proof that /register and /google carry. Limits (per number and
# overall) are kept by the backend, which also sends the SMS.
@router.post("/phone/send-code")
async def send_phone_code(request: PhoneCodeRequest):
    return await backend_client.post("/api/auth/phone/send-code", json={"phoneNo": request.phone_no})


@router.post("/phone/verify")
async def verify_phone_code(request: PhoneCodeRequest):
    return await backend_client.post("/api/auth/phone/verify", json={"phoneNo": request.phone_no, "code": request.code})


# The same for a signed-in customer whose number isn't confirmed yet (asked
# at checkout): a correct code makes it the account's number.
@router.post("/me/phone/send-code")
async def send_my_phone_code(request: PhoneCodeRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post(
        "/api/auth/me/phone/send-code", json={"phoneNo": request.phone_no},
        headers={"Authorization": authorization} if authorization else None,
    )


@router.post("/me/phone/verify")
async def verify_my_phone_code(request: PhoneCodeRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post(
        "/api/auth/me/phone/verify", json={"phoneNo": request.phone_no, "code": request.code},
        headers={"Authorization": authorization} if authorization else None,
    )


@router.post("/login")
async def login(request: LoginRequest):
    payload = {"userName": request.user_name, "password": request.password}
    return await backend_client.post("/api/auth/login", json=payload)


@router.get("/providers")
async def providers():
    """Which sign-in options to show - {"googleClientId": ...}, null when
    Google sign-in isn't set up (the site then offers username/password
    only)."""
    return await backend_client.get("/api/auth/providers")


@router.post("/google")
async def google(request: GoogleSignInRequest):
    """"Continue with Google": the backend checks Google's token itself.
    Answers with a signed-in session, or {needsPhone, needsUserName, name,
    email} when this is someone new - the site then asks for their phone
    number and a username of their own, and calls again with both."""
    payload = {
        "credential": request.credential,
        "phoneNo": request.phone_no,
        "userName": request.user_name,
        "phoneProof": request.phone_proof,
    }
    return await backend_client.post("/api/auth/google", json=payload)


@router.get("/me")
async def me(authorization: str | None = Header(default=None)):
    """Lets the site restore a signed-in session after a page reload by
    validating the stored token against the backend instead of trusting
    whatever is sitting in localStorage."""
    return await backend_client.get("/api/auth/me", headers={"Authorization": authorization} if authorization else None)


@router.put("/me/theme")
async def save_theme(request: ThemeRequest, authorization: str | None = Header(default=None)):
    """The signed-in customer's colour theme (forest, dark or light), so the
    site opens in it on any device. Visitors who are not signed in keep their
    choice in their own browser only."""
    return await backend_client.put(
        "/api/auth/me/theme", json={"theme": request.theme},
        headers={"Authorization": authorization} if authorization else None,
    )
