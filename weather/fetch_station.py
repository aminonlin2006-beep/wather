# -*- coding: utf-8 -*-
"""دریافت و پارس داده مشاهده‌ای ایستگاه‌های هواشناسی دزفول.
منبع: Ogimet (سینوپ رمزگشایی‌شده)."""
import json
import os
import re
import sys
import urllib.request

# ایستگاه‌های هواشناسی دزفول و اطراف
STATIONS = {
    "dezful_airport": {
        "name": "Dezful Airport",
        "name_fa": "دزفول فرودگاه",
        "wmo": "40812",
        "lat": 32.4344,
        "lon": 48.3976,
        "alt": 143.0,
    },
    "safiabad": {
        "name": "Safi-Abad Dezful",
        "name_fa": "صفی آباد دزفول",
        "wmo": "40794",
        "lat": 32.2667,
        "lon": 48.4167,
        "alt": 82.0,
    },
    "siah_mansur": {
        "name": "Siah Mansur",
        "name_fa": "سیاه منصور",
        "wmo": "40810",
        "lat": 32.2000,
        "lon": 48.5000,
        "alt": 100.0,
    },
}

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
OUT = os.path.join(DATA_DIR, "station.json")


def http_get(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", errors="replace")


def _clean(s):
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("&nbsp;", " ").strip()
    return s


def _to_iso(date_md, time_hm):
    """تبدیل '11/04/2026' و '18:00' به ISO UTC."""
    mo, dy, yr = date_md.split("/")
    return f"{yr}-{mo}-{dy}T{time_hm}:00"


def _num(s):
    s = _clean(s).replace(",", ".")
    if not s or s in ("----", "---", "-----", ""):
        return None
    try:
        return float(s)
    except ValueError:
        return None


def parse_synop(html):
    """استخراج ردیف‌های جدول سینوپ."""
    rows = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", html, flags=re.S | re.I):
        cells = [_clean(c) for c in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", tr, flags=re.S | re.I)]
        if len(cells) < 15:
            continue
        m = re.match(r"^(\d{2})/(\d{2})/(\d{4})$", cells[0])
        tm = re.match(r"^(\d{2}):(\d{2})$", cells[1])
        if not m or not tm:
            continue
        rec = {
            "time": _to_iso(cells[0], cells[1]),
            "temperature": _num(cells[2]),
            "dewpoint": _num(cells[3]),
            "humidity": _num(cells[4]),
            "wind_speed": _num(cells[9]),
            "wind_dir": cells[8] if cells[8] not in ("-----", "---", "") else None,
            "precip_6h": None,
            "cloud_octas": None,
        }
        prec = _clean(cells[13])
        pm = re.match(r"^([\d.]+)/(\d+)h$", prec)
        if pm:
            rec["precip_6h"] = float(pm.group(1))
        n = _num(cells[14])
        if n is not None and 0 <= n <= 8:
            rec["cloud_octas"] = n
        rows.append(rec)
    rows.sort(key=lambda r: r["time"])
    return rows


def fetch_one(wmo, name_fa):
    from datetime import datetime, timezone
    now = datetime.now(timezone.utc)
    url = (
        f"https://www.ogimet.com/cgi-bin/gsynres?ind={wmo}&lang=en"
        f"&decoded=yes&ndays=3&ano={now.year}&mes={now.month}&day={now.day}"
    )
    try:
        html = http_get(url)
        rows = parse_synop(html)
        return rows
    except Exception as exc:  # noqa: BLE001
        print(f"[WARN] {name_fa}: {exc}")
        return []


def main():
    stations = {}
    all_obs = []
    for key, st in STATIONS.items():
        rows = fetch_one(st["wmo"], st["name_fa"])
        if rows:
            stations[key] = st
            for r in rows:
                r["station"] = key
            all_obs.extend(rows)
            print(f"[OK] {st['name_fa']} (WMO {st['wmo']}): {len(rows)} مشاهده")
        else:
            print(f"[WARN] {st['name_fa']}: هیچ مشاهده‌ای دریافت نشد")

    if not all_obs:
        print("[ERR] هیچ داده‌ای از ایستگاه‌ها دریافت نشد.")
        return 1

    all_obs.sort(key=lambda r: r["time"])
    data = {"stations": stations, "observations": all_obs}
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print(f"[OK] کل: {len(all_obs)} مشاهده از {len(stations)} ایستگاه → {OUT}")
    for r in all_obs[-3:]:
        print("  ", r["time"], r["station"], r["temperature"], r["humidity"], r["wind_speed"], r["precip_6h"], r["cloud_octas"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
