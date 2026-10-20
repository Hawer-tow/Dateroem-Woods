// "check delivery areas near them and ... those areas are shown at the
// top as delivery areas near you and one can click that exact delivery
// area ... get a google maps or maps to see it through maps" - shared by
// AreaPicker (checkout + the read-only Shipping directory).

export function parseCoordinates(raw) {
  if (!raw) return null
  const parts = raw
    .split(',')
    .map((part) => parseFloat(part.trim()))
  if (parts.length !== 2 || parts.some((n) => Number.isNaN(n))) return null
  return { lat: parts[0], lng: parts[1] }
}

// Great-circle distance in km - plenty accurate at "which town is closest"
// scale, no mapping library needed just to sort a short list.
export function distanceKm(a, b) {
  const R = 6371
  const dLat = ((b.lat - a.lat) * Math.PI) / 180
  const dLng = ((b.lng - a.lng) * Math.PI) / 180
  const sinLat = Math.sin(dLat / 2)
  const sinLng = Math.sin(dLng / 2)
  const h =
    sinLat * sinLat + Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * sinLng * sinLng
  return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

export function googleMapsUrl(rawCoordinates) {
  const coords = parseCoordinates(rawCoordinates)
  if (!coords) return null
  return `https://www.google.com/maps/search/?api=1&query=${coords.lat},${coords.lng}`
}
