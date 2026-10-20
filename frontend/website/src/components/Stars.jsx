import { useState } from 'react'

const STAR_ROW = '★★★★★'

export const STAR_LABELS = ['Poor', 'Fair', 'Good', 'Very good', 'Excellent']

// Read-only stars, filled to the exact average (4.3 fills four and a third)
// with the wordmark's wood paint over dim empty stars. decorative: the text
// next to it already says it all (e.g. "No ratings yet").
export function Stars({ value, size = 'md', decorative = false }) {
  const fill = Math.max(0, Math.min(5, Number(value) || 0)) * 20
  const a11y = decorative ? { 'aria-hidden': true } : { role: 'img', 'aria-label': `${value} out of 5 stars` }
  return (
    <span className={`stars stars--${size}`} {...a11y}>
      <span className="stars__base" aria-hidden="true">
        {STAR_ROW}
      </span>
      <span className="stars__fill" style={{ width: `${fill}%` }} aria-hidden="true">
        {STAR_ROW}
      </span>
    </span>
  )
}

// Picking 1-5 stars: hovering previews, clicking (or arrow keys) sets it.
export function StarInput({ value, onChange }) {
  const [hover, setHover] = useState(0)
  const shown = hover || value
  return (
    <div className="star-input">
      <div className="star-input__row" role="radiogroup" aria-label="Your rating" onMouseLeave={() => setHover(0)}>
        {[1, 2, 3, 4, 5].map((stars) => (
          <button
            key={stars}
            type="button"
            role="radio"
            aria-checked={value === stars}
            aria-label={`${stars} star${stars === 1 ? '' : 's'} - ${STAR_LABELS[stars - 1]}`}
            tabIndex={value === stars || (!value && stars === 1) ? 0 : -1}
            className={stars <= shown ? 'star-input__star star-input__star--on' : 'star-input__star'}
            onMouseEnter={() => setHover(stars)}
            onClick={() => onChange(stars)}
            onKeyDown={(event) => {
              let next = 0
              if (event.key === 'ArrowRight' || event.key === 'ArrowUp') next = Math.min(5, (value || 0) + 1)
              if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') next = Math.max(1, (value || 2) - 1)
              if (!next) return
              event.preventDefault()
              onChange(next)
              event.currentTarget.parentElement.children[next - 1]?.focus()
            }}
          >
            {'★'}
          </button>
        ))}
      </div>
      <span className="star-input__label">{shown ? STAR_LABELS[shown - 1] : 'Tap a star'}</span>
    </div>
  )
}
