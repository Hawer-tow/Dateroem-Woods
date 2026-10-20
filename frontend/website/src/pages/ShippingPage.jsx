import { useEffect, useState } from 'react'
import { api, apiErrorMessage } from '../api/client.js'
import AreaPicker from '../components/AreaPicker.jsx'

export default function ShippingPage() {
  const [areas, setAreas] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    api
      .listShippingAreas()
      .then((res) => {
        if (!cancelled) setAreas(res)
      })
      .catch((err) => {
        if (!cancelled) setError(apiErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) return <p className="state-message">Loading pickup areas...</p>
  if (error) return <p className="state-message state-message--error">Couldn't reach the server: {error}</p>

  return (
    <div className="shipping-page">
      <h1>Shipping &amp; Pickup Areas</h1>
      {areas.length === 0 ? (
        <p className="state-message">No pickup areas have been set up yet.</p>
      ) : (
        <AreaPicker areas={areas} />
      )}
    </div>
  )
}
