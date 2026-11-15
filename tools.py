import hashlib, json, os, shutil, struct, sys, zlib, glob

def cfg():
    d = {}
    for line in open("config.properties", encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            d[k.strip()] = v.strip()
    return d

def png(size):
    rows = []
    c = (size - 1) / 2
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            inside = (x - c) ** 2 + (y - c) ** 2 < (size * 0.28) ** 2
            row += bytes((255, 255, 255, 255) if inside else (200, 40, 60, 255))
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows))
    def chunk(t, d):
        body = t + d
        return struct.pack(">I", len(d)) + body + struct.pack(">I", zlib.crc32(body))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))

def icons():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, s in sizes.items():
        os.makedirs(f"res/mipmap-{name}", exist_ok=True)
        with open(f"res/mipmap-{name}/ic_launcher.png", "wb") as f:
            f.write(png(s))

def source_id(name, lang, version=1):
    key = f"{name.lower()}/{lang}/{version}"
    return str(int.from_bytes(hashlib.md5(key.encode()).digest()[:8], "big") & 0x7FFFFFFFFFFFFFFF)

def index():
    c = cfg()
    pkg = "eu.kanade.tachiyomi.extension." + c["pkgSuffix"]
    code = int(c["versionCode"])
    version = f"1.4.{code}"
    apks = glob.glob("build/outputs/apk/debug/*.apk")
    if not apks:
        sys.exit("No APK found")
    apk_name = f"tachiyomi-{c['pkgSuffix']}-v{version}.apk"
    shutil.rmtree("repo_out", ignore_errors=True)
    os.makedirs("repo_out/apk"); os.makedirs("repo_out/icon")
    shutil.copy(apks[0], f"repo_out/apk/{apk_name}")
    shutil.copy("res/mipmap-xxxhdpi/ic_launcher.png", f"repo_out/icon/{pkg}.png")
    entry = [{
        "name": "Tachiyomi: " + c["extName"],
        "pkg": pkg,
        "apk": apk_name,
        "lang": c["lang"],
        "code": code,
        "version": version,
        "nsfw": int(c.get("nsfw", "0")),
        "sources": [{
            "name": c["extName"],
            "lang": c["lang"],
            "id": source_id(c["extName"], c["lang"]),
            "baseUrl": c["baseUrl"],
        }],
    }]
    with open("repo_out/index.min.json", "w", encoding="utf-8") as f:
        json.dump(entry, f, separators=(",", ":"), ensure_ascii=False)

if __name__ == "__main__":
    {"icons": icons, "index": index}[sys.argv[1]]()
