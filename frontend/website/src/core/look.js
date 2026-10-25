// "the most modern look of the website to feel like an out of the world
// experience making one feel the ambience of the forests ... in all
// sections" (25 October 2026). It was built and tested on the local preview
// first, then approved and put live the same day. It is on when the build
// has VITE_FOREST_LOOK=1: frontend/website/.env.production for the live
// site, .env.development.local for the preview on the company computer.
// Setting it to 0 and releasing again brings back the classic look, which
// is still all here.
export const FOREST_LOOK = import.meta.env.VITE_FOREST_LOOK === '1'

// Visitors who turn animations off on their device get a still forest.
export function motionAllowed() {
  return !(typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
}
