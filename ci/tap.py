#!/usr/bin/env python3
"""Tap the first UI element whose text or content-description equals (or contains) the argument."""
import re, subprocess, sys, time

def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout

def main():
    want = sys.argv[1]
    index = int(sys.argv[2]) if len(sys.argv) > 2 else 0
    for _ in range(4):
        xml = dump()
        nodes = re.findall(r'(?:text|content-desc)="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        hits = [n for n in nodes if n[0] == want] or [n for n in nodes if want in n[0]]
        if len(hits) > index:
            _, l, t, r, b = hits[index]
            x, y = (int(l) + int(r)) // 2, (int(t) + int(b)) // 2
            subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
            print(f"tapped {want!r} at {x},{y}")
            return
        time.sleep(1.5)
    print(f"NOT FOUND: {want!r}")

main()
