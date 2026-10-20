import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import CampaignVideo from './CampaignVideo.jsx'
import Icon from './Icon.jsx'
import { formatEndDate } from '../context/FeaturedContext.jsx'

// Keeps a frame from turning into a sliver or a ribbon whatever the media.
const clampRatio = (width, height) => {
  if (!width || !height) return null
  return Math.min(2.4, Math.max(0.5, width / height))
}

// Each media's shape once known, for the rest of the visit: back on the
// product, or on another product in the campaign, the card has its size
// straight away instead of growing again (and moving the ratings below).
const shapes = new Map()

// The soft glow behind an upright video: the playing picture itself, drawn
// a few times a second into a tiny canvas that the styles blur and darken.
// Same-site media, so drawing it is allowed.
function VideoGlow({ video }) {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!video || !canvas) return undefined
    const context = canvas.getContext('2d')
    let last = 0
    const draw = (force) => {
      const now = performance.now()
      if (!force && now - last < 400) return
      last = now
      if (video.readyState < 2) return
      try {
        context.drawImage(video, 0, 0, canvas.width, canvas.height)
      } catch {
        // The frame isn't drawable yet - the next event tries again.
      }
    }
    const drawNow = () => draw(true)
    const drawSometimes = () => draw(false)
    // The first frame can be ready before this starts listening, or report
    // ready a moment before it can actually be drawn - so keep trying for a
    // few seconds until something lands, instead of a black glow until play.
    let tries = 0
    let retry = null
    const painted = () => {
      try {
        return context.getImageData(12, 12, 1, 1).data[3] > 0
      } catch {
        return true
      }
    }
    const fill = () => {
      draw(true)
      if (!painted() && tries++ < 20) retry = setTimeout(fill, 250)
    }
    video.addEventListener('loadeddata', drawNow)
    video.addEventListener('canplay', drawNow)
    video.addEventListener('seeked', drawNow)
    video.addEventListener('timeupdate', drawSometimes)
    fill()
    return () => {
      clearTimeout(retry)
      video.removeEventListener('loadeddata', drawNow)
      video.removeEventListener('canplay', drawNow)
      video.removeEventListener('seeked', drawNow)
      video.removeEventListener('timeupdate', drawSometimes)
    }
  }, [video])

  return <canvas ref={canvasRef} className="product-campaign__glow" width={24} height={24} aria-hidden="true" />
}

// The campaign this product is part of, on the product's page. It used to
// be a full-width player as tall as the screen - "its too big one might not
// know there is other descriptions of the product below it" - and then, a
// size down, an upright video sat in its card "like a rice seed in a bag".
// Now the media gets a stage at least as wide as it is tall: the whole
// video (or picture) in the middle, never cropped, with a soft blurred glow
// of itself filling the sides, beside a few words about the campaign.
export default function ProductCampaignCard({ campaign, productId }) {
  const [ratio, setRatio] = useState(() => shapes.get(campaign.media) ?? null)
  const [videoElement, setVideoElement] = useState(null)
  const learnShape = (width, height) => {
    const shape = clampRatio(width, height)
    if (shape) shapes.set(campaign.media, shape)
    setRatio(shape)
  }
  const until = formatEndDate(campaign.endAt)
  const others = campaign.products.filter((product) => product.productId !== productId)
  const isImage = campaign.mediaType === 'IMAGE'

  return (
    <section className="product-campaign" id="campaign" tabIndex={-1} aria-label={`Featured campaign: ${campaign.name}`}>
      <div
        className="product-campaign__frame"
        style={ratio ? { '--ratio': ratio, '--stage-ratio': Math.max(ratio, 1) } : undefined}
      >
        {isImage ? (
          <img className="product-campaign__glow" src={campaign.media} alt="" aria-hidden="true" />
        ) : (
          <VideoGlow video={videoElement} />
        )}
        {isImage ? (
          <img
            className="product-campaign__media"
            src={campaign.media}
            alt={campaign.name}
            onLoad={(event) => learnShape(event.currentTarget.naturalWidth, event.currentTarget.naturalHeight)}
          />
        ) : (
          <CampaignVideo
            className="product-campaign__media"
            src={campaign.media}
            onSize={learnShape}
            onElement={setVideoElement}
          />
        )}
      </div>
      <div className="product-campaign__text">
        <p className="product-campaign__label">
          <Icon name="star" size={14} strokeWidth={2.2} />
          Featured campaign{until ? ` · until ${until}` : ''}
        </p>
        <h2>{campaign.name}</h2>
        {others.length > 0 && (
          <>
            <p className="product-campaign__also">Also in this campaign</p>
            <div className="product-campaign__products">
              {others.map((product) => (
                <Link key={product.productId} to={`/products/${product.productId}`} className="media-card__chip">
                  {product.name}
                </Link>
              ))}
            </div>
          </>
        )}
        <Link to="/featured" className="product-campaign__more">See all featured</Link>
      </div>
    </section>
  )
}
