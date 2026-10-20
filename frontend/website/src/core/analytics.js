// "if google analytics is free wire that instead" - GA4 is free for a
// site this size. Loaded only once, and only ever called after the
// visitor has actually granted the "analytics" cookie category - never
// on page load by default, so no tracking cookie is set before consent.
let loaded = false

export function loadGoogleAnalytics() {
  const measurementId = import.meta.env.VITE_GA_MEASUREMENT_ID
  if (!measurementId || loaded) return
  loaded = true

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`
  document.head.appendChild(script)

  window.dataLayer = window.dataLayer || []
  function gtag() {
    window.dataLayer.push(arguments)
  }
  window.gtag = gtag
  gtag('js', new Date())
  gtag('config', measurementId)
}

// One event (e.g. the chat button's nudges - shown / opened / dismissed),
// recorded only if analytics was consented to and has loaded; otherwise
// nothing at all happens.
export function trackEvent(name, params = {}) {
  if (loaded && typeof window.gtag === 'function') window.gtag('event', name, params)
}
