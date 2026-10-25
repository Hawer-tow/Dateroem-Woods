import { useEffect, useState } from 'react'

// True on screens wide enough for the Featured column beside the products
// (forest look); it follows the window as it is resized.
const WIDE = '(min-width: 1280px)'

export function useWideScreen() {
  const [wide, setWide] = useState(() => typeof window !== 'undefined' && window.matchMedia(WIDE).matches)

  useEffect(() => {
    const query = window.matchMedia(WIDE)
    const follow = () => setWide(query.matches)
    follow()
    query.addEventListener('change', follow)
    return () => query.removeEventListener('change', follow)
  }, [])

  return wide
}
