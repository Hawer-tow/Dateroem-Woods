// "the user has a choice if the rating be made public or private ... this
// only happens when one ticks the choice and has to read through the
// agreement area then tick yes" - the agreement shown before a rating can
// be made public. The version is sent along with the customer's "I agree"
// and stored with their rating; it must match CONSENT_VERSION in the
// backend's ReviewService. Change the text => bump both.
export const REVIEW_CONSENT_VERSION = '2026-09-26'

export const REVIEW_CONSENT_TITLE = 'Public rating agreement'

export const REVIEW_CONSENT_POINTS = [
  'Your rating is private unless you agree here. A private rating still counts towards the product\'s average star score, but nobody can see who gave it.',
  'If you make it public, anyone visiting the Dateroem website can see, on this product\'s page: your first name and the first letter of your last name (for example "Timothy K."), your star rating, your comment, and the month you rated.',
  'Nothing else about you is ever shown - not your full name, phone number, email, username, delivery location or order details.',
  'Only write in your comment what you are happy for the public to read. Please don\'t include phone numbers or other personal details, yours or anyone else\'s.',
  'Dateroem may hide a rating that is abusive, false or unrelated to the product. A hidden rating is removed from the product page and its score.',
  'You can change your mind at any time: open Ordered Goods, go to "Your ratings" and make the rating private again or remove it. It stops showing publicly straight away.',
  'Choosing public or private never affects your orders, prices or deliveries.',
]
