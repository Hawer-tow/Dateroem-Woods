import { useAuth } from '../context/AuthContext.jsx'
import PasswordFields from './PasswordFields.jsx'

// After an admin resets a password (migration 039), the temporary one works
// for one thing only: choosing a password of the person's own. Until then
// this window covers the site - it can't be closed, only signed out of.
export default function PasswordChangeGate() {
  const { user, logout } = useAuth()
  if (!user?.mustChangePassword) return null
  return (
    <div className="modal-overlay">
      <div className="modal password-gate" role="dialog" aria-modal="true" aria-labelledby="password-gate-title">
        <h2 id="password-gate-title">Choose a new password</h2>
        <p className="password-gate__text">
          Your password was reset by our team. Enter the temporary password you were given, then a new one of your own.
        </p>
        <PasswordFields currentLabel="Temporary password" submitLabel="Save my new password" />
        <button type="button" className="btn btn--text password-gate__signout" onClick={logout}>
          Sign out
        </button>
      </div>
    </div>
  )
}
