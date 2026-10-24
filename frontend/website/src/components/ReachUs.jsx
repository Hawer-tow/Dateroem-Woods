import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client.js'
import Icon from './Icon.jsx'

// Every way to reach the business - phone, WhatsApp, email and the socials -
// from the website server's settings (the same ones the footer and Help use) -
// the company's own contacts, never anyone's personal number.
// On the About page, and at the foot of each policy page.
export default function ReachUs({ title = 'Reach us' }) {
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
      <h3 className="about-card__title">{title}</h3>
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
