import { useEffect, useMemo, useState } from 'react'
import { distanceKm, googleMapsUrl, parseCoordinates } from '../core/geo.js'
import Icon from './Icon.jsx'

// "it appears and stays there its in red it makes one feel like they made
// a grave mistake ... make it disappear after some time" - declining a
// browser permission prompt is a normal, unremarkable choice, not an
// application error, so it fades on its own rather than sitting there
// looking alarming until something else happens to clear it.
const LOCATION_MESSAGE_MS = 6000

// Areas within this far still count as "near you" - close enough that
// leading with them actually saves a search, not just noise at the top.
const NEARBY_RADIUS_KM = 60
const NEARBY_MAX = 5

// "if delivery areas have the same city e.g Nakuru group them together ...
// allowing each grouped card ... to search for that area in that city to
// check if it's there" - areas are grouped into one card per city, with a
// search box that matches either the city or the area name and says so
// plainly when nothing matches ("we don't deliver there yet") instead of
// leaving an empty list. With onSelect the areas are pickable (checkout);
// without it the same cards are a read-only directory (Shipping page).
//
// "the website will check the location of the user, check delivery areas
// near them ... shown at the top as delivery areas near you and one can
// click that exact delivery area ... get a google maps to see it through
// maps" - geolocation is only ever requested from an explicit click (never
// on load - it's a real permission prompt, not something to spring on
// someone), and only areas an admin has actually pinned with coordinates
// (see the desktop app's own Coordinates field) can ever appear there or
// open in Maps.
export default function AreaPicker({ areas, selectedId = null, onSelect = null, description = true }) {
  const [query, setQuery] = useState('')
  const [myLocation, setMyLocation] = useState(null)
  const [locating, setLocating] = useState(false)
  const [locationError, setLocationError] = useState(null)

  useEffect(() => {
    if (!locationError) return
    const timer = setTimeout(() => setLocationError(null), LOCATION_MESSAGE_MS)
    return () => clearTimeout(timer)
  }, [locationError])

  const groups = useMemo(() => {
    const needle = query.trim().toLowerCase()
    const byCity = new Map()
    for (const area of areas) {
      const city = (area.city || 'Other').trim()
      const matches =
        !needle || city.toLowerCase().includes(needle) || (area.areaName || '').toLowerCase().includes(needle)
      if (!matches) continue
      if (!byCity.has(city)) byCity.set(city, [])
      byCity.get(city).push(area)
    }
    return [...byCity.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([city, list]) => ({
        city,
        areas: list.sort((x, y) => (x.areaName || '').localeCompare(y.areaName || '')),
      }))
  }, [areas, query])

  const nearby = useMemo(() => {
    if (!myLocation) return []
    return areas
      .map((area) => {
        const coords = parseCoordinates(area.coordinates)
        return coords ? { area, km: distanceKm(myLocation, coords) } : null
      })
      .filter((entry) => entry && entry.km <= NEARBY_RADIUS_KM)
      .sort((a, b) => a.km - b.km)
      .slice(0, NEARBY_MAX)
  }, [areas, myLocation])

  const cityCount = new Set(areas.map((a) => (a.city || 'Other').trim())).size

  function findNearMe() {
    if (!navigator.geolocation) {
      setLocationError("This browser can't share your location.")
      return
    }
    setLocating(true)
    setLocationError(null)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false)
        setMyLocation({ lat: position.coords.latitude, lng: position.coords.longitude })
      },
      (err) => {
        setLocating(false)
        setLocationError(
          err.code === err.PERMISSION_DENIED
            ? 'Location access was declined - you can still search or browse by city below.'
            : "Couldn't get your location - you can still search or browse by city below."
        )
      },
      { timeout: 10000 }
    )
  }

  return (
    <div className="area-picker">
      <div className="area-picker__search">
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search a city or area, e.g. Nakuru or Town Center"
          aria-label="Search delivery areas"
        />
        <span className="area-picker__count">
          {areas.length} area{areas.length === 1 ? '' : 's'} in {cityCount} cit{cityCount === 1 ? 'y' : 'ies'}
        </span>
      </div>

      {!myLocation && (
        <button type="button" className="btn btn--secondary area-picker__locate" onClick={findNearMe} disabled={locating}>
          {locating ? (
            'Finding your location...'
          ) : (
            <>
              <Icon name="pin" className="icon--inline" />
              Show delivery areas near me
            </>
          )}
        </button>
      )}
      {locationError && <p className="state-message area-picker__location-note">{locationError}</p>}

      {myLocation && nearby.length > 0 && (
        <section className="area-card area-card--nearby">
          <header className="area-card__header">
            <h3>
              <Icon name="pin" className="icon--inline" />
              Delivery areas near you
            </h3>
          </header>
          <ul className="area-card__list">
            {nearby.map(({ area, km }) => (
              <AreaRow
                key={area.deliveryAreaId}
                area={area}
                selected={selectedId != null && Number(selectedId) === area.deliveryAreaId}
                onSelect={onSelect}
                description={description}
                distanceLabel={`${km < 1 ? '<1' : km.toFixed(0)} km away`}
              />
            ))}
          </ul>
        </section>
      )}
      {myLocation && nearby.length === 0 && (
        <p className="state-message">No delivery area is close by yet - browse by city below instead.</p>
      )}

      {groups.length === 0 ? (
        <p className="area-picker__empty">
          No delivery area matches &ldquo;{query.trim()}&rdquo; yet. Try another spelling, or ask our Help chat
          whether we can reach you.
        </p>
      ) : (
        <div className="area-picker__groups">
          {groups.map((group) => (
            <section key={group.city} className="area-card">
              <header className="area-card__header">
                <h3>{group.city}</h3>
                <span className="area-card__badge">
                  {group.areas.length} area{group.areas.length === 1 ? '' : 's'}
                </span>
              </header>
              <ul className="area-card__list">
                {group.areas.map((area) => (
                  <AreaRow
                    key={area.deliveryAreaId}
                    area={area}
                    selected={selectedId != null && Number(selectedId) === area.deliveryAreaId}
                    onSelect={onSelect}
                    description={description}
                  />
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

// "can you allow it to be placed in a very ordery manner since now it
// seems not right" - the Maps link used to sit on its own extra line
// below the row, only for areas that had one, so a list mixing pinned and
// unpinned areas came out with jagged, uneven-height rows. It now always
// sits inline, on the same single row as the name, so every row lines up
// the same regardless of whether that particular area happens to have a
// link.
function AreaRow({ area, selected, onSelect, description, distanceLabel }) {
  const mapsUrl = googleMapsUrl(area.coordinates)
  const mapsLink = mapsUrl && (
    <a
      href={mapsUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="area-card__maps-link"
      onClick={(event) => event.stopPropagation()}
    >
      Map &rarr;
    </a>
  )
  return (
    <li>
      {onSelect ? (
        <button
          type="button"
          className={`area-card__area${selected ? ' area-card__area--selected' : ''}`}
          onClick={() => onSelect(area.deliveryAreaId)}
          aria-pressed={selected}
        >
          <span className="area-card__name">
            {area.areaName}
            {distanceLabel && <span className="area-card__distance">{distanceLabel}</span>}
          </span>
          <span className="area-card__row-end">
            {mapsLink}
            {selected && (
              <span className="area-card__tick">
                <Icon name="check" size={16} strokeWidth={2.6} />
              </span>
            )}
          </span>
        </button>
      ) : (
        <div className="area-card__area area-card__area--static">
          <div className="area-card__row-top">
            <span className="area-card__name">
              {area.areaName}
              {distanceLabel && <span className="area-card__distance">{distanceLabel}</span>}
            </span>
            {mapsLink}
          </div>
          {description && area.description && <span className="area-card__description">{area.description}</span>}
        </div>
      )}
    </li>
  )
}
