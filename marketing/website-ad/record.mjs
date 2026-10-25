// Films the website advert (ad.html) scene by scene (25 October 2026).
//   node --experimental-websocket record.mjs <devtools port> <ad page url> <frames folder>
// It needs a Chromium-based browser (Edge or Chrome) running headless with
// --remote-debugging-port, and ad.html served next to a website build
// (make-ad.sh). Headless browsers only send a few frames a second, so
// everything on the page runs SLOW times slower - the animations, the
// scrolling, the pointer and the campaign video - and list.txt plays the
// frames back SLOW times faster: smooth motion at the real speed. Then:
//   ffmpeg -f concat -safe 0 -i <frames>/list.txt -vf "fps=30,format=yuv420p" ... advert.mp4
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const SLOW = 12
const [port, adUrl, dir] = process.argv.slice(2)
rmSync(dir, { recursive: true, force: true })
mkdirSync(dir, { recursive: true })

const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json()
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((r) => ws.addEventListener('open', r))
let id = 0
const pending = new Map()
const frames = []
let recording = false
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pending.has(m.id)) {
    pending.get(m.id)(m)
    pending.delete(m.id)
  } else if (m.method === 'Page.screencastFrame') {
    ws.send(JSON.stringify({ id: ++id, method: 'Page.screencastFrameAck', params: { sessionId: m.params.sessionId } }))
    if (!recording) return
    const name = `f${String(frames.length).padStart(5, '0')}.jpg`
    writeFileSync(join(dir, name), Buffer.from(m.params.data, 'base64'))
    frames.push({ name, t: m.params.metadata.timestamp })
  }
})
const send = (method, params = {}) =>
  new Promise((resolve) => {
    const n = ++id
    pending.set(n, resolve)
    ws.send(JSON.stringify({ id: n, method, params }))
  })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const evaluate = async (expression) =>
  (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.result?.value
const slowDown = async () => {
  await send('Animation.enable')
  await send('Animation.setPlaybackRate', { playbackRate: 1 / SLOW })
}

// Film time: every wait and movement below is in seconds of the finished video.
let filmStart = 0
const filmNow = () => (Date.now() - filmStart) / 1000 / SLOW
const until = async (second) => sleep(Math.max(0, (second - filmNow()) * 1000 * SLOW))
const ease = (k) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2)

// Moves follow the clock, not a count of steps, so each scene lasts
// exactly as long as planned however long each step takes.
async function along(seconds, step) {
  const start = filmNow()
  for (;;) {
    const k = Math.min(1, (filmNow() - start) / seconds)
    await step(ease(k))
    if (k >= 1) return
    await sleep(40)
  }
}

let pointerAt = { x: 760, y: 1560 }
async function moveTo(x, y, seconds) {
  const from = { ...pointerAt }
  await along(seconds, async (k) => {
    const px = from.x + (x - from.x) * k
    const py = from.y + (y - from.y) * k
    await evaluate(`pointer(${px}, ${py}); true`)
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: px, y: py, pointerType: 'mouse' })
  })
  pointerAt = { x, y }
}
async function click() {
  await evaluate('clickMark(); true')
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: pointerAt.x, y: pointerAt.y, button: 'left', clickCount: 1 })
  await sleep(40)
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: pointerAt.x, y: pointerAt.y, button: 'left', clickCount: 1 })
}
async function scrollSite(toExpr, seconds) {
  const from = await evaluate(`document.getElementById('site').contentWindow.scrollY`)
  const to = await evaluate(`(() => { const w = document.getElementById('site').contentWindow; return Math.min(${toExpr}, w.document.documentElement.scrollHeight - w.innerHeight) })()`)
  await along(seconds, (k) => evaluate(`document.getElementById('site').contentWindow.scrollTo(0, ${from + (to - from) * k}); true`))
}
const place = (selector, fx = 0.5, fy = 0.5, index = 0) => evaluate(`placeOf(${JSON.stringify(selector)}, ${fx}, ${fy}, ${index})`)

// The page, sharp: drawn at twice the size and filmed at 1080 x 1920.
await send('Page.enable')
await send('Emulation.setDeviceMetricsOverride', { width: 1080, height: 1920, deviceScaleFactor: 2, mobile: false })
await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] })
await send('Page.navigate', { url: adUrl })
await sleep(4000)
await evaluate(`Promise.all(['400 40px Montserrat', '500 40px Montserrat', '600 80px Montserrat'].map((f) => document.fonts.load(f))).then(() => true)`)
await send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: 1080, maxHeight: 1920, everyNthFrame: 1 })
await slowDown()
await sleep(500)
recording = true
filmStart = Date.now()

// 0 s: the name.
await evaluate(`document.body.classList.add('s-intro'); true`)
await until(2.4)
// 2.4 s: the website starts loading behind the name, so its dawn entrance
// is playing as the window rises.
await evaluate('openSite(); true')
for (const wait of [40, 80, 150, 300, 600]) {
  await sleep(wait)
  await slowDown()
}
await until(3.0)
await evaluate(`document.body.classList.add('s-intro-out', 's-site'); caption('cap-site'); true`)

// 7 s: down to the timber.
await until(7.0)
await evaluate(`caption('cap-shop'); document.getElementById('chips').classList.add('on'); true`)
await scrollSite(`w.document.getElementById('products').getBoundingClientRect().top + w.scrollY - 104`, 1.8)

// 11 s: closer on the first card; the pointer picks 3 and goes to Order now.
await until(11.0)
await evaluate(`caption('cap-buy'); true`)
const card = await place('.product-card--shop', 0.5, 0.62, 0)
await evaluate(`camera(1.75, ${card.inX}, ${card.inY}, 500, 330); true`)
await until(12.5)
await evaluate('pointer(760, 1560, true); true')
await until(12.8)
const plus = await place('.card-buy .qty-stepper__btn', 0.5, 0.5, 1)
await moveTo(plus.x, plus.y, 0.8)
await until(13.7)
await click()
await until(14.25)
await click()
await until(14.9)
const order = await place('.card-buy__order', 0.42, 0.5, 0)
await moveTo(order.x, order.y, 0.8)
await until(16.6)
await evaluate(`pointer(${order.x}, ${order.y}, false); true`)

// 17 s: over to the Featured tree; its video plays inside the soil.
await until(17.0)
await evaluate(`caption('cap-featured'); true`)
const tree = await place('.featured-sapling__plant', 0.5, 0.5, 0)
if (tree) {
  await evaluate(`camera(1.5, ${tree.inX}, ${tree.inY}, 640, 318); true`)
  await evaluate(`(() => { const v = document.getElementById('site').contentDocument.querySelector('.featured-sapling video'); if (v) v.playbackRate = Math.max(0.0625, 1 / ${SLOW}); return true })()`)
  await until(18.5)
  const soil = await place('.featured-sapling__roots', 0.5, 0.62, 0)
  pointerAt = { x: soil.x + 260, y: soil.y + 260 }
  await evaluate(`pointer(${pointerAt.x}, ${pointerAt.y}, true); true`)
  await moveTo(soil.x, soil.y, 0.9)
  await until(22.6)
  await evaluate(`pointer(${soil.x}, ${soil.y}, false); true`)
}

// 23.2 s: where to order.
await until(23.2)
await evaluate(`document.body.classList.add('s-outro'); true`)
await until(30.0)

recording = false
await send('Page.stopScreencast')
const lines = []
frames.forEach((frame, i) => {
  const next = frames[i + 1]
  const duration = next ? Math.max(0.001, (next.t - frame.t) / SLOW) : 0.5
  lines.push(`file '${frame.name}'`, `duration ${duration.toFixed(4)}`)
})
if (frames.length) lines.push(`file '${frames[frames.length - 1].name}'`)
writeFileSync(join(dir, 'list.txt'), lines.join('\n') + '\n')
const span = frames.length ? (frames.at(-1).t - frames[0].t) / SLOW : 0
console.log(`${frames.length} frames, ${span.toFixed(1)} s of film, ${(frames.length / span).toFixed(1)} frames a second`)
await fetch(`http://127.0.0.1:${port}/json/close/${target.id}`)
ws.close()
