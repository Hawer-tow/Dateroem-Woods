// The long advert (25 October 2026): "the site's major operations for a
// customer" - browsing, searching, a product, ordering, choosing a pickup
// point, signing in, paying with M-Pesa, the order, Ordered Goods, tracking,
// delivery, bulk orders, returns, the assistant, who we are and where to
// find us. About two and a half minutes, made to be edited (voice, music)
// in DaVinci Resolve. The order is a sample (driver.mjs); nothing is
// written to the live site.
//   node --experimental-websocket record-long.mjs <devtools port> <ad page url> <frames folder>
import { SAMPLE, startFilming } from './driver.mjs'

const [port, url, dir] = process.argv.slice(2)
const f = await startFilming({ port, url, dir, slow: Number(process.argv[5]) || 8 })
const { hold } = f
const step = async (label, top, bottom = '') => {
  await f.chapter(label)
  await f.say(top, bottom)
}

// The opening question, then the name; the website starts loading behind it.
await f.hook('Hi there!')
await hold(2.2)
await f.hook('Do you need <span class="wood">luxury hardwood?</span>')
await hold(2.8)
await f.hook(null)
await f.body('s-intro')
await hold(1.4)
await f.openSite('/')
await hold(2.2)

// 1. The website.
await f.body('s-intro-out', 's-site')
await step('1 · The website', 'Welcome to', 'dateroemwoods.co.ke')
await hold(4.6)

// 2. Browse.
await step('2 · Browse', 'Native African', 'hardwoods')
await f.chips(['Timber poles', 'Sawn timber', 'Log pieces', 'Slabs & decor'])
await f.scrollTo('#products', 1.8)
await hold(0.8)
await f.camOn('.product-card--shop', 1.45, 330, 300, 0.5, 0.45, 0)
await hold(1.8)
await f.camOn('.product-card--shop', 1.45, 640, 300, 0.5, 0.45, 2)
await hold(2.0)
await f.cam(0)
await hold(0.6)
await f.scrollBy(380, 1.6)
await hold(1.4)
await f.scrollTo('#products', 1.2)

// 3. Featured.
await step('3 · Featured', 'See what’s featured', 'this week')
await f.chips(null)
await f.camOn('.featured-sapling__plant', 1.5, 640, 318)
await hold(1.5)
await f.slowVideos()
const soil = await f.place('.featured-sapling__roots', 0.5, 0.62)
if (soil) {
  await f.pointerAt(soil.x + 240, soil.y + 260)
  await f.moveTo(soil.x, soil.y, 0.9)
  await hold(3.0)
  await f.moveTo(soil.x + 300, soil.y + 280, 0.6)
}
await f.pointerOff()
await f.cam(0)
await hold(1.0)

// 4. Search.
await step('4 · Search', 'Find what you need', 'in seconds')
await f.scrollTo('.product-search', 1.0, '', 130)
await f.camOn('.product-search', 1.35, 330, 160, 0.4, 0.5)
await hold(1.3)
await f.clickOn('.product-search input', { fx: 0.3, after: 0.3 })
await f.type('poles', 1.0)
await hold(2.4)
await f.clickOn('.product-search__clear', { after: 1.0 })
await f.pointerOff()
await f.cam(0)
await hold(0.6)

// 5. A product.
await step('5 · Every detail', 'Every detail', 'before you buy')
await f.scrollTo('#products', 0.8)
await f.clickOn('.product-card--shop .product-card__image', { index: 0, move: 0.8, after: 0.2 })
await f.pointerOff()
await hold(1.2)
await f.camOn('.product-detail__info', 1.3, 560, 260, 0.5, 0.35)
await hold(3.0)
await f.cam(0)
await f.say('From the forest', 'to your door')
await f.scrollTo('.forest-journey', 1.6)
await hold(3.0)
await f.say('More from', 'the forest')
await f.scrollTo('.forest-more', 1.4)
await hold(1.8)
await f.scrollTo(0, 2.0)

// 6. Into the cart.
await step('6 · Order', 'Choose the amount,', 'add it to your cart')
await f.camOn('.product-detail__actions', 1.6, 420, 360, 0.3, 0.5)
await hold(1.3)
await f.clickOn('.product-detail__actions .qty-stepper__btn', { index: 1, after: 0.5 })
await f.clickOn('.product-detail__actions .btn--primary', { move: 0.6, after: 1.1 })
await f.clickOn('.product-detail__actions .btn--secondary', { text: 'Go to cart', move: 0.6, after: 0.3 })
await f.pointerOff()
await f.cam(0)
await hold(1.2)

// 7. Checkout: the pickup point and the M-Pesa number.
await step('7 · Checkout', 'Pick your delivery', 'or pickup point')
await f.clickOn('.checkout-form__areas .btn--secondary', { move: 0.8, after: 1.0 })
await f.clickOn('.area-picker__search input', { fx: 0.3, after: 0.3 })
await f.type('Nakuru', 0.9)
await hold(1.0)
await f.clickOn('.area-card__area', { text: 'Town Center', move: 0.8, after: 1.2 })
await f.clickOn('.checkout-form__confirm input', { index: 0, move: 0.7, after: 0.5 })
await f.say('Enter your', 'M-Pesa number')
await f.clickOn('.checkout-form input[placeholder="07XX XXX XXX"]', { fx: 0.3, move: 0.6, after: 0.3 })
await f.type(SAMPLE.phone, 1.3)
await hold(0.4)
await f.clickOn('.checkout-form__confirm input', { index: 1, move: 0.6, after: 0.8 })

// 8. Sign in (once).
await step('8 · Sign in', 'Sign in once,', 'or create a free account')
await f.clickOn('.checkout-form .btn--primary', { text: 'Place order', move: 0.7, after: 1.2 })
await f.camOn('.modal__form', 1.25, 500, 300, 0.5, 0.45)
await hold(1.3)
await f.clickOn('.modal__form input', { index: 0, fx: 0.3, after: 0.3 })
await f.type(SAMPLE.userName, 0.8)
await f.clickOn('.modal__form input', { index: 1, fx: 0.3, after: 0.3 })
await f.type(SAMPLE.password, 0.8)
await hold(0.4)
f.holdCheckout()
await f.clickOn('.modal__form button[type="submit"]', { move: 0.6, after: 0.4 })
await f.pointerOff()
await f.cam(0)

// 9. Pay with M-Pesa: the prompt on the phone, the PIN, then M-Pesa confirms.
await step('9 · Pay', 'Pay with M-Pesa,', 'right on your phone')
await f.chips(['No cash', 'Paid before delivery'])
await hold(1.2)
// The amount from the order the website just sent (2 of the first product).
await f.stk(f.sampleAmount())
await hold(1.6)
for (let n = 1; n <= 4; n += 1) {
  await f.stkPin(n)
  await hold(0.32)
}
await hold(0.4)
await f.stkSend()
await hold(0.7)
await f.stk(null)
f.releaseCheckout()
await hold(2.6)
f.markPaid()
await f.waitFor(`d.querySelector('.order-confirmation__status')`, 60)
await f.chips(null)
await hold(0.6)

// 10. The order is placed.
await step('10 · Done', 'Your order is placed,', 'with its own ticket')
await f.chips([`Ticket ${SAMPLE.ticket}`, 'About 3 weeks'])
await f.camOn('.order-confirmation__status', 1.3, 500, 260)
await hold(3.4)
await f.cam(0)
await f.scrollBy(300, 1.2)
await hold(1.4)

// 11. Ordered Goods.
await step('11 · Ordered Goods', 'Follow every order', 'in Ordered Goods')
await f.chips(null)
await f.goTo('/my-orders')
await hold(0.6)
await f.camOn('.my-orders__order, .my-orders__group-card', 1.25, 500, 300, 0.5, 0.35)
await hold(3.0)
await f.cam(0)

// 12. Track without signing in.
await step('12 · Track', 'Track any order', 'with its ticket')
await f.goTo('/track')
await hold(0.4)
await f.camOn('.track-form', 1.3, 500, 260)
await hold(1.2)
await f.clickOn('.track-form input', { index: 0, fx: 0.3, after: 0.3 })
await f.type(SAMPLE.ticket, 1.1)
await f.clickOn('.track-form input', { index: 1, fx: 0.3, after: 0.3 })
await f.type(SAMPLE.phone.slice(-3), 0.5)
await f.clickOn('.track-form__submit', { move: 0.6, after: 0.4 })
await f.pointerOff()
await f.waitFor(`d.querySelector('.track-result')`, 30)
await f.cam(0)
await f.scrollTo('.track-result', 1.0, '', 140)
await hold(3.0)

// 13. Delivery and pickup.
await step('13 · Delivery', 'Delivery or pickup', 'across Kenya')
await f.chips(['About 3 weeks', 'Pickup points by town'])
await f.goTo('/shipping', { to: '.area-picker__groups', room: 140 })
await hold(1.6)
await f.scrollBy(320, 2.4)
await hold(1.6)

// 14. Bulk orders.
await step('14 · Bulk orders', 'Big project?', 'Order in bulk with us')
await f.chips(null)
await f.goTo('/heavy-order', { to: '.showcase', room: 120 })
const show = await f.place('.showcase', 0.5, 0.5)
if (show) {
  await f.pointerAt(show.x, show.y + 140)
  await f.moveTo(show.x, show.y, 0.6)
  await hold(1.6)
  await f.clickOn('.showcase__arrow--next', { move: 0.6, after: 2.2 })
  await f.pointerOff()
}

// 15. Returns and refunds.
await step('15 · Returns & refunds', 'Damaged on arrival?', 'We replace or refund')
await f.chips(['Tell us within 3 days', 'Refunds to your M-Pesa'])
await f.goTo('/returns', { to: 'h2, h3', text: 'Damaged, wrong or missing goods', room: 130 })
await hold(3.6)
await f.scrollTo('h2, h3', 2.0, 'How refunds are paid', 130)
await hold(2.8)

// 16. The assistant.
await step('16 · Help', 'Ask us anything,', 'answers in seconds')
await f.chips(null)
await f.goTo('/help')
await hold(0.4)
await f.clickOn('.help-page__chat-cta', { move: 0.7, after: 1.4 })
await f.clickOn('.chat-welcome .chat-context__chip', { text: 'Where are you located?', move: 0.7, after: 0.3 })
await f.pointerOff()
await f.waitFor(`d.querySelectorAll('.chat-bubble--bot:not(.chat-bubble--typing)').length >= 1`, 60)
await hold(3.2)
await f.clickOn('.chat-window__form input', { fx: 0.3, after: 0.3 })
await f.type('How long does delivery take?', 1.4)
await f.clickOn('.chat-window__send', { move: 0.4, after: 0.3 })
await f.pointerOff()
await f.waitFor(`d.querySelectorAll('.chat-bubble--bot:not(.chat-bubble--typing)').length >= 2`, 60)
await hold(4.0)
await f.clickOn('.help-widget__close', { move: 0.6, after: 0.8 })
await f.pointerOff()

// 17. Who we are, and where.
await step('17 · About us', 'Grown in Central Kenya,', 'milled in Nakuru')
await f.goTo('/about', { to: '#about-company-title', room: 120 })
await hold(3.2)
await f.scrollTo('#about-how-title', 2.2, '', 120)
await hold(3.0)
await f.say('Visit our workshop', 'Old Nairobi Road, Nakuru')
await f.scrollTo('#about-location-title', 2.2, '', 120)
await hold(3.4)
await f.say('Meet', 'the founder')
await f.scrollTo('#about-founder-title', 2.2, '', 120)
await hold(3.0)

// 18. Where to follow us.
await step('18 · Follow us', 'Follow us', '@dateroem.woods')
await f.scrollTo(await f.ev('siteMaxScroll()'), 2.4)
await f.camOn('.footer__links', 1.3, 500, 360)
await hold(3.0)

// Where to order.
await f.outro()
await hold(7.0)
await f.finish()
