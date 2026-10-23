import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import DriverNote from '../components/DriverNote.jsx'
import Icon from '../components/Icon.jsx'

// "a Track my order page where a customer enters the ticket plus the last 3
// digits of their phone, without signing in" (23 October 2026) - for someone
// who ordered on another phone or isn't signed in here. The server checks
// both and limits wrong tries; the answer never names the customer.
const STEPS = [
  ['PLACED', 'Order placed'],
  ['PAID', 'Paid'],
  ['SCHEDULED', 'On a delivery'],
  ['DELIVERED', 'Delivered'],
]

// How far along the four steps each stage is (a delivered order has
// finished all four).
const STAGE_STEP = { AWAITING_PAYMENT: 0, PREPARING: 1, RESCHEDULING: 1, SCHEDULED: 2, DELIVERED: 4 }

const STAGE_TEXT = {
  AWAITING_PAYMENT: 'Waiting for the M-Pesa payment to be confirmed.',
  PREPARING: 'Paid - we are getting your order ready for delivery.',
  RESCHEDULING: 'Your delivery is being rescheduled - our team will confirm a new date.',
  SCHEDULED: 'Your order is on a delivery run.',
  DELIVERED: 'Delivered.',
  CANCELLED: 'This order was cancelled.',
}

export default function TrackOrderPage() {
  const [params] = useSearchParams()
  const [ticket, setTicket] = useState(params.get('ticket') || '')
  const [phone, setPhone] = useState('')
  const [order, setOrder] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    document.title = 'Track my order - Dateroem Woods'
  }, [])

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setOrder(null)
    setBusy(true)
    try {
      setOrder(await api.trackOrder(ticket.trim(), phone.trim()))
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="track-page">
      <header className="track-page__header">
        <h1>Track my order</h1>
        <p className="track-page__lead">
          Enter the ticket from your order (it looks like <span className="ticket-code">DW-7K3Q9P</span>) and the
          last 3 digits of the phone number on your account.
        </p>
      </header>

      <form className="track-form" onSubmit={handleSubmit}>
        <label className="track-form__field">
          Ticket
          <input
            type="text"
            value={ticket}
            onChange={(event) => setTicket(event.target.value)}
            placeholder="DW-7K3Q9P"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={12}
            required
          />
        </label>
        <label className="track-form__field track-form__field--short">
          Last 3 digits of your phone
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]{3}"
            value={phone}
            onChange={(event) => setPhone(event.target.value.replace(/\D/g, '').slice(0, 3))}
            placeholder="123"
            autoComplete="off"
            maxLength={3}
            required
          />
        </label>
        <button type="submit" className="btn btn--primary track-form__submit" disabled={busy}>
          {busy ? 'Checking...' : 'Track order'}
        </button>
      </form>

      {error && <p className="state-message state-message--error">{error}</p>}

      {order && <TrackResult order={order} />}

      <p className="track-page__more">
        Signed in? <Link to="/my-orders">Ordered Goods</Link> lists all your orders. Need a hand?{' '}
        <Link to="/help">Contact us</Link>.
      </p>
    </div>
  )
}

function TrackResult({ order }) {
  const cancelled = order.stage === 'CANCELLED'
  const reached = STAGE_STEP[order.stage] ?? 0
  const area = [order.areaName, order.deliveryCity].filter(Boolean).join(', ')

  return (
    <section className="track-result" aria-live="polite">
      <p className="track-result__ticket">
        <span className="ticket-code">{order.ticketCode}</span>
        {order.placedOn && <span className="track-result__placed">placed {order.placedOn}</span>}
      </p>

      {!cancelled && (
        <ol className="track-steps">
          {STEPS.map(([key, label], index) => (
            <li
              key={key}
              className={
                'track-steps__step' +
                (index < reached ? ' track-steps__step--done' : '') +
                (index === reached ? ' track-steps__step--current' : '')
              }
            >
              <span className="track-steps__dot" aria-hidden="true">
                {index < reached ? <Icon name="check" size={14} /> : index + 1}
              </span>
              <span className="track-steps__label">{label}</span>
            </li>
          ))}
        </ol>
      )}

      <p className={'track-result__status' + (cancelled ? ' track-result__status--cancelled' : '')}>
        {STAGE_TEXT[order.stage] || 'We have your order.'}
      </p>

      <dl className="track-result__facts">
        {order.deliveredAt && (
          <div>
            <dt>Delivered on</dt>
            <dd>{order.deliveredAt}</dd>
          </div>
        )}
        {!order.deliveredAt && !cancelled && order.estimatedDeliveryDate && (
          <div>
            <dt>{order.stage === 'SCHEDULED' ? 'Delivery date' : 'Estimated delivery'}</dt>
            <dd>{order.estimatedDeliveryDate}</dd>
          </div>
        )}
        {area && (
          <div>
            <dt>Pickup / delivery area</dt>
            <dd>{area}</dd>
          </div>
        )}
        {order.total != null && (
          <div>
            <dt>Total</dt>
            <dd>KES {Number(order.total).toLocaleString()}</dd>
          </div>
        )}
      </dl>

      {!order.deliveredAt && !cancelled && <DriverNote order={order} />}

      {order.items?.length > 0 && (
        <ul className="track-result__items">
          {order.items.map((item) => (
            <li key={item.productId}>
              {item.name || item.type} × {item.qty}
              {item.productUnit === 'board_feet' ? ' board ft' : ''}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
