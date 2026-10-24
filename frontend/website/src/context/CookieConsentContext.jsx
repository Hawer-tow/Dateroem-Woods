import { createContext, useContext, useEffect, useState } from 'react'

const STORAGE_KEY = 'dateroem_cookie_consent'

// "i would also want to add cookies to the site but also this should ask
// permission from the user not implement it automatically so we do not
// violate privacy laws of a person." Nothing that isn't strictly required
// for the page to function writes a cookie until the matching category
// here is granted - analytics (usage tracking), marketing (ad/retargeting
// tracking). Remembering the cart used to be a third, "functional"
// category, but it's strictly necessary for a feature the visitor
// actively used (adding something to their own cart) - the same exemption
// the auth token already had - so it's no longer gated on consent at all
// (see CartContext) and isn't one of these categories any more. The
// consent choice itself lives in localStorage, not a cookie - recording
// what someone decided is the one piece of bookkeeping every cookie-law
// regime treats as exempt from needing its own consent.
const DEFAULT_CONSENT = { analytics: false, marketing: false }

const CookieConsentContext = createContext(null)

function loadStoredConsent() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return { ...DEFAULT_CONSENT, ...parsed }
  } catch {
    return null
  }
}

export function CookieConsentProvider({ children }) {
  const [consent, setConsentState] = useState(() => loadStoredConsent() ?? DEFAULT_CONSENT)
  const [hasDecided, setHasDecided] = useState(() => loadStoredConsent() !== null)

  useEffect(() => {
    if (hasDecided) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(consent))
    }
  }, [consent, hasDecided])

  const value = {
    consent,
    hasDecided,
    hasConsent: (category) => Boolean(consent[category]),
    acceptAll: () => {
      setConsentState({ analytics: true, marketing: true })
      setHasDecided(true)
    },
    rejectAll: () => {
      setConsentState(DEFAULT_CONSENT)
      setHasDecided(true)
    },
    savePreferences: (next) => {
      setConsentState({ ...DEFAULT_CONSENT, ...next })
      setHasDecided(true)
    },
    // Cookie settings in the footer: the banner again, with the choices as
    // they stand (the Privacy page says they can be changed any time).
    reopen: () => setHasDecided(false),
  }

  return <CookieConsentContext.Provider value={value}>{children}</CookieConsentContext.Provider>
}

export function useCookieConsent() {
  const ctx = useContext(CookieConsentContext)
  if (!ctx) throw new Error('useCookieConsent must be used within a CookieConsentProvider')
  return ctx
}
