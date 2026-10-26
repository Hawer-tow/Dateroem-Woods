import { THEMES, useTheme } from '../context/ThemeContext.jsx'

// "like you see in mac os we usually have 3 dots can mine be green black and
// white you click the colors and the theme changes" (26 October 2026) - the
// colour themes as three dots: green Forest, black Dark, white Light. The
// one in use wears a wood ring; a click changes the theme at once (and saves
// it with the account when signed in). In the top bar on wide screens, in
// the fold-out menu (inMenu, with bigger dots) on phones.
export default function ThemeDots({ inMenu = false, onPicked }) {
  const { theme, setTheme } = useTheme()

  return (
    <div className={`theme-dots${inMenu ? ' theme-dots--in-menu' : ''}`}>
      {inMenu && <span className="theme-dots__label">Theme</span>}
      <div className="theme-dots__set" role="radiogroup" aria-label="Colour theme">
        {THEMES.map((option) => (
          <button
            key={option.id}
            type="button"
            role="radio"
            aria-checked={theme === option.id}
            aria-label={`${option.label} theme`}
            title={option.label}
            className={`theme-dots__dot theme-dots__dot--${option.id}`}
            onClick={() => {
              setTheme(option.id)
              onPicked?.()
            }}
          />
        ))}
      </div>
    </div>
  )
}
