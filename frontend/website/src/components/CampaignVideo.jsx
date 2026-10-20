import { useLayoutEffect, useRef } from 'react'

// "make sure the remembrance of the media is remembered" - where each
// campaign video was left, for this visit. Watching it on the Featured page
// and opening one of its products carries on from the same moment on the
// product page (larger), and coming back carries on there too. The moment
// itself also survives a page reload; carrying on playing does not (a
// browser only lets sound start after a click).
const left = new Map()
const STORE_KEY = 'dateroem.videoTimes'

function storedTimes() {
  try {
    return JSON.parse(sessionStorage.getItem(STORE_KEY) || '{}')
  } catch {
    return {}
  }
}

function storeTime(src, time) {
  try {
    const times = storedTimes()
    times[src] = time
    sessionStorage.setItem(STORE_KEY, JSON.stringify(times))
  } catch {
    // Private window or storage blocked: remembered for this page only.
  }
}

// onSize(width, height) reports the video's own shape once it is known,
// so a frame around it can take that shape. onElement receives the video
// element itself (for the soft glow behind it on the product page).
export default function CampaignVideo({ src, className, onSize, onElement }) {
  const ref = useRef(null)

  useLayoutEffect(() => {
    onElement?.(ref.current)
    return () => onElement?.(null)
  }, [onElement])

  // A layout effect so its clean-up runs while the video is still on the
  // page: once it has been taken off, the browser pauses it, and "was it
  // playing?" would always read no.
  useLayoutEffect(() => {
    const video = ref.current
    if (!video || !src) return undefined
    const saved = left.get(src) || { time: storedTimes()[src] || 0, playing: false }
    // Nothing is recorded until this video has been put back where it was
    // left: a player taken away before it loaded (or mounted twice, as
    // React does while developing) would otherwise save "0s, paused" over
    // the real place.
    let ready = false

    const restore = () => {
      ready = true
      if (saved.time > 0 && (!video.duration || saved.time < video.duration - 0.5)) {
        video.currentTime = saved.time
      }
      if (saved.playing) video.play().catch(() => {})
    }
    const remember = () => {
      if (!ready) return
      const time = video.ended ? 0 : video.currentTime
      left.set(src, { time, playing: !video.paused && !video.ended })
      storeTime(src, time)
    }

    if (video.readyState >= 1) restore()
    else video.addEventListener('loadedmetadata', restore, { once: true })
    video.addEventListener('timeupdate', remember)
    video.addEventListener('play', remember)
    video.addEventListener('pause', remember)
    video.addEventListener('ended', remember)
    return () => {
      remember()
      video.removeEventListener('loadedmetadata', restore)
      video.removeEventListener('timeupdate', remember)
      video.removeEventListener('play', remember)
      video.removeEventListener('pause', remember)
      video.removeEventListener('ended', remember)
    }
  }, [src])

  return (
    <video
      ref={ref}
      className={className}
      src={src}
      controls
      playsInline
      preload="metadata"
      onLoadedMetadata={onSize ? (event) => onSize(event.currentTarget.videoWidth, event.currentTarget.videoHeight) : undefined}
    />
  )
}
