import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from './AuthContext.jsx'

// "is it possible to add more themes like the dark and light theme and what
// we have will be default ... a user can change the theme and it will be
// remembered and when that user for that account goes into app or the
// mobile the theme is still the one they choose last" (20 October 2026).
// The colours themselves are in styles/index.css (html[data-theme]).
export const THEMES = [
  { id: 'forest', label: 'Forest' },
  { id: 'dark', label: 'Dark' },
  { id: 'light', label: 'Light' },
]
const THEME_IDS = THEMES.map((t) => t.id)
export const DEFAULT_THEME = 'forest'

// This browser's last choice (also read by the small script in index.html,
// so the page is drawn in it from the first frame). Kept only here - it is
// a display preference, not something sent anywhere for a visitor.
const STORAGE_KEY = 'dateroem_theme'
// Set while a signed-in choice hasn't reached the server yet (offline); the
// next sign-in check sends it instead of taking the account's older one.
const PENDING_KEY = 'dateroem_theme_pending'

// The browser's address-bar colour, per theme (the page colour).
const BAR_COLOUR = { forest: '#0f241b', dark: '#121417', light: '#f4f1ea' }

function stored(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function store(key, value) {
  try {
    if (value == null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // Private windows can refuse storage; the theme still applies now.
  }
}

function valid(theme) {
  return THEME_IDS.includes(theme) ? theme : null
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BAR_COLOUR[theme])
}

const ThemeContext = createContext(null)

export function ThemeProvider({ children }) {
  const { user, accountTheme } = useAuth()
  const [theme, setThemeState] = useState(() => valid(stored(STORAGE_KEY)) || DEFAULT_THEME)

  useEffect(() => {
    applyTheme(theme)
    store(STORAGE_KEY, theme)
  }, [theme])

  // Signed in (or the session checked on load): the account's theme wins,
  // so a choice made on the phone or the desktop shows here too - unless a
  // choice made here never reached the server, which is sent now instead.
  useEffect(() => {
    if (!user?.token || !valid(accountTheme)) return
    const pending = valid(stored(PENDING_KEY))
    if (pending) {
      api.saveTheme(pending, user.token).then(() => store(PENDING_KEY, null)).catch(() => {})
      setThemeState(pending)
    } else {
      setThemeState(accountTheme)
    }
  }, [user?.token, accountTheme])

  const setTheme = useCallback(
    (next) => {
      if (!valid(next)) return
      setThemeState(next)
      if (!user?.token) return
      store(PENDING_KEY, next)
      api
        .saveTheme(next, user.token)
        .then(() => store(PENDING_KEY, null))
        .catch(() => {})
    },
    [user?.token]
  )

  const value = useMemo(() => ({ theme, setTheme }), [theme, setTheme])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider')
  return ctx
}
