// "since this wood for areas previously having the orange color looks good
// you can extend it to the mobile and desktop" - renders the website's own
// painted wood (src/wood/) to PNGs for the desktop admin app and the mobile
// app, so all three wear the same wood. Re-run after changing the palette
// or the brush, then rebuild the apps:
//
//   npm run wood:export -- --mobile <mobile project>/app/src/main/res/drawable-nodpi
//
// --desktop defaults to this repo's admin_server_control desktop app.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Resvg } from '@resvg/resvg-js'
import { wordmarkSvg } from '../src/wood/wordmark.js'

const here = dirname(fileURLToPath(import.meta.url))
const textures = join(here, '..', 'src', 'assets', 'wood')
const args = process.argv.slice(2)
const argValue = (flag) => {
  const i = args.indexOf(flag)
  return i >= 0 ? args[i + 1] : null
}
const desktopDir = resolve(
  argValue('--desktop') ?? join(here, '..', '..', '..', 'admin_server_control', 'desktop', 'app', 'ui', 'assets', 'wood')
)
const mobileDir = argValue('--mobile')

function render(svg, width, file) {
  const png = new Resvg(svg, { fitTo: { mode: 'width', value: width }, background: 'rgba(0,0,0,0)' }).render().asPng()
  mkdirSync(dirname(file), { recursive: true })
  writeFileSync(file, png)
  console.log(`${file} (${Math.round(png.length / 1024)} KB)`)
}

const plank = readFileSync(join(textures, 'plank.svg'), 'utf8')
// A slightly lighter board for hover, same as the website's brightness lift.
const plankHover = plank.replace('</svg>', '<rect x="0" y="0" width="600" height="200" fill="#fff" opacity="0.1"/></svg>')
const logEnd = readFileSync(join(textures, 'log-end.svg'), 'utf8')
const wordmark = wordmarkSvg()

// Desktop - Qt picks the @2x file on high-DPI screens by itself.
render(plank, 600, join(desktopDir, 'wood_plank.png'))
render(plank, 1200, join(desktopDir, 'wood_plank@2x.png'))
render(plankHover, 600, join(desktopDir, 'wood_plank_hover.png'))
render(plankHover, 1200, join(desktopDir, 'wood_plank_hover@2x.png'))
render(wordmark, 440, join(desktopDir, 'dateroem_wordmark.png'))
render(wordmark, 880, join(desktopDir, 'dateroem_wordmark@2x.png'))

if (mobileDir) {
  render(plank, 1200, join(mobileDir, 'wood_plank.png'))
  render(logEnd, 384, join(mobileDir, 'wood_log_end.png'))
  render(wordmark, 1080, join(mobileDir, 'dateroem_wordmark.png'))
}
