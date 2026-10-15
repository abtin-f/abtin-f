# Pexels API reference

Base: `https://api.pexels.com/v1`
Auth: `Authorization: <API_KEY>` header (no `Bearer` prefix).
Limits: 200 requests/hour, 20,000/month. Headers `X-Ratelimit-Limit`, `X-Ratelimit-Remaining`, `X-Ratelimit-Reset` come back on 2xx responses only — notably *not* on 429s, so track them as you go.

> The older `https://api.pexels.com/videos/` path is deprecated. Everything here uses `/v1/videos/`.

## Contents
- [Endpoints](#endpoints)
- [Photo search parameters](#photo-search-parameters)
- [Video search parameters](#video-search-parameters)
- [Collections](#collections)
- [Photo resource](#photo-resource)
- [Video resource](#video-resource)
- [Attribution](#attribution)

## Endpoints

| Purpose | Endpoint |
|---|---|
| Search photos | `GET /v1/search?query=...` |
| Curated photos | `GET /v1/curated` |
| One photo | `GET /v1/photos/:id` |
| Search videos | `GET /v1/videos/search?query=...` |
| Popular videos | `GET /v1/videos/popular` |
| One video | `GET /v1/videos/videos/:id` (yes, doubled) |
| Featured collections | `GET /v1/collections/featured` |
| Your collections | `GET /v1/collections` |
| Media in a collection | `GET /v1/collections/:id` |

Pagination is shared: `page` (default 1), `per_page` (default 15, **max 80**). Responses carry `page`, `per_page`, `total_results`, and `next_page`/`prev_page` when applicable.

## Photo search parameters

| Param | Values |
|---|---|
| `query` | required |
| `orientation` | `landscape`, `portrait`, `square` |
| `size` | `large` (24MP), `medium` (12MP), `small` (4MP) — a *minimum*, not an exact match |
| `color` | `red`, `orange`, `yellow`, `green`, `turquoise`, `blue`, `violet`, `pink`, `brown`, `black`, `gray`, `white`, or any hex like `#ffffff` |
| `locale` | `en-US`, `pt-BR`, `es-ES`, `ca-ES`, `de-DE`, `it-IT`, `fr-FR`, `sv-SE`, `id-ID`, `pl-PL`, `ja-JP`, `zh-TW`, `zh-CN`, `ko-KR`, `th-TH`, `nl-NL`, `hu-HU`, `vi-VN`, `cs-CZ`, `da-DK`, `fi-FI`, `uk-UA`, `el-GR`, `ro-RO`, `nb-NO`, `sk-SK`, `tr-TR`, `ru-RU` |

Arabic is not among the supported locales — search Pexels in English and use Pixabay's `lang=ar` when an Arabic-language query matters.

## Video search parameters

Same `query`, `orientation`, `locale`, `page`, `per_page`. Plus:

| Param | Values |
|---|---|
| `size` | `large` (4K), `medium` (Full HD), `small` (HD) |

`min_duration` / `max_duration` exist only on `/videos/popular`, **not** on `/videos/search`. The CLI filters search results client-side to compensate, which means `--max-duration` on a search can return fewer items than `--limit` asked for.

## Collections

Collections can't be created or edited through the API — only read. You get featured collections plus your own.

Collection resource: `id` (string), `title`, `description`, `private`, `media_count`, `photos_count`, `videos_count`.

`GET /v1/collections/:id` accepts `type=photos|videos` to filter, and `sort=asc|desc` (default `asc`). Each item in `media` carries an extra `type` field of `"Photo"` or `"Video"`.

## Photo resource

```json
{
  "id": 2014422, "width": 3024, "height": 3024,
  "url": "https://www.pexels.com/photo/brown-rocks-during-golden-hour-2014422/",
  "photographer": "Joey Farina",
  "photographer_url": "https://www.pexels.com/@joey",
  "photographer_id": 680589,
  "avg_color": "#978E82",
  "src": {
    "original": "...", "large2x": "...", "large": "...", "medium": "...",
    "small": "...", "portrait": "...", "landscape": "...", "tiny": "..."
  },
  "alt": "Brown Rocks During Golden Hour"
}
```

`src` sizes: `original` (untouched), `large` (940×650), `large2x` (940×650 @2x), `medium` (350px tall), `small` (130px tall), `portrait` (800×1200 cropped), `landscape` (1200×627 cropped), `tiny` (280×200 cropped).

`avg_color` is handy as a placeholder background while an image loads. `alt` is a real text description — use it for accessibility rather than inventing one.

## Video resource

```json
{
  "id": 2499611, "width": 1080, "height": 1920, "duration": 22,
  "url": "https://www.pexels.com/video/2499611/",
  "image": "https://images.pexels.com/videos/2499611/free-video-2499611.jpg",
  "user": {"id": 680589, "name": "Joey Farina", "url": "https://www.pexels.com/@joey"},
  "video_files": [
    {"id": 125004, "quality": "hd", "file_type": "video/mp4",
     "width": 1080, "height": 1920, "fps": 23.976, "link": "..."}
  ],
  "video_pictures": [{"id": 308178, "picture": "...", "nr": 0}]
}
```

`video_files` is unsorted and mixes resolutions. Some entries have `quality: "hls"` with null dimensions and an `.m3u8` link — those are streaming manifests, not downloadable files, so skip them when picking a rendition. `video_pictures` are sequential preview frames, useful for a scrubbing thumbnail strip.

## Attribution

Pexels asks for a prominent link back ("Photos provided by Pexels") and credit to the photographer where possible ("Photo by John Doe on Pexels", linked to the photo page). Logos are at `https://images.pexels.com/lib/api/pexels.png` (black) and `pexels-white.png`.

Replicating core Pexels functionality — a wallpaper app being their named example — is not permitted.
