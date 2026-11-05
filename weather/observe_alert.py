# -*- coding: utf-8 -*-
"""تشخیص ناسازگاری مشاهده-مدل: وقتی بارش واقعی (رادار/ایستگاه) فعال است
ولی مدل‌ها آن را پیش‌بینی نکرده‌اند، هشدار می‌دهد."""
import json, os, sys

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

def main():
    # بارش واقعی از رادار
    nc = {}
    np_ = os.path.join(DATA_DIR, "radar_nowcast.json")
    if os.path.exists(np_):
        try:
            nc = json.load(open(np_, encoding="utf-8"))
        except Exception:
            nc = {}
    radar_cat = nc.get("category", "none")
    radar_active = radar_cat in ("moderate", "heavy")

    # آیا مدل‌ها برای چند ساعت آینده بارش پیش‌بینی کرده‌اند؟
    try:
        an = json.load(open(os.path.join(DATA_DIR, "analysis.json"), encoding="utf-8"))
    except Exception:
        an = []
    # جمع بارش پیش‌بینی‌شده 6 ساعت آینده
    future_precip = 0.0
    # ساده: میانگین بارش 6 رکورد اول
    for r in an[:6]:
        future_precip += r.get("precipitation_mean") or 0
    future_precip /= 6.0

    alert = None
    if radar_active and future_precip < 0.2:
        alert = {
            "level": "warning",
            "text": "بارش فعال مشاهده می‌شود ولی مدل‌ها آن را پیش‌بینی نکرده‌اند (همرفت ماندگار/محلی).",
        }
    elif radar_active:
        alert = {"level": "info", "text": "بارش فعال مشاهده می‌شود و مدل‌ها نیز آن را تأیید می‌کنند."}

    # ---- تصحیح مشاهده‌ای (blending مشاهده-مدل) ----
    # وقتی رادار/ایستگاه بارش فعال و ابر زیاد نشان می‌دهند، مقادیر ساعات
    # جاری باید به سمت واقعیت اصلاح شوند تا خروجی «بسیار کمتر از واقعیت»
    # نباشد. این فقط روی ساعات جاری اعمال می‌شود (نه کل آینده).
    correction = {"active": radar_active, "precip_min": 0.0, "cloud_min": None}
    if radar_active:
        # حداقل بارش ساعتی واقعی در هنگام بارش فعال (از رادار)
        correction["precip_min"] = 0.6
        # ابر: ایستگاه ۸ اکتاس = 100٪؛ حداقل 80٪ برای ساعات جاری
        correction["cloud_min"] = 80.0
        # اگر ایستگاه ابر ثبت کرده، از آن استفاده کن
        try:
            st = json.load(open(os.path.join(DATA_DIR, "station.json"), encoding="utf-8"))
            obs = st.get("observations", [])
            if obs:
                last_oct = obs[-1].get("cloud_octas")
                if last_oct is not None:
                    correction["cloud_min"] = min(100.0, last_oct * 12.5)
        except Exception:
            pass

    out = {
        "radar_active": radar_active,
        "future_precip": round(future_precip, 2),
        "alert": alert,
        "correction": correction,
    }
    op = os.path.join(DATA_DIR, "observe_alert.json")
    json.dump(out, open(op, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("[observe] radar_active=", radar_active, "future_precip=", round(future_precip, 2))
    print("[observe] correction=", correction)
    if alert:
        print("[observe] هشدار:", alert["text"])
    return 0

if __name__ == "__main__":
    sys.exit(main())
