// "in delivered goods to have the recently delivered goods as the first
// then the rest ... dont show all you can hide some delivered a long time
// only if one wants to see all since after 2 years ... having it appearing
// as the first one is not so good" - the Delivered goods group's order and
// how much of it shows before "Show older".

// Delivered within this long counts as recent.
export const RECENT_DAYS = 180
// At least this many always show (the latest ones), even if all are old -
// and with this many or fewer there's nothing to hide.
export const MIN_SHOWN = 4
// At most this many before "Show older" - two rows on a laptop.
export const MAX_SHOWN = 8

// Cards are one per product: { lastDelivered: 'YYYY-MM-DD' | null,
// latestOrderId }. Newest delivery first; undated (a heavy order marked
// delivered without a date) after the dated ones, newest order first.
export function newestDeliveredFirst(cards) {
  return [...cards].sort(
    (a, b) =>
      (b.lastDelivered || '').localeCompare(a.lastDelivered || '') || (b.latestOrderId || 0) - (a.latestOrderId || 0),
  )
}

// How many of the (newest-first) cards show before "Show older".
export function shownCount(cards, today = new Date()) {
  const cutoff = new Date(today.getTime() - RECENT_DAYS * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  const recent = cards.filter((card) => card.lastDelivered && card.lastDelivered >= cutoff).length
  return Math.min(cards.length, Math.max(MIN_SHOWN, Math.min(recent, MAX_SHOWN)))
}
