// "in the animations on the [delivery lorry] there is that image i want to
// use it as the company log ... enhance it making it look professional and
// appealing without having to restructure it so much" - the emblem painted
// on the lorry in the About film (dateroem animations/parents/05_DELIVERY.png),
// redrawn as clean vector lines: the end of a log with its wavy bark edge
// and growth rings, and a tree whose canopy is the innermost ring, its trunk
// standing on the second ring. Gold on near-black, like the lorry.
//
// Two weights of the same drawing:
//   full  every ring, for anything shown large (social pictures, home screen)
//   bold  bark edge, canopy and tree, thicker, for small sizes (the
//         footer) where thin rings would blur together
// and tabIconSvg(), the browser tab icon: the bold drawing at its thickest,
// filling the whole icon square.
//
// scripts/export-brand.mjs turns these into the website's icons and the
// pictures in brand/. Pure functions, no React - nothing here is bundled.

const C = 256
const TAU = Math.PI * 2
const BOTTOM = Math.PI / 2 // SVG y points down

export const GOLD = ['#f7e0ad', '#dcaa68', '#a96f37', '#e6bb7c']
export const NIGHT = ['#2b2119', '#120d09']

function ring(cx, cy, radius, from = 0, to = TAU, steps = 240) {
  const points = []
  for (let i = 0; i <= steps; i++) {
    const t = from + ((to - from) * i) / steps
    const r = radius(t)
    points.push([cx + r * Math.cos(t), cy + r * Math.sin(t)])
  }
  return points
}

const fmt = (n) => (Math.round(n * 10) / 10).toString()
const line = (points, closed) =>
  points.map(([x, y], i) => `${i ? 'L' : 'M'}${fmt(x)} ${fmt(y)}`).join('') + (closed ? 'Z' : '')

function stroke(d, width) {
  return `<path d="${d}" fill="none" stroke="url(#gold)" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round"/>`
}

// One branch: from the trunk, curving up and out to (dx, dy) away.
function branch(y0, dx, dy, width) {
  return [-1, 1]
    .map((side) => {
      const d = `M${C + side * 2} ${y0}Q${fmt(C + side * dx * 0.35)} ${fmt(y0 - dy * 0.78)} ${C + side * dx} ${y0 - dy}`
      return stroke(d, width)
    })
    .join('')
}

// The trunk: narrow at the top, widening down, flaring into its roots.
function trunk(top, bottom, topHalf, baseHalf, flareHalf) {
  const knee = bottom - (bottom - top) * 0.14
  return (
    `<path fill="url(#gold)" d="M${C - flareHalf} ${bottom}` +
    `C${C - baseHalf * 1.2} ${bottom - 6} ${C - baseHalf} ${bottom - 14} ${C - baseHalf} ${fmt(knee)}` +
    `L${C - topHalf} ${top + 6}Q${C} ${top - 4} ${C + topHalf} ${top + 6}` +
    `L${C + baseHalf} ${fmt(knee)}C${C + baseHalf} ${bottom - 14} ${C + baseHalf * 1.2} ${bottom - 6} ${C + flareHalf} ${bottom}Z"/>`
  )
}

function fullMark() {
  // The bark edge: uneven bumps, as on a real sawn log.
  const bark = ring(C, C, (t) => 214 + 5.5 * Math.sin(13 * t + 0.4) + 2.5 * Math.sin(4 * t + 1.3) + 1.5 * Math.sin(23 * t + 2))
  const second = ring(C, C, (t) => 180 + 2.2 * Math.sin(9 * t + 2.1) + 1.8 * Math.sin(3 * t + 0.2))
  // The third ring opens at the bottom for the trunk, and once more up on
  // the right - the small break the lorry's drawing has.
  const third = (from, to) => ring(C, C, (t) => 150 + 1.6 * Math.sin(7 * t + 1.1), from, to, 160)
  const cy = 240
  const gap = 0.2
  // The canopy: scalloped like leaves, open at the bottom for the trunk.
  const canopy = ring(C, cy, (t) => 92 + 12 * Math.abs(Math.sin(5.5 * (t - BOTTOM))), BOTTOM + gap, BOTTOM + TAU - gap, 330)
  return [
    stroke(line(bark, true), 15),
    stroke(line(second, true), 10),
    stroke(line(third(BOTTOM + 0.13, TAU * 0.86 + 0.0), false), 7),
    stroke(line(third(TAU * 0.86 + 0.09, TAU + BOTTOM - 0.13), false), 7),
    stroke(line(canopy, false), 10),
    trunk(156, 438, 3.5, 11, 27),
    branch(322, 76, 60, 8.5),
    branch(288, 72, 58, 8),
    branch(254, 62, 54, 7.5),
    branch(220, 46, 44, 7),
    branch(190, 26, 28, 6),
  ].join('')
}

function boldMark() {
  const bark = ring(C, C, (t) => 202 + 7 * Math.sin(11 * t + 0.4) + 3 * Math.sin(4 * t + 1.3))
  const cy = 236
  const gap = 0.24
  const canopy = ring(C, cy, (t) => 104 + 16 * Math.abs(Math.sin(4.5 * (t - BOTTOM))), BOTTOM + gap, BOTTOM + TAU - gap, 270)
  return [
    stroke(line(bark, true), 30),
    stroke(line(canopy, false), 22),
    trunk(150, 436, 7, 17, 40),
    branch(320, 70, 56, 18),
    branch(268, 62, 52, 17),
    branch(214, 40, 40, 16),
  ].join('')
}

function defs(tile) {
  return (
    '<defs>' +
    `<linearGradient id="gold" gradientUnits="userSpaceOnUse" x1="96" y1="40" x2="416" y2="472">` +
    `<stop offset="0" stop-color="${GOLD[0]}"/><stop offset="0.38" stop-color="${GOLD[1]}"/>` +
    `<stop offset="0.7" stop-color="${GOLD[2]}"/><stop offset="1" stop-color="${GOLD[3]}"/></linearGradient>` +
    (tile
      ? `<radialGradient id="night" cx="0.5" cy="0.42" r="0.7"><stop offset="0" stop-color="${NIGHT[0]}"/><stop offset="1" stop-color="${NIGHT[1]}"/></radialGradient>`
      : '') +
    '</defs>'
  )
}

// The emblem as an SVG document, 512 x 512. tile: 'none' (see-through),
// 'square' (dark square; phones round the corners themselves) or 'rounded'.
export function emblemSvg({ weight = 'full', tile = 'none' } = {}) {
  const background =
    tile === 'none' ? '' : `<rect width="512" height="512" ${tile === 'rounded' ? 'rx="112"' : ''} fill="url(#night)"/>`
  // On a tile the mark sits a little inside the edge.
  const mark = weight === 'bold' ? boldMark() : fullMark()
  const body = tile === 'none' ? mark : `<g transform="translate(256 256) scale(0.86) translate(-256 -256)">${mark}</g>`
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">${defs(tile !== 'none')}${background}${body}</svg>`
}

// The browser tab icon. "can we make it a little more visible ... without
// having to affect size allowed in the browser area" - browsers draw tab
// icons in a fixed small square, so the emblem fills all of it: its own
// wavy bark edge is the outline of the icon (dark inside, so it shows on
// light and dark tab bars alike, with see-through corners), drawn with the
// thickest lines of the three.
export function tabIconSvg() {
  const barkRadius = (t) => 208 + 6 * Math.sin(11 * t + 0.4) + 3 * Math.sin(4 * t + 1.3)
  const barkWidth = 40
  const bark = ring(C, C, barkRadius)
  const reach = Math.max(...bark.map(([x, y]) => Math.hypot(x - C, y - C))) + barkWidth / 2
  const cy = 236
  const gap = 0.26
  const canopy = ring(C, cy, (t) => 100 + 16 * Math.abs(Math.sin(4.5 * (t - BOTTOM))), BOTTOM + gap, BOTTOM + TAU - gap, 270)
  const mark = [
    `<path d="${line(bark, true)}" fill="url(#night)" stroke="url(#gold)" stroke-width="${barkWidth}" stroke-linejoin="round"/>`,
    stroke(line(canopy, false), 30),
    trunk(150, 436, 9, 22, 46),
    branch(318, 60, 52, 24),
    branch(262, 58, 48, 22),
    branch(210, 32, 32, 20),
  ].join('')
  const scale = (255 / reach).toFixed(3)
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">${defs(true)}` +
    `<g transform="translate(256 256) scale(${scale}) translate(-256 -256)">${mark}</g></svg>`
  )
}

// The emblem's markup without the <svg> wrapper and with its own gradient
// id, for placing it inside a bigger picture.
export function emblemGroup(weight = 'full') {
  return { defs: defs(false).replace(/^<defs>|<\/defs>$/g, ''), mark: weight === 'bold' ? boldMark() : fullMark() }
}
