import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCart } from '../context/CartContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useFeatured } from '../context/FeaturedContext.jsx'
import AuthModal from './AuthModal.jsx'
import Avatar from './Avatar.jsx'
import Icon from './Icon.jsx'
import ThemeDots from './ThemeDots.jsx'
import UpdatesMenu, { useUpdates } from './UpdatesMenu.jsx'
import WoodWordmark from './WoodWordmark.jsx'

// "feel spacious ... feels congested" - six links plus cart plus the
// account icon used to sit in one flex-wrap row, which reflowed into a
// cramped multi-line stack the moment the viewport got narrow. On phone
// widths the links now collapse behind a hamburger toggle into a proper
// dropdown panel (one column, generous tap targets) instead of wrapping;
// cart and account stay visible in the bar itself since those are the
// two things worth one-tap access to at all times.
export default function NavBar() {
  const { count } = useCart()
  const { user, isAuthenticated, logout } = useAuth()
  const { campaigns } = useFeatured()
  const [showAuth, setShowAuth] = useState(false)
  const [showMenu, setShowMenu] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const updates = useUpdates()

  const closeMobile = () => setMobileOpen(false)

  return (
    <header className="navbar">
      <div className="navbar__row">
        <Link to="/" className="navbar__brand" onClick={closeMobile}>
          {/* "put the logo in the dateroem woods area too since one may
              never scroll to the bottom" (26 October 2026) - the lorry
              emblem from the footer, beside the name. */}
          <img className="navbar__emblem" src="/brand/emblem.svg" alt="" width="38" height="38" />
          <WoodWordmark className="navbar__wordmark" />
          <span className="navbar__brand-suffix">Woods</span>
        </Link>

        <div className="navbar__spacer" />

        {/* The buttons on the right. On wide screens the header is three
            columns - the brand down the left, the page links in the middle,
            these on the right (26 October 2026, styles/index.css). */}
        <div className="navbar__actions">
          {/* Notices from the team ("we might be having an update soon"),
              only while one is showing - see UpdatesMenu. On phones it is in
              the fold-out menu instead. */}
          <ThemeDots />
          <UpdatesMenu updates={updates} onOpen={() => setShowMenu(false)} />

          {/* "e.g 134 will push the menu away ... a more dynamic and lively
              way without writing numbers so we preserve the area at the top
              from adjusting" - a pulsing dot instead of a growing digit
              count keeps this link's width fixed no matter how many items
              are in the cart. "make the cart name to be a shopping cart icon
              ... for even farter recognition" - icon-only, matching the
              navbar's other icon buttons, with the dot as a corner badge in
              a color distinct from the painted wood used everywhere else. */}
          <Link
            to="/cart"
            className="navbar__cart"
            onClick={closeMobile}
            aria-label={count > 0 ? `Cart, ${count} items` : 'Cart'}
          >
            <Icon name="cart" size={19} />
            {count > 0 && <span className="navbar__cart-dot" />}
          </Link>

          <div className="navbar__account">
            <button
              type="button"
              className={`navbar__user-icon${isAuthenticated && user.photoUrl ? ' navbar__user-icon--photo' : ''}`}
              onClick={() => (isAuthenticated ? setShowMenu((v) => !v) : setShowAuth(true))}
              aria-label={isAuthenticated ? `Account: ${user.name}` : 'Sign in'}
            >
              {isAuthenticated ? <Avatar name={user.name} photoUrl={user.photoUrl} size={34} /> : <Icon name="user" size={18} />}
            </button>
            {isAuthenticated && showMenu && (
              <div className="navbar__account-menu">
                <p className="navbar__account-name">{user.name}</p>
                {/* "a profile area" - photo, details, phone and password (23 October 2026). */}
                <Link to="/profile" className="navbar__account-link" onClick={() => setShowMenu(false)}>
                  My profile
                </Link>
                <button
                  type="button"
                  className="btn btn--text"
                  onClick={() => {
                    logout()
                    setShowMenu(false)
                  }}
                >
                  Sign out
                </button>
              </div>
            )}
          </div>

          <button
            type="button"
            className="navbar__toggle"
            onClick={() => setMobileOpen((v) => !v)}
            aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileOpen}
          >
            <Icon name={mobileOpen ? 'close' : 'menu'} size={18} />
            {updates.unread && !mobileOpen && <span className="updates-menu__dot" />}
          </button>
        </div>
      </div>

      <nav className={`navbar__links${mobileOpen ? ' navbar__links--open' : ''}`}>
        {/* "can they be placed in the center" (26 October 2026) - the page
            links as one group in the middle of the bar. */}
        <div className="navbar__pages">
          <Link to="/" onClick={closeMobile}>Products</Link>
          {/* Customers don't go looking for "Marketing" - the tab is named
              for what they find there, and only shows while something is. */}
          {campaigns.length > 0 && (
            <Link to="/featured" onClick={closeMobile}>Featured</Link>
          )}
          <Link to="/shipping" onClick={closeMobile}>Shipping</Link>
          <Link to="/heavy-order" onClick={closeMobile}>Bulk Orders</Link>
          <Link to="/about" onClick={closeMobile}>About</Link>
          <Link to="/help" onClick={closeMobile}>Help</Link>
          {/* "i want a tab to be there ordered goods" - a persistent, always
              -visible nav tab (not tucked away behind the account menu) so
              it's never one extra click to check on something already paid
              for, only shown once there's an account to have orders under. */}
          {/* Signed out, the same place offers Track my order (the ticket and
              the last 3 digits of the phone, 23 October 2026). */}
          {isAuthenticated ? (
            <Link to="/my-orders" onClick={closeMobile}>Ordered Goods</Link>
          ) : (
            <Link to="/track" onClick={closeMobile}>Track Order</Link>
          )}
        </div>
        <UpdatesMenu updates={updates} inMenu />
        {/* Saved with the account when signed in, so the phone and desktop
            apps open in it too. On wide screens the dots are in the bar. */}
        <ThemeDots inMenu onPicked={closeMobile} />
      </nav>

      {showAuth && <AuthModal onClose={() => setShowAuth(false)} />}
    </header>
  )
}
