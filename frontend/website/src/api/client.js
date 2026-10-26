const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
// Every BFF call goes under /svc (see vite.config.js) so its path never
// collides with a page route of the same name (/orders/:orderId,
// /products/:productId, /shipping, /help, /marketing all used to share a
// path prefix with the proxy meant for their own XHR calls, so a full
// page refresh on any of them got hijacked by the proxy instead of
// reaching the SPA).
const SVC_PREFIX = '/svc'

async function request(path, options = {}) {
  // Spreading ...options after `headers` was silently overwriting the
  // merged headers object on every call that also passed its own headers
  // (checkout, getOrder, the AI chat calls) - Content-Type: application/
  // json never actually made it onto the request, the browser fell back
  // to text/plain, and FastAPI then choked trying to build the Pydantic
  // model from the request body ("Input should be a valid dictionary or
  // object" - the body was valid JSON, but treated as an opaque string,
  // not JSON, because of the wrong content type header).
  const { headers, ...rest } = options
  let response
  try {
    response = await fetch(`${BASE_URL}${SVC_PREFIX}${path}`, {
      ...rest,
      headers: { 'Content-Type': 'application/json', ...(headers || {}) },
    })
  } catch (cause) {
    // No answer at all: the visitor is offline, or the site can't be
    // reached. apiErrorMessage turns this into words.
    const err = new Error('NETWORK')
    err.network = true
    err.cause = cause
    throw err
  }
  if (!response.ok) {
    const detail = await response.text().catch(() => '')
    const err = new Error(`API ${response.status}: ${detail || response.statusText}`)
    err.status = response.status
    throw err
  }
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

function authHeader(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// "the ai should work even if you dont have an account" - userId is
// undefined for an anonymous visitor, so the header is left off entirely
// rather than sent as the literal string "undefined".
function actorHeaders(token, userId) {
  const headers = { ...authHeader(token) }
  if (userId != null) headers['X-Actor-User-Id'] = String(userId)
  return headers
}

export const api = {
  listProducts: () => request('/products'),
  popularProducts: (limit = 10) => request(`/products/popular?limit=${limit}`),
  getProduct: (id) => request(`/products/${id}`),
  listCampaigns: () => request('/marketing/campaigns'),
  listShippingAreas: () => request('/shipping/areas'),
  checkout: (payload, token) =>
    request('/orders/checkout', { method: 'POST', body: JSON.stringify(payload), headers: authHeader(token) }),
  getOrder: (id, token) => request(`/orders/${id}`, { headers: authHeader(token) }),
  // Track my order - no sign-in: the ticket and the last 3 digits of the
  // account's phone number.
  trackOrder: (ticket, phone) =>
    request(`/orders/track?ticket=${encodeURIComponent(ticket)}&phone=${encodeURIComponent(phone)}`),
  myOrders: (token, limit = 50) => request(`/orders/mine?limit=${limit}`, { headers: authHeader(token) }),
  helpContact: () => request('/help/contact'),
  helpFaqs: () => request('/help/faqs'),
  helpNotices: () => request('/help/notices'),
  sendSupportEmail: (payload, token) =>
    request('/help/email', { method: 'POST', body: JSON.stringify(payload), headers: authHeader(token) }),
  chat: (question) => request('/help/chat', { method: 'POST', body: JSON.stringify({ question }) }),
  heavyOrderContact: () => request('/heavy-orders/contact'),
  heavyOrderShowcase: () => request('/heavy-orders/showcase'),
  register: (payload) => request('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload) => request('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  authProviders: () => request('/auth/providers'),
  googleSignIn: (payload) => request('/auth/google', { method: 'POST', body: JSON.stringify(payload) }),
  me: (token) => request('/auth/me', { headers: authHeader(token) }),
  // The signed-in person's own profile (migration 039): name and email (the
  // current password only when the email changes), password - answers a new
  // {token}, every other sign-in ends - and the photo, a square JPEG.
  updateProfile: (fields, token) =>
    request('/auth/me/profile', { method: 'PUT', body: JSON.stringify(fields), headers: authHeader(token) }),
  changePassword: (currentPassword, newPassword, token) =>
    request('/auth/me/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
      headers: authHeader(token),
    }),
  uploadPhoto: (jpegBlob, token) =>
    request('/auth/me/photo', {
      method: 'POST',
      body: jpegBlob,
      headers: { ...authHeader(token), 'Content-Type': 'image/jpeg' },
    }),
  removePhoto: (token) => request('/auth/me/photo', { method: 'DELETE', headers: authHeader(token) }),
  // The signed-in account's colour theme: 'forest', 'dark' or 'light'.
  saveTheme: (theme, token) =>
    request('/auth/me/theme', { method: 'PUT', body: JSON.stringify({ theme }), headers: authHeader(token) }),
  // The SMS code that confirms a phone number: at sign-up (no token; a
  // correct code returns {phoneProof}), or for the signed-in account.
  sendPhoneCode: (phoneNo, token = null) =>
    request(token ? '/auth/me/phone/send-code' : '/auth/phone/send-code', {
      method: 'POST',
      body: JSON.stringify({ phone_no: phoneNo }),
      headers: authHeader(token),
    }),
  verifyPhoneCode: (phoneNo, code, token = null) =>
    request(token ? '/auth/me/phone/verify' : '/auth/phone/verify', {
      method: 'POST',
      body: JSON.stringify({ phone_no: phoneNo, code }),
      headers: authHeader(token),
    }),
  aiChatHistory: (token, userId) => request('/ai/chat/history', { headers: actorHeaders(token, userId) }),
  // history: a guest's own recent turns ({ role, content }) - signed-in
  // conversations are already stored server-side, so they send none.
  // context: which page it was asked from - { page: 'product', product_id },
  // { page: 'cart', cart: [{ product_id, quantity }] } or { page: 'search', search }.
  sendAiChatMessage: (message, token, userId, history = [], context = null) =>
    request('/ai/chat', {
      method: 'POST',
      body: JSON.stringify(context ? { message, history, context } : { message, history }),
      headers: actorHeaders(token, userId),
    }),
  // { available } - whether the chat button may invite questions right now.
  aiChatStatus: () => request('/ai/chat/status'),
  // Product ratings - see ReviewService on the backend.
  reviewSummaries: () => request('/reviews/summary'),
  productReviews: (productId) => request(`/reviews/product/${productId}`),
  myReviews: (token) => request('/reviews/mine', { headers: authHeader(token) }),
  // review: { rating, comment, make_public, consent_version }
  saveReview: (productId, review, token) =>
    request(`/reviews/product/${productId}`, {
      method: 'PUT',
      body: JSON.stringify(review),
      headers: authHeader(token),
    }),
  deleteReview: (productId, token) =>
    request(`/reviews/product/${productId}`, { method: 'DELETE', headers: authHeader(token) }),
}

// "in the event of an error e.g the server is offline ... the user in the
// website or mobile never gets the whole details but what is necessary and
// presentable" (18 October 2026). Every error a customer sees goes through
// here: the server's own words when it wrote them for people ("API 403:
// {"detail":"You can rate a product once ..."}" -> just the message),
// otherwise a calm sentence by kind - never a status code, raw reply or
// browser error text.
const OFFLINE = "You seem to be offline. Check your internet connection and try again."
const UNREACHABLE = "We can't reach Dateroem right now. Please try again in a few minutes."
const PROBLEM = 'Something went wrong on our side. Please try again in a moment.'
const BY_STATUS = {
  400: 'Please check the details and try again.',
  401: 'Please sign in again.',
  403: "You don't have access to this.",
  404: "We couldn't find that - it may have been removed.",
  413: 'That is too large to send.',
  422: 'Please check the details and try again.',
  429: 'Too many tries. Please wait a moment and try again.',
  502: UNREACHABLE,
  503: UNREACHABLE,
  504: 'Dateroem is taking too long to answer. Please try again in a moment.',
}

export function apiErrorMessage(err) {
  if (err?.network) return typeof navigator !== 'undefined' && navigator.onLine === false ? OFFLINE : UNREACHABLE
  const match = /^API (\d+): (.*)$/s.exec(err?.message || '')
  if (!match) return PROBLEM
  const status = Number(match[1])
  try {
    const body = JSON.parse(match[2])
    // The site's servers only ever put a readable sentence in "detail"
    // (their own technical details stay in their logs).
    if (typeof body.detail === 'string' && body.detail.trim()) return body.detail
    if (Array.isArray(body.detail)) return BY_STATUS[422]
  } catch {
    // Not JSON (an error page from the router): never shown as it is.
  }
  return BY_STATUS[status] || (status >= 500 ? PROBLEM : BY_STATUS[400])
}
