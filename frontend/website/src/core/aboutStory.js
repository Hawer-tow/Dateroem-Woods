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
