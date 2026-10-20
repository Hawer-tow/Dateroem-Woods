// Paints the wood textures in src/assets/wood/ with the same brush as the
// Dateroem wordmark (src/wood/woodPaint.js) - "the rest of the areas in the
// website that have an orange like appearance ... place the paint like
// color like the one dateroem uses". Runs before every build (prebuild), so
// the textures can never drift away from the wordmark.
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { BARK, paintRings, ringsToSvg, smoothstep } from '../src/wood/woodPaint.js'

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'assets', 'wood')
mkdirSync(OUT, { recursive: true })

// True once ring r (less how far it can wander inward) takes in every
// corner of the box.
function coversBox(form, box, wanderMargin) {
  const corners = [
    [box.x0, box.y0],
    [box.x1, box.y0],
    [box.x0, box.y1],
    [box.x1, box.y1],
  ]
  return (r) => {
    const f = form(r)
    const reach = r - wanderMargin
    return corners.every(([x, y]) => ((x - f.cx) / (reach * f.sx)) ** 2 + ((y - f.cy) / reach) ** 2 <= 1)
  }
}

// A sawn board, for buttons, badges and bubbles. The pith sits just below
// the board and the rings are stretched long, so they cross it as the
// gently arched, wavy grain of flat-sawn timber.
{
  const view = { x: 0, y: 0, width: 600, height: 200 }
  const box = { x0: 0, y0: 0, x1: 600, y1: 200 }
  const pith = { x: 270, y: 262 }
  const form = (r) => ({ cx: pith.x - 0.06 * r, cy: pith.y, sx: 2.3, wander: 3.2 })
  const plank = paintRings({
    box,
    form,
    covers: coversBox(form, box, 5),
    scale: 3.2,
    detail: 2.2,
    // Text sits on top of this one - calmer grain than the wordmark's.
    contrast: 0.55,
    startRadius: 44,
    // The board shows no pale heart - honey near the bottom of the arches
    // through to vivid orange sapwood at the top.
    ageOf: (ring, oldest) => 0.15 + 0.85 * smoothstep(pith.y - box.y1, oldest.outer, ring.outer),
  })
  writeFileSync(join(OUT, 'plank.svg'), ringsToSvg(plank, view))
}

// A freshly cut round, for round things: pale heart, rings out to vivid
// orange sapwood, a dark bark rim and a couple of drying checks.
{
  const view = { x: 0, y: 0, width: 200, height: 200 }
  const box = { x0: 0, y0: 0, x1: 200, y1: 200 }
  const pith = { x: 103, y: 97 }
  const barkRadius = 88
  const form = (r) => ({
    cx: pith.x - 0.04 * r,
    cy: pith.y + 0.02 * r,
    sx: 1,
    wander: 0.4 + 1.6 * smoothstep(10, 80, r),
  })
  const log = paintRings({
    box,
    form,
    covers: (r) => r >= barkRadius,
    scale: 2.4,
    detail: 2,
    ageOf: (ring) => Math.min(1, ring.outer / barkRadius),
  })
  const extra = [
    // Drying checks running out from the heart.
    `<path d="M104.5 95.5L121 79.5L133.5 66" fill="none" stroke="#5a2c10" stroke-width="2" stroke-linecap="round" opacity="0.7"/>`,
    `<path d="M104.5 95.5L113 87.3" fill="none" stroke="#5a2c10" stroke-width="3.4" stroke-linecap="round" opacity="0.7"/>`,
    `<path d="M101.5 98.5L88.5 109L79 120.5" fill="none" stroke="#5a2c10" stroke-width="1.7" stroke-linecap="round" opacity="0.6"/>`,
    `<ellipse cx="${pith.x}" cy="${pith.y}" rx="3.2" ry="2.7" fill="#8a4418"/>`,
    // Bark, with its own rough fissures, and the thin cambium line inside it.
    `<circle cx="100" cy="100" r="${barkRadius + 5.5}" fill="none" stroke="${BARK}" stroke-width="12"/>`,
    `<circle cx="100" cy="100" r="${barkRadius + 5.5}" fill="none" stroke="#6e3c18" stroke-width="4" stroke-dasharray="7 5 3 6 11 4" opacity="0.8"/>`,
    `<circle cx="100" cy="100" r="${barkRadius - 0.8}" fill="none" stroke="#8a4a1c" stroke-width="1.8" opacity="0.85"/>`,
  ].join('\n')
  writeFileSync(join(OUT, 'log-end.svg'), ringsToSvg(log, view, extra))
}

console.log('painted wood textures into', OUT)
