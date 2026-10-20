import { Link } from 'react-router-dom'
import StoryScroller from '../components/StoryScroller.jsx'
import { ABOUT_CLOSING, ABOUT_INTRO } from '../core/aboutStory.js'

// "a new section about that has more information about the company" - a
// short introduction, the scroll-played journey from tree to doorstep, and
// the way on to the shop. The words live in core/aboutStory.js.
export default function AboutPage() {
  return (
    <div className="about-page">
      <header className="about-hero">
        <p className="about-hero__eyebrow">{ABOUT_INTRO.eyebrow}</p>
        <h1 className="about-hero__title">{ABOUT_INTRO.title}</h1>
        <p className="about-hero__lead">{ABOUT_INTRO.lead}</p>
      </header>

      <StoryScroller />

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
