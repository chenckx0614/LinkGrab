"""Generate golden test vectors for the Kotlin port: LCG-pinned ABogus."""
import json
import sys

sys.path.insert(0, ".")
from dtk.signing.native import abogus, websign, sm3


class LCG:
    """Deterministic rng with the same call surface as random.Random."""

    def __init__(self, seed):
        self.state = seed & ((1 << 64) - 1)

    def random(self):
        self.state = (self.state * 6364136223846793005 + 1442695040888963407) & ((1 << 64) - 1)
        return (self.state >> 11) / float(1 << 53)


UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
QUERY = ("device_platform=webapp&aid=6383&channel=channel_pc_web&aweme_id=7700635801125808369"
         "&msToken=AbCdEf123456Zz==")
NOW_MS = 1730000000000

out = {}

# 1) SM3 standard vectors
out["sm3_abc"] = sm3.sm3_hexdigest(b"abc")
out["sm3_empty"] = sm3.sm3_hexdigest(b"")

# 2) ABogus with pinned rng and fixed clock
ab = abogus.ABogus(UA, rng=LCG(42))
out["abogus_v1"] = ab.get_value(QUERY, now_ms=NOW_MS)

# reuse SAME seed & clock for a second, different query
ab2 = abogus.ABogus(UA, rng=LCG(42))
QUERY2 = QUERY + "&version_code=290100"
out["abogus_v2"] = ab2.get_value(QUERY2, body="", now_ms=NOW_MS)

# with a body (POST style)
ab3 = abogus.ABogus(UA, rng=LCG(7))
out["abogus_body"] = ab3.get_value("aid=6383", body="hello", now_ms=NOW_MS)

# 3) websign (x-secsdk)
pairs = [("aid", "6383"), ("aweme_id", "123"), ("a_bogus", "AbC/dE-F=")]
uifid = "UIFID_TEMP_test_1234567890"
query, signature, headers = websign.sign(pairs, uifid, timestamp=1730000000)
out["websign_query"] = query
out["websign_signature"] = signature
out["websign_headers"] = headers

with open("golden.json", "w") as f:
    json.dump(out, f, indent=1, ensure_ascii=False)
print(json.dumps(out, indent=1, ensure_ascii=False)[:1200])
