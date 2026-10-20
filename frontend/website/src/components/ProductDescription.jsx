// "the description of the product which is can be a lot ... when its
// rendered to the user it will be in the best way possible" - a product's
// description as customers see it. The backend may supply an AI-arranged
// copy (descriptionDisplay); either way the text goes through this
// built-in tidy-up first - "if ai is available use it if not then use the
// inbuilt system" - so a description pasted in a hurry still reads well.
//
// Layout conventions (what the AI copy uses, and what anyone can type):
//   a blank line            new paragraph
//   "- " (or •, –, ✓ ...)   bullet point
//   "1. " or "1) "          numbered step
//   a short line ending ":" heading (e.g. "Sizes available:")
//   "Length: 12 ft"         a spec row, shown as a neat table
//   **word**                bold
//
// Text with none of that (one pasted blob) is arranged by arrangeLoose():
// sentences into short paragraphs, "moisture 12% grade select origin
// nyeri" into a specifications table, "store off the ground, cover from
// rain" into care bullets, a leading ALL-CAPS title into a heading.

const BULLET = /^\s*[-*•]\s+(.*)$/
const ODD_BULLET = /^\s*(?:[•·–—▪►✓✔➤]|\*(?!\*)|>)\s*(.+)$/
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/
const SPEC = /^\s*([A-Za-z][^:]{0,28}):\s+(\S.*)$/
const ALLCAPS_LINE = /^[A-Z][A-Z0-9 &'()/-]{2,40}:?$/
const LEADING_CAPS = /^((?:[A-Z][A-Z0-9&'/-]+\s+){1,5}[A-Z][A-Z0-9&'/-]+)\s+(?=[a-z0-9(])/
const SPEC_LABEL =
  /\b(moisture content|moisture|sizes|size|dimensions|length|width|thickness|grade|treatment|origin|species|finish|colou?r|density|weight|price|delivery|warranty|availability|uses|applications)\b[:\s-]*/gi
const CARE_START = /^(store|keep|cover|avoid|protect|clean|stack|seal|oil|varnish|handle|do not|don't)\b/i
const NOT_A_VALUE = /^(of|is|are|was|were|for|to|and|the|a|an|in|on|with|at|by)\b/i
const HEADING_MAX = 60
const PARAGRAPH_TARGET = 280

// Long enough that it gets its own section below the price, with only a
// short preview beside it.
export function isLongDescription(text) {
  return Boolean(text) && (text.length > 220 || /\n/.test(text.trim()))
}

// The opening text, for the preview beside the price.
export function descriptionPreview(text) {
  const blocks = parseBlocks(arrange(text))
  const first = blocks.find((block) => block.type === 'paragraph') || blocks.find((block) => block.lines.length)
  const source = first ? first.lines.join(' ') : String(text || '')
  return source.replace(/\*\*/g, '')
}

export default function ProductDescription({ text }) {
  return (
    <div className="product-description">
      {parseBlocks(arrange(text)).map((block, index) => {
        if (block.type === 'heading') {
          return (
            <h3 key={index} className="product-description__heading">
              {renderInline(block.lines[0])}
            </h3>
          )
        }
        if (block.type === 'bullets' || block.type === 'numbered') {
          const List = block.type === 'bullets' ? 'ul' : 'ol'
          return (
            <List key={index} className="product-description__list">
              {block.lines.map((line, i) => (
                <li key={i}>{renderInline(line)}</li>
              ))}
            </List>
          )
        }
        if (block.type === 'specs') {
          return (
            <dl key={index} className="product-description__specs">
              {block.rows.map(([label, value], i) => (
                <div key={i} className="product-description__spec">
                  <dt>{label}</dt>
                  <dd>{renderInline(capitalize(value))}</dd>
                </div>
              ))}
            </dl>
          )
        }
        return (
          <p key={index} className="product-description__paragraph">
            {block.lines.map((line, i) => (
              <span key={i}>
                {i > 0 && <br />}
                {renderInline(line)}
              </span>
            ))}
          </p>
        )
      })}
    </div>
  )
}

// ---- built-in tidy-up: any text in, the layout conventions out ----

function arrange(text) {
  const lines = tidySpacing(String(text || '').replace(/\r\n?/g, '\n'))
    .split('\n')
    .map((line) => line.trim())
  return hasStructure(lines) ? arrangeStructured(lines) : arrangeLoose(lines)
}

function tidySpacing(text) {
  return text
    .replace(/[ \t]+/g, ' ')
    .replace(/ +([,;!?])/g, '$1')
    .replace(/([,;])(?=[A-Za-z(])/g, '$1 ')
    // "12ft,2x6" -> "12ft, 2x6", but "3,000" stays a number.
    .replace(/([A-Za-z%)])([,;])(?=\d)/g, '$1$2 ')
}

// Already laid out by a person or the AI - only light fixes then.
function hasStructure(lines) {
  const marked = lines.some(
    (line) =>
      BULLET.test(line) ||
      ODD_BULLET.test(line) ||
      NUMBERED.test(line) ||
      (line.endsWith(':') && line.length <= HEADING_MAX),
  )
  return marked || lines.filter((line) => SPEC.test(line)).length >= 2
}

function arrangeStructured(lines) {
  const out = []
  for (const line of lines) {
    if (!line) {
      out.push('')
    } else if (ALLCAPS_LINE.test(line) && (line.match(/[A-Z]/g) || []).length >= 4) {
      out.push(sentenceCase(line.replace(/:$/, '')) + ':')
    } else if (!BULLET.test(line) && ODD_BULLET.test(line)) {
      out.push('- ' + ODD_BULLET.exec(line)[1])
    } else if (isInlineSpecs(line)) {
      out.push(...line.split(/\s*[;|]\s*/).filter(Boolean))
    } else if (line.length > 400 && !BULLET.test(line) && !NUMBERED.test(line)) {
      out.push(...regroup(sentences(line)), '')
    } else {
      out.push(line)
    }
  }
  return out.join('\n')
}

function isInlineSpecs(line) {
  const parts = line.split(/\s*[;|]\s*/).filter(Boolean)
  return parts.length > 1 && parts.every((part) => SPEC.test(part))
}

function arrangeLoose(lines) {
  const out = []
  let paragraph = []
  let specs = []
  let inCare = false
  const flushParagraph = () => {
    if (paragraph.length) out.push(paragraph.join(' '), '')
    paragraph = []
  }
  const flushSpecs = () => {
    if (specs.length > 1) out.push('Specifications:')
    if (specs.length) out.push(...specs.map(([label, value]) => `${label}: ${value}`), '')
    specs = []
  }
  const closeCare = () => {
    if (inCare) out.push('')
    inCare = false
  }

  for (const raw of lines) {
    if (!raw) {
      flushParagraph()
      continue
    }
    let line = raw
    const title = LEADING_CAPS.exec(line)
    if (title && out.length === 0 && paragraph.length === 0) {
      out.push(sentenceCase(title[1]) + ':')
      line = line.slice(title[0].length)
    }
    for (const sentence of sentences(line)) {
      const rows = specRows(sentence)
      if (rows) {
        flushParagraph()
        closeCare()
        specs.push(...rows)
        continue
      }
      if (CARE_START.test(sentence)) {
        flushParagraph()
        flushSpecs()
        if (!inCare) out.push('Care and storage:')
        inCare = true
        for (const part of stripEnd(sentence).split(/\s*[,;]\s*/)) {
          if (part) out.push('- ' + capitalize(part))
        }
        continue
      }
      flushSpecs()
      closeCare()
      paragraph.push(capitalize(sentence))
      if (paragraph.join(' ').length > PARAGRAPH_TARGET) flushParagraph()
    }
  }
  flushParagraph()
  flushSpecs()
  return out.join('\n')
}

// "moisture 12-15% grade select few knots origin nyeri" -> spec rows, but
// only when the sentence starts with such a label and every value looks
// like one (not "length of each board is ...").
function specRows(sentence) {
  const body = stripEnd(sentence)
  const hits = []
  const seen = new Set()
  SPEC_LABEL.lastIndex = 0
  let match
  while ((match = SPEC_LABEL.exec(body))) {
    const label = match[1].toLowerCase()
    if (parenDepth(body, match.index) === 0 && !seen.has(label)) {
      hits.push(match)
      seen.add(label)
    }
  }
  if (hits.length === 0 || hits[0].index !== 0) return null
  const rows = hits.map((hit, i) => {
    const end = i + 1 < hits.length ? hits[i + 1].index : body.length
    return [sentenceCase(hit[1]), body.slice(hit.index + hit[0].length, end).trim().replace(/[\s,;]+$/, '')]
  })
  if (rows.some(([, value]) => !value || NOT_A_VALUE.test(value))) return null
  if (rows.length === 1 && rows[0][1].length > 80) return null
  return rows.map(([label, value]) => [label, capitalize(value)])
}

function sentences(line) {
  return line
    .replace(/([.!?])\s+(?=[A-Za-z0-9(])/g, '$1\u0000')
    .split('\u0000')
    .map((sentence) => sentence.trim())
    .filter(Boolean)
}

function regroup(list) {
  const paragraphs = []
  let current = ''
  for (const sentence of list) {
    current = current ? `${current} ${sentence}` : sentence
    if (current.length > PARAGRAPH_TARGET) {
      paragraphs.push(current, '')
      current = ''
    }
  }
  if (current) paragraphs.push(current)
  return paragraphs
}

function parenDepth(text, index) {
  let depth = 0
  for (let i = 0; i < index; i++) {
    if (text[i] === '(') depth++
    else if (text[i] === ')') depth = Math.max(0, depth - 1)
  }
  return depth
}

function stripEnd(text) {
  return text.replace(/[\s.!?]+$/, '')
}

function capitalize(text) {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

function sentenceCase(text) {
  return capitalize(text.toLowerCase())
}

// ---- layout conventions -> blocks ----

function parseBlocks(text) {
  const blocks = []
  let current = null
  const push = (type) => {
    if (!current || current.type !== type) {
      current = { type, lines: [], rows: [] }
      blocks.push(current)
    }
    return current
  }
  for (const raw of String(text || '').split('\n')) {
    const line = raw.trim()
    if (!line) {
      current = null
      continue
    }
    let match
    if ((match = BULLET.exec(line))) {
      push('bullets').lines.push(match[1])
    } else if ((match = NUMBERED.exec(line))) {
      push('numbered').lines.push(match[1])
    } else if (line.endsWith(':') && line.length <= HEADING_MAX) {
      blocks.push({ type: 'heading', lines: [line.slice(0, -1)], rows: [] })
      current = null
    } else if ((match = SPEC.exec(line)) && !/[.!?]$/.test(match[1])) {
      push('specs').rows.push([match[1], match[2]])
    } else {
      push('paragraph').lines.push(line)
    }
  }
  return blocks.map((block) => {
    // A bullet list that is all "Label: value" (how the AI often writes
    // specifications) reads best as the spec table.
    if (block.type === 'bullets' && block.lines.length > 1) {
      const rows = block.lines.map((line) => SPEC.exec(line))
      if (rows.every((row) => row && !/[.!?]$/.test(row[1]))) {
        return { type: 'specs', lines: [], rows: rows.map((row) => [row[1], row[2]]) }
      }
    }
    // A lone "Note: ..." line reads better as a sentence than a one-row table.
    if (block.type === 'specs' && block.rows.length === 1) {
      return { type: 'paragraph', lines: [`**${block.rows[0][0]}:** ${block.rows[0][1]}`], rows: [] }
    }
    return block
  })
}

function renderInline(text) {
  return String(text)
    .split(/(\*\*[^*]+\*\*)/g)
    .map((part, i) =>
      part.startsWith('**') && part.endsWith('**') && part.length > 4 ? <strong key={i}>{part.slice(2, -2)}</strong> : part,
    )
}
