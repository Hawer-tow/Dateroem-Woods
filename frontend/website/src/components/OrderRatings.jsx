import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import ReviewModal from './ReviewModal.jsx'
import { Stars } from './Stars.jsx'
import Icon from './Icon.jsx'

// "let the review happen after delivery ... they can still go to the
// website offer the ratings for the products they received" - the
// Ordered Goods page's ratings, loaded once and shared by every piece
// below:
//   Rate your goods  delivered products this customer hasn't rated yet
//   Your ratings     ones already rated - "once they rate a product then
//                    it can freely go out of their page but they can still
//                    access it e.g to make ratings private"
export function useOrderRatings(token) {
  const [data, setData] = useState(null)
  const [flash, setFlash] = useState(null)
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState(null)
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    api
      .myReviews(token)
      .then((res) => {
        if (!cancelled) setData(res)
      })
      // The orders themselves still show if ratings can't load.
      .catch(() => {
        if (!cancelled) setData({ toRate: [], rated: [] })
      })
    return () => {
      cancelled = true
    }
  }, [token])

  useEffect(() => {
    if (!flash) return undefined
    const timer = window.setTimeout(() => setFlash(null), 5000)
    return () => window.clearTimeout(timer)
  }, [flash])

  const onSaved = useCallback((result, message) => {
    setData(result)
    setEditing(null)
    setError(null)
    setFlash(message)
  }, [])

  // One tap back to private, without reopening the form - withdrawing
  // consent should never be harder than giving it.
  const makePrivate = useCallback(
    async (item) => {
      setBusyId(item.productId)
      setError(null)
      setFlash(null)
      try {
        const result = await api.saveReview(
          item.productId,
          { rating: item.rating, comment: item.comment, make_public: false, consent_version: null },
          token,
        )
        onSaved(result, `Your rating for ${item.name} is private now.`)
      } catch (err) {
        setError(apiErrorMessage(err))
      } finally {
        setBusyId(null)
      }
    },
    [token, onSaved],
  )

  return {
    toRate: data?.toRate || [],
    // Newest first from the backend, so rated[0] is the last one rated.
    rated: data?.rated || [],
    // The ratings panel/view only exist once something has been rated -
    // rating new goods happens on the Delivered goods card.
    hasRatings: Boolean(data?.rated?.length),
    flash,
    error,
    editing,
    setEditing,
    busyId,
    onSaved,
    makePrivate,
  }
}

// The side panel shows only the latest rating - "imagine i have rated more
// than 20 products ... on mobile I'll have to scroll through all that
// before I can get to the ordered products".
const RATED_PREVIEW = 1
// Enough ratings that finding one by eye gets slow - offer a search box.
const SEARCH_FROM = 6

// Orders view, right-hand side (on top on narrow screens): "place my
// ratings ... right of the main order goods cards for quicker
// identification" - the last one rated, one tap from Edit. "Show all"
// swaps the page to the ratings view below.
export function RatingsPanel({ ratings, onShowAll }) {
  const { rated, busyId, setEditing, makePrivate } = ratings
  return (
    <aside className="ratings-panel" aria-label="Your product ratings">
      <h2 className="ratings-panel__title">
        <Icon name="star" className="icon--inline" />
        My ratings
      </h2>
      <div className="ratings-panel__body">
        <RatingsMessages ratings={ratings} />

        {rated.length > 0 && (
          <section>
            <h3 className="ratings-panel__heading">
              Your ratings <span className="ratings-panel__count">{rated.length}</span>
            </h3>
            {rated.length > RATED_PREVIEW && <p className="ratings-panel__caption">Last rated</p>}
            <ul className="ratings-card__list">
              {rated.slice(0, RATED_PREVIEW).map((item) => (
                <RatedItem
                  key={item.productId}
                  item={item}
                  busy={busyId === item.productId}
                  onEdit={() => setEditing(item)}
                  onMakePrivate={() => makePrivate(item)}
                />
              ))}
            </ul>
            {rated.length > RATED_PREVIEW && (
              <button type="button" className="ratings-panel__toggle" onClick={onShowAll}>
                Show all {rated.length} ratings
                <span className="ratings-panel__chevron" aria-hidden="true" />
              </button>
            )}
          </section>
        )}
      </div>
    </aside>
  )
}

// Ratings view, right-hand side: "a button like tab like feature to go
// back to the ordered goods".
export function OrdersTab({ orders, onBack }) {
  const delivered = orders.filter((order) => order.deliveredAt).length
  return (
    <aside className="ratings-panel ratings-panel--tab" aria-label="Your ordered goods">
      <h2 className="ratings-panel__title">
        <Icon name="package" className="icon--inline" />
        Ordered Goods
      </h2>
      <div className="ratings-panel__body">
        <p className="ratings-panel__summary">
          {orders.length} order{orders.length === 1 ? '' : 's'}
          {delivered > 0 ? ` - ${delivered} delivered` : ''}
        </p>
        <button type="button" className="btn btn--primary ratings-panel__back" onClick={onBack}>
          <Icon name="arrowLeft" className="icon--inline" />
          Back to ordered goods
        </button>
      </div>
    </aside>
  )
}

// Ratings view, middle: "if you click the drop down where the ordered
// goods were we have the rated goods" - every rating, searchable, as cards.
export function RatingsFullView({ ratings }) {
  const { rated, busyId, setEditing, makePrivate } = ratings
  const [query, setQuery] = useState('')
  const needle = query.trim().toLowerCase()
  const shownRated = needle ? rated.filter((item) => (item.name || '').toLowerCase().includes(needle)) : rated

  return (
    <>
      <RatingsMessages ratings={ratings} className="ratings-full__messages" />

      {rated.length > 0 && (
        <section className="my-orders__group-card">
          <div className="ratings-full__head">
            <h2 className="my-orders__group-title ratings-full__title">
              <Icon name="pen" className="icon--inline" />
              Your ratings <span className="ratings-panel__count">{rated.length}</span>
            </h2>
            {rated.length >= SEARCH_FROM && (
              <input
                type="search"
                className="ratings-search"
                placeholder="Search your ratings..."
                aria-label="Search your ratings"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            )}
          </div>
          <ul className="ratings-grid">
            {shownRated.map((item) => (
              <RatedItem
                key={item.productId}
                item={item}
                busy={busyId === item.productId}
                onEdit={() => setEditing(item)}
                onMakePrivate={() => makePrivate(item)}
              />
            ))}
          </ul>
          {shownRated.length === 0 && <p className="ratings-panel__empty">No ratings match "{query.trim()}".</p>}
        </section>
      )}
    </>
  )
}

// The rating form, shared by both views.
export function RatingsModal({ ratings }) {
  const { editing, setEditing, onSaved } = ratings
  return editing ? <ReviewModal item={editing} onClose={() => setEditing(null)} onSaved={onSaved} /> : null
}

function RatingsMessages({ ratings, className = '' }) {
  const { flash, error } = ratings
  if (!flash && !error) return null
  return (
    <div className={className}>
      {flash && (
        <p className="ratings-flash" role="status">
          {flash}
        </p>
      )}
      {error && <p className="ratings-card__error">{error}</p>}
    </div>
  )
}

function RatedItem({ item, busy, onEdit, onMakePrivate }) {
  return (
    <li className="ratings-card__item ratings-card__item--rated">
      <Thumb item={item} />
      <div className="ratings-card__text">
        <Link to={`/products/${item.productId}`} className="ratings-card__name">
          {item.name}
        </Link>
        <span className="ratings-card__stars">
          <Stars value={item.rating} size="sm" /> <span className="ratings-card__meta">{item.ratedAt}</span>
        </span>
        <span className={item.isPublic ? 'ratings-card__chip ratings-card__chip--public' : 'ratings-card__chip'}>
          {item.isPublic ? `Public as ${item.publicName}` : 'Private'}
        </span>
        {item.isHidden && (
          <span className="ratings-card__hidden">
            Hidden by Dateroem - not shown on the product page or counted in its stars.
          </span>
        )}
        {item.comment && <p className="ratings-card__comment">{item.comment}</p>}
        <div className="ratings-card__buttons">
          <button type="button" className="btn btn--secondary ratings-card__action" onClick={onEdit}>
            Edit
          </button>
          {item.isPublic && (
            <button
              type="button"
              className="btn btn--secondary ratings-card__action"
              disabled={busy}
              onClick={onMakePrivate}
            >
              {busy ? 'Saving...' : 'Make private'}
            </button>
          )}
        </div>
      </div>
    </li>
  )
}

function Thumb({ item }) {
  return (
    <div className="ratings-card__thumb" style={item.image ? { backgroundImage: `url(${item.image})` } : undefined}>
      {!item.image && (item.name?.[0] ?? '?')}
    </div>
  )
}
