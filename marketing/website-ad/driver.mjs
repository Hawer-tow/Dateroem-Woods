// Shared by record-short.mjs and record-long.mjs (25 October 2026): films
// ad.html scene by scene, and plays the customer in the website window.
//
// Slow motion: headless browsers send only a few frames a second, so the
// page runs SLOW times slower - its animations, the scrolling, the pointer,
// the typing and any video - and list.txt plays the frames back SLOW times
// faster. Every wait and movement below is in seconds of the finished video
// and follows the clock, so scenes keep their length.
//
// The sample order: the window shows the live shop (live-data.vite.config.
// mjs), but nothing is ever written to it. Signing in, placing the order,
// the payment and the order pages are answered here with a sample customer
// and order; the assistant's answers are real (asked as a visitor); every
// other request that would change something is refused.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const LIVE = 'https://dateroemwoods.co.ke/svc'

// The sample customer and order shown in the videos - made up.
export const SAMPLE = {
  userName: 'grace.w',
  password: 'forest2026',
  name: 'Grace Wanjiru',
  phone: '0712345678',
  ticket: 'DW-4M8T2R',
  orderId: 9001,
}

export async function startFilming({ port, url, dir, slow }) {
  const SLOW = slow
  rmSync(dir, { recursive: true, force: true })
  mkdirSync(dir, { recursive: true })

  // The live shop's products and pickup areas, for the sample order.
  const products = await (await fetch(`${LIVE}/products`)).json()
  const areas = await (await fetch(`${LIVE}/shipping/areas`)).json()
  const today = new Date()
  const iso = (d) => d.toISOString().slice(0, 10)
  const eta = new Date(today.getTime() + 21 * 86400000)

  const order = { items: [], total: 0, area: areas[0], paid: false }
  let releaseCheckout = null
  let checkoutHeld = null

  function orderJson() {
    return {
      orderId: SAMPLE.orderId,
      ticketCode: SAMPLE.ticket,
      total: order.total,
      status: order.paid ? 'PAID' : 'PENDING',
      createdAt: iso(today),
      hotspotCity: order.area?.city,
      userId: 9001,
      areaName: order.area?.areaName,
      deliveryCity: order.area?.city,
      deliveryStatus: order.paid ? 'PENDING' : null,
      deliveredAt: null,
      estimatedDeliveryDate: order.paid ? iso(eta) : null,
      vehicleIdentifier: null,
      driverFirstName: null,
      isScheduled: 0,
      needsReassignment: 0,
      paymentStatus: order.paid ? 'COMPLETED' : 'PENDING',
      items: order.items,
      customerOrderNumber: 1,
    }
  }

  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json()
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((r) => ws.addEventListener('open', r))
  let id = 0
  const pending = new Map()
  const frames = []
  let recording = false
  const send = (method, params = {}) =>
    new Promise((resolve) => {
      const n = ++id
      pending.set(n, resolve)
      ws.send(JSON.stringify({ id: n, method, params }))
    })

  async function onRequest({ requestId, request }) {
    const path = new URL(request.url).pathname.replace(/^\/svc/, '')
    const method = request.method
    const reply = (body, status = 200) =>
      send('Fetch.fulfillRequest', {
        requestId,
        responseCode: status,
        responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
        body: Buffer.from(JSON.stringify(body)).toString('base64'),
      })
    try {
      if (method === 'POST' && path === '/auth/login') {
        return reply({
          token: 'sample-token-for-the-advert',
          userId: 9001,
          userName: SAMPLE.userName,
          name: SAMPLE.name,
          roleId: 0,
          roleName: 'Customer',
          canSyncSms: false,
          theme: 'forest',
          mustChangePassword: false,
          idleLockMinutes: 25,
        })
      }
      if (method === 'GET' && path === '/auth/me') {
        return reply({
          userId: 9001,
          userName: SAMPLE.userName,
          name: SAMPLE.name,
          phoneNo: SAMPLE.phone,
          phoneCodeRequired: false,
          photoUrl: null,
          theme: 'forest',
          mustChangePassword: false,
        })
      }
      if (path.startsWith('/auth/me/') && method !== 'GET') return reply({ ok: true })
      if (method === 'POST' && path === '/orders/checkout') {
        const body = JSON.parse(request.postData || '{}')
        order.area = areas.find((a) => String(a.deliveryAreaId) === String(body.delivery_area_id)) || order.area
        order.items = (body.items || []).map(({ product_id: pid, qty }) => {
          const p = products.find((x) => x.productId === pid) || {}
          return { qty, unitPrice: p.price, productId: pid, name: p.name, type: p.type, productUnit: p.unit, image: p.image }
        })
        order.total = order.items.reduce((sum, item) => sum + item.unitPrice * item.qty, 0)
        // Held until the film has shown the phone prompt (releaseCheckout).
        await checkoutHeld
        return reply({ orderId: SAMPLE.orderId, stkPushSent: true, stkPushError: null, ticketCode: SAMPLE.ticket })
      }
      if (path === '/orders/mine') return reply([orderJson()])
      if (path === '/orders/track') {
        const o = orderJson()
        return reply({
          ticketCode: o.ticketCode,
          stage: 'PREPARING',
          placedOn: o.createdAt,
          estimatedDeliveryDate: iso(eta),
          deliveredAt: null,
          areaName: o.areaName,
          deliveryCity: o.deliveryCity,
          total: o.total,
          items: o.items,
          driverFirstName: null,
          vehicleIdentifier: null,
        })
      }
      if (/^\/orders\/\d+$/.test(path)) return reply(orderJson())
      if (path === '/reviews/mine') return reply([])
      if (path === '/ai/chat/history') return reply([])
      if (method === 'POST' && path === '/ai/chat') {
        // A real answer from the live assistant, asked as a visitor.
        const headers = Object.entries(request.headers)
          .filter(([name]) => !/^(authorization|x-actor)/i.test(name))
          .map(([name, value]) => ({ name, value }))
        return send('Fetch.continueRequest', { requestId, headers })
      }
      if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        return reply({ detail: 'Not available in the advert' }, 403)
      }
      return send('Fetch.continueRequest', { requestId })
    } catch (err) {
      console.error('request handling failed', path, err.message)
      return send('Fetch.continueRequest', { requestId }).catch(() => {})
    }
  }

  ws.addEventListener('message', (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) {
      pending.get(m.id)(m)
      pending.delete(m.id)
    } else if (m.method === 'Fetch.requestPaused') {
      onRequest(m.params)
    } else if (m.method === 'Page.screencastFrame') {
      ws.send(JSON.stringify({ id: ++id, method: 'Page.screencastFrameAck', params: { sessionId: m.params.sessionId } }))
      if (!recording) return
      const name = `f${String(frames.length).padStart(5, '0')}.jpg`
      writeFileSync(join(dir, name), Buffer.from(m.params.data, 'base64'))
      frames.push({ name, t: m.params.metadata.timestamp })
    }
  })

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
  const ev = async (expression) =>
    (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.result?.value
  const slowDown = async () => {
    await send('Animation.enable')
    await send('Animation.setPlaybackRate', { playbackRate: 1 / SLOW })
  }

  let filmStart = 0
  const filmNow = () => (Date.now() - filmStart) / 1000 / SLOW
  const until = (second) => sleep(Math.max(0, (second - filmNow()) * 1000 * SLOW))
  const hold = (seconds) => until(filmNow() + seconds)
  const ease = (k) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2)
  async function along(seconds, step) {
    const start = filmNow()
    for (;;) {
      const k = Math.min(1, (filmNow() - start) / seconds)
      await step(ease(k))
      if (k >= 1) return
      await sleep(40)
    }
  }

  // The page, sharp: drawn at twice the size, filmed at 1080 x 1920.
  await send('Page.enable')
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/svc/*', requestStage: 'Request' }] })
  await send('Emulation.setDeviceMetricsOverride', { width: 1080, height: 1920, deviceScaleFactor: 2, mobile: false })
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] })
  await send('Page.navigate', { url })
  await sleep(4000)
  await ev(`Promise.all(['400 40px Montserrat', '500 40px Montserrat', '600 80px Montserrat'].map((f) => document.fonts.load(f))).then(() => true)`)
  await send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: 1080, maxHeight: 1920, everyNthFrame: 1 })
  await slowDown()
  await sleep(500)
  recording = true
  filmStart = Date.now()

  let pointerAt = { x: 760, y: 1560 }
  const film = {
    SLOW,
    ev,
    hold,
    until,
    along,
    filmNow,
    body: (...classes) => ev(`document.body.classList.add(${classes.map((c) => JSON.stringify(c)).join(',')}); true`),
    hook: (text) => ev(`hook(${JSON.stringify(text)}); true`),
    say: (top, bottom = '') => ev(`say(${JSON.stringify(top)}, ${JSON.stringify(bottom)}); true`),
    chapter: (text) => ev(`chapter(${JSON.stringify(text)}); true`),
    chips: (list) => ev(`chips(${JSON.stringify(list)}); true`),
    stk: (amount) => ev(`stk(${JSON.stringify(amount)}); true`),
    stkPin: (n) => ev(`stkPin(${n}); true`),
    stkSend: () => ev('stkSend(); true'),
    outro: () => ev(`document.body.classList.add('s-outro'); true`),

    // The website: open it (a fresh visit), follow a link, scroll, click.
    async openSite(path = '/') {
      await ev(`openSite(${JSON.stringify(path)}); true`)
      for (const wait of [40, 80, 150, 300, 600, 1200]) {
        await sleep(wait)
        await slowDown()
      }
    },
    async follow(href) {
      await ev(`follow(${JSON.stringify(href)})`)
      await sleep(300)
      await film.slowVideos()
    },
    // Another page, without a visible jump: the window fades out, the camera
    // goes back to the whole page, the link is followed and the page placed
    // at the right section while it can't be seen, then it fades back in.
    async goTo(href, { to = null, text = '', room = 120 } = {}) {
      await ev(`document.getElementById('site').style.opacity = '0'; true`)
      await hold(0.5)
      await ev(`(() => { const s = document.getElementById('site'); s.style.transition = 'none'; camera(0); void s.offsetWidth; s.style.transition = ''; return true })()`)
      await film.follow(href)
      await sleep(500)
      if (to) await film.jump(to, text, room)
      await sleep(300)
      await ev(`document.getElementById('site').style.opacity = '1'; true`)
      await hold(0.5)
    },
    async jump(selector, text = '', room = 104) {
      const y = await ev(`siteTop(${JSON.stringify(selector)}, ${JSON.stringify(text)}, ${room})`)
      if (y !== null) await ev(`siteScrollTo(${y}); true`)
    },
    async scrollTo(selectorOrY, seconds, text = '', room = 104) {
      const from = await ev('siteScrollY()')
      let to = typeof selectorOrY === 'number' ? selectorOrY : await ev(`siteTop(${JSON.stringify(selectorOrY)}, ${JSON.stringify(text)}, ${room})`)
      if (to === null || to === undefined) return
      to = Math.max(0, Math.min(to, await ev('siteMaxScroll()')))
      await along(seconds, (k) => ev(`siteScrollTo(${from + (to - from) * k}); true`))
    },
    async scrollBy(dy, seconds) {
      await film.scrollTo((await ev('siteScrollY()')) + dy, seconds)
    },
    place: (selector, fx = 0.5, fy = 0.5, index = 0, text = '') =>
      ev(`placeOf(${JSON.stringify(selector)}, ${fx}, ${fy}, ${index}, ${JSON.stringify(text)})`),
    cam: (s, x = 0, y = 0, cx = 500, cy = 312) => ev(`camera(${s}, ${x}, ${y}, ${cx}, ${cy}); true`),
    async camOn(selector, s, cx = 500, cy = 312, fx = 0.5, fy = 0.5, index = 0, text = '') {
      const p = await film.place(selector, fx, fy, index, text)
      if (p) await film.cam(s, p.inX, p.inY, cx, cy)
      return p
    },
    async slowVideos() {
      await ev(`(() => { const d = document.getElementById('site').contentDocument; d && d.querySelectorAll('video').forEach((v) => { v.playbackRate = Math.max(0.0625, 1 / ${SLOW}) }); return true })()`)
    },

    // The pointer.
    async pointerAt(x, y) {
      pointerAt = { x, y }
      await ev(`pointer(${x}, ${y}, true); true`)
    },
    pointerOff: () => ev(`pointer(${pointerAt.x}, ${pointerAt.y}, false); true`),
    async moveTo(x, y, seconds) {
      const from = { ...pointerAt }
      await ev(`pointer(${from.x}, ${from.y}, true); true`)
      await along(seconds, async (k) => {
        const px = from.x + (x - from.x) * k
        const py = from.y + (y - from.y) * k
        await ev(`pointer(${px}, ${py}); true`)
        await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: px, y: py, pointerType: 'mouse' })
      })
      pointerAt = { x, y }
    },
    async click() {
      await ev('clickMark(); true')
      await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: pointerAt.x, y: pointerAt.y, button: 'left', clickCount: 1 })
      await sleep(40)
      await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: pointerAt.x, y: pointerAt.y, button: 'left', clickCount: 1 })
    },
    // Moves to a part of the website and clicks it; false if it isn't there.
    async clickOn(selector, { fx = 0.5, fy = 0.5, index = 0, text = '', move = 0.7, after = 0.4 } = {}) {
      const p = await film.place(selector, fx, fy, index, text)
      if (!p) {
        console.warn('not found:', selector, text)
        return false
      }
      await film.moveTo(p.x, p.y, move)
      await film.click()
      await hold(after)
      return true
    },
    // Types into the focused field, a letter at a time.
    async type(text, seconds) {
      const each = seconds / Math.max(1, text.length)
      for (const ch of text) {
        await send('Input.insertText', { text: ch })
        await hold(each)
      }
    },
    // Waits (in real time, film running) until something shows on the website.
    async waitFor(expression, maxSeconds = 60) {
      const end = Date.now() + maxSeconds * 1000
      while (Date.now() < end) {
        if (await ev(`(() => { const d = document.getElementById('site').contentDocument; return Boolean(${expression}) })()`)) return true
        await sleep(200)
      }
      console.warn('waited in vain for', expression)
      return false
    },

    // The sample order: the checkout reply is held back until the phone
    // prompt has been shown, and the payment confirms when markPaid() runs.
    holdCheckout() {
      checkoutHeld = new Promise((r) => {
        releaseCheckout = r
      })
    },
    releaseCheckout: () => releaseCheckout?.(),
    markPaid() {
      order.paid = true
    },
    sampleAmount: () => `KES ${order.total.toLocaleString('en-KE')}`,
    productById: (pid) => products.find((p) => p.productId === pid),

    async finish() {
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
    },
  }
  return film
}
