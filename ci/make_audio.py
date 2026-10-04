#!/usr/bin/env python3
"""Writes the test music the emulator scenarios use.

* GlassTest{A..D}.wav   plain 60 s tones, no tags (title comes from the file name)
* CoverOne.mp3 / CoverTwo.mp3   silent 60 s MP3s with ID3 tags: title / artist / album, a 1000x1000 PNG cover and
  (CoverOne) embedded unsynced lyrics; CoverTwo gets a synced sidecar CoverTwo.lrc instead.
"""
import math, struct, sys, wave, zlib

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


def png(width, height):
    """Concentric colour rings, a diagonal and a 1px stripe block in the middle (shows how sharp the cover is drawn)."""
    palette = [(232, 56, 74), (255, 159, 10), (48, 209, 88), (10, 132, 255), (191, 90, 242), (255, 255, 255)]
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        row = bytearray()
        for x in range(width):
            ring = (min(x, y, width - 1 - x, height - 1 - y) // 50) % len(palette)
            r, g, b = palette[ring]
            if abs(x - y) < 6:
                r, g, b = 0, 0, 0
            if 350 <= x < 650 and 350 <= y < 650:
                r = g = b = 255 if (x // 1) % 2 == 0 else 0  # 1 px vertical stripes
            row += bytes((r, g, b))
        raw += row

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def frame(fid, body):
    return fid + struct.pack(">I", len(body)) + b"\x00\x00" + body


def text(fid, s):
    return frame(fid, b"\x00" + s.encode("latin-1"))


def id3(title, artist, album, track, cover, lyrics=None):
    frames = text(b"TIT2", title) + text(b"TPE1", artist) + text(b"TALB", album) + text(b"TRCK", track)
    frames += frame(b"APIC", b"\x00image/png\x00\x03\x00" + cover)
    if lyrics:
        frames += frame(b"USLT", b"\x00eng\x00" + lyrics.encode("latin-1"))
    size = len(frames)
    syncsafe = bytes(((size >> 21) & 0x7F, (size >> 14) & 0x7F, (size >> 7) & 0x7F, size & 0x7F))
    return b"ID3\x03\x00\x00" + syncsafe + frames


cover = png(1000, 1000)
silent_frame = b"\xff\xfb\x90\x64" + b"\x00" * 413  # one 417-byte MPEG-1 Layer III frame of silence (26 ms)
audio = silent_frame * 2300  # ~60 s
lyrics = "First line of the embedded lyrics\nSecond line of the embedded lyrics\nThird line of the embedded lyrics\nFourth line"
open(f"{out}/CoverOne.mp3", "wb").write(id3("CoverOne", "Cover Artist", "Cover Album", "1", cover, lyrics) + audio)
open(f"{out}/CoverTwo.mp3", "wb").write(id3("CoverTwo", "Cover Artist", "Cover Album", "2", cover) + audio)
open(f"{out}/CoverTwo.lrc", "w").write(
    "[ar:Cover Artist]\n[ti:CoverTwo]\n[00:01.00]Synced line one\n[00:04.00]Synced line two\n[00:07.00]Synced line three\n[00:10.00]Synced line four\n"
)
