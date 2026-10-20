// "4.4 · 5 ratings", "Featured in ..." and "Read the full description" jump
// down the product page. As plain #links they landed in the right place and
// were then pushed away: the campaign card turns up a moment after the
// ratings, then the video learns its shape and grows, both above them. A
// #link was also a Back step of its own, so the back arrow and the scroll
// keeper (ScrollManager) took it for another page - after opening a product
// from the list, "5 ratings" scrolled to where the list had been.
// jumpTo goes there with no history step and keeps the spot while the page
// fills in, letting go the moment the visitor scrolls themselves.
const HOLD_MS = 15000

let release = null

export function jumpTo(event, id) {
  // Ctrl, Shift or a middle click still opens the plain #link in a new tab.
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  const target = document.getElementById(id)
  if (!target) return
  event.preventDefault()
  release?.()
  target.scrollIntoView()
  // Where the keyboard (and a screen reader) carries on from, as a #link would.
  target.focus({ preventScroll: true })
  release = holdInPlace(target)
}

// Puts the section back on the same spot of the screen whenever something
// above it changes size. Any other scroll is the visitor's, and ends it.
function holdInPlace(target) {
  // The browser's own scroll anchoring picks the product details behind the
  // sticky header - above the card - so it would keep the wrong thing still.
  document.body.style.overflowAnchor = 'none'
  const spot = target.getBoundingClientRect().top
  const placeOf = () => target.getBoundingClientRect().top + window.scrollY
  let place = placeOf()
  let scrolled = window.scrollY
  let frame = 0
  const stopAt = performance.now() + HOLD_MS
  const done = () => {
    cancelAnimationFrame(frame)
    document.body.style.overflowAnchor = ''
    if (release === done) release = null
  }
  const check = () => {
    if (!target.isConnected || performance.now() > stopAt) return done()
    const now = placeOf()
    if (Math.abs(now - place) > 1) {
      window.scrollTo(0, now - spot)
      place = now
    } else if (Math.abs(window.scrollY - scrolled) > 1) {
      return done()
    }
    scrolled = window.scrollY
    frame = requestAnimationFrame(check)
    return undefined
  }
  frame = requestAnimationFrame(check)
  return done
}
