# Quest Studio ↔ QuestPlus: annotations and shared media

Both **legacy inline base64 annotations** and **pooled images** remain valid in Minecraft.
Quest Studio edits inline `data` values. **Only export** converts repeated images into
category-level `images`; import reverses this transformation before editing.

Example exported category:

```json
{
  "id":"questplus:examples",
  "icon":"minecraft:book",
  "title":{"en_us":"Examples"},
  "description":{"en_us":"Examples"},
  "images":["data:image/png;base64,iVBORw0KGgo..."],
  "annotations":[{
    "id":"banner",
    "x":50,
    "y":80,
    "width":320,
    "height":180,
    "text":{"en_us":"Hello","fr_fr":"Bonjour"},
    "rich":{
      "en_us":[{"type":"image","ref":0,"width":300,"height":150}],
      "fr_fr":[{"type":"image","ref":0,"width":300,"height":150}]
    }
  }]
}
```

- `ref` is a **zero-based integer** indexing `images` in the same category. No external URLs.
- Identical data URIs are exported once **per category**, no matter the annotation or locale.
- Broken/out-of-range refs, non-image base64, and mixed `data`+`ref` are rejected on import.
- Old JSON without `images` (and with inline `data`) works unchanged.
- Media upload limit: **2 MiB per PNG/JPEG/GIF**, decoded image up to **4096 × 4096**.
  Imported WebP is converted to PNG in Studio (also subject to 2 MiB).
- Per-category JSON size max **12 MiB** after deduplication; sync snapshot max **24M characters**.
  A large set of different images can exceed the aggregate sync cap.
- Annotation width **60–800 px**, height **20–600 px**. Embedded image W/H **8–400 × 8–300 px**.
- Quest `size` **24–96 px**, default **32 px**. No `size` means legacy size.
- Diagram coordinates pan and zoom, but quest and annotation rendered sizes are *screen pixels*.
- A new preview/translation language is seeded from authored content, including rich image
  parts. Missing values do not overwrite existing translated values; author edits are independent.

The server validates pooled data and forwards it to clients. The Minecraft client resolves
`ref` against its category's `images` and caches the decoded textures. The editor unpools
on import to keep its existing rich-text workflow.

- Quest descriptions may also contain optional `descriptionRich` per locale, including pixel-sized text (`sizePx`: 6–128) and embedded images; the original `description` remains as a backward-compatible text fallback.
- The Studio canvas displays only the quest icon by default. Its optional title is placed below the icon; neither node nor annotation dimensions change with wheel zoom.
