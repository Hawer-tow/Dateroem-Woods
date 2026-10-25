import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../Icon.jsx'
import Fireflies from './Fireflies.jsx'

// The shop's entrance in the forest look (25 October 2026): the board's misty
// forest across the whole width - mist drifting between the trees, light
// coming down, fireflies - with the promise and the way into the shop. It
// scrolls with the page like everything else ("when one scrolls it seems to
// be following one ... its like not smooth" - it used to drift slower than
// the page, and that lagged behind the scroll).
// The first time in a visit the forest arrives like dawn: dark and out of
// focus with fireflies glowing, it sharpens and settles back as the light
// comes up, then the words appear ("the misty thing that appears ... make
// something better"). Coming back to the shop later, it is simply there.
let entered = false

export default function ForestHero({ onShop }) {
  const enter = useRef(!entered).current

  useEffect(() => {
    entered = true
  }, [])

  return (
    <section
      className={`forest-hero${enter ? ' forest-hero--enter' : ''}`}
      aria-labelledby="forest-hero-title"
    >
      <div className="forest-hero__scene" aria-hidden="true">
        <div className="forest-hero__photo" />
        <div className="forest-hero__soft" />
        <div className="forest-hero__rays" />
        <div className="forest-hero__mist forest-hero__mist--back" />
        <div className="forest-hero__mist forest-hero__mist--front" />
        <Fireflies count={11} className="forest-hero__fireflies" />
        <div className="forest-hero__shade" />
      </div>
      <div className="forest-hero__content">
        <p className="forest-hero__eyebrow">Dateroem Woods · Nakuru, Kenya</p>
        <h1 id="forest-hero-title" className="forest-hero__title">
          Native African hardwoods,
          <span className="forest-hero__wood">from the forest to your door</span>
        </h1>
        <p className="forest-hero__lead">
          Timber chosen tree by tree from farms around Nakuru and Central Kenya - milled, finished and brought to you
          anywhere in Kenya.
        </p>
        <div className="forest-hero__actions">
          <button type="button" className="btn btn--primary forest-hero__shop" onClick={onShop}>
            Shop the timber
            <Icon name="arrowDown" size={16} className="icon--after" />
          </button>
          <Link to="/heavy-order" className="btn btn--secondary forest-hero__bulk">
            Bulk orders
          </Link>
        </div>
        <ul className="forest-hero__trust">
          <li>
            <Icon name="smartphone" size={16} /> Pay with M-Pesa
          </li>
          <li>
            <Icon name="truck" size={16} /> Delivery or pickup across Kenya
          </li>
          <li>
            <Icon name="tree" size={16} /> From farms around Nakuru
          </li>
        </ul>
      </div>
    </section>
  )
}
