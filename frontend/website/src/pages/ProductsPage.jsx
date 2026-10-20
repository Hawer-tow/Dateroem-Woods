import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, apiErrorMessage } from '../api/client.js'
import ProductRow from '../components/ProductRow.jsx'
import Icon from '../components/Icon.jsx'
import FeaturedBanner from '../components/FeaturedBanner.jsx'
import { useChatNudge } from '../context/ChatNudgeContext.jsx'
import { useFeatured } from '../context/FeaturedContext.jsx'

// "i go back im taken to the top product ... it should go back to the
// product i was at" - the list and the search are remembered while the tab
// is open, so coming Back shows them at once (and ScrollManager can put
// the visitor back where they were) instead of a loading screen; a fresh
// copy still loads quietly behind it.
let cachedProducts = null
let lastSearch = ''

export default function ProductsPage() {
  const [all, setAll] = useState(cachedProducts || [])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(!cachedProducts)
  const [search, setSearchState] = useState(lastSearch)
  // ?campaign=<id> (from "See the products") narrows the list to that
  // campaign's products; a campaign that has since ended just shows all.
  const [searchParams, setSearchParams] = useSearchParams()
  const campaignParam = searchParams.get('campaign')
  const { campaigns, loaded: featuredLoaded } = useFeatured()
  const activeCampaign = campaignParam
    ? campaigns.find((campaign) => String(campaign.campaignId) === campaignParam) || null
    : null

  function setSearch(value) {
    lastSearch = value
    setSearchState(value)
  }

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        // Ratings are extra - the catalog still loads if they don't.
        // rating: the average + count, null = not enough ratings yet,
        // undefined = ratings couldn't be loaded (the card shows nothing).
        const [allRes, ratings] = await Promise.all([
          api.listProducts(),
          api.reviewSummaries().catch(() => null),
        ])
        const products = allRes.map((product) => ({
          ...product,
          rating: ratings ? ratings[product.productId] || null : undefined,
        }))
        cachedProducts = products
        if (!cancelled) setAll(products)
      } catch (err) {
        // Already showing a remembered list - keep it rather than an error.
        if (!cancelled && !cachedProducts) setError(apiErrorMessage(err))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  const trimmedSearch = search.trim().toLowerCase()
  const filteredAll = useMemo(() => {
    let list = all
    if (activeCampaign) {
      const ids = new Set(activeCampaign.products.map((product) => product.productId))
      list = list.filter((product) => ids.has(product.productId))
    }
    if (!trimmedSearch) return list
    return list.filter((product) => product.name?.toLowerCase().includes(trimmedSearch))
  }, [all, trimmedSearch, activeCampaign])

  // A search that finds nothing is a moment the chat button can offer help.
  const { reportEmptySearch } = useChatNudge()
  const nothingFound = Boolean(trimmedSearch) && all.length > 0 && filteredAll.length === 0
  useEffect(() => {
    reportEmptySearch(nothingFound ? search.trim() : '')
  }, [nothingFound, search, reportEmptySearch])
  useEffect(() => () => reportEmptySearch(''), [reportEmptySearch])

  if (loading || (campaignParam && !featuredLoaded)) return <p className="state-message">Loading products...</p>
  if (error) return <p className="state-message state-message--error">Couldn't reach the server: {error}</p>

  return (
    <div className="products-page">
      {!campaignParam && !trimmedSearch && <FeaturedBanner />}
      <label className="product-search">
        <span className="product-search__icon">
          <Icon name="search" size={17} />
        </span>
        <input
          type="text"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search products..."
        />
        {search && (
          <button type="button" className="product-search__clear" onClick={() => setSearch('')} aria-label="Clear search">
            <Icon name="close" size={15} />
          </button>
        )}
      </label>

      {activeCampaign && (
        <div className="featured-filter">
          <span className="featured-filter__name">
            <Icon name="star" size={15} />
            Featured: {activeCampaign.name}
          </span>
          <button type="button" className="featured-filter__clear" onClick={() => setSearchParams({})}>
            Show all products
          </button>
        </div>
      )}
      {campaignParam && !activeCampaign && (
        <p className="featured-filter__ended">That featured selection has ended - here are all our products.</p>
      )}

      <ProductRow
        title={trimmedSearch ? `Results for "${search.trim()}"` : activeCampaign ? undefined : 'Products'}
        products={filteredAll}
        layout="grid"
        emptyMessage={trimmedSearch ? 'No products match that search.' : undefined}
      />
      {all.length === 0 && (
        <p className="state-message">No products listed yet - check back soon.</p>
      )}
    </div>
  )
}
