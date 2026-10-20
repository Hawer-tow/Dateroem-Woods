// The one painted-wood brush for the whole site. The Dateroem wordmark
// paints its rings with it directly, and scripts/paint-wood-textures.mjs
// uses it to paint the plank and log-end textures (src/assets/wood/) that
// every other wood-colored surface is filled with - "the rest of the areas
// in the website that have an orange like appearance ... place the paint
// like color like the one dateroem uses". Plain JS, no React or DOM, so
// Node can run it too.
//
// Colors come from the freshly cut stump photo: pale cream heartwood,
// vivid orange sapwood, dark bark.

export const EARLYWOOD = [
  [0, '#fde6bf'],
  [0.22, '#f9d296'],
  [0.48, '#f4b562'],
  [0.75, '#ee9a42'],
  [1, '#e5822b'],
]
export const LATEWOOD = [
  [0, '#d68b45'],
  [0.4, '#c76a26'],
  [1, '#a84a14'],
]
export const HIGHLIGHT = '#fff8ea'
export const BARK = '#4a260e'

// Deterministic, so a painting never reshuffles between renders or builds.
export function mulberry32(seed) {
  let a = seed
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function mix(a, b, t) {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16))
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16))
  return '#' + pa.map((v, i) => Math.round(v + (pb[i] - v) * t).toString(16).padStart(2, '0')).join('')
}

export function gradientAt(stops, t) {
  const x = Math.min(1, Math.max(0, t))
  for (let i = 1; i < stops.length; i++) {
    if (x <= stops[i][0]) {
      const [t0, c0] = stops[i - 1]
      const [t1, c1] = stops[i]
      return mix(c0, c1, (x - t0) / (t1 - t0))
    }
  }
  return stops[stops.length - 1][1]
}

export function smoothstep(a, b, x) {
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)))
  return t * t * (3 - 2 * t)
}

// A few long, crossing swells. Neighbouring rings sit in nearly the same
// spot of this field, so they bend together like real grain instead of
// tangling into each other.
function makeSwell(scale) {
  const rand = mulberry32(417)
  const swells = [
    [58, 1],
    [41, 0.75],
    [83, 0.9],
    [29, 0.3],
  ].map(([length, weight]) => {
    const angle = rand() * Math.PI * 2
    const k = (Math.PI * 2) / (length * scale)
    return { kx: Math.cos(angle) * k, ky: Math.sin(angle) * k, phase: rand() * Math.PI * 2, weight }
  })
  const total = swells.reduce((sum, s) => sum + s.weight, 0)
  return (x, y) => {
    let sum = 0
    for (const s of swells) sum += s.weight * Math.sin(s.kx * x + s.ky * y + s.phase)
    return (sum / total) * 1.5
  }
}

const tenth = (n) => Math.round(n * 10) / 10
const fmt = (n) => {
  const s = tenth(n).toString()
  return s === '-0' ? '0' : s
}

// Relative moves ("l") keep the numbers short - the textures carry tens of
// thousands of them. Deltas are taken between already-rounded points, so
// rounding never drifts along a long ring.
function joinPoints(points) {
  let [px, py] = points[0].map(tenth)
  let d = `M${fmt(px)} ${fmt(py)}l`
  let first = true
  for (let i = 1; i < points.length; i++) {
    const x = tenth(points[i][0])
    const y = tenth(points[i][1])
    const dx = fmt(x - px)
    const dy = fmt(y - py)
    d += (first || dx.startsWith('-') ? '' : ' ') + dx + (dy.startsWith('-') ? '' : ' ') + dy
    first = false
    px = x
    py = y
  }
  return d
}

function dashes(rand, scale, dashMin, dashMax, gapMin, gapMax) {
  const parts = []
  for (let i = 0; i < 4; i++) {
    parts.push(scale * (dashMin + rand() * (dashMax - dashMin)), scale * (gapMin + rand() * (gapMax - gapMin)))
  }
  return parts.map(fmt).join(' ')
}

/**
 * Paints year after year of growth rings around a pith, as SVG path data.
 *
 * box      - { x0, y0, x1, y1 }: the area that has to be covered; paths are
 *            clamped to it so huge rings only carry the arcs that show.
 * form(r)  - { cx, cy, sx, wander } for the ring at base radius r: its
 *            center, sideways stretch, and how far it wanders off round.
 * covers(r)- true once ring r takes in the whole box.
 * scale    - 1 is the wordmark's scale; bigger paints wider rings, longer
 *            swells and heavier strokes for textures seen larger.
 * ageOf    - maps (ring, oldest ring) to 0..1 along the palette, pith to bark.
 * detail   - point spacing multiplier; textures seen at 1:1 can go coarser.
 * contrast - how strongly ring lines and brush streaks show; backgrounds
 *            with text on top want them calmer than the wordmark does.
 *
 * Returns the rings in painter's order (oldest, outermost first) and the
 * sapwood color to lay underneath them.
 */
export function paintRings({ box, form, covers, scale = 1, startRadius = 1.4, ageOf, detail = 1, contrast = 1 }) {
  const swell = makeSwell(scale)
  const stroke = Math.sqrt(scale)

  function ringPoints(r, spacing) {
    const f = form(r)
    const steps = Math.max(56, Math.ceil((Math.PI * 2 * r * f.sx) / (spacing * stroke * detail)))
    const lobeWeight = smoothstep(4 * scale, 30 * scale, r)
    const points = []
    for (let i = 0; i < steps; i++) {
      const th = (i / steps) * Math.PI * 2
      const cos = Math.cos(th)
      const sin = Math.sin(th)
      const lobed = r * (1 + (0.035 * Math.sin(2 * th + 0.7) + 0.022 * Math.sin(3 * th + 2.1)) * lobeWeight)
      const radius = lobed + f.wander * swell(f.cx + lobed * cos * f.sx, f.cy + lobed * sin)
      points.push([f.cx + radius * cos * f.sx, f.cy + radius * sin])
    }
    return points
  }

  // A filled ring, squashed onto the edge of the box wherever it runs
  // outside it - whatever is painted gets clipped to a shape anyway.
  function closedPath(points) {
    const out = []
    for (const [x, y] of points) {
      const px = Math.min(box.x1, Math.max(box.x0, x))
      const py = Math.min(box.y1, Math.max(box.y0, y))
      const last = out[out.length - 1]
      const prev = out[out.length - 2]
      if (last && last[0] === px && last[1] === py) continue
      const alongSide =
        prev &&
        ((prev[0] === last[0] && last[0] === px && (px === box.x0 || px === box.x1)) ||
          (prev[1] === last[1] && last[1] === py && (py === box.y0 || py === box.y1)))
      if (alongSide) out[out.length - 1] = [px, py]
      else out.push([px, py])
    }
    return joinPoints(out) + 'Z'
  }

  // A painted line along a ring - only the stretches that pass through the
  // box. Rings are closed, so a stretch can wrap from the end to the start.
  function openPath(points) {
    const inside = points.map(([x, y]) => x >= box.x0 && x <= box.x1 && y >= box.y0 && y <= box.y1)
    if (inside.every(Boolean)) return joinPoints(points) + 'Z'
    const start = inside.indexOf(false)
    const runs = []
    let run = []
    for (let k = 1; k <= points.length; k++) {
      const i = (start + k) % points.length
      if (inside[i]) {
        run.push(points[i])
      } else if (run.length) {
        runs.push(run)
        run = []
      }
    }
    if (run.length) runs.push(run)
    return runs.filter((r) => r.length > 1).map(joinPoints).join('')
  }

  // Ring widths follow a slowly drifting "climate": runs of good years lay
  // down wide rings, dry spells narrow ones, and the tree grows a little
  // slower every year as it matures.
  const widthRand = mulberry32(1701)
  const rings = []
  let r = startRadius
  let climate = 0
  let extra = 1
  while (extra >= 0) {
    climate = 0.6 * climate + 0.4 * (widthRand() * 2 - 1)
    const vigor = 3.3 - 0.9 * smoothstep(0, 40 * scale, r)
    const width = scale * Math.max(1.7, vigor * (1 + 0.45 * climate) * (0.85 + 0.3 * widthRand()))
    rings.push({ inner: r, outer: r + width, width })
    r += width
    if (covers(r)) extra -= 1
  }

  const rand = mulberry32(90210)
  const oldest = rings[rings.length - 1]
  const painted = rings.map((ring, order) => {
    const age = ageOf ? ageOf(ring, oldest) : ring.outer / oldest.outer
    const base = gradientAt(EARLYWOOD, age)
    const late = gradientAt(LATEWOOD, age)
    // Never quite the same mix twice, like paint picked up fresh each ring.
    const early = rand() < 0.5 ? mix(base, HIGHLIGHT, 0.12 * rand()) : mix(base, late, 0.1 * rand())
    const lateWidth = stroke * Math.min(1.15, Math.max(0.7, (0.26 * ring.width) / scale))
    const lateLine = openPath(ringPoints(ring.outer - 0.3 * scale, 2.4))
    return {
      order,
      fill: closedPath(ringPoints(ring.outer, 2.4)),
      early,
      strokes: [
        // Earlywood darkening toward the year's latewood.
        { d: openPath(ringPoints(ring.inner + 0.62 * ring.width, 2.6)), color: mix(early, late, 0.4), width: 0.5 * ring.width, opacity: 0.45 * contrast },
        // Brush drag - light bristles...
        {
          d: openPath(ringPoints(ring.inner + 0.28 * ring.width, 3.2)),
          color: mix(early, HIGHLIGHT, 0.65),
          width: stroke,
          opacity: 0.6 * contrast,
          dash: dashes(rand, scale, 8, 30, 3, 12),
          offset: fmt(rand() * 60 * scale),
        },
        // ...and darker ones.
        {
          d: openPath(ringPoints(ring.inner + 0.5 * ring.width, 3.2)),
          color: mix(early, late, 0.7),
          width: 0.7 * stroke,
          opacity: 0.4 * contrast,
          dash: dashes(rand, scale, 5, 20, 4, 14),
          offset: fmt(rand() * 60 * scale),
        },
        // The ring line itself, with thick-thin brush pressure on top.
        { d: lateLine, color: late, width: lateWidth, opacity: 0.9 * contrast },
        {
          d: lateLine,
          color: late,
          width: lateWidth * 1.8,
          opacity: 0.35 * contrast,
          dash: dashes(rand, scale, 10, 40, 6, 24),
          offset: fmt(rand() * 60 * scale),
        },
        // A line can fall entirely outside the box - nothing to paint.
      ].filter((s) => s.d),
    }
  })
  return { rings: painted.reverse(), sapwood: gradientAt(EARLYWOOD, 1) }
}

// The painted rings as SVG markup: defs (a ring line painted twice - plain,
// then with brush pressure - is written once and reused) and the drawing.
export function ringsMarkup({ rings }, idPrefix = 'r') {
  const defs = []
  const body = []
  let nextId = 0
  for (const ring of rings) {
    body.push(`<path d="${ring.fill}" fill="${ring.early}"/>`)
    const shared = new Map()
    for (const s of ring.strokes) {
      let ref = shared.get(s.d)
      if (!ref && ring.strokes.filter((other) => other.d === s.d).length > 1) {
        ref = `${idPrefix}${nextId++}`
        shared.set(s.d, ref)
        defs.push(`<path id="${ref}" d="${s.d}"/>`)
      }
      const dash = s.dash ? ` stroke-dasharray="${s.dash}" stroke-dashoffset="${s.offset}"` : ''
      const paint = `fill="none" stroke="${s.color}" stroke-width="${fmt(s.width)}" stroke-opacity="${s.opacity}" stroke-linecap="round"${dash}`
      body.push(ref ? `<use href="#${ref}" ${paint}/>` : `<path d="${s.d}" ${paint}/>`)
    }
  }
  return { defs: defs.join('\n'), body: body.join('\n') }
}

// The same painting as a standalone SVG document, for CSS backgrounds.
export function ringsToSvg(painting, { x, y, width, height }, extra = '') {
  const { defs, body } = ringsMarkup(painting)
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${x} ${y} ${width} ${height}" width="${width}" height="${height}">`,
    defs ? `<defs>${defs}</defs>` : '',
    `<rect x="${x}" y="${y}" width="${width}" height="${height}" fill="${painting.sapwood}"/>`,
    body,
    extra,
    '</svg>',
  ].join('\n')
}
