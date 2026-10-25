"""The website's forest pictures (25 October 2026), made from the board's own
misty-forest photo and the entrance fog - the same forest as the phone and
desktop entrances.

    python prepare_website_forest.py

writes frontend/website/public/forest/:
  forest-wide.webp      1920 x 1080  the home page's entrance on wide screens
                                     (the tree line under the mist)
  forest-tall.webp       900 x 1350  the same on phones (the photo's own frame)
  forest-wide-soft.webp  640 x 360   the entrance out of focus, for its arrival
  forest-tall-soft.webp  300 x 450   (the forest comes into focus at dawn)
  forest-ambient.webp   1280 x 720   soft and blurred, behind every page
  fog.webp              1024 x 256   the drifting mist (with transparency), the
                                     fog and its mirror side by side so it
                                     repeats without a seam as it drifts
Camera data is never copied.
"""
import os

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "frontend", "website", "public", "forest")
FOG = os.path.join(ROOT, "admin_server_control", "desktop", "app", "ui", "assets", "entrance", "entrance_fog.png")


def clean(img: Image.Image) -> Image.Image:
    """Pixels only - no EXIF, GPS or camera data."""
    out = Image.new(img.mode, img.size)
    out.putdata(list(img.getdata()))
    return out


def save(img: Image.Image, name: str, quality: int):
    path = os.path.join(OUT, name)
    clean(img).save(path, "WEBP", quality=quality, method=6)
    print(f"{name}: {img.size[0]} x {img.size[1]}, {os.path.getsize(path) // 1024} KB")


def main():
    os.makedirs(OUT, exist_ok=True)
    forest = Image.open(os.path.join(HERE, "misty forest (original).jpg")).convert("RGB")
    w, h = forest.size
    # Wide: a 16:9 band from the mist down into the tree line.
    band_h = int(w * 9 / 16)
    top = int(h * 0.40)
    wide = forest.crop((0, top, w, top + band_h)).resize((1920, 1080), Image.LANCZOS)
    save(wide, "forest-wide.webp", 78)
    # Tall: the photo's own frame, for phones.
    save(forest.resize((900, 1350), Image.LANCZOS), "forest-tall.webp", 76)
    # Out of focus, for the entrance's arrival (it sharpens into the above).
    save(wide.resize((640, 360), Image.LANCZOS).filter(ImageFilter.GaussianBlur(9)), "forest-wide-soft.webp", 62)
    save(forest.resize((300, 450), Image.LANCZOS).filter(ImageFilter.GaussianBlur(7)), "forest-tall-soft.webp", 62)
    # Ambient: the tree line, very soft, for behind every page.
    ambient = forest.crop((0, int(h * 0.45), w, int(h * 0.45) + band_h)).resize((1280, 720), Image.LANCZOS)
    save(ambient.filter(ImageFilter.GaussianBlur(14)), "forest-ambient.webp", 70)
    # Mist, keeping its transparency.
    fog = Image.open(FOG).convert("RGBA").resize((512, 256), Image.LANCZOS)
    tile = Image.new("RGBA", (1024, 256))
    tile.paste(fog, (0, 0))
    tile.paste(fog.transpose(Image.FLIP_LEFT_RIGHT), (512, 0))
    save(tile, "fog.webp", 50)


if __name__ == "__main__":
    main()
