import { useEffect, useState } from 'react'
import { Link, useLocation, useParams, useNavigate } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import { useCart } from '../context/CartContext.jsx'
import ProductReviews, { RatingLine } from '../components/ProductReviews.jsx'
import BackArrowIcon from '../components/BackArrowIcon.jsx'
import ProductCampaignCard from '../components/ProductCampaignCard.jsx'
import Icon from '../components/Icon.jsx'
import ProductDescription, { descriptionPreview, isLongDescription } from '../components/ProductDescription.jsx'
import { useChatPage } from '../context/ChatNudgeContext.jsx'
import { useFeatured } from '../context/FeaturedContext.jsx'
import { jumpTo } from '../core/jumpTo.js'
import ForestJourney from '../components/forest/ForestJourney.jsx'
import ForestMore from '../components/forest/ForestMore.jsx'
import { FOREST_LOOK } from '../core/look.js'

export default function ProductDetailPage() {
  const { productId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { addItem } = useCart()
  const { campaignForProduct } = useFeatured()
  const [product, setProduct] = useState(null)
  const [error, setError] = useState(null)
  const [reviews, setReviews] = useState(null)
  const [added, setAdded] = useState(false)
  // "select the number of that product you want in the cart" right where
  // you can see it, with +/- buttons only (no free-typed number) - avoids
  // the mistake-upping risk of typing straight into a number field, here
  // and later in the cart itself.
  const [qty, setQtyLocal] = useState(1)
  // Lets the chat button notice a visitor lingering on this product (and
  // the chat know which product a question is about).
  useChatPage(product ? { kind: 'product', productId: product.productId, name: product.name, addedToCart: added } : null)

  useEffect(() => {
    let cancelled = false
    api
      .getProduct(productId)
      .then((res) => {
        if (!cancelled) setProduct(res)
      })
      .catch((err) => {
        if (!cancelled) setError(apiErrorMessage(err))
      })
    // Ratings are extra - the product still shows if they don't load.
    setReviews(null)
    api
      .productReviews(productId)
      .then((res) => {
        if (!cancelled) setReviews(res)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [productId])

  if (error) return <p className="state-message state-message--error">Couldn't load this product: {error}</p>
  if (!product) return <p className="state-message">Loading...</p>
  const campaign = campaignForProduct(product.productId)

  return (
    <div>
      {/* "make sure that there is back to the main products again" - the
          arrow goes back to wherever the visitor came from (the Featured
          page, a filtered list); "All products" is always the full
          catalogue. A product opened from a shared link has nowhere to go
          back to inside the site, so the arrow goes to the products too. */}
      <div className="product-detail__topbar">
        <button
          type="button"
          className="back-button"
          onClick={() => (location.key === 'default' ? navigate('/') : navigate(-1))}
          aria-label="Back"
        >
          <BackArrowIcon />
        </button>
        <Link to="/" className="product-detail__all-products">All products</Link>
      </div>
      <div className="product-detail">
      <div
        className="product-detail__image"
        style={product.image ? { backgroundImage: `url(${product.image})` } : undefined}
      >
        {!product.image && <span>{product.name?.[0] ?? '?'}</span>}
      </div>
      <div className="product-detail__info">
        <h1>{product.name}</h1>
        <RatingLine reviews={reviews} />
        {campaign && (campaign.media ? (
          <a href="#campaign" className="product-detail__featured" onClick={(event) => jumpTo(event, 'campaign')}>
            <Icon name="star" size={14} strokeWidth={2.2} />
            Featured in {campaign.name}
          </a>
        ) : (
          <Link to="/featured" className="product-detail__featured">
            <Icon name="star" size={14} strokeWidth={2.2} />
            Featured in {campaign.name}
          </Link>
        ))}
        <p className="product-detail__type">
          {product.type}
          {product.sku ? ` - SKU ${product.sku}` : ''}
        </p>
        {/* A long description would push the price and Order Now far
            down - only a short preview here, the rest in its own section. */}
        {isLongDescription(product.description) ? (
          <>
            <p className="product-detail__description product-detail__description--preview">
              {descriptionPreview(product.descriptionDisplay || product.description)}
            </p>
            <a href="#description" className="product-detail__more" onClick={(event) => jumpTo(event, 'description')}>
              Read the full description
              <Icon name="arrowDown" size={15} className="icon--after" />
            </a>
          </>
        ) : (
          <p className="product-detail__description">{product.description || 'No description provided yet.'}</p>
        )}
        {product.attributes && <p className="product-detail__attributes">{product.attributes}</p>}
        <p className="product-detail__price">KES {Number(product.price).toLocaleString()}</p>
        <p className="product-detail__stock">
          {Number(product.stock) > 0 ? `${product.stock} in stock` : 'Out of stock'}
        </p>
        <div className="product-detail__actions">
          {Number(product.stock) > 0 && (
            <div className="qty-stepper">
              <button
                type="button"
                className="qty-stepper__btn"
                onClick={() => setQtyLocal((current) => Math.max(1, current - 1))}
                disabled={qty <= 1}
                aria-label="Decrease quantity"
              >
                &minus;
              </button>
              <span className="qty-stepper__value">{qty}</span>
              <button
                type="button"
                className="qty-stepper__btn"
                onClick={() => setQtyLocal((current) => Math.min(Number(product.stock), current + 1))}
                disabled={qty >= Number(product.stock)}
                aria-label="Increase quantity"
              >
                +
              </button>
            </div>
          )}
          <button
            className="btn btn--primary"
            disabled={Number(product.stock) <= 0}
            onClick={() => {
              addItem(product, qty)
              setAdded(true)
              setQtyLocal(1)
            }}
          >
            {added ? 'Added to cart' : 'Order Now'}
          </button>
          {added && (
            <button className="btn btn--secondary" onClick={() => navigate('/cart')}>
              Go to cart
            </button>
          )}
        </div>
      </div>
      </div>
      {/* The forest look (25 October 2026): where the piece comes from and
          how it reaches you, right under it. */}
      {FOREST_LOOK && <ForestJourney />}
      {isLongDescription(product.description) && (
        <section className="product-about" id="description" tabIndex={-1}>
          <h2 className="product-about__heading">About this product</h2>
          {/* The AI-arranged copy when the backend has one; otherwise the
              built-in tidy-up arranges the text as typed. */}
          <ProductDescription text={product.descriptionDisplay || product.description} />
        </section>
      )}
      {/* The product's own details come first; its campaign follows as a
          card (the "Featured in" link above jumps here), then the reviews. */}
      {campaign?.media && <ProductCampaignCard campaign={campaign} productId={product.productId} />}
      <ProductReviews reviews={reviews} />
      {FOREST_LOOK && <ForestMore product={product} />}
    </div>
  )
}
