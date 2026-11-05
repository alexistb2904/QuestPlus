"""Export real Minecraft captures to compact web media; Python/Pillow and FFmpeg required."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CAPTURE = ROOT / "build/showcase"
OUTPUT = ROOT / "docs/showcase"


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def export():
    assert "SHOWCASE PASS" in (ROOT / "build/showcase-capture.log").read_text(encoding="utf-8"), "Capture did not pass"
    ffmpeg = shutil.which("ffmpeg")
    assert ffmpeg, "FFmpeg must be installed"
    OUTPUT.mkdir(parents=True, exist_ok=True)
    sources = {
        "tablet": next((CAPTURE / "screenshots").glob("*_02-tablet-complementary.png")),
        "details": next((CAPTURE / "screenshots").glob("*_03-quest-details-complementary.png")),
        "team": next((CAPTURE / "screenshots").glob("*_04-team-complementary.png")),
        "hud": next((CAPTURE / "screenshots").glob("*_05-hud-complementary.png")),
        "studio": ROOT / "output/playwright/studio-final.png",
    }
    for name, source in sources.items():
        with Image.open(source) as image:
            assert image.width >= 1280 and image.height >= 720, f"Capture too small: {source}"
            image.convert("RGB").save(OUTPUT / f"{name}.webp", quality=92, method=6)

    clips = {name: sorted((CAPTURE / "frames" / name).glob("*.png"))
             for name in ("opening", "tree", "details", "closing")}
    assert all(len(frames) >= 16 for frames in clips.values()), "Incomplete capture sequences"
    edits = {
        "tablet": clips["opening"][:30] + clips["tree"][:12] + clips["closing"][:18],
        "quests": clips["tree"] + clips["details"],
    }
    for name, frames in edits.items():
        sequence = CAPTURE / f"{name}-concat.txt"
        lines = []
        for frame in frames + frames[-1:]:
            quoted = frame.as_posix().replace("'", "'\\''")
            lines.extend([f"file '{quoted}'", "duration 0.1"])
        sequence.write_text("\n".join(lines) + "\n", encoding="utf-8")
        run(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", sequence,
            "-filter_complex", "fps=10,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle",
            "-loop", "0", OUTPUT / f"{name}.gif")
        run(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", sequence,
            "-vf", "fps=10", "-c:v", "libx264", "-crf", "19", "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUTPUT / f"{name}.mp4")
        with Image.open(OUTPUT / f"{name}.gif") as gif:
            assert gif.n_frames >= len(frames) - 1, f"Truncated GIF: {name}"
            assert gif.info.get("loop") == 0, f"Non-looping GIF: {name}"
        assert (OUTPUT / f"{name}.gif").stat().st_size < 5 * 1024 * 1024, f"GIF too large: {name}"

    assets = json.loads((ROOT / "build/tablet-assets/manifest.json").read_text(encoding="utf-8"))
    manifest = {
        "capturedOn": "2026-11-05",
        "minecraft": "26.3", "questplus": "0.2.0", "fabricLoader": "0.20.1",
        "gpu": "NVIDIA GeForce RTX 4070 Ti SUPER",
        "playerAnimationLibrary": "1.2.8", "firstPersonPalExpansion": "1.1.1",
        "assets": {key: assets[key] for key in ("sodium", "iris", "complementary-reimagined")},
        "capture": "GPU-rendered Minecraft frames; default Complementary HIGH preset; synchronous readback watchdog compensation in capture harness only",
        "editing": "Cuts only, no generated game imagery; GIFs sampled at 10 fps, not a performance measurement",
        "files": {},
    }
    for file in sorted(OUTPUT.iterdir()):
        if file.suffix not in (".gif", ".webp", ".mp4"):
            continue
        manifest["files"][file.name] = {"bytes": file.stat().st_size, "sha256": hashlib.sha256(file.read_bytes()).hexdigest()}
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("PASS: 5 screenshots, 2 looping GIFs, 2 MP4s; frame counts, sizes and hashes verified")


if __name__ == "__main__":
    export()
