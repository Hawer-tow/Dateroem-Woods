import { useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../components/Icon.jsx'
import StoryScroller from '../components/StoryScroller.jsx'
import { ABOUT_CLOSING, ABOUT_COMPANY, ABOUT_FOUNDER, ABOUT_HOW, ABOUT_INTRO, ABOUT_LOCATION } from '../core/aboutStory.js'

// "a new section about that has more information about the company" - a
// short introduction, the scroll-played journey from tree to doorstep, then
// who we are (the registered business, mission, vision, what we stand for)
// and the founder, and the way on to the shop. The words live in
// core/aboutStory.js.
export default function AboutPage() {
  return (
    <div className="about-page">
      <header className="about-hero">
        <p className="about-hero__eyebrow">{ABOUT_INTRO.eyebrow}</p>
        <h1 className="about-hero__title">{ABOUT_INTRO.title}</h1>
        <p className="about-hero__lead">{ABOUT_INTRO.lead}</p>
      </header>

      <StoryScroller />

      <section className="about-company" aria-labelledby="about-company-title">
        <div className="about-company__text">
          <p className="about-hero__eyebrow">{ABOUT_COMPANY.eyebrow}</p>
          <h2 id="about-company-title" className="about-section__title">{ABOUT_COMPANY.title}</h2>
          <p className="about-company__lead">{ABOUT_COMPANY.text}</p>
        </div>
        <dl className="about-facts">
          {ABOUT_COMPANY.facts.map(([label, value]) => (
            <div key={label} className="about-facts__row">
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="about-how" aria-labelledby="about-how-title">
        <h2 id="about-how-title" className="about-section__title">{ABOUT_HOW.title}</h2>
        <ol className="about-how__steps">
          {ABOUT_HOW.steps.map(([title, text], i) => (
            <li key={title} className="about-how__step">
              <span className="about-how__number" aria-hidden="true">{i + 1}</span>
              <h3>{title}</h3>
              <p>{text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="about-purpose" aria-label="Mission and vision">
        <article className="about-purpose__card">
          <h3>Our mission</h3>
          <p>{ABOUT_COMPANY.mission}</p>
        </article>
        <article className="about-purpose__card">
          <h3>Our vision</h3>
          <p>{ABOUT_COMPANY.vision}</p>
        </article>
      </section>

      <section className="about-values" aria-labelledby="about-values-title">
        <h2 id="about-values-title" className="about-section__title">What we stand for</h2>
        <ul className="about-values__list">
          {ABOUT_COMPANY.values.map((value) => (
            <li key={value}>{value}</li>
          ))}
        </ul>
      </section>

      <section className="about-location" aria-labelledby="about-location-title">
        <div className="about-location__pin" aria-hidden="true">
          <Icon name="pin" size={26} />
        </div>
        <div>
          <h2 id="about-location-title" className="about-section__title">{ABOUT_LOCATION.title}</h2>
          <p className="about-location__place">{ABOUT_LOCATION.place}</p>
          <address className="about-location__address">{ABOUT_LOCATION.address}</address>
          <p className="about-location__note">{ABOUT_LOCATION.note}</p>
          <a className="btn btn--secondary about-location__map" href={ABOUT_LOCATION.mapUrl} target="_blank" rel="noopener noreferrer">
            Open in Google Maps
          </a>
        </div>
      </section>

      <section className="about-founder" aria-labelledby="about-founder-title">
        <FounderPhoto />
        <div className="about-founder__text">
          <p className="about-hero__eyebrow">{ABOUT_FOUNDER.eyebrow}</p>
          <h2 id="about-founder-title" className="about-section__title">{ABOUT_FOUNDER.name}</h2>
          <p className="about-founder__role">{ABOUT_FOUNDER.role}, Dateroem Woods</p>
          <p className="about-founder__bio">{ABOUT_FOUNDER.text}</p>
        </div>
      </section>

      <section className="about-closing">
        <h2 className="about-closing__title">{ABOUT_CLOSING.title}</h2>
        <p className="about-closing__text">{ABOUT_CLOSING.text}</p>
        <div className="about-closing__actions">
          <Link to="/" className="btn btn--primary">
            Browse products
          </Link>
          <Link to="/heavy-order" className="btn btn--secondary">
            Bulk orders
          </Link>
          <Link to="/help" className="btn btn--secondary">
            Talk to us
          </Link>
        </div>
      </section>
    </div>
  )
}

// The founder's photo (public/brand/founder.jpg) over the company emblem.
// Until the photo is added - or while it loads - the frame shows the emblem
// instead of a broken picture.
function FounderPhoto() {
  const [missing, setMissing] = useState(false)
  return (
    <div className="about-founder__photo">
      <img src="/brand/emblem.svg" alt="" className="about-founder__placeholder" />
      {!missing && (
        <img
          src={ABOUT_FOUNDER.photo}
          alt={`${ABOUT_FOUNDER.name}, ${ABOUT_FOUNDER.role.toLowerCase()} of Dateroem Woods`}
          onError={() => setMissing(true)}
          loading="lazy"
        />
      )}
    </div>
  )
}
