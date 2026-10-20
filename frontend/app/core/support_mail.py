"""The Help page's "Send us an email" form.

"the customer can do all the email typing here just like what they would
have done in their outlook or gmail web" - the website sends the message to
the support address through the business Gmail, with the customer's name as
the sender and their address as Reply-To, so pressing Reply in Gmail writes
back to them. They get a confirmation in their own inbox. Nothing is stored
on our side, and the message text is never written to the logs.

Abuse the form must not allow:
- emailing anyone but us: the recipient is always one of our own desks -
  settings.support_email, or settings.orders_email for the Bulk Orders
  page (_desk below; anything else falls back to support);
- spam to strangers through the confirmation: a copy of the message goes
  only to a signed-in customer's own account email; anyone else gets a
  short note without their text, at most twice a day per address;
- header tricks: every header is set here, the customer's address is
  checked strictly, and the email library refuses line breaks in headers;
- flooding: limits per address and overall (Gmail sends ~500 a day), a
  hidden field only bots fill in, and a minimum time to write a message.
Behind the ngrok tunnel every visitor arrives from the same address, so the
limits are per email address and overall, not per visitor.
"""
import logging
import re
import smtplib
import ssl
import time
from collections import defaultdict, deque
from datetime import datetime
from email.message import EmailMessage
from email.utils import formataddr, make_msgid

from app.core.config import settings

log = logging.getLogger(__name__)


def _desk(name: str | None) -> dict:
    """Which of our inboxes a message goes to. "remember we have another
    +orders for the bulk orders inquiries" - the Bulk Orders page writes to
    the orders desk, everything else to support."""
    if name == "orders":
        return {"key": "orders", "email": settings.orders_email, "name": settings.orders_name, "page": "Bulk Orders"}
    return {"key": "support", "email": settings.support_email, "name": settings.support_name, "page": "Help"}

EMAIL = re.compile(r"^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
MAX_MESSAGE = 4000
MIN_MESSAGE = 10
MIN_WRITING_MS = 3000

HOUR, DAY = 3600, 86400
# (window seconds, how many) - per sender address, and for the whole site.
PER_ADDRESS = ((HOUR, 3), (DAY, 10))
OVERALL = ((HOUR, 30), (DAY, 120))
GUEST_NOTES_PER_DAY = 2

_sent: dict[str, deque] = defaultdict(deque)


class FormProblem(Exception):
    """Something the sender should fix (400) or wait for (429)."""

    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.message = message
        self.status = status


def configured() -> bool:
    return bool(settings.smtp_user and settings.smtp_app_password)


def _one_line(text: str, limit: int) -> str:
    return " ".join((text or "").split())[:limit]


def _allowed(key: str, rules) -> bool:
    now = time.monotonic()
    times = _sent[key]
    while times and now - times[0] > DAY:
        times.popleft()
    return all(sum(1 for t in times if now - t <= window) < most for window, most in rules)


def _record(*keys: str):
    now = time.monotonic()
    for key in keys:
        _sent[key].append(now)


def prepare(form: dict, account: dict | None) -> dict:
    """Checks the form and works out who it is from. account is the signed-in
    customer (from the backend, never from the form) or None."""
    name = _one_line(form.get("name", ""), 80)
    email = (form.get("email") or "").strip()
    signed_in_email = (account or {}).get("email") or ""
    if account:
        name = _one_line(account.get("name") or name, 80)
        if signed_in_email:
            email = signed_in_email
    if not name:
        raise FormProblem("Please enter your name.")
    if len(email) > 254 or not EMAIL.match(email):
        raise FormProblem("Please enter a valid email address, so we can reply to you.")
    message = (form.get("message") or "").strip()
    if len(message) < MIN_MESSAGE:
        raise FormProblem("Please write a little more, so we can help.")
    if len(message) > MAX_MESSAGE:
        raise FormProblem(f"Please keep the message under {MAX_MESSAGE} characters.")
    phone = re.sub(r"[^0-9+ ]", "", form.get("phone") or "")[:20].strip()
    subject = _one_line(form.get("subject", ""), 120) or "Help request"
    return {
        "name": name,
        "email": email,
        "phone": phone,
        "subject": subject,
        "message": message,
        "account": account,
        "desk": _desk(form.get("desk")),
        # A full copy of the message only to the signed-in customer's own address.
        "verified": bool(account and signed_in_email and email.lower() == signed_in_email.lower()),
    }


def looks_automated(form: dict) -> bool:
    return bool((form.get("website") or "").strip()) or int(form.get("elapsedMs") or 0) < MIN_WRITING_MS


def check_limits(mail: dict):
    address = "to:" + mail["email"].lower()
    if not _allowed("overall", OVERALL):
        raise FormProblem("We are receiving a lot of messages right now - please try again later, or use your own email app (link below).", 429)
    if not _allowed(address, PER_ADDRESS):
        raise FormProblem("You have sent several messages already - we will reply to them soon. Please try again later.", 429)


def _to_us(mail: dict) -> EmailMessage:
    account = mail["account"]
    who = (f"Signed in as {account.get('userName')} - this is the email on their account"
           if mail["verified"] else
           f"Signed in as {account.get('userName')} - email typed in the form" if account else
           "Not signed in - the email address was typed in the form and is not verified")
    msg = EmailMessage()
    msg["From"] = formataddr((f"{mail['name']} (via website)", settings.smtp_user))
    msg["To"] = mail["desk"]["email"]
    msg["Reply-To"] = formataddr((mail["name"], mail["email"]))
    msg["Subject"] = f"Website: {mail['subject']}"
    msg["Message-ID"] = make_msgid(domain="dateroem.website")
    msg.set_content(
        f"{mail['message']}\n\n"
        f"--\n"
        f"{mail['name']} <{mail['email']}>\n"
        f"Phone: {mail['phone'] or 'not given'}\n"
        f"{who}\n"
        f"Sent from the {mail['desk']['page']} page of the Dateroem website on {datetime.now():%d %b %Y, %H:%M}.\n"
        f"Press Reply to answer the customer.\n"
    )
    return msg


def _to_customer(mail: dict) -> EmailMessage | None:
    msg = EmailMessage()
    desk = mail["desk"]
    msg["From"] = formataddr((desk["name"], settings.smtp_user))
    msg["To"] = mail["email"]
    msg["Reply-To"] = desk["email"]
    msg["Message-ID"] = make_msgid(domain="dateroem.website")
    if mail["verified"]:
        first = mail["name"].split(" ")[0]
        msg["Subject"] = f"We received your message: {mail['subject']}"
        msg.set_content(
            f"Hello {first},\n\n"
            f"Thank you for contacting Dateroem. We have received your message and will reply to this email address.\n\n"
            f"Your message - {mail['subject']}:\n\n{mail['message']}\n\n"
            f"{desk['name']}\n"
        )
        return msg
    # Anyone not signed in: no copy of their text, so the form can't be used
    # to send someone else a message that seems to come from us.
    note_key = "note:" + mail["email"].lower()
    if not _allowed(note_key, ((DAY, GUEST_NOTES_PER_DAY),)):
        return None
    _record(note_key)
    msg["Subject"] = "We received your message"
    msg.set_content(
        "Hello,\n\n"
        "A message was sent to Dateroem from our website with this email address for the reply. "
        "We will reply to this address.\n\n"
        "If you did not send it, you can ignore this email.\n\n"
        f"{desk['name']}\n"
    )
    return msg


def send(mail: dict) -> bool:
    """Sends the message to us and the confirmation to the customer (one
    connection). Returns whether a confirmation went out. Raises on failure."""
    to_us = _to_us(mail)
    to_customer = _to_customer(mail)
    context = ssl.create_default_context()
    with smtplib.SMTP_SSL(settings.smtp_host, settings.smtp_port, context=context, timeout=20) as smtp:
        smtp.login(settings.smtp_user, settings.smtp_app_password.replace(" ", ""))
        smtp.send_message(to_us)
        _record("overall", "to:" + mail["email"].lower())
        if to_customer is not None:
            try:
                smtp.send_message(to_customer)
            except smtplib.SMTPException as exc:
                # Ours arrived; a failed confirmation shouldn't make the
                # customer send it again.
                log.warning("support email confirmation not sent: %s", type(exc).__name__)
                return False
    return to_customer is not None
