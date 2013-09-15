package se.embargo.retroboy.filter;

import java.util.Arrays;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IIndexedPalette;
import se.embargo.retroboy.color.IPalette;
import se.embargo.retroboy.color.MonochromePalette;
import se.embargo.retroboy.graphic.GaussianBlur;

/**
 * @link	http://fox-gieg.com/tutorials/2008/fake-pxl2000-effect/
 */
public class Pxl2000Filter extends AbstractFilter {
	private final double _bordersize = 0.125d;
	private final FilterBody _body = new FilterBody();
	private final IIndexedPalette _palette = new MonochromePalette(7);
	
	/**
	 * Amount of blurring
	 */
	private final float[] _blurkernel = GaussianBlur.createKernel(0.5d, 5, 3);

	/**
	 * Amount of sharpening
	 */
	private final float _sharpenAmount = 2.5f;
	
	/**
	 * Amount of scan lines to enhance
	 */
	private final float _scanlineAmount = 1.75f;
	
	/**
	 * Number of levels of color depth
	 */
	private final float _posterizeLevels = 90;
	
	/**
	 * Ratio of dynamic range compression
	 * @link	http://www.finerimage.com.au/Articles/Photoshop/Levels.php
	 */
	private final float _dynamicRangeCompression = 0.9f;
	
	/**
	 * Scratch buffer to hold result from previous frame.
	 */
	private int[] _scratch = null;

	@Override
	public IPalette getPalette() {
		return _palette;
	}
	
	@Override
	public boolean isColorFilter() {
		return false;
	}

	@Override
	public synchronized void accept(ImageBuffer buffer) {
		final int borderwidth = (int)((double)buffer.imagewidth * _bordersize),
				  borderheight = (int)((double)buffer.imageheight * _bordersize);
		final int bordercolor = 0xff000000;

		// Initialize the scratch buffer
		if (_scratch == null || _scratch.length != buffer.image.array().length) {
			_scratch = Arrays.copyOf(buffer.image.array(), buffer.image.array().length);
		}
		
		// Apply the PXL-2000 effect
		Parallel.forRange(_body, buffer, borderheight, buffer.imageheight - borderheight);

		// Apply the scratch buffer
		buffer.image.rewind();
		buffer.image.put(_scratch);

		// Black out the first and last lines
		final int[] image = buffer.image.array();
		Arrays.fill(image, 0, buffer.imagewidth * borderheight, bordercolor);
		Arrays.fill(image, buffer.imagewidth * (buffer.imageheight - borderheight), buffer.imagewidth * buffer.imageheight, bordercolor);
		
		// Black out the sides
		for (int i = borderheight, last = buffer.imageheight - borderheight, pos; i < last; i++) {
			pos = buffer.imagewidth * i;
			Arrays.fill(image, pos, pos + borderwidth, bordercolor);
			
			pos = pos + buffer.imagewidth - borderwidth;
			Arrays.fill(image, pos, pos + borderwidth, bordercolor);
		}
	}
	
	private class FilterBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
			final int[] source = buffer.image.array();
			final int[] target = _scratch;
			
			final int borderwidth = (int)((double)buffer.imagewidth * _bordersize);
			final int width = buffer.imagewidth, xlast = width - borderwidth;
			final float[] kernel = _blurkernel;
			final float sharpen = _sharpenAmount;
			final float scanline = _scanlineAmount;
			final float posterize = (255f / _posterizeLevels);
			final float compression = _dynamicRangeCompression;
			
			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = borderwidth; x < xlast; x++) {
					final int i = x + yi;
					final int pixel = source[i];
					float lum = 0;

					// Apply Gaussian and motion blur (mix in portion of previous pixel)
					lum += (float)(target[i            ] & 0xff) * kernel[0];
					lum += (float)(source[i - width - 1] & 0xff) * kernel[1];
					lum += (float)(source[i - width    ] & 0xff) * kernel[2];
					lum += (float)(source[i - width + 1] & 0xff) * kernel[3];
					lum += (float)(target[i            ] & 0xff) * kernel[4];
					lum += (float)(source[i		    - 2] & 0xff) * kernel[5];
					lum += (float)(source[i		    - 1] & 0xff) * kernel[6];
					lum += (float)(source[i			   ] & 0xff) * kernel[7];
					lum += (float)(source[i		    + 1] & 0xff) * kernel[8];
					lum += (float)(source[i		    + 2] & 0xff) * kernel[9];
					lum += (float)(target[i            ] & 0xff) * kernel[10];
					lum += (float)(source[i + width - 1] & 0xff) * kernel[11];
					lum += (float)(source[i + width    ] & 0xff) * kernel[12];
					lum += (float)(source[i + width + 1] & 0xff) * kernel[13];
					lum += (float)(target[i            ] & 0xff) * kernel[14];
					
					// Apply unsharp mask
					final float lumadiff = Math.abs((pixel & 0xff) - lum);
					float contrast = lumadiff * sharpen;
					float factor = (259f * (contrast + 255f)) / (255f * (259f - contrast));
					lum = factor * (lum - 128f) + 128f;
					
					// Simulate scan lines
					contrast = lumadiff * scanline * (float)(y % 2 * 2 - 1);
					factor = (259f * (contrast + 255f)) / (255f * (259f - contrast));
					lum = factor * (lum - 128f) + 128f;
					
					// Compress dynamic range
					lum = (lum - 128f) * compression + 128f;
					
					// Reduce color depth
					lum = Math.round(lum / posterize) * posterize;

					// Clamp light levels
					lum = Math.max(12.5f, Math.min(lum, 242.5f));
					
					// Output the pixel, but keep alpha channel intact
					final int color = (int)lum;
					target[i] = (pixel & 0xff000000) | (color << 16) | (color << 8) | color;
				}
			}
		}
	}
}
