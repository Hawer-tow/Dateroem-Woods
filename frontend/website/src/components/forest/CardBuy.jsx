import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Icon from '../Icon.jsx'
import { useCart } from '../../context/CartContext.jsx'

// Buying straight from a product card in the forest look (25 October 2026:
// "place an order now and a cart on each product since one might want to
// order directly ... when they select the amount"). The amount picker counts
// what is already in the cart, so the cart never holds more than the stock;
// Add to cart says so for a moment and offers the cart, Order now goes
// straight there (where the delivery area and M-Pesa steps follow).
export default function CardBuy({ product }) {
  const { items, addItem } = useCart()
  const navigate = useNavigate()
  const inCart = items.find((item) => item.productId === product.productId)?.qty || 0
  const left = Math.max(0, Math.floor(Number(product.stock) || 0) - inCart)
  const [qty, setQty] = useState(1)
  const [added, setAdded] = useState(0)

  // Never more than is left, never less than one.
  useEffect(() => {
    setQty((current) => Math.min(Math.max(1, current), Math.max(1, left)))
  }, [left])

  // "Added" shows for a few seconds, then the button is ready again.
  useEffect(() => {
    if (!added) return undefined
    const timer = setTimeout(() => setAdded(0), 3500)
    return () => clearTimeout(timer)
  }, [added])

  if (Number(product.stock) <= 0) {
    return <p className="card-buy card-buy--note">Out of stock</p>
  }
  if (left === 0) {
    return (
      <div className="card-buy card-buy--note">
        <span>All {inCart} in your cart</span>
        <button type="button" className="card-buy__link" onClick={() => navigate('/cart')}>
          View cart
        </button>
      </div>
    )
  }

  return (
    <div className="card-buy">
      <div className="qty-stepper card-buy__qty">
        <button
          type="button"
          className="qty-stepper__btn"
          onClick={() => setQty((current) => Math.max(1, current - 1))}
          disabled={qty <= 1}
          aria-label={`Fewer ${product.name}`}
        >
          &minus;
        </button>
        <span className="qty-stepper__value" aria-live="polite">
          {qty}
        </span>
        <button
          type="button"
          className="qty-stepper__btn"
          onClick={() => setQty((current) => Math.min(left, current + 1))}
          disabled={qty >= left}
          aria-label={`More ${product.name}`}
        >
          +
        </button>
      </div>
      {added ? (
        <button
          type="button"
          className="btn btn--secondary card-buy__add card-buy__add--done"
          onClick={() => navigate('/cart')}
          aria-label={`Added ${added} to the cart - view cart`}
        >
          <Icon name="check" size={15} strokeWidth={2.4} />
          View cart
        </button>
      ) : (
        <button
          type="button"
          className="btn btn--secondary card-buy__add"
          onClick={() => {
            addItem(product, qty)
            setAdded(qty)
            setQty(1)
          }}
        >
          <Icon name="cart" size={15} />
          Add to cart
        </button>
      )}
      <button
        type="button"
        className="btn btn--primary card-buy__order"
        onClick={() => {
          addItem(product, qty)
          navigate('/cart')
        }}
      >
        Order now
      </button>
    </div>
  )
}
