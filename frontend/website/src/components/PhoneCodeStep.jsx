import { useEffect, useRef, useState } from 'react'
import { apiErrorMessage } from '../api/client.js'

// "push a code to the phone number they placed so that its verified" - the
// step that sends a 6-digit code by SMS to `phone` and checks what's typed
// back. `send(phone)` and `verify(phone, code)` are the API calls (sign-up
// or the signed-in account); `onVerified(result)` gets verify's answer.
// Used by the sign-up screen (AuthModal) and at checkout (CartPage).
export default function PhoneCodeStep({ phone, send, verify, onVerified, onBack, backLabel = 'Change number' }) {
  const [sentTo, setSentTo] = useState(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState(null)
  const [sending, setSending] = useState(false)
  const [checking, setChecking] = useState(false)
  const [resendIn, setResendIn] = useState(0)
  const sentOnce = useRef(false)
  const inputRef = useRef(null)

  async function sendCode() {
    setError(null)
    setSending(true)
    try {
      const res = await send(phone)
      // This number isn't asked for a code (codes go to some networks only,
      // or texts are paused): carry straight on.
      if (res?.codeNeeded === false) {
        onVerified(res)
        return
      }
      setSentTo(res?.sentTo || phone)
      setResendIn(res?.resendAfterSeconds ?? 60)
      setCode('')
      inputRef.current?.focus()
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setSending(false)
    }
  }

  // The first code goes as soon as the step opens (once, even in React's
  // development double-run).
  useEffect(() => {
    if (sentOnce.current) return
    sentOnce.current = true
    sendCode()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (resendIn <= 0) return undefined
    const timer = setTimeout(() => setResendIn((left) => left - 1), 1000)
    return () => clearTimeout(timer)
  }, [resendIn])

  async function check(value) {
    if (checking) return
    if (!/^\d{6}$/.test(value)) {
      setError('Enter the 6-digit code from the text message.')
      return
    }
    setError(null)
    setChecking(true)
    try {
      const res = await verify(phone, value)
      onVerified(res)
    } catch (err) {
      setError(apiErrorMessage(err))
      setChecking(false)
      // Ready to type again straight away.
      setCode('')
      setTimeout(() => inputRef.current?.focus(), 0)
    }
  }

  function onCodeChange(event) {
    const value = event.target.value.replace(/\D/g, '').slice(0, 6)
    setCode(value)
    // Six digits typed (or pasted from the SMS): checked straight away.
    if (value.length === 6) check(value)
  }

  return (
    <form
      className="modal__form phone-code"
      onSubmit={(event) => {
        event.preventDefault()
        check(code)
      }}
    >
      <p className="phone-code__sent" aria-live="polite">
        {sending && !sentTo
          ? 'One moment...'
          : sentTo
            ? (
              <>
                We sent a 6-digit code by SMS to <strong>{sentTo}</strong>. It expires in 10 minutes.
              </>
            )
            : "We couldn't send the code yet."}
      </p>
      <label>
        Code from the SMS
        <input
          ref={inputRef}
          className="phone-code__input"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="\d{6}"
          maxLength={6}
          placeholder="------"
          value={code}
          onChange={onCodeChange}
          disabled={checking}
          autoFocus
          aria-describedby="phone-code-note"
        />
      </label>
      {error && <p className="state-message state-message--error">{error}</p>}
      <button className="btn btn--primary" type="submit" disabled={checking || code.length !== 6}>
        {checking ? 'Checking...' : 'Confirm'}
      </button>
      <div className="phone-code__actions">
        <button type="button" className="btn--link" onClick={sendCode} disabled={sending || checking || resendIn > 0}>
          {sending ? 'Sending...' : resendIn > 0 ? `Send a new code (${resendIn}s)` : 'Send a new code'}
        </button>
        {onBack && (
          <button type="button" className="btn--link" onClick={onBack} disabled={checking}>
            {backLabel}
          </button>
        )}
      </div>
      <p id="phone-code-note" className="modal__field-note">
        This confirms the number is yours. We use it only for your account, orders and deliveries. No code after a
        minute? Send a new one, or contact us from the Help page.
      </p>
    </form>
  )
}
