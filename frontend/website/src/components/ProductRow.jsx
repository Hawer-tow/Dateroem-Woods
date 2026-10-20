import ProductCard from './ProductCard.jsx'

// layout="scroll" (default) is the horizontal "featured" carousel - fine
// for a short curated row. layout="grid" wraps instead, so a long catalog
// doesn't turn into one endless horizontal strip to scroll through. Each
// product's own marketerNote (set on the product itself, in the mobile
// app) supplies its badge now, so this no longer threads one through.
export default function ProductRow({ title, products, layout = 'scroll', emptyMessage }) {
  if (!products?.length) {
    return emptyMessage ? <p className="state-message">{emptyMessage}</p> : null
  }
  return (
    <section className="product-row">
      {title && <h2>{title}</h2>}
      <div className={layout === 'grid' ? 'product-grid' : 'product-row__scroller'}>
        {products.map((product) => (
          <ProductCard key={product.productId} product={product} />
        ))}
      </div>
    </section>
  )
}
