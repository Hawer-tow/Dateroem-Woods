import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { useHelpWidget } from '../context/HelpWidgetContext.jsx'
import { useChatNudge } from '../context/ChatNudgeContext.jsx'
import Icon from './Icon.jsx'
import { FOREST_LOOK } from '../core/look.js'
import Fireflies from './forest/Fireflies.jsx'

// One-tap questions for the page the chat was opened from - the commonest
// things people ask at that moment.
const QUICK_QUESTIONS = {
  product: ['Is it in stock?', 'What sizes and prices are there?', 'Can you deliver it to my area?'],
  cart: ['How much is delivery?', 'Where can I pick it up?', 'How do I pay with M-Pesa?'],
  search: ["What do you have that's close?", 'Can you get it for me?'],
}

// The forest look's welcome offers these when the chat opens away from a
// product, the cart or a search (25 October 2026: "the chat area not being
// so modern").
const GENERAL_QUESTIONS = ['How long does delivery take?', 'How do I pay with M-Pesa?', 'Where are you located?', 'Do you take bulk orders?']

// The page context as the server takes it (the label is only for the
// "Asking about ..." line here).
function contextForServer(context) {
  if (!context) return null
  const { label: _label, ...rest } = context
  return rest
}

// "categorized by weeks if we are in this month ... after that if its
// over 1 month its not compiled in months. a total of 6 month
// conversation are kept apart from that more than that are retired not
// deleted just cannot be seen" - same rule as the mobile app's
// ChatViewModel.dateGroups(): this month's own days folded into weeks
// (most recent first), the 5 months before that each collapsed into one
// bucket, anything older left out of the picker entirely (still in the
// database - AiChatService.history() is untouched - just not offered here).
const MONTHS_KEPT = 6

// "also for the ai when the browser page is refreshed [all is lost]" - a
// signed-in visitor's history already survives a refresh (it's reloaded
// from the server below); a guest's never did, since there's no server-
// side conversation for them at all - this is that same memory for guests,
// kept in this tab/window only (sessionStorage, not localStorage - a
// guest's chat shouldn't leak into a next visit on a shared machine the
// way a signed-in account's own history intentionally does).
const GUEST_CHAT_KEY = 'dateroem_guest_chat'

function loadGuestMessages() {
  try {
    const raw = sessionStorage.getItem(GUEST_CHAT_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function dayKey(createdAt) {
  return createdAt ? createdAt.slice(0, 10) : null
}

function daysInMonth(year, month) {
  return new Date(year, month + 1, 0).getDate()
}

function formatDay(date) {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function computeDateGroups(messages, now = new Date()) {
  const currentMonthIndex = now.getFullYear() * 12 + now.getMonth()
  const currentWeekOfMonth = Math.ceil(now.getDate() / 7)
  const buckets = new Map()

  for (const message of messages) {
    const key = dayKey(message.createdAt)
    if (!key) continue
    const date = new Date(message.createdAt)
    if (Number.isNaN(date.getTime())) continue
    const monthIndex = date.getFullYear() * 12 + date.getMonth()
    const monthsBack = currentMonthIndex - monthIndex
    if (monthsBack < 0 || monthsBack >= MONTHS_KEPT) continue // retired - out of the picker

    let bucketKey
    let label
    let sortKey
    if (monthsBack === 0) {
      const weekOfMonth = Math.ceil(date.getDate() / 7)
      bucketKey = `week:${monthIndex}:${weekOfMonth}`
      if (weekOfMonth === currentWeekOfMonth) {
        label = 'This week'
      } else if (weekOfMonth === currentWeekOfMonth - 1) {
        label = 'Last week'
      } else {
        const start = new Date(date.getFullYear(), date.getMonth(), (weekOfMonth - 1) * 7 + 1)
        const end = new Date(
          date.getFullYear(),
          date.getMonth(),
          Math.min(weekOfMonth * 7, daysInMonth(date.getFullYear(), date.getMonth()))
        )
        label = `${formatDay(start)} - ${formatDay(end)}`
      }
      // Weeks always sort above every month bucket, most recent week first.
      sortKey = 1_000_000 + weekOfMonth
    } else {
      bucketKey = `month:${monthIndex}`
      label = date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
      sortKey = monthIndex
    }

    if (!buckets.has(bucketKey)) buckets.set(bucketKey, { label, count: 0, dateKeys: new Set(), sortKey })
    const bucket = buckets.get(bucketKey)
    bucket.count += 1
    bucket.dateKeys.add(key)
  }

  return Array.from(buckets.values()).sort((a, b) => b.sortKey - a.sortKey)
}

// "like a clock like feature" - a literal clock face (no icon pack
// dependency), same shape as the mobile app's ClockIcon.
function ClockIcon({ size = 20 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
      <line x1="12" y1="12" x2="12" y2="6.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <line x1="12" y1="12" x2="15.5" y2="9.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

// "it should be able to categorize the answers properly" - the assistant
// answers in a fixed plain-text layout (AiChatService's web prompt): a
// heading is a short line ending in a colon, list items start with "- ".
// This turns that into real headings and lists; anything else stays a
// plain paragraph, so an answer that strays from the layout still reads fine.
const HEADING_MAX_WORDS = 5
const LABEL_MAX_WORDS = 3

function parseAnswer(text) {
  const blocks = []
  let list = null
  for (const rawLine of (text || '').split('\n')) {
    const line = rawLine.trim().replace(/^#+\s*/, '')
    if (!line) {
      list = null
      continue
    }
    const item = line.match(/^(?:[-•*]|\d+[.)])\s+(.+)$/)
    if (item) {
      if (!list) {
        list = { type: 'list', items: [] }
        blocks.push(list)
      }
      list.items.push(item[1])
      continue
    }
    list = null
    const plain = line.replace(/\*\*/g, '')
    if (plain.endsWith(':') && plain.split(/\s+/).length <= HEADING_MAX_WORDS) {
      blocks.push({ type: 'heading', text: plain.slice(0, -1) })
    } else {
      blocks.push({ type: 'paragraph', text: line })
    }
  }
  return blocks
}

// **bold** from a model that slips into markdown, and a short "Label:" at
// the start of a line ("Payment: M-Pesa only", "Nakuru: Town Center") shown
// in bold so the eye can scan down the labels.
function renderInline(text) {
  const label = text.match(/^([^:*]{1,28}):\s+(.+)$/)
  if (label && label[1].trim().split(/\s+/).length <= LABEL_MAX_WORDS) {
    return (
      <>
        <strong>{label[1]}:</strong> {renderBold(label[2])}
      </>
    )
  }
  return renderBold(text)
}

function renderBold(text) {
  return text.split(/\*\*(.+?)\*\*/g).map((part, i) => (i % 2 === 1 ? <strong key={i}>{part}</strong> : part))
}

function ChatAnswer({ text }) {
  return (
    <div className="chat-answer">
      {parseAnswer(text).map((block, i) => {
        if (block.type === 'heading') return <p key={i} className="chat-answer__heading">{block.text}</p>
        if (block.type === 'list') {
          return (
            <ul key={i} className="chat-answer__list">
              {block.items.map((item, j) => (
                <li key={j}>{renderInline(item)}</li>
              ))}
            </ul>
          )
        }
        return <p key={i}>{renderInline(block.text)}</p>
      })}
    </div>
  )
}

// A guest's last few turns, sent with each question so follow-ups work -
// there's no server-side conversation for someone without an account.
const GUEST_CONTEXT_TURNS = 8

// Within this many pixels of the bottom counts as "at the latest answer".
const AT_BOTTOM_SLACK = 48

// The backend refuses a signed-in question whose sign-in token has expired
// (it no longer proves which account is asking) - that needs a fresh sign-in,
// not a retry or the keyword bot.
function isExpiredSignIn(err) {
  return /^API 401\b/.test(err?.message || '')
}

// Anything on the page a visitor might be about to tap.
const PAGE_CONTROLS =
  'a[href], button, input, select, textarea, label, summary, [role="button"], [role="link"], [tabindex]:not([tabindex="-1"])'

// Would this nudge label sit on top of one of the page's own controls (a
// "Place order" button, a field)? Checks a grid of points across it.
function coversControl(label) {
  if (!label) return false
  const box = label.getBoundingClientRect()
  for (let col = 0; col <= 4; col += 1) {
    for (let row = 0; row <= 2; row += 1) {
      const x = box.left + 4 + ((box.width - 8) * col) / 4
      const y = box.top + 4 + ((box.height - 8) * row) / 2
      const under = document.elementsFromPoint(x, y).find((el) => !label.contains(el))
      if (under?.closest(PAGE_CONTROLS) && !under.closest('.help-widget__fab')) return true
    }
  }
  return false
}

// The chat itself, moved out of HelpPage.jsx wholesale - "the ai should
// work even if you dont have an account" still holds (every visitor gets
// the real AI, falling back to the free lookup bot only if the assistant
// itself is unconfigured), this is purely about where it lives on screen.
export default function HelpWidget() {
  const { isOpen, open, close } = useHelpWidget()
  const { nudge, settleLabel, dismissNudge, chatContext } = useChatNudge()
  // What this opening of the chat is about (a product, the cart, a search
  // that found nothing) - sent with each question so "is it in stock?"
  // needs no explaining; the quick questions show until the first one's asked.
  const [askContext, setAskContext] = useState(null)
  const [askedThisOpen, setAskedThisOpen] = useState(false)
  const { user, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const panelRef = useRef(null)
  const [messages, setMessages] = useState(() => (isAuthenticated ? [] : loadGuestMessages()))
  const [question, setQuestion] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(isAuthenticated)
  // null = today's live conversation; set = browsing one past week/month,
  // read-only - "you can offer a category on conversation you click it
  // you can see conversation on this date to prevent a pile of
  // conversations".
  const [viewingBucket, setViewingBucket] = useState(null)
  const [showHistoryPicker, setShowHistoryPicker] = useState(false)

  const dateGroups = useMemo(() => computeDateGroups(messages), [messages])
  const visibleMessages = useMemo(() => {
    if (!viewingBucket) return messages
    return messages.filter((message) => {
      const key = dayKey(message.createdAt)
      return key && viewingBucket.dateKeys.has(key)
    })
  }, [messages, viewingBucket])

  // "if i was on some other info above provide like an arrow down such if
  // you click it you go to the latest answer it disappears if you are the
  // lowest/latest answer" - the list tracks whether it's scrolled to the
  // bottom; when it isn't, a down-arrow button takes you to the latest
  // answer (with a dot if a new one arrived while you were reading higher up).
  const messagesRef = useRef(null)
  const lastMessageRef = useRef(null)
  const atBottomRef = useRef(true)
  const shownCountRef = useRef(0)
  const [atBottom, setAtBottom] = useState(true)
  const [unseenAnswer, setUnseenAnswer] = useState(false)

  function updateAtBottom() {
    const el = messagesRef.current
    if (!el) return
    const near = el.scrollHeight - el.scrollTop - el.clientHeight <= AT_BOTTOM_SLACK
    atBottomRef.current = near
    setAtBottom(near)
    if (near) setUnseenAnswer(false)
  }

  function scrollToLatest(behavior = 'smooth') {
    const el = messagesRef.current
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior })
    atBottomRef.current = true
    setAtBottom(true)
    setUnseenAnswer(false)
  }

  // New messages: your own question (and the typing dots) always scroll
  // into view. A new answer does too if you were following along at the
  // bottom - lined up at its first line when it's taller than the panel,
  // so you start reading from the top of it - but if you had scrolled up
  // to read something older, you stay put and the arrow gets its dot.
  useLayoutEffect(() => {
    const el = messagesRef.current
    const count = visibleMessages.length
    const added = count - shownCountRef.current
    shownCountRef.current = count
    if (!el || added === 0) return
    const latest = visibleMessages[count - 1]
    if (added !== 1 || !latest) {
      // Conversation loaded, reopened or switched to a past week/month.
      el.scrollTop = el.scrollHeight
      updateAtBottom()
      return
    }
    if (latest.from === 'user') {
      scrollToLatest()
      return
    }
    if (!atBottomRef.current) {
      setUnseenAnswer(true)
      return
    }
    const answer = lastMessageRef.current
    const top = answer && answer.offsetHeight > el.clientHeight - 24 ? answer.offsetTop - 8 : el.scrollHeight
    el.scrollTo({ top, behavior: 'smooth' })
  }, [visibleMessages])

  // The typing dots appear/disappear below the last message - keep them in
  // view while following along.
  useLayoutEffect(() => {
    if (sending && atBottomRef.current) scrollToLatest()
  }, [sending])

  // Opening the panel lands on the latest message.
  useEffect(() => {
    if (isOpen && !loadingHistory) scrollToLatest('auto')
  }, [isOpen, loadingHistory])

  useEffect(() => {
    if (!isOpen) return
    setAskContext(chatContext())
    setAskedThisOpen(false)
  }, [isOpen, chatContext])

  // A nudge label is checked before it's ever painted: if it would cover one
  // of the page's own buttons or fields, it tries the other side of the
  // chat button, and if that's taken too the ring alone has to do.
  const labelRef = useRef(null)
  const [labelMovedFor, setLabelMovedFor] = useState(null)
  const showingLabel = Boolean(nudge?.showLabel) && !isOpen
  const labelMoved = showingLabel && labelMovedFor === nudge.id
  useLayoutEffect(() => {
    if (!showingLabel || nudge.settled) return
    // (One left over from a moment ago isn't worth showing now.)
    const fresh = Date.now() - nudge.id < 3000
    if (fresh && !coversControl(labelRef.current)) settleLabel(nudge.id, true)
    else if (fresh && !labelMoved) setLabelMovedFor(nudge.id)
    else settleLabel(nudge.id, false)
  }, [showingLabel, nudge, labelMoved, settleLabel])

  // "when the keyboard pops in it doesnt feel good ... left with the
  // chat text area" - the panel's height was fixed against the full page
  // height, so once the on-screen keyboard opened, the space it actually
  // ate wasn't accounted for and the input/send row ended up pushed down
  // behind it. window.visualViewport reports the area that's actually
  // visible once the keyboard is up; tracking it and feeding the real
  // numbers into the panel's own height (via CSS vars scoped to just this
  // element, not the whole page) keeps the input pinned in view and lets
  // only the message list underneath it scroll.
  useEffect(() => {
    const viewport = window.visualViewport
    if (!isOpen || !viewport || !panelRef.current) return

    function applyViewport() {
      const el = panelRef.current
      if (!el) return
      el.style.setProperty('--vw-top', `${viewport.offsetTop}px`)
      el.style.setProperty('--vw-height', `${viewport.height}px`)
    }

    applyViewport()
    viewport.addEventListener('resize', applyViewport)
    viewport.addEventListener('scroll', applyViewport)
    return () => {
      viewport.removeEventListener('resize', applyViewport)
      viewport.removeEventListener('scroll', applyViewport)
      panelRef.current?.style.removeProperty('--vw-top')
      panelRef.current?.style.removeProperty('--vw-height')
    }
  }, [isOpen])

  // Guest messages are already restored from sessionStorage at mount
  // (the useState initializer above) and kept in sync by the effect
  // below - reopening the widget must leave them alone, not wipe them
  // back to empty the way this used to on every open.
  // Keeps a guest's conversation across a refresh - nothing to do for a
  // signed-in visitor, whose history already lives on the server.
  useEffect(() => {
    if (isAuthenticated) return
    try {
      sessionStorage.setItem(GUEST_CHAT_KEY, JSON.stringify(messages))
    } catch {
      // sessionStorage unavailable (private mode etc.) - the conversation
      // still works this session, it just won't survive a refresh.
    }
  }, [messages, isAuthenticated])

  useEffect(() => {
    if (!isOpen) return
    setViewingBucket(null)
    setShowHistoryPicker(false)
    if (!isAuthenticated) {
      setLoadingHistory(false)
      return
    }
    let cancelled = false
    setLoadingHistory(true)
    api
      .aiChatHistory(user.token, user.userId)
      .then((rows) => {
        if (cancelled) return
        setMessages(
          rows.map((row) => ({
            from: row.role === 'user' ? 'user' : 'bot',
            text: row.content,
            suggestedRoute: row.suggestedRoute,
            suggestedLabel: row.suggestedLabel,
            createdAt: row.createdAt,
          }))
        )
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoadingHistory(false)
      })
    return () => {
      cancelled = true
    }
  }, [isOpen, isAuthenticated, user])

  function handleSend(event) {
    event.preventDefault()
    sendQuestion(question.trim())
  }

  async function sendQuestion(text) {
    if (!text || sending) return
    setAskedThisOpen(true)
    const context = contextForServer(askContext)
    // Sending always returns to the live view - answering from inside a
    // past week/month wouldn't make sense.
    setViewingBucket(null)
    // A guest's recent turns go along with the question, so a follow-up
    // ("and in Nakuru?") has something to refer back to.
    const guestHistory = isAuthenticated
      ? []
      : messages.slice(-GUEST_CONTEXT_TURNS).map((m) => ({
          role: m.from === 'user' ? 'user' : 'assistant',
          content: String(m.text || '').slice(0, 1500),
        }))
    const userMessage = { from: 'user', text }
    setMessages((prev) => [...prev, userMessage])
    setQuestion('')
    setSending(true)
    // "why not provide a valid answer?" - a single dropped connection
    // (this dev setup's known Windows<->WSL relay hiccup - see
    // vite.config.js) used to send every question straight to help.py's
    // generic keyword bot on the very first hiccup. One retry of the real
    // assistant before giving up on it.
    const askAssistant = () =>
      isAuthenticated
        ? api.sendAiChatMessage(text, user.token, user.userId, [], context)
        : api.sendAiChatMessage(text, undefined, undefined, guestHistory, context)
    try {
      let res
      try {
        res = await askAssistant()
      } catch (firstErr) {
        if (isExpiredSignIn(firstErr)) throw firstErr
        res = await askAssistant()
      }
      setMessages((prev) => [
        ...prev,
        { from: 'bot', text: res.answer, suggestedRoute: res.suggestedRoute, suggestedLabel: res.suggestedLabel },
      ])
    } catch (err) {
      if (isExpiredSignIn(err)) {
        setMessages((prev) => [
          ...prev,
          { from: 'bot', text: 'Your sign-in has expired - please sign in again (account icon, top right) so I can look up your orders.' },
        ])
        return
      }
      try {
        const res = await api.chat(userMessage.text)
        setMessages((prev) => [...prev, { from: 'bot', text: res.answer }])
      } catch (fallbackErr) {
        setMessages((prev) => [...prev, { from: 'bot', text: `Sorry, something went wrong: ${fallbackErr.message}` }])
      }
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      <button
        type="button"
        className={`help-widget__fab${isOpen ? ' help-widget__fab--hidden' : ''}`}
        onClick={open}
        aria-label="Open help chat"
      >
        <Icon name="chat" size={24} strokeWidth={2.1} />
        {/* One growth ring per nudge (a new key replays it) - only just
            after it fires, so closing the chat later doesn't replay it. */}
        {nudge && !isOpen && Date.now() - nudge.id < 4000 && (
          <span key={nudge.id} className="help-widget__nudge-ring" aria-hidden="true" />
        )}
      </button>

      {showingLabel && (
        <div
          ref={labelRef}
          className={`chat-nudge${labelMoved ? ' chat-nudge--moved' : ''}${nudge.settled ? ' chat-nudge--shown' : ''}`}
          role="status"
        >
          <button type="button" className="chat-nudge__text" onClick={open}>
            {nudge.text}
          </button>
          <button type="button" className="chat-nudge__close" onClick={dismissNudge} aria-label="No thanks">
            <Icon name="close" size={14} />
          </button>
        </div>
      )}

      {isOpen && <div className="help-widget__scrim" onClick={close} />}

      <aside ref={panelRef} className={`help-widget${isOpen ? ' help-widget--open' : ''}`} aria-hidden={!isOpen}>
        {/* The forest look: the site's misty forest behind the chat - its
            photo, drifting mist and a few fireflies (styles/forest.css). */}
        {FOREST_LOOK && (
          <div className="help-widget__forest" aria-hidden="true">
            <div className="help-widget__forest-photo" />
            <div className="help-widget__forest-mist" />
            {isOpen && <Fireflies count={6} className="help-widget__fireflies" />}
          </div>
        )}
        <div className="help-widget__header">
          {FOREST_LOOK ? (
            <div className="help-widget__who">
              <span className="help-widget__avatar" aria-hidden="true">
                <img src="/brand/emblem.svg" alt="" width="30" height="30" />
              </span>
              <div>
                <h2>Dateroem assistant</h2>
                <p className="help-widget__status">
                  <span className="help-widget__status-dot" aria-hidden="true" />
                  Online · replies in seconds
                </p>
              </div>
            </div>
          ) : (
            <h2>Ask Dateroem</h2>
          )}
          <button type="button" className="help-widget__close" onClick={close} aria-label="Close">
            <Icon name="close" size={18} />
          </button>
        </div>
        {!isAuthenticated && !FOREST_LOOK && (
          <p className="state-message help-widget__hint">
            Ask away - sign in and I can also look up your own orders and remember our conversation.
          </p>
        )}
        {isAuthenticated && (
          // "place that clock on the left side below the stars and the
          // assistant name" - same layout as the mobile app: a row right
          // under the title, clock on the left, "Viewing/Back" on the
          // right only while browsing a past bucket.
          <div className="help-widget__history-row">
            <button
              type="button"
              className="help-widget__history-btn"
              onClick={() => setShowHistoryPicker((prev) => !prev)}
              aria-label="Conversation history"
            >
              <ClockIcon />
            </button>
            {viewingBucket && (
              <div className="help-widget__viewing">
                <span>Viewing: {viewingBucket.label}</span>
                <button type="button" className="btn btn--secondary" onClick={() => setViewingBucket(null)}>
                  Back to conversation
                </button>
              </div>
            )}
            {showHistoryPicker && (
              <div className="help-widget__history-panel">
                <p className="help-widget__history-title">
                  <ClockIcon size={16} /> Conversation history
                </p>
                {dateGroups.length === 0 && <p className="state-message">Nothing to browse yet.</p>}
                {dateGroups.map((group) => (
                  <button
                    key={group.label}
                    type="button"
                    className="help-widget__history-item"
                    onClick={() => {
                      setViewingBucket(group)
                      setShowHistoryPicker(false)
                    }}
                  >
                    <span>
                      <ClockIcon size={14} /> {group.label}
                    </span>
                    <span className="help-widget__history-count">{group.count}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
        <div className="chat-window chat-window--widget">
          <div className="chat-window__viewport">
            <div className="chat-window__messages" ref={messagesRef} onScroll={updateAtBottom}>
              {loadingHistory && <p className="state-message">Loading your conversation...</p>}
              {!loadingHistory && visibleMessages.length === 0 && FOREST_LOOK && !viewingBucket && (
                <div className="chat-welcome">
                  <p className="chat-welcome__hello">Hi there!</p>
                  <p>
                    I&apos;m the Dateroem assistant. Ask me about our timber, prices, delivery or paying with M-Pesa
                    {isAuthenticated ? ' - or about your own orders.' : '.'}
                  </p>
                  {!isAuthenticated && (
                    <p className="chat-welcome__note">Sign in and I can also look up your orders and remember our conversation.</p>
                  )}
                  {!askContext && (
                    <div className="chat-context__chips">
                      {GENERAL_QUESTIONS.map((quick) => (
                        <button key={quick} type="button" className="chat-context__chip" onClick={() => sendQuestion(quick)}>
                          {quick}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
              {!loadingHistory && visibleMessages.length === 0 && !(FOREST_LOOK && !viewingBucket) && (
                <p className="state-message">
                  {viewingBucket ? `Nothing in ${viewingBucket.label}.` : 'Ask us anything about our products.'}
                </p>
              )}
              {!loadingHistory &&
                visibleMessages.map((message, index) => (
                  <div
                    key={index}
                    ref={index === visibleMessages.length - 1 ? lastMessageRef : undefined}
                    className={`chat-bubble chat-bubble--${message.from}`}
                  >
                    {message.from === 'bot' ? <ChatAnswer text={message.text} /> : <p>{message.text}</p>}
                    {message.suggestedRoute && (
                      <button
                        type="button"
                        className="btn btn--secondary chat-bubble__link"
                        onClick={() => {
                          navigate(message.suggestedRoute)
                          close()
                        }}
                      >
                        {message.suggestedLabel || 'Go there'}
                      </button>
                    )}
                  </div>
                ))}
              {sending && !viewingBucket && (
                <div className="chat-bubble chat-bubble--bot chat-bubble--typing" role="status" aria-label="Dateroem is typing">
                  <span />
                  <span />
                  <span />
                </div>
              )}
            </div>
            {!atBottom && visibleMessages.length > 0 && (
              <button
                type="button"
                className="chat-window__jump"
                onClick={() => scrollToLatest()}
                aria-label={unseenAnswer ? 'New answer - go to the latest answer' : 'Go to the latest answer'}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M12 5v13M6 12l6 6 6-6" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                {unseenAnswer && <span className="chat-window__jump-dot" />}
              </button>
            )}
          </div>
          {!viewingBucket && askContext && !askedThisOpen && !loadingHistory && (
            <div className="chat-context">
              <p className="chat-context__about">
                Asking about <strong>{askContext.label}</strong>
              </p>
              <div className="chat-context__chips">
                {(QUICK_QUESTIONS[askContext.page] || []).map((quick) => (
                  <button key={quick} type="button" className="chat-context__chip" onClick={() => sendQuestion(quick)}>
                    {quick}
                  </button>
                ))}
              </div>
            </div>
          )}
          {!viewingBucket && (
            <form className="chat-window__form" onSubmit={handleSend}>
              <input
                type="text"
                value={question}
                maxLength={1000}
                onChange={(event) => setQuestion(event.target.value)}
                placeholder="Type a question..."
              />
              {/* "add a rotating feature so that one can see progress not
                  just making the button inactive" - a spinner inside the
                  button while the answer is on its way. */}
              {FOREST_LOOK ? (
                <button
                  className="chat-window__send chat-window__send--round"
                  type="submit"
                  disabled={sending || !question.trim()}
                  aria-busy={sending}
                  aria-label={sending ? 'Sending' : 'Send'}
                >
                  {sending ? <span className="chat-window__send-spinner" aria-hidden="true" /> : <Icon name="send" size={19} strokeWidth={2.1} />}
                </button>
              ) : (
              <button className="btn btn--primary chat-window__send" type="submit" disabled={sending} aria-busy={sending}>
                {sending ? (
                  <>
                    <span className="chat-window__send-spinner" aria-hidden="true" />
                    Sending
                  </>
                ) : (
                  'Send'
                )}
              </button>
              )}
            </form>
          )}
        </div>
      </aside>
    </>
  )
}
