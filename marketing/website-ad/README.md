# The website advert

A 30-second vertical video (1080 x 1920, the shape of TikTok and WhatsApp
Status) made from the real website in the Forest look (25 October 2026). It
has five scenes:

1. The company name.
2. The website arriving.
3. The timber.
4. Picking an amount and Order now.
5. The Featured tree, then where to order.

It has no music. On TikTok, a business account may only use sounds from TikTok's
Commercial Music Library, so add the sound inside TikTok.

| File | What it is |
| --- | --- |
| `ad.html` | The advert page. It holds the captions, the closing card (address, WhatsApp number, @dateroem.woods) and the browser window with the website in it. |
| `record.mjs` | Films the page scene by scene. The scene times are in it. |
| `make-ad.sh` | Builds the website as it is live and serves it with `ad.html` on port 4192. |

## Making it again

1. Start the website copy on this computer, in Ubuntu:
   `bash /root/fathersProject/marketing/website-ad/make-ad.sh` (it keeps running).
   The window shows this computer's copy of the shop, so bring its products up to date first if they changed.
2. Start a headless browser (Windows):
   `msedge --headless=new --remote-debugging-port=9333 --user-data-dir=%TEMP%\ad-edge`.
3. Film it (about 7 minutes; the page runs 12 times slower, so the motion comes out smooth):
   `node --experimental-websocket record.mjs 9333 http://127.0.0.1:4192/ad/ad.html <frames folder>`.
4. Turn the frames into the video:
   `ffmpeg -f concat -safe 0 -i <frames folder>/list.txt -vf "fps=30,format=yuv420p" -c:v libx264 -profile:v high -crf 18 -preset slow -movflags +faststart advert.mp4`.

To change the words, edit the captions (`cap-...`) and the closing card in
`ad.html`. To change the timing, edit the `until(...)` seconds in `record.mjs`.
