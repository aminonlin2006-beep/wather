#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرحله ۵: اجرای سرتاسری و تولید گزارش نهایی (به ساعت ایران).
"""
import json
import os
from datetime import datetime, timedelta, timezone

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
IRAN_OFFSET = timedelta(hours=3, minutes=30)
IRAN_TZ = timezone(timedelta(hours=3, minutes=30))

MODEL_LABELS = {
    "gfs": "🇺🇸 GFS (آمریکایی)",
    "ecmwf": "🇪🇺 ECMWF (اروپایی)",
    "icon": "🇩🇪 ICON (آلمانی)",
}


def fmt(v, digits=2):
    """فرمت عدد با حداکثر `digits` رقم اعشار (حذف صفرهای اضافی)."""
    if v is None:
        return "—"
    try:
        f = float(v)
    except (TypeError, ValueError):
        return str(v)
    s = f"{f:.{digits}f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s


def utc_to_iran(utc_iso: str) -> str:
    try:
        dt = datetime.fromisoformat(utc_iso)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(IRAN_TZ).strftime("%Y-%m-%d %H:%M")
    except Exception:  # noqa: BLE001
        return utc_iso


def wind_dir_label(deg):
    if deg is None:
        return "—"
    dirs = ["شمالی", "شمال شمال شرقی", "شمال شرقی", "شرق شمال شرقی",
            "شرقی", "شرق جنوب شرقی", "جنوب شرقی", "جنوب جنوب شرقی",
            "جنوبی", "جنوب جنوب غربی", "جنوب غربی", "غرب جنوب غربی",
            "غربی", "غرب شمال غربی", "شمال غربی", "شمال شمال غربی"]
    idx = int((deg + 11.25) / 22.5) % 16
    return dirs[idx]


def load(name):
    with open(os.path.join(DATA_DIR, name), "r", encoding="utf-8") as f:
        return json.load(f)


def load_ensemble_prob() -> dict:
    """برمی‌گرداند: {time_utc: میانگین احتمال بارش دو مدل ensemble}."""
    prob_map = {}
    keys = ["ensemble_gfs.json", "ensemble_ecmwf.json"]
    series = []
    for k in keys:
        p = os.path.join(DATA_DIR, k)
        if not os.path.exists(p):
            continue
        with open(p, "r", encoding="utf-8") as f:
            data = json.load(f)
        times = data["hourly"]["time"]
        probs = data["hourly"]["precipitation_probability"]
        series.append(dict(zip(times, probs)))
    if not series:
        return prob_map
    all_times = set()
    for s in series:
        all_times.update(s.keys())
    for t in sorted(all_times):
        vals = [s.get(t) for s in series if t in s]
        vals = [v for v in vals if v is not None]
        if vals:
            prob_map[t] = round(sum(vals) / len(vals), 1)
    return prob_map


def build_report(track, analysis, ensemble_prob=None) -> str:
    pat = track["pattern"]
    window = track["window"]
    if ensemble_prob is None:
        ensemble_prob = {}

    lines = []
    lines.append("# گزارش رهگیری همرفتی — بخش مرکزی دزفول (جنوب شهر)")
    lines.append("")
    lines.append(f"**بازه پیش‌بینی:** {utc_to_iran(pat['window_start'])} تا {utc_to_iran(pat['window_end'])} (به ساعت ایران)")
    lines.append("")
    lines.append("## ۱. اختلاف ساعت منبع و ایران")
    lines.append("")
    lines.append("همه مدل‌ها و سورس‌ها زمان را به **UTC** می‌دهند. ایران **UTC+3:30** است؛")
    lines.append("بنابراین: **ساعت ایران = ساعت منبع + ۳ ساعت و ۳۰ دقیقه**.")
    lines.append("")
    lines.append("## ۲. جدول میانگین چندمدلی وزن‌دهی‌شده (GFS + ECMWF + ICON) — به ساعت ایران")
    lines.append("")
    lines.append("| ساعت (ایران) | دما °C | بارش mm | احتمال بارش % | ناپایداری | ابر % | نوع ابر | باد km/h | تندباد | جهت | صاعقه | شدید | فاز | اطمینان % |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in window:
        iran = utc_to_iran(r["time"])
        temp = r.get("temperature_2m_mean")
        precip = r.get("precipitation_mean")
        prob = ensemble_prob.get(r["time"], 0)
        cape_lvl = r.get("cape_level", "—")
        cd = r.get("cloud_depth")
        ctype = r.get("cloud_type", "—")
        speed = r.get("windspeed_10m_mean")
        gust = r.get("windgusts_10m_mean")
        wdir = wind_dir_label(r.get("winddirection_10m_mean"))
        score = r.get("thunderstorm_score", 0)
        severe = r.get("severe_index", 0)
        phase = r.get("phase", "—")
        conf = r.get("confidence", 0)
        lines.append(
            f"| {iran} | {fmt(temp)} | {fmt(precip)} | {fmt(prob)} | {cape_lvl} | "
            f"{fmt(cd)} | {ctype} | {fmt(speed)} | {fmt(gust)} | {wdir} | "
            f"{fmt(score)} | {fmt(severe)} | {phase} | {fmt(conf)} |"
        )
    lines.append("")
    lines.append("## ۳. الگوی استخراج‌شده از محاسبات")
    lines.append("")
    lines.append(f"- **محرک همرفتی (شروع):** {pat['trigger_times'] and '، '.join(utc_to_iran(t) for t in pat['trigger_times']) or 'در این بازه محرک مشخصی شناسایی نشد'}")
    lines.append(f"- **اوج بارش:** {utc_to_iran(pat['peak_precip_time'])} ({pat['peak_precip_mm']} mm)")
    lines.append(f"- **اوج امتیاز صاعقه/رعدوبرق:** {utc_to_iran(pat['peak_thunderstorm_time'])} (امتیاز {pat['peak_thunderstorm_score']} از ۱۰۰)")
    lines.append(f"- **مدت‌زمان فاز فعال:** {pat['active_duration_h']} ساعت")
    _mc = pat['max_cape']
    _lvl = "بالا" if _mc > 1500 else ("متوسط" if _mc > 500 else "پایین")
    lines.append(f"- **بیشینه ناپایداری در بازه:** {_lvl} (CAPE ~{fmt(_mc)} J/kg)")
    lines.append("")
    lines.append("## ۴. جمع‌بندی")
    lines.append("")
    # جمع‌بندی خودکار
    max_score = max((r.get("thunderstorm_score", 0) for r in window), default=0)
    max_precip = max((r.get("precipitation_mean", 0) or 0 for r in window), default=0)
    if max_score >= 40 or max_precip >= 1:
        verdict = "احتمال قابل توجه رعدوبرق/بارش همرفتی در این بازه وجود دارد — هوشیاری لازم است."
    elif max_score >= 15 or max_precip > 0:
        verdict = "فعالیت همرفتی ضعیف تا متوسط محتمل است؛ بارش سبک/پراکنده دور از انتظار نیست."
    else:
        verdict = "خطر همرفتی پایین است؛ آسمان عمدتاً ابری بدون بارش محسوس."
    lines.append(f"- {verdict}")
    lines.append("")
    lines.append("> این گزارش از میانگین وزن‌دهی‌شده سه مدل (GFS/ECMWF/ICON) و احتمال ensemble تهیه شده است و صرفاً جنبه اطلاع‌رسانی دارد.")
    return "\n".join(lines)


def main() -> int:
    track = load("track.json")
    analysis = load("analysis.json")
    ensemble_prob = load_ensemble_prob()
    report = build_report(track, analysis, ensemble_prob)
    report_path = os.path.join(DATA_DIR, "report.md")
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report)
    print(report)
    print(f"\n[OK] گزارش در {report_path} ذخیره شد.")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
