// "it should be able to categorize the answers properly" - the assistant
// answers in a fixed plain-text layout (AiChatService's web prompt): a
// heading is a short line ending in a colon, list items start with "- ".
// This turns that into real headings and lists; anything else stays a
// plain paragraph, so an answer that strays from the layout still reads fine.
const HEADING_MAX_WORDS = 5

// Seen on the live site (25 October 2026): the model sometimes writes a
// list on one line - "... order. Delivery: - Normal-sized items ... - Very
// large items ..." or "Here's how: 1. Add ... 2. Choose ...". Such a line
// is unfolded into its heading and items first. Product lines carry their
// own " - " (name - KES price - stock), so a line that looks like one is
// left as it is.
const INLINE_BULLETS = /^(.*?)(?:^|(?<=[.!?])\s+)([^.!?:]{1,40}):\s+-\s+(.+)$/
const INLINE_STEPS = /^(.*?):\s+1[.)]\s+(.+)$/
const PRODUCT_PART = /^(KES\b|\d+(\.\d+)?\s+in stock|out of stock)/i

function unfoldLine(line) {
  const bullets = line.match(INLINE_BULLETS)
  if (bullets) {
    const items = bullets[3].split(/\s+-\s+(?=[A-Z0-9])/)
    if (items.length >= 2 && !items.some((item) => PRODUCT_PART.test(item))) {
      return [bullets[1].trim(), `${bullets[2].trim()}:`, ...items.map((item) => `- ${item.trim()}`)].filter(Boolean)
    }
  }
  const steps = line.match(INLINE_STEPS)
  if (steps) {
    const items = steps[2].split(/\s+\d+[.)]\s+/)
    if (items.length >= 2) {
      return [`${steps[1].trim()}:`, ...items.map((item, i) => `${i + 1}. ${item.trim()}`)]
    }
  }
  return [line]
}

export function parseAnswer(text) {
  const blocks = []
  let list = null
  const lines = (text || '').split('\n').flatMap((rawLine) => unfoldLine(rawLine.trim()))
  for (const rawLine of lines) {
    const line = rawLine.trim().replace(/^#+\s*/, '')
    if (!line) {
      list = null
      continue
    }
    const item = line.match(/^(?:[-•*]|\d+[.)])\s+(.+)$/)
    if (item) {
      if (!list) {
        list = { type: 'list', items: [] }
        blocks.push(list)
      }
      list.items.push(item[1])
      continue
    }
    list = null
    const plain = line.replace(/\*\*/g, '')
    if (plain.endsWith(':') && plain.split(/\s+/).length <= HEADING_MAX_WORDS) {
      blocks.push({ type: 'heading', text: plain.slice(0, -1) })
    } else {
      blocks.push({ type: 'paragraph', text: line })
    }
  }
  return blocks
}
