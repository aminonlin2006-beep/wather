#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرحله ۲: موتور میانگین چندمدلی.
هم‌ترازی GFS/ECMWF/ICON بر اساس ساعت (UTC) و محاسبه میانگین، انحراف معیار و توافق.
"""
import json
import math
import os

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
# سه مدل مستقل (GFS013 به‌عنوان مدل تکراری GFS حذف شد)
MODEL_NAMES = ["gfs", "ecmwf", "icon"]

# اصلاح بایاس بارش هر مدل (کالیبره بر اساس مشاهدات منطقه).
# ICON بیش‌پیش‌بینی می‌کند → ضریب < 1.
PRECIP_BIAS = {"icon": 0.6}

# متغیرهای عددی که در ensemble شرکت می‌کنند
VARS = [
    "temperature_2m",
    "precipitation",
    "cape",
    "lifted_index",
    "freezinglevel_height",
    "cloudcover",
    "cloudcover_low",
    "cloudcover_mid",
    "cloudcover_high",
    "windspeed_10m",
    "windgusts_10m",
    "winddirection_10m",
    "relativehumidity_2m",
]


def load_models() -> dict:
    models = {}
    for name in MODEL_NAMES:
        path = os.path.join(DATA_DIR, f"{name}.json")
        with open(path, "r", encoding="utf-8") as f:
            models[name] = json.load(f)
    return models


def _mean(vals):
    vals = [v for v in vals if v is not None]
    if not vals:
        return None
    return sum(vals) / len(vals)


def _std(vals):
    vals = [v for v in vals if v is not None]
    if len(vals) < 2:
        return 0.0
    m = sum(vals) / len(vals)
    return math.sqrt(sum((v - m) ** 2 for v in vals) / len(vals))


def _median(vals):
    vals = sorted(v for v in vals if v is not None)
    if not vals:
        return None
    n = len(vals)
    mid = n // 2
    if n % 2 == 1:
        return vals[mid]
    return (vals[mid - 1] + vals[mid]) / 2.0


def _load_station_skill():
    """بارگذاری مهارت واقعی هر مدل از اعتبارسنجی (بایاس دما و بارش).

    برمی‌گرداند: dict {مدل: {temp_err, precip_err}} — هرچه کمتر بهتر.
    """
    cal = _load_calibrated()
    tb = cal.get("temperature_bias", {})
    pb = cal.get("precipitation_bias", {})
    skill = {}
    for name in MODEL_NAMES:
        skill[name] = {
            "temp_err": tb.get(name),
            "precip_err": pb.get(name),
        }
    return skill


def compute_weights(models: dict) -> dict:
    """وزن‌دهی بر اساس مهارت واقعی هر مدل (از ایستگاه‌ها).

    اگر تاریخچه اعتبارسنجی کافی باشد، وزن هر مدل از معکوس خطای واقعی‌اش
    (بایاس دما + بارش) محاسبه می‌شود. اگر کافی نباشد، به وزن مساوی برمی‌گردد
    (چون داده‌ای برای قضاوت نداریم و حذف مدل بدون دلیل صحیح نیست).
    """
    skill = _load_station_skill()
    has_skill = any(
        s.get("temp_err") is not None or s.get("precip_err") is not None
        for s in skill.values()
    )
    if not has_skill:
        n = len(MODEL_NAMES)
        return {name: 1.0 / n for name in MODEL_NAMES}

    weights = {}
    for name in MODEL_NAMES:
        s = skill[name]
        err = 0.0
        cnt = 0
        if s.get("temp_err") is not None:
            err += s["temp_err"]
            cnt += 1
        if s.get("precip_err") is not None:
            err += s["precip_err"] * 2.0  # بارش وزن بیشتری دارد
            cnt += 1
        if cnt == 0:
            err = 1.0
        else:
            err /= cnt
        weights[name] = 1.0 / (1.0 + err)
    tot = sum(weights.values())
    if tot <= 0:
        n = len(MODEL_NAMES)
        return {name: 1.0 / n for name in MODEL_NAMES}
    return {k: v / tot for k, v in weights.items()}


def _wmean(vals, weights):
    pairs = [(v, w) for v, w in zip(vals, weights) if v is not None]
    if not pairs:
        return None
    s = sum(v * w for v, w in pairs)
    ws = sum(w for _, w in pairs)
    return s / ws if ws else None


def _load_calibrated():
    """بارگذاری ضرایب کالیبره‌شده از اعتبارسنجی (اگر موجود باشد)."""
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "calibrated_bias.json")
    if not os.path.exists(p):
        return {}
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return {}


def _load_temp_bias():
    """میانگین بایاس دما از کالیبره (برای اصلاح MOS). اگر کافی نباشد صفر."""
    cal = _load_calibrated()
    tb = cal.get("temperature_bias", {})
    vals = [v for v in tb.values() if v is not None]
    if len(vals) >= 3:
        return round(sum(vals) / len(vals), 2)
    return 0.0


def _load_precip_bias():
    """ضریب اصلاح بارش هر مدل از کالیبره؛ اگر نبود، پیش‌فرض PRECIP_BIAS.
    نسبت بایاس >1 (بیش‌پیش‌بینی) → ضریب <1."""
    cal = _load_calibrated()
    pb = cal.get("precipitation_bias", {})
    merged = dict(PRECIP_BIAS)
    for name, ratio in pb.items():
        if ratio and ratio > 0:
            merged[name] = round(min(2.0, max(0.2, 1.0 / ratio)), 3)
    return merged


def build_ensemble(models: dict) -> list:
    """برمی‌گرداند: لیست رکوردهای ساعتی با مقادیر ensemble."""
    weights = compute_weights(models)
    order = MODEL_NAMES
    wlist = [weights[n] for n in order]
    temp_bias = _load_temp_bias()
    precip_bias = _load_precip_bias()
    # اولین مدل به‌عنوان مرجع زمان
    ref = models[order[0]]
    times = ref["hourly"]["time"]
    rows = []
    for i, t in enumerate(times):
        row = {"time": t, "model_weights": weights}
        for var in VARS:
            vals = []
            for name in order:
                arr = models[name]["hourly"].get(var)
                if arr is not None and i < len(arr):
                    vals.append(arr[i])
            # بارش: به‌جای میانگین وزن‌دهی‌شده از میانه استفاده می‌شود
            # چون به مدل‌های پرت (مثلاً ICON که بارش را بیش از حد پیش‌بینی
            # می‌کند) مقاوم است و دقت را بالا می‌برد.
            if var == "precipitation":
                # اعمال اصلاح بایاس بارش هر مدل (کالیبره بر اساس مشاهدات منطقه)
                cal_vals = []
                for name, v in zip(order, vals):
                    if v is None:
                        continue
                    cal_vals.append(v * precip_bias.get(name, 1.0))
                med = _median(cal_vals)
                nn = [v for v in cal_vals if v is not None]
                mx = max(nn) if nn else None
                row["precipitation_max"] = round(mx, 3) if mx is not None else None
                # ترکیب همرفتی‌آگاه: اگر مدل‌ها اختلاف شدید دارند (سناریوی
                # همرفتی)، سیگنال اقلیت حفظ می‌شود تا رگبار از دست نرود.
                if nn and mx is not None and med is not None and mx > med * 2:
                    row[f"{var}_mean"] = round(max(med, 0.5 * mx), 3)
                else:
                    row[f"{var}_mean"] = round(med, 3) if med is not None else None
            else:
                row[f"{var}_mean"] = _wmean(vals, wlist)
            # اصلاح بایاس دما (MOS) بر اساس داده ایستگاه — اگر تاریخچه کافی باشد
            if var == "temperature_2m" and temp_bias:
                t = row[f"{var}_mean"]
                if t is not None:
                    row[f"{var}_mean"] = round(t - temp_bias, 2)
            row[f"{var}_std"] = _std(vals)
        # توافق: نسبت مدل‌هایی که مقدار غیرصفر/غیرتهی دارند (برای بارش و CAPE)
        precip_vals = []
        cape_vals = []
        for name in order:
            arr = models[name]["hourly"].get("precipitation")
            if arr is not None and i < len(arr):
                precip_vals.append(arr[i])
            carr = models[name]["hourly"].get("cape")
            if carr is not None and i < len(carr):
                cape_vals.append(carr[i])
        row["precip_nonzero_models"] = sum(1 for v in precip_vals if v and v > 0)
        row["cape_positive_models"] = sum(1 for v in cape_vals if v and v > 0)
        row["n_models"] = len(order)
        rows.append(row)
    return rows


def save_ensemble(rows: list) -> str:
    path = os.path.join(DATA_DIR, "ensemble.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=2)
    return path


def main() -> int:
    models = load_models()
    rows = build_ensemble(models)
    path = save_ensemble(rows)
    print(f"[OK] ensemble: {len(rows)} ساعت → {path}")
    # نمونه کوچک
    for r in rows[:3]:
        print(" ", r["time"], "T=", round(r["temperature_2m_mean"], 1),
              "precip=", r["precipitation_mean"], "cape=", r["cape_mean"])
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
