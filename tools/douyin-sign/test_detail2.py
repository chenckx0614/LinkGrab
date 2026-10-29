"""Live test v2: douyin aweme/detail — a_bogus only, retry (server samples)."""
import json
import random
import re
import string
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, ".")
from dtk.signing.native import abogus

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
AWEME_ID = "7700635801125808369"


def mint_ttwid():
    body = json.dumps({
        "region": "cn", "aid": 1768, "needFid": False,
        "service": "www.ixigua.com", "migrate_info": {"ticket": "", "source": "node"},
        "cbUrlProtocol": "https", "union": True,
    }).encode()
    req = urllib.request.Request(
        "https://ttwid.bytedance.com/ttwid/union/register/",
        method="POST", data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        set_cookie = resp.headers.get("Set-Cookie", "")
        m = re.search(r"ttwid=([^;]+)", set_cookie)
        print("ttwid:", "ok" if m else "MISS")
        return m.group(1) if m else None


PARAMS = [
    ("device_platform", "webapp"),
    ("aid", "6383"),
    ("channel", "channel_pc_web"),
    ("pc_client_type", "1"),
    ("version_code", "290100"),
    ("version_name", "29.1.0"),
    ("cookie_enabled", "true"),
    ("screen_width", "1920"),
    ("screen_height", "1080"),
    ("browser_language", "zh-CN"),
    ("browser_platform", "Win32"),
    ("browser_name", "Chrome"),
    ("browser_version", "131.0.0.0"),
    ("browser_online", "true"),
    ("engine_name", "Blink"),
    ("engine_version", "131.0.0.0"),
    ("os_name", "Windows"),
    ("os_version", "10"),
    ("cpu_core_num", "8"),
    ("device_memory", "8"),
    ("platform", "PC"),
    ("downlink", "10"),
    ("effective_type", "4g"),
    ("round_trip_time", "0"),
    ("update_version_code", "170400"),
    ("aweme_id", AWEME_ID),
]


def attempt(ttwid, n):
    ms_token = "".join(random.choices(string.ascii_letters + string.digits, k=116)) + "=="
    params = PARAMS + [("msToken", ms_token)]
    # a_bogus preimage: python urlencode(form) — matches dtk's encode_query
    query = urllib.parse.urlencode(params)
    ab = abogus.ABogus(UA)
    bogus = ab.get_value(query)
    full = f"{query}&a_bogus={urllib.parse.quote(bogus, safe='')}"
    url = f"https://www.douyin.com/aweme/v1/web/aweme/detail/?{full}"
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Referer": "https://www.douyin.com/",
        "Cookie": f"ttwid={ttwid}; msToken={ms_token}",
    })
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        body = e.read()
        status = e.code
    text = body.decode("utf-8", "replace")
    ok = status == 200 and text.strip().startswith("{")
    print(f"attempt {n}: HTTP {status} len {len(body)} {'OK' if ok else text[:80]}")
    if ok:
        d = json.loads(text)
        detail = d.get("aweme_detail") or {}
        print("  status_code:", d.get("status_code"), "| desc:", (detail.get("desc") or "")[:40])
        imgs = detail.get("images") or []
        print("  images:", len(imgs), "| video:", bool(detail.get("video")))
        return detail
    return None


def main():
    ttwid = mint_ttwid()
    if not ttwid:
        return
    for n in range(1, 7):
        if attempt(ttwid, n):
            print("SUCCESS on attempt", n)
            return
        time.sleep(1.5)
    print("all attempts failed")


if __name__ == "__main__":
    main()
