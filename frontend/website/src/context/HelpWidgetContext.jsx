import { createContext, useContext, useState } from 'react'

// "help is mostly ai in mobile it can be placed to feel like a side area" -
// the chat used to be its own full-page route, the only thing on the site
// that behaved like a destination instead of a tool you reach for from
// wherever you already are. This context just tracks open/closed so any
// page (the navbar link, the Help page's own contact info, a future
// "ask about this product" button) can open the same floating panel
// without prop-drilling.
const HelpWidgetContext = createContext(null)

export function HelpWidgetProvider({ children }) {
  const [isOpen, setIsOpen] = useState(false)
  const value = {
    isOpen,
    open: () => setIsOpen(true),
    close: () => setIsOpen(false),
    toggle: () => setIsOpen((v) => !v),
  }
  return <HelpWidgetContext.Provider value={value}>{children}</HelpWidgetContext.Provider>
}

export function useHelpWidget() {
  const ctx = useContext(HelpWidgetContext)
  if (!ctx) throw new Error('useHelpWidget must be used within a HelpWidgetProvider')
  return ctx
}
