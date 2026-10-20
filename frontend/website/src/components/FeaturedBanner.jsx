import { useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from './Icon.jsx'
import { useFeatured, formatEndDate } from '../context/FeaturedContext.jsx'

const HIDDEN_KEY = 'dateroem.featuredHidden'

function readHidden() {
  try {
    return new Set(JSON.parse(localStorage.getItem(HIDDEN_KEY) || '[]'))
  } catch {
    return new Set()
  }
}

// One slim strip at the top of the Products page for the newest live
// campaign - where customers already are, instead of a tab they'd skip.
// Never a pop-up, never sound: a video shows its first frame only (the
// Featured page has the player). Closing it hides every campaign live at
// that moment, so it doesn't come straight back with the next one; only
// a campaign that starts later shows it again.
export default function FeaturedBanner() {
  const { campaigns } = useFeatured()
  const [hidden, setHidden] = useState(readHidden)

  const showing = campaigns.filter((campaign) => !hidden.has(campaign.campaignId))
  if (showing.length === 0) return null

  const campaign = showing[0]
  const until = formatEndDate(campaign.endAt)
  const names = campaign.products.map((product) => product.name)
  const summary = names.length <= 2 ? names.join(' and ') : `${names.slice(0, 2).join(', ')} and ${names.length - 2} more`
  const productsLink = `/?campaign=${campaign.campaignId}`

  function hide() {
    const next = new Set(campaigns.map((c) => c.campaignId))
    setHidden(next)
    try {
      localStorage.setItem(HIDDEN_KEY, JSON.stringify([...next]))
    } catch {
      // Private window or storage blocked: hidden for this visit only.
    }
  }

  return (
    <section className="featured-banner" aria-label="Featured">
      <Link to={productsLink} className="featured-banner__media" tabIndex={-1} aria-hidden="true">
        {campaign.media ? (
          campaign.mediaType === 'IMAGE' ? (
            <img src={campaign.media} alt="" />
          ) : (
            <video src={`${campaign.media}#t=0.1`} muted playsInline preload="metadata" />
          )
        ) : (
          <Icon name="star" size={30} strokeWidth={1.5} />
        )}
      </Link>
      <div className="featured-banner__text">
        <p className="featured-banner__label">Featured{until ? ` · until ${until}` : ''}</p>
        <h2>{campaign.name}</h2>
        <p className="featured-banner__products">{summary}</p>
      </div>
      <div className="featured-banner__actions">
        <Link to={productsLink} className="btn btn--primary">See the products</Link>
        {campaigns.length > 1 && (
          <Link to="/featured" className="featured-banner__more">See all featured ({campaigns.length})</Link>
        )}
      </div>
      <button type="button" className="featured-banner__close" onClick={hide} aria-label="Hide this">
        <Icon name="close" size={15} />
      </button>
    </section>
  )
}
