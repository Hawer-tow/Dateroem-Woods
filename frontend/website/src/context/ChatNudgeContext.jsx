import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client.js'
import { trackEvent } from '../core/analytics.js'
import { useCookieConsent } from './CookieConsentContext.jsx'
import { useHelpWidget } from './HelpWidgetContext.jsx'

// "Helpful-moment nudges" - the chat button stays still until a visitor
// looks unsure, then sends out one growth ring (and, once a visit, a short
// label beside it):
//   - about 40 s on one product without adding it to the cart
//   - coming back to a product already looked at this visit
//   - lingering in the cart before checkout
//   - a search that finds nothing
// Kept from being a nuisance by the rules in fire(): at most 2 nudges and 1
// label a visit, nothing in a visit's first 15 s, 2 minutes between nudges,
// only once the visitor has been still for a moment (never mid-scroll or
// mid-typing), never while the chat is open or during payment, none at all
// once they've opened the chat, no label while the cookie banner is up or
// where it would cover one of the page's own buttons, a closed label means
// no more labels - and only when the assistant can actually answer right
// now. All of it lives in this tab only.

// Every timing in one place, to tune once real visits show how people browse.
export const NUDGE_TIMING = {
  quietStartMs: 15_000,
  productDwellMs: 40_000,
  revisitDelayMs: 8_000,
  cartDwellMs: 30_000,
  emptySearchDelayMs: 1_500,
  stillMs: 4_000,
  gapMs: 120_000,
  maxPulses: 2,
  maxLabels: 1,
  labelVisibleMs: 7_000,
  statusTtlMs: 60_000,
  // A chat opened this soon after a nudge is about what the nudge was about.
  fromNudgeMs: 120_000,
}
const T = NUDGE_TIMING
const SESSION_KEY = 'dateroem_chat_nudges'

function freshSession() {
  return { startedAt: Date.now(), pulses: 0, labels: 0, lastAt: 0, chatOpened: false, labelsOff: false, seen: {} }
}

function loadSession() {
  try {
    const stored = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null')
    return stored && typeof stored === 'object' ? { ...freshSession(), ...stored } : freshSession()
  } catch {
    return freshSession()
  }
}

function saveSession(session) {
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // Private mode etc. - the rules still hold for this page view.
  }
}

function shorten(text, max) {
  const clean = String(text || '').trim()
  return clean.length > max ? `${clean.slice(0, max - 1).trimEnd()}...` : clean
}

function cartLabel(items) {
  const count = items.reduce((sum, item) => sum + (item.qty || 0), 0)
  return `your cart (${count} item${count === 1 ? '' : 's'})`
}

const ChatNudgeContext = createContext(null)

export function ChatNudgeProvider({ children }) {
  const { isOpen } = useHelpWidget()
  const { hasDecided } = useCookieConsent()
  const [page, setPage] = useState(null)
  const [emptySearch, setEmptySearch] = useState('')
  // { id (when it fired), trigger, text, showLabel, context }
  const [nudge, setNudge] = useState(null)

  const session = useRef(loadSession())
  const lastActivity = useRef(0)
  const status = useRef({ at: 0, available: false })
  const timers = useRef(new Set())
  const generation = useRef(0)
  const openedFrom = useRef(null)
  const pageRef = useRef(page)
  const nudgeRef = useRef(nudge)
  const isOpenRef = useRef(isOpen)
  const consentRef = useRef(hasDecided)
  pageRef.current = page
  nudgeRef.current = nudge
  isOpenRef.current = isOpen
  consentRef.current = hasDecided

  const later = useCallback((ms, fn) => {
    const id = window.setTimeout(() => {
      timers.current.delete(id)
      fn()
    }, ms)
    timers.current.add(id)
  }, [])

  const clearTimers = useCallback(() => {
    timers.current.forEach((id) => window.clearTimeout(id))
    timers.current.clear()
  }, [])

  // Any scroll, key, tap or click counts as "busy" for a few seconds.
  useEffect(() => {
    const mark = () => {
      lastActivity.current = Date.now()
    }
    const events = ['scroll', 'keydown', 'pointerdown', 'touchstart', 'wheel']
    events.forEach((name) => window.addEventListener(name, mark, { passive: true }))
    return () => events.forEach((name) => window.removeEventListener(name, mark))
  }, [])

  // Only invite questions the assistant can answer right now.
  const assistantAvailable = useCallback(async () => {
    const now = Date.now()
    if (now - status.current.at < T.statusTtlMs) return status.current.available
    try {
      const res = await api.aiChatStatus()
      status.current = { at: now, available: Boolean(res?.available) }
    } catch {
      status.current = { at: now, available: false }
    }
    return status.current.available
  }, [])

  const fire = useCallback(
    async (trigger, text, context, stillValid, attempt = 0) => {
      const s = session.current
      const now = Date.now()
      if (s.chatOpened || isOpenRef.current || s.pulses >= T.maxPulses) return
      if (now - s.lastAt < T.gapMs) return
      if (trigger !== 'search' && now - s.startedAt < T.quietStartMs) return
      if (document.visibilityState === 'hidden') return
      // Wait for the visitor to be still - a nudge never lands mid-scroll
      // or mid-typing. Someone busy for a long while is left alone.
      const quietFor = now - lastActivity.current
      if (quietFor < T.stillMs) {
        if (attempt < 8) later(T.stillMs - quietFor + 250, () => fire(trigger, text, context, stillValid, attempt + 1))
        return
      }
      if (!(await assistantAvailable())) return
      if (!stillValid() || session.current.chatOpened || isOpenRef.current) return
      s.pulses += 1
      s.lastAt = Date.now()
      saveSession(s)
      // The label waits for the cookie banner to be answered (it would sit
      // on top of it on a phone). It's only offered here - the chat button
      // first checks it wouldn't cover one of the page's own buttons
      // (settleLabel), so it counts once it's really been seen.
      const offerLabel = s.labels < T.maxLabels && !s.labelsOff && consentRef.current
      setNudge({ id: Date.now(), trigger, text, context, showLabel: offerLabel, settled: !offerLabel })
      if (!offerLabel) trackEvent('chat_nudge_shown', { trigger, with_label: false })
    },
    [assistantAvailable, later]
  )

  // The chat button's answer to an offered label: shown (somewhere clear of
  // the page's buttons) or not (no clear spot - the ring alone says it).
  const settleLabel = useCallback(
    (id, shown) => {
      const current = nudgeRef.current
      if (!current || current.id !== id || current.settled) return
      if (shown) {
        const s = session.current
        s.labels += 1
        saveSession(s)
        later(T.labelVisibleMs, () =>
          setNudge((latest) => (latest && latest.id === id ? { ...latest, showLabel: false } : latest))
        )
      }
      nudgeRef.current = { ...current, showLabel: shown, settled: true }
      setNudge(nudgeRef.current)
      trackEvent('chat_nudge_shown', { trigger: current.trigger, with_label: shown })
    },
    [later]
  )

  // What the page says it is (see useChatPage) decides which moment to wait for.
  const pageKey = page ? JSON.stringify(page) : ''
  useEffect(() => {
    clearTimers()
    generation.current += 1
    // A label about the last page (or the cart before its change) is done.
    setNudge((shown) => (shown?.showLabel ? { ...shown, showLabel: false } : shown))
    const current = pageRef.current
    if (!current) return
    const myGeneration = generation.current
    const stillValid = () => generation.current === myGeneration
    const s = session.current

    if (current.kind === 'product' && current.productId) {
      // Seen in an earlier visit to its page this session = coming back.
      const firstVisit = s.seen[current.productId]
      if (!firstVisit) {
        const ids = Object.keys(s.seen)
        if (ids.length >= 50) delete s.seen[ids[0]]
        s.seen[current.productId] = current.visitId
        saveSession(s)
      }
      if (current.addedToCart) return
      const name = shorten(current.name, 28)
      const context = { page: 'product', product_id: current.productId, label: current.name }
      if (firstVisit && firstVisit !== current.visitId) {
        later(T.revisitDelayMs, () => fire('revisit', `Still deciding on ${name}? Ask us anything.`, context, stillValid))
      } else {
        later(T.productDwellMs, () => fire('product', `Questions about ${name}?`, context, stillValid))
      }
    } else if (current.kind === 'cart' && current.items?.length && !current.paying) {
      const context = {
        page: 'cart',
        cart: current.items.map((item) => ({ product_id: item.productId, quantity: item.qty })),
        label: cartLabel(current.items),
      }
      later(T.cartDwellMs, () => fire('cart', 'Questions about delivery or payment?', context, stillValid))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageKey])

  // A search that found nothing - a moment after typing stops.
  useEffect(() => {
    if (!emptySearch) return undefined
    const query = emptySearch
    const id = window.setTimeout(() => {
      fire(
        'search',
        `Can't find "${shorten(query, 24)}"? Ask us.`,
        { page: 'search', search: query.slice(0, 80), label: `"${shorten(query, 40)}"` },
        () => true
      )
    }, T.emptySearchDelayMs)
    return () => window.clearTimeout(id)
  }, [emptySearch, fire])

  // Opening the chat: remember what a recent nudge was about (for the
  // chat's context), and no more nudges this visit.
  useEffect(() => {
    if (!isOpen) return
    const recent = nudgeRef.current
    if (recent && Date.now() - recent.id < T.fromNudgeMs) {
      openedFrom.current = { context: recent.context, at: Date.now() }
      trackEvent('chat_opened_after_nudge', { trigger: recent.trigger })
    }
    const s = session.current
    if (!s.chatOpened) {
      s.chatOpened = true
      saveSession(s)
    }
    clearTimers()
    setNudge((current) => (current ? { ...current, showLabel: false } : current))
  }, [isOpen, clearTimers])

  useEffect(() => clearTimers, [clearTimers])

  const dismissNudge = useCallback(() => {
    const s = session.current
    s.labelsOff = true
    saveSession(s)
    setNudge((current) => {
      if (current) trackEvent('chat_nudge_dismissed', { trigger: current.trigger })
      return current ? { ...current, showLabel: false } : current
    })
  }, [])

  // What the chat should know it's being asked from: a search the visitor
  // was nudged about, else the product or cart on screen. label is for the
  // chat's own "Asking about ..." line, not sent.
  const chatContext = useCallback(() => {
    // (The chat asks as it opens - possibly before this provider's own
    // effect has noted the nudge, so a just-fired nudge counts too.)
    const now = Date.now()
    const from = openedFrom.current
    const recent = nudgeRef.current
    const nudged =
      from && now - from.at < T.fromNudgeMs
        ? from.context
        : recent && now - recent.id < T.fromNudgeMs
          ? recent.context
          : null
    if (nudged?.page === 'search') return nudged
    const current = pageRef.current
    if (current?.kind === 'product' && current.productId) {
      return { page: 'product', product_id: current.productId, label: current.name }
    }
    if (current?.kind === 'cart' && current.items?.length) {
      return {
        page: 'cart',
        cart: current.items.map((item) => ({ product_id: item.productId, quantity: item.qty })),
        label: cartLabel(current.items),
      }
    }
    return null
  }, [])

  const value = useMemo(
    () => ({ nudge, settleLabel, dismissNudge, chatContext, setPage, reportEmptySearch: setEmptySearch }),
    [nudge, settleLabel, dismissNudge, chatContext]
  )
  return <ChatNudgeContext.Provider value={value}>{children}</ChatNudgeContext.Provider>
}

export function useChatNudge() {
  const ctx = useContext(ChatNudgeContext)
  if (!ctx) throw new Error('useChatNudge must be used within a ChatNudgeProvider')
  return ctx
}

// A page says what it is while it's on screen:
//   { kind: 'product', productId, name, addedToCart }
//   { kind: 'cart', items: [{ productId, qty }], paying }
// or null for nothing to watch for.
export function useChatPage(context) {
  const ctx = useContext(ChatNudgeContext)
  const visitId = useRef(Math.random().toString(36).slice(2))
  const key = context ? JSON.stringify(context) : ''
  const setPage = ctx?.setPage
  useEffect(() => {
    if (!setPage) return
    setPage(key ? { ...JSON.parse(key), visitId: visitId.current } : null)
  }, [key, setPage])
  useEffect(
    () => () => {
      setPage?.(null)
    },
    [setPage]
  )
}
