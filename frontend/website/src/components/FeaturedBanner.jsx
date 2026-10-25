import { useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from './Icon.jsx'
import { useFeatured, formatEndDate } from '../context/FeaturedContext.jsx'
import { FOREST_LOOK } from '../core/look.js'

const HIDDEN_KEY = 'dateroem.featuredHidden'

function readHidden() {
  try {
    return new Set(JSON.parse(localStorage.getItem(HIDDEN_KEY) || '[]'))
  } catch {
    return new Set()
  }
}

// The newest live campaign the visitor hasn't hidden - shared by this strip
// and the forest look's Featured column (forest/FeaturedSapling). Null when
// nothing is live; { hidden: true, show } when the visitor hid what is.
// Hiding covers every campaign live at that moment, so it doesn't come
// straight back with the next one; only a campaign that starts later shows
// it again - or the visitor bringing it back ("i hid the image im looking
// for a way to bring it back", 25 October 2026).
export function useFeaturedSpot() {
  const { campaigns } = useFeatured()
  const [hidden, setHidden] = useState(readHidden)

  function remember(next) {
    setHidden(next)
    try {
      localStorage.setItem(HIDDEN_KEY, JSON.stringify([...next]))
    } catch {
      // Private window or storage blocked: for this visit only.
    }
  }

  if (campaigns.length === 0) return null
  const showing = campaigns.filter((campaign) => !hidden.has(campaign.campaignId))
  if (showing.length === 0) return { hidden: true, show: () => remember(new Set()) }

  const campaign = showing[0]
  return {
    hidden: false,
    campaign,
    campaigns,
    names: campaign.products.map((product) => product.name),
    until: formatEndDate(campaign.endAt),
    productsLink: `/?campaign=${campaign.campaignId}`,
    hide: () => remember(new Set(campaigns.map((c) => c.campaignId))),
  }
}

// One slim strip at the top of the Products page for the newest live
// campaign - where customers already are, instead of a tab they'd skip.
// Never a pop-up, never sound: a video shows its first frame only (the
// Featured page has the player).
export default function FeaturedBanner() {
  const spot = useFeaturedSpot()
  if (!spot) return null
  // Hidden: the forest look leaves a small way back; the classic strip just
  // goes (the Featured tab still has it).
  if (spot.hidden) {
    return FOREST_LOOK ? (
      <button type="button" className="featured-banner__unhide" onClick={spot.show}>
        <Icon name="leaf" size={14} strokeWidth={1.9} />
        Show featured
      </button>
    ) : null
  }

  const { campaign, campaigns, names, until, productsLink, hide } = spot
  const summary = names.length <= 2 ? names.join(' and ') : `${names.slice(0, 2).join(', ')} and ${names.length - 2} more`

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
        <p className="featured-banner__label">
          {FOREST_LOOK && <Icon name="star" size={12} strokeWidth={2.2} />}
          Featured{until ? ` · until ${until}` : ''}
        </p>
        <h2>{campaign.name}</h2>
        {/* The forest look's spotlight names each product on its own chip
            (25 October 2026: "the way featured appears ... make it more
            presentable"). */}
        {FOREST_LOOK ? (
          <ul className="featured-banner__chips">
            {names.slice(0, 3).map((name, i) => (
              <li key={`${i}-${name}`}>{name}</li>
            ))}
            {names.length > 3 && <li className="featured-banner__chip-more">+{names.length - 3} more</li>}
          </ul>
        ) : (
          <p className="featured-banner__products">{summary}</p>
        )}
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
