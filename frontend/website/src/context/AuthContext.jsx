import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../api/client.js'

const AuthContext = createContext(null)
const STORAGE_KEY = 'dateroem_auth'

function loadStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function store(session) {
  if (session) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } else {
    localStorage.removeItem(STORAGE_KEY)
  }
}

// The parts of a sign-in or /auth/me answer the site keeps. photoUrl and
// mustChangePassword (migration 039): the profile photo, and an admin's
// password reset still waiting for a password of the person's own.
function sessionFrom(token, res, previous = null) {
  return {
    token,
    userId: res.userId,
    userName: res.userName,
    name: res.name,
    photoUrl: res.photoUrl !== undefined ? res.photoUrl : previous?.photoUrl ?? null,
    mustChangePassword: Boolean(res.mustChangePassword),
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadStored)
  const [checking, setChecking] = useState(true)
  // The colour theme saved with the account, as the server last said
  // (sign-in or the check on load); ThemeContext applies it.
  const [accountTheme, setAccountTheme] = useState(null)

  useEffect(() => {
    // Validate any stored token against the backend on load, rather than
    // trusting localStorage blindly - a stale/forged token should not look
    // like a valid session.
    const stored = loadStored()
    if (!stored?.token) {
      setChecking(false)
      return
    }
    api
      .me(stored.token)
      .then((res) => {
        const session = sessionFrom(stored.token, res, stored)
        setUser(session)
        store(session)
        setAccountTheme(res.theme ?? null)
      })
      .catch(() => {
        store(null)
        setUser(null)
      })
      .finally(() => setChecking(false))
  }, [])

  const persist = useCallback((session, theme = null) => {
    setUser(session)
    setAccountTheme(theme)
    store(session)
  }, [])

  // After signing in: the photo isn't in the sign-in answer, so it is fetched.
  const signedIn = useCallback(
    (res) => {
      persist(sessionFrom(res.token, res), res.theme)
      api
        .me(res.token)
        .then((me) => setUser((current) => (current?.token === res.token ? sessionFrom(res.token, me, current) : current)))
        .catch(() => {})
    },
    [persist]
  )

  // Changes to the signed-in person's own details (profile page, new token
  // after a password change), kept for the next visit too.
  const updateUser = useCallback((changes) => {
    setUser((current) => {
      if (!current) return current
      const next = { ...current, ...changes }
      store(next)
      return next
    })
  }, [])

  const value = useMemo(
    () => ({
      user,
      checking,
      accountTheme,
      isAuthenticated: Boolean(user),
      updateUser,
      login: async (userName, password) => {
        signedIn(await api.login({ user_name: userName, password }))
      },
      register: async (fields) => {
        signedIn(await api.register(fields))
      },
      // "Continue with Google": signs in and returns null, or - for someone
      // new, whose phone number and own username are still needed - returns
      // {needsPhone, needsUserName, name, email}; call again with the same
      // credential, their phone and the username they chose (and, while SMS
      // codes are on, the phoneProof from confirming that phone).
      googleSignIn: async (credential, phoneNo = null, userName = null, phoneProof = null) => {
        const res = await api.googleSignIn({ credential, phone_no: phoneNo, user_name: userName, phone_proof: phoneProof })
        if (res?.needsPhone) return res
        signedIn(res)
        return null
      },
      logout: () => persist(null),
    }),
    [user, checking, accountTheme, persist, signedIn, updateUser]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
