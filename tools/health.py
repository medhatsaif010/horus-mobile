#!/usr/bin/env python3
"""Horus Shield health check, for Horus Studio's "الصحة" page and the owner's alerts.

Runs every hour and after every build (see .github/workflows/guard.yml). Writes health.json:
  {"at": ..., "status": "ok|warn|bad", "checks": [{"id", "status", "title", "detail", "fix"}]}
Arabic texts are for the owner (Egyptian Arabic). Standard library only.
"""
import json, os, sys, time, urllib.request, urllib.error
from datetime import datetime, timezone, date

REPO = os.environ.get("GITHUB_REPOSITORY", "medhatsaif010/horus-mobile")
TOKEN = os.environ.get("GH_TOKEN", "")
SITE = "https://horusict.com"
REL = "https://github.com/medhatsaif010/horus-mobile-releases/releases/download/apk"
UA = "Mozilla/5.0 (HorusShield health check)"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def http(url, follow=True, head=False):
    """(status, body or '', location). Never raises."""
    req = urllib.request.Request(url, method="HEAD" if head else "GET", headers={"User-Agent": UA})
    opener = urllib.request.build_opener() if follow else urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(req, timeout=40) as r:
            return r.status, (b"" if head else r.read(200_000)).decode("utf-8", "replace"), r.headers.get("location", "")
    except urllib.error.HTTPError as e:
        return e.code, "", e.headers.get("location", "") if e.headers else ""
    except Exception as e:
        return 0, str(e)[:200], ""


def api(path):
    req = urllib.request.Request("https://api.github.com" + path, headers={
        "Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json", "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.load(r)


checks = []


def add(id, status, title, detail="", fix=""):
    checks.append({"id": id, "status": status, "title": title, "detail": detail, "fix": fix})


# 1. the website
code, body, _ = http(SITE + "/")
if code == 200 and "Horus" in body:
    add("site", "ok", "الموقع شغال", "horusict.com بيفتح عادي.")
elif code in (403, 503) and ("challenge" in body.lower() or "cf-" in body.lower() or code == 403):
    add("site", "bad", "الموقع بيطلب «تأكيد إنك إنسان»", f"horusict.com رد بـ {code}. غالبًا وضع Under Attack أو Bot Fight شغال.",
        "من Cloudflare: Security ثم Settings، واقفل Under Attack Mode وBot Fight Mode.")
else:
    add("site", "bad", "الموقع مش بيفتح", f"horusict.com رد بـ {code or 'مفيش رد'}.", "افتح Cloudflare وشوف آخر نشر للموقع في Workers & Pages.")

# 2. the download link and the file behind it
code, _, loc = http(SITE + "/download", follow=False)
apk_code, _, _ = http(REL + "/HorusShield.apk", head=True)
if code in (301, 302) and "HorusShield.apk" in loc and apk_code == 200:
    add("download", "ok", "لينك التحميل شغال", "horusict.com/download بيوصّل لآخر نسخة.")
elif apk_code != 200:
    add("download", "bad", "ملف البرنامج مش موجود", f"ملف HorusShield.apk رد بـ {apk_code}.", "الفحص بيعيد النشر لوحده. لو فضلت حمرا ساعة، كلّم Claude.")
else:
    add("download", "bad", "لينك التحميل على الموقع مش شغال", f"horusict.com/download رد بـ {code}.", "اتأكد إن الموقع شغال وإن ملف _redirects موجود.")

# 3. what users have vs what was approved
live = None
code, body, _ = http(REL + "/version.json")
try:
    live = json.loads(body) if code == 200 else None
except Exception:
    live = None
if not live:
    add("version", "bad", "ملف التحديثات مش موجود", f"version.json رد بـ {code}.", "البرنامج عند الناس مش هيعرف إن في تحديث. الفحص بيعيد النشر لوحده.")

last_ok = None   # newest main build whose publish step succeeded
pending = None   # newest main build that finished but did not publish
running = None
if TOKEN:
    try:
        runs = api(f"/repos/{REPO}/actions/workflows/build.yml/runs?branch=main&per_page=15")["workflow_runs"]
        runs = [r for r in runs if r["event"] in ("push", "workflow_dispatch")]
        for r in runs:
            if r["status"] != "completed":
                running = running or r
                continue
            jobs = {j["name"]: j for j in api(f"/repos/{REPO}/actions/runs/{r['id']}/jobs")["jobs"]}
            pub = jobs.get("publish", {}).get("conclusion")
            if pub == "success":
                last_ok = r
                break
            if pending is None:
                failed = [n for n, j in jobs.items() if j.get("conclusion") == "failure"]
                pending = (r, failed, pub)
    except Exception as e:
        add("builds", "warn", "مش قادر أقرا حالة البناء", str(e)[:150])

if live and last_ok:
    v = live.get("versionName", "?")
    if pending and pending[0]["run_number"] > last_ok["run_number"]:
        r, failed, pub = pending
        why = ("الاختبار على المحاكي فشل" if "smoke" in failed else "البناء نفسه فشل" if "build" in failed
               else "خطوة النشر فشلت" if "publish" in failed else "خطوة النشر اتخطت")
        add("publish", "bad", "آخر تعديل متنشرش للناس",
            f"البناء رقم {r['run_number']} ({(r['head_commit'] or {}).get('message', '').splitlines()[0][:60]}): {why}. الناس لسه على {v}.",
            "افتح «الإصدارات» في Studio، أو اطلب من Claude يشوف السبب.")
    else:
        add("publish", "ok", f"الناس عندها آخر نسخة ({v})", "آخر تعديل وافقت عليه اتنشر.")
if running:
    add("building", "warn", "في نسخة بتتبني دلوقتي", f"البناء رقم {running['run_number']} شغال، وبياخد حوالي نص ساعة.")

# 4. the urgent alert
code, body, _ = http(SITE + "/alert.json")
try:
    a = json.loads(body) if code == 200 else None
except Exception:
    a = None
if a is None:
    add("alert", "warn", "ملف التنبيه العاجل مش بيفتح", f"alert.json رد بـ {code}. لو نشرت تنبيه، الموبايلات مش هتشوفه.",
        "غالبًا Bot Fight Mode بيمنع البرنامج. اقفله من Cloudflare.")
elif a.get("active"):
    until = a.get("until")
    txt = (a.get("text") or {}).get("eg") or (a.get("text") or {}).get("en") or ""
    expired = bool(until) and date.fromisoformat(until) < date.today()
    if expired:
        add("alert", "ok", "مفيش تنبيه ظاهر", "آخر تنبيه خلصت مدته.")
    elif len(txt.strip()) < 15 or txt.strip().lower() in ("test", "تجربة", "تست"):
        add("alert", "bad", "تنبيه شكله تجربة ظاهر عند كل الناس", f"«{txt[:60]}» {'لحد ' + until if until else 'من غير تاريخ نهاية'}.",
            "من Studio: «التنبيه العاجل» ثم «أوقف التنبيه».")
    else:
        add("alert", "warn", "في تنبيه عاجل ظاهر عند الناس", f"«{txt[:80]}» {'لحد ' + until if until else 'من غير تاريخ نهاية — افتكر توقفه'}.")
else:
    add("alert", "ok", "مفيش تنبيه عاجل", "")

# 5. change requests waiting on a failed test
if TOKEN:
    try:
        bad_prs = []
        for p in api(f"/repos/{REPO}/pulls?state=open&per_page=30"):
            rs = api(f"/repos/{REPO}/actions/runs?head_sha={p['head']['sha']}&per_page=3")["workflow_runs"]
            if rs and rs[0]["status"] == "completed" and rs[0]["conclusion"] == "failure":
                bad_prs.append(f"#{p['number']} {p['title'][:40]}")
        if bad_prs:
            add("changes", "warn", "طلبات تعديل الاختبار بتاعها فشل", " · ".join(bad_prs), "افتح «التعديلات»: ارفضها أو اطلب من Claude يصلّحها.")
    except Exception:
        pass

# 6. the OneDrive backup (written by .github/workflows/backup.yml)
if TOKEN:
    try:
        rel = api(f"/repos/{REPO}/releases/tags/backup-status")
        a = next((x for x in rel["assets"] if x["name"] == "backup.json"), None)
        b = None
        if a:
            req = urllib.request.Request(a["url"], headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/octet-stream", "User-Agent": UA})
            with urllib.request.urlopen(req, timeout=40) as r:
                b = json.load(r)
        if b:
            age_h = (datetime.now(timezone.utc) - datetime.fromisoformat(b["at"].replace("Z", "+00:00"))).total_seconds() / 3600
            if not b.get("ok"):
                add("backup", "bad", "آخر باك أب فشل", f"من {int(age_h)} ساعة.", "افتح «الباك أب» في Studio وشوف السبب.")
            elif age_h > 36:
                add("backup", "bad", "الباك أب متعملش من أكتر من يوم", f"آخر باك أب ناجح من {int(age_h)} ساعة.", "افتح «الباك أب» في Studio واعمل باك أب دلوقتي.")
            else:
                add("backup", "ok", "الباك أب شغال", f"آخر نسخة ناجحة من {int(age_h)} ساعة، و{len(b.get('files', []))} نسخة محفوظة على OneDrive.")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            add("backup", "warn", "مفيش باك أب لسه", "أول باك أب على OneDrive لسه ماتعملش.")
    except Exception as e:
        add("backup", "warn", "مش قادر أقرا حالة الباك أب", str(e)[:120])

order = {"bad": 2, "warn": 1, "ok": 0}
overall = max((c["status"] for c in checks), key=lambda s: order[s], default="ok")
out = {"at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "status": overall, "live": (live or {}).get("versionName"), "checks": checks}
json.dump(out, open("health.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps(out, ensure_ascii=False, indent=1))
