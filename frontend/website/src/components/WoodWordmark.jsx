import { useId } from 'react'
import { BARK } from '../wood/woodPaint.js'
import { LETTERS, LETTER_EDGES, VIEW_H, VIEW_W, VIEW_X, VIEW_Y, WOOD } from '../wood/wordmark.js'

// "painted by a brush ... even the name itself is designed to feel like a
// tree so that the theme of rings of maturity of a tree are felt in depth".
// The whole word is one slice through a single log. The "o" is the heart of
// the tree - its counter reads as the split pith of a freshly cut stump -
// and year after year of growth rings spread out from it through every
// other letter: round and tight in the young wood, then wider, more
// stretched and wavier out in the old wood at "D" and "m". The rings are
// laid on with the site's shared wood brush (src/wood/woodPaint.js), the
// same one every other wood surface on the site is painted with. The
// letters and rings themselves live in src/wood/wordmark.js, shared with
// the export that puts this same mark in the mobile and desktop apps.

export default function WoodWordmark({ className = '' }) {
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '')
  const cutId = `cut-${uid}`
  const lettersId = `letters-${uid}`
  const liftId = `lift-${uid}`
  const region = { x: VIEW_X, y: VIEW_Y, width: VIEW_W, height: VIEW_H }

  return (
    <svg
      className={`wood-wordmark ${className}`.trim()}
      viewBox={`${VIEW_X} ${VIEW_Y} ${VIEW_W} ${VIEW_H}`}
      role="img"
      aria-label="Dateroem"
    >
      <defs>
        {/* Hand-cut, not typeset: the letter edges bend gently. The same
            filter shapes both the letters and their bark, so they line up. */}
        <filter id={cutId} filterUnits="userSpaceOnUse" {...region} colorInterpolationFilters="sRGB">
          <feTurbulence type="fractalNoise" baseFrequency="0.05" numOctaves="1" seed="4" result="wobble" />
          <feDisplacementMap in="SourceGraphic" in2="wobble" scale="3" xChannelSelector="R" yChannelSelector="G" />
        </filter>
        <mask id={lettersId} maskUnits="userSpaceOnUse" {...region}>
          <g fill="#fff" filter={`url(#${cutId})`}>
            {LETTERS.map((d, i) => (
              <path key={i} d={d} />
            ))}
          </g>
        </mask>
        <filter id={liftId} filterUnits="userSpaceOnUse" {...region}>
          <feDropShadow dx="0" dy="1.2" stdDeviation="0.9" floodColor="#000" floodOpacity="0.5" />
        </filter>
      </defs>

      <g filter={`url(#${liftId})`}>
        <g fill="none" stroke={BARK} strokeWidth="2.6" strokeLinejoin="round" filter={`url(#${cutId})`}>
          {LETTER_EDGES.map((d, i) => (
            <path key={i} d={d} />
          ))}
        </g>
        <g mask={`url(#${lettersId})`}>
          <rect {...region} fill={WOOD.sapwood} />
          {WOOD.rings.map((ring) => (
            <g key={ring.order} className="wood-wordmark__ring" style={{ '--ring': ring.order }}>
              <path d={ring.fill} fill={ring.early} />
              {ring.strokes.map((s, i) => (
                <path
                  key={i}
                  d={s.d}
                  fill="none"
                  stroke={s.color}
                  strokeWidth={s.width}
                  strokeOpacity={s.opacity}
                  strokeDasharray={s.dash}
                  strokeDashoffset={s.offset}
                  strokeLinecap="round"
                />
              ))}
            </g>
          ))}
        </g>
      </g>
    </svg>
  )
}
