// "can that check for a valid number take place in the confirmative area
// immediately there to prevent even proceeding to an attempt" - mirrors
// the backend's own MpesaClient.validateAndNormalizePhone exactly (same
// accepted formats, same 12-digit-normalized length rule), so a number
// that passes here is guaranteed not to bounce off the server with a 400.
export function isValidKenyanPhone(raw) {
  const digits = (raw || '').replace(/\D/g, '')
  let normalized
  if (digits.startsWith('0')) {
    normalized = '254' + digits.slice(1)
  } else if (digits.startsWith('254')) {
    normalized = digits
  } else {
    normalized = '254' + digits
  }
  return normalized.length === 12 && (normalized.startsWith('2547') || normalized.startsWith('2541'))
}

export const KENYAN_PHONE_HINT = 'Enter a valid Kenyan phone number (07XXXXXXXX, 01XXXXXXXX, or 2547XXXXXXXX)'

// The same number however it's written (0712..., 254712..., +254 712 ...).
export function samePhone(a, b) {
  const last9 = (raw) => (raw || '').replace(/\D/g, '').slice(-9)
  return last9(a).length === 9 && last9(a) === last9(b)
}
