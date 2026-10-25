import { useEffect, useState } from 'react'
import { api } from '../../api/client.js'
import ProductRow from '../ProductRow.jsx'

// "More from the forest" at the end of a product page (forest look, 25
// October 2026): up to four other products, the same kind of wood first.
// It is extra - if the list doesn't load, the page simply ends earlier.
// Each card carries its ratings, or "No ratings yet", as on the home page.
export default function ForestMore({ product }) {
  const [others, setOthers] = useState([])

  useEffect(() => {
    let cancelled = false
    Promise.all([api.listProducts(), api.reviewSummaries().catch(() => null)])
      .then(([products, ratings]) => {
        if (cancelled) return
        const list = products.map((item) => ({
          ...item,
          rating: ratings ? ratings[item.productId] || null : undefined,
        }))
        const rest = list.filter((item) => String(item.productId) !== String(product.productId))
        const sameKind = rest.filter((item) => item.type && item.type === product.type)
        const otherKinds = rest.filter((item) => !(item.type && item.type === product.type))
        setOthers([...sameKind, ...otherKinds].slice(0, 4))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [product.productId, product.type])

  return (
    <div className="forest-more">
      <ProductRow title="More from the forest" products={others} layout="grid" />
    </div>
  )
}
