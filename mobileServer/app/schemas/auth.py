from typing import Literal

from pydantic import BaseModel


class DeviceProof(BaseModel):
    # The phone's proof of which install it is (see the backend's
    # DeviceService): its public key, a one-time challenge and that
    # challenge signed by the key that never leaves the phone.
    publicKey: str
    challenge: str
    signature: str
    model: str | None = None
    osVersion: str | None = None
    appVersion: str | None = None


class LoginRequest(BaseModel):
    user_name: str
    password: str
    # Sent by every current version of the app; older versions without it
    # get a token that the backend refuses for mobile work.
    device: DeviceProof | None = None


class ThemeRequest(BaseModel):
    # The colour themes an account can choose (USERS.Theme, migration 037).
    theme: Literal["forest", "dark", "light"]
