"""Turns the About page's animation frames into the light web set it plays.

    python3 scripts/build_story_frames.py ["/root/fathersProject/dateroem animations/frames"]

The source is the full export (frame-0001.jpg ... from the stitched video of
the five stages). Straight from the export that's ~600 frames and ~170 MB -
far too much for a phone - so this keeps every STEP-th frame (the page
blends neighbouring frames while scrolling, so it still plays smoothly) and
writes into public/story/:

    lg/0001.avif ...   1152 wide, for laptops and tablets
    sm/0001.avif ...   720 wide, for phones
    fb/0001.webp ...   720 wide WebP, only for browsers without AVIF
    manifest.json      frame count and where each stage starts/ends

AVIF because these frames are full of fine detail (leaves, sawdust) that
WebP/JPEG spend a lot of bytes on - AVIF is about half the size for a
picture that looks the same.

The stages are found from the hard cuts between the five clips, so a new
export (e.g. one without the watermark) only needs this script re-run.
"""
import json
import os
import shutil
import sys

from PIL import Image, ImageChops, ImageStat

SOURCE = sys.argv[1] if len(sys.argv) > 1 else '/root/fathersProject/dateroem animations/frames'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'public', 'story')
STAGES = 5
STEP = 2
# name: (width, format, quality)
SETS = {
    'lg': (1152, 'AVIF', 40),
    'sm': (720, 'AVIF', 40),
    'fb': (720, 'WEBP', 58),
}
EXTENSIONS = {'AVIF': 'avif', 'WEBP': 'webp'}
CUT_THRESHOLD = 30  # mean pixel difference between neighbours that marks a cut


def main():
    names = sorted(n for n in os.listdir(SOURCE) if n.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')))
    if not names:
        sys.exit(f'No frames in {SOURCE}')

    # Where the clips join: the biggest jumps between neighbouring frames.
    jumps = []
    prev = None
    for i, name in enumerate(names):
        small = Image.open(os.path.join(SOURCE, name)).convert('L').resize((64, 36))
        if prev is not None:
            jumps.append((ImageStat.Stat(ImageChops.difference(small, prev)).mean[0], i))
        prev = small
    cuts = sorted(i for diff, i in sorted(jumps, reverse=True)[: STAGES - 1] if diff >= CUT_THRESHOLD)
    if len(cuts) != STAGES - 1:
        # No clear cuts (e.g. a cross-faded export) - split evenly instead.
        cuts = [round(len(names) * k / STAGES) for k in range(1, STAGES)]
    bounds = list(zip([0] + cuts, cuts + [len(names)]))

    # Every STEP-th frame inside each stage, always including a stage's first
    # and last frame so a stage never shows a frame of its neighbour.
    picked = []
    stages = []
    for start, end in bounds:
        indexes = list(range(start, end, STEP))
        if indexes[-1] != end - 1:
            indexes.append(end - 1)
        stages.append({'start': len(picked), 'end': len(picked) + len(indexes) - 1})
        picked.extend(indexes)

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    totals = {}
    for key, (width, fmt, quality) in SETS.items():
        os.makedirs(os.path.join(OUT, key))
        total = 0
        for n, index in enumerate(picked, start=1):
            image = Image.open(os.path.join(SOURCE, names[index])).convert('RGB')
            height = round(image.height * width / image.width)
            path = os.path.join(OUT, key, f'{n:04d}.{EXTENSIONS[fmt]}')
            options = {'speed': 6} if fmt == 'AVIF' else {'method': 6}
            image.resize((width, height), Image.LANCZOS).save(path, fmt, quality=quality, **options)
            total += os.path.getsize(path)
        totals[key] = total

    first = Image.open(os.path.join(SOURCE, names[0]))
    manifest = {
        'count': len(picked),
        'aspect': round(first.width / first.height, 4),
        'sets': {key: {'width': width, 'ext': EXTENSIONS[fmt]} for key, (width, fmt, _) in SETS.items()},
        'stages': stages,
    }
    with open(os.path.join(OUT, 'manifest.json'), 'w') as handle:
        json.dump(manifest, handle, indent=2)

    print(f'{len(names)} source frames, cuts at {[c + 1 for c in cuts]}')
    print(f'{len(picked)} frames kept, stages {stages}')
    for key, total in totals.items():
        print(f'{key}: {total / 1e6:.1f} MB ({total / len(picked) / 1e3:.0f} KB a frame)')


if __name__ == '__main__':
    main()
