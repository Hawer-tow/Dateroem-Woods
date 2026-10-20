// The company emblem (src/brand/emblem.js) as files:
//   public/                 the website's own icons - browser tab
//                           (favicon.svg, favicon.ico; the emblem filling
//                           the whole icon), phone home screen
//                           (apple-touch-icon.png, icon-192/512.png) and the
//                           footer's emblem (brand/emblem.svg)
//   <repo>/brand/           pictures for the business: the social media
//                           profile pictures and the logo on its own
//   desktop app             its window and taskbar icon (the website's
//                           icon), and the emblem for its sign-in card and
//                           intro
//   phone app (--mobile)    its home-screen icon (the website's icon) and
//                           the emblem for its entrance
//   <repo>/deploy/maintenance  the router's "we'll be right back" page,
//                           shown while a server is down
//
//   npm run brand:export
//   npm run brand:export -- --mobile <phone project>/app/src/main/res
//
// --desktop defaults to this repo's admin_server_control desktop app.
// After a change, rebuild the website and the phone app; the desktop app
// picks the files up when it next starts.
//
// The lettering uses Montserrat (brand/fonts, SIL Open Font License).
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Resvg } from '@resvg/resvg-js'
import { GOLD, NIGHT, emblemGroup, emblemSvg, tabIconSvg } from '../src/brand/emblem.js'

const here = dirname(fileURLToPath(import.meta.url))
const pub = join(here, '..', 'public')
const brand = join(here, '..', '..', '..', 'brand')
const fonts = join(brand, 'fonts')
const fontOptions = {
  fontFiles: ['Regular', 'Medium', 'SemiBold'].map((w) => join(fonts, `Montserrat-${w}.ttf`)),
  loadSystemFonts: false,
  defaultFontFamily: 'Montserrat',
}

function png(svg, width) {
  return new Resvg(svg, { fitTo: { mode: 'width', value: width }, font: fontOptions }).render().asPng()
}

function write(file, data) {
  mkdirSync(dirname(file), { recursive: true })
  writeFileSync(file, data)
  console.log(`${file} (${Math.max(1, Math.round(data.length / 1024))} KB)`)
}

// A .ico holding PNG pictures, for browsers that don't take the SVG icon.
function ico(sizes, svg) {
  const images = sizes.map((size) => png(svg, size))
  const header = Buffer.alloc(6 + 16 * images.length)
  header.writeUInt16LE(0, 0)
  header.writeUInt16LE(1, 2)
  header.writeUInt16LE(images.length, 4)
  let offset = header.length
  images.forEach((image, i) => {
    const at = 6 + 16 * i
    header.writeUInt8(sizes[i] % 256, at)
    header.writeUInt8(sizes[i] % 256, at + 1)
    header.writeUInt16LE(1, at + 4)
    header.writeUInt16LE(32, at + 6)
    header.writeUInt32LE(image.length, at + 8)
    header.writeUInt32LE(offset, at + 12)
    offset += image.length
  })
  return Buffer.concat([header, ...images])
}

// "a picture with the dateroem woods below it ... for the business socials
// tiktok and instagram" / "a profile size image". 1080 x 1080, and
// everything inside the circle TikTok and Instagram cut profile pictures to.
function profileSvg({ withName }) {
  const { defs, mark } = emblemGroup('full')
  const size = withName ? 600 : 860
  const top = withName ? 150 : 110
  const emblem = `<g transform="translate(${540 - size / 2} ${top}) scale(${size / 512})">${mark}</g>`
  const word = (text, y, sizePx, spacing, weight) =>
    `<text x="540" y="${y}" text-anchor="middle" font-family="Montserrat" font-weight="${weight}" ` +
    `font-size="${sizePx}" letter-spacing="${spacing}" fill="url(#gold-text)">${text}</text>`
  // "WOODS" is inked from x 392 to 687 at this size; a short line either
  // side of it, 28 apart from the letters.
  const name = withName
    ? word('DATEROEM', 835, 100, 16, 600) +
      word('WOODS', 905, 46, 26, 500) +
      `<path d="M274 889H364M716 889H806" stroke="url(#gold-text)" stroke-width="3" stroke-linecap="round"/>`
    : ''
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1080" viewBox="0 0 1080 1080"><defs>${defs}` +
    `<radialGradient id="night" cx="540" cy="500" r="720" gradientUnits="userSpaceOnUse">` +
    `<stop offset="0" stop-color="${NIGHT[0]}"/><stop offset="1" stop-color="${NIGHT[1]}"/></radialGradient>` +
    `<linearGradient id="gold-text" gradientUnits="userSpaceOnUse" x1="200" y1="760" x2="880" y2="920">` +
    `<stop offset="0" stop-color="${GOLD[0]}"/><stop offset="0.45" stop-color="${GOLD[1]}"/>` +
    `<stop offset="0.8" stop-color="${GOLD[2]}"/><stop offset="1" stop-color="${GOLD[3]}"/></linearGradient>` +
    `</defs><rect width="1080" height="1080" fill="url(#night)"/>${emblem}${name}</svg>`
  )
}

const tab = tabIconSvg()
const homeScreen = emblemSvg({ weight: 'full', tile: 'square' })

// Website
write(join(pub, 'favicon.svg'), Buffer.from(tab))
write(join(pub, 'favicon.ico'), ico([16, 32, 48], tab))
write(join(pub, 'apple-touch-icon.png'), png(homeScreen, 180))
write(join(pub, 'icon-192.png'), png(homeScreen, 192))
write(join(pub, 'icon-512.png'), png(homeScreen, 512))
write(join(pub, 'brand', 'emblem.svg'), Buffer.from(emblemSvg({ weight: 'bold' })))

// For the business
write(join(brand, 'Dateroem Woods profile picture.png'), png(profileSvg({ withName: true }), 1080))
write(join(brand, 'Dateroem Woods profile picture (emblem only).png'), png(profileSvg({ withName: false }), 1080))
write(join(brand, 'Dateroem Woods logo.svg'), Buffer.from(emblemSvg({ weight: 'full' })))
write(join(brand, 'Dateroem Woods logo.png'), png(emblemSvg({ weight: 'full' }), 1024))
write(join(brand, 'Dateroem Woods logo on dark.png'), png(emblemSvg({ weight: 'full', tile: 'square' }), 1024))

// The desktop and phone apps - "the one we have as the website icon will be
// used in the mobile app as its icon replacing the tree like one also in
// the desktop" (18 October 2026). Their icons are the website's browser-tab
// icon, the clearest drawing at small sizes; the full emblem, every ring,
// goes where it is shown large.
const args = process.argv.slice(2)
const argValue = (flag) => {
  const i = args.indexOf(flag)
  return i >= 0 ? args[i + 1] : null
}
const desktopApp = resolve(argValue('--desktop') ?? join(here, '..', '..', '..', 'admin_server_control', 'desktop', 'app'))
const mobileRes = argValue('--mobile')

// The router's "we'll be right back" page (deploy/maintenance/index.html),
// shown by Caddy when a server behind it is down - "in the event of an
// error e.g the server is offline ... never gets the whole details but what
// is necessary and presentable" (18 October 2026). Everything is inside the
// one file (it must work with every other server down); it checks every 20
// seconds whether the site is back and then reloads.
const maintenancePage = `<!doctype html>
<html lang="en" data-maintenance>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Dateroem Woods - back shortly</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
    padding: 32px 16px; text-align: center; color: #f3e9d8;
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    background: radial-gradient(circle at 50% 38%, ${NIGHT[0]}, ${NIGHT[1]} 70%);
  }
  main { max-width: 440px; }
  svg { width: min(46vw, 180px); height: auto; }
  h1 { margin: 28px 0 10px; font-size: 1.6rem; font-weight: 600; letter-spacing: 0.02em; color: ${GOLD[0]}; }
  p { margin: 0 0 12px; line-height: 1.55; color: #d8cbb6; }
  .contact { margin-top: 22px; font-size: 0.95rem; }
  .contact a { color: ${GOLD[1]}; text-decoration: none; font-weight: 600; }
  .waiting { margin-top: 26px; font-size: 0.82rem; color: #9c8e78; }
  .dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: ${GOLD[1]};
         margin-right: 8px; vertical-align: middle; animation: pulse 1.6s ease-in-out infinite; }
  @keyframes pulse { 50% { opacity: 0.25; } }
  @media (prefers-reduced-motion: reduce) { .dot { animation: none; } }
</style>
</head>
<body>
<main>
  ${emblemSvg({ weight: 'full' }).replace('<svg ', '<svg role="img" aria-label="Dateroem Woods" ')}
  <h1>We'll be right back</h1>
  <p>Dateroem Woods is taking a short break. Please try again in a few minutes.</p>
  <p class="contact">Need help now? Call or WhatsApp <a href="tel:+254182995011">+254 182 995 011</a></p>
  <p class="waiting"><span class="dot"></span>This page will reload by itself when we're back.</p>
</main>
<script>
  setInterval(function () {
    fetch(location.href, { cache: 'no-store' }).then(function (r) {
      return r.ok ? r.text() : '';
    }).then(function (text) {
      if (text && text.indexOf('data-maintenance') === -1) location.reload();
    }).catch(function () {});
  }, 20000);
</script>
</body>
</html>
`
write(join(here, '..', '..', '..', 'deploy', 'maintenance', 'index.html'), Buffer.from(maintenancePage))

write(join(desktopApp, 'assets', 'icon.png'), png(tab, 512))
write(join(desktopApp, 'assets', 'icon.ico'), ico([16, 24, 32, 48, 64, 128, 256], tab))
write(join(desktopApp, 'ui', 'assets', 'brand', 'emblem.png'), png(emblemSvg({ weight: 'full' }), 640))
write(join(desktopApp, 'ui', 'assets', 'brand', 'emblem_bold.png'), png(emblemSvg({ weight: 'bold' }), 256))

if (mobileRes) {
  const res = resolve(mobileRes)
  const split = (svg) => {
    const inner = svg.replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '')
    const defsEnd = inner.indexOf('</defs>') + '</defs>'.length
    return { defs: inner.slice(0, defsEnd), mark: inner.slice(defsEnd) }
  }
  // Android's adaptive icon: a 108 dp square of which launchers show at
  // least the middle 72 dp circle (66 dp is the safe zone), so the emblem
  // is 62 dp across - all of it shows whatever shape the phone cuts.
  const layer = (svg) => {
    const { defs, mark } = split(svg)
    const s = 62
    return (
      `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">${defs}` +
      `<g transform="translate(${(108 - s) / 2} ${(108 - s) / 2}) scale(${s / 512})">${mark}</g></svg>`
    )
  }
  // Android 13's themed icons tint this one-colour version.
  const monochrome = tab.replace(/url\(#gold\)/g, '#ffffff').replace('fill="url(#night)"', 'fill="none"')
  // Before Android 8 there are no adaptive icons: a whole picture, a
  // rounded square and a round one.
  const legacy = (round) => {
    const { defs, mark } = split(tab)
    const tile = round ? '<circle cx="256" cy="256" r="256" fill="url(#night)"/>' : '<rect width="512" height="512" rx="92" fill="url(#night)"/>'
    return (
      `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">${defs}${tile}` +
      `<g transform="translate(256 256) scale(0.78) translate(-256 -256)">${mark}</g></svg>`
    )
  }
  const densities = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 }
  for (const [name, k] of Object.entries(densities)) {
    const dir = join(res, `mipmap-${name}`)
    write(join(dir, 'ic_launcher_foreground.png'), png(layer(tab), 108 * k))
    write(join(dir, 'ic_launcher_monochrome.png'), png(layer(monochrome), 108 * k))
    write(join(dir, 'ic_launcher.png'), png(legacy(false), 48 * k))
    write(join(dir, 'ic_launcher_round.png'), png(legacy(true), 48 * k))
    // The older icons were WebP files of the same names; Android refuses
    // two pictures with one name.
    for (const old of ['ic_launcher.webp', 'ic_launcher_round.webp']) {
      if (existsSync(join(dir, old))) {
        rmSync(join(dir, old))
        console.log(`${join(dir, old)} (removed: replaced by the PNG)`)
      }
    }
  }
  // The dark behind the emblem, as on the website's home-screen icon.
  write(
    join(res, 'drawable', 'ic_launcher_background.xml'),
    Buffer.from(
      '<?xml version="1.0" encoding="utf-8"?>\n' +
        '<!-- Written by the website\'s "npm run brand:export" (scripts/export-brand.mjs):\n' +
        '     the dark behind the emblem, as on the website\'s home-screen icon. -->\n' +
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n' +
        '    xmlns:aapt="http://schemas.android.com/aapt"\n' +
        '    android:width="108dp" android:height="108dp"\n' +
        '    android:viewportWidth="108" android:viewportHeight="108">\n' +
        '    <path android:pathData="M0,0h108v108h-108z">\n' +
        '        <aapt:attr name="android:fillColor">\n' +
        `            <gradient android:type="radial" android:centerX="54" android:centerY="46"\n` +
        `                android:gradientRadius="76" android:startColor="${NIGHT[0]}" android:endColor="${NIGHT[1]}" />\n` +
        '        </aapt:attr>\n' +
        '    </path>\n' +
        '</vector>\n'
    )
  )
  write(join(res, 'drawable-nodpi', 'dateroem_emblem.png'), png(emblemSvg({ weight: 'full' }), 640))
}
