import Fireflies from './Fireflies.jsx'

// Behind every page in the forest look (25 October 2026): the board's misty
// forest, soft and far away, with mist drifting between the trees, light
// coming down through the canopy, leaf shadows moving as if wind stirs the
// branches above, and a few fireflies. All of it is CSS movement
// (styles/forest.css) on one fixed layer under the page, so scrolling stays
// smooth; with animations turned off it is a still forest. The veil dims
// only the photo, so text on the page stays easy to read while the light,
// mist and fireflies stay bright.
export default function ForestAmbience() {
  return (
    <div className="forest-ambience" aria-hidden="true">
      <div className="forest-ambience__photo" />
      <div className="forest-ambience__veil" />
      <div className="forest-ambience__rays" />
      <div className="forest-ambience__leaves" />
      <div className="forest-ambience__mist forest-ambience__mist--far" />
      <div className="forest-ambience__mist forest-ambience__mist--near" />
      <Fireflies count={14} className="forest-ambience__fireflies" />
    </div>
  )
}
