#!/usr/bin/env python3
"""Horus Shield health check, for Horus Studio's Health page and the owner's alerts.

Runs every hour and after every build (see .github/workflows/guard.yml). Writes health.json:
  {"at": ..., "status": "ok|warn|bad", "checks": [{"id", "status", "title", "detail", "fix"}]}
Standard library only.
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
    add("site", "ok", "Website is up", "horusict.com opens normally.")
elif code in (403, 503) and ("challenge" in body.lower() or "cf-" in body.lower() or code == 403):
    add("site", "bad", "Website shows a \"verify you are human\" page", f"horusict.com answered {code}. Under Attack Mode or Bot Fight Mode is probably on.",
        "In Cloudflare: Security > Settings, turn off Under Attack Mode and Bot Fight Mode.")
else:
    add("site", "bad", "Website is down", f"horusict.com answered {code or 'nothing'}.", "Open Cloudflare and check the latest site deployment in Workers & Pages.")

# 2. the download link and the file behind it
code, _, loc = http(SITE + "/download", follow=False)
apk_code, _, _ = http(REL + "/HorusShield.apk", head=True)
if code in (301, 302) and "HorusShield.apk" in loc and apk_code == 200:
    add("download", "ok", "Download link works", "horusict.com/download leads to the latest version.")
elif apk_code != 200:
    add("download", "bad", "App file is missing", f"HorusShield.apk answered {apk_code}.", "The check republishes it by itself. If it stays red for an hour, ask Claude.")
else:
    add("download", "bad", "Download link on the website is broken", f"horusict.com/download answered {code}.", "Check that the website is up and that the _redirects file exists.")

# 3. what users have vs what was approved
live = None
code, body, _ = http(REL + "/version.json")
try:
    live = json.loads(body) if code == 200 else None
except Exception:
    live = None
if not live:
    add("version", "bad", "Update file is missing", f"version.json answered {code}.", "Installed apps will not see new versions. The check republishes it by itself.")

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
        add("builds", "warn", "Could not read the build status", str(e)[:150])

if live and last_ok:
    v = live.get("versionName", "?")
    if pending and pending[0]["run_number"] > last_ok["run_number"]:
        r, failed, pub = pending
        why = ("the emulator test failed" if "smoke" in failed else "the build failed" if "build" in failed
               else "the publish step failed" if "publish" in failed else "the publish step was skipped")
        add("publish", "bad", "The latest change did not reach users",
            f"Build {r['run_number']} ({(r['head_commit'] or {}).get('message', '').splitlines()[0][:60]}): {why}. Users are still on {v}.",
            "Open Releases in Studio, or ask Claude to find the cause.")
    else:
        add("publish", "ok", f"Users have the latest version ({v})", "The last approved change was published.")
if running:
    add("building", "warn", "A version is being built now", f"Build {running['run_number']} is running; it takes about half an hour.")

# 4. the urgent alert
code, body, _ = http(SITE + "/alert.json")
try:
    a = json.loads(body) if code == 200 else None
except Exception:
    a = None
if a is None:
    add("alert", "warn", "Urgent alert file does not open", f"alert.json answered {code}. If you publish an alert, phones will not see it.",
        "Bot Fight Mode is probably blocking the app. Turn it off in Cloudflare.")
elif a.get("active"):
    until = a.get("until")
    txt = (a.get("text") or {}).get("eg") or (a.get("text") or {}).get("en") or ""
    expired = bool(until) and date.fromisoformat(until) < date.today()
    if expired:
        add("alert", "ok", "No alert showing", "The last alert has expired.")
    elif len(txt.strip()) < 15 or txt.strip().lower() in ("test", "تجربة", "تست"):
        add("alert", "bad", "A test-looking alert is showing to everyone", f"\"{txt[:60]}\" {'until ' + until if until else 'with no end date'}.",
            "In Studio: Urgent alert > Stop the alert.")
    else:
        add("alert", "warn", "An urgent alert is showing to users", f"\"{txt[:80]}\" {'until ' + until if until else 'with no end date - remember to stop it'}.")
else:
    add("alert", "ok", "No urgent alert", "")

# 5. change requests waiting on a failed test
if TOKEN:
    try:
        bad_prs = []
        for p in api(f"/repos/{REPO}/pulls?state=open&per_page=30"):
            rs = api(f"/repos/{REPO}/actions/runs?head_sha={p['head']['sha']}&per_page=3")["workflow_runs"]
            if rs and rs[0]["status"] == "completed" and rs[0]["conclusion"] == "failure":
                bad_prs.append(f"#{p['number']} {p['title'][:40]}")
        if bad_prs:
            add("changes", "warn", "Change requests with a failed test", " · ".join(bad_prs), "Open Changes: reject them or ask Claude to fix them.")
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
                add("backup", "bad", "The last backup failed", f"{int(age_h)} hours ago.", "Open Backup in Studio to see why.")
            elif age_h > 36:
                add("backup", "bad", "No backup for more than a day", f"Last good backup {int(age_h)} hours ago.", "Open Backup in Studio and run one now.")
            else:
                add("backup", "ok", "Backup is working", f"Last good copy {int(age_h)} hours ago; {len(b.get('files', []))} copies kept on OneDrive.")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            add("backup", "warn", "No backup yet", "The first OneDrive backup has not run yet.")
    except Exception as e:
        add("backup", "warn", "Could not read the backup status", str(e)[:120])

# 7. the threat-intel pack, rebuilt daily by medhatsaif010/horus-intel
if TOKEN:
    try:
        rel = api("/repos/medhatsaif010/horus-intel/releases/tags/intel")
        pk = next((x for x in rel["assets"] if x["name"] == "horus-intel.json"), None)
        lg = next((x for x in rel["assets"] if x["name"] == "build.log"), None)
        if not pk:
            add("intel", "bad", "Threat intel pack is missing", "The intel release has no horus-intel.json.", "Run \"Horus threat-intel pack\" in the horus-intel repository's Actions.")
        else:
            age_h = (datetime.now(timezone.utc) - datetime.fromisoformat(pk["updated_at"].replace("Z", "+00:00"))).total_seconds() / 3600
            log = ""
            if lg:
                req = urllib.request.Request(lg["url"], headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/octet-stream", "User-Agent": UA})
                with urllib.request.urlopen(req, timeout=40) as r:
                    log = r.read(200_000).decode("utf-8", "replace")
            kept = [l.strip() for l in log.splitlines() if "keeping yesterday" in l or "skipped" in l]
            if age_h > 36:
                add("intel", "bad", "Threat intel is out of date", f"Last update {int(age_h)} hours ago.", "Open the horus-intel repository's Actions and check the failed \"Horus threat-intel pack\" run.")
            elif kept:
                add("intel", "warn", "Threat intel updated, but some sources were down", " · ".join(kept)[:300], "Phones keep yesterday's data for those sources. If it repeats for days, ask Claude to check the source.")
            else:
                add("intel", "ok", "Threat intel is up to date", f"Updated {int(age_h)} hours ago ({pk['size'] // 1024} KB).")
    except Exception as e:
        add("intel", "warn", "Could not read the threat intel status", str(e)[:120])

order = {"bad": 2, "warn": 1, "ok": 0}
overall = max((c["status"] for c in checks), key=lambda s: order[s], default="ok")
out = {"at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "status": overall, "live": (live or {}).get("versionName"), "checks": checks}
json.dump(out, open("health.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps(out, ensure_ascii=False, indent=1))
