#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
سرور وب ساده برای داشبورد + به‌روزرسانی خودکار هنگام باز شدن.

اجرا:
    python3 weather/server.py
سپس مرورگر: http://127.0.0.1:8000/
"""
import http.server
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
DASH = os.path.join(ROOT, "dashboard")
PORT = int(os.environ.get("PORT", "8000"))

PIPELINE = [
    [sys.executable, os.path.join(ROOT, "fetch_models.py")],
    [sys.executable, os.path.join(ROOT, "fetch_ensemble.py")],
    [sys.executable, os.path.join(ROOT, "fetch_station.py")],
    [sys.executable, os.path.join(ROOT, "ensemble.py")],
    [sys.executable, os.path.join(ROOT, "convection.py")],
    [sys.executable, os.path.join(ROOT, "track.py")],
    [sys.executable, os.path.join(ROOT, "radar_nowcast.py")],
    [sys.executable, os.path.join(ROOT, "observe_alert.py")],
    [sys.executable, os.path.join(ROOT, "forecast.py")],
    [sys.executable, os.path.join(ROOT, "build_dashboard.py")],
    [sys.executable, os.path.join(ROOT, "verify.py")],
]


def run_pipeline():
    log = []
    for cmd in PIPELINE:
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT, timeout=180)
        except subprocess.TimeoutExpired:
            log.append(f"$ {' '.join(cmd)} -> timeout")
            return False, "\n".join(log)
        log.append(f"$ {' '.join(cmd)} -> rc={r.returncode}")
        if r.stdout.strip():
            log.append(r.stdout.strip()[-400:])
        if r.stderr.strip():
            log.append(r.stderr.strip()[-400:])
        if r.returncode != 0:
            return False, "\n".join(log)
    return True, "\n".join(log)


def read_generated():
    p = os.path.join(DASH, "data.js")
    if not os.path.exists(p):
        return None
    txt = open(p, "r", encoding="utf-8").read()
    start = txt.find("{")
    end = txt.rfind("}")
    if start == -1 or end == -1:
        return None
    try:
        data = json.loads(txt[start:end + 1])
        return data.get("meta", {}).get("generated_iran")
    except Exception:
        return None


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=DASH, **kw)

    def do_GET(self):
        if self.path.startswith("/api/refresh"):
            ok, log = run_pipeline()
            gen = read_generated()
            body = json.dumps(
                {"ok": ok, "generated": gen, "log": log[-2000:]},
                ensure_ascii=False,
            ).encode("utf-8")
            self.send_response(200 if ok else 500)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        return super().do_GET()

    def log_message(self, fmt, *args):
        pass

    def handle_error(self, request, client_address):
        # خطاهای قطع اتصال کلاینت (مثلاً هنگام اجرای طولانی به‌روزرسانی)
        # را نادیده بگیر تا لاگ شلوغ نشود.
        exc = sys.exc_info()[1]
        if isinstance(exc, (BrokenPipeError, ConnectionResetError)):
            return
        super().handle_error(request, client_address)


if __name__ == "__main__":
    print(f"[OK] داشبورد در http://127.0.0.1:{PORT}/")
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        srv.shutdown()
