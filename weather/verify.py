# -*- coding: utf-8 -*-
"""اعتبارسنجی تاریخی: مقایسه پیش‌بینی مدل‌ها با داده ایستگاه صفی آباد."""
import datetime, json, os, sys

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
MODELS = ["gfs", "ecmwf", "icon"]

def load_models():
    models = {}
    for name in MODELS:
        p = os.path.join(DATA_DIR, f"{name}.json")
        if os.path.exists(p):
            try:
                models[name] = json.load(open(p, encoding="utf-8"))
            except Exception:
                pass
    return models

def load_station():
    p = os.path.join(DATA_DIR, "station.json")
    if not os.path.exists(p):
        return {}
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return {}

def compute_bias(history, name, key):
    errs = []
    for rec in history:
        m = rec.get("models", {}).get(name)
        if not m:
            continue
        mv, ov = m.get("model_" + key), m.get("obs_" + key)
        if mv is None or ov is None:
            continue
        errs.append(abs(mv - ov))
    return round(sum(errs) / len(errs), 2) if errs else None


def compute_precip_ratio(history, name):
    """نسبت بایاس بارش مدل (پیش‌بینی/مشاهده) — >1 یعنی بیش‌پیش‌بینی."""
    ratios = []
    for rec in history:
        m = rec.get("models", {}).get(name)
        if not m:
            continue
        mv = m.get("model_precipitation")
        ov = m.get("obs_precip_6h")
        if mv is None or ov is None or ov <= 0:
            continue
        ratios.append(mv / ov)
    if len(ratios) < 3:
        return None
    return round(sum(ratios) / len(ratios), 3)

def main():
    models = load_models()
    station = load_station()
    obs = station.get("observations", [])
    if not obs or not models:
        print("[verify] داده کافی نیست.")
        return 0
    last = obs[-1]
    t = last["time"][:13]
    results = {}
    for name, m in models.items():
        h = m.get("hourly", {})
        times = h.get("time", [])
        if t not in times:
            continue
        i = times.index(t)
        results[name] = {
            "time": t,
            "model_temperature": h.get("temperature_2m", [None] * len(times))[i],
            "obs_temperature": last.get("temperature"),
            "model_precipitation": h.get("precipitation", [None] * len(times))[i],
            "obs_precip_6h": last.get("precip_6h"),
        }
    hist_path = os.path.join(DATA_DIR, "verify_history.json")
    history = []
    if os.path.exists(hist_path):
        try:
            history = json.load(open(hist_path, encoding="utf-8"))
        except Exception:
            history = []
    if results:
        history.append({
            "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "models": results,
        })
        history = history[-500:]
        json.dump(history, open(hist_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"[verify] {len(results)} مدل برای ساعت {t} ثبت شد. کل رکوردها: {len(history)}")
    cal = {"temperature_bias": {}, "precipitation_bias": {}}
    for name in MODELS:
        tb = compute_bias(history, name, "temperature")
        if tb is not None:
            cal["temperature_bias"][name] = tb
            print(f"  {name}: بایاس دما = ±{tb}°C")
        pr = compute_precip_ratio(history, name)
        if pr is not None:
            cal["precipitation_bias"][name] = pr
            print(f"  {name}: نسبت بایاس بارش = {pr}")
    cal_path = os.path.join(DATA_DIR, "calibrated_bias.json")
    json.dump(cal, open(cal_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"[verify] کالیبره در {cal_path} ذخیره شد.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
