#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرحله ۱: دریافت داده چندمدلی (GFS/ECMWF/ICON) از Open-Meteo و ایندکس رادار RainViewer.
خروجی: فایل‌های JSON خام در weather/data/
"""
import json
import os
import sys
import urllib.parse
import urllib.request

LAT = 32.3006   # بخش مرکزی دزفول (جنوب شهر، دشت خوزستان) — 32°18'02"N 48°25'51"E
LON = 48.4308
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

VARIABLES = (
    "temperature_2m,precipitation,cape,lifted_index,freezinglevel_height,"
    "cloudcover,cloudcover_low,cloudcover_mid,cloudcover_high,"
    "windspeed_10m,windgusts_10m,winddirection_10m,relativehumidity_2m"
)

MODELS = {
    "gfs": "gfs_seamless",        # آمریکایی، تفکیک ~0.25°
    "ecmwf": "ecmwf_ifs025",      # اروپایی
    "icon": "icon_seamless",      # آلمانی
}

RAINVIEWER_URL = "https://api.rainviewer.com/public/weather-maps.json"


def http_get(url: str, timeout: int = 60) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8")


def fetch_model(name: str, api_model: str) -> dict:
    params = {
        "latitude": LAT,
        "longitude": LON,
        "hourly": VARIABLES,
        "forecast_days": 8,
        "timezone": "UTC",
        "models": api_model,
    }
    url = "https://api.open-meteo.com/v1/forecast?" + urllib.parse.urlencode(params)
    raw = http_get(url)
    data = json.loads(raw)
    data["_model"] = name
    data["_api_model"] = api_model
    return data


def fetch_radar() -> dict:
    raw = http_get(RAINVIEWER_URL)
    return json.loads(raw)


def main() -> int:
    os.makedirs(OUT_DIR, exist_ok=True)
    ok = True
    for name, api_model in MODELS.items():
        try:
            data = fetch_model(name, api_model)
            path = os.path.join(OUT_DIR, f"{name}.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            n = len(data["hourly"]["time"])
            print(f"[OK] {name}: {n} ساعت، آخرین {data['hourly']['time'][-1]}")
        except Exception as exc:  # noqa: BLE001
            print(f"[FAIL] {name}: {exc}")
            ok = False

    try:
        radar = fetch_radar()
        path = os.path.join(OUT_DIR, "radar.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(radar, f, ensure_ascii=False, indent=2)
        past = radar.get("radar", {}).get("past", [])
        print(f"[OK] radar: {len(past)} فریم گذشته، آخرین {past[-1]['time'] if past else '—'}")
    except Exception as exc:  # noqa: BLE001
        print(f"[FAIL] radar: {exc}")
        ok = False

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
