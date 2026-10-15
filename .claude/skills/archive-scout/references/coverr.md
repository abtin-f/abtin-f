# Coverr API reference

Base: `https://api.coverr.co`
Auth: either `?api_key={key}` as a query parameter, or `Authorization: Bearer {key}` as a header. The CLI uses the query parameter.
Limits: none published. Be reasonable anyway.

Coverr is video-only. Its library is curated for a specific purpose — website backgrounds, hero sections, ambient loops — which makes it the right first stop for that use case and the wrong one for general-purpose footage search.

## Contents
- [Endpoints](#endpoints)
- [Video listing parameters](#video-listing-parameters)
- [Video resource](#video-resource)
- [Signed URLs](#signed-urls)
- [The download ping](#the-download-ping)
- [Collections](#collections)
- [Categories](#categories)

## Endpoints

| Purpose | Endpoint |
|---|---|
| List / search videos | `GET /videos` |
| One video | `GET /videos/:id` |
| Report a download | `PATCH /videos/:id/stats/downloads` |
| List collections | `GET /collections` |
| One collection | `GET /collections/:id` |
| Videos in a collection | `GET /collections/:id/videos` |
| List categories | `GET /categories` |
| One category | `GET /categories/:id` (accepts id **or** slug) |
| Videos in a category | `GET /categories/:id/videos` |

**Pagination is 0-indexed** — `page=0` is the first page, unlike Pexels and Pixabay. `page_size` defaults to 20. The CLI converts from 1-indexed input so `--page` behaves consistently across sources.

Responses wrap results in `hits`, alongside `page`, `pages`, `page_size`, `total`.

## Video listing parameters

| Param | Notes |
|---|---|
| `page` | Default 0 |
| `page_size` | Default 20 |
| `query` | Search term; searching is just `/videos` with this set |
| `sort` | `date`, `popular`, `trending`. Default `popular`. |
| `urls` | Boolean, default **false**. See below. |

There are no server-side filters for duration, orientation, or resolution. Filter client-side on `duration`, `is_vertical`, `max_width`/`max_height` — which is what the CLI does for `--min-duration`, `--max-duration`, and `--orientation`. Because filtering happens after fetching, a narrow filter may return fewer results than `--limit` requested; raise `--limit` or page through.

## Video resource

```json
{
  "id": "S1YbPl1NfI",
  "created_at": "2020-11-12T08:55:42.710Z",
  "title": "Cutting Wood Building Material With a Circular Electric Saw",
  "description": "A close-up of the circular miter saw lowers and cuts off material",
  "poster": "https://storage.coverr.co/p/...",
  "thumbnail": "https://storage.coverr.co/t/...",
  "is_vertical": false,
  "tags": ["circular saw", "miter saw", "saw"],
  "downloads": 4, "views": 1,
  "published_at": "2020-11-15T09:27:18.578Z",
  "aspect_ratio": "16:9",
  "duration": 11.625,
  "max_height": 1152, "max_width": 2048,
  "urls": {
    "mp4": "https://storage.coverr.co/videos/...?token={token}",
    "mp4_preview": "https://storage.coverr.co/videos/.../preview?token={token}",
    "mp4_download": "https://storage.coverr.co/videos/.../download?token={token}&filename=..."
  }
}
```

Ids are short alphanumeric strings, not integers. `duration` is a float. Titles and descriptions are human-written and genuinely descriptive — better search-result labels than Pexels' slug-derived names.

## Signed URLs

`urls` is **omitted from list responses unless you pass `urls=true`**. This is the single easiest thing to get wrong: a search comes back looking fine, and every download link is missing. The CLI always sends `urls=true`. Single-video responses (`GET /videos/:id`) always include `urls`.

The three variants:
- `mp4` — the file, for inline playback or embedding
- `mp4_preview` — low resolution, for hover previews; also signals to Coverr that a video was genuinely viewed rather than just thumbnailed
- `mp4_download` — same file plus a `Content-Disposition` header so browsers save rather than play it

URLs are JWT-signed and tied to the API key. The tokens don't expire, which is worth remembering before pasting one somewhere public.

## The download ping

```
PATCH /videos/:id/stats/downloads   →   204 No Content
```

Coverr states this is mandatory, not optional: it feeds their popularity ranking and guides what content gets made, and they say they periodically check that partners have implemented it. If a video is downloaded through `mp4_download`, the header alone registers it. If it's fetched any other way — or "selected" in an app to be hotlinked later — send the ping explicitly.

The CLI does this automatically whenever it downloads a Coverr video, and exposes it as `download --source coverr --id <id>` for the manual case. Failures are non-fatal and reported as `stats_pinged: false`.

## Collections

Editorially curated, updated as new footage lands.

```json
{
  "id": "kJibI2VztM",
  "title": "Paris Is For Lovers",
  "slug": "paris-is-for-lovers",
  "description": "Paris is always a good idea...",
  "cover_video": "xj9ZIsytBA",
  "cover_image": "https://storage.coverr.co/t/...",
  "tags": ["paris", "paris cafe", "love"],
  "videos": ["ypWCoOJp3g", "YrKN1E6hBJ"],
  "published": false
}
```

`videos` is an array of ids only — fetch `/collections/:id/videos` for the actual objects. That endpoint accepts `query` for searching within the collection, plus the usual `page`, `page_size`, `urls`.

## Categories

System-wide buckets forming the browse structure — roughly 25 of them, updated as videos are tagged.

```json
{
  "id": "8XEAeHkazU",
  "name": "Business & Work",
  "slug": "business-work",
  "description": "Business and work videos are all about showing different work environments...",
  "cover_video": "bJ7KveC5oR",
  "cover_image": "https://storage.coverr.co/t/...",
  "tags": ["business", "work", "office"],
  "order": 1
}
```

`/categories/:id` accepts the slug as well as the id, so `business-work` works directly and is more readable in scripts.

Licensing terms: https://coverr.co/license
