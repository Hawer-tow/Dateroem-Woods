import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { api, apiErrorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { isValidEmail, suggestEmail } from '../core/email.js'
import Icon from './Icon.jsx'

// The fields that can be typed in (the first empty one gets the cursor
// when the window opens with a mouse; the bot trap and phone never do).
const TYPED_FIELDS = 'input[type=text]:not([readonly]):not(.help-email__trap), input[type=email]:not([readonly]), textarea'

// "is there a way i can type there and provide my email there in the website
// and i sent it from there ... just like what they would have done in their
// outlook or gmail web" - the message goes to the support inbox through the
// website server (frontend/app/core/support_mail.py); pressing Reply there
// writes back to this customer. A signed-in customer's name and email come
// from their account (and the server takes them from the account too).
//
// "i want that email thingy to appear once some one clicks on the email" -
// it is a window over the page, opened by the email button on the Help and
// Bulk Orders pages. The page keeps it mounted while closed, so closing it
// by accident keeps what was typed. contact: the page's /contact answer
// ({ name, email }); subject: what the subject starts as; desk: which of our
// inboxes it goes to - 'support', or 'orders' for the Bulk Orders page.
export default function EmailDialog({ open, onClose, contact, subject = '', desk = 'support' }) {
  const { user } = useAuth()
  const [account, setAccount] = useState(null)
  const [fields, setFields] = useState({ name: '', email: '', phone: '', subject, message: '', website: '' })
  const [sending, setSending] = useState(false)
  const [error, setError] = useState(null)
  const [sent, setSent] = useState(null)
  const openedAt = useRef(null)
  const dialogRef = useRef(null)
  const toName = contact.name || 'Dateroem support'

  useEffect(() => {
    if (!user?.token) {
      setAccount(null)
      return
    }
    api.me(user.token).then(setAccount).catch(() => setAccount(null))
  }, [user?.token])

  // "when a user is logged in do prefill sections like phone number" - the
  // phone on their account, if the field is still empty (it stays editable).
  useEffect(() => {
    if (account?.phoneNo) setFields((current) => (current.phone ? current : { ...current, phone: account.phoneNo }))
  }, [account?.phoneNo])

  // Open: the page behind stays put, the cursor goes to the first empty
  // field (only with a mouse - on a phone the keyboard would cover the
  // window straight away), and closing gives the focus back to the button.
  useEffect(() => {
    if (!open) return undefined
    if (openedAt.current === null) openedAt.current = Date.now()
    const returnTo = document.activeElement
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const box = dialogRef.current
    const empty = [...box.querySelectorAll(TYPED_FIELDS)].find((field) => !field.value)
    if (empty && window.matchMedia?.('(pointer: fine)').matches) empty.focus()
    else box.focus({ preventScroll: true })
    return () => {
      document.body.style.overflow = previous
      returnTo?.focus?.()
    }
  }, [open])

  function writeAnother() {
    setFields((current) => ({ ...current, subject, message: '' }))
    setSent(null)
    setError(null)
    openedAt.current = Date.now()
  }

  const closeRef = useRef(null)
  closeRef.current = () => {
    if (sending) return
    if (sent) writeAnother()
    onClose()
  }
  useEffect(() => {
    if (!open) return undefined
    const onKey = (event) => {
      if (event.key === 'Escape') closeRef.current()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  if (!open) return null

  const close = () => closeRef.current()
  const lockedName = account?.name || ''
  const lockedEmail = account?.email || ''
  const email = lockedEmail || fields.email.trim()
  const suggestion = !lockedEmail && email && isValidEmail(email) ? suggestEmail(email) : null
  const update = (key) => (event) => setFields((current) => ({ ...current, [key]: event.target.value }))
  // The visitor's own email app, carrying over whatever is typed so far.
  const ownApp = `mailto:${contact.email}?subject=${encodeURIComponent(fields.subject || subject || 'Help request')}`
    + (fields.message ? `&body=${encodeURIComponent(fields.message)}` : '')

  async function send(event) {
    event.preventDefault()
    setError(null)
    if (!lockedEmail && !isValidEmail(email)) {
      setError('Please enter a valid email address, so we can reply to you.')
      return
    }
    setSending(true)
    try {
      const result = await api.sendSupportEmail(
        { ...fields, name: lockedName || fields.name, email, desk, elapsedMs: Date.now() - openedAt.current },
        user?.token,
      )
      setSent(result)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setSending(false)
    }
  }

  // Portaled to <body>, like the other windows: the navbar's
  // backdrop-filter would otherwise shrink a fixed layer to its strip.
  return createPortal(
    <div className="modal-overlay" onClick={close}>
      <div
        ref={dialogRef}
        className="modal email-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="help-email-title"
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
      >
        <button type="button" className="modal__close" onClick={close} disabled={sending} aria-label="Close">
          &times;
        </button>
        {sent ? (
          <section className="help-email help-email--sent" aria-live="polite">
            <h2 id="help-email-title">
              <Icon name="check" className="icon--inline" /> Sent
            </h2>
            <p>
              Your email is on its way to {toName}. We will reply to <strong>{sent.replyTo}</strong>.
              {sent.confirmation && (sent.fullCopy
                ? ' A copy of your message is in your inbox.'
                : ' A short confirmation is on its way to that address.')}
            </p>
            <div className="help-email__actions">
              <button type="button" className="btn btn--primary" onClick={close}>Done</button>
              <button type="button" className="btn btn--secondary" onClick={writeAnother}>Write another</button>
            </div>
          </section>
        ) : (
          <section className="help-email">
            <h2 id="help-email-title">Send us an email</h2>
            <p className="help-email__intro">Write to us here - no email app needed.</p>
            <form className="help-email__form" onSubmit={send}>
              <p className="help-email__to">
                <span>To</span> {toName}
              </p>
              <div className="help-email__row">
                <label>
                  Your name
                  <input type="text" autoComplete="name" value={lockedName || fields.name} onChange={update('name')}
                    readOnly={Boolean(lockedName)} required maxLength={80} />
                </label>
                <label>
                  Your email
                  <input type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false}
                    value={lockedEmail || fields.email} onChange={update('email')} readOnly={Boolean(lockedEmail)}
                    required maxLength={254} />
                  {lockedEmail && <span className="help-email__note">From your account - we reply here.</span>}
                  {suggestion && (
                    <span className="help-email__note">
                      Did you mean{' '}
                      <button type="button" className="btn--link help-email__suggestion"
                        onClick={() => setFields((current) => ({ ...current, email: suggestion }))}>
                        {suggestion}
                      </button>?
                    </span>
                  )}
                </label>
              </div>
              <div className="help-email__row">
                <label>
                  Phone (optional)
                  <input type="tel" inputMode="tel" autoComplete="tel" placeholder="07XXXXXXXX" value={fields.phone}
                    onChange={update('phone')} maxLength={20} />
                </label>
                <label>
                  Subject
                  <input type="text" value={fields.subject} onChange={update('subject')} maxLength={120}
                    placeholder="e.g. Question about my order" />
                </label>
              </div>
              <label>
                Message
                <textarea rows={6} value={fields.message} onChange={update('message')} required minLength={10}
                  maxLength={4000} placeholder="How can we help?" />
                <span className="help-email__note">{fields.message.length} / 4000</span>
              </label>
              {/* Only bots fill this in; people never see it. */}
              <input className="help-email__trap" type="text" name="website" tabIndex={-1} autoComplete="off"
                aria-hidden="true" value={fields.website} onChange={update('website')} />
              {error && <p className="state-message state-message--error" role="alert">{error}</p>}
              <button type="submit" className="btn btn--primary help-email__send" disabled={sending}>
                <Icon name="mail" className="icon--inline" />
                {sending ? 'Sending...' : 'Send'}
              </button>
              <p className="help-email__note">We use your name, email and phone only to reply to this message.</p>
            </form>
            {/* "still leave an option to go to an external site" - and the
                address to copy, for a computer with no email app. */}
            <p className="email-modal__own-app">
              Prefer your own email app? <a href={ownApp}>Open it</a> or write to <span>{contact.email}</span>
            </p>
          </section>
        )}
      </div>
    </div>,
    document.body,
  )
}
