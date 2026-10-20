import { Link } from 'react-router-dom'
import { Stars } from './Stars.jsx'
import Icon from './Icon.jsx'
import { useFeatured } from '../context/FeaturedContext.jsx'

export default function ProductCard({ product }) {
  // Products in a live campaign carry its name, bottom-left on the photo
  // (the marketer's badge keeps the top-left corner).
  const { campaignForProduct } = useFeatured()
  const campaign = campaignForProduct(product.productId)
  return (
    <Link to={`/products/${product.productId}`} className="product-card">
      <div
        className="product-card__image"
        style={product.image ? { backgroundImage: `url(${product.image})` } : undefined}
      >
        {!product.image && <span className="product-card__placeholder">{product.name?.[0] ?? '?'}</span>}
        {product.marketerNote && <span className="product-card__badge">{product.marketerNote}</span>}
        {campaign && (
          <span className="product-card__featured" title={`Featured: ${campaign.name}`}>
            <Icon name="star" size={12} strokeWidth={2.2} />
            <span className="product-card__featured-name">{campaign.name}</span>
          </span>
        )}
      </div>
      <div className="product-card__body">
        <h3>{product.name}</h3>
        <p className="product-card__price">KES {Number(product.price).toLocaleString()}</p>
        {/* The average of every buyer's stars, once enough have rated;
            until then, empty stars so shoppers know ratings exist. */}
        {product.rating ? (
          <p className="product-card__rating">
            <Stars value={product.rating.average} size="sm" />
            <span className="product-card__rating-average">{Number(product.rating.average).toFixed(1)}</span>
            <span className="product-card__rating-count">({product.rating.count})</span>
          </p>
        ) : (
          product.rating === null && (
            <p className="product-card__rating product-card__rating--none">
              <Stars value={0} size="sm" decorative />
              <span className="product-card__rating-count">No ratings yet</span>
            </p>
          )
        )}
      </div>
    </Link>
  )
}
