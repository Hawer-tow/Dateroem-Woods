import { useRef } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../Icon.jsx'
import { useFeaturedSpot } from '../FeaturedBanner.jsx'
import { motionAllowed } from '../../core/look.js'

// The Featured section on wide screens in the forest look (25 October 2026,
// the owner's idea and picture): a slim young tree on the right of the
// products, its roots in a ball of soil. The campaign itself is inside the
// ball - its picture or video shows under the top of the soil with the roots
// growing over it ("make the featured section to be in that picture ... so
// that the space the featured takes is less"; "completely remove the test
// tube ... even slimmer"). A tag on the stem names it and a FEATURED heading
// above says what it is. The whole tree opens the campaign's products; a
// video plays, silently, while the mouse is over it. The pictures come from
// brand/featured/prepare_featured_plant.py. Narrower screens keep the
// spotlight card above the products (FeaturedBanner).
export default function FeaturedSapling() {
  const spot = useFeaturedSpot()
  const videoRef = useRef(null)

  if (!spot) return null
  // Hidden: folded into a slim FEATURED tab that brings it back.
  if (spot.hidden) {
    return (
      <aside className="featured-sapling featured-sapling--folded" aria-label="Featured">
        <button type="button" className="featured-sapling__unfold" onClick={spot.show} title="Show the featured section">
          <Icon name="leaf" size={16} strokeWidth={1.8} />
          <span>Featured</span>
        </button>
      </aside>
    )
  }
  const { campaign, campaigns, until, productsLink, hide } = spot
  const video = campaign.media && campaign.mediaType !== 'IMAGE'

  const play = () => {
    if (video && motionAllowed()) videoRef.current?.play().catch(() => {})
  }
  const pause = () => videoRef.current?.pause()

  return (
    <aside className="featured-sapling" aria-labelledby="featured-sapling-title">
      <p className="featured-sapling__label">
        <Icon name="leaf" size={16} strokeWidth={1.8} />
        Featured
      </p>
      <Link
        to={productsLink}
        className="featured-sapling__plant"
        onMouseEnter={play}
        onMouseLeave={pause}
        onFocus={play}
        onBlur={pause}
      >
        <img className="featured-sapling__crown" src="/forest/plant-crown.webp" alt="" width="296" height="425" />
        <span className="featured-sapling__roots">
          <img className="featured-sapling__ball" src="/forest/plant-roots.webp" alt="" width="296" height="603" />
          {campaign.media && (
            <>
              <span className="featured-sapling__window">
                <span className="featured-sapling__media">
                  {video ? (
                    <video ref={videoRef} src={`${campaign.media}#t=0.1`} muted loop playsInline preload="metadata" />
                  ) : (
                    <img src={campaign.media} alt="" />
                  )}
                </span>
              </span>
              <img className="featured-sapling__rootlines" src="/forest/plant-rootlines.webp" alt="" width="296" height="603" />
              {video && (
                <span className="featured-sapling__play" aria-hidden="true">
                  <Icon name="play" size={11} strokeWidth={0} />
                </span>
              )}
            </>
          )}
          <span className="featured-sapling__tag">
            <span id="featured-sapling-title" className="featured-sapling__name">
              {campaign.name}
            </span>
            <span className="featured-sapling__cta">
              See the products
              <Icon name="chevronRight" size={12} strokeWidth={2.4} />
            </span>
          </span>
        </span>
      </Link>
      {until && <p className="featured-sapling__until">Until {until}</p>}
      {campaigns.length > 1 && (
        <Link to="/featured" className="featured-sapling__more">
          All featured ({campaigns.length})
        </Link>
      )}
      <button type="button" className="featured-sapling__close" onClick={hide} aria-label="Hide the featured section">
        <Icon name="close" size={12} />
        Hide
      </button>
    </aside>
  )
}
