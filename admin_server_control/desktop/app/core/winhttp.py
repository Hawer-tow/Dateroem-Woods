"""A requests transport that sends https:// requests with WinHTTP.

On the office network, Python's own encrypted connections to the public
tunnel are cut off during the handshake, while Windows' own - the one
browsers and curl use - go straight through (the same fault that made pip
downloads fail on this computer). WinHTTP is Windows' HTTP service, with
its built-in TLS and certificate checks, reached through pywin32 (which
voice input already needs). Mounted by core/http.py.
"""
import gzip
import zlib

import requests
from requests.adapters import BaseAdapter
from requests.structures import CaseInsensitiveDict
from requests.utils import get_encoding_from_headers

# WinHTTP error codes (as COM HRESULTs) worth telling apart.
_TIMEOUT = 0x80072EE2
_CANCELLED_OR_FAILED = {
    0x80072EE7: "the server name could not be found",
    0x80072EFD: "could not connect to the server",
    0x80072EFE: "the connection was closed",
    0x80072F8F: "a secure connection could not be made",
    0x80072F7D: "a secure connection could not be made",
}


class WinHttpAdapter(BaseAdapter):
    """Sends the request with WinHTTP and returns a normal requests.Response
    (or raises requests' own exceptions)."""

    def send(self, request, stream=False, timeout=None, verify=True, cert=None, proxies=None):
        import pythoncom
        import pywintypes
        import win32com.client

        pythoncom.CoInitialize()  # per thread; harmless when already done
        seconds = timeout if isinstance(timeout, (int, float)) else (timeout[-1] if timeout else 20)
        milliseconds = int(seconds * 1000)
        try:
            winhttp = win32com.client.Dispatch("WinHttp.WinHttpRequest.5.1")
            winhttp.SetTimeouts(milliseconds, milliseconds, milliseconds, milliseconds)
            winhttp.Open(request.method, request.url, False)
            for name, value in request.headers.items():
                winhttp.SetRequestHeader(name, value)
            body = request.body
            if body is None:
                winhttp.Send()
            else:
                winhttp.Send(body.encode("utf-8") if isinstance(body, str) else bytes(body))
            status = winhttp.Status
            reason = winhttp.StatusText
            raw_headers = winhttp.GetAllResponseHeaders()
            content = bytes(winhttp.ResponseBody or b"")
        except pywintypes.com_error as exc:
            code = (exc.excepinfo[5] if exc.excepinfo else exc.hresult) & 0xFFFFFFFF
            if code == _TIMEOUT:
                raise requests.Timeout(f"No answer from {request.url} in {seconds:g} seconds", request=request) from exc
            reason_text = _CANCELLED_OR_FAILED.get(code, f"Windows network error 0x{code:08X}")
            raise requests.ConnectionError(f"{reason_text} ({request.url})", request=request) from exc

        headers = CaseInsensitiveDict()
        for line in raw_headers.split("\r\n"):
            if ":" in line:
                name, value = line.split(":", 1)
                headers[name.strip()] = value.strip()
        # The servers compress larger answers; WinHTTP hands them over as sent.
        encoding = headers.get("Content-Encoding", "").lower()
        if encoding == "gzip":
            content = gzip.decompress(content)
        elif encoding == "deflate":
            content = zlib.decompress(content)
        if encoding in ("gzip", "deflate"):
            del headers["Content-Encoding"]

        response = requests.Response()
        response.status_code = status
        response.reason = reason
        response.headers = headers
        response._content = content
        response.encoding = get_encoding_from_headers(headers)
        response.url = request.url
        response.request = request
        return response

    def close(self):
        pass
