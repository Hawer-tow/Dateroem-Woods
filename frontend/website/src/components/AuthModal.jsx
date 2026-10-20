import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { api, apiErrorMessage } from '../api/client.js'
import { isValidKenyanPhone, KENYAN_PHONE_HINT, samePhone } from '../core/phone.js'
import { isValidEmail, suggestEmail } from '../core/email.js'
import { mountGoogleButton, releaseGoogleButton, signInProviders } from '../core/googleSignIn.js'
import AuthStory from './AuthStory.jsx'
import PhoneCodeStep from './PhoneCodeStep.jsx'
import WoodWordmark from './WoodWordmark.jsx'

// The backend's answer when a sign-up arrives without a confirmed number.
const NEEDS_CODE = /confirm your phone number|phone confirmation has expired/i

const EMAIL_HINT = "That email address doesn't look right - for example name@gmail.com."

// "i dont want it to fill the username i think that is something one makes"
// - always typed by the person, never made up for them. The same rule as
// the backend: 3 to 30 characters, no spaces, no "@" (so it can't be
// mistaken for an email address, which can also be used to sign in).
const USER_NAME_RULE = /^[^\s@]{3,30}$/
const USER_NAME_HINT = 'Choose a username of 3 to 30 characters, with no spaces and no @.'

// Google's "G", for the stand-in button shown until Google sign-in is set up.
function GoogleMark() {
  return (
    <svg viewBox="0 0 48 48" width="18" height="18" aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  )
}

// "when one clicks log in or create an account i want to have the
// animation in the about page half of the screen and the other half you
// have sign in" - a screen of its own now, not a small pop-up: the form on
// the left, the About page's film (acquisition through delivery) on the
// right. Opened from the account icon and from the cart's checkout prompt,
// exactly as the pop-up was; closing goes back to the same page.
export default function AuthModal({ onClose, message }) {
  const { login, register, googleSignIn } = useAuth()
  const [mode, setMode] = useState('login')
  const [fields, setFields] = useState({ userName: '', password: '', name: '', email: '', phoneNo: '' })
  const [emailTouched, setEmailTouched] = useState(false)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  // "can my website have both the manual logging in and the google assisted
  // one" - Google's button above the usual form, when it's set up.
  const googleButtonRef = useRef(null)
  // 'checking' | 'ready' | 'unset' (no Google client set up yet) | 'offline'
  const [google, setGoogle] = useState('checking')
  const [googleError, setGoogleError] = useState(null)
  const [googleBusy, setGoogleBusy] = useState(false)
  // Someone new who came in through Google: their phone number is still
  // needed ("a user must at least have a number") - {credential, name, email}.
  const [googleNew, setGoogleNew] = useState(null)
  const [googlePhone, setGooglePhone] = useState('')
  const [googleUserName, setGoogleUserName] = useState('')
  // "push a code to the phone number they placed so that its verified" -
  // while SMS codes are on (phoneCodeOn), a new account's number is
  // confirmed first: codeStep is 'register' or 'google' while the code is
  // being typed, and proof ({phone, value}) is kept so a sign-up that fails
  // for another reason (a username taken) doesn't need a second code.
  const [phoneCodeOn, setPhoneCodeOn] = useState(false)
  const [phoneCodeFor, setPhoneCodeFor] = useState(null)
  const [codeStep, setCodeStep] = useState(null)
  const [proof, setProof] = useState(null)

  // A full screen: the page behind stays put, and Escape closes it.
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = (event) => {
      if (event.key === 'Escape') closeRef.current(false)
    }
    window.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = previous
      window.removeEventListener('keydown', onKey)
    }
  }, [])

  // Google calls back into whatever this render's handler is.
  const onGoogleToken = useRef(null)
  onGoogleToken.current = async (credential) => {
    setGoogleError(null)
    setGoogleBusy(true)
    try {
      const needs = await googleSignIn(credential)
      if (needs) {
        setError(null)
        setGooglePhone('')
        setGoogleUserName('')
        if (needs.needsPhoneCode) setPhoneCodeOn(true)
        setGoogleNew({ credential, name: needs.name, email: needs.email })
        return
      }
      onClose(true)
    } catch (err) {
      setGoogleError(apiErrorMessage(err))
    } finally {
      setGoogleBusy(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    const handler = (credential) => onGoogleToken.current(credential)
    signInProviders()
      .then(({ googleClientId, phoneCode, phoneCodeFor: networks }) => {
        if (cancelled) return undefined
        setPhoneCodeOn(Boolean(phoneCode))
        setPhoneCodeFor(Array.isArray(networks) ? networks : null)
        if (!googleClientId || !googleButtonRef.current) {
          setGoogle('unset')
          return undefined
        }
        return mountGoogleButton(googleButtonRef.current, googleClientId, handler).then(() => {
          if (!cancelled) setGoogle('ready')
        })
      })
      // Google unreachable (offline, blocked): the usual form still works.
      .catch(() => {
        if (!cancelled) setGoogle('offline')
      })
    return () => {
      cancelled = true
      releaseGoogleButton(handler)
    }
  }, [])

  function update(key) {
    return (event) => setFields((prev) => ({ ...prev, [key]: event.target.value }))
  }

  function switchMode(next) {
    setMode(next)
    setError(null)
  }

  const email = fields.email.trim()
  const emailWrong = mode === 'register' && emailTouched && email !== '' && !isValidEmail(email)
  const emailSuggestion = mode === 'register' && email !== '' ? suggestEmail(email) : null

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    // "make the phone number a must" - every new account needs a number
    // to identify the customer by; caught here before the server is asked.
    if (mode === 'register' && !USER_NAME_RULE.test(fields.userName.trim())) {
      setError(USER_NAME_HINT)
      return
    }
    if (mode === 'register' && !isValidKenyanPhone(fields.phoneNo)) {
      setError(KENYAN_PHONE_HINT)
      return
    }
    if (mode === 'register' && email !== '' && !isValidEmail(email)) {
      setEmailTouched(true)
      setError(EMAIL_HINT)
      return
    }
    if (mode === 'register' && phoneCodeOn && !proofFor(fields.phoneNo)) {
      setCodeStep('register')
      return
    }
    if (mode === 'register') {
      await createAccount(proofFor(fields.phoneNo))
      return
    }
    setSubmitting(true)
    try {
      await login(fields.userName.trim(), fields.password)
      onClose(true)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  // The kept proof, when it's for this same number.
  function proofFor(phone) {
    return proof && samePhone(proof.phone, phone) ? proof.value : null
  }

  async function createAccount(phoneProof) {
    setError(null)
    setSubmitting(true)
    try {
      await register({
        user_name: fields.userName.trim(),
        name: fields.name,
        password: fields.password,
        email: email || null,
        phone_no: fields.phoneNo.trim(),
        phone_proof: phoneProof,
      })
      onClose(true)
    } catch (err) {
      const message = apiErrorMessage(err)
      if (NEEDS_CODE.test(message)) {
        // SMS codes were switched on after this screen opened, or the proof ran out.
        setProof(null)
        setPhoneCodeOn(true)
        setCodeStep('register')
      } else {
        setError(message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function finishGoogle(event) {
    event.preventDefault()
    setError(null)
    if (!USER_NAME_RULE.test(googleUserName.trim())) {
      setError(USER_NAME_HINT)
      return
    }
    if (!isValidKenyanPhone(googlePhone)) {
      setError(KENYAN_PHONE_HINT)
      return
    }
    if (phoneCodeOn && !proofFor(googlePhone)) {
      setCodeStep('google')
      return
    }
    await createGoogleAccount(proofFor(googlePhone))
  }

  async function createGoogleAccount(phoneProof) {
    setError(null)
    setSubmitting(true)
    try {
      await googleSignIn(googleNew.credential, googlePhone.trim(), googleUserName.trim(), phoneProof)
      onClose(true)
    } catch (err) {
      const message = apiErrorMessage(err)
      if (NEEDS_CODE.test(message)) {
        setProof(null)
        setPhoneCodeOn(true)
        setCodeStep('google')
      } else {
        setError(message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  // A correct code: keep the proof and carry on creating the account. A
  // number that isn't asked for a code (codeNeeded false) carries on without.
  function phoneConfirmed(res) {
    const step = codeStep
    const phone = step === 'google' ? googlePhone.trim() : fields.phoneNo.trim()
    const phoneProof = res?.phoneProof ?? null
    if (phoneProof) setProof({ phone, value: phoneProof })
    setCodeStep(null)
    if (step === 'google') createGoogleAccount(phoneProof)
    else createAccount(phoneProof)
  }

  // "Airtel numbers get a text ..." when codes go to some networks only.
  const codeNote = phoneCodeFor?.length
    ? ` ${phoneCodeFor.join(' and ')} numbers get a text with a code to confirm them.`
    : " We'll text it a code to confirm it's yours."

  function leaveGoogleFinish() {
    setGoogleNew(null)
    setError(null)
  }

  const firstName = (googleNew?.name || '').trim().split(/\s+/)[0]

  // Portaled to <body>: the navbar's backdrop-filter would otherwise make
  // a "fixed, full-screen" layer size itself to the navbar's thin strip.
  return createPortal(
    <div className="auth-screen" role="dialog" aria-modal="true" aria-labelledby="auth-screen-title">
      <section className="auth-screen__form-side">
        <div className="auth-screen__form">
          <WoodWordmark className="auth-screen__brand" />

          {codeStep ? (
            <div className="auth-finish">
              <h1 id="auth-screen-title" className="auth-screen__title">
                Confirm your phone number
              </h1>
              <p className="auth-screen__lead">One last step before we create your account.</p>
              <PhoneCodeStep
                phone={codeStep === 'google' ? googlePhone.trim() : fields.phoneNo.trim()}
                send={(phone) => api.sendPhoneCode(phone)}
                verify={(phone, code) => api.verifyPhoneCode(phone, code)}
                onVerified={phoneConfirmed}
                onBack={() => setCodeStep(null)}
              />
            </div>
          ) : googleNew ? (
            <div className="auth-finish">
              <h1 id="auth-screen-title" className="auth-screen__title">
                {firstName ? `Welcome, ${firstName}!` : 'Welcome!'}
              </h1>
              <p className="auth-screen__lead">
                Two last things: a username of your choice, and your phone number - it identifies your account and lets
                us reach you about deliveries.
              </p>
              {googleNew.email && <p className="auth-finish__email">Signing up as {googleNew.email}</p>}
              <form onSubmit={finishGoogle} className="modal__form">
                <label>
                  Choose a username
                  <input
                    type="text"
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    value={googleUserName}
                    onChange={(event) => setGoogleUserName(event.target.value)}
                    required
                    autoFocus
                  />
                  <span className="modal__field-note">3 to 30 characters, no spaces or @.</span>
                </label>
                <label>
                  Phone number
                  <input
                    type="tel"
                    inputMode="tel"
                    autoComplete="tel"
                    placeholder="07XXXXXXXX"
                    value={googlePhone}
                    onChange={(event) => setGooglePhone(event.target.value)}
                    required
                  />
                  {phoneCodeOn && <span className="modal__field-note">{codeNote.trim()}</span>}
                </label>
                {error && <p className="state-message state-message--error">{error}</p>}
                <button className="btn btn--primary" type="submit" disabled={submitting}>
                  {submitting ? 'Please wait...' : 'Create my account'}
                </button>
                <button type="button" className="btn--link auth-finish__back" onClick={leaveGoogleFinish} disabled={submitting}>
                  Back
                </button>
              </form>
            </div>
          ) : (
            <>
              <h1 id="auth-screen-title" className="auth-screen__title">
                {mode === 'login' ? 'Welcome back' : 'Create your account'}
              </h1>
              <p className="auth-screen__lead">
                {mode === 'login'
                  ? 'Sign in to order timber and follow your deliveries.'
                  : 'It takes a minute. Your phone number is how we reach you about deliveries.'}
              </p>
              {message && <p className="auth-screen__notice">{message}</p>}
              <div className="modal__tabs" role="tablist" aria-label="Sign in or create an account">
                <button
                  type="button"
                  role="tab"
                  aria-selected={mode === 'login'}
                  className={mode === 'login' ? 'modal__tab modal__tab--active' : 'modal__tab'}
                  onClick={() => switchMode('login')}
                >
                  Sign in
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={mode === 'register'}
                  className={mode === 'register' ? 'modal__tab modal__tab--active' : 'modal__tab'}
                  onClick={() => switchMode('register')}
                >
                  Create account
                </button>
              </div>
            </>
          )}

          {/* Stays on the page (hidden) during the phone step, so going Back
              finds Google's button still there. "i need to see a login with
              google area" - until the business's Google sign-in is set up
              (GOOGLE_CLIENT_ID), its place shows a button that isn't live
              yet; once it is, Google's own button appears right here. */}
          <div className={`auth-google auth-google--${google}`} hidden={Boolean(googleNew || codeStep)}>
            <div ref={googleButtonRef} className="auth-google__button" hidden={google === 'unset' || google === 'offline'} />
            {(google === 'unset' || google === 'offline') && (
              <>
                <button type="button" className="auth-google__placeholder" disabled aria-describedby="auth-google-note">
                  <GoogleMark />
                  Continue with Google
                </button>
                <p id="auth-google-note" className="auth-google__note">
                  {google === 'unset'
                    ? 'Google sign-in is coming soon - for now, use your username or email below.'
                    : "Google can't be reached right now - use your username or email below."}
                </p>
              </>
            )}
            {googleBusy && <p className="auth-google__note">Signing you in...</p>}
            {googleError && <p className="state-message state-message--error">{googleError}</p>}
            <p className="auth-divider">
              <span>or</span>
            </p>
          </div>

          {!googleNew && !codeStep && (
            <form onSubmit={handleSubmit} className="modal__form">
              <label>
                {mode === 'login' ? 'Username or email' : 'Choose a username'}
                <input
                  type="text"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={fields.userName}
                  onChange={update('userName')}
                  required
                />
                {mode === 'register' && <span className="modal__field-note">3 to 30 characters, no spaces or @.</span>}
              </label>
              {mode === 'register' && (
                <label>
                  Full name
                  <input type="text" autoComplete="name" value={fields.name} onChange={update('name')} required />
                </label>
              )}
              <label>
                Password
                <input
                  type="password"
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  value={fields.password}
                  onChange={update('password')}
                  required
                  minLength={6}
                />
              </label>
              {mode === 'register' && (
                <>
                  <label>
                    Phone number
                    <input
                      type="tel"
                      inputMode="tel"
                      autoComplete="tel"
                      placeholder="07XXXXXXXX"
                      value={fields.phoneNo}
                      onChange={update('phoneNo')}
                      required
                    />
                    <span className="modal__field-note">
                      Identifies your account and lets us reach you about deliveries.
                      {phoneCodeOn && codeNote}
                    </span>
                  </label>
                  <div className="auth-screen__email">
                    <label>
                      Email (optional)
                      <input
                        type="email"
                        inputMode="email"
                        autoComplete="email"
                        autoCapitalize="none"
                        spellCheck={false}
                        value={fields.email}
                        onChange={update('email')}
                        onBlur={() => setEmailTouched(true)}
                        aria-invalid={emailWrong}
                      />
                    </label>
                    {emailWrong && <p className="auth-screen__field-error">{EMAIL_HINT}</p>}
                    {emailSuggestion && (
                      <p className="modal__field-note">
                        Did you mean{' '}
                        <button
                          type="button"
                          className="btn--link auth-screen__suggestion"
                          onClick={() => setFields((prev) => ({ ...prev, email: emailSuggestion }))}
                        >
                          {emailSuggestion}
                        </button>
                        ?
                      </p>
                    )}
                  </div>
                </>
              )}
              {error && <p className="state-message state-message--error">{error}</p>}
              <button className="btn btn--primary" type="submit" disabled={submitting}>
                {submitting ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}
              </button>
            </form>
          )}
        </div>
      </section>

      <aside className="auth-screen__story-side" aria-label="The Dateroem story, from the forest to your door">
        <AuthStory />
      </aside>

      <button type="button" className="auth-screen__close" onClick={() => onClose(false)} aria-label="Close">
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>
    </div>,
    document.body
  )
}
