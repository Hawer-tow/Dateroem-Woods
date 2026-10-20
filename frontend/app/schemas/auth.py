from typing import Literal

from pydantic import BaseModel, Field


class RegisterRequest(BaseModel):
    user_name: str
    name: str
    password: str
    email: str | None = None
    phone_no: str | None = None
    # From /auth/phone/verify: shows the number was confirmed by an SMS code.
    phone_proof: str | None = Field(default=None, max_length=100)


class LoginRequest(BaseModel):
    user_name: str
    password: str


class GoogleSignInRequest(BaseModel):
    # The sign-in token from the "Continue with Google" button; the phone
    # number and a username of their own choosing only on the second call,
    # for someone new.
    credential: str
    phone_no: str | None = None
    user_name: str | None = None
    phone_proof: str | None = Field(default=None, max_length=100)


class PhoneCodeRequest(BaseModel):
    # "push a code to the phone number they placed so that its verified" -
    # the number to send a code to, and (when checking) the code typed back.
    phone_no: str = Field(max_length=20)
    code: str | None = Field(default=None, max_length=10)


class ThemeRequest(BaseModel):
    # The colour themes an account can choose (USERS.Theme, migration 037).
    theme: Literal["forest", "dark", "light"]
