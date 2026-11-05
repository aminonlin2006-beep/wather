#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرحله ۴: رهگیری و پیش‌بینی ۱۲ ساعته.
از سری زمانی تحلیل همرفتی، فازهای سلول همرفتی (شروع/اوج/زوال/آرام)
تشخیص داده می‌شود و یک الگوی ۱۲ ساعته استخراج می‌گردد.
"""
import json
import math
import os
from datetime import datetime, timezone

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
IRAN_OFFSET = 3 * 3600 + 30 * 60  # UTC+3:30

# آستانه‌های فاز
SCORE_ACTIVE = 40.0
PRECIP_ACTIVE = 1.0
SCORE_DEVELOP = 15.0


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M")


def classify_phase(score, precip, trigger):
    """طبقه‌بندی فاز همرفتی برای یک ساعت."""
    if score >= SCORE_ACTIVE or precip >= PRECIP_ACTIVE:
        return "اوج (فعال)"
    if trigger:
        return "شروع (توسعه)"
    if score >= SCORE_DEVELOP:
        return "در حال توسعه"
    return "آرام"


def confidence(rec):
    """سطح اطمینان از توافق مدل‌ها (0-100)."""
    # اگر حداقل ۲ مدل بارش غیرصفر داشته باشند → اطمینان بالاتر
    nz = rec.get("precip_nonzero_models", 0)
    pos = rec.get("cape_positive_models", 0)
    n = rec.get("n_models", 3)
    base = 50.0
    base += (nz / n) * 30.0
    base += (pos / n) * 20.0
    return round(min(100.0, base), 1)


def build_track(rows: list, now_iso: str) -> dict:
    # پیدا کردن اولین ساعت >= now
    start_idx = None
    for i, r in enumerate(rows):
        if r["time"] >= now_iso[:13]:  # مقایسه تا سطح ساعت
            start_idx = i
            break
    if start_idx is None:
        start_idx = 0

    window = rows[start_idx:start_idx + 12]
    if len(window) < 12:
        window = rows[start_idx:]

    for r in window:
        r["phase"] = classify_phase(
            r.get("thunderstorm_score", 0),
            r.get("precipitation_mean", 0) or 0,
            r.get("convective_trigger", False),
        )
        r["confidence"] = confidence(r)

    # ---- استخراج الگو ----
    triggers = [r for r in window if r.get("convective_trigger")]
    active = [r for r in window if r.get("phase", "").startswith("اوج")]
    max_precip_rec = max(window, key=lambda r: r.get("precipitation_mean", 0) or 0)
    max_score_rec = max(window, key=lambda r: r.get("thunderstorm_score", 0) or 0)

    # مدت‌زمان فاز فعال
    active_times = [r["time"] for r in active]
    duration_h = 0
    if active_times:
        first = datetime.fromisoformat(active_times[0])
        last = datetime.fromisoformat(active_times[-1])
        duration_h = int((last - first).total_seconds() // 3600) + 1

    pattern = {
        "trigger_times": [r["time"] for r in triggers],
        "peak_precip_time": max_precip_rec["time"],
        "peak_precip_mm": round(max_precip_rec.get("precipitation_mean", 0) or 0, 2),
        "peak_thunderstorm_time": max_score_rec["time"],
        "peak_thunderstorm_score": max_score_rec.get("thunderstorm_score", 0),
        "active_duration_h": duration_h,
        "max_cape": round(max((r.get("cape_mean", 0) or 0 for r in window), default=0), 1),
        "window_start": window[0]["time"] if window else None,
        "window_end": window[-1]["time"] if window else None,
    }
    return {"window": window, "pattern": pattern, "generated_at": utc_now_iso()}


def save_track(result: dict) -> str:
    path = os.path.join(DATA_DIR, "track.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    return path


def main() -> int:
    with open(os.path.join(DATA_DIR, "analysis.json"), "r", encoding="utf-8") as f:
        rows = json.load(f)
    now = utc_now_iso()
    result = build_track(rows, now)
    path = save_track(result)
    pat = result["pattern"]
    print(f"[OK] رهگیری: {len(result['window'])} ساعت ({pat['window_start']} → {pat['window_end']})")
    print(f"[OK] الگو: محرک در {pat['trigger_times'] or '—'} | "
          f"اوج بارش {pat['peak_precip_time']} ({pat['peak_precip_mm']}mm) | "
          f"اوج صاعقه {pat['peak_thunderstorm_time']} (امتیاز {pat['peak_thunderstorm_score']}) | "
          f"مدت فعال {pat['active_duration_h']}h | بیشینه CAPE {pat['max_cape']}")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
