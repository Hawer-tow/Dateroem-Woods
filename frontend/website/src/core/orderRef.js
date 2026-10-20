// "the url order/39 is not good" - a raw, visibly-sequential Order_ID in
// the address bar looks like an internal database key and invites poking
// at neighbouring numbers, even though the backend already 403s anyone
// who isn't the order's owner. This isn't meant as security (the XOR mask
// is fixed and not remotely secret) - it's just so the URL a customer
// sees/shares doesn't read as "record #39 of however many exist".
const MASK = 0x5bf03

export function encodeOrderRef(orderId) {
  return (Number(orderId) ^ MASK).toString(36)
}

export function decodeOrderRef(ref) {
  const n = parseInt(ref, 36)
  return Number.isNaN(n) ? null : n ^ MASK
}
