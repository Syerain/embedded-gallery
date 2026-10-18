#!/usr/bin/env python3
"""
Pack a natyz-style dataset (root containing zh/ and en/) into app assets.

Rules
- en is the full superset; zh preferred: gallery exists in zh -> zh files win,
  en-only files of the same gallery (e.g. videos) are still appended.
- Images are converted to lossy WebP; videos copied verbatim.
- Cover = lowest-numbered image of the merged gallery.
- Emits <dst>/media/<gid>/... and <dst>/manifest.json (dst == assets/gallery).
"""
import argparse, json, re, shutil
from pathlib import Path
from PIL import Image

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"}
VIDEO_EXTS = {".mp4", ".webm", ".mkv", ".mov", ".m4v", ".avi"}


def natural_key(s: str):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]


def slugify(s: str) -> str:
    s = re.sub(r"[^A-Za-z0-9]+", "_", s)
    return re.sub(r"_+", "_", s).strip("_")


def pretty_title(dirname: str) -> str:
    m = re.match(r"^\s*FB\s*(\d+)\s*[-_]?\s*(.*?)\s*$", dirname)
    if m:
        num, rest = m.group(1), m.group(2)
        rest = rest.replace("_", " ").strip()
        return f"FB{int(num):02d} {rest}" if rest else f"FB{int(num):02d}"
    return dirname.replace("_", " ").strip()


def list_media(d: Path):
    files = [f for f in sorted(d.iterdir()) if f.is_file() and f.suffix.lower() in (IMAGE_EXTS | VIDEO_EXTS)]
    files.sort(key=lambda f: natural_key(f.name))
    return files


def convert_image(src: Path, dst: Path, quality: int, max_dim: int):
    dst = dst.with_suffix(".webp")
    img = Image.open(src)
    w, h = img.size
    if max_dim and max(w, h) > max_dim:
        r = max_dim / max(w, h)
        img = img.resize((int(w * r), int(h * r)), Image.LANCZOS)
    img.save(dst, "WEBP", quality=quality, method=6)
    return dst, img.size


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="root containing zh/ and en/")
    ap.add_argument("--dst", required=True, help="assets output dir (e.g. app/src/main/assets/gallery)")
    ap.add_argument("--titles-zh", default=None, help="JSON map dirname -> zh title")
    ap.add_argument("--img-quality", type=int, default=90)
    ap.add_argument("--cover-max-dim", type=int, default=1600)
    ap.add_argument("--clean", action="store_true", help="wipe dst/media before packing")
    ap.add_argument("--video-dir", default=None,
                    help="directory whose video files replace ALL source videos")
    ap.add_argument("--video-gallery", default=None,
                    help="gallery directory name that receives the --video-dir videos")
    ap.add_argument("--thumbs", action="store_true",
                    help="also write small webp thumbnails (+_t) for list/grid UI")
    args = ap.parse_args()

    src = Path(args.src)
    dst = Path(args.dst)
    zh_dir, en_dir = src / "zh", src / "en"
    assert zh_dir.is_dir() and en_dir.is_dir(), f"need {zh_dir} and {en_dir}"

    zh_titles = {}
    if args.titles_zh and Path(args.titles_zh).is_file():
        zh_titles = json.loads(Path(args.titles_zh).read_text(encoding="utf-8"))

    media = dst / "media"
    if args.clean and media.exists():
        shutil.rmtree(media)
    media.mkdir(parents=True, exist_ok=True)

    gallery_dirs = sorted(
        {d.name for d in zh_dir.iterdir() if d.is_dir()} |
        {d.name for d in en_dir.iterdir() if d.is_dir()},
        key=natural_key,
    )

    manifest_galleries = []
    total_imgs = 0
    for gname in gallery_dirs:
        gid = slugify(gname)
        zh_g, en_g = zh_dir / gname, en_dir / gname
        # merged file list: zh first (by filename), en files not in zh appended
        zh_files = list_media(zh_g) if zh_g.is_dir() else []
        en_files = list_media(en_g) if en_g.is_dir() else []
        zh_names = {f.name for f in zh_files}
        merged = zh_files + [f for f in en_files if f.name not in zh_names]
        if args.video_dir:
            # videos come exclusively from --video-dir; ignore any source videos
            merged = [f for f in merged if f.suffix.lower() not in VIDEO_EXTS]
        if not merged:
            continue

        gout = media / gid
        gout.mkdir(parents=True, exist_ok=True)

        items = []
        cover_path, cover_w, cover_h = None, 3, 4
        for f in merged:
            stem = slugify(f.stem) or f.stem
            ext = f.suffix.lower()
            if ext in IMAGE_EXTS:
                out, (w, h) = convert_image(f, gout / stem, args.img_quality, 0)
                rel = f"gallery/media/{gid}/{out.name}"
                item = {
                    "id": stem, "type": "image", "path": rel, "w": w, "h": h, "title": None,
                }
                if args.thumbs:
                    img = Image.open(f)
                    tw, th = img.size
                    r = min(1.0, 1000 / max(tw, th))
                    if r < 1.0:
                        img = img.resize((int(tw * r), int(th * r)), Image.LANCZOS)
                    img.save(gout / f"{stem}_t.webp", "WEBP", quality=75, method=6)
                    item["thumb"] = f"gallery/media/{gid}/{stem}_t.webp"
                items.append(item)
                total_imgs += 1
                if cover_path is None:
                    cover_path = item.get("thumb", rel)
                    cover_w, cover_h = w, h
            elif ext in VIDEO_EXTS:
                shutil.copy2(f, gout / f.name)
                items.append({
                    "id": stem, "type": "video", "path": f"gallery/media/{gid}/{f.name}",
                    "w": 0, "h": 0, "title": f.stem,
                })
        if cover_path is None:
            # video-only gallery: use first video as "cover"
            first = items[0]
            cover_path, cover_w, cover_h = first["path"], 16, 9

        source = "zh" if zh_files else "en"
        manifest_galleries.append({
            "id": gid,
            "title": pretty_title(gname),
            "titleZh": zh_titles.get(gname, zh_titles.get(pretty_title(gname), "")),
            "cover": cover_path,
            "coverW": cover_w,
            "coverH": cover_h,
            "source": source,
            "items": items,
        })
        print(f"[{source}] {gname}: {len(items)} items")

    # ---- inject replacement videos from --video-dir into --video-gallery ----
    if args.video_dir:
        vdir = Path(args.video_dir)
        target_id = slugify(args.video_gallery or "")
        target = next((g for g in manifest_galleries if g["id"] == target_id), None)
        assert target is not None, f"video gallery {args.video_gallery!r} not found"
        for f in sorted(vdir.iterdir(), key=lambda p: natural_key(p.name)):
            if f.is_file() and f.suffix.lower() in VIDEO_EXTS:
                gout = media / target_id
                gout.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, gout / f.name)
                target["items"].append({
                    "id": slugify(f.stem) or f.stem,
                    "type": "video",
                    "path": f"gallery/media/{target_id}/{f.name}",
                    "w": 0, "h": 0, "title": f.stem,
                })
                print(f"[video] {f.name} -> {target_id}")

    manifest = {"version": 1, "galleries": manifest_galleries}
    (dst / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    print(f"\nOK: {len(manifest_galleries)} galleries, {total_imgs} images -> {dst}")


if __name__ == "__main__":
    main()
