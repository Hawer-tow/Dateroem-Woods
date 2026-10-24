import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client.js'
import { useHelpWidget } from '../context/HelpWidgetContext.jsx'
import EmailDialog from '../components/EmailDialog.jsx'
import Icon from '../components/Icon.jsx'

// The chat itself lives in HelpWidget.jsx now, as a floating side panel
// reachable from every page - "help is mostly ai in mobile it can be
// placed to feel like a side area", not a dedicated destination. This
// page is the door into that same widget, the ways to reach the team and
// the frequently asked questions.
export default function HelpPage() {
  const { open } = useHelpWidget()
  const [contact, setContact] = useState(null)
  const [writing, setWriting] = useState(false)

  useEffect(() => {
    api.helpContact().then(setContact).catch(() => setContact(null))
  }, [])

  return (
    <div className="help-page">
      <h1>Help Center</h1>
      <p className="state-message">
        Have a question about a product, an order, or shipping? Our AI assistant can help right away.
      </p>
      <button type="button" className="btn btn--primary help-page__chat-cta" onClick={open}>
        <Icon name="chat" className="icon--inline" />
        Chat with us
      </button>
      {/* "isnt the help contancts being below the faqs make it tiring to
          access" - right under the chat, before the questions. */}
      {contact && (
        <div className="help-page__contact">
          <p className="help-page__contact-label">Or reach our team directly:</p>
          <div className="help-page__contact-links">
            {contact.emailForm ? (
              <button type="button" className="help-page__contact-link" onClick={() => setWriting(true)}>
                <Icon name="mail" className="icon--inline" />
                Send us an email
              </button>
            ) : (
              <a className="help-page__contact-link" href={`mailto:${contact.email}?subject=${encodeURIComponent('Help request')}`}>
                <Icon name="mail" className="icon--inline" />
                {contact.name || contact.email}
              </a>
            )}
            <a className="help-page__contact-link" href={`tel:${contact.phone}`}>
              <Icon name="phone" className="icon--inline" />
              {contact.phone}
            </a>
            <a className="help-page__contact-link" href={contact.whatsappLink} target="_blank" rel="noopener noreferrer">
              <Icon name="chat" className="icon--inline" />
              WhatsApp
            </a>
          </div>
          {/* The address to copy, for a computer with no email app. */}
          {contact.name && (
            <p className="help-page__contact-address">
              Email address: <span>{contact.email}</span>
            </p>
          )}
        </div>
      )}
      {contact?.emailForm && <EmailDialog open={writing} onClose={() => setWriting(false)} contact={contact} />}
      <FaqSection />
    </div>
  )
}

// The page a question can point at, named as in the menu.
const PAGE_NAMES = {
  '/': 'Products',
  '/featured': 'Featured',
  '/cart': 'Cart',
  '/my-orders': 'Ordered Goods',
  '/shipping': 'Shipping',
  '/heavy-order': 'Bulk Orders',
  '/about': 'About',
  '/returns': 'Returns & Refunds',
  '/privacy': 'Privacy',
  '/terms': 'Terms',
}

// "adding faqs in the help area ... to stop feeling less empty to assist in
// one getting common assistance from areas in the site" - the questions
// admins keep up to date in the desktop app (FAQs), grouped by topic in
// their set order, with a search box and topic buttons. Nothing shows if
// there are none or they can't be loaded - the chat and contacts remain.
function FaqSection() {
  const [faqs, setFaqs] = useState([])
  const [query, setQuery] = useState('')
  const [topic, setTopic] = useState(null)

  useEffect(() => {
    api.helpFaqs().then((rows) => setFaqs(rows || [])).catch(() => setFaqs([]))
  }, [])

  const topics = useMemo(() => [...new Set(faqs.map((faq) => faq.topic))], [faqs])
  const groups = useMemo(() => {
    // Every word typed must appear somewhere in the question, answer or
    // topic; capitals and punctuation don't count, so "mpesa pin" finds
    // "M-Pesa PIN".
    const words = plain(query).split(/\s+/).filter(Boolean)
    const shown = faqs.filter((faq) => {
      const text = plain(`${faq.question} ${faq.answer} ${faq.topic}`)
      return (!topic || faq.topic === topic) && words.every((word) => text.includes(word))
    })
    const byTopic = new Map()
    shown.forEach((faq) => byTopic.set(faq.topic, [...(byTopic.get(faq.topic) || []), faq]))
    return [...byTopic.entries()]
  }, [faqs, query, topic])

  if (faqs.length === 0) return null

  return (
    <section className="help-faq" aria-labelledby="help-faq-title">
      <h2 id="help-faq-title">Frequently asked questions</h2>
      <div className="help-faq__tools">
        <label className="help-faq__search">
          <Icon name="search" className="icon--inline" />
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search the questions"
            aria-label="Search the questions"
          />
        </label>
        <div className="help-faq__topics" role="group" aria-label="Topics">
          {[null, ...topics].map((name) => (
            <button
              key={name || 'all'}
              type="button"
              className="help-faq__topic"
              aria-pressed={topic === name}
              onClick={() => setTopic(name)}
            >
              {name || 'All'}
            </button>
          ))}
        </div>
      </div>
      {groups.map(([name, items]) => (
        <div className="help-faq__group" key={name}>
          <h3>{name}</h3>
          {items.map((faq) => (
            <details className="help-faq__item" key={faq.faqId}>
              <summary>{faq.question}</summary>
              <div className="help-faq__answer">
                <FaqAnswer text={faq.answer} />
                {faq.linkPath && (
                  <Link className="help-faq__link" to={faq.linkPath}>
                    Go to {PAGE_NAMES[faq.linkPath] || 'the page'}
                  </Link>
                )}
              </div>
            </details>
          ))}
        </div>
      ))}
      {groups.length === 0 && (
        <p className="state-message">No question matches that. Try other words, or ask the chat assistant.</p>
      )}
    </section>
  )
}

function plain(text) {
  return text.toLowerCase().replace(/[^a-z0-9\s]/g, '')
}

// Answers are plain text from the desktop app: each line is a paragraph,
// and lines starting "1. ", "2. " ... become a numbered list.
function FaqAnswer({ text }) {
  const blocks = []
  text.split('\n').map((line) => line.trim()).filter(Boolean).forEach((line) => {
    const step = line.match(/^\d+\.\s+(.*)$/)
    const last = blocks[blocks.length - 1]
    if (step && last?.steps) last.steps.push(step[1])
    else if (step) blocks.push({ steps: [step[1]] })
    else blocks.push({ text: line })
  })
  return blocks.map((block, index) => (block.steps
    ? <ol key={index}>{block.steps.map((step, i) => <li key={i}>{step}</li>)}</ol>
    : <p key={index}>{block.text}</p>))
}
