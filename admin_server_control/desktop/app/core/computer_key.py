"""This computer's sign-in key for the desktop app.

"a standalone application that has higher privilege ... allow one to share
it as an application to other admins" - the backend only lets the desktop
app in from approved computers (its ComputerService), and a computer proves
which one it is by signing a one-time challenge with this key, the way the
phone app does with its phone's secure hardware.

Windows' own key store (CNG, through ncrypt.dll - nothing extra to install)
creates and keeps the key. The computer's TPM security chip holds it when
there is one (Microsoft Platform Crypto Provider), otherwise Windows'
software key store does. Either way the private key is made inside Windows,
can't be exported and belongs to this Windows user, so copying the app or
its folder to another computer does not copy it.
"""
import base64
import ctypes
import hashlib
import platform
import struct
from ctypes import wintypes

KEY_NAME = "Dateroem Admin Control sign-in"
# TPM first, so the key lives in hardware where the computer has a TPM.
_PROVIDERS = ("Microsoft Platform Crypto Provider", "Microsoft Software Key Storage Provider")
_ALGORITHM = "ECDSA_P256"
_PUBLIC_BLOB = "ECCPUBLICBLOB"
_NTE_BAD_KEYSET = 0x80090016  # no key by that name yet
# An X.509 SubjectPublicKeyInfo for a P-256 key (id-ecPublicKey, prime256v1)
# is this fixed DER prefix followed by the 65-byte uncompressed point - the
# form the backend reads (X509EncodedKeySpec).
_P256_SPKI_PREFIX = bytes.fromhex("3059301306072a8648ce3d020106082a8648ce3d030107034200")

_HANDLE = ctypes.c_size_t
_ncrypt = None


class ComputerKeyError(Exception):
    pass


def _lib():
    global _ncrypt
    if _ncrypt is None:
        lib = ctypes.WinDLL("ncrypt.dll")
        status = ctypes.c_long
        lib.NCryptOpenStorageProvider.argtypes = [ctypes.POINTER(_HANDLE), ctypes.c_wchar_p, wintypes.DWORD]
        lib.NCryptOpenKey.argtypes = [_HANDLE, ctypes.POINTER(_HANDLE), ctypes.c_wchar_p, wintypes.DWORD, wintypes.DWORD]
        lib.NCryptCreatePersistedKey.argtypes = [
            _HANDLE, ctypes.POINTER(_HANDLE), ctypes.c_wchar_p, ctypes.c_wchar_p, wintypes.DWORD, wintypes.DWORD
        ]
        lib.NCryptFinalizeKey.argtypes = [_HANDLE, wintypes.DWORD]
        lib.NCryptExportKey.argtypes = [
            _HANDLE, _HANDLE, ctypes.c_wchar_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD,
            ctypes.POINTER(wintypes.DWORD), wintypes.DWORD,
        ]
        lib.NCryptSignHash.argtypes = [
            _HANDLE, ctypes.c_void_p, ctypes.c_char_p, wintypes.DWORD, ctypes.c_void_p, wintypes.DWORD,
            ctypes.POINTER(wintypes.DWORD), wintypes.DWORD,
        ]
        lib.NCryptDeleteKey.argtypes = [_HANDLE, wintypes.DWORD]
        lib.NCryptFreeObject.argtypes = [_HANDLE]
        for name in ("NCryptOpenStorageProvider", "NCryptOpenKey", "NCryptCreatePersistedKey", "NCryptFinalizeKey",
                     "NCryptExportKey", "NCryptSignHash", "NCryptDeleteKey", "NCryptFreeObject"):
            getattr(lib, name).restype = status
        _ncrypt = lib
    return _ncrypt


def _code(status: int) -> int:
    return status & 0xFFFFFFFF


class ComputerKey:
    """The key itself is created the first time it is needed and kept by
    Windows from then on. name is only ever changed by tests."""

    def __init__(self, name: str = KEY_NAME):
        self.name = name

    def proof(self, challenge: str, app_version: str) -> dict:
        """What the backend needs to recognise this computer: the public key,
        the challenge signed with the private key, and what the SuperAdmin
        sees when approving it."""
        provider, key = self._open(create=True)
        try:
            public_key = self._public_key(key)
            signature = self._sign(key, challenge.encode("utf-8"))
        finally:
            _lib().NCryptFreeObject(key)
            _lib().NCryptFreeObject(provider)
        return {
            "publicKey": base64.b64encode(public_key).decode("ascii"),
            "challenge": challenge,
            "signature": base64.b64encode(signature).decode("ascii"),
            "model": (platform.node() or "Windows computer")[:120],
            "osVersion": f"Windows {platform.release()} ({platform.version()})"[:40],
            "appVersion": app_version,
        }

    def delete(self) -> bool:
        """Removes the key from Windows (tests, or starting over); False if there was none."""
        opened = self._open(create=False)
        if opened is None:
            return False
        provider, key = opened
        try:
            # NCryptDeleteKey also frees the key handle.
            status = _lib().NCryptDeleteKey(key, 0)
            if status != 0:
                raise ComputerKeyError(f"Windows could not remove this computer's sign-in key (0x{_code(status):08X}).")
        finally:
            _lib().NCryptFreeObject(provider)
        return True

    # ---------------------------------------------------------------- helpers

    def _open(self, create: bool):
        lib = _lib()
        # An existing key first, wherever it was made.
        for provider_name in _PROVIDERS:
            provider = _HANDLE()
            if lib.NCryptOpenStorageProvider(ctypes.byref(provider), provider_name, 0) != 0:
                continue
            key = _HANDLE()
            if lib.NCryptOpenKey(provider, ctypes.byref(key), self.name, 0, 0) == 0:
                return provider, key
            lib.NCryptFreeObject(provider)
        if not create:
            return None
        # None yet: make one - in the TPM if this computer has a working one.
        failures = []
        for provider_name in _PROVIDERS:
            provider = _HANDLE()
            status = lib.NCryptOpenStorageProvider(ctypes.byref(provider), provider_name, 0)
            if status != 0:
                failures.append(f"{provider_name}: 0x{_code(status):08X}")
                continue
            key = _HANDLE()
            status = lib.NCryptCreatePersistedKey(provider, ctypes.byref(key), _ALGORITHM, self.name, 0, 0)
            if status == 0:
                status = lib.NCryptFinalizeKey(key, 0)
                if status == 0:
                    return provider, key
                lib.NCryptDeleteKey(key, 0)
            failures.append(f"{provider_name}: 0x{_code(status):08X}")
            lib.NCryptFreeObject(provider)
        raise ComputerKeyError("Windows could not create this computer's sign-in key (" + "; ".join(failures) + ").")

    @staticmethod
    def _public_key(key) -> bytes:
        lib = _lib()
        size = wintypes.DWORD()
        status = lib.NCryptExportKey(key, 0, _PUBLIC_BLOB, None, None, 0, ctypes.byref(size), 0)
        if status != 0:
            raise ComputerKeyError(f"Windows could not read this computer's sign-in key (0x{_code(status):08X}).")
        blob = ctypes.create_string_buffer(size.value)
        status = lib.NCryptExportKey(key, 0, _PUBLIC_BLOB, None, blob, size, ctypes.byref(size), 0)
        if status != 0:
            raise ComputerKeyError(f"Windows could not read this computer's sign-in key (0x{_code(status):08X}).")
        # BCRYPT_ECCKEY_BLOB: magic, key length, then X and Y.
        _magic, length = struct.unpack("<II", blob.raw[:8])
        x, y = blob.raw[8:8 + length], blob.raw[8 + length:8 + 2 * length]
        return _P256_SPKI_PREFIX + b"\x04" + x + y

    @staticmethod
    def _sign(key, message: bytes) -> bytes:
        lib = _lib()
        digest = hashlib.sha256(message).digest()
        signature = ctypes.create_string_buffer(64)
        size = wintypes.DWORD()
        status = lib.NCryptSignHash(key, None, digest, len(digest), signature, len(signature), ctypes.byref(size), 0)
        if status != 0:
            raise ComputerKeyError(f"Windows could not sign in with this computer's key (0x{_code(status):08X}).")
        return _der_signature(signature.raw[:size.value])


def _der_signature(raw: bytes) -> bytes:
    # Windows gives r and s side by side; the backend (SHA256withECDSA)
    # reads the standard DER form: SEQUENCE { INTEGER r, INTEGER s }.
    half = len(raw) // 2

    def integer(value: bytes) -> bytes:
        value = value.lstrip(b"\x00") or b"\x00"
        if value[0] & 0x80:
            value = b"\x00" + value
        return b"\x02" + bytes([len(value)]) + value

    body = integer(raw[:half]) + integer(raw[half:])
    return b"\x30" + bytes([len(body)]) + body
