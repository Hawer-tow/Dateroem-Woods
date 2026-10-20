import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { encodeOrderRef } from '../core/orderRef.js'
import { newestDeliveredFirst, shownCount } from '../core/deliveredGoods.js'
import {
  OrdersTab,
  RatingsFullView,
  RatingsModal,
  RatingsPanel,
  useOrderRatings,
} from '../components/OrderRatings.jsx'
import { Stars } from '../components/Stars.jsx'
import Icon from '../components/Icon.jsx'

// "if one has ordered something provide the section ordered goods where
// one can see the product and the message e.g if its light to be
// delivered within 3 weeks" - what was bought and a plain-English
// delivery message. "now the admin assigns a delivery ... the customer in
// the ordered goods sees will be delivered on 21st" - once an admin sets a
// real Estimated_Delivery_Date/Vehicle_ID, that concrete date + vehicle
// replaces the generic SLA line (both live in estimatedDeliveryDate).
//
// "if its the same product only place one card then you count how many of
// that product there is ... even for not yet assigned do this to remove
// congestion" - inside each group, one card per PRODUCT (not per order),
// with how many orders and pieces it covers and links to those orders.
//
// "if the product was signed then the product was delivered then add them
// to another bigger card ... already delivered goods such that its only
// there where you can rate the goods ... once delivered they move up" -
// the groups, top to bottom:
//   Delivered goods           signed for on delivery; the only place to rate
//   Assigned to a delivery    on a delivery run, with its date
//   Not yet assigned          waiting for a run, with the estimate
//   Cancelled                 collapsed at the bottom
//
// "ordered is by the product primary key ... but never show this id to the
// user, order by a number e.g from the first ordered to the last" -
// Order_ID (never shown) decides the sequence, oldest first; customers
// only ever see their own customerOrderNumber.
export default function MyOrdersPage() {
  const { user, isAuthenticated } = useAuth()
  const [orders, setOrders] = useState(null)
  const [error, setError] = useState(null)
  const ratings = useOrderRatings(isAuthenticated ? user.token : null)
  // "if you click the drop down where the ordered goods were we have the
  // rated goods ... a button like tab ... to go back to the ordered goods"
  // - two views of this page. Kept in the address (?view=ratings) so the
  // phone's own Back button also returns to the orders.
  const [params, setParams] = useSearchParams()
  const view = ratings.hasRatings && params.get('view') === 'ratings' ? 'ratings' : 'orders'

  function switchView(next) {
    setParams(next === 'ratings' ? { view: 'ratings' } : {})
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  useEffect(() => {
    if (!isAuthenticated) return
    api
      .myOrders(user.token)
      .then(setOrders)
      .catch((err) => setError(apiErrorMessage(err)))
  }, [isAuthenticated, user])

  if (!isAuthenticated) {
    return <p className="state-message">Sign in to see the goods you've ordered and their delivery status.</p>
  }
  if (error) return <p className="state-message state-message--error">Couldn't load your orders: {error}</p>
  if (!orders) return <p className="state-message">Loading your orders...</p>
  if (orders.length === 0) {
    return (
      <div className="my-orders">
        <h1>Ordered Goods</h1>
        <p className="state-message">
          You haven't ordered anything yet. <Link to="/">Browse products</Link>
        </p>
      </div>
    )
  }

  // api.myOrders() comes back newest-first (Order_ID DESC) - "from the
  // first ordered to the last" wants the opposite.
  const oldestFirst = [...orders].sort((a, b) => a.orderId - b.orderId)
  const groups = { delivered: [], assigned: [], pending: [], cancelled: [] }
  for (const order of oldestFirst) groups[stageOf(order)].push(order)

  return (
    <div className="my-orders">
      <h1>{view === 'ratings' ? 'My Ratings' : 'Ordered Goods'}</h1>
      {/* The main content in the middle, the other view's panel on the
          right (on top of it on narrow screens). */}
      <div className={ratings.hasRatings ? 'my-orders__layout my-orders__layout--with-ratings' : 'my-orders__layout'}>
        <div className="my-orders__main">
          {view === 'ratings' ? (
            <RatingsFullView ratings={ratings} />
          ) : (
            <>
              <GroupCard
                stage="delivered"
                icon="delivered"
                title="Delivered goods"
                note="Rate what you received - one rating per product, and you can change it any time."
                orders={groups.delivered}
                ratings={ratings}
              />
              <GroupCard stage="assigned" icon="truck" title="Assigned to a delivery" orders={groups.assigned} />
              <GroupCard stage="pending" icon="package" title="Not yet assigned to a delivery" orders={groups.pending} />
              <GroupCard stage="cancelled" icon="cancelled" title="Cancelled orders" orders={groups.cancelled} collapsed />
              {!oldestFirst.some((order) => (order.items || []).length > 0) && (
                <p className="state-message">No goods to show on these orders yet.</p>
              )}
            </>
          )}
        </div>
        {ratings.hasRatings &&
          (view === 'ratings' ? (
            <OrdersTab orders={orders} onBack={() => switchView('orders')} />
          ) : (
            <RatingsPanel ratings={ratings} onShowAll={() => switchView('ratings')} />
          ))}
      </div>
      <RatingsModal ratings={ratings} />
    </div>
  )
}

// "since in mobile the signing is done by the customer so if the product
// was signed then the product was delivered" - the delivery worker's
// signed hand-over is what sets deliveredAt; a heavy order marked
// delivered directly carries its own DELIVERED status instead.
function stageOf(order) {
  if (order.status === 'CANCELLED') return 'cancelled'
  if (order.deliveredAt || order.status === 'DELIVERED') return 'delivered'
  if (order.deliveryGroup || order.isScheduled) return 'assigned'
  return 'pending'
}

function GroupCard({ stage, icon, title, note, orders, ratings, collapsed = false }) {
  const [showAll, setShowAll] = useState(false)
  let cards = productCards(orders)
  if (cards.length === 0) return null
  // Delivered goods: newest delivery first, and goods delivered long ago
  // folded away until asked for (see deliveredGoods.js). Every other
  // group keeps first ordered -> latest.
  let hidden = 0
  if (stage === 'delivered') {
    cards = newestDeliveredFirst(cards)
    const shown = shownCount(cards)
    hidden = cards.length - shown
    if (!showAll) cards = cards.slice(0, shown)
  }
  const grid = (
    <>
      <ul className="my-orders__order-grid">
        {cards.map((card) => (
          <ProductCard key={card.productId} card={card} stage={stage} ratings={ratings} />
        ))}
      </ul>
      {hidden > 0 && (
        <button
          type="button"
          className="my-orders__show-more"
          aria-expanded={showAll}
          onClick={() => setShowAll((open) => !open)}
        >
          {showAll ? 'Show only recent deliveries' : `Show ${hidden} older delivered product${hidden === 1 ? '' : 's'}`}
          <span className="ratings-panel__chevron" aria-hidden="true" />
        </button>
      )}
    </>
  )
  if (collapsed) {
    return (
      <details className="my-orders__group-card my-orders__group-card--collapsed">
        <summary className="my-orders__group-title">
          <Icon name={icon} className="icon--inline" />
          {title} ({orders.length})
        </summary>
        {grid}
      </details>
    )
  }
  return (
    <section className={`my-orders__group-card my-orders__group-card--${stage}`}>
      <h2 className="my-orders__group-title">
        <Icon name={icon} className="icon--inline" />
        {title}
      </h2>
      {note && <p className="my-orders__group-note">{note}</p>}
      {grid}
    </section>
  )
}

// One card per product across these orders: how many orders it's in, how
// many pieces in total, and those orders. Orders without any products (an
// admin's delivery-run placeholder records) have nothing to show.
function productCards(orders) {
  const byProduct = new Map()
  for (const order of orders) {
    for (const item of order.items || []) {
      const key = item.productId ?? item.name
      if (key == null) continue
      let card = byProduct.get(key)
      if (!card) {
        card = {
          productId: item.productId,
          name: item.name || item.type,
          image: item.image,
          unit: item.productUnit,
          qty: 0,
          orders: [],
          lastDelivered: null,
          latestOrderId: 0,
        }
        byProduct.set(key, card)
      }
      card.qty += Number(item.qty) || 0
      if (!card.orders.includes(order)) card.orders.push(order)
      if (order.deliveredAt && (!card.lastDelivered || order.deliveredAt > card.lastDelivered)) {
        card.lastDelivered = order.deliveredAt
      }
      card.latestOrderId = Math.max(card.latestOrderId, order.orderId)
    }
  }
  return [...byProduct.values()]
}

// Hover/tap target is the product picture (to its page) and each order
// number (to that order's page) - the card as a whole holds links and a
// button, so it can't be one link itself.
function ProductCard({ card, stage, ratings }) {
  const rating = stage === 'delivered' ? ratingFor(card.productId, ratings) : null
  const shownOrders = card.orders.slice(0, 6)
  return (
    <li className="my-orders__order">
      <div className={rating ? 'my-orders__order-box my-orders__order-box--with-rating' : 'my-orders__order-box'}>
        <Link
          to={`/products/${card.productId}`}
          className="my-orders__order-box-thumb"
          style={card.image ? { backgroundImage: `url(${card.image})` } : undefined}
          aria-label={card.name}
        >
          {!card.image && <span className="my-orders__order-box-placeholder">{(card.name || '?')[0]}</span>}
          {card.orders.length > 1 && (
            <span className="my-orders__count-badge" aria-hidden="true">
              {'×'}
              {card.orders.length}
            </span>
          )}
        </Link>
        <div className="my-orders__order-box-body">
          <span className="my-orders__order-box-id">{card.name}</span>
          <span className="my-orders__order-box-date">
            {card.orders.length} order{card.orders.length === 1 ? '' : 's'} - {quantityLabel(card.qty, card.unit)}
          </span>
          <p className="my-orders__order-box-delivery">
            <Icon name={stageIcon(stage, card.orders)} size={15} className="icon--inline" />
            {stageMessage(stage, card.orders)}
          </p>
          <p className="my-orders__order-links">
            {shownOrders.map((order, index) => (
              <span key={order.orderId}>
                {index > 0 && ' '}
                <Link to={'/orders/' + encodeOrderRef(order.orderId)}>#{order.customerOrderNumber}</Link>
              </span>
            ))}
            {card.orders.length > shownOrders.length && ` +${card.orders.length - shownOrders.length} more`}
          </p>
        </div>
      </div>
      {/* Delivered goods only: one rating per product. */}
      {rating && (
        <div className="my-orders__order-rating">
          <button
            type="button"
            className={rating.rated ? 'my-orders__rate-btn my-orders__rate-btn--done' : 'my-orders__rate-btn'}
            onClick={() => ratings.setEditing(rating.target)}
          >
            {rating.rated ? (
              <>
                <Stars value={rating.target.rating} size="sm" decorative />
                <span>Rated - Edit</span>
              </>
            ) : (
              <>
                <Icon name="star" size={14} strokeWidth={2.2} />
                <span>Rate this product</span>
              </>
            )}
          </button>
        </div>
      )}
    </li>
  )
}

// The customer's rating to edit, or the "to rate" entry to start one;
// null when it can't be rated (e.g. the product has since been removed).
function ratingFor(productId, ratings) {
  const rated = ratings.rated.find((r) => r.productId === productId)
  if (rated) return { rated: true, target: rated }
  const toRate = ratings.toRate.find((r) => r.productId === productId)
  return toRate ? { rated: false, target: toRate } : null
}

function quantityLabel(qty, unit) {
  const name = unit || 'pieces'
  return `${qty} ${qty === 1 && name.endsWith('s') ? name.slice(0, -1) : name}`
}

// The icon drawn before stageMessage's text - same branches, same order.
function stageIcon(stage, orders) {
  if (stage === 'cancelled') return 'cancelled'
  if (stage === 'delivered') return 'delivered'
  return orders.some((order) => order.estimatedDeliveryDate) ? 'truck' : 'package'
}

function stageMessage(stage, orders) {
  if (stage === 'cancelled') return 'Cancelled'
  if (stage === 'delivered') {
    const dates = [...new Set(orders.map((order) => order.deliveredAt).filter(Boolean))].sort()
    if (dates.length === 0) return 'Delivered'
    return `${dates.length > 1 ? 'Last delivered' : 'Delivered'} on ${dates[dates.length - 1]}`
  }
  // Still to come: the soonest date among these orders ("Next:" when they
  // don't all share it).
  const dated = orders.filter((order) => order.estimatedDeliveryDate)
  if (dated.length === 0) return "We'll confirm a delivery date soon."
  const soonest = [...dated].sort((a, b) => a.estimatedDeliveryDate.localeCompare(b.estimatedDeliveryDate))[0]
  const sameDate = orders.every((order) => order.estimatedDeliveryDate === soonest.estimatedDeliveryDate)
  return (sameDate ? '' : 'Next: ') + deliveryMessage(soonest)
}

function deliveryMessage(order) {
  const days = Math.ceil((new Date(order.estimatedDeliveryDate) - new Date()) / (1000 * 60 * 60 * 24))
  const when = days > 0 ? 'in ' + days + ' day' + (days === 1 ? '' : 's') : 'any day now'
  // A scheduled flag means an admin has linked this order to a real
  // delivery run - a concrete promise, not the generic small/large-goods
  // SLA estimate, so it's worth calling out differently even though both
  // read from the same date field.
  if (order.isScheduled) {
    return (
      'Delivery scheduled for ' +
      order.estimatedDeliveryDate +
      ' (' +
      when +
      ')' +
      (order.vehicleIdentifier ? ' - vehicle ' + order.vehicleIdentifier : '')
    )
  }
  return 'Estimated delivery: ' + order.estimatedDeliveryDate + ' (' + when + ')'
}
