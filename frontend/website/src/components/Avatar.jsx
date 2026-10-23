import { useState } from 'react'

// The account's profile photo, or its first letter until there is one (or
// if the picture can't be loaded).
export default function Avatar({ name, photoUrl, size = 36, className = '' }) {
  const [broken, setBroken] = useState(null)
  const showPhoto = photoUrl && broken !== photoUrl
  return (
    <span className={`avatar ${className}`} style={{ width: size, height: size, fontSize: size * 0.42 }} aria-hidden="true">
      {showPhoto ? (
        <img src={photoUrl} alt="" onError={() => setBroken(photoUrl)} />
      ) : (
        (name?.trim()?.[0] ?? '?').toUpperCase()
      )}
    </span>
  )
}
