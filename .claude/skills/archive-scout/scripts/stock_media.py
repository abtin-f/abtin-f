#!/usr/bin/env python3
"""
stock_media.py — search and download royalty-free media from Pexels, Pixabay and Coverr.

Standard library only. No pip install required.

Usage examples:
    python3 stock_media.py keys
    python3 stock_media.py setup --pexels KEY --pixabay KEY --coverr KEY
    python3 stock_media.py search "coffee shop" --type photo --limit 5
    python3 stock_media.py search "city night" --type video --source coverr --limit 3
    python3 stock_media.py search "arrow icon" --type vector --transparent
    python3 stock_media.py search "ocean" --type video --download ./assets --limit 2
    python3 stock_media.py get pexels 2014422 --type photo
    python3 stock_media.py download <url> ./assets --source coverr --id S1YbPl1NfI
    python3 stock_media.py collections --source pexels
    python3 stock_media.py collection-media <id> --source coverr
    python3 stock_media.py categories
    python3 stock_media.py quota
"""

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# --------------------------------------------------------------------------
# API keys.
#
# Resolved per source, in this order:
#   1. environment variable  (PEXELS_API_KEY / PIXABAY_API_KEY / COVERR_API_KEY)
#   2. config file           (~/.config/stock-media/config.json, written by `setup`)
#   3. bundled default       (the constants below)
#
# To share this skill without giving away your own keys, blank the three
# BUNDLED_KEYS values. Recipients then run `setup` once and their keys persist
# in their own config file.
# --------------------------------------------------------------------------
BUNDLED_KEYS = {
    "pexels": "",
    "pixabay": "",
    "coverr": "",
}

SOURCES = ("pexels", "pixabay", "coverr")

ENV_VARS = {
    "pexels": "PEXELS_API_KEY",
    "pixabay": "PIXABAY_API_KEY",
    "coverr": "COVERR_API_KEY",
}

SIGNUP_URLS = {
    "pexels": "https://www.pexels.com/api/new/ (instant, free)",
    "pixabay": "https://pixabay.com/accounts/register/ then the key is shown at "
               "https://pixabay.com/api/docs/ (instant, free)",
    "coverr": "email team@coverr.co describing your use case (manual approval)",
}

CONFIG_PATH = os.environ.get(
    "STOCK_MEDIA_CONFIG",
    os.path.join(os.path.expanduser("~"), ".config", "stock-media", "config.json"),
)

_config_cache = None


def load_config():
    global _config_cache
    if _config_cache is None:
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as fh:
                loaded = json.load(fh)
            _config_cache = loaded if isinstance(loaded, dict) else {}
        except (OSError, ValueError):
            _config_cache = {}
    return _config_cache


def save_config(updates):
    """Merge keys into the config file. Written 0600 — it holds secrets."""
    global _config_cache
    cfg = dict(load_config())
    for source, key in updates.items():
        if key:
            cfg[source] = key.strip()
    directory = os.path.dirname(CONFIG_PATH)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(CONFIG_PATH, "w", encoding="utf-8") as fh:
        json.dump(cfg, fh, indent=2)
    try:
        os.chmod(CONFIG_PATH, 0o600)
    except OSError:
        pass
    _config_cache = cfg
    return cfg


def resolve_key(source):
    """Return (key, human-readable origin). Either may be None."""
    env_name = ENV_VARS[source]
    env_val = os.environ.get(env_name)
    if env_val:
        return env_val, f"environment variable {env_name}"
    cfg_val = load_config().get(source)
    if cfg_val:
        return cfg_val, f"config file {CONFIG_PATH}"
    if not os.environ.get("STOCK_MEDIA_IGNORE_BUNDLED"):
        bundled = BUNDLED_KEYS.get(source)
        if bundled:
            return bundled, "bundled default"
    return None, None


def get_key(source):
    key, _ = resolve_key(source)
    if not key:
        raise SystemExit(
            f"NO_KEY:{source} — no API key configured for {source}.\n"
            f"Get one: {SIGNUP_URLS[source]}\n"
            f"Then save it once with:\n"
            f"    python3 stock_media.py setup --{source} YOUR_KEY\n"
            f"It will be stored in {CONFIG_PATH} and reused from then on."
        )
    return key


def mask(key):
    if not key:
        return None
    if len(key) <= 10:
        return "*" * len(key)
    return f"{key[:4]}…{key[-4:]}"

PEXELS_BASE = "https://api.pexels.com/v1"
PIXABAY_IMAGE_BASE = "https://pixabay.com/api/"
PIXABAY_VIDEO_BASE = "https://pixabay.com/api/videos/"
COVERR_BASE = "https://api.coverr.co"

USER_AGENT = "stock-media-skill/1.0"
TIMEOUT = 45

# Pixabay's terms require responses be cached for 24h. Cache everything;
# it also keeps us well inside Pexels' 200/hour budget.
CACHE_DIR = os.environ.get(
    "STOCK_MEDIA_CACHE", os.path.join(os.path.expanduser("~"), ".cache", "stock-media")
)
CACHE_TTL = 24 * 60 * 60

# Last-seen rate limit headers, surfaced by the `quota` command.
_LAST_RATE_HEADERS = {}


# --------------------------------------------------------------------------
# HTTP plumbing
# --------------------------------------------------------------------------
def _cache_path(url, headers):
    key = hashlib.sha256(
        (url + json.dumps(sorted(headers.items()))).encode("utf-8")
    ).hexdigest()
    return os.path.join(CACHE_DIR, key + ".json")


def _read_cache(path):
    try:
        if time.time() - os.path.getmtime(path) < CACHE_TTL:
            with open(path, "r", encoding="utf-8") as fh:
                return json.load(fh)
    except (OSError, ValueError):
        pass
    return None


def _write_cache(path, payload):
    try:
        os.makedirs(CACHE_DIR, exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(payload, fh)
    except OSError:
        pass  # cache failures should never break a search


def http_json(url, headers=None, use_cache=True, retries=3):
    """GET a URL and parse JSON, with 24h caching and 429 backoff."""
    headers = dict(headers or {})
    headers.setdefault("User-Agent", USER_AGENT)

    path = _cache_path(url, headers)
    if use_cache:
        cached = _read_cache(path)
        if cached is not None:
            return cached

    delay = 2
    last_err = None
    for attempt in range(retries):
        req = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                for h in ("X-Ratelimit-Limit", "X-Ratelimit-Remaining",
                          "X-Ratelimit-Reset", "X-RateLimit-Limit",
                          "X-RateLimit-Remaining", "X-RateLimit-Reset"):
                    if resp.headers.get(h):
                        _LAST_RATE_HEADERS[h] = resp.headers.get(h)
                data = json.loads(resp.read().decode("utf-8"))
            if use_cache:
                _write_cache(path, data)
            return data
        except urllib.error.HTTPError as exc:
            body = ""
            try:
                body = exc.read().decode("utf-8", "replace")[:300]
            except Exception:
                pass
            if exc.code == 429 and attempt < retries - 1:
                time.sleep(delay)
                delay *= 2
                continue
            if exc.code in (401, 403):
                src = _source_for_host(_host(url))
                hint = ""
                if src:
                    _, origin = resolve_key(src)
                    hint = (f"\nThe {src} key currently in use came from {origin}. "
                            f"Replace it with:\n"
                            f"    python3 stock_media.py setup --{src} YOUR_KEY\n"
                            f"Get a key: {SIGNUP_URLS[src]}")
                raise SystemExit(
                    f"BAD_KEY:{src or 'unknown'} — auth/access error {exc.code} "
                    f"from {_host(url)}.{hint}\nResponse: {body}"
                )
            if exc.code == 429:
                raise SystemExit(
                    f"Rate limited by {_host(url)}. Wait and retry, "
                    f"or narrow the search. Response: {body}"
                )
            last_err = f"HTTP {exc.code} from {_host(url)}: {body}"
        except urllib.error.URLError as exc:
            last_err = f"Network error reaching {_host(url)}: {exc.reason}"
            if attempt < retries - 1:
                time.sleep(delay)
                delay *= 2
                continue
    raise SystemExit(last_err or "Request failed")


def _host(url):
    return urllib.parse.urlparse(url).netloc


def _source_for_host(host):
    if "pexels" in host:
        return "pexels"
    if "pixabay" in host:
        return "pixabay"
    if "coverr" in host:
        return "coverr"
    return None


def _qs(params):
    clean = {k: v for k, v in params.items() if v not in (None, "", [])}
    return urllib.parse.urlencode(clean)


# --------------------------------------------------------------------------
# Normalized result shape
# --------------------------------------------------------------------------
def make_result(**kw):
    """Every source is flattened into this one shape."""
    base = {
        "source": None,        # pexels | pixabay | coverr
        "id": None,
        "type": None,          # photo | illustration | vector | video
        "title": None,
        "tags": [],
        "width": None,
        "height": None,
        "duration": None,      # seconds, videos only
        "orientation": None,   # landscape | portrait | square
        "page_url": None,      # human-viewable source page
        "preview_url": None,   # small image / poster frame
        "download_url": None,  # the file to actually fetch
        "author": None,
        "author_url": None,
        "attribution": None,   # ready-to-paste credit line
        "license_note": None,
    }
    base.update(kw)
    return base


def _orient(w, h):
    if not w or not h:
        return None
    if w > h * 1.05:
        return "landscape"
    if h > w * 1.05:
        return "portrait"
    return "square"


# --------------------------------------------------------------------------
# Pexels
# --------------------------------------------------------------------------
def _pexels_headers():
    return {"Authorization": get_key("pexels")}


def pexels_photo_to_result(p):
    src = p.get("src", {})
    author = p.get("photographer")
    return make_result(
        source="pexels",
        id=p.get("id"),
        type="photo",
        title=p.get("alt") or None,
        width=p.get("width"),
        height=p.get("height"),
        orientation=_orient(p.get("width"), p.get("height")),
        page_url=p.get("url"),
        preview_url=src.get("medium") or src.get("small"),
        download_url=src.get("original"),
        author=author,
        author_url=p.get("photographer_url"),
        attribution=f"Photo by {author} on Pexels" if author else "Photo on Pexels",
        license_note="Free to use. Credit Pexels and the photographer where possible.",
    )


def pexels_video_to_result(v):
    files = [f for f in v.get("video_files", []) if f.get("quality") != "hls"]
    files.sort(key=lambda f: (f.get("width") or 0) * (f.get("height") or 0), reverse=True)
    best = files[0] if files else {}
    user = v.get("user", {}) or {}
    author = user.get("name")
    pics = v.get("video_pictures") or []
    return make_result(
        source="pexels",
        id=v.get("id"),
        type="video",
        title=v.get("url", "").rstrip("/").split("/")[-1].replace("-", " ") or None,
        tags=v.get("tags") or [],
        width=v.get("width"),
        height=v.get("height"),
        duration=v.get("duration"),
        orientation=_orient(v.get("width"), v.get("height")),
        page_url=v.get("url"),
        preview_url=v.get("image") or (pics[0].get("picture") if pics else None),
        download_url=best.get("link"),
        author=author,
        author_url=user.get("url"),
        attribution=f"Video by {author} on Pexels" if author else "Video on Pexels",
        license_note="Free to use. Credit Pexels and the videographer where possible.",
    )


def pexels_search(query, media_type, limit=10, page=1, orientation=None,
                  size=None, color=None, locale=None,
                  min_duration=None, max_duration=None):
    per_page = min(max(limit, 1), 80)
    if media_type == "video":
        url = f"{PEXELS_BASE}/videos/search?" + _qs({
            "query": query, "per_page": per_page, "page": page,
            "orientation": orientation, "size": size, "locale": locale,
        })
        data = http_json(url, _pexels_headers())
        results = [pexels_video_to_result(v) for v in data.get("videos", [])]
        if min_duration is not None:
            results = [r for r in results if (r["duration"] or 0) >= min_duration]
        if max_duration is not None:
            results = [r for r in results if (r["duration"] or 0) <= max_duration]
        return results
    url = f"{PEXELS_BASE}/search?" + _qs({
        "query": query, "per_page": per_page, "page": page,
        "orientation": orientation, "size": size, "color": color, "locale": locale,
    })
    data = http_json(url, _pexels_headers())
    return [pexels_photo_to_result(p) for p in data.get("photos", [])]


def pexels_curated(media_type, limit=10, page=1, min_duration=None, max_duration=None):
    per_page = min(max(limit, 1), 80)
    if media_type == "video":
        url = f"{PEXELS_BASE}/videos/popular?" + _qs({
            "per_page": per_page, "page": page,
            "min_duration": min_duration, "max_duration": max_duration,
        })
        data = http_json(url, _pexels_headers())
        return [pexels_video_to_result(v) for v in data.get("videos", [])]
    url = f"{PEXELS_BASE}/curated?" + _qs({"per_page": per_page, "page": page})
    data = http_json(url, _pexels_headers())
    return [pexels_photo_to_result(p) for p in data.get("photos", [])]


def pexels_get(media_id, media_type):
    if media_type == "video":
        data = http_json(f"{PEXELS_BASE}/videos/videos/{media_id}", _pexels_headers())
        return pexels_video_to_result(data)
    data = http_json(f"{PEXELS_BASE}/photos/{media_id}", _pexels_headers())
    return pexels_photo_to_result(data)


def pexels_collections(featured=True, limit=20, page=1):
    path = "collections/featured" if featured else "collections"
    url = f"{PEXELS_BASE}/{path}?" + _qs({"per_page": min(limit, 80), "page": page})
    data = http_json(url, _pexels_headers())
    return [{
        "source": "pexels",
        "id": c.get("id"),
        "title": c.get("title"),
        "description": c.get("description"),
        "media_count": c.get("media_count"),
        "photos_count": c.get("photos_count"),
        "videos_count": c.get("videos_count"),
    } for c in data.get("collections", [])]


def pexels_collection_media(collection_id, media_type=None, limit=20, page=1, sort="asc"):
    type_param = {"photo": "photos", "video": "videos"}.get(media_type)
    url = f"{PEXELS_BASE}/collections/{collection_id}?" + _qs({
        "type": type_param, "per_page": min(limit, 80), "page": page, "sort": sort,
    })
    data = http_json(url, _pexels_headers())
    out = []
    for m in data.get("media", []):
        if m.get("type") == "Video":
            out.append(pexels_video_to_result(m))
        else:
            out.append(pexels_photo_to_result(m))
    return out


# --------------------------------------------------------------------------
# Pixabay
# --------------------------------------------------------------------------
def pixabay_image_to_result(h):
    tags = [t.strip() for t in (h.get("tags") or "").split(",") if t.strip()]
    author = h.get("user")
    return make_result(
        source="pixabay",
        id=h.get("id"),
        type=h.get("type", "photo").split("/")[0],
        title=", ".join(tags[:3]) or None,
        tags=tags,
        width=h.get("imageWidth"),
        height=h.get("imageHeight"),
        orientation=_orient(h.get("imageWidth"), h.get("imageHeight")),
        page_url=h.get("pageURL"),
        preview_url=h.get("previewURL"),
        # fullHDURL/imageURL need full API approval; largeImageURL always works.
        download_url=h.get("fullHDURL") or h.get("largeImageURL") or h.get("webformatURL"),
        author=author,
        author_url=f"https://pixabay.com/users/{author}-{h.get('user_id')}/" if author else None,
        attribution=f"Image by {author} on Pixabay" if author else "Image on Pixabay",
        license_note="Pixabay Content License. Show where results came from. "
                     "Do not permanently hotlink; download to your own storage.",
    )


def pixabay_video_to_result(h):
    tags = [t.strip() for t in (h.get("tags") or "").split(",") if t.strip()]
    streams = h.get("videos", {}) or {}
    best, best_px = None, -1
    for name in ("large", "medium", "small", "tiny"):
        s = streams.get(name) or {}
        if s.get("url"):
            px = (s.get("width") or 0) * (s.get("height") or 0)
            if px > best_px:
                best, best_px = s, px
    best = best or {}
    author = h.get("user")
    return make_result(
        source="pixabay",
        id=h.get("id"),
        type="video",
        title=", ".join(tags[:3]) or None,
        tags=tags,
        width=best.get("width"),
        height=best.get("height"),
        duration=h.get("duration"),
        orientation=_orient(best.get("width"), best.get("height")),
        page_url=h.get("pageURL"),
        preview_url=best.get("thumbnail"),
        download_url=best.get("url"),
        author=author,
        author_url=f"https://pixabay.com/users/{author}-{h.get('user_id')}/" if author else None,
        attribution=f"Video by {author} on Pixabay" if author else "Video on Pixabay",
        license_note="Pixabay Content License. Show where results came from.",
    )


def pixabay_search(query, media_type, limit=10, page=1, orientation=None,
                   category=None, colors=None, min_width=None, min_height=None,
                   editors_choice=False, safesearch=True, order="popular", lang=None):
    per_page = min(max(limit, 3), 200)  # Pixabay rejects per_page < 3
    if media_type == "video":
        url = PIXABAY_VIDEO_BASE + "?" + _qs({
            "key": get_key("pixabay"), "q": query, "page": page, "per_page": per_page,
            "category": category, "min_width": min_width, "min_height": min_height,
            "editors_choice": "true" if editors_choice else None,
            "safesearch": "true" if safesearch else None,
            "order": order, "lang": lang,
        })
        data = http_json(url)
        return [pixabay_video_to_result(h) for h in data.get("hits", [])]

    image_type = {"photo": "photo", "illustration": "illustration",
                  "vector": "vector"}.get(media_type, "all")
    url = PIXABAY_IMAGE_BASE + "?" + _qs({
        "key": get_key("pixabay"), "q": query, "page": page, "per_page": per_page,
        "image_type": image_type, "orientation": orientation, "category": category,
        "colors": colors, "min_width": min_width, "min_height": min_height,
        "editors_choice": "true" if editors_choice else None,
        "safesearch": "true" if safesearch else None,
        "order": order, "lang": lang,
    })
    data = http_json(url)
    return [pixabay_image_to_result(h) for h in data.get("hits", [])]


def pixabay_get(media_id, media_type):
    base = PIXABAY_VIDEO_BASE if media_type == "video" else PIXABAY_IMAGE_BASE
    url = base + "?" + _qs({"key": get_key("pixabay"), "id": media_id})
    data = http_json(url)
    hits = data.get("hits", [])
    if not hits:
        raise SystemExit(f"No Pixabay {media_type} with id {media_id}")
    return (pixabay_video_to_result if media_type == "video"
            else pixabay_image_to_result)(hits[0])


# --------------------------------------------------------------------------
# Coverr
# --------------------------------------------------------------------------
def coverr_video_to_result(v):
    urls = v.get("urls", {}) or {}
    return make_result(
        source="coverr",
        id=v.get("id"),
        type="video",
        title=v.get("title"),
        tags=v.get("tags") or [],
        width=v.get("max_width"),
        height=v.get("max_height"),
        duration=v.get("duration"),
        orientation="portrait" if v.get("is_vertical") else _orient(
            v.get("max_width"), v.get("max_height")),
        page_url=f"https://coverr.co/videos/{v.get('id')}",
        preview_url=v.get("thumbnail") or v.get("poster"),
        download_url=urls.get("mp4_download") or urls.get("mp4"),
        author="Coverr",
        author_url="https://coverr.co",
        attribution="Video from Coverr",
        license_note="Coverr license (coverr.co/license). Ping the download-stats "
                     "endpoint whenever a video is actually downloaded.",
    )


def coverr_search(query=None, limit=10, page=0, sort="popular", vertical=None,
                  min_duration=None, max_duration=None):
    url = f"{COVERR_BASE}/videos?" + _qs({
        "api_key": get_key("coverr"), "query": query,
        "page_size": min(max(limit, 1), 50), "page": page,
        "sort": sort, "urls": "true",
    })
    data = http_json(url)
    results = [coverr_video_to_result(v) for v in data.get("hits", [])]
    if vertical is True:
        results = [r for r in results if r["orientation"] == "portrait"]
    elif vertical is False:
        results = [r for r in results if r["orientation"] != "portrait"]
    if min_duration is not None:
        results = [r for r in results if (r["duration"] or 0) >= min_duration]
    if max_duration is not None:
        results = [r for r in results if (r["duration"] or 0) <= max_duration]
    return results


def coverr_get(video_id):
    url = f"{COVERR_BASE}/videos/{video_id}?" + _qs({"api_key": get_key("coverr")})
    return coverr_video_to_result(http_json(url))


def coverr_collections(limit=20, page=0):
    url = f"{COVERR_BASE}/collections?" + _qs({
        "api_key": get_key("coverr"), "page_size": limit, "page": page})
    data = http_json(url)
    return [{
        "source": "coverr",
        "id": c.get("id"),
        "title": c.get("title"),
        "slug": c.get("slug"),
        "description": c.get("description"),
        "video_count": len(c.get("videos") or []),
        "tags": c.get("tags") or [],
    } for c in data.get("hits", [])]


def coverr_categories(limit=30, page=0):
    url = f"{COVERR_BASE}/categories?" + _qs({
        "api_key": get_key("coverr"), "page_size": limit, "page": page})
    data = http_json(url)
    return [{
        "source": "coverr",
        "id": c.get("id"),
        "name": c.get("name"),
        "slug": c.get("slug"),
        "description": c.get("description"),
        "tags": c.get("tags") or [],
    } for c in data.get("hits", [])]


def coverr_collection_videos(collection_id, limit=20, page=0, query=None):
    url = f"{COVERR_BASE}/collections/{collection_id}/videos?" + _qs({
        "api_key": get_key("coverr"), "page_size": limit, "page": page,
        "query": query, "urls": "true"})
    return [coverr_video_to_result(v) for v in http_json(url).get("hits", [])]


def coverr_category_videos(category_id, limit=20, page=0, query=None):
    url = f"{COVERR_BASE}/categories/{category_id}/videos?" + _qs({
        "api_key": get_key("coverr"), "page_size": limit, "page": page,
        "query": query, "urls": "true"})
    return [coverr_video_to_result(v) for v in http_json(url).get("hits", [])]


def coverr_ping_download(video_id):
    """Coverr requires this after an actual download. Never fatal."""
    url = f"{COVERR_BASE}/videos/{video_id}/stats/downloads?" + _qs(
        {"api_key": get_key("coverr")})
    req = urllib.request.Request(url, method="PATCH",
                                 headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT):
            return True
    except Exception:
        return False


# --------------------------------------------------------------------------
# Source routing
# --------------------------------------------------------------------------
ROUTING = {
    # media type -> ordered source preference
    "photo":        ["pexels", "pixabay"],
    "illustration": ["pixabay"],
    "vector":       ["pixabay"],
    "video":        ["pexels", "pixabay", "coverr"],
    "background":   ["coverr", "pexels"],   # loops / hero footage
}


def route(media_type, source, transparent=False, color=None):
    """Return the ordered list of sources to try."""
    if source and source != "auto":
        return [source]
    # Only Pixabay supports transparency and named colour filters on images.
    if media_type in ("photo", "illustration", "vector") and (transparent or color):
        if transparent:
            return ["pixabay"]
        return ["pixabay", "pexels"]
    return ROUTING.get(media_type, ["pexels", "pixabay", "coverr"])


def search_source(src, args):
    """Dispatch one source with the arguments it understands."""
    mtype = "video" if args.type == "background" else args.type
    if src == "pexels":
        if mtype in ("illustration", "vector"):
            return []  # Pexels has no such category
        if not args.query:
            return pexels_curated(mtype, args.limit, args.page,
                                  args.min_duration, args.max_duration)
        return pexels_search(
            args.query, mtype, args.limit, args.page,
            orientation=args.orientation, size=args.size, color=args.color,
            locale=args.locale,
            min_duration=args.min_duration, max_duration=args.max_duration)
    if src == "pixabay":
        colors = "transparent" if args.transparent else args.color
        return pixabay_search(
            args.query or "", mtype, args.limit, args.page,
            orientation=args.orientation, category=args.category, colors=colors,
            min_width=args.min_width, min_height=args.min_height,
            editors_choice=args.editors_choice, safesearch=not args.no_safesearch,
            order=args.order, lang=args.lang)
    if src == "coverr":
        if mtype != "video":
            return []  # Coverr is video-only
        vertical = None
        if args.orientation == "portrait":
            vertical = True
        elif args.orientation in ("landscape", "square"):
            vertical = False
        return coverr_search(args.query, args.limit,
                             args.page if args.page else 0,
                             sort=args.order if args.order in
                             ("date", "popular", "trending") else "popular",
                             vertical=vertical,
                             min_duration=args.min_duration,
                             max_duration=args.max_duration)
    raise SystemExit(f"Unknown source: {src}")


# --------------------------------------------------------------------------
# Downloading
# --------------------------------------------------------------------------
def slugify(text, fallback="media"):
    text = re.sub(r"[^\w\s-]", "", (text or "")).strip().lower()
    text = re.sub(r"[\s_-]+", "-", text)
    return text[:60] or fallback


def guess_ext(url, media_type):
    path = urllib.parse.urlparse(url).path.lower()
    for ext in (".jpg", ".jpeg", ".png", ".svg", ".mp4", ".webm", ".mov", ".gif"):
        if path.endswith(ext):
            return ext
    return ".mp4" if media_type == "video" else ".jpg"


def download_file(url, dest_path):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp, \
            open(dest_path, "wb") as fh:
        while True:
            chunk = resp.read(1 << 16)
            if not chunk:
                break
            fh.write(chunk)
    return os.path.getsize(dest_path)


def download_results(results, folder):
    os.makedirs(folder, exist_ok=True)
    saved = []
    for i, r in enumerate(results, 1):
        url = r.get("download_url")
        if not url:
            r["download_error"] = "no download URL available"
            continue
        name = f"{r['source']}-{r['id']}-{slugify(r.get('title'), str(i))}"
        path = os.path.join(folder, name + guess_ext(url, r.get("type")))
        try:
            size = download_file(url, path)
        except Exception as exc:
            r["download_error"] = str(exc)
            continue
        r["local_path"] = path
        r["bytes"] = size
        # Coverr asks that real downloads be reported back.
        if r["source"] == "coverr":
            r["stats_pinged"] = coverr_ping_download(r["id"])
        saved.append(r)
    write_credits(saved, folder)
    return saved


def write_credits(results, folder):
    if not results:
        return
    path = os.path.join(folder, "CREDITS.md")
    existing = ""
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as fh:
            existing = fh.read()
    lines = [] if existing else ["# Media credits\n"]
    for r in results:
        fname = os.path.basename(r.get("local_path", ""))
        if fname and fname in existing:
            continue
        lines.append(
            f"- `{fname}` — {r.get('attribution')} "
            f"([source]({r.get('page_url')}))"
        )
    if lines:
        with open(path, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")


# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------
def emit(payload, as_table=False):
    if as_table and isinstance(payload, list):
        for r in payload:
            bits = [f"[{r.get('source')}] {r.get('id')}", r.get("title") or ""]
            if r.get("width"):
                bits.append(f"{r['width']}x{r['height']}")
            if r.get("duration"):
                bits.append(f"{r['duration']}s")
            if r.get("local_path"):
                bits.append(f"-> {r['local_path']}")
            print(" | ".join(b for b in bits if b))
        return
    print(json.dumps(payload, indent=2, ensure_ascii=False))


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def build_parser():
    p = argparse.ArgumentParser(
        prog="stock_media.py",
        description="Search and download royalty-free media from Pexels, Pixabay and Coverr.")
    sub = p.add_subparsers(dest="command", required=True)

    s = sub.add_parser("search", help="search one or more sources")
    s.add_argument("query", nargs="?", default=None,
                   help="search term; omit for curated/popular results")
    s.add_argument("--type", default="photo",
                   choices=["photo", "illustration", "vector", "video", "background"],
                   help="'background' routes to Coverr for hero/loop footage")
    s.add_argument("--source", default="auto",
                   choices=["auto", "pexels", "pixabay", "coverr"])
    s.add_argument("--limit", type=int, default=10)
    s.add_argument("--page", type=int, default=1)
    s.add_argument("--orientation", choices=["landscape", "portrait", "square"])
    s.add_argument("--size", choices=["large", "medium", "small"],
                   help="Pexels minimum size")
    s.add_argument("--color", help="colour name, or hex for Pexels photos")
    s.add_argument("--transparent", action="store_true",
                   help="transparent background (Pixabay only)")
    s.add_argument("--category", help="Pixabay category")
    s.add_argument("--min-width", type=int, dest="min_width")
    s.add_argument("--min-height", type=int, dest="min_height")
    s.add_argument("--min-duration", type=int, dest="min_duration")
    s.add_argument("--max-duration", type=int, dest="max_duration")
    s.add_argument("--editors-choice", action="store_true", dest="editors_choice")
    s.add_argument("--no-safesearch", action="store_true", dest="no_safesearch")
    s.add_argument("--order", default="popular",
                   help="popular | latest (Pixabay), date | popular | trending (Coverr)")
    s.add_argument("--lang", help="Pixabay search language code, e.g. ar, en")
    s.add_argument("--locale", help="Pexels locale, e.g. ar not supported; use en-US")
    s.add_argument("--all-sources", action="store_true",
                   help="query every compatible source and merge")
    s.add_argument("--download", metavar="FOLDER",
                   help="download the results into FOLDER")
    s.add_argument("--table", action="store_true", help="compact text output")
    s.add_argument("--no-cache", action="store_true")

    g = sub.add_parser("get", help="fetch one item by source + id")
    g.add_argument("source", choices=["pexels", "pixabay", "coverr"])
    g.add_argument("id")
    g.add_argument("--type", default="photo", choices=["photo", "video"])
    g.add_argument("--download", metavar="FOLDER")
    g.add_argument("--table", action="store_true")

    d = sub.add_parser("download", help="download a known URL")
    d.add_argument("url")
    d.add_argument("folder")
    d.add_argument("--name", help="base filename without extension")
    d.add_argument("--source", choices=["pexels", "pixabay", "coverr"])
    d.add_argument("--id", help="item id; required to report Coverr download stats")

    c = sub.add_parser("collections", help="list collections")
    c.add_argument("--source", default="pexels", choices=["pexels", "coverr"])
    c.add_argument("--mine", action="store_true", help="Pexels: your own collections")
    c.add_argument("--limit", type=int, default=20)
    c.add_argument("--page", type=int, default=1)
    c.add_argument("--table", action="store_true")

    cm = sub.add_parser("collection-media", help="list media inside a collection")
    cm.add_argument("id")
    cm.add_argument("--source", default="pexels", choices=["pexels", "coverr"])
    cm.add_argument("--type", choices=["photo", "video"])
    cm.add_argument("--limit", type=int, default=20)
    cm.add_argument("--page", type=int, default=1)
    cm.add_argument("--download", metavar="FOLDER")
    cm.add_argument("--table", action="store_true")

    cat = sub.add_parser("categories", help="list Coverr categories")
    cat.add_argument("--limit", type=int, default=30)
    cat.add_argument("--table", action="store_true")

    cv = sub.add_parser("category-videos", help="list videos in a Coverr category")
    cv.add_argument("id", help="category id or slug")
    cv.add_argument("--query")
    cv.add_argument("--limit", type=int, default=20)
    cv.add_argument("--download", metavar="FOLDER")
    cv.add_argument("--table", action="store_true")

    sub.add_parser("quota", help="show last-seen rate limit headers")

    st = sub.add_parser(
        "setup", help="save API keys once; they persist for future sessions")
    st.add_argument("--pexels")
    st.add_argument("--pixabay")
    st.add_argument("--coverr")
    st.add_argument("--path", action="store_true",
                    help="just print the config file location")

    k = sub.add_parser(
        "keys", help="show which sources have a key and where it came from")
    k.add_argument("--json", action="store_true", dest="as_json")
    return p


def cmd_search(args):
    if args.no_cache:
        globals()["CACHE_TTL"] = 0
    sources = (route(args.type, "auto", args.transparent, args.color)
               if args.all_sources else
               route(args.type, args.source, args.transparent, args.color))
    results, errors = [], []
    for src in sources:
        try:
            found = search_source(src, args)
        except SystemExit as exc:
            errors.append(f"{src}: {exc}")
            continue
        results.extend(found)
        if found and not args.all_sources:
            break  # first source that returns something wins
    if not results:
        key_errors = [e for e in errors if "NO_KEY:" in e or "BAD_KEY:" in e]
        if key_errors and len(key_errors) == len(sources):
            # Every source failed on credentials — that's a setup problem, not a
            # search problem. "Try a broader query" would send people the wrong way.
            emit({
                "results": [],
                "problem": "no usable API key for any source this search needed",
                "sources_tried": sources,
                "errors": key_errors,
                "fix": "Get a key for one of the sources above, then save it once "
                       "with `setup --<source> YOUR_KEY`. It persists after that.",
            })
            return 2
        msg = {"results": [], "sources_tried": sources, "errors": errors,
               "hint": "Try a broader query, a different --type, or --all-sources."}
        emit(msg)
        return 1
    results = results[:args.limit] if not args.all_sources else results
    if args.download:
        results = download_results(results, args.download)
    emit(results, args.table)
    if errors:
        print("\n".join("warning: " + e for e in errors), file=sys.stderr)
    return 0


def cmd_get(args):
    if args.source == "pexels":
        r = pexels_get(args.id, args.type)
    elif args.source == "pixabay":
        r = pixabay_get(args.id, args.type)
    else:
        r = coverr_get(args.id)
    out = [r]
    if args.download:
        out = download_results(out, args.download)
    emit(out, args.table)
    return 0


def cmd_download(args):
    os.makedirs(args.folder, exist_ok=True)
    base = args.name or slugify(os.path.basename(
        urllib.parse.urlparse(args.url).path)) or "media"
    path = os.path.join(args.folder, base + guess_ext(args.url, None))
    size = download_file(args.url, path)
    result = {"local_path": path, "bytes": size}
    if args.source == "coverr" and args.id:
        result["stats_pinged"] = coverr_ping_download(args.id)
    emit(result)
    return 0


def cmd_collections(args):
    if args.source == "pexels":
        emit(pexels_collections(not args.mine, args.limit, args.page), args.table)
    else:
        emit(coverr_collections(args.limit, max(args.page - 1, 0)), args.table)
    return 0


def cmd_collection_media(args):
    if args.source == "pexels":
        res = pexels_collection_media(args.id, args.type, args.limit, args.page)
    else:
        res = coverr_collection_videos(args.id, args.limit, max(args.page - 1, 0))
    if args.download:
        res = download_results(res, args.download)
    emit(res, args.table)
    return 0


def cmd_categories(args):
    emit(coverr_categories(args.limit), args.table)
    return 0


def cmd_category_videos(args):
    res = coverr_category_videos(args.id, args.limit, 0, args.query)
    if args.download:
        res = download_results(res, args.download)
    emit(res, args.table)
    return 0


def cmd_setup(args):
    if args.path:
        emit({"config_path": CONFIG_PATH})
        return 0
    updates = {"pexels": args.pexels, "pixabay": args.pixabay,
               "coverr": args.coverr}
    if not any(updates.values()):
        print("Nothing to save. Provide at least one key, e.g.:\n"
              "    python3 stock_media.py setup --pexels YOUR_KEY\n\n"
              "Where to get keys:", file=sys.stderr)
        for s in SOURCES:
            print(f"  {s:8} {SIGNUP_URLS[s]}", file=sys.stderr)
        return 1
    save_config(updates)
    emit({
        "saved_to": CONFIG_PATH,
        "updated": [s for s, v in updates.items() if v],
        "note": "Stored with 0600 permissions. These keys are reused "
                "automatically from now on; no need to run setup again.",
    })
    return 0


def cmd_keys(args):
    status = {}
    for s in SOURCES:
        key, origin = resolve_key(s)
        status[s] = {
            "configured": bool(key),
            "key": mask(key),
            "origin": origin,
            "get_a_key": None if key else SIGNUP_URLS[s],
        }
    payload = {"config_path": CONFIG_PATH, "sources": status}
    if args.as_json:
        emit(payload)
        return 0
    print(f"config file: {CONFIG_PATH}"
          f"{'' if os.path.exists(CONFIG_PATH) else '  (not created yet)'}\n")
    for s in SOURCES:
        st = status[s]
        if st["configured"]:
            print(f"  {s:8} OK    {st['key']}  via {st['origin']}")
        else:
            print(f"  {s:8} MISSING — {st['get_a_key']}")
    return 0


def cmd_quota(args):
    emit(_LAST_RATE_HEADERS or {
        "note": "Rate limit headers appear only after a live request in this process. "
                "Pexels: 200/hour, 20000/month. Pixabay: 100/minute. "
                "Coverr: no published limit."})
    return 0


def main(argv=None):
    args = build_parser().parse_args(argv)
    handlers = {
        "search": cmd_search, "get": cmd_get, "download": cmd_download,
        "collections": cmd_collections, "collection-media": cmd_collection_media,
        "categories": cmd_categories, "category-videos": cmd_category_videos,
        "quota": cmd_quota, "setup": cmd_setup, "keys": cmd_keys,
    }
    return handlers[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
