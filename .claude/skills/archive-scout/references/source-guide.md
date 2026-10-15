# Source guide

What each source is good for and what to double-check. Everything here is a starting
point; confirm on the item's own page. Do not treat this file as a license grant.

| Source | Key | Good for | License field from tool | Check on the page |
|---|---|---|---|---|
| wikimedia | none | Factual photos, ships, equipment, maps, diagrams, historical images | `LicenseShortName` per file (e.g. CC BY-SA 4.0, Public domain) | Author/credit line, "Attribution" and "Permission" fields, warnings about trademarks, personality rights, or non-free content |
| openverse | none | CC images aggregated from Flickr, museums, etc. | License + version | Landing page of the origin (`page_url`); the aggregator can be stale; confirm creator and license there |
| internetarchive | none | Old books/plates, historical films, archival scans | "see item" | Per-item rights statement; many uploads have unclear or restricted rights |
| loc | none | Historical US prints and photographs | "see item (LoC)" | The item's "Rights & Access" statement; no rights info does not mean public domain |
| nasa | none | Space imagery, some ocean/earth science | "public domain (NASA)" as reported | NASA media usage guidelines: restrictions on logos, identifiable people, implied endorsement |
| pexels | key | Modern stock photos/video | "Pexels (free)" | Current Pexels license page; restrictions on recognizable people and brands |
| pixabay | key | Modern stock photos, illustrations, vectors, video | "Pixabay (free)" | Current Pixabay license page; AI-generated content exists here; no permanent hotlinking |
| coverr | key (stock_media.py only) | Ambient/background video loops | Coverr license | Coverr license page; download ping is automatic |

## Topic hints: undersea cables

Likely strong sources: Wikimedia Commons (cable-laying ships, cable cross-sections, landing stations, historical telegraph cable, maps), Library of Congress and Internet Archive (historic transatlantic cable material), NASA (ocean and Earth imagery). Stock sites tend to have generic ocean/ship/server-room imagery and rarely depict real submarine cable hardware - be skeptical of stock results titled "undersea cable"; many are renders or illustrations.

Useful phrasings: `submarine communications cable`, `cable layer ship`, `cable ship`, `cable landing station`, `submarine cable cross section`, `undersea cable repair`, `transatlantic telegraph cable`, `submarine cable map`, `optical fiber cable`, `data center network equipment`.

## Classification cues (real vs. illustration vs. AI)

- **Real photograph:** page names a photographer, camera/EXIF data, a place/date, or an archive accession number.
- **Illustration/diagram/map:** vector (SVG) or "drawing" media type, labels, legends, infographic styling, "diagram", "map", "schematic" in title/categories.
- **3D render / AI-generated:** no photographer or provenance, glossy "perfect" lighting, tags like "ai generated", "midjourney", "stable diffusion", "render", "3d"; Pixabay and some Openverse sources host these. If unsure, label `unverified`.

Never state an image is real/AI-free without evidence from the page.
