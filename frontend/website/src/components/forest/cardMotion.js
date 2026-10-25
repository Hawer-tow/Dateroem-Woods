import { motionAllowed } from '../../core/look.js'

// How a product card moves in the forest look (25 October 2026).

// Cards further down the page rise into place as they come into view, a
// few at a time like saplings in a row (the product page's journey strip
// does the same). Cards already on screen when the page opens keep the
// site's usual opening rise (index.css).
let observer = null

function watcher() {
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        let shown = 0
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          entry.target.style.setProperty('--reveal-delay', `${Math.min(shown, 5) * 80}ms`)
          entry.target.dataset.reveal = 'seen'
          observer.unobserve(entry.target)
          shown += 1
        }
      },
      { rootMargin: '0px 0px -6% 0px', threshold: 0.05 },
    )
  }
  return observer
}

export function revealWhenSeen(card) {
  if (!card || typeof IntersectionObserver === 'undefined' || !motionAllowed()) return undefined
  if (card.getBoundingClientRect().top < window.innerHeight) return undefined
  card.dataset.reveal = 'wait'
  const io = watcher()
  io.observe(card)
  return () => io.unobserve(card)
}

// With a mouse, the card leans a little toward the pointer and a soft light
// follows it across the wood, like sun through leaves. Touch screens skip it.
export function leanToward(event) {
  if (event.pointerType !== 'mouse' || !motionAllowed()) return
  const card = event.currentTarget
  const box = card.getBoundingClientRect()
  const x = (event.clientX - box.left) / box.width
  const y = (event.clientY - box.top) / box.height
  card.style.setProperty('--tilt-x', `${((x - 0.5) * 7).toFixed(2)}deg`)
  card.style.setProperty('--tilt-y', `${((0.5 - y) * 7).toFixed(2)}deg`)
  card.style.setProperty('--glow-x', `${(x * 100).toFixed(1)}%`)
  card.style.setProperty('--glow-y', `${(y * 100).toFixed(1)}%`)
}

export function settle(event) {
  const card = event.currentTarget
  card.style.removeProperty('--tilt-x')
  card.style.removeProperty('--tilt-y')
}
