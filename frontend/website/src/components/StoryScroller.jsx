import { useEffect, useRef, useState } from 'react'
import { STORY_STAGES } from '../core/aboutStory.js'

// The About page's journey: the animation's frames are drawn onto a canvas
// that stays pinned on screen while the section scrolls past, so scrolling
// plays the film forwards and backwards. Each fifth of the scroll is one
// stage (0-20% acquisition, 21-40% harvest ... 81-100% delivery); its card
// flies in exactly when its clip begins.
//
// Step by step: "if you scroll you will move from the order of company
// functions one by one" - inside the story a wheel flick, a swipe or an
// arrow key glides straight on to the next stage, its clip playing on the
// way, and rests there with the card up. The timeline dots glide the same
// way. Up from the first stage or down from the last, the page scrolls on
// as normal.
//
// "Incredibly smooth": nothing waits and nothing jolts.
//   - A glide starts the moment the visitor asks (no pause for scrolling to
//     stop first) and carries on from the speed they were already moving
//     at - a new flick mid-glide simply extends it.
//   - On phones the finger moves the page 1:1 and lets go into the glide at
//     the finger's own speed.
//   - Frames are decoded off the main thread ahead of where the film is
//     heading, so drawing one never stalls a frame; small copies of the
//     whole film stand in if a fast glide outruns them.
//   - Neighbouring frames are blended, and scenes change like a film cut:
//     the old one drifts closer and dims as the new one settles in.

const FRAMES_URL = `${import.meta.env.BASE_URL}story/`
// Share of each stage's scroll spent dissolving into the next clip.
const DISSOLVE = 0.14
// The last clip reaches its final frame a little early and holds it.
const FINAL_HOLD = 0.12
const PARALLEL_LOADS = 6
// Preparing a frame (decoding it) is the one slow part - 50-100 ms each on
// an ordinary laptop, against 1-2 ms to draw one that's ready. So it all
// happens while the story rests on a stage, never during a glide: at rest
// the whole next step (its clip and the cut into it) is made ready.
const PARALLEL_DECODES = 3
const DECODES_WHILE_GLIDING = 1
// Full-size frames kept ready around the current one: a whole step ahead
// in the direction the film is heading, a little behind.
const READY_AHEAD = 70
const READY_BEHIND = 16
// Every Nth frame is also kept as a small copy, for the whole film.
const SKETCH_EVERY = 6
const SKETCH_WIDTH = 480
// How long gliding through one stage takes - long enough to see its clip.
const STEP_MS = 1600
// Wheel events closer together than this are one flick.
const WHEEL_GAP_MS = 220
// A lifted finger coasts on like a normal fling past the story's ends.
const COAST_MS = 325
const SCROLL_KEYS = new Set(['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', ' ', 'Spacebar'])

export default function StoryScroller() {
  const sectionRef = useRef(null)
  const stickyRef = useRef(null)
  const canvasRef = useRef(null)
  const railFillRef = useRef(null)
  const loadBarRef = useRef(null)
  const glideToStageRef = useRef(null)
  const [stage, setStage] = useState(0)
  const [started, setStarted] = useState(false)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const stageCount = STORY_STAGES.length

  useEffect(() => {
    const section = sectionRef.current
    const sticky = stickyRef.current
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const connection = navigator.connection
    const saveData = Boolean(connection && (connection.saveData || /2g$/.test(connection.effectiveType || '')))
    const aborter = typeof AbortController !== 'undefined' ? new AbortController() : null

    let cancelled = false
    let stages = []
    let count = 0
    let set = null
    let progress = 0
    let stickyTop = 0
    let currentStage = -1
    let startedOnce = false
    let drawnOnce = false
    let raf = 0

    // ----- Frames: downloaded (small files) -> decoded (ready to draw) -----
    let blobs = []
    let requested = new Uint8Array(0)
    let staticOrder = []
    let staticNext = 0
    let sketchList = []
    let downloads = 0
    let finished = 0
    let planned = 1
    const decoded = new Map()
    const sketches = new Map()
    const decoding = new Set()
    const sketching = new Set()
    const broken = new Set()
    let focusFrame = 0
    let heading = 1

    // Pins just below the navbar - its bar only, not the phone menu when
    // that's open (the menu drops over the page, the story shouldn't jump).
    function measureNavbar() {
      const nav = document.querySelector('.navbar')
      if (!nav) return
      const row = nav.querySelector('.navbar__row')
      const links = nav.querySelector('.navbar__links')
      const linksShown = links && !links.classList.contains('navbar__links--open') ? links.offsetHeight : 0
      section.style.setProperty('--story-top', `${(row ? row.offsetHeight : nav.offsetHeight) + linksShown}px`)
    }

    function position() {
      const rect = section.getBoundingClientRect()
      const travel = rect.height - sticky.offsetHeight
      return { rect, travel, p: travel > 0 ? (stickyTop - rect.top) / travel : 0 }
    }

    function stageOfFrame(index) {
      const found = stages.findIndex((s) => index >= s.start && index <= s.end)
      return found < 0 ? 0 : found
    }

    function focusOf(index) {
      return STORY_STAGES[stageOfFrame(index)]?.focusX ?? 0.5
    }

    // Scroll progress -> which frames to show and how much of each. The
    // film only moves with the visitor's own scrolling (or a glide they
    // asked for), so it plays with reduced motion on too.
    function framesAt(p) {
      const n = stages.length
      const x = Math.min(p * n, n - 1e-6)
      const i = Math.floor(x)
      const t = x - i
      const { start, end } = stages[i]
      if (i < n - 1 && t > 1 - DISSOLVE) {
        const k = (t - (1 - DISSOLVE)) / DISSOLVE
        return { a: end, b: stages[i + 1].start, mix: k * k * (3 - 2 * k), cut: true }
      }
      const play = i < n - 1 ? 1 - DISSOLVE : 1 - FINAL_HOLD
      const f = start + Math.min(t / play, 1) * (end - start)
      const a = Math.floor(f)
      return { a, b: Math.min(a + 1, end), mix: f - a, cut: false }
    }

    // The frame ready to draw, or the closest one of the same clip that is
    // (never one from the neighbouring scene): full size if one is within a
    // couple of frames, otherwise the nearest small copy.
    function drawableNear(index) {
      const s = stages[stageOfFrame(index)]
      let sketch = null
      for (let d = 0; d <= s.end - s.start; d += 1) {
        for (const i of d ? [index - d, index + d] : [index]) {
          if (i < s.start || i > s.end) continue
          const full = decoded.get(i)
          if (full && (d <= 2 || !sketch)) return full
          if (!sketch && sketches.has(i)) sketch = sketches.get(i)
        }
        if (sketch && d >= 2) return sketch
      }
      return sketch
    }

    // "Cover" the canvas like a background photo; on a narrow screen keep
    // the part of the picture the stage's focusX points at. zoom > 1 moves
    // the camera in on the middle.
    function drawCover(img, focusX, zoom) {
      const cw = canvas.width
      const ch = canvas.height
      const iw = img.naturalWidth || img.width
      const ih = img.naturalHeight || img.height
      let sw = iw
      let sh = ih
      let sx = 0
      let sy = 0
      if (iw / ih > cw / ch) {
        sw = ih * (cw / ch)
        sx = (iw - sw) * focusX
      } else {
        sh = iw / (cw / ch)
        sy = (ih - sh) / 2
      }
      if (zoom !== 1) {
        sx += (sw - sw / zoom) / 2
        sy += (sh - sh / zoom) / 2
        sw /= zoom
        sh /= zoom
      }
      ctx.drawImage(img, sx, sy, sw, sh, 0, 0, cw, ch)
    }

    function draw({ a, b, mix, cut }) {
      if (!canvas.width) return
      const A = drawableNear(a)
      if (!A) return
      const B = mix > 0.004 ? drawableNear(b) : null
      ctx.globalAlpha = 1
      if (cut) {
        // Between scenes, like a film cut: the old one drifts closer and
        // dims while the new one settles back into place as it fades up.
        drawCover(A, focusOf(a), 1 + 0.05 * mix)
        if (B) {
          ctx.globalAlpha = mix
          drawCover(B, focusOf(b), 1.07 - 0.07 * mix)
        }
        ctx.globalAlpha = 0.42 * Math.sin(Math.PI * mix)
        ctx.fillStyle = '#07120c'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
      } else {
        drawCover(A, focusOf(a), 1)
        if (B && B !== A) {
          ctx.globalAlpha = mix
          drawCover(B, focusOf(b), 1)
        }
      }
      ctx.globalAlpha = 1
      if (!drawnOnce) {
        drawnOnce = true
        setReady(true)
      }
    }

    function render() {
      const { p } = position()
      progress = Math.min(Math.max(p, 0), 1)
      // Dot i is reached as stage i begins; the line is full once the last
      // stage starts.
      if (railFillRef.current) {
        railFillRef.current.style.transform = `scaleX(${Math.min((progress * stageCount) / (stageCount - 1), 1)})`
      }
      // The card (and the timeline's dot) changes halfway through the cut,
      // the moment the new scene takes over the picture.
      const i = Math.min(Math.floor(progress * stageCount + DISSOLVE / 2), stageCount - 1)
      if (i !== currentStage) {
        currentStage = i
        setStage(i)
      }
      if (!startedOnce && progress > 0.004) {
        startedOnce = true
        setStarted(true)
      }
      if (!count) return
      const frames = framesAt(progress)
      if (frames.a !== focusFrame) {
        heading = frames.a > focusFrame ? 1 : -1
        focusFrame = frames.a
      }
      keepReady()
      pump()
      draw(frames)
    }

    // ----- Decoding: off the main thread, ahead of the film -----
    function toDrawable(blob, width) {
      if (typeof window.createImageBitmap === 'function') {
        return width
          ? window.createImageBitmap(blob, { resizeWidth: width, resizeQuality: 'medium' })
          : window.createImageBitmap(blob)
      }
      return new Promise((resolve, reject) => {
        const url = URL.createObjectURL(blob)
        const img = new Image()
        img.onload = () => {
          URL.revokeObjectURL(url)
          resolve(img)
        }
        img.onerror = () => {
          URL.revokeObjectURL(url)
          reject(new Error('frame'))
        }
        img.src = url
      })
    }

    function release(frame) {
      frame?.close?.()
    }

    function decode(index, small) {
      const busy = small ? sketching : decoding
      busy.add(index)
      toDrawable(blobs[index], small ? SKETCH_WIDTH : 0).then(
        (frame) => {
          busy.delete(index)
          if (cancelled) {
            release(frame)
            return
          }
          const store = small ? sketches : decoded
          release(store.get(index))
          store.set(index, frame)
          schedule()
        },
        () => {
          busy.delete(index)
          broken.add(index)
          if (!cancelled) keepReady()
        },
      )
    }

    function nextSketch() {
      for (const i of sketchList) {
        if (blobs[i] && !sketches.has(i) && !sketching.has(i) && !broken.has(i)) return i
      }
      return -1
    }

    function keepReady() {
      if (!count) return
      const lo = Math.max(0, focusFrame - (heading > 0 ? READY_BEHIND : READY_AHEAD))
      const hi = Math.min(count - 1, focusFrame + (heading > 0 ? READY_AHEAD : READY_BEHIND))
      // Far behind now - free the memory.
      for (const [i, frame] of decoded) {
        if (i < lo - 4 || i > hi + 4) {
          release(frame)
          decoded.delete(i)
        }
      }
      // Mid-glide the processor is needed for drawing - one at a time then.
      const slots = glide || touch ? DECODES_WHILE_GLIDING : PARALLEL_DECODES
      while (decoding.size + sketching.size < slots) {
        let best = -1
        let bestScore = Infinity
        for (let i = lo; i <= hi; i += 1) {
          if (!blobs[i] || decoded.has(i) || decoding.has(i) || broken.has(i)) continue
          const score = Math.abs(i - focusFrame) * ((i - focusFrame) * heading >= 0 ? 1 : 1.6)
          if (score < bestScore) {
            bestScore = score
            best = i
          }
        }
        if (best >= 0) {
          decode(best, false)
          continue
        }
        // Nothing near is waiting - fill in the small copies, one slot kept free.
        if (glide || touch || decoding.size + sketching.size >= PARALLEL_DECODES - 1) break
        const sketch = nextSketch()
        if (sketch < 0) break
        decode(sketch, true)
      }
    }

    // ----- Downloading: what's about to be seen first, then the rest -----
    function frameUrl(index) {
      return `${FRAMES_URL}${set.key}/${String(index + 1).padStart(4, '0')}.${set.ext}`
    }

    function pickDownload() {
      for (let d = 0; d <= READY_AHEAD; d += 1) {
        const ahead = focusFrame + d * heading
        if (ahead >= 0 && ahead < count && !requested[ahead]) return ahead
        const behind = focusFrame - d * heading
        if (d <= READY_BEHIND && behind >= 0 && behind < count && !requested[behind]) return behind
      }
      while (staticNext < staticOrder.length) {
        const index = staticOrder[staticNext]
        staticNext += 1
        if (!requested[index]) return index
      }
      return -1
    }

    function showLoading() {
      const bar = loadBarRef.current
      if (!bar) return
      bar.style.transform = `scaleX(${Math.min(finished / planned, 1)})`
      if (staticNext >= staticOrder.length && downloads === 0) bar.parentElement.classList.add('story__loading--done')
    }

    function pump() {
      while (downloads < PARALLEL_LOADS) {
        const index = pickDownload()
        if (index < 0) return
        requested[index] = 1
        downloads += 1
        fetch(frameUrl(index), aborter ? { signal: aborter.signal } : undefined)
          .then((response) => (response.ok ? response.blob() : null))
          .catch(() => null)
          .then((blob) => {
            downloads -= 1
            if (cancelled) return
            if (blob) blobs[index] = blob
            finished += 1
            showLoading()
            keepReady()
            pump()
          })
      }
    }

    // An AVIF the browser can't read fails to decode - then use the WebP set.
    async function readable(index) {
      try {
        const response = await fetch(frameUrl(index), aborter ? { signal: aborter.signal } : undefined)
        if (!response.ok) return false
        const blob = await response.blob()
        release(await toDrawable(blob, 0))
        blobs[index] = blob
        requested[index] = 1
        finished += 1
        return true
      } catch {
        return false
      }
    }

    async function start() {
      let manifest
      try {
        const response = await fetch(`${FRAMES_URL}manifest.json`, aborter ? { signal: aborter.signal } : undefined)
        if (!response.ok) throw new Error(`manifest ${response.status}`)
        manifest = await response.json()
      } catch {
        if (!cancelled) {
          detach()
          setFailed(true)
        }
        return
      }
      if (cancelled) return
      stages = manifest.stages.slice(0, stageCount)
      const total = manifest.count
      blobs = new Array(total).fill(null)
      requested = new Uint8Array(total)
      // Phones (and data savers) get the small frames, wider screens the big ones.
      const narrow = window.matchMedia('(max-width: 720px)').matches
      const key = narrow || saveData ? 'sm' : 'lg'
      set = { key, ...manifest.sets[key] }
      if (set.ext === 'avif' && manifest.sets.fb && !(await readable(stages[0].start))) {
        if (cancelled) return
        blobs.fill(null)
        requested.fill(0)
        finished = 0
        set = { key: 'fb', ...manifest.sets.fb }
      }
      if (cancelled) return

      const seen = new Set()
      const add = (list, index) => {
        if (!seen.has(index)) {
          seen.add(index)
          list.push(index)
        }
      }
      stages.forEach((s) => {
        add(sketchList, s.start)
        add(sketchList, s.end)
      })
      for (let i = 0; i < total; i += SKETCH_EVERY) add(sketchList, i)
      staticOrder = [...sketchList]
      if (!saveData) {
        for (let i = 0; i < total; i += 3) add(staticOrder, i)
        for (let i = 0; i < total; i += 1) add(staticOrder, i)
      }
      planned = saveData ? staticOrder.length : total
      count = total
      resize()
      pump()
      schedule()
    }

    // ----- Motion -----
    let glide = null
    let prevSample = null
    let lastSample = null

    function tick(now) {
      raf = 0
      if (glide) advanceGlide(now)
      render()
      if (glide) raf = window.requestAnimationFrame(tick)
    }

    function schedule() {
      if (!raf) raf = window.requestAnimationFrame(tick)
    }

    function onScroll() {
      prevSample = lastSample
      lastSample = { y: window.scrollY, t: performance.now() }
      schedule()
    }

    // How fast the page is already moving (px per ms), so a glide picks
    // up from it instead of jolting.
    function currentVelocity(now) {
      if (glide && glide.kind === 'step') {
        const s = Math.min((now - glide.t0) / glide.T, 1)
        return (glide.delta * (6 * s - 6 * s * s) + glide.m0 * (3 * s * s - 4 * s + 1)) / glide.T
      }
      if (!prevSample || !lastSample || now - lastSample.t > 100 || lastSample.t <= prevSample.t) return 0
      return (lastSample.y - prevSample.y) / (lastSample.t - prevSample.t)
    }

    // Glides the page to a point of the story: an ease in and out over
    // about STEP_MS a stage, starting at the speed it was already moving.
    function glideTo(targetP, velocity) {
      const { rect, travel } = position()
      if (travel <= 0) return
      const now = performance.now()
      const v = velocity ?? currentVelocity(now)
      const y0 = window.scrollY
      const delta = rect.top - stickyTop + targetP * travel
      if (Math.abs(delta) < 1) {
        glide = null
        return
      }
      if (reduceMotion) {
        glide = null
        window.scrollTo(0, y0 + delta)
        return
      }
      const T = Math.min(Math.max((STEP_MS * Math.abs(delta)) / (travel / stageCount), 500), STEP_MS * 1.8)
      // Carried-over speed, never enough to overshoot the stage.
      let m0 = v * T
      if (Math.sign(m0) !== Math.sign(delta)) m0 = 0
      m0 = Math.sign(delta) * Math.min(Math.abs(m0), 2.5 * Math.abs(delta))
      // A plain scroll to where the page is stops the browser's own smooth
      // scrolling, so the two never pull against each other.
      if (!glide) window.scrollTo(0, y0)
      glide = { kind: 'step', y0, delta, m0, T, t0: now, lastY: window.scrollY, targetP }
      schedule()
    }

    // Past the story's ends a lifted finger coasts on like a normal fling.
    function coast(v) {
      if (reduceMotion || Math.abs(v) < 0.05) return
      glide = { kind: 'coast', y0: window.scrollY, v, t0: performance.now(), lastY: window.scrollY, targetP: null }
      schedule()
    }

    function advanceGlide(now) {
      const g = glide
      const drift = window.scrollY - g.lastY
      if (Math.abs(drift) > 4) {
        if (g.kind === 'step' && now - g.t0 < 200) {
          // Just started: the browser's own smooth scroll finishing - fold
          // it in and carry on to the same place.
          g.y0 += drift
          g.delta -= drift
        } else {
          // Moved by something else (a scrollbar drag) - that wins.
          glide = null
          return
        }
      }
      let y
      let done
      if (g.kind === 'coast') {
        const t = now - g.t0
        y = g.y0 + g.v * COAST_MS * (1 - Math.exp(-t / COAST_MS))
        done = t > COAST_MS * 6
      } else {
        const s = Math.min(Math.max((now - g.t0) / g.T, 0), 1)
        y = g.y0 + g.delta * (3 * s * s - 2 * s * s * s) + g.m0 * (s * s * s - 2 * s * s + s)
        done = s >= 1
      }
      window.scrollTo(0, y)
      g.lastY = window.scrollY
      if (done) glide = null
    }

    // ----- Step by step -----
    // Where the story comes to rest: its start, then the end of each clip
    // (the stage's card up, just before the dissolve into the next).
    const rests = [0]
    for (let i = 0; i < stageCount; i += 1) {
      const played = i < stageCount - 1 ? 1 - DISSOLVE : 1 - FINAL_HOLD
      rests.push((i + played) / stageCount - 0.001)
    }
    glideToStageRef.current = (index) => glideTo(rests[index + 1])

    function inStory(p) {
      return p > -0.003 && p < 1.003
    }

    function nextRest(from, dir) {
      if (dir > 0) return rests.find((r) => r > from + 0.004) ?? null
      for (let k = rests.length - 1; k >= 0; k -= 1) if (rests[k] < from - 0.004) return rests[k]
      return null
    }

    function nearestRest(p) {
      return rests.reduce((best, r) => (Math.abs(r - p) < Math.abs(best - p) ? r : best))
    }

    // The next stage that way - counted from where a running glide is
    // headed, so each new flick adds one more. null: that way leaves the
    // story (up from its start, down from its end).
    function stepTarget(dir, p) {
      return nextRest(glide && glide.targetP != null ? glide.targetP : p, dir)
    }

    let wheelAt = 0
    let wheelDir = 0
    let wheelAbs = 0
    function onWheel(event) {
      if (event.ctrlKey || !event.deltaY || !section.contains(event.target)) return
      const now = performance.now()
      const dir = Math.sign(event.deltaY)
      const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? window.innerHeight : 1
      const abs = Math.abs(event.deltaY) * unit
      // A new flick: after a pause, the other way, or a trackpad swipe
      // rising out of the drift of the last one.
      const fresh = now - wheelAt > WHEEL_GAP_MS || dir !== wheelDir || abs > wheelAbs * 1.8 + 6
      const drifting = abs < wheelAbs
      wheelAt = now
      wheelDir = dir
      wheelAbs = abs
      const { p } = position()
      if (!glide && !inStory(p)) return
      if (!fresh) {
        // The rest of the same flick.
        if (glide || (drifting && Math.abs(p - nearestRest(p)) < 0.004)) {
          event.preventDefault()
          return
        }
      }
      const target = stepTarget(dir, p)
      if (target == null) {
        glide = null
        return
      }
      event.preventDefault()
      glideTo(target)
    }

    function onKey(event) {
      if (!SCROLL_KEYS.has(event.key) || event.altKey || event.ctrlKey || event.metaKey) return
      if (document.querySelector('.modal-overlay')) return
      const el = event.target
      const tag = el?.tagName
      const space = event.key === ' ' || event.key === 'Spacebar'
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el?.isContentEditable) return
      if (space && (tag === 'BUTTON' || tag === 'A')) return
      const { p } = position()
      if (!glide && !inStory(p)) return
      if (event.repeat && glide) {
        event.preventDefault()
        return
      }
      const up = event.key === 'ArrowUp' || event.key === 'PageUp' || (space && event.shiftKey)
      const target = stepTarget(up ? -1 : 1, p)
      if (target == null) {
        glide = null
        return
      }
      event.preventDefault()
      glideTo(target)
    }

    // Phones: inside the story the finger moves the page itself, 1:1, and
    // lets go into the glide at the finger's own speed - the browser's
    // fling never starts, so nothing fights.
    let touch = null
    function onTouchStart(event) {
      // A finger on a moving page catches it; a plain tap on the story
      // lets it go on (see onTouchEnd).
      const caught = glide && glide.targetP != null ? glide.targetP : null
      glide = null
      touch = null
      if (event.touches.length !== 1 || !section.contains(event.target)) return
      const { p } = position()
      if (caught == null && !inStory(p)) return
      const point = event.touches[0]
      touch = {
        x: point.clientX,
        y: point.clientY,
        scrollY: window.scrollY,
        samples: [{ t: event.timeStamp, y: window.scrollY }],
        moved: false,
        caught,
      }
    }

    function onTouchMove(event) {
      if (!touch) return
      if (event.touches.length !== 1) {
        touch = null
        return
      }
      const point = event.touches[0]
      if (event.cancelable) event.preventDefault()
      if (!touch.moved && Math.abs(touch.y - point.clientY) < 4 && Math.abs(touch.x - point.clientX) < 4) return
      touch.moved = true
      window.scrollTo(0, touch.scrollY + (touch.y - point.clientY))
      touch.samples.push({ t: event.timeStamp, y: window.scrollY })
      if (touch.samples.length > 8) touch.samples.shift()
      schedule()
    }

    function onTouchEnd(event) {
      const held = touch
      touch = null
      if (!held) return
      if (!held.moved) {
        if (held.caught != null) glideTo(held.caught, 0)
        return
      }
      // The finger's speed over its last ~100 ms.
      const recent = held.samples.filter((sample) => event.timeStamp - sample.t < 100)
      const first = recent[0] || held.samples[held.samples.length - 1]
      const last = held.samples[held.samples.length - 1]
      const v = last.t > first.t ? (last.y - first.y) / (last.t - first.t) : 0
      const moved = window.scrollY - held.scrollY
      const dir = Math.abs(v) > 0.2 ? Math.sign(v) : Math.abs(moved) > 40 ? Math.sign(moved) : 0
      const { p } = position()
      // Swiping on while it's still gliding that way adds one more stage,
      // like a second wheel flick.
      let from = p
      if (held.caught != null && dir > 0) from = Math.max(p, held.caught)
      if (held.caught != null && dir < 0) from = Math.min(p, held.caught)
      const target = dir ? nextRest(from, dir) : inStory(p) ? nearestRest(p) : null
      if (target == null) coast(v)
      else glideTo(target, v)
    }

    function resize() {
      measureNavbar()
      stickyTop = parseFloat(window.getComputedStyle(sticky).top) || 0
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      const cssWidth = canvas.clientWidth
      // No sharper than the frames themselves can fill.
      const scale = set && cssWidth ? Math.min(dpr, (set.width * 1.5) / cssWidth) : dpr
      const width = Math.round(cssWidth * scale)
      const height = Math.round(canvas.clientHeight * scale)
      if (width && height && (canvas.width !== width || canvas.height !== height)) {
        canvas.width = width
        canvas.height = height
        ctx.imageSmoothingQuality = 'medium'
        // Resizing wipes the canvas - paint it again straight away.
        render()
        return
      }
      schedule()
    }

    const resizeObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null
    // Reduced motion: no gliding - scrolling stays exactly where the
    // visitor leaves it, and the timeline jumps straight to a stage.
    const stepping = !reduceMotion
    const passive = { passive: true }
    const active = { passive: false }
    function detach() {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', resize)
      if (stepping) {
        window.removeEventListener('wheel', onWheel, active)
        window.removeEventListener('keydown', onKey)
        window.removeEventListener('touchstart', onTouchStart, passive)
        window.removeEventListener('touchmove', onTouchMove, active)
        window.removeEventListener('touchend', onTouchEnd, passive)
        window.removeEventListener('touchcancel', onTouchEnd, passive)
      }
      resizeObserver?.disconnect()
      if (raf) window.cancelAnimationFrame(raf)
      raf = 0
      glide = null
    }

    resizeObserver?.observe(canvas)
    window.addEventListener('scroll', onScroll, passive)
    window.addEventListener('resize', resize)
    if (stepping) {
      window.addEventListener('wheel', onWheel, active)
      window.addEventListener('keydown', onKey)
      window.addEventListener('touchstart', onTouchStart, passive)
      window.addEventListener('touchmove', onTouchMove, active)
      window.addEventListener('touchend', onTouchEnd, passive)
      window.addEventListener('touchcancel', onTouchEnd, passive)
    }
    resize()
    start()

    return () => {
      cancelled = true
      glideToStageRef.current = null
      detach()
      aborter?.abort()
      decoded.forEach(release)
      sketches.forEach(release)
      decoded.clear()
      sketches.clear()
    }
  }, [stageCount])

  // A stage from the timeline - the film plays through on the way there.
  function goToStage(index) {
    glideToStageRef.current?.(index)
  }

  // Frames couldn't load at all - the story still reads as plain cards.
  if (failed) {
    return (
      <section className="story story--plain" aria-label="Our story">
        {STORY_STAGES.map((item, index) => (
          <StoryCard key={item.label} item={item} index={index} active />
        ))}
      </section>
    )
  }

  const classes = ['story']
  if (ready) classes.push('story--ready')
  if (started) classes.push('story--started')

  return (
    <section ref={sectionRef} className={classes.join(' ')} style={{ '--story-stages': stageCount }} aria-label="Our story">
      <div ref={stickyRef} className="story__sticky">
        <div className="story__screen">
          <canvas ref={canvasRef} className="story__canvas" role="img" aria-label={STORY_STAGES[stage].scene} />
          <div className="story__shade" aria-hidden="true" />
          <div className="story__loading" aria-hidden="true">
            <span ref={loadBarRef} />
          </div>
          <p className="story__hint" aria-hidden="true">
            Scroll to follow the journey
          </p>
        </div>

        <nav className="story__rail" aria-label="Stages of the journey">
          <span className="story__rail-line" aria-hidden="true">
            <span ref={railFillRef} className="story__rail-fill" />
          </span>
          <ol>
            {STORY_STAGES.map((item, index) => (
              <li
                key={item.label}
                className={index < stage ? 'is-done' : index === stage ? 'is-active' : undefined}
              >
                <button
                  type="button"
                  onClick={() => goToStage(index)}
                  aria-current={index === stage ? 'step' : undefined}
                  aria-label={`Stage ${index + 1}: ${item.label}`}
                >
                  <span className="story__rail-dot" aria-hidden="true" />
                  <span className="story__rail-label" aria-hidden="true">
                    {item.label}
                  </span>
                </button>
              </li>
            ))}
          </ol>
        </nav>

        <div className="story__cards">
          {STORY_STAGES.map((item, index) => (
            <StoryCard key={item.label} item={item} index={index} active={index === stage} />
          ))}
        </div>
      </div>
    </section>
  )
}

function StoryCard({ item, index, active }) {
  const side = index % 2 === 0 ? 'left' : 'right'
  return (
    <article className={`story-card story-card--${side}${active ? ' is-active' : ''}`}>
      <p className="story-card__label">
        <span className="story-card__number">{String(index + 1).padStart(2, '0')}</span>
        {item.label}
      </p>
      <h2 className="story-card__title">{item.title}</h2>
      <p className="story-card__text">{item.text}</p>
    </article>
  )
}
