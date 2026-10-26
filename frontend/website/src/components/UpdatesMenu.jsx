import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import { formatEndDate } from '../context/FeaturedContext.jsx'
import Icon from './Icon.jsx'

// The newest notice this browser has opened. Written only when the visitor
// opens Updates (a feature they used, like the theme), never on its own.
const SEEN_KEY = 'dateroem_updates_seen'

function readSeen() {
  try {
    return Number(localStorage.getItem(SEEN_KEY)) || 0
  } catch {
    return 0
  }
}

function writeSeen(noticeId) {
  try {
    localStorage.setItem(SEEN_KEY, String(noticeId))
  } catch {
    // Storage blocked (private mode): the dot simply shows again next visit.
  }
}

// "a feature not mostly visible but is available in the website like an
// information area ... e.g we might be having an update soon or new
// features" (26 October 2026) - the notices the admins write in the desktop
// app, loaded once per visit. Each one leaves by itself after its last day.
export function useUpdates() {
  const [notices, setNotices] = useState([])
  const [seen, setSeen] = useState(readSeen)

  useEffect(() => {
    let cancelled = false
    api
      .helpNotices()
      .then((list) => {
        if (!cancelled && Array.isArray(list)) setNotices(list)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const newest = notices.length ? Math.max(...notices.map((notice) => notice.noticeId)) : 0
  return {
    notices,
    unread: newest > seen,
    markSeen: () => {
      if (newest > seen) {
        writeSeen(newest)
        setSeen(newest)
      }
    },
  }
}

// Wide screens: a quiet bell beside the cart. Phones (inMenu): an Updates
// row in the fold-out menu - a fourth round button didn't fit beside the
// emblem on a 360px phone - while the menu button carries the dot. Either
// way it is there only while a notice is showing, with a dot until the
// visitor has opened the newest one.
export default function UpdatesMenu({ updates, inMenu = false, onOpen }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open || inMenu) return undefined
    const closeOutside = (event) => {
      if (!ref.current?.contains(event.target)) setOpen(false)
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open, inMenu])

  if (updates.notices.length === 0) return null

  const toggle = () => {
    if (!open) {
      onOpen?.()
      updates.markSeen()
    }
    setOpen((value) => !value)
  }

  return (
    <div className={`updates-menu${inMenu ? ' updates-menu--in-menu' : ''}`} ref={ref}>
      {inMenu ? (
        <button type="button" className="updates-menu__row" aria-expanded={open} onClick={toggle}>
          <Icon name="bell" size={16} />
          Updates
          {updates.unread && <span className="updates-menu__dot updates-menu__dot--inline" />}
        </button>
      ) : (
        <button
          type="button"
          className="navbar__cart updates-menu__button"
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-label={updates.unread ? 'Updates (new)' : 'Updates'}
          onClick={toggle}
        >
          <Icon name="bell" size={18} />
          {updates.unread && <span className="updates-menu__dot" />}
        </button>
      )}
      {open && (
        <div className="updates-menu__panel" role="dialog" aria-label="Updates from Dateroem Woods">
          {!inMenu && <p className="updates-menu__heading">Updates</p>}
          <ul className="updates-menu__list">
            {updates.notices.map((notice) => (
              <li key={notice.noticeId} className="updates-menu__item">
                <p className="updates-menu__title">{notice.title}</p>
                <p className="updates-menu__message">{notice.message}</p>
                {notice.postedOn && <p className="updates-menu__date">{formatEndDate(notice.postedOn)}</p>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
