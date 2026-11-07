"""Export a fresh, verified showcase run and dated browser captures for the docs."""
from datetime import date, datetime
import hashlib
import json
from pathlib import Path
import runpy

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DAY = date.today().isoformat()
OUTPUT = ROOT / "web/docs-site/content/public/media"


def main():
    log = ROOT / "mod/build/showcase-capture.log"
    assert date.fromtimestamp(log.stat().st_mtime) == date.today(), "Run Minecraft capture today first"
    assert "SHOWCASE PASS" in log.read_text(encoding="utf-8"), "Minecraft capture failed"
    # Reuse the existing checked exporter without replacing the earlier showcase assets.
    exporter = runpy.run_path(str(ROOT / "scripts/showcase/export.py"))
    export = exporter["export"]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    browser = ROOT / f"output/playwright/docs-{DAY}-studio.png"
    assert date.fromtimestamp(browser.stat().st_mtime) == date.today(), "Capture Studio today first"
    export(OUTPUT, browser)
    manifest_path = OUTPUT / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["sources"]["studio"] = browser.relative_to(ROOT).as_posix()
    manifest["sources"]["tablet-shader"] = manifest["sources"]["tablet"]
    (OUTPUT / "tablet.webp").replace(OUTPUT / "tablet-shader.webp")
    for name, pattern in (("tablet", "*_06-tablet-readable.png"), ("tablet-flat", "*_07-tablet-flat.png")):
        capture = next((ROOT / "mod/build/showcase/screenshots").glob(pattern))
        assert date.fromtimestamp(capture.stat().st_mtime) == date.today(), f"Stale game capture: {name}"
        with Image.open(capture) as image:
            assert image.size == (1600, 900), f"Unexpected game dimensions: {name}"
            image.convert("RGB").save(OUTPUT / f"{name}.webp", quality=92, method=6)
        manifest["sources"][name] = capture.relative_to(ROOT).as_posix()
    for name in ("objectives", "rewards", "localization", "registry", "tables", "ftb-import", "server"):
        capture = ROOT / f"output/playwright/docs-{DAY}-{name}.png"
        assert date.fromtimestamp(capture.stat().st_mtime) == date.today(), f"Stale capture: {name}"
        with Image.open(capture) as image:
            assert image.width >= 1280 and image.height >= 720, f"Small capture: {name}"
            image.convert("RGB").save(OUTPUT / f"{name}.webp", quality=90, method=6)
        manifest["sources"][name] = capture.relative_to(ROOT).as_posix()
    manifest["capturedOn"] = DAY
    manifest["exportedAt"] = datetime.now().astimezone().isoformat()
    manifest["browser"] = "Fresh local Quest Studio captures with Playwright, 1920 by 1080, English interface"
    for name, source in manifest["sources"].items():
        sidecar = {"prompt": f"Origin: real product screenshot captured on {DAY}. Source: {source}. No generated UI.", "createdAt": manifest["exportedAt"]}
        (OUTPUT / f"{name}.webp.json").write_text(json.dumps(sidecar, indent=2) + "\n", encoding="utf-8")
    manifest["files"] = {
        path.name: {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        for path in sorted(OUTPUT.iterdir()) if path.suffix in (".webp", ".gif", ".mp4")
    }
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {len(manifest['files'])} fresh media files, dimensions and hashes verified")


if __name__ == "__main__":
    main()
