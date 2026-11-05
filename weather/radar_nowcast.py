# -*- coding: utf-8 -*-
"""Nowcast راداری: بررسی بارش نزدیک نقطه هدف از آخرین فریم رادار RainViewer.
رادار گذشته RainViewer را می‌گیرد و tile مربوط به محل را دانلود می‌کند، سپس
پوشش/شدت بارش را در همسایگی نقطه هدف تخمین می‌زند (0-3 ساعت آینده).
"""
import json
import math
import os
import sys
import urllib.request
from io import BytesIO
from PIL import Image

LAT, LON = 32.3006, 48.4308
ZOOM = 9
RADIUS = 14
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")


def http_get_bytes(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def tile_xy(lat, lon, z):
    n = 2 ** z
    lat_r = math.radians(lat)
    xt = (lon + 180) / 360 * n
    yt = (1 - math.log(math.tan(lat_r) + 1 / math.cos(lat_r)) / math.pi) / 2 * n
    return xt, yt, int(xt), int(yt)


def analyze_tile(png_bytes, x_pix, y_pix, radius=RADIUS):
    im = Image.open(BytesIO(png_bytes)).convert("RGBA")
    W, H = im.size
    wet = 0
    total = 0
    intensity_sum = 0.0
    for dx in range(-radius, radius + 1):
        for dy in range(-radius, radius + 1):
            x = x_pix + dx
            y = y_pix + dy
            if 0 <= x < W and 0 <= y < H:
                r, g, b, a = im.getpixel((x, y))
                total += 1
                if a > 20:
                    wet += 1
                    intensity_sum += (255 - min(r, g, b)) / 255.0
    cover = wet / total if total else 0.0
    intensity = intensity_sum / wet if wet else 0.0
    return cover, intensity


def main():
    idx_path = os.path.join(DATA_DIR, "radar.json")
    if not os.path.exists(idx_path):
        print("[radar] فایل ایندکس رادار نیست.")
        return 0
    idx = json.load(open(idx_path, encoding="utf-8"))
    past = idx.get("radar", {}).get("past", [])
    if not past:
        print("[radar] فریم راداری نیست.")
        return 0
    last = past[-1]
    xt, yt, xi, yi = tile_xy(LAT, LON, ZOOM)
    x_pix = int((xt - xi) * 256)
    y_pix = int((yt - yi) * 256)
    url = "https://tilecache.rainviewer.com" + last["path"] + f"/{ZOOM}/{xi}/{yi}.png"
    try:
        png = http_get_bytes(url)
    except Exception as exc:
        print(f"[radar] خطا در دانلود tile: {exc}")
        return 1
    cover, intensity = analyze_tile(png, x_pix, y_pix)

    if cover < 0.02:
        category, label = "none", "بدون بارش نزدیک"
    elif cover < 0.15:
        category, label = "light", "بارش سبک/پراکنده نزدیک"
    elif cover < 0.35:
        category, label = "moderate", "بارش متوسط نزدیک"
    else:
        category, label = "heavy", "بارش قابل توجه نزدیک"

    result = {
        "frame_time": last["time"],
        "cover": round(cover, 3),
        "intensity": round(intensity, 3),
        "category": category,
        "label": label,
    }
    out = os.path.join(DATA_DIR, "radar_nowcast.json")
    json.dump(result, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"[radar] nowcast: {label} | پوشش={cover:.2f} شدت={intensity:.2f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
