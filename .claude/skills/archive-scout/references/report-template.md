# Asset report template

Copy this structure. Fill every field from tool output or a page you read; use
`unknown - not provided by API` rather than guessing. Do not download before the
report is approved.

```markdown
# <Video topic> - visual asset shortlist (The Archive)

Searched: <sources actually queried> | Skipped/errored: <source: reason> | Date: <YYYY-MM-DD>
Previews inspected: <yes/no per asset, or "metadata only">

## Real photographs

### 1. <title as given by the source>
- **Preview / source page:** <thumb URL> / <page_url>
- **Type:** real photograph (evidence: <photographer named / EXIF / archive ID>)
- **Dimensions:** <W x H> (source: API | page | unknown) - suitability: full-frame 16:9 | inset only | zoom-safe
- **License:** <name + version> (checked on page: yes/no) - attribution required: <yes/no>
  - Credit line: "<author>, <title>, <license>, via <source>, <page_url>"
  - Cautions: <NC/ND/SA, logos, people, trademarks, or none found>
- **Where it fits:** <narration beat, e.g. "when we explain what is inside the cable, 0:45-1:00">
- **Download:** direct file URL available (<url>) | page-only | blocked | unknown

## Illustrations / diagrams / maps
(same fields; Type: illustration | diagram | map)

## AI-generated or unverified
(same fields; Type: AI-generated | render | unverified - say what evidence you have)

## Rejected / not cleared
- <title> - <reason: NC license, low resolution, watermark, off-topic, unclear rights>

## Gaps and next searches
- <category with thin coverage> -> <next query / source>
```

Summary line at the end: `<n> cleared, <n> need license confirmation, <n> rejected`.
