# The website adverts

Two vertical videos (1080 x 1920, the shape of TikTok and WhatsApp Status),
made from the real website in the Forest look (25 October 2026). Both open
with "Hi there! Do you need luxury hardwood?".

- **Short (about 1 minute):** a general view.
  - The website.
  - What we sell.
  - How ordering works.
  - Paying with M-Pesa.
  - Delivery or pickup.
  - Damaged goods and refunds.
  - Where to find us.
  - Where to order and follow us.
- **Long (about 2.5 to 3 minutes):** a customer doing everything, in 18 labelled steps.
  - Browse, see what's featured, search and open a product.
  - Choose the amount and pick a pickup point.
  - Sign in and pay with M-Pesa.
  - The order and its ticket, Ordered Goods, and tracking.
  - Delivery, bulk orders and returns.
  - The assistant, who we are, and where to follow us.
  - It is made to be edited (voice, music) in DaVinci Resolve.

Neither has music. On TikTok, a business account may only use sounds from TikTok's
Commercial Music Library, so add the sound inside TikTok or in the editor.

## Real screens, sample order

The window shows the **live** shop: its products, pickup areas, campaigns,
pictures and the assistant's real answers. `live-data.vite.config.mjs` reads them
from https://dateroemwoods.co.ke. Nothing is ever written there.

`driver.mjs` answers signing in, placing the order, the payment and the order
pages itself, with a sample customer and order (`SAMPLE`: Grace Wanjiru, 0712 345
678, ticket DW-4M8T2R). It refuses every other request that would change
something.

The M-Pesa prompt is an illustrated phone, labelled "Illustration", not
Safaricom's screen.

| File | What it is |
| --- | --- |
| `ad.html` | The stage. It holds the opening and closing cards, the captions (shrunk to stay on two lines), the step labels, the browser window, and the phone. |
| `driver.mjs` | The filming, the slow motion, the pointer and typing, and the sample order. |
| `record-short.mjs`, `record-long.mjs` | The two scripts, scene by scene. Times are in seconds of the finished video. |
| `make-ad.sh` | Builds the website as it is live and serves it with `ad.html` on port 4192. |
| `live-data.vite.config.mjs` | Lets that copy read the live shop's data. |

## Making them again

Edit the live shop first (products, campaign, pictures); the adverts pick it up.

1. In Ubuntu: `bash /root/fathersProject/marketing/website-ad/make-ad.sh` (it keeps running).
2. Start a headless browser (Windows):
   `msedge --headless=new --remote-debugging-port=9333 --user-data-dir=%TEMP%\ad-edge`.
3. Film. A trailing `1` runs it at normal speed, as a quick test of the steps.
   - The short one takes about 13 minutes:
     `node --experimental-websocket record-short.mjs 9333 http://127.0.0.1:4192/ad/ad.html <frames folder>`
   - The long one takes about 25 minutes:
     `node --experimental-websocket record-long.mjs 9333 http://127.0.0.1:4192/ad/ad.html <frames folder>`
4. Make the video:
   `ffmpeg -f concat -safe 0 -i <frames folder>/list.txt -vf "fps=30,format=yuv420p" -c:v libx264 -profile:v high -crf 18 -preset slow -movflags +faststart advert.mp4`.

To change the words, edit `say(...)`, `step(...)` and `chips(...)` in the scripts.
To change the closing card, edit `ad.html`.
