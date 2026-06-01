"""
Generate Android launcher icons + overlay trigger from Logo-Hoso-fnl.png.

Steps:
1. Load source PNG (white background).
2. Convert white-ish pixels to transparent.
3. Crop to triangle bounding box (with small padding).
4. Generate adaptive icon foregrounds (5 densities) — triangle centered
   inside a 108dp canvas, sized to ~55% of width to stay in safe zone.
5. Generate overlay trigger PNGs (5 densities) — triangle filling ~85%
   of a 24dp canvas (the ImageView is 48dp with 12dp padding).
"""
from pathlib import Path
from PIL import Image


SRC = Path(r"D:/30-Dev-Projects/Hoso/Logo-Hoso-fnl.png")
RES = Path(r"D:/30-Dev-Projects/Hoso/app/src/main/res")

# Foreground adaptive icon: full canvas = 108dp, safe zone = 66dp (centered).
# Foreground sizes per density (108dp -> px):
LAUNCHER_FG_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}
# Triangle takes ~55% of canvas width => stays well inside the 66dp safe zone.
LAUNCHER_TRIANGLE_RATIO = 0.55

# Overlay trigger: ImageView is 48dp with 12dp padding -> effective 24dp.
# We render at 24dp content size per density, triangle fills 90% (no padding
# applied here since ImageView already pads the bitmap).
TRIGGER_SIZES = {
    "mdpi": 24,
    "hdpi": 36,
    "xhdpi": 48,
    "xxhdpi": 72,
    "xxxhdpi": 96,
}
TRIGGER_TRIANGLE_RATIO = 0.95


def load_and_transparentize(path: Path, white_threshold: int = 240) -> Image.Image:
    """Load PNG and turn near-white pixels transparent."""
    im = Image.open(path).convert("RGBA")
    pixels = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if r >= white_threshold and g >= white_threshold and b >= white_threshold:
                pixels[x, y] = (r, g, b, 0)
            else:
                # Smooth alpha based on how close to white the pixel is
                # (prevents harsh edge halos).
                brightness = (r + g + b) / 3
                if brightness > 200:
                    # Fade alpha proportionally between 200 and white_threshold
                    fade = (white_threshold - brightness) / (white_threshold - 200)
                    pixels[x, y] = (r, g, b, max(0, min(255, int(255 * fade))))
    return im


def crop_to_content(im: Image.Image, padding: int = 8) -> Image.Image:
    """Crop to the non-transparent bounding box, with a small padding."""
    bbox = im.getbbox()
    if bbox is None:
        return im
    left, top, right, bottom = bbox
    left = max(0, left - padding)
    top = max(0, top - padding)
    right = min(im.size[0], right + padding)
    bottom = min(im.size[1], bottom + padding)
    return im.crop((left, top, right, bottom))


def render_centered(
    content: Image.Image,
    canvas_size: int,
    fill_ratio: float,
) -> Image.Image:
    """Render `content` centered on a transparent square canvas of `canvas_size`,
    scaled so its widest dimension matches `canvas_size * fill_ratio`.
    """
    cw, ch = content.size
    target = int(canvas_size * fill_ratio)
    scale = target / max(cw, ch)
    new_w = max(1, int(cw * scale))
    new_h = max(1, int(ch * scale))
    scaled = content.resize((new_w, new_h), Image.LANCZOS)

    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    x = (canvas_size - new_w) // 2
    y = (canvas_size - new_h) // 2
    canvas.paste(scaled, (x, y), scaled)
    return canvas


def main() -> None:
    print(f"[gen] loading {SRC}")
    raw = load_and_transparentize(SRC)
    print(f"[gen] source size: {raw.size}")

    content = crop_to_content(raw, padding=12)
    print(f"[gen] cropped to content: {content.size}")

    # Launcher foreground (5 densities)
    for density, size in LAUNCHER_FG_SIZES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / "ic_launcher_foreground.png"
        img = render_centered(content, size, LAUNCHER_TRIANGLE_RATIO)
        img.save(out_path, "PNG", optimize=True)
        print(f"[gen] wrote {out_path} ({size}x{size})")

    # Overlay trigger (5 densities)
    for density, size in TRIGGER_SIZES.items():
        out_dir = RES / f"drawable-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / "ic_overlay_trigger_logo.png"
        img = render_centered(content, size, TRIGGER_TRIANGLE_RATIO)
        img.save(out_path, "PNG", optimize=True)
        print(f"[gen] wrote {out_path} ({size}x{size})")


if __name__ == "__main__":
    main()
