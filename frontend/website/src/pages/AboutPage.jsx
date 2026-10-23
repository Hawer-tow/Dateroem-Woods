import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client.js'
import Icon from '../components/Icon.jsx'
import StoryScroller from '../components/StoryScroller.jsx'
import { ABOUT_CLOSING, ABOUT_COMPANY, ABOUT_FOUNDER, ABOUT_HOW, ABOUT_INTRO, ABOUT_LOCATION } from '../core/aboutStory.js'

// "a new section about that has more information about the company" - a
// short introduction, the scroll-played journey from tree to doorstep, then
// who we are (the registered business), how we work, what drives us, where
// to find and reach us, and the founder, and the way on to the shop. The
// words live in core/aboutStory.js.
//
// "the rest need to be ordered good especially in big screens ... easy to
// spot the business information" (23 October 2026) - every section after
// the film follows the one the owner liked: the words on the left, the facts
// in a card on the right, in one centred column.
export default function AboutPage() {
  return (
    <div className="about-page">
      <header className="about-hero">
        <p className="about-hero__eyebrow">{ABOUT_INTRO.eyebrow}</p>
        <h1 className="about-hero__title">{ABOUT_INTRO.title}</h1>
        <p className="about-hero__lead">{ABOUT_INTRO.lead}</p>
      </header>

      <StoryScroller />

      <div className="about-body">
        <section className="about-split" aria-labelledby="about-company-title">
          <div>
            <p className="about-hero__eyebrow">{ABOUT_COMPANY.eyebrow}</p>
            <h2 id="about-company-title" className="about-section__title">{ABOUT_COMPANY.title}</h2>
            <p className="about-section__lead">{ABOUT_COMPANY.text}</p>
          </div>
          <dl className="about-card about-facts">
            {ABOUT_COMPANY.facts.map(([label, value]) => (
              <div key={label} className="about-facts__row">
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="about-how" aria-labelledby="about-how-title">
          <header className="about-section__head">
            <p className="about-hero__eyebrow">{ABOUT_HOW.eyebrow}</p>
            <h2 id="about-how-title" className="about-section__title">{ABOUT_HOW.title}</h2>
            <p className="about-section__lead">{ABOUT_HOW.lead}</p>
          </header>
          <ol className="about-how__steps">
            {ABOUT_HOW.steps.map(([title, text], i) => (
              <li key={title} className="about-how__step">
                <span className="about-how__marker" aria-hidden="true">
                  <span className="about-how__number">{i + 1}</span>
                </span>
                <h3>{title}</h3>
                <p>{text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="about-split" aria-labelledby="about-purpose-title">
          <div>
            <p className="about-hero__eyebrow">{ABOUT_COMPANY.purposeEyebrow}</p>
            <h2 id="about-purpose-title" className="about-section__title">{ABOUT_COMPANY.purposeTitle}</h2>
            <div className="about-purpose__item">
              <h3>Our mission</h3>
              <p>{ABOUT_COMPANY.mission}</p>
            </div>
            <div className="about-purpose__item">
              <h3>Our vision</h3>
              <p>{ABOUT_COMPANY.vision}</p>
            </div>
          </div>
          <div className="about-card">
            <h3 className="about-card__title">What we stand for</h3>
            <ul className="about-values">
              {ABOUT_COMPANY.values.map((value) => (
                <li key={value}>
                  <span className="about-values__tick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  <span>{value}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="about-split" aria-labelledby="about-location-title">
          <div>
            <p className="about-hero__eyebrow">{ABOUT_LOCATION.eyebrow}</p>
            <h2 id="about-location-title" className="about-section__title">{ABOUT_LOCATION.title}</h2>
            <p className="about-location__place">
              <Icon name="pin" size={18} className="icon--inline" /> {ABOUT_LOCATION.place}
            </p>
            <address className="about-location__address">{ABOUT_LOCATION.address}</address>
            <p className="about-section__lead">{ABOUT_LOCATION.note}</p>
            <div className="about-location__actions">
              <a className="btn btn--primary" href={ABOUT_LOCATION.mapUrl} target="_blank" rel="noopener noreferrer">
                Open in Google Maps
              </a>
              <Link to="/help" className="btn btn--secondary">
                Contact us
              </Link>
            </div>
          </div>
          <ReachUs />
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
      </div>

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

// The company's own contacts, from the website server's settings (the same
// ones the footer and Help page show) - never anyone's personal number.
function ReachUs() {
  const [contact, setContact] = useState(null)

  useEffect(() => {
    api
      .helpContact()
      .then(setContact)
      .catch(() => setContact(null))
  }, [])

  if (!contact) return null
  const outside = { target: '_blank', rel: 'noopener noreferrer' }
  const rows = [
    contact.phone && ['phone', 'Phone', <a href={`tel:${contact.phone.replace(/\s+/g, '')}`}>{contact.phone}</a>],
    contact.whatsappLink && ['whatsapp', 'WhatsApp', <a href={contact.whatsappLink} {...outside}>{contact.whatsapp || 'Chat with us'}</a>],
    contact.email && ['mail', 'Email', contact.emailForm ? <Link to="/help">{contact.email}</Link> : <a href={`mailto:${contact.email}`}>{contact.email}</a>],
    contact.tiktok && ['tiktok', 'TikTok', <a href={contact.tiktok} {...outside}>@dateroem.woods</a>],
    contact.instagram && ['instagram', 'Instagram', <a href={contact.instagram} {...outside}>@dateroem.woods</a>],
  ].filter(Boolean)

  return (
    <div className="about-card">
      <h3 className="about-card__title">Reach us</h3>
      <dl className="about-facts about-contact">
        {rows.map(([icon, label, value]) => (
          <div key={label} className="about-facts__row">
            <dt>
              <Icon name={icon} size={15} className="icon--inline" /> {label}
            </dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
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
