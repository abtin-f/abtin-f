# Pixabay API reference

Images: `GET https://pixabay.com/api/`
Videos: `GET https://pixabay.com/api/videos/`
Auth: `key` query parameter.
Limits: 100 requests per 60 seconds, tied to the key rather than the IP. Headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. Exceeding it returns 429 with a plain-text body.

Pixabay is the only one of the three sources with illustrations, vectors, transparent backgrounds, and named colour filtering — which is why the skill routes those requests here unconditionally.

## Contents
- [Shared parameters](#shared-parameters)
- [Image-only parameters](#image-only-parameters)
- [Video-only parameters](#video-only-parameters)
- [Categories](#categories)
- [Image response](#image-response)
- [Video response](#video-response)
- [Full API access](#full-api-access)
- [Terms worth respecting](#terms-worth-respecting)

## Shared parameters

| Param | Notes |
|---|---|
| `key` | required |
| `q` | URL-encoded, max 100 characters. Omit to get everything. |
| `lang` | `cs da de en es fr id it hu nl no pl pt ro sk fi sv tr vi th bg ru el ja ko zh`. Default `en`. |
| `id` | Retrieve a specific item by id |
| `category` | See list below |
| `min_width` / `min_height` | Default 0 |
| `editors_choice` | `true`/`false`, default false |
| `safesearch` | `true`/`false`, default **false** — the CLI sends `true` unless `--no-safesearch` |
| `order` | `popular` or `latest` |
| `page` | Default 1 |
| `per_page` | **3–200**, default 20. Values below 3 are rejected; the CLI clamps up to 3. |

Note the `lang` parameter is a genuine differentiator: an Arabic-language query isn't supported, but many other locales are, and results can differ meaningfully from the English equivalent.

## Image-only parameters

| Param | Values |
|---|---|
| `image_type` | `all`, `photo`, `illustration`, `vector` |
| `orientation` | `all`, `horizontal`, `vertical` |
| `colors` | `grayscale`, `transparent`, `red`, `orange`, `yellow`, `green`, `turquoise`, `blue`, `lilac`, `pink`, `white`, `gray`, `black`, `brown` — comma-separate for multiple |

`orientation` uses `horizontal`/`vertical` here, not Pexels' `landscape`/`portrait`. The CLI passes through whatever you give it, so use Pixabay's vocabulary when targeting Pixabay directly.

`colors=transparent` is the way to get PNGs with alpha channels — genuinely useful for logos, icons, and UI assets, and unavailable from the other two sources.

## Video-only parameters

| Param | Values |
|---|---|
| `video_type` | `all`, `film`, `animation` |

No orientation filter on videos. Derive it from the returned stream dimensions instead.

## Categories

`backgrounds`, `fashion`, `nature`, `science`, `education`, `feelings`, `health`, `people`, `religion`, `places`, `animals`, `industry`, `computer`, `food`, `sports`, `transportation`, `travel`, `buildings`, `business`, `music`

Same list for images and videos.

## Image response

```json
{
  "total": 4692, "totalHits": 500,
  "hits": [{
    "id": 195893,
    "pageURL": "https://pixabay.com/en/blossom-bloom-flower-195893/",
    "type": "photo",
    "tags": "blossom, bloom, flower",
    "previewURL": "...", "previewWidth": 150, "previewHeight": 84,
    "webformatURL": "..._640.jpg", "webformatWidth": 640, "webformatHeight": 360,
    "largeImageURL": "..._1280.jpg",
    "imageWidth": 4000, "imageHeight": 2250, "imageSize": 4731420,
    "views": 7671, "downloads": 6439, "likes": 5,
    "user_id": 48777, "user": "Josch13"
  }]
}
```

`total` is all matches; `totalHits` is how many the API will actually let you page through — capped at 500 per query by default. Asking for page 30 of a 20-per-page search gets you nothing.

`tags` is a comma-separated **string**, not an array.

`webformatURL` is valid for 24 hours only. Its `_640` suffix can be swapped for `_180`, `_340`, or `_960` to get other sizes.

Profile URL format: `https://pixabay.com/users/{user}-{user_id}/`.

## Video response

```json
{
  "hits": [{
    "id": 125, "pageURL": "https://pixabay.com/videos/id-125/",
    "type": "film", "tags": "flowers, yellow, blossom", "duration": 12,
    "videos": {
      "large":  {"url": "...", "width": 1920, "height": 1080, "size": 6615235, "thumbnail": "..."},
      "medium": {"url": "...", "width": 1280, "height": 720,  "size": 3562083, "thumbnail": "..."},
      "small":  {"url": "...", "width": 640,  "height": 360,  "size": 1030736, "thumbnail": "..."},
      "tiny":   {"url": "...", "width": 480,  "height": 270,  "size": 426799,  "thumbnail": "..."}
    },
    "views": 4462, "downloads": 1464, "user_id": 1281706, "user": "Coverr-Free-Footage"
  }]
}
```

The size names are nominal, not guaranteed: `large` is usually 3840×2160 but comes back with an empty URL and `size: 0` when unavailable. `medium`, `small`, and `tiny` are always present. Pick by actual pixel count rather than by name — which is what the CLI does.

Appending `download=1` to a video URL makes browsers download rather than inline it.

## Full API access

`fullHDURL`, `imageURL` (the true original), and `vectorURL` only appear for accounts approved for full API access. Without it, `largeImageURL` at 1280px is the ceiling. That's adequate for web use but thin for print or large-format design; full access can be requested from Pixabay's API page.

## Terms worth respecting

- **Cache for 24 hours.** Stated as a requirement, not a suggestion. The CLI does this by default.
- **No permanent hotlinking.** Pixabay URLs are for temporarily displaying search results. Anything shipped should be downloaded and self-hosted. Videos may be embedded directly, but self-hosting is still recommended.
- **No systematic mass downloading.** The API is meant for real human-driven requests.
- **Show where results came from** whenever they're displayed. This is the one thing Pixabay asks in exchange for free API use.
