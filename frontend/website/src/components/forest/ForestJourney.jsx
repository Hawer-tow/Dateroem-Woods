import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../Icon.jsx'
import { revealWhenSeen } from './cardMotion.js'

// Under every product in the forest look (25 October 2026): where the piece
// comes from and how it reaches the buyer - the About page's "How we work"
// in four short steps, with the delivery time and the refund rule from the
// policy pages (core/policies.js), so nothing here promises more than they do.
const STEPS = [
  ['tree', 'Grown on Kenyan farms', 'Native hardwoods from farms across Central Kenya.'],
  ['sprout', 'Chosen with the farmer', 'Bought tree by tree, with the permits each harvest needs.'],
  ['axe', 'Milled and finished', 'Cut and finished at our workshop in Nakuru.'],
  ['truck', 'Brought to you', 'Delivered across Kenya, or collected from a pickup point you choose.'],
]

export default function ForestJourney() {
  // Below the fold, it rises into view and the path between the steps grows.
  const ref = useRef(null)
  useEffect(() => revealWhenSeen(ref.current), [])

  return (
    <section ref={ref} className="forest-journey" aria-labelledby="forest-journey-title">
      <h2 id="forest-journey-title" className="forest-journey__title">
        From the forest to your door
      </h2>
      <ol className="forest-journey__steps">
        {STEPS.map(([icon, title, text]) => (
          <li key={title} className="forest-journey__step">
            <span className="forest-journey__icon">
              <Icon name={icon} size={22} />
            </span>
            <span className="forest-journey__step-title">{title}</span>
            <span className="forest-journey__step-text">{text}</span>
          </li>
        ))}
      </ol>
      <ul className="forest-journey__notes">
        <li>
          <Icon name="smartphone" size={15} /> Pay with M-Pesa on your phone
        </li>
        <li>
          <Icon name="package" size={15} /> About 3 weeks to your door (very large single pieces, up to 3 months)
        </li>
        <li>
          <Icon name="warning" size={15} /> Damaged on arrival? <Link to="/returns">Returns &amp; Refunds</Link>
        </li>
      </ul>
    </section>
  )
}
