import { Stars } from './Stars.jsx'
import { jumpTo } from '../core/jumpTo.js'

function monthLabel(yearMonth) {
  const [year, month] = (yearMonth || '').split('-').map(Number)
  if (!year || !month) return ''
  return new Date(year, month - 1, 1).toLocaleString('en', { month: 'short', year: 'numeric' })
}

// The small line under the product name - links down to the full section
// once there is one; until then just "No ratings yet".
export function RatingLine({ reviews }) {
  if (!reviews) return null
  if (!reviews.shown) {
    return (
      <span className="rating-line rating-line--none">
        <Stars value={0} size="sm" decorative />
        <span className="rating-line__count">No ratings yet</span>
      </span>
    )
  }
  return (
    <a href="#reviews" className="rating-line" onClick={(event) => jumpTo(event, 'reviews')}>
      <Stars value={reviews.average} size="sm" />
      <span className="rating-line__average">{Number(reviews.average).toFixed(1)}</span>
      <span className="rating-line__count">
        {reviews.count} rating{reviews.count === 1 ? '' : 's'}
      </span>
    </a>
  )
}

// "show ratings only after 2-3 ratings ... an average of the ratings can
// be done" - the average, the star breakdown and the ratings customers
// agreed to make public (first name + initial only). Private ones count in
// the numbers and are only ever mentioned as a count. Until enough buyers
// have rated there's no section at all - "the customers dont need to see
// this" explanation.
export default function ProductReviews({ reviews }) {
  if (!reviews?.shown) return null

  const breakdown = reviews.breakdown || {}
  const publicReviews = reviews.reviews || []
  return (
    <section className="product-reviews" id="reviews" tabIndex={-1}>
      <h2 className="product-reviews__heading">Ratings</h2>
      <div className="product-reviews__overview">
        <div className="product-reviews__score">
          <span className="product-reviews__average">{Number(reviews.average).toFixed(1)}</span>
          <Stars value={reviews.average} size="lg" />
          <span className="product-reviews__total">
            average of {reviews.count} buyer rating{reviews.count === 1 ? '' : 's'}
          </span>
        </div>
        <ul className="product-reviews__bars">
          {[5, 4, 3, 2, 1].map((stars) => {
            const count = breakdown[stars] || 0
            return (
              <li key={stars} className="product-reviews__bar-row">
                <span className="product-reviews__bar-label">{stars} {'★'}</span>
                <span className="product-reviews__bar" aria-hidden="true">
                  <span style={{ width: `${reviews.count ? (count / reviews.count) * 100 : 0}%` }} />
                </span>
                <span className="product-reviews__bar-count" aria-label={`${count} rated ${stars} stars`}>
                  {count}
                </span>
              </li>
            )
          })}
        </ul>
      </div>

      {publicReviews.length > 0 && (
        <ul className="product-reviews__list">
          {publicReviews.map((review, index) => (
            <li key={`${review.name}-${review.ratedMonth}-${index}`} className="product-reviews__item">
              <div className="product-reviews__item-head">
                <span className="product-reviews__name">{review.name}</span>
                <Stars value={review.rating} size="sm" />
                <span className="product-reviews__month">{monthLabel(review.ratedMonth)}</span>
              </div>
              <p className="product-reviews__verified">Verified buyer - rated after delivery</p>
              {review.comment && <p className="product-reviews__comment">{review.comment}</p>}
            </li>
          ))}
        </ul>
      )}
      {reviews.privateCount > 0 && (
        <p className="product-reviews__private">
          {publicReviews.length > 0 ? '+ ' : ''}
          {reviews.privateCount} private rating{reviews.privateCount === 1 ? '' : 's'} - counted in the stars above; the
          buyers chose to keep {reviews.privateCount === 1 ? 'it' : 'them'} private.
        </p>
      )}
    </section>
  )
}
