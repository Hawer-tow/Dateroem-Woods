import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client.js'
import EmailDialog from './EmailDialog.jsx'
import Icon from './Icon.jsx'

// "in the lower part of any page will have our socials there whatapp tiktok
// email and company telphone number" - the company emblem (from the
// delivery lorry in the About film), then every way to reach the business.
// The contacts come from the website server's settings; TikTok and
// Instagram show once their links are set there. The email opens the same
// Send us an email window as the Help page.
export default function Footer() {
  const [contact, setContact] = useState(null)
  const [writing, setWriting] = useState(false)

  useEffect(() => {
    api.helpContact().then(setContact).catch(() => setContact(null))
  }, [])

  const outside = { target: '_blank', rel: 'noopener noreferrer' }

  return (
    <footer className="footer">
      <div className="footer__brand">
        <img className="footer__emblem" src="/brand/emblem.svg" alt="" width="52" height="52" />
        <div>
          <p className="footer__name">Dateroem Woods</p>
          <p className="footer__tagline">Native African hardwoods, sourced responsibly.</p>
        </div>
      </div>
      {contact && (
        <ul className="footer__links" aria-label="Reach us">
          <li>
            <a className="footer__link" href={contact.whatsappLink} {...outside}>
              <Icon name="whatsapp" /> WhatsApp
            </a>
          </li>
          {contact.tiktok && (
            <li>
              <a className="footer__link" href={contact.tiktok} {...outside}>
                <Icon name="tiktok" /> TikTok
              </a>
            </li>
          )}
          {contact.instagram && (
            <li>
              <a className="footer__link" href={contact.instagram} {...outside}>
                <Icon name="instagram" /> Instagram
              </a>
            </li>
          )}
          <li>
            {contact.emailForm ? (
              <button type="button" className="footer__link" onClick={() => setWriting(true)}>
                <Icon name="mail" /> {contact.email}
              </button>
            ) : (
              <a className="footer__link" href={`mailto:${contact.email}`}>
                <Icon name="mail" /> {contact.email}
              </a>
            )}
          </li>
          <li>
            <a className="footer__link" href={`tel:${contact.phone}`}>
              <Icon name="phone" /> {contact.phone}
            </a>
          </li>
        </ul>
      )}
      <p className="footer__more">
        <Link to="/track">Track my order</Link>
      </p>
      <p className="footer__copy">&copy; {new Date().getFullYear()} Dateroem Wood Sales.</p>
      {contact?.emailForm && <EmailDialog open={writing} onClose={() => setWriting(false)} contact={contact} />}
    </footer>
  )
}
