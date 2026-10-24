// The three policy pages (24 October 2026): Returns & Refunds, Privacy and
// Terms. "provide ... the refund rules" - the owner's answers, word for word
// in substance: 1) cancel before delivery - full refund if nothing is cut or
// loaded, otherwise a cancellation cost agreed first; 2) late past the
// latest delivery date - full refund, nothing deducted; 3) damaged, wrong or
// missing - tell us within 3 days; 4) no returns, except damaged goods;
// 5) to the same M-Pesa number within 3 working days; 6) bulk orders are
// fixed once agreed; 7) how to reach us. The Privacy page lists what the
// system really keeps and who else sees it (Kenya's Data Protection Act,
// 2019). Change a rule here and in the Help page's answers together.

export const POLICY_UPDATED = '24 October 2026'

export const POLICIES = {
  returns: {
    path: '/returns',
    title: 'Returns & Refunds',
    lead:
      'We want every order to arrive as promised. This page explains when you can cancel, what happens if something arrives damaged, and how your money comes back to you.',
    sections: [
      {
        heading: 'Cancelling before delivery',
        paragraphs: [
          'You can cancel your order at any time before it is delivered.',
          'If nothing has been cut or loaded for your order yet, you get back the full amount you paid.',
          'If work has already been done for it - timber cut to your sizes, or transport arranged - we agree a cancellation cost with you first and refund the rest.',
        ],
      },
      {
        heading: 'If we are late',
        paragraphs: [
          'Every order shows its latest delivery date: about 3 weeks after you order, or 3 months for very large single pieces. If that date passes and your order has not been delivered, you can cancel it and get back the full amount, with nothing deducted.',
        ],
      },
      {
        heading: 'Damaged, wrong or missing goods',
        paragraphs: [
          'Please check your goods when they arrive. If anything is damaged, is not what you ordered, or is missing, tell us within 3 days of delivery. Send photos and your order ticket (it starts with DW-).',
          'Once we have confirmed the problem, we replace those pieces or refund them.',
        ],
      },
      {
        heading: 'Returns',
        paragraphs: [
          'We do not take goods back because of a change of mind. Goods can only be returned when they arrived damaged.',
        ],
      },
      {
        heading: 'How refunds are paid',
        paragraphs: [
          'Refunds are sent to the same M-Pesa number that paid for the order, within 3 working days of agreeing the amount with you. M-Pesa sends you its usual message when the money arrives.',
        ],
      },
      {
        heading: 'Bulk orders',
        paragraphs: [
          'Bulk orders are agreed with each customer one by one. Once agreed, their terms are fixed and are not changed afterwards.',
        ],
      },
      {
        heading: 'How to ask',
        paragraphs: [
          'Call or WhatsApp us, or send us an email, using the details below. Mention your order ticket (DW-...) - you will find it on your order confirmation, in Ordered Goods, and on the Track my order page.',
        ],
      },
    ],
  },

  privacy: {
    path: '/privacy',
    title: 'Privacy',
    lead:
      'This page explains what we keep about you, why, who else sees it and the choices you have. We follow Kenya’s Data Protection Act, 2019.',
    sections: [
      {
        heading: 'Who we are',
        paragraphs: [
          'Dateroem Woods (Dateroem Wood Sales) is a timber business in Nakuru, Kenya. We decide how the information on this site is used, and you can reach us with the details at the bottom of this page.',
        ],
      },
      {
        heading: 'What we keep',
        bullets: [
          'Your account: your name, username and phone number (confirmed with an SMS code), your email if you give one, and your password - stored scrambled, so nobody, not even our staff, can read it. Also the profile photo and colour theme you choose, if any.',
          'If you sign in with Google: the name and email of your Google account.',
          'Your orders: what you bought, the delivery area you chose, the M-Pesa number you paid with, M-Pesa’s receipt, your order ticket and the reviews you write.',
          'Messages: what you ask our assistant, kept with your account so the conversation can continue. An email you send through the site goes straight to our mailbox; the site keeps no copy.',
          'Cookies: your cart and your sign-in, which the site needs to work. Analytics and marketing cookies only if you allow them.',
        ],
      },
      {
        heading: 'Why we use it',
        bullets: [
          'To take, prepare and deliver your orders, and to take payment and send refunds.',
          'To confirm your phone number and keep your account safe.',
          'To answer you when you ask for help.',
          'To keep the business records Kenyan law requires.',
          'Only if you allow it: to understand how the site is used, so we can improve it.',
        ],
      },
      {
        heading: 'Who else sees it',
        paragraphs: ['We never sell your information. It is shared only where needed to serve you:'],
        bullets: [
          'Our staff, to prepare and deliver your order - only what each of them needs.',
          'Safaricom (M-Pesa), to take your payment and to send refunds.',
          'Africa’s Talking, to send the SMS code that confirms your number.',
          'Google, if you choose to sign in with Google, and Google Analytics if you allow analytics cookies.',
          'Our assistant’s AI service (Groq at present), which receives the questions you ask the assistant in order to write its answers.',
        ],
        after:
          'Some of these services keep data outside Kenya. We use them only for the purposes above.',
      },
      {
        heading: 'How long we keep it',
        paragraphs: [
          'Your account stays until you ask us to close it. Orders and payment records are kept for as long as Kenyan tax law requires (at least five years). Your conversations with the assistant are kept with your account and can be deleted on request.',
        ],
      },
      {
        heading: 'Your choices and rights',
        bullets: [
          'See and change your name, email, photo and password any time in My profile.',
          'Ask us for a copy of what we keep about you, to correct it, or to delete it.',
          'Change your cookie choices any time with Cookie settings at the bottom of every page.',
          'If you are unhappy with how we handle your data, you can complain to the Office of the Data Protection Commissioner (odpc.go.ke).',
        ],
      },
      {
        heading: 'Keeping it safe',
        paragraphs: [
          'The site is reached over an encrypted connection, passwords are stored scrambled, and only staff who need your details for their work can see them. The shop is meant for adults; we do not knowingly keep information about children.',
        ],
      },
      {
        heading: 'Changes',
        paragraphs: ['When this page changes, the date at the top changes with it.'],
      },
    ],
  },

  terms: {
    path: '/terms',
    title: 'Terms',
    lead:
      'These terms apply when you use this site and when you order from Dateroem Woods. By placing an order you agree to them.',
    sections: [
      {
        heading: 'Your account',
        paragraphs: [
          'Give us your real name and a phone number you use - we confirm it with an SMS code. Keep your password to yourself: you are responsible for orders placed from your account.',
        ],
      },
      {
        heading: 'Products and prices',
        paragraphs: [
          'Prices are in Kenya shillings. Timber is a natural material: the colour and grain of what you receive can differ a little from the photos. The stock shown is live and is confirmed when you place your order.',
        ],
      },
      {
        heading: 'Ordering and paying',
        paragraphs: [
          'You pay with M-Pesa: a payment prompt arrives on the phone number you give at checkout. Your order is confirmed once M-Pesa confirms the payment. If the payment fails or you cancel the prompt, the order is cancelled automatically and nothing is taken.',
        ],
      },
      {
        heading: 'Delivery',
        paragraphs: [
          'We deliver to, or you collect from, the delivery area you choose. Every order shows its latest delivery date - about 3 weeks after ordering, or 3 months for very large single pieces - and you can follow it with your order ticket on the Track my order page. We show the driver’s first name and the number plate when we share them for your delivery.',
        ],
      },
      {
        heading: 'Cancellations, refunds and returns',
        paragraphs: ['See Returns & Refunds - it is part of these terms.'],
        link: '/returns',
      },
      {
        heading: 'Bulk orders',
        paragraphs: [
          'Bulk orders (from the Bulk Orders page) are agreed with you separately. Once agreed, their terms are fixed.',
        ],
      },
      {
        heading: 'Reviews and the assistant',
        paragraphs: [
          'Reviews must be honest and about the product; we may remove reviews that are abusive or misleading. Our assistant answers with the help of AI and can make mistakes - for anything important, such as prices, sizes or delivery dates, check with us.',
        ],
      },
      {
        heading: 'Our responsibility',
        paragraphs: [
          'We are responsible for delivering what you ordered, as described, by the latest delivery date. We are not responsible for losses that do not come from our own fault. Nothing in these terms takes away your rights under Kenyan law, including the Consumer Protection Act, 2012.',
        ],
      },
      {
        heading: 'Changes and the law',
        paragraphs: [
          'We may update these terms; the date at the top shows the latest version, and an order follows the terms in force when it was placed. These terms are governed by the laws of Kenya. If something goes wrong, please talk to us first - most things can be put right quickly.',
        ],
      },
    ],
  },
}

export const POLICY_ORDER = ['returns', 'privacy', 'terms']
