"""
Renders the launcher icon to the flat PNG a submission form asks for.

The icon itself lives in res/ as a vector, which is what the app ships and the
only version that should ever be edited. This exists because competition and
store forms want a 1024x1024 PNG and will not take a vector — so it re-draws
the same three rectangles from the same coordinates rather than anyone
exporting a screenshot and hoping it matches.

The shapes are copied from res/drawable/ic_launcher_foreground.xml and the
colour from res/values/colors.xml. If either changes, change it there first and
re-run this; the vector is the source of truth, not the PNG.
"""

from pathlib import Path
from PIL import Image, ImageDraw

# The adaptive-icon viewport. Android reserves the outer ring for masking and
# only the middle ~72dp is guaranteed visible, but a flat submission icon has no
# mask, so it is drawn edge to edge at full bleed.
VIEWPORT = 108
SIZE = 1024
SCALE = SIZE / VIEWPORT

BACKGROUND = (0x25, 0x63, 0xEB)

# x, y, width, height, alpha — three spines on a shelf, receding.
SPINES = [
    (38, 32, 10, 44, 255),
    (52, 36, 8, 40, 191),
    (64, 40, 6, 36, 128),
]


def render(size: int = SIZE) -> Image.Image:
    scale = size / VIEWPORT
    # Drawn on RGBA so the partly transparent spines composite against the
    # background rather than being flattened to a lighter blue by hand.
    canvas = Image.new("RGBA", (size, size), BACKGROUND + (255,))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    for x, y, width, height, alpha in SPINES:
        draw.rectangle(
            [x * scale, y * scale, (x + width) * scale, (y + height) * scale],
            fill=(255, 255, 255, alpha),
        )

    return Image.alpha_composite(canvas, layer).convert("RGB")


if __name__ == "__main__":
    out = Path(__file__).resolve().parents[2] / "docs" / "press"
    out.mkdir(parents=True, exist_ok=True)

    for size in (1024, 512):
        path = out / f"icon-{size}.png"
        render(size).save(path)
        print(f"{path.relative_to(Path.cwd())}  {size}x{size}")
