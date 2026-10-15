---
name: archive-scout
description: Source, vet and report real visual assets (photos, archival images, maps, scientific imagery, footage) for documentary-style YouTube explainers on the channel "The Archive". Combines two retrieval toolkits - webmedia.py (Wikimedia Commons, Openverse, Internet Archive, Library of Congress, NASA, plus Pexels/Pixabay/GIF engines) and stock_media.py (Pexels, Pixabay, Coverr) - behind one license-first workflow. Use whenever a task asks to find, collect, shortlist or download visuals, B-roll, stock footage or reference images for a video, even if no source is named. The workflow never invents assets, resolutions or license permissions, and never downloads before terms are checked.
---

# Archive Scout

One skill for finding visuals for **The Archive** (documentary-style explainers).
It merges two toolkits and adds the vetting workflow the channel needs.

| Toolkit | Script | Sources | Strength |
|---|---|---|---|
| web-media-getter | `scripts/webmedia.py` | Wikimedia Commons, Openverse, Internet Archive, Library of Congress, NASA (no key); Pexels, Pixabay (free key); GIPHY/KLIPY for `--type gif` | Factual, historical, scientific, CC-licensed material. **First choice for documentary work.** |
| stock-media | `scripts/stock_media.py` | Pexels, Pixabay, Coverr | Modern stock photos, illustrations, vectors, video loops; has `--min-width`, orientation, `CREDITS.md`, caching. |

Both are stdlib-only Python 3. Run them from this skill's directory (in Claude chat the skill is mounted in the code sandbox - `cd` into the folder that contains this SKILL.md, or call the scripts by their full path).
Keys: `PEXELS_API_KEY`, `PIXABAY_API_KEY`, `COVERR_API_KEY` (env var, or `stock_media.py setup`).
Run `python3 scripts/stock_media.py keys` to see what is configured. Sources that need a key and have none are skipped, not faked.

## Hard rules (the whole point of this skill)

1. **Never invent** an image, URL, source, resolution, license or attribution string. Every field in a report must come from tool output or from a page you actually read. If a value is unknown, write `unknown - not provided by API` and say how to check it.
2. **Search first, download later.** Never pass `--download` to either script during discovery. Download only after (a) the user approves the shortlist and (b) the license on the **source page** has been read for each file.
3. **Tool-reported license is a lead, not a verdict.** `license` from an API (e.g. "CC BY-SA 4.0", "Pexels (free)") must be confirmed on `page_url`. Many items have per-file terms, third-party rights (logos, people, trademarks), or non-commercial/no-derivatives clauses that matter for monetized YouTube. Flag `NC`/`ND`/`see item`/`unknown` as **not cleared**.
4. **Real vs. illustration vs. AI-generated must be labeled separately.** No API here reports "AI-generated" reliably. Classify from the page (description, tags, uploader notes, category). If you cannot tell, label `unverified` - never assume "real photo". Pixabay/Openverse/stock sites can contain AI images and renders.
5. **Resolution honesty.** See "Resolution traps" below. Report original pixel dimensions only when the API gave them or you measured them after an approved download. 16:9 at 1920x1080 is the floor for full-frame use; for zoom/crop moves prefer >= 3000 px on the long side, and say when an asset is only suitable as an inset.
6. **Don't claim you looked at an image you didn't.** You can judge sharpness/composition only if you actually viewed the preview (e.g. downloaded the thumbnail to a temp folder and opened it with your file/image viewer). Otherwise say "preview not inspected" and judge on metadata only.

## Running in Claude chat (claude.ai) vs Claude Code

This skill works in both. In chat, **Code execution and file creation** must be enabled, and the scripts need outbound HTTPS.

- **If the sandbox has network access**, everything above works as written.
- **If the scripts fail with `403`/`Tunnel connection failed`/timeouts** (common when network access is restricted), do not give up and do not invent results. Either ask the user to allow these domains in their Claude settings - `commons.wikimedia.org`, `api.openverse.org`, `archive.org`, `www.loc.gov`, `images-api.nasa.gov`, `api.pexels.com`, `pixabay.com`, `api.coverr.co` - or use the **fallback**: search with the web search tool and read each item's page with the web fetch tool. Same rules apply: only report what the pages actually say.
- Fallback for Wikimedia Commons (no key, returns license and size per file):
  `https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=<terms>&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url|size|mediatype|extmetadata&iiurlwidth=800&format=json`
  (`imageinfo[0].url` = original file, `.width/.height` = original size, `extmetadata.LicenseShortName` / `.Artist` = license and credit.)
- **API keys in chat:** Pexels/Pixabay/Coverr keys are optional. If the user pastes one, run `python3 scripts/stock_media.py setup --pexels <key>`; it lives only in that sandbox session. No key means those sources are skipped and the no-key sources still work.
- **Showing results:** chat can render previews only if you save the thumbnail to a file and view it, or link the preview URL. Otherwise give the source page link plus metadata and say "preview not inspected".
- **Delivering downloads:** save approved files plus `CREDITS.md` into the outputs folder and zip them so the user can download them.

## Workflow

### 1. Plan the shot list
Break the topic into 4-6 visual categories (for undersea cables: cable close-ups and cross-sections; cable-laying ships; repair/recovery operations; seabed and landing infrastructure; maps/diagrams). Aim for the requested count (e.g. 12-15) with spread across categories, not 15 variations of one shot.

### 2. Search broadly, per category, no downloads
```bash
# Documentary / factual sources first
python3 scripts/webmedia.py "submarine communications cable" --type image --source wikimedia,openverse,nasa,loc,internetarchive --count 10 --json
# Modern stock for generic ocean/ship/server-room fills
python3 scripts/stock_media.py search "cable laying ship" --type photo --orientation landscape --min-width 1920 --limit 8
# Video, if wanted
python3 scripts/webmedia.py "cable laying ship" --type video --source nasa,internetarchive,pexels --count 6 --json
```
Tips: try 2-3 phrasings per category ("submarine cable", "undersea cable", "fiber optic cable ship", "cable landing station", "telegraph cable"); two-word queries beat long ones. A source erroring or returning nothing is normal - record it in the report rather than hiding it.

### 3. Fetch previews and inspect
Download **thumbnails only** (the `thumb` / `preview_url` field) into a temp folder and view them to judge sharpness, subject, and 16:9 fit. Do not download full files yet. Discard watermarked, low-res, text-heavy or off-topic hits.

### 4. Check the source page and terms
For each shortlisted item, open `page_url` (web fetch / browse tool) and confirm: license name and version, author, any "requires attribution/share-alike" text, restrictions (people, logos, trademarks, "editorial use only"), and whether it is a photograph, drawing, diagram, map or render. See `references/source-guide.md` for source-specific traps.

### 5. Report (before any download)
Use the template in `references/report-template.md`. Each asset needs all five items the channel asks for:
1. Preview + original source page (link; embed preview only if it renders in the interface)
2. Resolution/dimensions (with provenance: API / page / unknown)
3. License + required attribution (ready-to-paste credit line when the license requires one)
4. Where it fits in the video (one or two sentences, tied to a narration beat)
5. Direct-download status: `direct file URL available` / `page-only (manual download)` / `blocked or needs login` / `unknown`

Group the report as **Real photographs**, **Illustrations / diagrams / maps**, **AI-generated or unverified**, and a final **Rejected / not cleared** list with reasons. End with gaps (categories that stayed thin) and next-search suggestions.

### 6. Download only approved, cleared items
```bash
python3 scripts/stock_media.py download "<direct_url>" ./assets/the-archive/<topic>      # stock sources
# webmedia.py has no single-URL mode: use the record's `url` (original) with curl,
# or re-run its search narrowed to one result - avoid blind `--download` on a whole result set.
```
After download: measure real dimensions (`file <path>`, or `identify`/`ffprobe` if installed), write `CREDITS.md` with the exact attribution lines, and keep the source page URL next to each file.

## Resolution traps (read before quoting numbers)

- **Wikimedia (webmedia.py):** `w`/`h` are the *original* size, but `dl` and `thumb` are an **800 px-wide rendition**. `url` is the original file. Never describe `dl` as high-res.
- **Openverse:** `w`/`h` can be null; `url` may be a re-hosted copy on the origin site. Verify on the landing page.
- **Internet Archive / LoC:** no dimensions from the search API; `dl` for IA is resolved only at download time (and capped near 15 MB). Report dimensions as unknown until measured.
- **NASA:** `dl` is the thumbnail link, not the original. The real full-size file must be located via the item page or `images-api.nasa.gov/asset/<nasa_id>`.
- **Pexels:** webmedia.py returns `large2x` (~1880 px wide), not the original; `stock_media.py` returns the original. Pixabay without full-access approval caps at 1280 px (`largeImageURL`).
- **Videos:** archival sources return whole films, not shots. Say so, and note a timestamp to look for rather than implying a ready clip.

## Licensing quick notes

(Starting points only - always verify on the page; terms change.)
- **CC BY / CC BY-SA:** attribution required (author, title, license, link); BY-SA also requires derivatives to carry the same license - flag for video use.
- **CC BY-NC / -ND:** not cleared for a monetized channel / for cropping and zooming. Reject unless the user decides otherwise.
- **Public domain / CC0:** no attribution legally required, but credit anyway; still check for third-party marks.
- **Pexels / Pixabay / Coverr:** their own licenses; read the current license page, note restrictions on identifiable people/brands, and do not hotlink Pixabay.
- **Coverr:** `stock_media.py` pings Coverr's download endpoint automatically on download; keep that behavior.

## Files

- `scripts/webmedia.py` - from connerkward/web-media-getter-skill (MIT)
- `scripts/stock_media.py`, `references/{pexels,pixabay,coverr}.md` - from anasdallah/stock-media-skill (MIT)
- `references/source-guide.md`, `references/report-template.md` - this skill
- `licenses/` - upstream license texts; keep them when redistributing
