import { api } from '../api/client.js'

// "can my website have both the manual logging in and the google assisted
// one" - Google's own sign-in button (Google Identity Services). Its script
// loads only when the sign-in window opens, never on ordinary pages.
const SCRIPT_URL = 'https://accounts.google.com/gsi/client'

let scriptLoading = null
let providers = null
let initializedFor = null
let onCredential = null

// {googleClientId} from the backend - null there means Google sign-in isn't
// set up, and only username/password is offered.
export function signInProviders() {
  if (!providers) {
    providers = api.authProviders().catch(() => {
      providers = null
      return { googleClientId: null }
    })
  }
  return providers
}

function loadGoogleIdentity() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id)
  if (!scriptLoading) {
    scriptLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SCRIPT_URL
      script.async = true
      script.defer = true
      script.onload = () =>
        window.google?.accounts?.id ? resolve(window.google.accounts.id) : reject(new Error('Google sign-in unavailable'))
      script.onerror = () => {
        scriptLoading = null
        script.remove()
        reject(new Error('Google sign-in unavailable'))
      }
      document.head.appendChild(script)
    })
  }
  return scriptLoading
}

// Draws Google's "Continue with Google" button into element; onToken gets
// the sign-in token once the person picks their account. Google keeps one
// handler per page, so whichever sign-in window is open takes it over.
export async function mountGoogleButton(element, clientId, onToken) {
  const identity = await loadGoogleIdentity()
  onCredential = onToken
  if (initializedFor !== clientId) {
    identity.initialize({
      client_id: clientId,
      callback: (response) => onCredential?.(response.credential),
      ux_mode: 'popup',
      context: 'signin',
      itp_support: true,
    })
    initializedFor = clientId
  }
  const width = Math.round(Math.min(400, Math.max(220, element.clientWidth || 320)))
  identity.renderButton(element, {
    type: 'standard',
    theme: 'outline',
    size: 'large',
    shape: 'pill',
    text: 'continue_with',
    logo_alignment: 'center',
    width,
  })
}

export function releaseGoogleButton(onToken) {
  if (onCredential === onToken) onCredential = null
}
