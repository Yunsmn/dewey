"""Writes the demo's music bed: a slow pad, a soft pulse, and a lift for the payoff.

Synthesised rather than downloaded, because every royalty-free track worth using
carries an attribution string that then has to live in the video description, and
because a bed written to the cut can breathe with it: it sits still under the
problem, opens up when the sort lands, and gets out of the way of the voice.

Usage: music.py OUT.wav SECONDS [LIFT_AT]
"""
from __future__ import annotations

import sys

import numpy as np
import soundfile as sf

RATE = 24000
# A minor, the plainest sad-to-hopeful four chords there are. Frequencies in Hz.
CHORDS = [
    (220.00, 261.63, 329.63),  # Am
    (174.61, 220.00, 261.63),  # F
    (261.63, 329.63, 392.00),  # C
    (196.00, 246.94, 293.66),  # G
]
BAR = 3.6  # seconds per chord, slow enough that nobody taps along


def adsr(length: int, attack: float, release: float) -> np.ndarray:
    """A gentle envelope, so no note starts or ends with a click."""
    envelope = np.ones(length)
    a, r = int(attack * RATE), int(release * RATE)
    envelope[:a] = np.linspace(0.0, 1.0, a) ** 2
    envelope[-r:] = np.linspace(1.0, 0.0, r) ** 2
    return envelope


def pad(frequencies: tuple[float, ...], seconds: float, brightness: float) -> np.ndarray:
    """One chord: a few detuned sines per note, which is what makes it sound like an instrument."""
    t = np.linspace(0.0, seconds, int(seconds * RATE), endpoint=False)
    voice = np.zeros_like(t)
    for f in frequencies:
        for detune, weight in ((0.0, 1.0), (0.6, 0.5), (-0.6, 0.5)):
            voice += weight * np.sin(2 * np.pi * (f + detune) * t)
        # A touch of the octave above, faded in by brightness, opens the chord up.
        voice += brightness * 0.35 * np.sin(2 * np.pi * f * 2 * t)
    voice /= len(frequencies) * 2.4
    return voice * adsr(len(t), 0.9, 1.2)


def pulse(at: float, total: float) -> np.ndarray:
    """A soft low thump, felt more than heard, on the beat that carries the payoff."""
    track = np.zeros(int(total * RATE))
    length = int(0.5 * RATE)
    t = np.linspace(0.0, 0.5, length, endpoint=False)
    hit = np.sin(2 * np.pi * np.linspace(70, 42, length) * t) * np.exp(-t * 7.5)
    start = int(at * RATE)
    for i in range(6):
        offset = start + int(i * BAR / 2 * RATE)
        if offset + length < len(track):
            track[offset:offset + length] += hit * (0.55 - i * 0.07)
    return track


def arpeggio(frequencies: tuple[float, ...], seconds: float, level: float) -> np.ndarray:
    """A light plucked figure over the chord: what makes the bed feel casual rather than ambient."""
    track = np.zeros(int(seconds * RATE))
    step = 0.45
    notes = [frequencies[0] * 2, frequencies[1] * 2, frequencies[2] * 2, frequencies[1] * 2]
    length = int(step * 1.6 * RATE)
    t = np.linspace(0.0, step * 1.6, length, endpoint=False)
    for i in range(int(seconds / step)):
        f = notes[i % len(notes)]
        pluck = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2 * t)) * np.exp(-t * 4.5)
        start = int(i * step * RATE)
        end = min(start + length, len(track))
        track[start:end] += pluck[:end - start] * level
    return track


def main() -> None:
    out, seconds = sys.argv[1], float(sys.argv[2])
    lift_at = float(sys.argv[3]) if len(sys.argv) > 3 else seconds * 0.25

    track = np.zeros(int(seconds * RATE) + RATE)
    position = 0.0
    index = 0
    while position < seconds:
        # Brightness climbs once the app is on screen, so the bed lifts with the story.
        brightness = 0.0 if position < lift_at else min(1.0, (position - lift_at) / 20.0)
        chord = pad(CHORDS[index % len(CHORDS)], BAR + 1.2, brightness)
        start = int(position * RATE)
        # Each chord rings on past its bar, so the last one runs off the end.
        end = min(start + len(chord), len(track))
        track[start:end] += chord[:end - start]
        # The pluck comes in with the app and stays, so the bed has a pulse to it.
        if position >= lift_at - BAR:
            figure = arpeggio(CHORDS[index % len(CHORDS)], BAR, 0.5 * min(1.0, brightness + 0.45))
            end = min(start + len(figure), len(track))
            track[start:end] += figure[:end - start]
        position += BAR
        index += 1

    track = track[:int(seconds * RATE)]
    track += pulse(lift_at, seconds)

    # Fade the whole bed in and out, and leave headroom: this sits under a voice.
    fade = int(2.5 * RATE)
    track[:fade] *= np.linspace(0.0, 1.0, fade)
    track[-fade:] *= np.linspace(1.0, 0.0, fade)
    track /= max(np.abs(track).max(), 1e-6)
    sf.write(out, (track * 0.85).astype(np.float32), RATE)
    print(f"{out}  {seconds:.1f}s  lift at {lift_at:.1f}s")


if __name__ == "__main__":
    main()
