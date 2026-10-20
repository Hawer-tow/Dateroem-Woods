import { useEffect, useState } from 'react'
import { api, apiErrorMessage } from '../api/client.js'
import EmailDialog from '../components/EmailDialog.jsx'
import ShowcaseSlider from '../components/ShowcaseSlider.jsx'

export default function HeavyOrderPage() {
  const [contact, setContact] = useState(null)
  const [error, setError] = useState(null)
  const [writing, setWriting] = useState(false)
  const [showcase, setShowcase] = useState([])

  useEffect(() => {
    api.heavyOrderContact().then(setContact).catch((err) => setError(apiErrorMessage(err)))
    // The example pictures are a bonus: if they can't load, the page is
    // simply as it was without them.
    api.heavyOrderShowcase().then((rows) => setShowcase(rows || [])).catch(() => setShowcase([]))
  }, [])

  return (
    <div className="heavy-order-page">
      <h1>Bulk &amp; Heavy Orders</h1>
      <p>
        Large or heavy timber orders involve permits and certifications that need a direct conversation with our
        team - reach out through any of the channels below and an admin will follow up to negotiate and finalize
        your order.
      </p>
      {showcase.length > 0 && (
        <>
          <h2 className="heavy-order-page__showcase-title">What bulk orders look like</h2>
          <ShowcaseSlider pictures={showcase} />
        </>
      )}
      {error && <p className="state-message state-message--error">Couldn't load contact details: {error}</p>}
      {contact && (
        <div className="heavy-order-page__contacts">
          <a
            className="btn btn--primary"
            href={contact.whatsappLink}
            target="_blank"
            rel="noreferrer"
          >
            WhatsApp us
          </a>
          <a className="btn btn--secondary" href={`tel:${contact.phone}`}>
            Call {contact.phone}
          </a>
          {/* The same email window as the Help page, starting with the
              subject filled in; the plain email link when it can't send. */}
          {contact.emailForm ? (
            <button type="button" className="btn btn--secondary" onClick={() => setWriting(true)}>
              Send us an email
            </button>
          ) : (
            <a className="btn btn--secondary" href={`mailto:${contact.email}?subject=${encodeURIComponent(BULK_SUBJECT)}`}>
              Email {contact.email}
            </a>
          )}
        </div>
      )}
      {contact?.emailForm && (
        <EmailDialog open={writing} onClose={() => setWriting(false)} contact={contact} subject={BULK_SUBJECT} desk="orders" />
      )}
    </div>
  )
}

const BULK_SUBJECT = 'Bulk order enquiry'
