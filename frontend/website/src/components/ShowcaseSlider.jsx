import { useCallback, useEffect, useRef, useState } from 'react'
import Icon from './Icon.jsx'

const ADVANCE_MS = 5000
const SWIPE_PX = 40

function prefersStill() {
  return typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
}

// "pictures that can be used to show case what bulk orders look like so that
// customers can see a template of what is meant by bulk orders ... like a
// slide" - the Bulk Orders page's slideshow. pictures: [{ showcaseId, image,
// caption }] in order, kept up to date by Marketers and admins in the phone
// and desktop apps. Moves on every 5 seconds, pausing while the visitor
// hovers, focuses, touches it or has the tab in the background; visitors who
// switch animations off get a still picture they change themselves.
export default function ShowcaseSlider({ pictures }) {
  const [index, setIndex] = useState(0)
  // Each reason to hold still on its own, so ending one (lifting a finger)
  // doesn't restart the show while another (the mouse still over it) holds.
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [pressed, setPressed] = useState(false)
  const [hiddenTab, setHiddenTab] = useState(false)
  const held = hovered || focused || pressed
  const [still] = useState(prefersStill)
  const pointerX = useRef(null)
  const count = pictures.length
  const shown = count ? index % count : 0

  const go = useCallback((step) => setIndex((current) => ((current % count) + step + count) % count), [count])

  useEffect(() => {
    const onVisibility = () => setHiddenTab(document.hidden)
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [])

  // index is a dependency on purpose: after any change (automatic or the
  // visitor's own) the next move is a full 5 seconds away.
  useEffect(() => {
    if (still || held || hiddenTab || count < 2) return undefined
    const timer = setTimeout(() => go(1), ADVANCE_MS)
    return () => clearTimeout(timer)
  }, [still, held, hiddenTab, count, go, index])

  if (count === 0) return null

  function onKeyDown(event) {
    if (event.key === 'ArrowLeft') go(-1)
    if (event.key === 'ArrowRight') go(1)
  }

  function onPointerDown(event) {
    pointerX.current = event.clientX
    setPressed(true)
  }

  function onPointerUp(event) {
    const start = pointerX.current
    pointerX.current = null
    setPressed(false)
    if (start === null) return
    const dx = event.clientX - start
    if (Math.abs(dx) >= SWIPE_PX) go(dx < 0 ? 1 : -1)
  }

  return (
    <section
      className="showcase"
      aria-roledescription="carousel"
      aria-label="Bulk order examples"
      tabIndex={0}
      onKeyDown={onKeyDown}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false)
      }}
    >
      <div
        className="showcase__frame"
        onPointerDown={onPointerDown}
        onPointerUp={onPointerUp}
        onPointerCancel={() => { pointerX.current = null; setPressed(false) }}
      >
        {pictures.map((picture, i) => (
          <figure
            key={picture.showcaseId}
            className={`showcase__slide${i === shown ? ' is-active' : ''}`}
            aria-roledescription="slide"
            aria-label={`${i + 1} of ${count}`}
            aria-hidden={i !== shown}
            style={{ '--showcase-picture': `url("${picture.image}")` }}
          >
            <img
              src={picture.image}
              alt={picture.caption || `Bulk order example ${i + 1}`}
              loading={i === 0 ? 'eager' : 'lazy'}
              decoding="async"
              draggable={false}
            />
            {picture.caption && <figcaption>{picture.caption}</figcaption>}
          </figure>
        ))}
        {count > 1 && (
          <>
            <button type="button" className="showcase__arrow showcase__arrow--prev" onClick={() => go(-1)} aria-label="Previous picture">
              <Icon name="chevronLeft" size={22} />
            </button>
            <button type="button" className="showcase__arrow showcase__arrow--next" onClick={() => go(1)} aria-label="Next picture">
              <Icon name="chevronRight" size={22} />
            </button>
          </>
        )}
      </div>
      {count > 1 && (
        <div className="showcase__dots">
          {pictures.map((picture, i) => (
            <button
              key={picture.showcaseId}
              type="button"
              className="showcase__dot"
              aria-label={`Show picture ${i + 1}`}
              aria-current={i === shown}
              onClick={() => setIndex(i)}
            />
          ))}
        </div>
      )}
    </section>
  )
}
