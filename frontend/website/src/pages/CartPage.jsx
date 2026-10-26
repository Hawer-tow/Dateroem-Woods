import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useCart } from '../context/CartContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { api, apiErrorMessage } from '../api/client.js'
import AuthModal from '../components/AuthModal.jsx'
import Icon from '../components/Icon.jsx'
import PhoneCodeStep from '../components/PhoneCodeStep.jsx'
import { encodeOrderRef } from '../core/orderRef.js'
import { isValidKenyanPhone, KENYAN_PHONE_HINT } from '../core/phone.js'
import { useChatPage } from '../context/ChatNudgeContext.jsx'

export default function CartPage() {
  const { items, total, removeItem, setQty, clear } = useCart()
  const { isAuthenticated, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [areas, setAreas] = useState([])
  // "take you to the deliveries area since its bigger, then once you
  // select the area then you come to the number area and pay" - the area
  // itself is picked on its own page (AreaSelectPage); it hands the choice
  // (and whatever phone number was already typed) back through router
  // state rather than cramming the full picker into this form.
  const [deliveryAreaId, setDeliveryAreaId] = useState(() =>
    location.state?.deliveryAreaId ? String(location.state.deliveryAreaId) : ''
  )
  const [phoneNumber, setPhoneNumber] = useState(() => location.state?.phoneNumber ?? '')
  const [confirmed, setConfirmed] = useState(false)
  // "provide a confirmation area for the number before stk pushing to
  // correct any errors made by the customer" - same pattern as the
  // delivery-area confirm below: shows the number back, and re-typing it
  // un-confirms so a last-second edit can't sneak through unconfirmed.
  const [phoneConfirmed, setPhoneConfirmed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  // "let that rotate until safaricom receives the cash and gives
  // successful feedback then we can proceed to create a new order" -
  // 'sending' covers the checkout call itself (create the order, send the
  // push); 'confirming' covers the wait after that for Safaricom's own
  // answer. The order the backend creates during 'sending' is real but
  // still PENDING - it's only ever shown to the customer, and the cart
  // only ever cleared, once 'confirming' resolves to an actual COMPLETED.
  const [waitStage, setWaitStage] = useState(null)
  // The new order's ticket (DW-7K3Q9P), shown while M-Pesa confirms.
  const [waitTicket, setWaitTicket] = useState(null)
  const [error, setError] = useState(null)
  const [showAuth, setShowAuth] = useState(false)
  // Lets the chat button notice someone lingering before checkout - and
  // never while an order is being placed or paid for.
  useChatPage({
    kind: 'cart',
    items: items.map((item) => ({ productId: item.productId, qty: item.qty })),
    paying: submitting || Boolean(waitStage),
  })

  useEffect(() => {
    api.listShippingAreas().then(setAreas).catch(() => setAreas([]))
  }, [])

  // "when a user is logged in do prefill sections like phone number" - the
  // M-Pesa number starts as the phone on their account (only if nothing was
  // typed yet). It stays editable, and the "I confirm ... is the correct
  // number" tick is still needed before paying. "so when prefilling it
  // during payment is not wrong" - while SMS codes are on, only a number
  // the customer confirmed with a code is filled in (phoneCodeRequired is
  // true until they have).
  const [needsPhoneCode, setNeedsPhoneCode] = useState(false)
  useEffect(() => {
    if (!user?.token) return undefined
    let cancelled = false
    api.me(user.token)
      .then((me) => {
        if (cancelled) return
        setNeedsPhoneCode(Boolean(me?.phoneCodeRequired))
        if (me?.phoneNo && !me.phoneCodeRequired) setPhoneNumber((current) => current || me.phoneNo)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [user?.token])

  // "push a code to the phone number they placed so that its verified" - an
  // account made before SMS codes (or whose number an admin changed)
  // confirms its number once, here, before the first order: {phone, stage}
  // where stage is 'enter' (the number, editable) or 'code' (typing the code).
  const [phoneCheck, setPhoneCheck] = useState(null)
  const [phoneCheckError, setPhoneCheckError] = useState(null)

  function storedToken() {
    try {
      return JSON.parse(localStorage.getItem('dateroem_auth') || 'null')?.token ?? null
    } catch {
      return null
    }
  }

  function askToConfirmPhone(accountPhone) {
    setPhoneCheckError(null)
    setPhoneCheck({ phone: accountPhone || phoneNumber.trim(), stage: 'enter' })
  }

  function phoneVerified(res) {
    setPhoneCheck(null)
    setNeedsPhoneCode(false)
    if (res?.phoneNo) setPhoneNumber((current) => current.trim() || res.phoneNo)
    placeOrder()
  }

  // Polls the same order-status endpoint the confirmation page itself
  // polls (it reconciles with Safaricom on each read - see OrderService's
  // own reconcile-on-read logic), just from here first, before ever
  // showing the customer an order at all. ~4 minutes total - real Daraja
  // (unlike the sandbox) commonly takes a good minute or two to answer.
  const CONFIRM_POLL_INTERVAL_MS = 3000
  const CONFIRM_MAX_POLLS = 80

  async function waitForPaymentOutcome(orderId, token) {
    for (let attempt = 0; attempt < CONFIRM_MAX_POLLS; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, CONFIRM_POLL_INTERVAL_MS))
      try {
        const order = await api.getOrder(orderId, token)
        if (order.paymentStatus === 'COMPLETED') return 'COMPLETED'
        if (order.paymentStatus === 'FAILED' || order.status === 'CANCELLED') return 'FAILED'
      } catch {
        // A transient read error - not a payment outcome, keep polling.
      }
    }
    return 'TIMEOUT'
  }

  async function placeOrder() {
    // Read the token straight from storage rather than the AuthContext
    // closure: this can run immediately after a login/register just
    // completed, before this component's next render has picked up the
    // fresh context value.
    const token = storedToken()
    if (!token) {
      setShowAuth(true)
      return
    }
    setError(null)
    setSubmitting(true)
    // Checked fresh each time: the account may have just been signed into.
    const me = await api.me(token).catch(() => null)
    if (me?.phoneCodeRequired) {
      setSubmitting(false)
      askToConfirmPhone(me.phoneNo)
      return
    }
    setWaitStage('sending')
    try {
      const result = await api.checkout(
        {
          delivery_area_id: Number(deliveryAreaId),
          items: items.map((item) => ({ product_id: item.productId, qty: item.qty })),
          phone_number: phoneNumber.trim() || null,
        },
        token
      )
      setWaitTicket(result.ticketCode || null)
      setWaitStage('confirming')
      const outcome = await waitForPaymentOutcome(result.orderId, token)
      if (outcome === 'COMPLETED') {
        clear()
        navigate(`/orders/${encodeOrderRef(result.orderId)}`)
      } else if (outcome === 'FAILED') {
        // "failure to a successful stk push take some one back to the
        // cart to try again" - the backend has already cancelled this
        // order and given the stock back (OrderService.
        // cancelUnpaidOrder); the cart itself was never touched, so
        // everything's still right here to just try again.
        setError("The M-Pesa payment wasn't completed, so the order wasn't placed. Check the number and try again.")
      } else {
        setError(
          "Still waiting on M-Pesa - if you already paid, check \"My Orders\" in a moment; otherwise the number may " +
            'not have gotten the prompt. Try again.'
        )
      }
    } catch (err) {
      const message = apiErrorMessage(err)
      if (/confirm your phone number/i.test(message)) {
        askToConfirmPhone(me?.phoneNo)
      } else {
        setError(message)
      }
    } finally {
      setSubmitting(false)
      setWaitStage(null)
    }
  }

  function handleCheckout(event) {
    event.preventDefault()
    if (!deliveryAreaId) {
      setError('Choose a delivery/pickup area')
      return
    }
    if (!confirmed) {
      setError('Please confirm your delivery area is correct before placing the order')
      return
    }
    // "make that section a must to fill so we confirm im not the
    // problem" - required now, so a blank field can never again be the
    // silent explanation for "no prompt showed up".
    if (!phoneNumber.trim()) {
      setError('Enter your M-Pesa number to receive the payment prompt')
      return
    }
    // "can that check for a valid number take place in the confirmative
    // area immediately there to prevent even proceeding to an attempt" -
    // caught here too (belt-and-suspenders with the inline check below
    // that already hides the confirm checkbox for an invalid number).
    if (!isValidKenyanPhone(phoneNumber)) {
      setError(KENYAN_PHONE_HINT)
      return
    }
    if (!phoneConfirmed) {
      setError('Please confirm your M-Pesa number is correct before placing the order')
      return
    }
    // Browsing and building a cart never require an account - only the
    // final "place order" step does, and it's a nudge (a modal right here),
    // not a wall the visitor has to fight through earlier.
    if (!isAuthenticated) {
      setShowAuth(true)
      return
    }
    placeOrder()
  }

  const selectedArea = areas.find((area) => String(area.deliveryAreaId) === String(deliveryAreaId))

  // "when once clicks cart and there is nothing there provide a link to the
  // products page" (26 October 2026) - on a phone the menu at the top is
  // easy to miss, so the way back is right here.
  if (items.length === 0) {
    return (
      <div className="cart-empty">
        <Icon name="cart" size={34} strokeWidth={1.5} />
        <p className="cart-empty__title">Your cart is empty</p>
        <p className="cart-empty__text">Browse the timber and add what you need.</p>
        <Link to="/" className="btn btn--primary">Browse products</Link>
      </div>
    )
  }

  function chooseArea() {
    navigate('/cart/area', { state: { selectedId: deliveryAreaId || null, phoneNumber } })
  }

  return (
    <div className="cart-page">
      <h1>Your Cart</h1>
      <div className="cart-page__body">
        <div className="cart-page__items">
          <ul className="cart-list">
            {items.map((item) => (
              <li key={item.productId} className="cart-list__item">
                {/* "place them like cards the product ordered as well as
                    a copy of the image of the product" - the cart record
                    never carried a product's image before (see
                    CartContext's addItem), so there was nothing to show
                    here even with a slot ready for it. Falls back to an
                    initial letter, same treatment ProductCard already
                    uses for a product with no image on file. */}
                <div
                  className="cart-list__thumb"
                  style={item.image ? { backgroundImage: `url(${item.image})` } : undefined}
                >
                  {!item.image && <span className="cart-list__thumb-placeholder">{item.name?.[0] ?? '?'}</span>}
                </div>
                <div className="cart-list__info">
                  <span className="cart-list__name">{item.name}</span>
                  {/* "batched together ... in a very ordery manner ... not
                      like products" - name up top, then the controls that
                      go together (stepper, price, remove) close together
                      on the line below, instead of one row stretched wide
                      with a big dead gap wherever the name happened to be
                      short. */}
                  <div className="cart-list__controls">
                    {/* +/- only, no free-typed number - "prevent mistake
                        upping of products in the cart area or during
                        payment". */}
                    <div className="qty-stepper qty-stepper--compact">
                      <button
                        type="button"
                        className="qty-stepper__btn"
                        onClick={() => setQty(item.productId, item.qty - 1)}
                        disabled={item.qty <= 1}
                        aria-label={`Decrease ${item.name} quantity`}
                      >
                        &minus;
                      </button>
                      <span className="qty-stepper__value">{item.qty}</span>
                      <button
                        type="button"
                        className="qty-stepper__btn"
                        onClick={() => setQty(item.productId, item.qty + 1)}
                        aria-label={`Increase ${item.name} quantity`}
                      >
                        +
                      </button>
                    </div>
                    <span className="cart-list__price">KES {(item.price * item.qty).toLocaleString()}</span>
                    <button
                      type="button"
                      className="cart-list__remove"
                      onClick={() => removeItem(item.productId)}
                      aria-label={`Remove ${item.name}`}
                      title="Remove"
                    >
                      &times;
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
          <p className="cart-page__total">Total: KES {total.toLocaleString()}</p>
        </div>

        <form className="checkout-form" onSubmit={handleCheckout}>
          {!isAuthenticated && (
            <p className="checkout-form__note">You'll be asked to sign in or create an account before we place the order.</p>
          )}
          {isAuthenticated && needsPhoneCode && (
            <p className="checkout-form__note">
              Before your first order we'll text a code to confirm the phone number on your account.
            </p>
          )}
          <div className="checkout-form__areas">
            <p className="checkout-form__label">Delivery / pickup area</p>
            {selectedArea ? (
              <div className="checkout-form__area-summary">
                <span>
                  <strong>{selectedArea.areaName}</strong>, {selectedArea.city}
                </span>
                <button type="button" className="btn btn--secondary" onClick={chooseArea}>
                  Change
                </button>
              </div>
            ) : (
              <button type="button" className="btn btn--secondary" onClick={chooseArea}>
                Choose delivery / pickup area
              </button>
            )}
          </div>
          {deliveryAreaId && (
            <label className="checkout-form__confirm">
              <input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
              I confirm {selectedArea ? <strong>{selectedArea.areaName}, {selectedArea.city}</strong> : 'this'} is the correct
              delivery/pickup area for this order
            </label>
          )}
          <label>
            M-Pesa number
            <input
              type="tel"
              placeholder="07XX XXX XXX"
              value={phoneNumber}
              onChange={(event) => {
                setPhoneNumber(event.target.value)
                setPhoneConfirmed(false)
              }}
              required
            />
          </label>
          <p className="checkout-form__note">
            Any format works - 07XXXXXXXX, 01XXXXXXXX, or 2547XXXXXXXX. We'll send an M-Pesa payment prompt here once you place the order.
          </p>
          {/* "can that check for a valid number take place in the
              confirmative area immediately there to prevent even
              proceeding to an attempt" - checked live, right in this
              same confirm area, instead of waiting for the server's own
              400 after placing the order. An invalid number gets the
              error instead of the confirm checkbox - there's nothing
              correct to confirm yet. */}
          {phoneNumber.trim() && !isValidKenyanPhone(phoneNumber) && (
            <p className="state-message state-message--error">{KENYAN_PHONE_HINT}</p>
          )}
          {phoneNumber.trim() && isValidKenyanPhone(phoneNumber) && (
            <label className="checkout-form__confirm">
              <input
                type="checkbox"
                checked={phoneConfirmed}
                onChange={(event) => setPhoneConfirmed(event.target.checked)}
              />
              I confirm <strong>{phoneNumber.trim()}</strong> is the correct number to send the payment prompt to
            </label>
          )}
          {error && <p className="state-message state-message--error">{error}</p>}
          <button
            className="btn btn--primary"
            type="submit"
            disabled={
              submitting || !deliveryAreaId || !confirmed || !isValidKenyanPhone(phoneNumber) || !phoneConfirmed
            }
          >
            {submitting ? 'Placing order...' : 'Place order'}
          </button>
          <p className="cart-terms-note">
            By placing your order you agree to our <Link to="/terms">Terms</Link> and{' '}
            <Link to="/returns">Returns &amp; Refunds</Link>.
          </p>
        </form>
      </div>

      {/* "the stk message ... make it look good e.g a pop up screen that
          shows waiting for confirmation ... let that rotate until
          safaricom receives the cash and gives successful feedback" -
          this now keeps rotating for as long as the whole thing takes
          (sending the prompt, then waiting on Safaricom's own answer -
          often a couple of minutes for real Daraja), not just the first
          few seconds of it. */}
      {waitStage && (
        <div className="modal-overlay">
          <div className="modal modal--waiting">
            <div className="checkout-waiting__spinner" aria-hidden="true" />
            {waitStage === 'sending' ? (
              <>
                <p className="checkout-waiting__title">Sending payment prompt&hellip;</p>
                <p className="checkout-waiting__hint">
                  Check <strong>{phoneNumber.trim()}</strong> for the M-Pesa prompt and enter your PIN.
                </p>
              </>
            ) : (
              <>
                <p className="checkout-waiting__title">Waiting for M-Pesa to confirm&hellip;</p>
                <p className="checkout-waiting__hint">
                  This can take a couple of minutes - the order only goes through once M-Pesa confirms the payment.
                </p>
                {waitTicket && (
                  <p className="checkout-waiting__ticket">
                    Your ticket: <strong className="ticket-code">{waitTicket}</strong>
                  </p>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {phoneCheck && (
        <div className="modal-overlay">
          <div className="modal modal--phone" role="dialog" aria-modal="true" aria-labelledby="phone-check-title">
            <h2 id="phone-check-title" className="phone-check__title">
              Confirm your phone number
            </h2>
            <p className="phone-check__lead">
              A one-time step before your first order: we'll text a 6-digit code to the phone number for your account.
            </p>
            {phoneCheck.stage === 'enter' ? (
              <form
                className="modal__form"
                onSubmit={(event) => {
                  event.preventDefault()
                  if (!isValidKenyanPhone(phoneCheck.phone)) {
                    setPhoneCheckError(KENYAN_PHONE_HINT)
                    return
                  }
                  setPhoneCheckError(null)
                  setPhoneCheck({ phone: phoneCheck.phone.trim(), stage: 'code' })
                }}
              >
                <label>
                  Your phone number
                  <input
                    type="tel"
                    inputMode="tel"
                    autoComplete="tel"
                    placeholder="07XXXXXXXX"
                    value={phoneCheck.phone}
                    onChange={(event) => setPhoneCheck({ phone: event.target.value, stage: 'enter' })}
                    required
                    autoFocus
                  />
                </label>
                {phoneCheckError && <p className="state-message state-message--error">{phoneCheckError}</p>}
                <button className="btn btn--primary" type="submit">
                  Send code
                </button>
                <button type="button" className="btn--link" onClick={() => setPhoneCheck(null)}>
                  Not now
                </button>
              </form>
            ) : (
              <PhoneCodeStep
                phone={phoneCheck.phone}
                send={(phone) => api.sendPhoneCode(phone, storedToken())}
                verify={(phone, code) => api.verifyPhoneCode(phone, code, storedToken())}
                onVerified={phoneVerified}
                onBack={() => setPhoneCheck({ phone: phoneCheck.phone, stage: 'enter' })}
              />
            )}
          </div>
        </div>
      )}

      {showAuth && (
        <AuthModal
          message="Sign in or create a free account to complete your order."
          onClose={(success) => {
            setShowAuth(false)
            // Only auto-retry when the modal closed because sign-in just
            // succeeded - dismissing it (the X button) should mean "never
            // mind", not immediately reopen the same prompt.
            if (success) placeOrder()
          }}
        />
      )}
    </div>
  )
}
