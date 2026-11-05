#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرحله ۳: تحلیل همرفتی.
از مقادیر ensemble، شاخص‌های زیر محاسبه می‌شود:
  - سطح CAPE (ناپایداری)
  - امتیاز صاعقه/رعدوبرق (0-100)
  - ضخامت ابر و نوع ابر (همرفتی در برابر لایه‌ای)
  - برش باد (شاخص سازمان‌دهی سلول)
  - تشخیص محرک همرفتی (onset)
"""
import json
import math
import os
import sys

# ---------------------------------------------------------------- CAPE level
def cape_level(cape):
    if cape is None or cape <= 0:
        return "پایین"
    if cape < 500:
        return "پایین"
    if cape < 1500:
        return "متوسط"
    return "بالا"


# ------------------------------------------------------- thunderstorm score (0-100)
def thunderstorm_score(cape, precip, cloud_depth, gust):
    """
    امتیاز صاعقه/رعدوبرق از ترکیب:
      CAPE (0-40) + نرخ بارش (0-30) + ضخامت ابر (0-20) + تندباد (0-10)
    """
    # CAPE component
    cape_c = 0.0
    if cape is not None and cape > 0:
        cape_c = min(40.0, cape / 50.0)  # 2000 J/kg → 40
    # precipitation component (mm/h)
    p = precip if precip is not None else 0.0
    p_c = min(30.0, p * 6.0)  # 5 mm/h → 30
    # cloud depth component (0-100 → 0-20)
    cd = cloud_depth if cloud_depth is not None else 0.0
    cd_c = cd / 5.0  # 100 → 20
    # gust component (km/h → 0-10)
    g = gust if gust is not None else 0.0
    g_c = min(10.0, g / 13.0)  # 130 km/h → 10
    return round(min(100.0, cape_c + p_c + cd_c + g_c), 1)


# ------------------------------------------------------- severe storm index (0-100)
def severe_index(cape, shear, rh):
    """
    شاخص طوفان شدید از ترکیب (بدون وابستگی به Lifted Index که فقط از GFS
    می‌آید):
      CAPE (0-40) + برش باد (0-30) + رطوبت نسبی (0-30)
    همه این متغیرها برای هر چهار مدل در دسترس هستند.
    """
    cap = 0.0
    if cape is not None and cape > 0:
        cap = min(40.0, cape / 50.0)
    sh = 0.0
    if shear is not None:
        sh = min(30.0, shear / 5.0)
    rh_c = 0.0
    if rh is not None:
        # رطوبت نسبی بالاتر → احتمال بارش همرفتی بیشتر
        rh_c = max(0.0, min(30.0, (rh - 40.0) * 0.6))
    return round(min(100.0, cap + sh + rh_c), 1)


# ------------------------------------------------------------- cloud type
def cloud_type(cloud_depth, cape, precip):
    """همرفتی در برابر لایه‌ای."""
    if cape is not None and cape > 300 and precip is not None and precip >= 0.1:
        return "همرفتی"
    if cloud_depth is not None and cloud_depth >= 80:
        # لایه‌ای غلیظ
        if cape is not None and cape > 100:
            return "همرفتی/لایه‌ای"
        return "لایه‌ای"
    return "پراکنده"


# ------------------------------------------------------------- wind shear (km/h)
def wind_shear(prev, cur):
    """بزرگی اختلاف بردار باد بین دو ساعت متوالی."""
    if prev is None or cur is None:
        return 0.0
    ps, pd = prev
    cs, cd = cur
    p_rad = math.radians(pd)
    c_rad = math.radians(cd)
    pu = -ps * math.sin(p_rad)
    pv = -ps * math.cos(p_rad)
    cu = -cs * math.sin(c_rad)
    cv = -cs * math.cos(c_rad)
    return math.hypot(cu - pu, cv - pv)


def _safe(rows, i, key):
    if 0 <= i < len(rows):
        return rows[i].get(key)
    return None


def analyze(rows: list) -> list:
    """به هر رکورد ensemble، فیلدهای همرفتی اضافه می‌کند."""
    out = []
    for i, r in enumerate(rows):
        cape = r.get("cape_mean")
        precip = r.get("precipitation_mean")
        low = r.get("cloudcover_low_mean")
        mid = r.get("cloudcover_mid_mean")
        high = r.get("cloudcover_high_mean")
        gust = r.get("windgusts_10m_mean")
        speed = r.get("windspeed_10m_mean")
        direction = r.get("winddirection_10m_mean")

        cloud_depth = None
        vals = [v for v in (low, mid, high) if v is not None]
        if vals:
            cloud_depth = sum(vals) / len(vals)

        prev_wind = None
        if i > 0:
            prev_wind = (_safe(rows, i - 1, "windspeed_10m_mean"),
                         _safe(rows, i - 1, "winddirection_10m_mean"))
        cur_wind = (speed, direction)
        shear = wind_shear(prev_wind, cur_wind)

        r["cape_level"] = cape_level(cape)
        r["thunderstorm_score"] = thunderstorm_score(cape, precip, cloud_depth, gust)
        r["severe_index"] = severe_index(cape, shear, r.get("relativehumidity_2m_mean"))
        r["lifted_index"] = r.get("lifted_index_mean")
        r["freezing_level"] = r.get("freezinglevel_height_mean")
        r["cloud_depth"] = round(cloud_depth, 1) if cloud_depth is not None else None
        r["cloud_type"] = cloud_type(cloud_depth, cape, precip)
        r["wind_shear"] = round(shear, 1)
        # سطح اطمینان از توافق مدل‌ها (0-100) — برای همه رکوردها
        nz = r.get("precip_nonzero_models", 0)
        pos = r.get("cape_positive_models", 0)
        n = r.get("n_models", 4)
        r["confidence"] = round(min(100.0, 50.0 + (nz / n) * 30.0 + (pos / n) * 20.0), 1)
        out.append(r)

    # تشخیص محرک همرفتی (onset): جهش CAPE یا شروع بارش همراه با CAPE>0
    for i, r in enumerate(out):
        trigger = False
        reason = []
        cape = r.get("cape_mean") or 0.0
        precip = r.get("precipitation_mean") or 0.0
        if i > 0:
            prev_cape = out[i - 1].get("cape_mean") or 0.0
            prev_precip = out[i - 1].get("precipitation_mean") or 0.0
            if cape - prev_cape >= 200:
                trigger = True
                reason.append(f"جهش CAPE (+{round(cape - prev_cape)} J/kg)")
            if precip > 0 and prev_precip == 0 and cape > 0:
                trigger = True
                reason.append("شروع بارش با CAPE مثبت")
        r["convective_trigger"] = trigger
        r["trigger_reason"] = "؛ ".join(reason) if reason else ""
    return out


def save_analysis(rows: list) -> str:
    import json
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "analysis.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=2)
    return path


def main() -> int:
    import json
    import sys
    data_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
    with open(os.path.join(data_dir, "ensemble.json"), "r", encoding="utf-8") as f:
        rows = json.load(f)
    rows = analyze(rows)
    path = save_analysis(rows)
    print(f"[OK] تحلیل همرفتی: {len(rows)} ساعت → {path}")
    for r in rows[:4]:
        print(" ", r["time"], "CAPE=", r["cape_mean"], r["cape_level"],
              "| صاعقه=", r["thunderstorm_score"],
              "| ابر=", r["cloud_depth"], r["cloud_type"],
              "| برش=", r["wind_shear"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
