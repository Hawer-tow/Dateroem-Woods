// "do allow it to accept the +...@gmai... and also verify if an email is
// correct and a valid email". The same shape rule the backend applies
// (util/EmailAddresses.java, which also checks the domain really exists),
// here so a slip shows while typing. A "+tag" is fine: name+shop@gmail.com
// reaches name@gmail.com.
const EMAIL_FORMAT =
  /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/

export function isValidEmail(value) {
  const email = (value || '').trim()
  return email.length <= 254 && EMAIL_FORMAT.test(email)
}

// A typo'd domain such as "gmai.com" may still exist (and pass the
// backend's check), so the big providers get a gentle "did you mean".
const KNOWN_DOMAINS = [
  'gmail.com', 'googlemail.com', 'yahoo.com', 'yahoo.co.uk', 'ymail.com', 'outlook.com', 'hotmail.com',
  'live.com', 'msn.com', 'icloud.com', 'me.com', 'aol.com', 'protonmail.com', 'proton.me', 'mail.com',
  'gmx.com', 'zoho.com', 'yandex.com',
]

function distance(a, b) {
  const row = Array.from({ length: b.length + 1 }, (_, j) => j)
  for (let i = 1; i <= a.length; i++) {
    let previous = row[0]
    row[0] = i
    for (let j = 1; j <= b.length; j++) {
      const kept = row[j]
      row[j] = Math.min(row[j] + 1, row[j - 1] + 1, previous + (a[i - 1] === b[j - 1] ? 0 : 1))
      previous = kept
    }
  }
  return row[b.length]
}

// "name@gmial.com" -> "name@gmail.com"; null when the domain looks fine.
export function suggestEmail(value) {
  const email = (value || '').trim()
  const at = email.lastIndexOf('@')
  if (at < 1) return null
  const domain = email.slice(at + 1).toLowerCase()
  if (!domain || KNOWN_DOMAINS.includes(domain)) return null
  let best = null
  let bestDistance = domain.length > 6 ? 3 : 2
  for (const known of KNOWN_DOMAINS) {
    const d = distance(domain, known)
    if (d < bestDistance) {
      best = known
      bestDistance = d
    }
  }
  // "name@gmail" with the ".com" left off.
  if (!best) best = KNOWN_DOMAINS.find((known) => known.split('.')[0] === domain) || null
  return best ? `${email.slice(0, at + 1)}${best}` : null
}
