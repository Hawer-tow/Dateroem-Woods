import { createContext, useContext, useEffect, useMemo, useReducer } from 'react'
import { getCookie, setCookie } from '../core/cookies.js'

// "_v2" since 18 October 2026: the products were renumbered, so a cart saved
// before could point at a different product with the same number.
const CART_COOKIE = 'dateroem_cart_v2'
const CartContext = createContext(null)

function loadCartFromCookie() {
  try {
    const raw = getCookie(CART_COOKIE)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function cartReducer(state, action) {
  switch (action.type) {
    case 'ADD': {
      const qty = Math.max(1, action.qty ?? 1)
      const existing = state.items.find((item) => item.productId === action.product.productId)
      if (existing) {
        return {
          items: state.items.map((item) =>
            item.productId === action.product.productId ? { ...item, qty: item.qty + qty } : item
          ),
        }
      }
      return {
        items: [
          ...state.items,
          {
            productId: action.product.productId,
            name: action.product.name,
            price: action.product.price,
            // "provide rose wood in the ordered goods with its image
            // since the above have not gotten it yet" - the cart record
            // never carried the product's own image before, so there was
            // nothing for the cart card to show even once it had a slot
            // for one.
            image: action.product.image ?? null,
            qty,
          },
        ],
      }
    }
    case 'REMOVE':
      return { items: state.items.filter((item) => item.productId !== action.productId) }
    case 'SET_QTY':
      return {
        items: state.items.map((item) =>
          item.productId === action.productId ? { ...item, qty: Math.max(1, action.qty) } : item
        ),
      }
    case 'CLEAR':
      return { items: [] }
    default:
      return state
  }
}

export function CartProvider({ children }) {
  // "when im in the cart and refresh the page all is lost" - this used to
  // only persist once functional-cookie consent was granted, so anyone who
  // hadn't explicitly accepted (the default) lost their cart on every
  // refresh. Remembering what someone actively put in their own cart is
  // the same kind of strictly-necessary bookkeeping as the auth token
  // (never gated on consent either) - it's what makes the page they're
  // already on work, not tracking - so this no longer depends on it.
  const [state, dispatch] = useReducer(cartReducer, { items: [] }, () => ({ items: loadCartFromCookie() }))

  useEffect(() => {
    setCookie(CART_COOKIE, JSON.stringify(state.items))
  }, [state])

  const value = useMemo(() => {
    const total = state.items.reduce((sum, item) => sum + item.price * item.qty, 0)
    const count = state.items.reduce((sum, item) => sum + item.qty, 0)
    return {
      items: state.items,
      total,
      count,
      addItem: (product, qty = 1) => dispatch({ type: 'ADD', product, qty }),
      removeItem: (productId) => dispatch({ type: 'REMOVE', productId }),
      setQty: (productId, qty) => dispatch({ type: 'SET_QTY', productId, qty }),
      clear: () => dispatch({ type: 'CLEAR' }),
    }
  }, [state])

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used within CartProvider')
  return ctx
}
