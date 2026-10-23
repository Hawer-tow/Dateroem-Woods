// The profile page's helpers (migration 039).

// A chosen picture as the square JPEG the website server stores: the
// centre of it, 512 px, so a phone's 12-megapixel photo goes up as ~80 KB.
export async function squareJpeg(file, size = 512) {
  const url = URL.createObjectURL(file)
  try {
    const image = await new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('That file is not a picture.'))
      img.src = url
    })
    const side = Math.min(image.naturalWidth, image.naturalHeight)
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, size, size)
    ctx.drawImage(
      image,
      (image.naturalWidth - side) / 2,
      (image.naturalHeight - side) / 2,
      side,
      side,
      0,
      0,
      size,
      size
    )
    return await new Promise((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('That picture could not be read.'))), 'image/jpeg', 0.85)
    )
  } finally {
    URL.revokeObjectURL(url)
  }
}

// "sensitive data show/hide" - shown partly until the eye is pressed:
// 0712 345 678 -> 07•• ••• 678; jane.doe@gmail.com -> ja•••@gmail.com.
export function maskPhone(phone) {
  if (!phone) return ''
  const digits = String(phone).replace(/\D/g, '')
  if (digits.length < 6) return '•••'
  return `${digits.slice(0, 2)}•• ••• ${digits.slice(-3)}`
}

export function maskEmail(email) {
  if (!email) return ''
  const [name, domain] = String(email).split('@')
  if (!domain) return '•••'
  return `${name.slice(0, 2)}•••@${domain}`
}

// The same rule as the server: 8 or more characters, letters and numbers.
export function passwordProblem(password, userName) {
  if (!password || password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return 'Use at least 8 characters, with letters and numbers.'
  }
  if (userName && userName.length >= 3 && password.toLowerCase().includes(userName.toLowerCase())) {
    return "Don't use your username in your password."
  }
  return null
}
