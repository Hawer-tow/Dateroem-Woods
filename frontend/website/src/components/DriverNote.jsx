import { useEffect, useState } from 'react'
import { api } from '../api/client.js'
import Icon from './Icon.jsx'

// "vehicle visible only to staff ... unless enabled" (migration 038) - the
// driver's first name and the number plate arrive only when staff chose to
// share them for this delivery; otherwise nothing shows. The number to
// call is always the company's, never the driver's own.
export default function DriverNote({ order }) {
  const [phone, setPhone] = useState(null)
  const shared = Boolean(order?.vehicleIdentifier)

  useEffect(() => {
    if (!shared) return
    api
      .helpContact()
      .then((contact) => setPhone(contact?.phone || null))
      .catch(() => setPhone(null))
  }, [shared])

  if (!shared) return null
  return (
    <p className="driver-note">
      <Icon name="truck" className="icon--inline" />
      <span>
        {order.driverFirstName ? (
          <>
            Your driver: <strong>{order.driverFirstName}</strong>, vehicle <strong>{order.vehicleIdentifier}</strong>.
          </>
        ) : (
          <>
            Vehicle <strong>{order.vehicleIdentifier}</strong>.
          </>
        )}
        {phone && (
          <>
            {' '}
            Questions about the delivery? Call us on <a href={`tel:${phone.replace(/\s+/g, '')}`}>{phone}</a>.
          </>
        )}
      </span>
    </p>
  )
}
