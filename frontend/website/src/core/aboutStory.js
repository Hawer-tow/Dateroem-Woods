// The About page's words - edit them here. The pictures come from
// public/story/ (made by scripts/build_story_frames.py), one stage per clip
// of the animation, in this same order.
//
// Each stage:
//   label   the step's name, above the title
//   title   the card's heading
//   text    the card's paragraph
//   scene   what the picture shows, read out by screen readers
//   focusX  on phones the wide picture is cropped to fit - which part of
//           it to keep (0 = left edge, 0.5 = centre, 1 = right edge)

export const ABOUT_INTRO = {
  eyebrow: 'About Dateroem',
  title: 'From the forest to your door',
  lead:
    'Every piece of timber we sell has a story - the tree it grew on, the hands that shaped it and the road it travelled to reach you. Scroll to follow it.',
}

export const STORY_STAGES = [
  {
    label: 'Acquisition',
    title: 'Sustainable Selection in Kenya Highlands',
    text: 'Sourcing elderly, majestic White Oak giants responsibly to protect canopy ecosystems.',
    scene: 'A great old tree on a misty hillside at sunrise',
    focusX: 0.5,
  },
  {
    label: 'Harvest',
    title: 'Precision Timber Logging',
    text: 'Capturing optimal wood core grain structure via clean, low-impact artisanal harvesting methods.',
    scene: 'A logger in safety gear felling a tree with a chainsaw',
    focusX: 0.36,
  },
  {
    label: 'Value addition',
    title: 'The Nakuru Mastercraft Studio',
    text: 'Precision planing, laser guiding, and hand-finishing raw timber into luxury flagship furniture slabs.',
    scene: 'A craftsman guiding a timber slab through a planer in the Dateroem workshop',
    focusX: 0.5,
  },
  {
    label: 'Packaging',
    title: 'Luxury Presentation Canvas',
    text: "Protecting premium handiwork inside sustainable organic canvas wrappers bound with twine, featuring our signature leather 'Made in Kenya' tag.",
    scene: "Timber wrapped in canvas and twine, with a leather 'Made in Kenya' tag",
    focusX: 0.55,
  },
  {
    label: 'Delivery',
    title: 'Nationwide Transit Reach',
    text: 'Fleet deployment mapping across the Great Rift Valley highway corridor, delivering flagship furniture straight to your door across East Africa.',
    scene: 'A Dateroem truck on a highway above the Rift Valley at sunset',
    focusX: 0.55,
  },
]

export const ABOUT_CLOSING = {
  title: 'Ready when you are',
  text: 'Browse what is in stock today, or talk to us about a bulk order.',
}

// "use the company details such as founder Charles ... in the about page so
// that it looks good and an area for a photo" (23 October 2026) - from the
// Business Profile (documentation/Business Profile.docx). The tax PIN and
// personal phone numbers stay off the website; contacts are the company's.
export const ABOUT_COMPANY = {
  eyebrow: 'Who we are',
  title: 'A Kenyan timber business, rooted in Nakuru',
  text:
    'Dateroem Woods deals in timber and native African hardwoods. We buy our trees from farms across Central Kenya, add value through precision milling and finishing at our workshop in Nakuru, and deliver to customers across the country. Customers browse and order on our website, pay securely with M-Pesa and collect from a pickup point of their choice; for large or specialised orders our team works directly with the customer on supply, permits and delivery. We serve builders, furniture makers, businesses and homeowners.',
  facts: [
    ['Registered name', 'Dateroem Woods'],
    ['Business registration', 'BN-OVS62756'],
    ['Workshop', 'Ebenezar Plot, Old Nairobi Road, Nakuru'],
    ['Where our timber comes from', 'Farms across Central Kenya'],
    ['What we do', 'Sourcing, milling, finishing and nationwide delivery of timber and native African hardwoods'],
  ],
  mission:
    'To make quality, responsibly sourced Kenyan timber easy to buy and reliable to receive - from the forest to the customer\'s door.',
  vision:
    'To be a trusted name in East African timber, known for quality hardwoods, fair sourcing and dependable service.',
  values: [
    'A simple, secure way to buy timber online and pay with M-Pesa.',
    'Timber sourced responsibly and value added locally through milling and finishing.',
    'Reliable delivery through a nationwide network of pickup and delivery points.',
    'Local suppliers and farmers paid promptly and transparently.',
    'A sustainable business that protects the forests it depends on.',
  ],
}

// How we do business - "where we are located and how we do our business ...
// sourcing is from central kenya as usual" (23 October 2026).
export const ABOUT_HOW = {
  title: 'How we work',
  steps: [
    ['Sourcing', 'We buy trees from farms across Central Kenya, with the permits each harvest needs, and pay the farmers promptly and transparently.'],
    ['Milling and finishing', 'The logs come to our workshop in Nakuru, where they are milled and finished into poles, sawn timber, boards and templates.'],
    ['Ordering and paying', 'Customers order on our website or through our team and pay with M-Pesa on their own phone - every payment is matched to its order automatically.'],
    ['Delivery', `Orders are collected from a pickup point of the customer's choice or delivered across the country; bulk orders are arranged directly with our team.`],
  ],
}

// Where to find us. The map pin is the owner's own (23 October 2026), kept
// without Google's tracking parameters.
export const ABOUT_LOCATION = {
  title: 'Where to find us',
  place: 'Dateroem Woods workshop',
  address: 'Ebenezar Plot, Old Nairobi Road, Nakuru, Kenya',
  mapUrl: 'https://www.google.com/maps/search/?api=1&query=-0.295030%2C36.125348',
  note: 'We serve customers across Kenya from here - by delivery or through our pickup points.',
}

// The founder. The photo is public/brand/founder.jpg (square, at least 800 x
// 800): until that file is added the frame shows the company emblem.
export const ABOUT_FOUNDER = {
  eyebrow: 'The people behind Dateroem',
  name: 'Charles Mwangi Kimundu',
  role: 'Founder',
  photo: '/brand/founder.jpg',
  text:
    'Charles founded Dateroem Woods and leads it from the workshop in Nakuru - from choosing the trees and the milling to making sure every order reaches its customer.',
}
