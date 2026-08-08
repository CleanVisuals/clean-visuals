/*
 * Copyright (c) 2026, Clean Visuals
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cleanvisuals.features.backgrounds;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Draws a framed image into an arbitrary region rectangle.
 * <p>
 * Deliberately knows nothing about the chatbox, widgets or the client. It takes a target
 * rectangle and a {@link BackgroundTransform} and renders accordingly, so the chatbox,
 * inventory, bank and login screen can each own one of these rather than reimplementing
 * framing.
 * <p>
 * The composed result is cached and rebuilt only when the target size, the transform or the
 * source image changes -- never per frame. Recomposing every frame would put a full image
 * scale in the render path, which matters now and matters a great deal once animated frames
 * arrive.
 */
@Slf4j
public class RegionBackground
{
	/**
	 * Composed frames beyond this are not cached, and are recomposed on each show instead.
	 * A long animation into a large region can otherwise cache tens of megabytes.
	 */
	private static final long CACHE_BUDGET_BYTES = 64L * 1024 * 1024;

	private AnimatedImage animation;
	private BufferedImage source;
	private String loadedPath;

	/**
	 * Composed frames keyed by frame index, all sharing one {@link #composedKey}. Dropped
	 * wholesale whenever the target size, transform or adjustments change.
	 */
	private final Map<Integer, BufferedImage> composedFrames = new HashMap<>();
	private String composedKey;
	private boolean cacheFrames = true;

	/**
	 * The most recently composed frame, held even when full caching is off.
	 * <p>
	 * Without this, an animation too large to cache is rescaled and colour-adjusted on every
	 * render. The client redraws far faster than an animation advances -- roughly 50 times a
	 * second against 15 -- so about two thirds of that work reproduced the frame already on
	 * screen. One frame of memory removes it.
	 */
	private BufferedImage lastComposed;
	private int lastComposedIndex = -1;

	/**
	 * Loads an image from disk, or falls back to an obvious test pattern when the path is
	 * empty or unreadable. Reloads only when the path actually changes.
	 */
	public void setImagePath(String path)
	{
		String normalised = path == null ? "" : path.trim();
		if (Objects.equals(normalised, loadedPath) && source != null)
		{
			return;
		}

		loadedPath = normalised;
		invalidate();

		if (normalised.isEmpty())
		{
			setAnimation(AnimatedImage.still(testPattern()));
			return;
		}

		File file = new File(normalised);
		if (!file.isFile())
		{
			log.warn("Background image not found: {} -- using test pattern", normalised);
			setAnimation(AnimatedImage.still(testPattern()));
			return;
		}

		try
		{
			AnimatedImage loaded = AnimatedImage.load(file);
			setAnimation(loaded);

			if (loaded.isAnimated())
			{
				log.debug("Loaded {} frames ({}ms) from {}",
					loaded.frameCount(), loaded.getTotalDurationMs(), normalised);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to read background image: {}", normalised, e);
			setAnimation(AnimatedImage.still(testPattern()));
		}
	}

	private void setAnimation(AnimatedImage loaded)
	{
		animation = loaded;
		source = loaded.frame(0);
	}

	/**
	 * Which frame should be showing after the given playback time, or 0 for a still image.
	 */
	public int frameAt(long elapsedMs)
	{
		return animation == null ? 0 : animation.indexAt(elapsedMs);
	}

	public boolean isAnimated()
	{
		return animation != null && animation.isAnimated();
	}

	public int frameCount()
	{
		return animation == null ? 0 : animation.frameCount();
	}

	public void clear()
	{
		animation = null;
		source = null;
		loadedPath = null;
		invalidate();
	}

	private void invalidate()
	{
		composedFrames.clear();
		composedKey = null;
		cacheFrames = true;
		lastComposed = null;
		lastComposedIndex = -1;
	}

	/**
	 * Draws the framed and colour-adjusted image to fill {@code target}, at {@code alpha}
	 * opacity.
	 */
	public void draw(Graphics2D graphics, Rectangle target, BackgroundTransform transform,
		ImageAdjustments adjustments, float alpha, int frameIndex)
	{
		if (source == null || target == null || target.width <= 0 || target.height <= 0)
		{
			return;
		}

		BufferedImage image = compose(target.width, target.height, transform, adjustments, frameIndex);
		if (image == null)
		{
			return;
		}

		Composite previous = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(alpha)));
		graphics.drawImage(image, target.x, target.y, null);
		graphics.setComposite(previous);
	}

	/**
	 * The framed, colour-adjusted image at the given size.
	 * <p>
	 * Exposed for callers that need the pixels rather than a draw -- notably fixed mode, which
	 * has to hand them to the client as a sprite instead of drawing an overlay.
	 */
	public BufferedImage composeFor(int width, int height, BackgroundTransform transform,
		ImageAdjustments adjustments)
	{
		return composeFor(width, height, transform, adjustments, 0);
	}

	public BufferedImage composeFor(int width, int height, BackgroundTransform transform,
		ImageAdjustments adjustments, int frameIndex)
	{
		if (source == null || width <= 0 || height <= 0)
		{
			return null;
		}
		return compose(width, height, transform, adjustments, frameIndex);
	}

	public boolean hasImage()
	{
		return source != null;
	}

	private BufferedImage compose(int width, int height, BackgroundTransform transform,
		ImageAdjustments adjustments, int frameIndex)
	{
		String key = width + "x" + height
			+ "|" + transform.getFit()
			+ "|" + transform.getZoomPercent()
			+ "|" + transform.getFocalX()
			+ "|" + transform.getFocalY()
			+ "|" + adjustments.getHueShiftDegrees()
			+ "|" + adjustments.getSaturationPercent()
			+ "|" + adjustments.isGrayscale()
			+ "|" + loadedPath;

		if (!key.equals(composedKey))
		{
			composedFrames.clear();
			composedKey = key;
			lastComposed = null;
			lastComposedIndex = -1;

			// Decide up front whether caching every frame at this size is affordable, rather
			// than discovering it after the heap has filled.
			long estimate = (long) width * height * 4 * Math.max(1, frameCount());
			cacheFrames = estimate <= CACHE_BUDGET_BYTES;
			if (!cacheFrames)
			{
				log.debug("Not caching frames: {} frames at {}x{} would need ~{}MB",
					frameCount(), width, height, estimate / (1024 * 1024));
			}
		}

		BufferedImage cached = composedFrames.get(frameIndex);
		if (cached != null)
		{
			return cached;
		}

		// Same frame as last time, on an animation too large to cache in full. This is the common
		// case while a GIF plays: the frame index only advances every few renders.
		if (lastComposed != null && lastComposedIndex == frameIndex)
		{
			return lastComposed;
		}

		BufferedImage frame = animation == null ? source : animation.frame(frameIndex);
		if (frame == null)
		{
			return null;
		}
		// drawFramed and drawTiled read the current frame through this field.
		source = frame;

		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		switch (transform.getFit())
		{
			case STRETCH:
				// Zoom and focal point are meaningless when the image is forced to the exact
				// region shape, so they are ignored rather than half-applied.
				g.drawImage(source, 0, 0, width, height, null);
				break;

			case TILE:
				drawTiled(g, width, height, transform);
				break;

			case FIT:
				drawFramed(g, width, height, transform, false);
				break;

			case FILL:
			default:
				drawFramed(g, width, height, transform, true);
				break;
		}

		g.dispose();

		// Applied after framing, so the pixel work happens on a region-sized image rather
		// than the full-resolution source.
		adjustments.applyTo(result);

		if (cacheFrames)
		{
			composedFrames.put(frameIndex, result);
		}

		lastComposed = result;
		lastComposedIndex = frameIndex;
		return result;
	}

	/**
	 * Scales the image to cover (or fit inside) the region, then positions it so the focal
	 * point lands at the centre of the region.
	 */
	private void drawFramed(Graphics2D g, int width, int height, BackgroundTransform transform, boolean cover)
	{
		double scaleX = (double) width / source.getWidth();
		double scaleY = (double) height / source.getHeight();
		double base = cover ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
		double scale = base * transform.zoom();

		int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

		// Put the focal point of the scaled image at the centre of the region.
		int x = (int) Math.round(width / 2d - transform.getFocalX() * drawWidth);
		int y = (int) Math.round(height / 2d - transform.getFocalY() * drawHeight);

		g.drawImage(source, x, y, drawWidth, drawHeight, null);
	}

	/**
	 * Repeats the image at natural size scaled by zoom, with the focal point shifting the
	 * tiling origin so the pattern can be aligned.
	 */
	private void drawTiled(Graphics2D g, int width, int height, BackgroundTransform transform)
	{
		int tileWidth = Math.max(1, (int) Math.round(source.getWidth() * transform.zoom()));
		int tileHeight = Math.max(1, (int) Math.round(source.getHeight() * transform.zoom()));

		// Focal point shifts the origin by up to one tile, which is all that is meaningful
		// for a repeating pattern.
		int originX = -(int) Math.round(transform.getFocalX() * tileWidth);
		int originY = -(int) Math.round(transform.getFocalY() * tileHeight);

		for (int y = originY; y < height; y += tileHeight)
		{
			for (int x = originX; x < width; x += tileWidth)
			{
				g.drawImage(source, x, y, tileWidth, tileHeight, null);
			}
		}
	}

	/**
	 * Fallback so something unmistakable always renders when no usable image is configured.
	 */
	private static BufferedImage testPattern()
	{
		final int size = 64;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		for (int y = 0; y < size; y += 16)
		{
			for (int x = 0; x < size; x += 16)
			{
				boolean even = ((x / 16) + (y / 16)) % 2 == 0;
				g.setColor(even ? Color.MAGENTA : Color.CYAN);
				g.fillRect(x, y, 16, 16);
			}
		}
		g.dispose();
		return image;
	}

	private static float clamp01(float value)
	{
		return Math.max(0f, Math.min(1f, value));
	}
}
