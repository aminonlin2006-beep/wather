#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
دریافت احتمال بارش از مدل‌های ensemble (GEFS و ECMWF ensemble) از Open-Meteo.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
LAT = 32.3006
LON = 48.4308


def http_get(url: str, timeout: int = 60) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8")


def fetch_ensemble_prob(model_key: str, api_model: str) -> dict:
    params = {
        "latitude": LAT,
        "longitude": LON,
        "hourly": "temperature_2m,precipitation_probability,precipitation",
        "forecast_days": 8,
        "timezone": "UTC",
        "models": api_model,
        "ensemble": "true",
    }
    url = "https://api.open-meteo.com/v1/forecast?" + urllib.parse.urlencode(params)
    data = json.loads(http_get(url))
    path = os.path.join(DATA_DIR, f"ensemble_{model_key}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    return data


def main() -> int:
    for key, api_model in [("gfs", "gfs_seamless"), ("ecmwf", "ecmwf_ifs025")]:
        data = fetch_ensemble_prob(key, api_model)
        times = data["hourly"]["time"]
        prob = data["hourly"]["precipitation_probability"]
        print(f"[OK] ensemble {key}: {len(times)} ساعت، آخرین {times[-1]}")
        # نمایش چند مقدار نمونه
        for i in range(17, 25):
            print(f"   {times[i]}  prob={prob[i]}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
