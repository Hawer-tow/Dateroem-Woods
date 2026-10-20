import { useState } from 'react'
import { useCookieConsent } from '../context/CookieConsentContext.jsx'

// Remembering your cart is strictly necessary for the site to work the
// way you're already using it (see CartContext) - it's on unconditionally,
// same as the auth token, so it's no longer one of the categories offered
// here. Only the two genuinely optional categories remain.
const CATEGORY_LABELS = {
  analytics: { title: 'Analytics', description: 'Helps us see how the site is used, anonymously.' },
  marketing: { title: 'Marketing', description: 'Used for ad personalization and retargeting.' },
}

export default function CookieConsentBanner() {
  const { hasDecided, consent, acceptAll, rejectAll, savePreferences } = useCookieConsent()
  const [customizing, setCustomizing] = useState(false)
  const [draft, setDraft] = useState(consent)

  if (hasDecided) return null

  return (
    <div className="cookie-banner" role="dialog" aria-label="Cookie preferences">
      <div className="cookie-banner__body">
        <p className="cookie-banner__text">
          We use cookies to remember your cart and, if you allow it, to understand site usage and personalize ads.
          Nothing beyond what the site needs to function is set unless you say yes.
        </p>

        {customizing && (
          <div className="cookie-banner__options">
            {Object.entries(CATEGORY_LABELS).map(([key, { title, description }]) => (
              <label key={key} className="cookie-banner__option">
                <input
                  type="checkbox"
                  checked={draft[key]}
                  onChange={(e) => setDraft((prev) => ({ ...prev, [key]: e.target.checked }))}
                />
                <span>
                  <strong>{title}</strong>
                  <br />
                  <span className="cookie-banner__option-desc">{description}</span>
                </span>
              </label>
            ))}
          </div>
        )}

        <div className="cookie-banner__actions">
          {customizing ? (
            <button type="button" className="btn btn--primary" onClick={() => savePreferences(draft)}>
              Save preferences
            </button>
          ) : (
            <>
              <button type="button" className="btn btn--primary" onClick={acceptAll}>
                Accept all
              </button>
              <button type="button" className="btn btn--secondary" onClick={rejectAll}>
                Reject all
              </button>
              <button type="button" className="btn btn--text cookie-banner__customize" onClick={() => setCustomizing(true)}>
                Customize
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
