import { Link } from 'react-router-dom'
import ReachUs from '../components/ReachUs.jsx'
import { POLICIES, POLICY_ORDER, POLICY_UPDATED } from '../core/policies.js'

// Returns & Refunds, Privacy and Terms (24 October 2026) - one layout, the
// words in core/policies.js. Each ends with every way to reach us and the
// other two policies.
export default function PolicyPage({ policy }) {
  const page = POLICIES[policy]
  const others = POLICY_ORDER.filter((key) => key !== policy).map((key) => POLICIES[key])

  return (
    <article className="policy-page">
      <header className="policy-page__header">
        <h1>{page.title}</h1>
        <p className="policy-page__updated">Last updated {POLICY_UPDATED}</p>
        <p className="policy-page__lead">{page.lead}</p>
      </header>

      {page.sections.map((section) => (
        <section key={section.heading} className="policy-section">
          <h2 className="policy-section__title">{section.heading}</h2>
          {(section.paragraphs || []).map((text) => (
            <p key={text}>{text}</p>
          ))}
          {section.bullets && (
            <ul className="policy-section__list">
              {section.bullets.map((text) => (
                <li key={text}>{text}</li>
              ))}
            </ul>
          )}
          {section.after && <p>{section.after}</p>}
          {section.link && (
            <p>
              <Link to={section.link}>Read {POLICIES[POLICY_ORDER.find((key) => POLICIES[key].path === section.link)].title}</Link>
            </p>
          )}
        </section>
      ))}

      <div className="policy-page__contact">
        <ReachUs title="Questions? Reach us" />
      </div>

      <nav className="policy-page__more" aria-label="Our other policies">
        Also read:{' '}
        {others.map((other, index) => (
          <span key={other.path}>
            {index > 0 && ' · '}
            <Link to={other.path}>{other.title}</Link>
          </span>
        ))}
      </nav>
    </article>
  )
}
