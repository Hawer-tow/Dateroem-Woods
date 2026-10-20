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
        setUser({ token: stored.token, userId: res.userId, userName: res.userName, name: res.name })
        setAccountTheme(res.theme ?? null)
      })
      .catch(() => {
        localStorage.removeItem(STORAGE_KEY)
        setUser(null)
      })
      .finally(() => setChecking(false))
  }, [])

  const persist = useCallback((session, theme = null) => {
    setUser(session)
    setAccountTheme(theme)
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [])

  const value = useMemo(
    () => ({
      user,
      checking,
      accountTheme,
      isAuthenticated: Boolean(user),
      login: async (userName, password) => {
        const res = await api.login({ user_name: userName, password })
        persist({ token: res.token, userId: res.userId, userName: res.userName, name: res.name }, res.theme)
      },
      register: async (fields) => {
        const res = await api.register(fields)
        persist({ token: res.token, userId: res.userId, userName: res.userName, name: res.name }, res.theme)
      },
      // "Continue with Google": signs in and returns null, or - for someone
      // new, whose phone number and own username are still needed - returns
      // {needsPhone, needsUserName, name, email}; call again with the same
      // credential, their phone and the username they chose (and, while SMS
      // codes are on, the phoneProof from confirming that phone).
      googleSignIn: async (credential, phoneNo = null, userName = null, phoneProof = null) => {
        const res = await api.googleSignIn({ credential, phone_no: phoneNo, user_name: userName, phone_proof: phoneProof })
        if (res?.needsPhone) return res
        persist({ token: res.token, userId: res.userId, userName: res.userName, name: res.name }, res.theme)
        return null
      },
      logout: () => persist(null),
    }),
    [user, checking, accountTheme, persist]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
