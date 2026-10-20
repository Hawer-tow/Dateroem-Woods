import { useEffect, useRef, useState } from 'react'
import { THEMES, useTheme } from '../context/ThemeContext.jsx'
import Icon from './Icon.jsx'

// "at the top i have about help ... that space is not fully utilized ... put
// the theme there but like a menu not 3 things" (20 October 2026) - one
// Theme button at the end of the top links; it opens a short list and the
// one picked applies at once (and is saved with the account when signed in).
export default function ThemeMenu({ onPicked }) {
  const { theme, setTheme } = useTheme()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const closeOutside = (event) => {
      if (!ref.current?.contains(event.target)) setOpen(false)
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  return (
    <div className="theme-menu" ref={ref}>
      <button
        type="button"
        className="theme-menu__button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className={`theme-menu__swatch theme-menu__swatch--${theme}`} aria-hidden="true" />
        Theme
        <span className="theme-menu__caret" aria-hidden="true" />
      </button>
      {open && (
        <ul className="theme-menu__list" role="menu" aria-label="Colour theme">
          {THEMES.map((option) => (
            <li key={option.id} role="none">
              <button
                type="button"
                role="menuitemradio"
                aria-checked={theme === option.id}
                className="theme-menu__item"
                onClick={() => {
                  setTheme(option.id)
                  setOpen(false)
                  onPicked?.()
                }}
              >
                <span className={`theme-menu__swatch theme-menu__swatch--${option.id}`} aria-hidden="true" />
                <span className="theme-menu__label">{option.label}</span>
                {theme === option.id && <Icon name="check" size={14} />}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
