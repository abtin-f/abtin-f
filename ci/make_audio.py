#!/usr/bin/env python3
"""Writes a few 60 s sine-tone WAV files so the emulator has real songs for the media-session test."""
import math, struct, sys, wave

out = sys.argv[1]
RATE = 22050
for name, freq in (("GlassTestA", 330.0), ("GlassTestB", 440.0), ("GlassTestC", 550.0), ("GlassTestD", 660.0)):
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        frames = bytearray()
        for i in range(RATE * 60):
            env = min(1.0, i / 2000.0)
            frames += struct.pack("<h", int(9000 * env * math.sin(2 * math.pi * freq * i / RATE)))
        w.writeframes(bytes(frames))
