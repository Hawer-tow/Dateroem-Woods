import { useState } from 'react'
import { api, apiErrorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { passwordProblem } from '../core/profile.js'

// Change the password: the current one, the new one twice. On success the
// browser keeps a new sign-in (every other one ends) - used by the profile
// page and by the window that follows an admin's password reset.
export default function PasswordFields({ currentLabel = 'Current password', submitLabel = 'Change password', onDone }) {
  const { user, updateUser } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function submit(event) {
    event.preventDefault()
    const problem = passwordProblem(next, user?.userName)
    if (problem) {
      setError(problem)
      return
    }
    if (next !== again) {
      setError("The two new passwords don't match.")
      return
    }
    setError(null)
    setSaving(true)
    try {
      const res = await api.changePassword(current, next, user.token)
      updateUser({ token: res.token, mustChangePassword: false })
      setCurrent('')
      setNext('')
      setAgain('')
      onDone?.()
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="profile-form" onSubmit={submit}>
      <label>
        {currentLabel}
        <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
      </label>
      <label>
        New password
        <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" required />
      </label>
      <label>
        New password again
        <input type="password" value={again} onChange={(e) => setAgain(e.target.value)} autoComplete="new-password" required />
      </label>
      <p className="profile-form__hint">At least 8 characters, with letters and numbers. Other devices signed in to this account will be signed out.</p>
      {error && <p className="state-message state-message--error">{error}</p>}
      <button type="submit" className="btn btn--primary" disabled={saving}>
        {saving ? 'Saving...' : submitLabel}
      </button>
    </form>
  )
}
