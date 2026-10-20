import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { api, apiErrorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { REVIEW_CONSENT_POINTS, REVIEW_CONSENT_TITLE, REVIEW_CONSENT_VERSION } from '../core/reviewConsent.js'
import { StarInput } from './Stars.jsx'
import Icon from './Icon.jsx'

const COMMENT_MAX = 500

// What the backend will show for a public rating - first name and the
// initial of the last name ("Timothy K."). Only a preview: the backend
// works the real one out from the account itself.
function publicNamePreview(fullName) {
  const parts = (fullName || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return 'A verified buyer'
  const word = parts[0]
  const singleCase = word === word.toLowerCase() || word === word.toUpperCase()
  const first = word[0].toUpperCase() + (singleCase ? word.slice(1).toLowerCase() : word.slice(1))
  return parts.length > 1 ? `${first} ${parts[parts.length - 1][0].toUpperCase()}.` : first
}

// Rate (or change the rating of) one delivered product. item: { productId,
// name, image } plus, when already rated, { rating, comment, isPublic,
// publicName, consentVersion, consentAt }. onSaved gets the customer's
// refreshed ratings (the same shape as api.myReviews).
export default function ReviewModal({ item, onClose, onSaved }) {
  const { user } = useAuth()
  const alreadyRated = item.rating != null
  const alreadyAgreed = Boolean(item.isPublic && item.consentVersion === REVIEW_CONSENT_VERSION)
  const [rating, setRating] = useState(item.rating || 0)
  const [comment, setComment] = useState(item.comment || '')
  const [makePublic, setMakePublic] = useState(Boolean(item.isPublic))
  // "has to read through the agreement area then tick yes" / "only appear
  // once you have scrolled to the bottom ... (no excuses later)" - the
  // "I agree" tick doesn't exist on the page at all until the agreement
  // has been scrolled to its end.
  const [readToEnd, setReadToEnd] = useState(alreadyAgreed)
  const [agreed, setAgreed] = useState(alreadyAgreed)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const agreementRef = useRef(null)
  const endRef = useRef(null)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [busy, onClose])

  // Read = the end of the agreement has been in view, however it got
  // there (wheel, touch, keyboard, or a screen tall enough to show it all).
  useEffect(() => {
    const box = agreementRef.current
    const end = endRef.current
    if (!makePublic || !box || !end) return undefined
    if (typeof IntersectionObserver === 'undefined') {
      if (box.scrollHeight <= box.clientHeight + 4) setReadToEnd(true)
      return undefined
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) setReadToEnd(true)
      },
      { root: box, threshold: 1 },
    )
    observer.observe(end)
    return () => observer.disconnect()
  }, [makePublic])

  function onAgreementScroll(event) {
    const box = event.currentTarget
    if (box.scrollTop + box.clientHeight >= box.scrollHeight - 8) setReadToEnd(true)
  }

  // Turning public off and on again means reading the agreement again.
  function togglePublic(checked) {
    setMakePublic(checked)
    if (!checked) {
      setAgreed(false)
      setReadToEnd(false)
    }
  }

  const canSave = rating > 0 && (!makePublic || agreed) && !busy

  async function save(event) {
    event.preventDefault()
    if (!canSave) return
    setBusy(true)
    setError(null)
    try {
      const result = await api.saveReview(
        item.productId,
        {
          rating,
          comment: comment.trim() || null,
          make_public: makePublic,
          consent_version: makePublic ? REVIEW_CONSENT_VERSION : null,
        },
        user.token,
      )
      onSaved(result, makePublic ? 'Thanks! Your public rating is saved.' : 'Thanks! Your rating is saved.')
    } catch (err) {
      setError(apiErrorMessage(err))
      setBusy(false)
    }
  }

  async function remove() {
    setBusy(true)
    setError(null)
    try {
      const result = await api.deleteReview(item.productId, user.token)
      onSaved(result, 'Your rating was removed.')
    } catch (err) {
      setError(apiErrorMessage(err))
      setBusy(false)
    }
  }

  return createPortal(
    <div className="modal-overlay" onClick={() => !busy && onClose()}>
      <div
        className="modal review-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="review-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <button type="button" className="modal__close" onClick={onClose} disabled={busy} aria-label="Close">
          &times;
        </button>
        <div className="review-modal__product">
          <div
            className="review-modal__thumb"
            style={item.image ? { backgroundImage: `url(${item.image})` } : undefined}
          >
            {!item.image && (item.name?.[0] ?? '?')}
          </div>
          <div>
            <h2 id="review-modal-title" className="review-modal__title">
              {alreadyRated ? 'Your rating' : 'Rate this product'}
            </h2>
            <p className="review-modal__name">{item.name}</p>
          </div>
        </div>

        <form className="review-modal__form" onSubmit={save}>
          <StarInput value={rating} onChange={setRating} />

          <label className="review-modal__comment">
            <span>Comment (optional)</span>
            <textarea
              value={comment}
              maxLength={COMMENT_MAX}
              rows={3}
              placeholder="How was the quality, size and finish?"
              onChange={(event) => setComment(event.target.value)}
            />
            <span className="review-modal__count">
              {comment.length}/{COMMENT_MAX}
            </span>
          </label>

          <fieldset className="review-modal__privacy">
            <legend>Who can see it</legend>
            <p className="review-modal__privacy-note">
              {makePublic
                ? 'Public: shown on the product page with your first name and initial.'
                : 'Private: it counts towards the product’s average stars, but nobody sees who gave it.'}
            </p>
            <label className="review-modal__check">
              <input type="checkbox" checked={makePublic} onChange={(event) => togglePublic(event.target.checked)} />
              <span>
                Make my rating public as <strong>{item.publicName || publicNamePreview(user?.name)}</strong>
              </span>
            </label>

            {makePublic && (
              <div className="review-consent">
                <p className="review-consent__title">{REVIEW_CONSENT_TITLE}</p>
                <div
                  ref={agreementRef}
                  className="review-consent__text"
                  tabIndex={0}
                  onScroll={onAgreementScroll}
                  aria-label="Public rating agreement - scroll to read it all"
                >
                  <ol>
                    {REVIEW_CONSENT_POINTS.map((point) => (
                      <li key={point}>{point}</li>
                    ))}
                  </ol>
                  <span ref={endRef} className="review-consent__end" aria-hidden="true" />
                </div>
                {alreadyAgreed && agreed && item.consentAt && (
                  <p className="review-consent__hint">You agreed to this on {item.consentAt}.</p>
                )}
                {readToEnd ? (
                  <label className="review-modal__check review-consent__agree">
                    <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
                    <span>Yes - I have read the agreement and agree to my rating being public.</span>
                  </label>
                ) : (
                  <p className="review-consent__hint review-consent__hint--scroll">
                    <Icon name="arrowDown" size={15} className="icon--inline" />
                    Scroll to the end of the agreement to continue.
                  </p>
                )}
              </div>
            )}
          </fieldset>

          {error && <p className="review-modal__error">{error}</p>}

          <div className="review-modal__actions">
            <button type="submit" className="btn btn--primary" disabled={!canSave}>
              {busy ? 'Saving...' : alreadyRated ? 'Save changes' : 'Submit rating'}
            </button>
            <button type="button" className="btn btn--secondary" onClick={onClose} disabled={busy}>
              Cancel
            </button>
          </div>

          {alreadyRated && (
            <div className="review-modal__remove">
              {confirmRemove ? (
                <>
                  <span>Remove your rating for good?</span>
                  <button type="button" className="btn--text" onClick={remove} disabled={busy}>
                    Yes, remove it
                  </button>
                  <button type="button" className="btn--link" onClick={() => setConfirmRemove(false)} disabled={busy}>
                    Keep it
                  </button>
                </>
              ) : (
                <button type="button" className="btn--text" onClick={() => setConfirmRemove(true)} disabled={busy}>
                  Remove my rating
                </button>
              )}
            </div>
          )}
        </form>
      </div>
    </div>,
    document.body,
  )
}
