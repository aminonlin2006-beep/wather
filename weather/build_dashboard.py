#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ساخت فایل data.js برای داشبورد وب از روی خروجی‌های محاسباتی.
"""
import json
import os
import sys

from forecast import utc_to_iran, wind_dir_label

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
DASH_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboard")


def load(name):
    with open(os.path.join(DATA_DIR, name), "r", encoding="utf-8") as f:
        return json.load(f)


def combined_prob() -> dict:
    prob_map = {}
    series = []
    for k in ["ensemble_gfs.json", "ensemble_ecmwf.json"]:
        p = os.path.join(DATA_DIR, k)
        if not os.path.exists(p):
            continue
        data = json.load(open(p, "r", encoding="utf-8"))
        series.append(dict(zip(data["hourly"]["time"],
                               data["hourly"]["precipitation_probability"])))
    all_times = set()
    for s in series:
        all_times.update(s.keys())
    for t in sorted(all_times):
        vals = [s.get(t) for s in series if t in s]
        vals = [v for v in vals if v is not None]
        if vals:
            prob_map[t] = round(sum(vals) / len(vals), 1)
    return prob_map


def _r2(v):
    """گرد کردن به حداکثر ۲ رقم اعشار."""
    if v is None:
        return None
    return round(float(v), 2)


def _agreement(r):
    """نشانگر توافق مدل‌ها برای بارش (good/weak)."""
    pstd = r.get("precipitation_std")
    pmean = r.get("precipitation_mean") or 0
    if pstd is not None and pstd >= 0.5 and pstd > pmean * 1.5:
        return "weak"
    return "good"


def load_plumes(window_start, window_end) -> list:
    """پلوم‌های اسپاگتی: سری دما و بارش هر مدل در بازه پنجره."""
    models = [
        ("gfs", "GFS"),
       
        ("ecmwf", "ECMWF"),
        ("icon", "ICON"),
    ]
    plumes = []
    for key, label in models:
        p = os.path.join(DATA_DIR, f"{key}.json")
        if not os.path.exists(p):
            continue
        data = json.load(open(p, "r", encoding="utf-8"))
        times = data["hourly"]["time"]
        temps = data["hourly"].get("temperature_2m", [])
        precips = data["hourly"].get("precipitation", [])
        t_sel = []
        pr_sel = []
        for i, t in enumerate(times):
            if window_start <= t <= window_end:
                t_sel.append(temps[i] if i < len(temps) else None)
                pr_sel.append(precips[i] if i < len(precips) else None)
        plumes.append({
            "model": key,
            "label": label,
            "temps": [_r2(v) for v in t_sel],
            "precips": [_r2(v) for v in pr_sel],
        })
    return plumes


_PHASE_SEV = {"آرام": 0, "در حال توسعه": 1, "در حال زوال": 2, "فعال": 3}
_CAPE_SEV = {"پایین": 0, "متوسط": 1, "بالا": 2}


def _hour_row(r, prob):
    return {
        "time_utc": r["time"],
        "time_iran": utc_to_iran(r["time"]),
        "temp": _r2(r.get("temperature_2m_mean")),
        "precip": _r2(r.get("precipitation_mean")),
        "prob": _r2(prob.get(r["time"], 0)),
        "cape": _r2(r.get("cape_mean")),
        "cape_level": r.get("cape_level", "—"),
        "rh": _r2(r.get("relativehumidity_2m_mean")),
        "cloud_depth": _r2(r.get("cloud_depth")),
        "cloud_type": r.get("cloud_type", "—"),
        "wind_speed": _r2(r.get("windspeed_10m_mean")),
        "gust": _r2(r.get("windgusts_10m_mean")),
        "wdir": wind_dir_label(r.get("winddirection_10m_mean")),
        "score": _r2(r.get("thunderstorm_score", 0)),
        "severe": _r2(r.get("severe_index", 0)),
        "phase": r.get("phase", "—"),
        "confidence": _r2(r.get("confidence", 0)),
        "agreement": _agreement(r),
        "precip_max": _r2(r.get("precipitation_max")),
        "convective_risk": bool(
            r.get("precipitation_max") is not None
            and r["precipitation_max"] >= 0.5
            and (r.get("precipitation_mean") or 0) < 0.2
        ),
        "precip_risk": _precip_risk(r, prob.get(r["time"], 0)),
        "qpf": _qpf(r, prob.get(r["time"], 0)),
    }


def _qpf(r, prob):
    """QPF احتمالی: بیشینهِ (میانه بارش، بیشینه مدل × احتمال/100)."""
    med = r.get("precipitation_mean") or 0
    mx = r.get("precipitation_max") or 0
    return _r2(max(med, mx * prob / 100.0))


def _conv_potential(h, nowcast_cat):
    """پتانسیل همرفتی (0-100) — جایگزین شاخص‌های CAPE که در این منطقه
    هنگام بارش واقعی صفر بودند. ترکیب: احتمال ensemble + بیشینه بارش
    مدل‌ها + رطوبت نسبی + رادار (بارش جاری)."""
    score = 0.0
    score += min(40.0, (h.get("prob") or 0) * 0.8)
    mx = h.get("precip_max") or 0
    score += min(30.0, mx * 12.0)
    rh = h.get("rh")
    if rh is not None:
        score += max(0.0, min(20.0, (rh - 40.0) * 0.4))
    if nowcast_cat in ("moderate", "heavy"):
        score += 10.0
    return round(min(100.0, score), 1)


def _precip_risk(r, prob):
    """ریسک بارش ترکیبی (QPF احتمالی): احتمال ensemble + ریسک همرفتی."""
    if r.get("convective_risk") or prob >= 50:
        return "بالا"
    if prob >= 25 or (r.get("precipitation_mean") or 0) >= 0.2:
        return "متوسط"
    return "پایین"


def _block_agg(rows, prob):
    temps = [r.get("temperature_2m_mean") for r in rows if r.get("temperature_2m_mean") is not None]
    precips = [r.get("precipitation_mean") or 0 for r in rows]
    capes = [r.get("cape_mean") for r in rows if r.get("cape_mean") is not None]
    clouds = [r.get("cloud_depth") for r in rows if r.get("cloud_depth") is not None]
    winds = [r.get("windspeed_10m_mean") for r in rows if r.get("windspeed_10m_mean") is not None]
    gusts = [r.get("windgusts_10m_mean") for r in rows if r.get("windgusts_10m_mean") is not None]
    scores = [r.get("thunderstorm_score") or 0 for r in rows]
    severes = [r.get("severe_index") or 0 for r in rows]
    probs = [prob.get(r["time"], 0) for r in rows]
    phases = [r.get("phase") for r in rows if r.get("phase") and r.get("phase") != "—"]
    dom_phase = max(phases, key=lambda p: _PHASE_SEV.get(p, 0)) if phases else "—"
    maxs = [r.get("precipitation_max") for r in rows if r.get("precipitation_max") is not None]
    block_max = max(maxs) if maxs else 0
    levels = [r.get("cape_level") for r in rows if r.get("cape_level")]
    dom_level = max(levels, key=lambda x: _CAPE_SEV.get(x, 0)) if levels else "—"
    return {
        "start_iran": utc_to_iran(rows[0]["time"]),
        "end_iran": utc_to_iran(rows[-1]["time"]),
        "temp_min": _r2(min(temps)) if temps else None,
        "temp_max": _r2(max(temps)) if temps else None,
        "precip_sum": _r2(sum(precips)),
        "precip_max": _r2(block_max),
        "convective_risk": bool(block_max >= 0.5 and sum(precips) < 0.2 * len(rows)),
        "prob_max": _r2(max(probs)) if probs else 0,
        "cape_max": _r2(max(capes)) if capes else 0,
        "cape_level": dom_level,
        "score_max": _r2(max(scores)),
        "severe_max": _r2(max(severes)),
        "cloud_mean": _r2(sum(clouds) / len(clouds)) if clouds else None,
        "wind_max": _r2(max(winds)) if winds else None,
        "gust_max": _r2(max(gusts)) if gusts else None,
        "phase": dom_phase,
    }


def _chunk(rows, n):
    return [rows[i:i + n] for i in range(0, len(rows), n) if rows[i:i + n]]


def _group_by_iran_day(rows, prob):
    """گروه‌بندی ساعت‌ها بر اساس روز تقویمی ایران (برای سازگاری با نمای روزانه/هفتگی)."""
    groups = {}
    order = []
    for r in rows:
        date = utc_to_iran(r["time"])[:10]
        if date not in groups:
            groups[date] = []
            order.append(date)
        groups[date].append(r)
    return [_block_agg(groups[d], prob) for d in order]


def build_ranges(analysis, prob, now_iso):
    # مقایسه در سطح ساعت تا `now_iso` با دقیقه، ساعت فعلی را از دست ندهد
    now_hour = now_iso[:13]
    rows = [r for r in analysis if r["time"][:13] >= now_hour]
    # ساعتی: کل بازه پیش‌بینی (یک هفته) تا با روزانه/هفتگی هم‌خوان باشد
    hourly = [_hour_row(r, prob) for r in rows[:168]]
    h6 = [_block_agg(chunk, prob) for chunk in _chunk(rows[:48], 6)]
    h12 = [_block_agg(chunk, prob) for chunk in _chunk(rows[:48], 12)]
    # روزانه: بر اساس روز تقویمی ایران
    day = _group_by_iran_day(rows, prob)[:7]
    return {"hourly": hourly, "h6": h6, "h12": h12, "day": day}


def build_data() -> dict:
    analysis = load("analysis.json")
    track = load("track.json")
    prob = combined_prob()
    pat = track["pattern"]
    plumes = load_plumes(pat["window_start"], pat["window_end"])
    now_iso = track.get("generated_at") or pat["window_start"]
    ranges = build_ranges(analysis, prob, now_iso)

    # ایستگاه‌های هواشناسی دزفول (داده مشاهده‌ای رسمی)
    station = {}
    observed = []
    sp = os.path.join(DATA_DIR, "station.json")
    if os.path.exists(sp):
        try:
            sd = json.load(open(sp, "r", encoding="utf-8"))
            stations_meta = sd.get("stations", {})
            station = {
                "names_fa": [meta.get("name_fa") for meta in stations_meta.values()],
                "wmo": [meta.get("wmo") for meta in stations_meta.values()],
            }
            obs_all = sd.get("observations", [])
            for o in obs_all[-24:]:
                octas = o.get("cloud_octas")
                observed.append({
                    "time_iran": utc_to_iran(o["time"]),
                    "station": o.get("station"),
                    "temperature": _r2(o.get("temperature")),
                    "humidity": _r2(o.get("humidity")),
                    "wind_speed": _r2(o.get("wind_speed")),
                    "wind_dir": o.get("wind_dir"),
                    "precip_6h": _r2(o.get("precip_6h")),
                    "cloud_pct": round(octas / 8 * 100, 1) if octas is not None else None,
                })
        except Exception:
            pass

    # Nowcast راداری (بارش نزدیک نقطه، از آخرین فریم رادار)
    nowcast = {}
    np_ = os.path.join(DATA_DIR, "radar_nowcast.json")
    if os.path.exists(np_):
        try:
            nowcast = json.load(open(np_, "r", encoding="utf-8"))
        except Exception:
            nowcast = {}

    # تشخیص ناسازگاری مشاهده-مدل (بارش فعال ولی مدل‌ها صفر)
    observe_alert = {}
    op_ = os.path.join(DATA_DIR, "observe_alert.json")
    if os.path.exists(op_):
        try:
            observe_alert = json.load(open(op_, "r", encoding="utf-8"))
        except Exception:
            observe_alert = {}

    # پتانسیل همرفتی برای رکوردهای ساعتی (جایگزین شاخص‌های CAPE)
    nowcast_cat = nowcast.get("category", "none")
    for h in ranges["hourly"]:
        h["conv_potential"] = _conv_potential(h, nowcast_cat)

    # تصحیح مشاهده‌ای (blending): وقتی رادار/ایستگاه بارش فعال و ابر زیاد
    # نشان می‌دهند ولی مدل‌ها صفرند، مقادیر چند ساعت اول را به سمت واقعیت
    # اصلاح کن تا خروجی «بسیار کمتر از واقعیت» نباشد.
    corr = observe_alert.get("correction") or {}
    if corr.get("active"):
        n_corr = 6  # ساعات جاری (رادار واقعیت را می‌بیند)
        for h in ranges["hourly"][:n_corr]:
            pmin = corr.get("precip_min", 0.0)
            if (h.get("precip") or 0) < pmin:
                h["precip"] = _r2(max(h.get("precip") or 0, pmin))
                h["precip_orig"] = h.get("precip")
            cmin = corr.get("cloud_min")
            if cmin is not None and (h.get("cloud_depth") or 0) < cmin:
                h["cloud_depth"] = _r2(cmin)

    # وضعیت اعتبارسنجی انباشتی (بایاس دما هر مدل نسبت به ایستگاه)
    verification = {"count": 0, "bias": {}}
    vp = os.path.join(DATA_DIR, "verify_history.json")
    if os.path.exists(vp):
        try:
            hist = json.load(open(vp, "r", encoding="utf-8"))
            verification["count"] = len(hist)
            for name in ["gfs", "ecmwf", "icon"]:
                errs = []
                for rec in hist:
                    m = rec.get("models", {}).get(name)
                    if m and m.get("model_temperature") is not None and m.get("obs_temperature") is not None:
                        errs.append(abs(m["model_temperature"] - m["obs_temperature"]))
                if errs:
                    verification["bias"][name] = round(sum(errs) / len(errs), 2)
        except Exception:
            pass

    analysis_by_time = {r["time"]: r for r in analysis}
    hours = []
    for w in track["window"]:
        t = w["time"]
        a = analysis_by_time.get(t, {})
        hours.append({
            "time_utc": t,
            "time_iran": utc_to_iran(t),
            "temp": _r2(w.get("temperature_2m_mean")),
            "precip": _r2(w.get("precipitation_mean")),
            "prob": _r2(prob.get(t, 0)),
            "cape": _r2(w.get("cape_mean")),
            "cloud_depth": _r2(w.get("cloud_depth")),
            "cloud_type": w.get("cloud_type", "—"),
            "wind_speed": _r2(w.get("windspeed_10m_mean")),
            "gust": _r2(w.get("windgusts_10m_mean")),
            "wdir": wind_dir_label(w.get("winddirection_10m_mean")),
            "score": _r2(w.get("thunderstorm_score", 0)),
            "severe": _r2(w.get("severe_index", 0)),
            "phase": w.get("phase", "—"),
            "confidence": _r2(w.get("confidence", 0)),
        })

    pat = track["pattern"]
    max_score = max((h["score"] for h in hours), default=0)
    max_precip = max((h["precip"] or 0 for h in hours), default=0)
    if max_score >= 40 or max_precip >= 1:
        verdict = "alert"
        verdict_text = "احتمال قابل توجه رعدوبرق/بارش همرفتی — هوشیاری لازم است."
    elif max_score >= 15 or max_precip > 0:
        verdict = "watch"
        verdict_text = "فعالیت همرفتی ضعیف تا متوسط؛ بارش سبک/پراکنده دور از انتظار نیست."
    else:
        verdict = "ok"
        verdict_text = "خطر همرفتی پایین؛ آسمان عمدتاً ابری بدون بارش محسوس."

    model_weights = {}
    if analysis:
        mw = analysis[0].get("model_weights")
        if isinstance(mw, dict):
            model_weights = {k: round(v, 3) for k, v in mw.items()}

    return {
        "meta": {
            "location": "بخش مرکزی دزفول (جنوب شهر)",
            "lat": 32.3006,
            "lon": 48.4308,
            "iran_offset": "+3:30",
            "generated_iran": utc_to_iran(track.get("generated_at") or hours[0]["time_utc"]),
        },
        "pattern": {
            "window_start_iran": utc_to_iran(pat["window_start"]),
            "window_end_iran": utc_to_iran(pat["window_end"]),
            "peak_precip_time_iran": utc_to_iran(pat["peak_precip_time"]),
            "peak_precip_mm": _r2(pat["peak_precip_mm"]),
            "peak_ts_time_iran": utc_to_iran(pat["peak_thunderstorm_time"]),
            "peak_ts_score": _r2(pat["peak_thunderstorm_score"]),
            "active_duration_h": pat["active_duration_h"],
            "max_cape": _r2(pat["max_cape"]),
            "trigger_times_iran": [utc_to_iran(t) for t in pat["trigger_times"]],
        },
        "verdict": {"level": verdict, "text": verdict_text},
        "model_weights": model_weights,
        "plumes": plumes,
        "hours": hours,
        "ranges": ranges,
        "station": station,
        "observed": observed,
        "nowcast": nowcast,
        "verification": verification,
        "observe_alert": observe_alert,
    }


def main() -> int:
    os.makedirs(DASH_DIR, exist_ok=True)
    data = build_data()
    js = "window.FORECAST_DATA = " + json.dumps(data, ensure_ascii=False, indent=2) + ";"
    out = os.path.join(DASH_DIR, "data.js")
    with open(out, "w", encoding="utf-8") as f:
        f.write(js)
    print(f"[OK] داشبورد داده → {out}")
    print(f"      {len(data['hours'])} ساعت | verdict={data['verdict']['level']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
