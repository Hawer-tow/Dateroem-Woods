import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/client.js'

// The live campaigns, loaded once per visit and shared by the Featured tab,
// the Featured page, the Products page banner and the product tags. The
// website server already drops anything a customer shouldn't see (drafts,
// paused or ended campaigns, campaigns with no products).
const FeaturedContext = createContext({ campaigns: [], loaded: false, error: null, campaignForProduct: () => null })

export function FeaturedProvider({ children }) {
  const [state, setState] = useState({ campaigns: [], loaded: false, error: null })

  useEffect(() => {
    let cancelled = false
    api
      .listCampaigns()
      .then((campaigns) => {
        if (!cancelled) setState({ campaigns, loaded: true, error: null })
      })
      .catch((err) => {
        if (!cancelled) setState({ campaigns: [], loaded: true, error: apiErrorMessage(err) })
      })
    return () => {
      cancelled = true
    }
  }, [])

  const value = useMemo(() => {
    // A product in more than one campaign is tagged with the newest
    // (the list comes newest first).
    const byProduct = {}
    for (const campaign of state.campaigns) {
      for (const product of campaign.products) {
        if (!byProduct[product.productId]) byProduct[product.productId] = campaign
      }
    }
    return { ...state, campaignForProduct: (productId) => byProduct[productId] || null }
  }, [state])

  return <FeaturedContext.Provider value={value}>{children}</FeaturedContext.Provider>
}

export function useFeatured() {
  return useContext(FeaturedContext)
}

// "2026-10-30" -> "30 Oct" (with the year when it isn't this year). Built
// from the parts so the day never shifts with the visitor's time zone.
export function formatEndDate(isoDate) {
  if (!isoDate) return null
  const [year, month, day] = isoDate.slice(0, 10).split('-').map(Number)
  const end = new Date(year, month - 1, day)
  const options = { day: 'numeric', month: 'short' }
  if (year !== new Date().getFullYear()) options.year = 'numeric'
  return end.toLocaleDateString('en-GB', options)
}
