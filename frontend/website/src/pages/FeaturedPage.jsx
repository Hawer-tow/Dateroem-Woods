import { Link } from 'react-router-dom'
import CampaignVideo from '../components/CampaignVideo.jsx'
import Icon from '../components/Icon.jsx'
import { useFeatured, formatEndDate } from '../context/FeaturedContext.jsx'

// Was the "Marketing" page, which showed customers staff details (Draft,
// the social channel, "? to ?" dates, "Product #12"). Customers now see
// only live campaigns, as things to look at and buy: the picture or
// video, the real product names, and a way into those products.
// "images and videos ... like cards and playable in that area only ...
// like netflix videos not outside it so it doesn't feel stuffed" - each
// video still plays inside its own card, never by itself.
export default function FeaturedPage() {
  const { campaigns, loaded, error } = useFeatured()

  if (!loaded) return <p className="state-message">Loading...</p>
  if (error) return <p className="state-message state-message--error">Couldn't reach the server: {error}</p>

  return (
    <div className="featured-page">
      <h1>Featured</h1>
      {campaigns.length === 0 ? (
        <div className="featured-page__empty">
          <p className="state-message">Nothing is featured right now.</p>
          <Link to="/" className="btn btn--primary">Browse all products</Link>
        </div>
      ) : (
        <div className="marketing-grid">
          {campaigns.map((campaign) => {
            const until = formatEndDate(campaign.endAt)
            return (
              <article key={campaign.campaignId} className="media-card">
                <div className="media-card__frame">
                  {campaign.media ? (
                    campaign.mediaType === 'IMAGE' ? (
                      <img className="media-card__media" src={campaign.media} alt={campaign.name} />
                    ) : (
                      <CampaignVideo className="media-card__media media-card__media--video" src={campaign.media} />
                    )
                  ) : (
                    <div className="media-card__placeholder">
                      <Icon name="star" size={40} strokeWidth={1.5} />
                    </div>
                  )}
                  {until && <span className="media-card__badge">Until {until}</span>}
                </div>
                <div className="media-card__body">
                  <h3>{campaign.name}</h3>
                  <div className="media-card__products">
                    {campaign.products.map((product) => (
                      <Link key={product.productId} to={`/products/${product.productId}`} className="media-card__chip">
                        {product.name}
                      </Link>
                    ))}
                  </div>
                  <Link to={`/?campaign=${campaign.campaignId}`} className="btn btn--primary media-card__cta">
                    See the products
                  </Link>
                </div>
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}
