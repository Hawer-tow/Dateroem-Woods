// A fixed "random" number between 0 and 1 for item i, so the fireflies and
// the footer's trees stand in the same places on every visit and page.
export function seeded(i, salt) {
  const x = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453
  return x - Math.floor(x)
}
