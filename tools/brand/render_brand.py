"""
Renders the README logo and banner from the launcher icon's own shapes.

Same source of truth as tools/icon/render_icon.py: the three spines and the
blue are the ones in res/drawable/ic_launcher_foreground.xml. The wordmark is
set in Inter, which is not committed; pass its folder with --fonts (a folder
holding Inter-400.ttf ... Inter-800.ttf).

    python tools/brand/render_brand.py --fonts /path/to/inter
"""

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "icon"))
from render_icon import BACKGROUND, SPINES, VIEWPORT  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "docs" / "press"
INK = (0x1B, 0x20, 0x30)
NIGHT = (0x0F, 0x13, 0x20)
DEEP = (0x1A, 0x3F, 0xB8)


def rounded_icon(size: int, radius_fraction: float = 0.23) -> Image.Image:
    """The launcher icon as it looks on a home screen: the squircle-ish mask applied, transparent outside."""
    scale = size / VIEWPORT
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_fraction), fill=255)

    # A faint vertical gradient so it reads as an object rather than a flat swatch.
    fill = Image.new("RGBA", (size, size))
    top, bottom = (0x3B, 0x76, 0xF6), DEEP
    for y in range(size):
        t = y / (size - 1)
        colour = tuple(int(top[i] + (bottom[i] - top[i]) * t * 0.55) for i in range(3))
        ImageDraw.Draw(fill).line([(0, y), (size, y)], fill=colour + (255,))
    icon.paste(fill, (0, 0), mask)

    spines = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(spines)
    for x, y, width, height, alpha in SPINES:
        draw.rounded_rectangle(
            [x * scale, y * scale, (x + width) * scale, (y + height) * scale],
            radius=max(1, int(scale * 1.2)),
            fill=(255, 255, 255, alpha),
        )
    return Image.alpha_composite(icon, spines)


def with_shadow(image: Image.Image, blur: int, offset: int, opacity: int = 90) -> Image.Image:
    pad = blur * 3
    canvas = Image.new("RGBA", (image.width + pad * 2, image.height + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = image.split()[3].point(lambda a: a * opacity // 255)
    shadow.paste((10, 20, 60, 255), (pad, pad + offset), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas = Image.alpha_composite(canvas, shadow)
    canvas.alpha_composite(image, (pad, pad))
    return canvas


def logo(fonts: Path) -> Image.Image:
    """Icon plus wordmark, on transparent — for the top of the README on light or dark GitHub."""
    icon = rounded_icon(220)
    word = ImageFont.truetype(str(fonts / "Inter-800.ttf"), 150)
    text_w = int(ImageDraw.Draw(Image.new("L", (1, 1))).textlength("Dewey", font=word))
    canvas = Image.new("RGBA", (220 + 48 + text_w + 20, 240), (0, 0, 0, 0))
    canvas.alpha_composite(icon, (0, 10))
    # Blue wordmark: legible on both GitHub's white and its dark background.
    ImageDraw.Draw(canvas).text((220 + 48, 120), "Dewey", font=word, fill=BACKGROUND + (255,), anchor="lm")
    return canvas


def banner(fonts: Path, width: int = 1280, height: int = 640, subtitle: str | None = None) -> Image.Image:
    """The social preview and video title card: dark ground, a soft blue glow, icon, name, line."""
    canvas = Image.new("RGBA", (width, height), NIGHT + (255,))
    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse(
        [width * 0.18, -height * 0.35, width * 0.82, height * 0.75], fill=BACKGROUND + (120,)
    )
    canvas = Image.alpha_composite(canvas, glow.filter(ImageFilter.GaussianBlur(height // 5)))

    icon = with_shadow(rounded_icon(int(height * 0.3)), blur=18, offset=10)
    canvas.alpha_composite(icon, ((width - icon.width) // 2, int(height * 0.12)))

    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype(str(fonts / "Inter-800.ttf"), int(height * 0.14))
    line = ImageFont.truetype(str(fonts / "Inter-500.ttf"), int(height * 0.045))
    draw.text((width / 2, height * 0.66), "Dewey", font=title, fill=(255, 255, 255, 255), anchor="mm")
    draw.text(
        (width / 2, height * 0.78),
        subtitle or "Finds the file you can't name.",
        font=line,
        fill=(0xC9, 0xD3, 0xEA, 255),
        anchor="mm",
    )
    return canvas


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--fonts", type=Path, required=True)
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    logo(args.fonts).save(OUT / "logo.png")
    banner(args.fonts).convert("RGB").save(OUT / "banner.png")
    print("wrote", OUT / "logo.png", OUT / "banner.png")
