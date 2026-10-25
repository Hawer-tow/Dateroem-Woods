// The short advert (25 October 2026), about a minute - short enough for
// WhatsApp Status in one piece: "a general view of the site, who we are,
// product types, where to find us, our socials, how we handle orders, how we
// handle damaged goods or refunds". Opens with "Hi there! Do you need luxury
// hardwood?". Filmed from the live shop; nothing is written to it.
//   node --experimental-websocket record-short.mjs <devtools port> <ad page url> <frames folder>
import { startFilming } from './driver.mjs'

const [port, url, dir] = process.argv.slice(2)
const f = await startFilming({ port, url, dir, slow: Number(process.argv[5]) || 12 })
const { hold } = f

// 0-5 s: the question.
await f.hook('Hi there!')
await hold(2.2)
await f.hook('Do you need <span class="wood">luxury hardwood?</span>')
await hold(2.6)

// 5-8.5 s: who we are; the website starts loading behind it.
await f.hook(null)
await f.body('s-intro')
await hold(1.2)
await f.openSite('/')
await hold(2.2)

// 8.5-13 s: the website.
await f.body('s-intro-out', 's-site')
await f.say('Shop online at', 'dateroemwoods.co.ke')
await hold(4.4)

// 13-20 s: what we sell.
await f.say('Poles, sawn timber,', 'slabs & decor')
await f.chips(['Native hardwoods', 'Central Kenya farms'])
await f.scrollTo('#products', 1.6)
await hold(0.6)
await f.camOn('.product-card--shop', 1.4, 330, 300, 0.5, 0.45, 0)
await hold(1.6)
await f.camOn('.product-card--shop', 1.4, 640, 300, 0.5, 0.45, 2)
await hold(1.8)

// 20-27 s: how ordering works.
await f.say('Pick the amount.', 'Order in a tap.')
await f.chips(null)
await f.camOn('.product-card--shop', 1.75, 500, 330, 0.5, 0.62, 0)
await hold(1.3)
await f.clickOn('.card-buy .qty-stepper__btn', { index: 1, move: 0.8, after: 0.45 })
await f.clickOn('.card-buy .qty-stepper__btn', { index: 1, move: 0.2, after: 0.6 })
// Only pointing at Order now - this advert doesn't open the cart.
const orderNow = await f.place('.card-buy__order', 0.42, 0.5, 0)
if (orderNow) await f.moveTo(orderNow.x, orderNow.y, 0.7)
await hold(1.4)
await f.pointerOff()

// 27-33 s: paying.
await f.say('Pay with M-Pesa,', 'right on your phone')
const first = await f.ev(`document.getElementById('site').contentDocument.querySelector('.product-card--shop .product-card__price')?.textContent || ''`)
const unit = Number(String(first).replace(/[^\d.]/g, '')) || 0
await f.stk(`KES ${(unit * 3).toLocaleString('en-KE')}`)
await hold(1.5)
for (let n = 1; n <= 4; n += 1) {
  await f.stkPin(n)
  await hold(0.3)
}
await f.stkSend()
await hold(1.4)
await f.stk(null)

// 33-39 s: delivery.
await f.say('Delivery or pickup', 'across Kenya')
await f.chips(['About 3 weeks', 'Pickup points by town'])
await f.goTo('/shipping', { to: '.area-picker__groups', room: 140 })
await f.scrollBy(140, 3.4)
await hold(0.6)

// 39-46 s: damaged goods and refunds.
await f.say('Damaged on arrival?', 'We replace or refund')
await f.chips(['Tell us within 3 days', 'Refunds to your M-Pesa'])
await f.goTo('/returns', { to: 'h2, h3', text: 'Damaged, wrong or missing goods', room: 130 })
await f.scrollBy(120, 4.2)
await hold(0.8)

// 46-52 s: where to find us.
await f.say('Visit our workshop', 'Old Nairobi Road, Nakuru')
await f.chips(['WhatsApp +254 182 995 011'])
await f.goTo('/about', { to: '#about-location-title', room: 120 })
await f.scrollBy(90, 4.0)
await hold(0.6)

// 52-60 s: where to order and follow us.
await f.chips(null)
await f.outro()
await hold(8.0)
await f.finish()
