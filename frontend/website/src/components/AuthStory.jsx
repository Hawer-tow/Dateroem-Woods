import { useEffect, useRef, useState } from 'react'
import { STORY_STAGES } from '../core/aboutStory.js'

// "the animation like of the acquisition ... delivery on the right" - the
// About page's film beside the sign-in form, playing by itself: each
// chapter's frames in turn, then a dissolve into the next, looping. Light
// on purpose: one frame in three (neighbours blended so the motion stays
// smooth), fetched as it plays with the first chapter first, and only a
// handful kept decoded at a time. Phones, data savers and "reduce motion"
// get a calm cross-fade of one picture per chapter instead of the film.
const FRAMES_URL = `${import.meta.env.BASE_URL}story/`
const FRAME_STEP = 3
const STAGE_MS = 5600
const DISSOLVE = 0.16
const READY_AHEAD = 10
const PARALLEL_LOADS = 4
const STILL_MS = 5200
const STILL_FADE_MS = 1400

export default function AuthStory() {
  const canvasRef = useRef(null)
  const fillRef = useRef(null)
  const pausedRef = useRef(false)
  const [stage, setStage] = useState(0)
  const [paused, setPaused] = useState(false)
  const [showing, setShowing] = useState(false)

  useEffect(() => {
    pausedRef.current = paused
  }, [paused])

  useEffect(() => {
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const connection = navigator.connection
    const saveData = Boolean(connection && (connection.saveData || /2g$/.test(connection.effectiveType || '')))
    const film = window.matchMedia('(min-width: 900px)').matches && !reduceMotion && !saveData
    const aborter = typeof AbortController !== 'undefined' ? new AbortController() : null
    let cancelled = false
    let raf = 0
    let set = null
    let stages = []
    // The film: frame numbers in playing order, which chapter each is in,
    // and where each chapter starts and ends in that order.
    const order = []
    const stageOf = []
    const stageStart = []
    const stageEnd = []
    // Calm mode: one picture per chapter.
    let stills = []
    const blobs = new Map()
    const bitmaps = new Map()
    const decoding = new Set()
    const queue = []
    let loading = 0
    let clock = 0
    let lastTime = 0
    let shownStage = -1
    let drawnOnce = false

    const frameUrl = (index) => `${FRAMES_URL}${set.key}/${String(index + 1).padStart(4, '0')}.${set.ext}`
    const focusOf = (stageIndex) => STORY_STAGES[stageIndex]?.focusX ?? 0.5

    async function fetchFrame(index) {
      const response = await fetch(frameUrl(index), aborter ? { signal: aborter.signal } : undefined)
      if (!response.ok) throw new Error(`frame ${response.status}`)
      return response.blob()
    }

    function pump() {
      while (loading < PARALLEL_LOADS && queue.length) {
        const index = queue.shift()
        if (blobs.has(index)) continue
        loading += 1
        fetchFrame(index)
          .then((blob) => blobs.set(index, blob))
          .catch(() => {})
          .finally(() => {
            loading -= 1
            if (!cancelled) pump()
          })
      }
    }

    function decode(index) {
      if (bitmaps.has(index) || decoding.has(index) || !blobs.has(index)) return
      decoding.add(index)
      createImageBitmap(blobs.get(index))
        .then((bitmap) => {
          decoding.delete(index)
          if (cancelled) bitmap.close()
          else bitmaps.set(index, bitmap)
        })
        .catch(() => decoding.delete(index))
    }

    function keepOnly(wanted) {
      for (const [index, bitmap] of bitmaps) {
        if (!wanted.has(index)) {
          bitmap.close()
          bitmaps.delete(index)
        }
      }
    }

    function resize() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      const width = Math.round(canvas.clientWidth * dpr)
      const height = Math.round(canvas.clientHeight * dpr)
      if (width && height && (canvas.width !== width || canvas.height !== height)) {
        canvas.width = width
        canvas.height = height
      }
    }

    // Fills the panel like a background photo, keeping each chapter's
    // subject in view (the About page's own focus points).
    function drawCover(image, focusX, alpha) {
      const cw = canvas.width
      const ch = canvas.height
      let sw = image.width
      let sh = image.height
      let sx = 0
      let sy = 0
      if (image.width / image.height > cw / ch) {
        sw = image.height * (cw / ch)
        sx = (image.width - sw) * focusX
      } else {
        sh = image.width / (cw / ch)
        sy = (image.height - sh) / 2
      }
      ctx.globalAlpha = alpha
      ctx.drawImage(image, sx, sy, sw, sh, 0, 0, cw, ch)
      ctx.globalAlpha = 1
    }

    function showStage(index, progress) {
      if (index !== shownStage) {
        shownStage = index
        setStage(index)
      }
      if (fillRef.current) fillRef.current.style.transform = `scaleX(${Math.min(1, Math.max(0, progress))})`
    }

    function markDrawn() {
      if (!drawnOnce) {
        drawnOnce = true
        setShowing(true)
      }
    }

    // Where the film is at a moment: two neighbouring frames (positions in
    // `order`) and how much of the second to blend in.
    function filmAt(ms) {
      const loop = STAGE_MS * stages.length
      const t = ((ms % loop) + loop) % loop
      const i = Math.min(Math.floor(t / STAGE_MS), stages.length - 1)
      const p = (t - i * STAGE_MS) / STAGE_MS
      const play = 1 - DISSOLVE
      if (p < play) {
        const x = stageStart[i] + (p / play) * (stageEnd[i] - stageStart[i])
        const a = Math.floor(x)
        return { stage: i, progress: p, a, b: Math.min(a + 1, stageEnd[i]), mix: x - a }
      }
      // The dissolve into the next chapter: its words take over halfway,
      // once its picture is the one mostly showing.
      const mix = (p - play) / DISSOLVE
      const next = (i + 1) % stages.length
      return {
        stage: mix >= 0.5 ? next : i,
        progress: mix >= 0.5 ? 0 : p,
        a: stageEnd[i],
        b: stageStart[next],
        mix,
      }
    }

    function filmTick(dt) {
      const at = filmAt(clock)
      const wanted = new Set()
      for (let k = 0; k <= READY_AHEAD; k++) wanted.add(order[(at.a + k) % order.length])
      wanted.add(order[at.b])
      wanted.forEach(decode)
      const first = bitmaps.get(order[at.a])
      const second = bitmaps.get(order[at.b])
      if (first) {
        drawCover(first, focusOf(stageOf[at.a]), 1)
        if (second && at.mix > 0.01) drawCover(second, focusOf(stageOf[at.b]), at.mix)
        markDrawn()
        // The clock only moves on once the next frame is ready - if the
        // network is slower than the film, it waits instead of skipping.
        if (!pausedRef.current && (second || at.mix <= 0.01)) clock += dt
      }
      showStage(at.stage, at.progress)
      keepOnly(wanted)
    }

    function stillsTick(dt) {
      const n = stills.length
      const t = clock % (STILL_MS * n)
      const i = Math.floor(t / STILL_MS)
      const local = t - i * STILL_MS
      const fade = Math.max(0, (local - (STILL_MS - STILL_FADE_MS)) / STILL_FADE_MS)
      stills.forEach(decode)
      const current = bitmaps.get(stills[i])
      const next = bitmaps.get(stills[(i + 1) % n])
      if (current) {
        drawCover(current, focusOf(i), 1)
        if (next && fade > 0) drawCover(next, focusOf((i + 1) % n), fade)
        markDrawn()
        if (!pausedRef.current && (next || fade === 0)) clock += dt
      }
      if (fade >= 0.5) showStage((i + 1) % n, 0)
      else showStage(i, local / STILL_MS)
    }

    function tick(time) {
      raf = requestAnimationFrame(tick)
      // A hidden tab pauses requestAnimationFrame; don't jump on return.
      const dt = lastTime ? Math.min(time - lastTime, 100) : 0
      lastTime = time
      resize()
      if (film) filmTick(dt)
      else stillsTick(dt)
    }

    // The big frames where the panel is big; an AVIF-blind browser gets
    // the WebP copies instead.
    async function chooseSet(manifest, preferred, firstIndex) {
      set = { key: preferred, ...manifest.sets[preferred] }
      if (set.ext !== 'avif' || !manifest.sets.fb) return
      try {
        const blob = await fetchFrame(firstIndex)
        const bitmap = await createImageBitmap(blob)
        blobs.set(firstIndex, blob)
        bitmaps.set(firstIndex, bitmap)
      } catch {
        if (cancelled) return
        blobs.clear()
        bitmaps.clear()
        set = { key: 'fb', ...manifest.sets.fb }
      }
    }

    async function start() {
      let manifest
      try {
        const response = await fetch(`${FRAMES_URL}manifest.json`, aborter ? { signal: aborter.signal } : undefined)
        if (!response.ok) throw new Error(`manifest ${response.status}`)
        manifest = await response.json()
      } catch {
        return
      }
      if (cancelled) return
      stages = manifest.stages.slice(0, STORY_STAGES.length)
      if (film) {
        stages.forEach((s, i) => {
          stageStart.push(order.length)
          for (let f = s.start; f <= s.end; f += FRAME_STEP) {
            order.push(f)
            stageOf.push(i)
          }
          if (order[order.length - 1] !== s.end) {
            order.push(s.end)
            stageOf.push(i)
          }
          stageEnd.push(order.length - 1)
        })
        await chooseSet(manifest, 'lg', order[0])
      } else {
        stills = stages.map((s) => Math.round((s.start + s.end) / 2))
        await chooseSet(manifest, 'sm', stills[0])
      }
      if (cancelled) return
      queue.push(...(film ? order : stills))
      pump()
      raf = requestAnimationFrame(tick)
    }

    start()
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
      if (aborter) aborter.abort()
      bitmaps.forEach((bitmap) => bitmap.close())
      bitmaps.clear()
    }
  }, [])

  const current = STORY_STAGES[stage]
  return (
    <div className={`auth-story${showing ? ' auth-story--showing' : ''}`}>
      <canvas ref={canvasRef} className="auth-story__canvas" aria-hidden="true" />
      <div className="auth-story__shade" aria-hidden="true" />
      <div className="auth-story__caption">
        <div className="auth-story__progress">
          <div className="auth-story__bars" aria-hidden="true">
            {/* Finished chapters full, later ones empty; the current one is
                filled as it plays (straight on the element, every frame). */}
            {STORY_STAGES.map((s, i) => (
              <span key={s.label} className="auth-story__bar">
                <span ref={i === stage ? fillRef : undefined} style={{ transform: `scaleX(${i < stage ? 1 : 0})` }} />
              </span>
            ))}
          </div>
          <button
            type="button"
            className="auth-story__pause"
            onClick={() => setPaused((value) => !value)}
            aria-label={paused ? 'Play the story' : 'Pause the story'}
          >
            {paused ? (
              <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M7 4.5v15l12-7.5z" fill="currentColor" /></svg>
            ) : (
              <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z" fill="currentColor" /></svg>
            )}
          </button>
        </div>
        <div key={stage} className="auth-story__words">
          <p className="auth-story__label">{current.label}</p>
          <h2 className="auth-story__title">{current.title}</h2>
          <p className="auth-story__text">{current.text}</p>
        </div>
      </div>
    </div>
  )
}
