import { seeded } from './seeded.js'

// A few fireflies drifting and glowing, each on its own rhythm (the forest
// look, 25 October 2026). Their places come from a fixed sequence, so they
// don't jump about between pages. In the Light theme they read as specks
// of pollen in the morning sun; with animations turned off they rest.
export default function Fireflies({ count = 12, className = '' }) {
  const flies = Array.from({ length: count }, (_, i) => ({
    left: `${(seeded(i, 1) * 100).toFixed(2)}%`,
    top: `${(18 + seeded(i, 2) * 76).toFixed(2)}%`,
    '--drift-x': `${Math.round(seeded(i, 3) * 90 - 45)}px`,
    '--drift-y': `${Math.round(-10 - seeded(i, 4) * 70)}px`,
    '--fly-time': `${(9 + seeded(i, 5) * 10).toFixed(1)}s`,
    '--glow-time': `${(2.4 + seeded(i, 6) * 3.2).toFixed(1)}s`,
    '--fly-delay': `${(-seeded(i, 7) * 14).toFixed(1)}s`,
    '--size': `${(2.4 + seeded(i, 8) * 2.6).toFixed(1)}px`,
  }))
  return (
    <div className={`fireflies ${className}`.trim()} aria-hidden="true">
      {flies.map((style, i) => (
        <span key={i} className="firefly" style={style} />
      ))}
    </div>
  )
}
