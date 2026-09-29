#!/usr/bin/env python3
"""
Builds the Dewey demo video from demo/scenes.json.

    python demo/build_demo.py            # full video -> demo/out/dewey-demo.mp4
    python demo/build_demo.py --voice am_michael
    python demo/build_demo.py --only 03-sort

Every scene's narration is synthesised sentence by sentence with Kokoro (an
open-weights TTS model, run locally), which is also what makes the subtitles
exact: each sentence's start and end are known rather than guessed.

Screen recordings go in demo/footage/, named as scenes.json says (03-sort.mp4
and so on). A phone scene with no recording yet renders a placeholder that
says what to record, so the whole cut can be watched and timed before a single
clip exists. A recording is fitted to its narration: sped up if it runs long
(up to 2.5x), held on its last frame if it runs short.

Nothing here is committed that is not ours: the Kokoro weights, the fonts and
the rendered output are fetched or built into demo/.cache and demo/out.

Requires: pip install kokoro-onnx soundfile imageio-ffmpeg pillow numpy fonttools brotli
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import subprocess
import tarfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import imageio_ffmpeg
import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
CACHE = ROOT / ".cache"
OUT = ROOT / "out"
FOOTAGE = ROOT / "footage"
ASSETS = ROOT / "assets"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

W, H, FPS = 1920, 1080, 30
SAMPLE_RATE = 24000
SENTENCE_GAP = 0.32
LEAD_IN = 0.45
TAIL = 0.75
FADE = 0.28

NIGHT = (0x0F, 0x13, 0x20)
SURFACE = (0x1A, 0x20, 0x33)
BLUE = (0x25, 0x63, 0xEB)
ACCENT = (0x8A, 0x9D, 0xFF)
INK = (0xEE, 0xF1, 0xF8)
MUTED = (0xA2, 0xAB, 0xBE)
FAINT = (0x6C, 0x75, 0x8A)
GREEN = (0x4A, 0xDE, 0x80)
AMBER = (0xF5, 0xBE, 0x5B)

KOKORO_BASE = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/"
FONTSOURCE = {
    "inter": ("@fontsource/inter", "inter-latin-{w}-normal.woff2", [500, 600, 700, 800]),
    "mono": ("@fontsource/jetbrains-mono", "jetbrains-mono-latin-{w}-normal.woff2", [400, 700]),
}

# Screen rectangle of the phone in phone scenes: a 1080x2400 recording scaled to 880 tall.
PHONE_H = 860
PHONE_W = round(PHONE_H * 1080 / 2400)
PHONE_X = 560 - PHONE_W // 2
PHONE_Y = 58
PHONE_RADIUS = 34


# -- fetching -----------------------------------------------------------------

def fetch(url: str, dest: Path) -> Path:
    if not dest.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        print(f"  fetching {url}")
        with urllib.request.urlopen(url) as response:
            dest.write_bytes(response.read())
    return dest


def font_files() -> dict[str, Path]:
    """Inter and JetBrains Mono as TTFs, converted from Fontsource's npm packages."""
    from fontTools.ttLib import TTFont

    paths = {}
    for key, (package, pattern, weights) in FONTSOURCE.items():
        wanted = {w: CACHE / "fonts" / f"{key}-{w}.ttf" for w in weights}
        if not all(p.exists() for p in wanted.values()):
            meta = json.loads(urllib.request.urlopen(f"https://registry.npmjs.org/{package}/latest").read())
            tarball = urllib.request.urlopen(meta["dist"]["tarball"]).read()
            with tarfile.open(fileobj=io.BytesIO(tarball)) as tar:
                for w, dest in wanted.items():
                    member = tar.extractfile(f"package/files/{pattern.format(w=w)}")
                    font = TTFont(io.BytesIO(member.read()))
                    font.flavor = None
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    font.save(dest)
        paths.update({f"{key}-{w}": p for w, p in wanted.items()})
    return paths


FONTS: dict[str, Path] = {}


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS[name]), size)


# -- narration ----------------------------------------------------------------

class Narrator:
    def __init__(self, voice: str, speed: float):
        from kokoro_onnx import Kokoro

        model = fetch(KOKORO_BASE + "kokoro-v1.0.onnx", CACHE / "kokoro" / "kokoro-v1.0.onnx")
        voices = fetch(KOKORO_BASE + "voices-v1.0.bin", CACHE / "kokoro" / "voices-v1.0.bin")
        self.kokoro = Kokoro(str(model), str(voices))
        self.voice, self.speed = voice, speed

    def say(self, text: str) -> np.ndarray:
        key = hashlib.sha1(f"{self.voice}|{self.speed}|{text}".encode()).hexdigest()[:16]
        path = CACHE / "tts" / f"{key}.wav"
        if not path.exists():
            samples, rate = self.kokoro.create(text, voice=self.voice, speed=self.speed, lang="en-us")
            assert rate == SAMPLE_RATE, rate
            path.parent.mkdir(parents=True, exist_ok=True)
            sf.write(path, samples, rate)
        samples, _ = sf.read(path, dtype="float32")
        return samples


@dataclass
class Line:
    text: str
    start: float
    end: float


def narrate(narrator: Narrator, sentences: list[str], wav: Path) -> tuple[float, list[Line]]:
    """One scene's audio, with a lead-in, gaps between sentences and a tail; returns its length and timings."""
    silence = lambda seconds: np.zeros(int(seconds * SAMPLE_RATE), dtype=np.float32)  # noqa: E731
    parts, lines, t = [silence(LEAD_IN)], [], LEAD_IN
    for i, sentence in enumerate(sentences):
        audio = narrator.say(sentence)
        length = len(audio) / SAMPLE_RATE
        lines.append(Line(sentence, t, t + length))
        parts.append(audio)
        t += length
        if i < len(sentences) - 1:
            parts.append(silence(SENTENCE_GAP))
            t += SENTENCE_GAP
    parts.append(silence(TAIL))
    t += TAIL
    sf.write(wav, np.concatenate(parts), SAMPLE_RATE)
    return t, lines


# -- drawing ------------------------------------------------------------------

def backdrop() -> Image.Image:
    img = Image.new("RGBA", (W, H), NIGHT + (255,))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([W * 0.05, -H * 0.5, W * 0.7, H * 0.8], fill=BLUE + (70,))
    return Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(220)))


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, current = [], ""
    for word in text.split():
        trial = f"{current} {word}".strip()
        if draw.textlength(trial, font=fnt) <= width:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def subtitle(text: str) -> Image.Image:
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    fnt = font("inter-600", 34)
    lines = wrap(draw, text, fnt, 1500)
    line_h = 46
    box_h = line_h * len(lines) + 28
    box_w = max(draw.textlength(line, font=fnt) for line in lines) + 56
    top = H - 38 - box_h
    draw.rounded_rectangle([(W - box_w) / 2, top, (W + box_w) / 2, top + box_h], radius=18, fill=NIGHT + (205,))
    for i, line in enumerate(lines):
        draw.text((W / 2, top + 14 + line_h * i + line_h / 2), line, font=fnt, fill=INK + (255,), anchor="mm")
    return img


def rounded_icon(size: int) -> Image.Image:
    import sys

    sys.path.insert(0, str(REPO / "tools" / "brand"))
    sys.path.insert(0, str(REPO / "tools" / "icon"))
    from render_brand import rounded_icon as brand_icon  # noqa: E402

    return brand_icon(size)


def phone_plate(scene: dict, has_footage: bool) -> Image.Image:
    img = backdrop()
    draw = ImageDraw.Draw(img)
    # Bezel.
    bezel = 14
    draw.rounded_rectangle(
        [PHONE_X - bezel, PHONE_Y - bezel, PHONE_X + PHONE_W + bezel, PHONE_Y + PHONE_H + bezel],
        radius=PHONE_RADIUS + bezel, fill=(0x05, 0x07, 0x0D, 255), outline=(0x2A, 0x31, 0x48, 255), width=3,
    )
    draw.rounded_rectangle(
        [PHONE_X, PHONE_Y, PHONE_X + PHONE_W, PHONE_Y + PHONE_H], radius=PHONE_RADIUS, fill=SURFACE + (255,)
    )
    if not has_footage:
        f_head, f_body = font("inter-800", 30), font("inter-500", 22)
        draw.text((PHONE_X + PHONE_W / 2, PHONE_Y + 300), "RECORD", font=f_head, fill=AMBER + (255,), anchor="mm")
        draw.text((PHONE_X + PHONE_W / 2, PHONE_Y + 340), scene["footage"], font=f_body, fill=MUTED + (255,), anchor="mm")
        for i, line in enumerate(wrap(draw, scene["record"], f_body, PHONE_W - 60)):
            draw.text((PHONE_X + 30, PHONE_Y + 390 + i * 30), line, font=f_body, fill=INK + (255,))

    # Panel.
    x = 1010
    draw.text((x, 230), scene["kicker"].upper(), font=font("inter-700", 26), fill=ACCENT + (255,))
    y = 280
    for line in wrap(draw, scene["headline"], font("inter-800", 64), 800):
        draw.text((x, y), line, font=font("inter-800", 64), fill=INK + (255,))
        y += 78
    y += 30
    for bullet in scene["bullets"]:
        draw.ellipse([x, y + 14, x + 12, y + 26], fill=BLUE + (255,))
        draw.text((x + 32, y), bullet, font=font("inter-500", 34), fill=(0xD5, 0xDB, 0xEA, 255))
        y += 58
    return img


def downloads_plates() -> list[Image.Image]:
    """The hook: a Downloads folder of real test-corpus file names, then the one that is the bill."""
    names = [
        ("Scan_20240312_004.pdf", "412 KB", "12 Mar"), ("IMG_9338.pdf", "1.8 MB", "3 Mar"),
        ("Nouveau document 7.pdf", "96 KB", "27 Feb"), ("WhatsApp Doc 2023-10-22 at 07.05.19.pdf", "233 KB", "22 Oct"),
        ("file-825778.pdf", "88 KB", "9 Oct"), ("document (5).pdf", "61 KB", "8 Jan"),
        ("photo_7075@20220406.pdf", "2.1 MB", "6 Apr"), ("document (12).pdf", "74 KB", "2 Apr"),
        ("Scan_20231105_019.pdf", "388 KB", "5 Nov"), ("IMG_2891.pdf", "1.2 MB", "30 Sep"),
    ]
    plates = []
    for highlight in (False, True):
        img = backdrop()
        draw = ImageDraw.Draw(img)
        left, top, width = 260, 110, 900
        draw.rounded_rectangle([left, top, left + width, top + 860], radius=28, fill=SURFACE + (255,))
        draw.text((left + 44, top + 40), "Download", font=font("inter-800", 48), fill=INK + (255,))
        draw.text((left + 44, top + 104), "143 files", font=font("inter-500", 26), fill=MUTED + (255,))
        for i, (name, size, date) in enumerate(names):
            y = top + 170 + i * 66
            hit = highlight and name == "document (5).pdf"
            if hit:
                draw.rounded_rectangle([left + 20, y - 8, left + width - 20, y + 56], radius=14, fill=BLUE + (80,))
            draw.rounded_rectangle([left + 44, y, left + 84, y + 48], radius=8, fill=(0xE5, 0x48, 0x4D, 255))
            draw.text((left + 64, y + 24), "PDF", font=font("inter-800", 13), fill=(255, 255, 255, 255), anchor="mm")
            draw.text((left + 108, y + 4), name, font=font("inter-600", 28), fill=INK + (255,))
            draw.text((left + 108, y + 36), f"{size} · {date}", font=font("inter-500", 18), fill=FAINT + (255,))
        if highlight:
            page_path = ASSETS / "lydec-page.png"
            if page_path.exists():
                page = Image.open(page_path).convert("RGBA")
                page.thumbnail((540, 720))
                shadowed = Image.new("RGBA", (page.width + 80, page.height + 80), (0, 0, 0, 0))
                sh = Image.new("RGBA", page.size, (0, 0, 0, 160))
                shadowed.paste(sh, (40, 52))
                shadowed = shadowed.filter(ImageFilter.GaussianBlur(20))
                shadowed.alpha_composite(page, (40, 40))
                img.alpha_composite(shadowed, (1240, 150))
                draw = ImageDraw.Draw(img)
                draw.text((1280 + page.width / 2, 130), "Lydec · electricity · January · 281.26 MAD",
                          font=font("inter-600", 26), fill=ACCENT + (255,), anchor="mm")
        plates.append(img)
    return plates


def title_plate(subtitle_text: str | None = None) -> Image.Image:
    import sys

    sys.path.insert(0, str(REPO / "tools" / "brand"))
    sys.path.insert(0, str(REPO / "tools" / "icon"))
    from render_brand import banner

    fonts_dir = CACHE / "fonts" / "inter-alias"
    fonts_dir.mkdir(parents=True, exist_ok=True)
    for w in (500, 800):
        alias = fonts_dir / f"Inter-{w}.ttf"
        if not alias.exists():
            alias.write_bytes(FONTS[f"inter-{w}"].read_bytes())
    return banner(fonts_dir, W, H, subtitle_text)


def numbers_plate() -> Image.Image:
    img = backdrop()
    draw = ImageDraw.Draw(img)
    draw.text((W / 2, 170), "ON THE PHONE, MEASURED", font=font("inter-700", 28), fill=ACCENT + (255,), anchor="mm")
    tiles = [
        ("97 / 100", "documents filed", INK),
        ("0", "filed in the wrong place", GREEN),
        ("0 bytes", "sent anywhere to sort", GREEN),
    ]
    tile_w, gap = 480, 50
    x0 = (W - (tile_w * 3 + gap * 2)) / 2
    for i, (big, small, colour) in enumerate(tiles):
        x = x0 + i * (tile_w + gap)
        draw.rounded_rectangle([x, 300, x + tile_w, 640], radius=32, fill=SURFACE + (255,))
        draw.text((x + tile_w / 2, 440), big, font=font("inter-800", 96), fill=colour + (255,), anchor="mm")
        draw.text((x + tile_w / 2, 550), small, font=font("inter-500", 32), fill=MUTED + (255,), anchor="mm")
    draw.text((W / 2, 740), "3 held for review instead of guessed  ·  13 categories  ·  French, Arabic, English",
              font=font("inter-500", 30), fill=FAINT + (255,), anchor="mm")
    return img


CODE = """\
// Entitlements.kt: configured before anything asks what is unlocked
Purchases.configure(
    PurchasesConfiguration.Builder(context, REVENUECAT_KEY)
        .apply { if (appUserId != null) appUserID(appUserId) }.build())
Purchases.sharedInstance.updatedCustomerInfoListener =
    UpdatedCustomerInfoListener { info -> state.value = info.hasLibrarian() }

// RevenueCatLibrarianBilling.kt: the paywall, from the live offering
val offering = Purchases.sharedInstance.awaitOfferings().current
Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
val info = Purchases.sharedInstance.awaitRestore()

// Account.kt: the purchase follows the person, not the phone
Purchases.sharedInstance.awaitLogIn(user.uid)

// MeScreen.kt: RevenueCat's own Customer Center
CustomerCenter(options = CustomerCenterOptions.Builder().build(), onDismiss = …)"""

SDK_WORDS = re.compile(
    r"\b(Purchases|PurchasesConfiguration|UpdatedCustomerInfoListener|awaitOfferings|awaitPurchase|"
    r"PurchaseParams|awaitRestore|awaitLogIn|appUserID|CustomerCenter|CustomerCenterOptions|configure)\b"
)


def code_plate() -> Image.Image:
    img = backdrop()
    draw = ImageDraw.Draw(img)
    draw.text((140, 90), "REVENUECAT SDK, IN THE APP", font=font("inter-700", 28), fill=ACCENT + (255,))
    draw.rounded_rectangle([120, 150, W - 120, 850], radius=28, fill=(0x0A, 0x0D, 0x17, 255),
                           outline=(0x2A, 0x31, 0x48, 255), width=2)
    mono, mono_bold = font("mono-400", 26), font("mono-700", 26)
    y = 180
    for line in CODE.splitlines():
        x = 170
        if line.strip().startswith("//"):
            draw.text((x, y), line, font=mono, fill=FAINT + (255,))
        else:
            pos = 0
            for match in SDK_WORDS.finditer(line):
                before = line[pos:match.start()]
                draw.text((x, y), before, font=mono, fill=(0xC9, 0xD1, 0xE3, 255))
                x += draw.textlength(before, font=mono)
                draw.text((x, y), match.group(0), font=mono_bold, fill=ACCENT + (255,))
                x += draw.textlength(match.group(0), font=mono_bold)
                pos = match.end()
            draw.text((x, y), line[pos:], font=mono, fill=(0xC9, 0xD1, 0xE3, 255))
        y += 39
    return img


def close_plate() -> Image.Image:
    img = title_plate("Finds the file you can't name.")
    draw = ImageDraw.Draw(img)
    draw.text((W / 2, H - 150), "github.com/Yunsmn/dewey", font=font("inter-600", 34), fill=INK + (255,), anchor="mm")
    draw.text((W / 2, H - 100), "Built for RevenueCat Shipaton 2026", font=font("inter-500", 26),
              fill=MUTED + (255,), anchor="mm")
    return img


# -- composing ----------------------------------------------------------------

def run(args: list[str]) -> str:
    result = subprocess.run([FFMPEG, "-hide_banner", "-y", *args], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[-3000:])
    return result.stderr


def probe(path: Path) -> tuple[float, int, int]:
    info = subprocess.run([FFMPEG, "-hide_banner", "-i", str(path)], capture_output=True, text=True).stderr
    h, m, s = re.search(r"Duration: (\d+):(\d+):([\d.]+)", info).groups()
    w, hgt = map(int, re.search(r"Video: .*?(\d{2,5})x(\d{2,5})", info).groups())
    return int(h) * 3600 + int(m) * 60 + float(s), w, hgt


def compose(scene: dict, duration: float, lines: list[Line], wav: Path, work: Path) -> Path:
    kind = scene["kind"]
    footage = FOOTAGE / scene["footage"] if scene.get("footage") else None
    has_footage = bool(footage and footage.exists())

    # Plates: (image, start seconds). Later plates cover earlier ones from their start.
    if kind == "phone":
        plates = [(phone_plate(scene, has_footage), 0.0)]
    elif kind == "downloads":
        first, second = downloads_plates()
        plates = [(first, 0.0), (second, lines[-1].start - 0.2)]
    elif kind == "title":
        plates = [(title_plate(), 0.0)]
    elif kind == "numbers":
        plates = [(numbers_plate(), 0.0)]
    elif kind == "code":
        plates = [(code_plate(), 0.0)]
    elif kind == "close":
        plates = [(close_plate(), 0.0)]
    else:
        raise ValueError(kind)

    inputs, filters = [], []
    for i, (plate, _) in enumerate(plates):
        path = work / f"{scene['id']}-plate{i}.png"
        plate.convert("RGB").save(path)
        inputs += ["-loop", "1", "-t", f"{duration:.3f}", "-framerate", str(FPS), "-i", str(path)]
    stream = "[0:v]"
    for i in range(1, len(plates)):
        filters.append(f"{stream}[{i}:v]overlay=enable='gte(t,{plates[i][1]:.3f})'[p{i}]")
        stream = f"[p{i}]"
    next_input = len(plates)

    if has_footage:
        clip_len, cw, ch = probe(footage)
        scale = min(PHONE_W / cw, PHONE_H / ch)
        sw, sh = int(cw * scale) // 2 * 2, int(ch * scale) // 2 * 2
        speed = min(max(clip_len / (duration - 0.2), 1.0), 2.5)
        mask = Image.new("L", (sw, sh), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], radius=PHONE_RADIUS, fill=255)
        mask_path = work / f"{scene['id']}-mask.png"
        mask.save(mask_path)
        inputs += ["-i", str(footage), "-loop", "1", "-framerate", str(FPS), "-t", f"{duration:.3f}", "-i", str(mask_path)]
        fi, mi = next_input, next_input + 1
        next_input += 2
        ox, oy = PHONE_X + (PHONE_W - sw) // 2, PHONE_Y + (PHONE_H - sh) // 2
        filters.append(
            f"[{fi}:v]setpts=PTS/{speed:.4f},fps={FPS},scale={sw}:{sh},format=rgba,"
            f"tpad=stop_mode=clone:stop_duration={duration:.3f}[clip]"
        )
        filters.append(f"[{mi}:v]format=gray[mask]")
        filters.append("[clip][mask]alphamerge[rounded]")
        filters.append(f"{stream}[rounded]overlay={ox}:{oy}:shortest=0[withclip]")
        stream = "[withclip]"

    for k, line in enumerate(lines):
        path = work / f"{scene['id']}-sub{k}.png"
        subtitle(line.text).save(path)
        inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{duration:.3f}", "-i", str(path)]
        end = lines[k + 1].start if k + 1 < len(lines) else duration - 0.15
        filters.append(f"{stream}[{next_input}:v]overlay=enable='between(t,{line.start - 0.05:.3f},{end:.3f})'[s{k}]")
        stream = f"[s{k}]"
        next_input += 1

    filters.append(
        f"{stream}fade=t=in:st=0:d={FADE},fade=t=out:st={duration - FADE:.3f}:d={FADE},format=yuv420p[v]"
    )
    inputs += ["-i", str(wav)]
    out = work / f"{scene['id']}.mp4"
    run([
        *inputs,
        "-filter_complex", ";".join(filters),
        "-map", "[v]", "-map", f"{next_input}:a",
        "-t", f"{duration:.3f}", "-r", str(FPS),
        "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
        str(out),
    ])
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--voice")
    parser.add_argument("--speed", type=float)
    parser.add_argument("--only", help="build a single scene id, for checking one clip")
    args = parser.parse_args()

    spec = json.loads((ROOT / "scenes.json").read_text())
    voice = args.voice or spec["voice"]
    speed = args.speed or spec["speed"]
    OUT.mkdir(parents=True, exist_ok=True)
    work = CACHE / "work"
    work.mkdir(parents=True, exist_ok=True)

    FONTS.update(font_files())
    narrator = Narrator(voice, speed)

    segments, srt, clock = [], [], 0.0
    for scene in spec["scenes"]:
        if args.only and scene["id"] != args.only:
            continue
        wav = work / f"{scene['id']}.wav"
        duration, lines = narrate(narrator, scene["sentences"], wav)
        has = scene.get("footage") and (FOOTAGE / scene["footage"]).exists()
        print(f"{scene['id']:14} {duration:5.1f}s  {'footage' if has else ('PLACEHOLDER' if scene['kind'] == 'phone' else scene['kind'])}")
        segments.append(compose(scene, duration, lines, wav, work))
        srt += [Line(l.text, clock + l.start, clock + l.end) for l in lines]
        clock += duration

    listing = work / "segments.txt"
    listing.write_text("".join(f"file '{s}'\n" for s in segments))
    name = f"dewey-demo-{args.only}.mp4" if args.only else "dewey-demo.mp4"
    joined = work / "joined.mp4"
    run(["-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", str(joined)])
    run(["-i", str(joined), "-c:v", "copy", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "aac", "-b:a", "192k",
         "-ar", "48000", "-movflags", "+faststart", str(OUT / name)])

    def stamp(t: float) -> str:
        ms = int(round(t * 1000))
        return f"{ms // 3600000:02}:{ms // 60000 % 60:02}:{ms // 1000 % 60:02},{ms % 1000:03}"

    (OUT / name.replace(".mp4", ".srt")).write_text(
        "".join(f"{i}\n{stamp(l.start)} --> {stamp(l.end)}\n{l.text}\n\n" for i, l in enumerate(srt, 1))
    )
    print(f"\n{OUT / name}  {clock:.1f}s ({int(clock // 60)}:{int(clock % 60):02})")


if __name__ == "__main__":
    main()
