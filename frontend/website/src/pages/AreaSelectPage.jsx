import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import AreaPicker from '../components/AreaPicker.jsx'
import BackArrowIcon from '../components/BackArrowIcon.jsx'

// "we may have more than 10 even 20 delivery areas is it good to list them
// there [in the cart] ... can there be a way to take you to the deliveries
// area since its bigger" - the full, searchable, grouped-by-city picker
// gets a whole page to breathe in instead of being squeezed into the
// checkout form. Picking one sends you straight back to the cart with the
// choice (and whatever phone number was already typed) carried along.
export default function AreaSelectPage() {
  const [areas, setAreas] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const location = useLocation()
  const navigate = useNavigate()
  const selectedId = location.state?.selectedId ?? null
  const phoneNumber = location.state?.phoneNumber ?? ''

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

  function backToCart() {
    navigate('/cart', { state: { deliveryAreaId: selectedId, phoneNumber } })
  }

  function pick(areaId) {
    navigate('/cart', { state: { deliveryAreaId: areaId, phoneNumber }, replace: true })
  }

  return (
    <div className="area-select-page">
      <button type="button" className="back-button" onClick={backToCart} aria-label="Back to cart">
        <BackArrowIcon />
      </button>
      <h1>Choose your delivery / pickup area</h1>
      {loading && <p className="state-message">Loading pickup areas...</p>}
      {error && <p className="state-message state-message--error">Couldn't reach the server: {error}</p>}
      {!loading && !error && (
        areas.length === 0 ? (
          <p className="state-message">No pickup areas have been set up yet.</p>
        ) : (
          <AreaPicker areas={areas} selectedId={selectedId} description onSelect={pick} />
        )
      )}
    </div>
  )
}
