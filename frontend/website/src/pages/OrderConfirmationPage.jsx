import { useEffect, useRef, useState } from 'react'
import { useParams, useLocation, Link } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import { decodeOrderRef } from '../core/orderRef.js'
import DriverNote from '../components/DriverNote.jsx'
import Icon from '../components/Icon.jsx'

const POLL_INTERVAL_MS = 4000
// Stop polling after ~2 minutes - a sandbox STK prompt that hasn't been
// answered by then almost certainly won't be; the page still shows
// whatever the last known status was.
const MAX_POLLS = 30

// "place the stkpush directly ... this will allow me to test and see if
// one can really pay using the stk push" - paymentStatus comes from the
// order's own PRODUCT_PAYMENTS row (set by OrderService when a phone
// number was given at checkout) and is polled here since Safaricom's
// callback lands asynchronously, on its own schedule, not as a direct
// response to placing the order.
export default function OrderConfirmationPage() {
  const { orderRef } = useParams()
  // The route/URL only ever carries the obfuscated reference - decoded
  // once here back into the real numeric id the API actually needs.
  const orderId = decodeOrderRef(orderRef)
  // "never allow an order to pass if the prompt fail" - OrderService now
  // rejects the whole order outright unless the STK push actually sent,
  // so getting to this page at all means it did; stkPushSent/stkPushError
  // (passed via router state before that fix) can no longer be anything
  // but true/null and are gone from here.
  const [order, setOrder] = useState(null)
  const [error, setError] = useState(null)
  const pollCount = useRef(0)

  useEffect(() => {
    let token = null
    try {
      token = JSON.parse(localStorage.getItem('dateroem_auth') || 'null')?.token
    } catch {
      token = null
    }
    if (!token) {
      setError('Sign in to see this order\'s delivery status.')
      return
    }
    if (orderId == null) {
      setError("That order link doesn't look right.")
      return
    }

    let cancelled = false
    let timer = null

    function fetchOrder() {
      api
        .getOrder(orderId, token)
        .then((res) => {
          if (cancelled) return
          setOrder(res)
          pollCount.current += 1
          if (res.paymentStatus === 'PENDING' && pollCount.current < MAX_POLLS) {
            timer = setTimeout(fetchOrder, POLL_INTERVAL_MS)
          }
        })
        .catch((err) => {
          if (!cancelled) setError(apiErrorMessage(err))
        })
    }

    fetchOrder()
    return () => {
      cancelled = true
      if (timer) clearTimeout(timer)
    }
  }, [orderId])

  const deliveryStatus = order?.deliveryStatus?.toLowerCase()
  const area = order?.areaName || order?.deliveryCity
  const paymentStatus = order?.paymentStatus

  return (
    <div className="order-confirmation">
      <h1>Order placed</h1>
      <p>
        {/* "never allow them to see e.g order 39 that is the system" -
            the order's ticket (DW-7K3Q9P, migration 038) says nothing
            about how many orders exist, and is what the customer quotes
            to us or types into Track my order. */}
        Your order{order?.ticketCode ? <> <strong className="ticket-code">{order.ticketCode}</strong></> : ''} has been recorded.
      </p>
      {order?.ticketCode && (
        <p className="order-confirmation__ticket-hint">
          Keep this ticket. You can check the order any time on{' '}
          <Link to={`/track?ticket=${encodeURIComponent(order.ticketCode)}`}>Track my order</Link>, even without signing in.
        </p>
      )}

      {paymentStatus === 'PENDING' && (
        <p className="order-confirmation__payment order-confirmation__payment--pending">
          <Icon name="smartphone" className="icon--inline" />
          STK push sent - check your phone and enter your M-Pesa PIN to complete payment.
        </p>
      )}
      {paymentStatus === 'COMPLETED' && (
        <p className="order-confirmation__payment order-confirmation__payment--ok">
          <Icon name="delivered" className="icon--inline" />
          Payment confirmed - thank you!
        </p>
      )}
      {/* "all of this orders are done soft cash no pay on delivery" -
          the prompt sending is guaranteed (see the useState comment
          above); this is the customer declining/mistyping their PIN or
          letting the prompt time out on their phone after it was sent,
          a separate, later moment the checkout-time fix doesn't cover. */}
      {paymentStatus === 'FAILED' && (
        <p className="order-confirmation__payment order-confirmation__payment--failed">
          <Icon name="warning" className="icon--inline" />
          The payment prompt wasn't completed - Ask AI Help below to send a fresh one to the same number.
        </p>
      )}

      {/* "the area where one can see the products they paid for" - line
          items from the order itself, not just the total. */}
      {order?.items?.length > 0 && (
        <div className="order-confirmation__items">
          <p className="order-confirmation__status-label">What you ordered</p>
          <ul className="order-confirmation__item-list">
            {order.items.map((item) => (
              <li key={item.productId} className="order-confirmation__item">
                <span className="order-confirmation__item-name">
                  {item.name || item.type} × {item.qty} {item.productUnit === 'board_feet' ? 'board ft' : ''}
                </span>
                <span className="order-confirmation__item-price">
                  KES {(item.unitPrice * item.qty).toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {order && (
        <div className="order-confirmation__status">
          <p className="order-confirmation__status-label">Delivery status</p>
          <p className="order-confirmation__status-value">
            {order.deliveredAt
              ? `Delivered on ${order.deliveredAt}${area ? ` to ${area}` : ''}`
              : `${deliveryStatus ? deliveryStatus[0].toUpperCase() + deliveryStatus.slice(1) : 'Pending'}${area ? ` - heading to ${area}` : ''}`}
          </p>
          {/* "this will be delivered in a month e.g 23 days" - set the
              moment the order is placed (large items get a 3-month
              window, everything else 3 weeks), shown as a countdown so
              it stays meaningful no matter how long ago the order was
              placed. */}
          {!order.deliveredAt && order.estimatedDeliveryDate && (
            <p className="order-confirmation__eta">
              <Icon name="truck" className="icon--inline" />
              Estimated delivery: {order.estimatedDeliveryDate}
              {(() => {
                const days = Math.ceil((new Date(order.estimatedDeliveryDate) - new Date()) / (1000 * 60 * 60 * 24))
                return days > 0 ? ` (in ${days} day${days === 1 ? '' : 's'})` : ' (any day now)'
              })()}
            </p>
          )}
          {!order.deliveredAt && <DriverNote order={order} />}
          <p className="order-confirmation__status-hint">
            You can also ask the AI Help chat "where's my order" any time for the latest status.
          </p>
        </div>
      )}
      {error && <p className="state-message state-message--error">{error}</p>}

      <div className="order-confirmation__actions">
        <Link to="/help" className="btn btn--primary">
          Ask AI Help about delivery
        </Link>
        <Link to="/" className="btn btn--secondary">
          Keep browsing
        </Link>
      </div>
    </div>
  )
}
