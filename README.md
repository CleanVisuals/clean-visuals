# Clean Visuals

Custom backgrounds and interface theming for RuneLite.

Put an image or animated GIF behind the chatbox, the side panel and the login screen, recolour or
hide the surrounding game frame, and draw your own borders.

**[Join the Discord](https://discord.gg/NaQqKakYY)** — get help, report bugs.

---

## Using it

Backgrounds live in the **sidebar panel**: open a region, tick it on, choose an image, then frame
it with the controls underneath. Every control can be dragged for a rough value or typed into for
an exact one, and the game updates as you go.

Frame colour, hiding and borders live in the **config screen**.

Save a preset before experimenting — it stores everything, so you can always get back.

---

## Requirements and limitations

Read these first — several are structural rather than bugs.

### Resizable layouts only

Both the classic and modern arrangements work. **Fixed layout is not supported** and the plugin
draws nothing there. In fixed layout the region sits on the game frame rather than over the
scene, which needs an entirely different rendering approach — one that also could never animate,
because it would mean rebuilding a sprite every frame and resetting the client's shared sprite
cache with it.

### Hiding and tinting are global

RuneLite replaces sprites **by id**, and the id is all the game knows. The side panel backing is
also the trade and bank backdrop; button backgrounds appear in every interface with a button.
**Hiding or recolouring one removes or recolours it everywhere it is drawn.**

This is inherent to the mechanism, not something the plugin can scope. Enable one option at a
time and check the bank before committing to a look. Custom borders are the exception — they are
drawn per region and affect nothing else.

### How long a GIF can be

Frames are held decoded, so the limit is **memory, not seconds**. A frame costs
`width × height × 4` bytes, and loading stops at a **256MB budget** — which means how long a GIF
can run depends on how big its frames are:

| GIF size | Per frame | Frames that fit | At 5fps | At 10fps |
|---|---|---|---|---|
| 520×130 | ~270KB | ~950 | ~3m 10s | ~1m 35s |
| 500×300 | ~600KB | ~425 | ~1m 25s | ~42s |
| 1280×720 | ~3.7MB | ~70 | ~14s | ~7s |

**Sizing a GIF to the region it fills is what buys length.** A chatbox is a few hundred pixels
wide, so a chatbox-shaped GIF runs for minutes while a 1080p one runs for seconds.

Going over the budget **truncates the animation** rather than failing — it loops on what fitted,
and a line in the client log says how many frames were kept. Frames are also downscaled so the
longest side is at most 1280px, and a hard ceiling of 2000 frames applies regardless of size.

There is **no audio**, and video files are not supported.

### Side panel border removal is stone only

The game's *Modern Layout → Side panel visual appearance* offers stone and steel. Only stone can
be removed. Steel was implemented and reverted: blanking its sprites removed most of the border
but always left a vertical divider that is not a widget and is not in any known border sprite
family. A partly removed border looked worse than none.

### Login screen

Only the **background** and the **flames** can be changed. RuneLite exposes exactly two methods
for the login screen and nothing else — the logo and login box are drawn by the client from its
own sprites, outside the override path, so they cannot be hidden, recoloured or replaced.

---

## Features

### Backgrounds

Three regions, configured independently: **chatbox**, **side panel**, **login screen**.

- Images (PNG, JPG, BMP) and animated GIFs
- Fit modes — fill, fit, stretch, tile — with zoom and focal point
- Hue, saturation and greyscale adjustment
- Separate image opacity and region see-through controls

### Game UI

- **Tint** a group to a colour while keeping its shading. Hue and saturation cannot recolour grey
  — saturation is applied as a multiplier, and grey has none — and most of the OSRS frame is
  desaturated stone. Tinting sets hue and saturation from your colour and preserves each pixel's
  brightness, so bevels and rivets survive.
- Per-group tints, so the minimap and chat bar can differ
- Global hue, saturation and greyscale for relative adjustment
- **Hide** frame parts: minimap surround, side panel frame and backing, chat bar, chat tabs, orb
  surrounds, scrollbars, button backgrounds
- Side panel border removal
- Keep the **report button** and the **new-message flash** in colour, so notifications stay
  noticeable under black & white or heavy tinting

### Borders

Draw your own border around the chatbox or the side panel — solid, double line, inset, outset,
rounded, soft glow or corners only, with your own colour, thickness and opacity. Scoped to the one
region, unlike removing the game's own border.

### Presets

Save and load complete looks — all three regions, every Game UI setting and the borders — as named
files.

---

## Storage

```
.runelite/clean-visuals/assets     imported images and GIFs
.runelite/clean-visuals/presets    saved presets
```

Every image you pick is **copied into the assets folder**, so a saved preset keeps working if you
move or delete the original. Nothing is ever deleted for you — if the folder grows, tidy it in
your file manager.

---

## Status

A personal project, published because RuneLite's launcher will not load a plugin any other way.
Provided as-is: issues and feature requests may not be actioned, and it is shaped around how one
person uses the game.

Bug reports are still welcome, particularly anything that breaks after a RuneLite update — either
in [issues](../../issues) or on the [Discord](https://discord.gg/NaQqKakYY).

---

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).

The component lifecycle classes (`ComponentManager`, `PluginLifecycleComponent`,
`ComponentStateChanged`) originate from
[melkypie/resource-packs](https://github.com/melkypie/resource-packs) and retain their original
copyright notices.
