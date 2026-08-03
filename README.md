# Clean Visuals

Custom backgrounds and interface theming for RuneLite.

Put an image, GIF or video behind the chatbox, the side panel and the login screen, and recolour
or hide the surrounding game frame.

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
time and check the bank before committing to a look.

### Video needs ffmpeg installed separately

The plugin does not decode video and does not bundle a decoder. Video is converted to still
frames **on import** by calling [ffmpeg](https://www.gyan.dev/ffmpeg/builds/), which must already
be on your machine. `ffprobe` is used too; it ships in the same builds.

ffmpeg is found on `PATH`. Failing that, these are checked — **Windows paths only**, so on macOS
or Linux it must be on `PATH`:

```
C:\ffmpeg\bin\ffmpeg.exe
C:\Program Files\ffmpeg\bin\ffmpeg.exe
%USERPROFILE%\scoop\shims\ffmpeg.exe
```

There is **no audio**.

### Memory

Animation frames are held decoded, budgeted at **320MB**. A frame costs `width × height × 4`
bytes, so how much video fits depends on the region and the clip's aspect ratio:

| Region | Import width | 16:9 clip at 15fps | at 5fps |
|---|---|---|---|
| Side panel | 260px | ~2m 27s | ~7m |
| Chatbox | 520px | ~45s | ~2m 15s |
| Login screen | 800px | ~19s | ~57s |

A portrait clip gets roughly a third of a landscape one at the same width, because height is what
consumes the budget. Exceeding it **truncates the clip** rather than failing.

RuneLite's default heap does not comfortably hold 320MB of frames alongside the game. Raise
`-Xmx` if you use long videos.

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

- Images (PNG, JPG, BMP), animated GIFs, and video
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

### Presets

Save and load complete looks — all three regions plus every Game UI setting — as named files.

---

## Using video

**Choose video** opens an import dialog with a start position, previewed as an actual frame, and
a frame rate. The end point is shown and derived from the frame budget.

Notes:

- **Lower frame rates buy duration** for the same memory *and* less disk. 15, 12, 10, 8 and 5 are
  offered.
- **Seeking is keyframe-accurate**, so playback may begin slightly before the position you picked.
  Exact seeking would mean decoding everything skipped.
- **One clip per region.** Re-importing replaces that region's frames; the folder is cleared first
  so old frames cannot be spliced into the new animation.
- **Changing start or frame rate requires re-importing** — both are extraction settings.

### Frame folders made by hand

Pointing a region's image path at a **folder** loads it as a frame sequence. Numbered files, in
name order:

```
ffmpeg -i clip.mp4 -vf "fps=15,scale=520:-1" frames/%04d.png
```

Zero-padding matters — sorting is by name, so `%d` puts frame 10 before frame 2. Frames play at
15fps unless the folder holds an `fps.txt` containing a single number. Keep that and the `fps=`
above in agreement, or playback runs fast or slow by exactly their ratio. Imported video writes
its own `fps.txt`.

---

## Storage

```
.runelite/clean-visuals/assets     imported images and video frames
.runelite/clean-visuals/presets    saved presets
```

Assets accumulate — every image picked is copied in and kept. **Manage stored files**, in the
plugin's side panel, lists everything with its size, marks what a preset or your current settings
still reference, and deletes only what you tick. Nothing is deleted automatically.

---

## Status

A personal project, published because RuneLite's launcher will not load a plugin any other way.
Provided as-is: issues and feature requests may not be actioned, and it is shaped around how one
person uses the game.

Bug reports are still welcome, particularly anything that breaks after a RuneLite update.

---

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).

The component lifecycle classes (`ComponentManager`, `PluginLifecycleComponent`,
`ComponentStateChanged`) originate from
[melkypie/resource-packs](https://github.com/melkypie/resource-packs) and retain their original
copyright notices.
