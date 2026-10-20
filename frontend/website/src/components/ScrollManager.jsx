import { useEffect, useLayoutEffect, useRef } from 'react'
import { useLocation, useNavigationType } from 'react-router-dom'

// "when mobile i open a product im taken to the bottom of the product
// description which should start from the top and i go back im taken to
// the top ... it should go back to the product i was at before" - a
// single-page app keeps whatever scroll position the last page had, so:
//   a new page (a link, a button)   starts at the top (or at its #anchor)
//   Back / Forward                  returns to where that page was left
// Positions are remembered per history entry while the tab is open.
const positions = new Map()

export default function ScrollManager() {
  const location = useLocation()
  const navigationType = useNavigationType()
  const keyRef = useRef(location.key)

  useEffect(() => {
    // The browser's own restoration fights this one on every Back.
    if ('scrollRestoration' in window.history) window.history.scrollRestoration = 'manual'
    let frame = 0
    const remember = () => {
      if (frame) return
      frame = requestAnimationFrame(() => {
        frame = 0
        positions.set(keyRef.current, window.scrollY)
      })
    }
    window.addEventListener('scroll', remember, { passive: true })
    return () => {
      window.removeEventListener('scroll', remember)
      cancelAnimationFrame(frame)
    }
  }, [])

  useLayoutEffect(() => {
    keyRef.current = location.key
    if (navigationType === 'POP') {
      const saved = positions.get(location.key)
      // Nothing remembered (e.g. an in-page #anchor jump): leave the
      // browser's own position alone.
      return saved == null ? undefined : restore(saved)
    }
    if (location.hash) {
      const target = document.getElementById(decodeURIComponent(location.hash.slice(1)))
      if (target) {
        target.scrollIntoView()
        return undefined
      }
    }
    window.scrollTo(0, 0)
    return undefined
    // Only a move to another history entry should scroll.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key])

  return null
}

// A page coming back may still be filling in (products loading), so keep
// trying for a moment until it's tall enough - and stop the moment the
// visitor scrolls themselves.
function restore(y) {
  let frame = 0
  let tries = 0
  let cancelled = false
  const stop = () => {
    cancelled = true
    cancelAnimationFrame(frame)
    window.removeEventListener('wheel', stop)
    window.removeEventListener('touchstart', stop)
    window.removeEventListener('keydown', stop)
  }
  const attempt = () => {
    if (cancelled) return
    const max = document.documentElement.scrollHeight - window.innerHeight
    window.scrollTo(0, Math.min(y, Math.max(0, max)))
    if (max >= y || tries++ > 120) {
      stop()
      return
    }
    frame = requestAnimationFrame(attempt)
  }
  window.addEventListener('wheel', stop, { passive: true })
  window.addEventListener('touchstart', stop, { passive: true })
  window.addEventListener('keydown', stop)
  attempt()
  return stop
}
