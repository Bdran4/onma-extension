import glob, json, os, re, subprocess

apk = glob.glob("build/outputs/apk/debug/*.apk")[0]
fp = "0" * 64
try:
    out = subprocess.run(["keytool", "-printcert", "-jarfile", apk],
                         capture_output=True, text=True).stdout
    m = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", out)
    if m:
        fp = m.group(1).replace(":", "").lower()
except Exception:
    pass

repo = os.environ.get("GITHUB_REPOSITORY", "")
meta = {"meta": {
    "name": "Onma Repo",
    "shortName": "Onma",
    "website": "https://github.com/" + repo,
    "signingKeyFingerprint": fp,
}}
with open("repo_out/repo.json", "w", encoding="utf-8") as f:
    json.dump(meta, f, separators=(",", ":"), ensure_ascii=False)
