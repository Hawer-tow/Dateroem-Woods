import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import Avatar from '../components/Avatar.jsx'
import Icon from '../components/Icon.jsx'
import PasswordFields from '../components/PasswordFields.jsx'
import PhoneCodeStep from '../components/PhoneCodeStep.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { isValidKenyanPhone, KENYAN_PHONE_HINT } from '../core/phone.js'
import { maskEmail, maskPhone, squareJpeg } from '../core/profile.js'

// "a profile area ... updating contacts/password" (23 October 2026): the
// signed-in customer's photo, name, email, phone number and password, all
// saved with the account. The email and phone show partly until the eye is
// pressed; a new phone number is confirmed by SMS code where codes are sent.
export default function ProfilePage() {
  const { user, isAuthenticated, updateUser } = useAuth()
  const [me, setMe] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    if (!user?.token) return
    api
      .me(user.token)
      .then((res) => {
        setMe(res)
        updateUser({ name: res.name, photoUrl: res.photoUrl ?? null })
      })
      .catch((err) => setError(apiErrorMessage(err)))
  }, [user?.token, updateUser])

  useEffect(() => {
    document.title = 'My profile - Dateroem Woods'
    load()
  }, [load])

  if (!isAuthenticated) {
    return (
      <div className="profile-page">
        <h1>My profile</h1>
        <p className="profile-page__lead">Sign in with the account icon at the top to see and change your details.</p>
      </div>
    )
  }

  return (
    <div className="profile-page">
      <PhotoCard me={me} onChanged={load} />
      {error && <p className="state-message state-message--error">{error}</p>}
      {me && (
        <div className="profile-page__grid">
          <DetailsCard me={me} onSaved={load} />
          <PhoneCard me={me} onSaved={load} />
          <section className="profile-card" aria-labelledby="profile-password-title">
            <h2 id="profile-password-title" className="profile-card__title">Password</h2>
            {me.hasPassword ? (
              <PasswordFields />
            ) : (
              <p className="profile-card__note">You sign in with Google, so there is no password here to change.</p>
            )}
          </section>
          <section className="profile-card" aria-labelledby="profile-look-title">
            <h2 id="profile-look-title" className="profile-card__title">Look of the site</h2>
            <p className="profile-card__note">
              Choose Forest, Dark or Light from the Theme menu at the top. It is saved with your account, so the site opens in it on
              your other devices too.
            </p>
            <p className="profile-card__note">
              Looking for an order? <Link to="/my-orders">Ordered Goods</Link> lists them all.
            </p>
          </section>
        </div>
      )}
    </div>
  )
}

function PhotoCard({ me, onChanged }) {
  const { user, updateUser } = useAuth()
  const fileRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function choose(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setError(null)
    setBusy(true)
    try {
      const res = await api.uploadPhoto(await squareJpeg(file), user.token)
      updateUser({ photoUrl: res.photoUrl })
      onChanged()
    } catch (err) {
      setError(err?.message?.startsWith('API') || err?.network ? apiErrorMessage(err) : err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    setError(null)
    setBusy(true)
    try {
      await api.removePhoto(user.token)
      updateUser({ photoUrl: null })
      onChanged()
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="profile-hero" aria-label="Your photo">
      <Avatar name={user.name} photoUrl={user.photoUrl} size={112} className="profile-hero__avatar" />
      <div className="profile-hero__text">
        <p className="about-hero__eyebrow">My profile</p>
        <h1 className="profile-hero__name">{me?.name || user.name}</h1>
        <p className="profile-hero__user">@{user.userName}</p>
        <div className="profile-hero__actions">
          <input ref={fileRef} type="file" accept="image/*" hidden onChange={choose} />
          <button type="button" className="btn btn--secondary" onClick={() => fileRef.current?.click()} disabled={busy}>
            {busy ? 'Saving...' : user.photoUrl ? 'Change photo' : 'Add a photo'}
          </button>
          {user.photoUrl && (
            <button type="button" className="btn btn--text" onClick={remove} disabled={busy}>
              Remove photo
            </button>
          )}
        </div>
        {error && <p className="state-message state-message--error">{error}</p>}
      </div>
    </section>
  )
}

// A value shown partly until the eye is pressed.
function Hidden({ value, mask }) {
  const [shown, setShown] = useState(false)
  if (!value) return <span className="profile-card__empty">Not given</span>
  return (
    <span className="profile-hidden">
      <span>{shown ? value : mask(value)}</span>
      <button
        type="button"
        className="profile-hidden__eye"
        onClick={() => setShown((v) => !v)}
        aria-label={shown ? 'Hide' : 'Show'}
        title={shown ? 'Hide' : 'Show'}
      >
        {shown ? 'Hide' : 'Show'}
      </button>
    </span>
  )
}

function DetailsCard({ me, onSaved }) {
  const { user, updateUser } = useAuth()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(me.name || '')
  const [email, setEmail] = useState(me.email || '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const emailChanged = (email.trim().toLowerCase() || null) !== ((me.email || '').toLowerCase() || null)

  async function save(event) {
    event.preventDefault()
    setError(null)
    setSaving(true)
    try {
      const res = await api.updateProfile(
        { name, email: email.trim() || null, currentPassword: emailChanged && me.hasPassword ? password : null },
        user.token
      )
      updateUser({ name: res.name })
      setEditing(false)
      setPassword('')
      onSaved()
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="profile-card" aria-labelledby="profile-details-title">
      <h2 id="profile-details-title" className="profile-card__title">Your details</h2>
      {!editing ? (
        <>
          <dl className="profile-facts">
            <div>
              <dt>Name</dt>
              <dd>{me.name}</dd>
            </div>
            <div>
              <dt>Username</dt>
              <dd>@{me.userName}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>
                <Hidden value={me.email} mask={maskEmail} />
              </dd>
            </div>
          </dl>
          <button
            type="button"
            className="btn btn--secondary"
            onClick={() => {
              setName(me.name || '')
              setEmail(me.email || '')
              setEditing(true)
            }}
          >
            Edit details
          </button>
        </>
      ) : (
        <form className="profile-form" onSubmit={save}>
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
          </label>
          <label>
            Email (optional)
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
          </label>
          {emailChanged && me.hasPassword && (
            <label>
              Your password, to change the email
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
            </label>
          )}
          <p className="profile-form__hint">Your username can't be changed here - ask us if it is wrong.</p>
          {error && <p className="state-message state-message--error">{error}</p>}
          <div className="profile-form__actions">
            <button type="submit" className="btn btn--primary" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
            <button type="button" className="btn btn--text" onClick={() => setEditing(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

function PhoneCard({ me, onSaved }) {
  const { user } = useAuth()
  const [stage, setStage] = useState(null) // null | 'enter' | 'code'
  const [phone, setPhone] = useState('')
  const [error, setError] = useState(null)

  function start(event) {
    event.preventDefault()
    if (!isValidKenyanPhone(phone)) {
      setError(KENYAN_PHONE_HINT)
      return
    }
    setError(null)
    setStage('code')
  }

  return (
    <section className="profile-card" aria-labelledby="profile-phone-title">
      <h2 id="profile-phone-title" className="profile-card__title">Phone number</h2>
      <dl className="profile-facts">
        <div>
          <dt>Number</dt>
          <dd>
            <Hidden value={me.phoneNo} mask={maskPhone} />
            {me.phoneNo && me.phoneVerifiedAt && (
              <span className="profile-badge">
                <Icon name="check" size={12} /> Confirmed
              </span>
            )}
          </dd>
        </div>
      </dl>
      {/* A staff member's number receives payouts, so only an admin changes it. */}
      {stage === null && me.roleName !== 'Customer' && (
        <p className="profile-card__note">Your number is changed by an admin.</p>
      )}
      {stage === null && me.roleName === 'Customer' && (
        <button type="button" className="btn btn--secondary" onClick={() => setStage('enter')}>
          Change number
        </button>
      )}
      {stage === 'enter' && (
        <form className="profile-form" onSubmit={start}>
          <label>
            New phone number
            <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="07XX XXX XXX" required />
          </label>
          <p className="profile-form__hint">We may text a code to the new number to confirm it is yours.</p>
          {error && <p className="state-message state-message--error">{error}</p>}
          <div className="profile-form__actions">
            <button type="submit" className="btn btn--primary">
              Continue
            </button>
            <button type="button" className="btn btn--text" onClick={() => setStage(null)}>
              Cancel
            </button>
          </div>
        </form>
      )}
      {stage === 'code' && (
        <PhoneCodeStep
          phone={phone}
          send={(number) => api.sendPhoneCode(number, user.token)}
          verify={(number, code) => api.verifyPhoneCode(number, code, user.token)}
          onVerified={() => {
            setStage(null)
            setPhone('')
            onSaved()
          }}
          onBack={() => setStage('enter')}
        />
      )}
    </section>
  )
}
