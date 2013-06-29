package se.embargo.retroboy.filter;

import java.util.Arrays;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IIndexedPalette;
import se.embargo.retroboy.color.IPalette;
import se.embargo.retroboy.color.MonochromePalette;

/**
 * @link	http://fox-gieg.com/tutorials/2008/fake-pxl2000-effect/
 */
public class Pxl2000Filter extends AbstractFilter {
	private final double _bordersize = 0.125d;
	private final FilterBody _body = new FilterBody();
	private final BlurBody _blur = new BlurBody();
	private final IIndexedPalette _palette = new MonochromePalette(7);
	
	/**
	 * @link	http://www.swageroo.com/wordpress/how-to-program-a-gaussian-blur-without-using-3rd-party-libraries/
	 */
	private final float[] _blurkernel = {
		0.0947416f, 0.118317f, 0.0947416f,
		0.1183180f, 0.147761f, 0.1183180f,
		0.0947416f, 0.118317f, 0.0947416f};
	
	/**
	 * Amount of sharpening
	 */
	private final float _sharpenAmount = 0.5f;
	
	/**
	 * Number of levels of color depth
	 */
	private final float _posterizeLevels = 90;
	
	/**
	 * Ratio of dynamic range compression
	 * @link	http://www.finerimage.com.au/Articles/Photoshop/Levels.php
	 */
	private final float _dynamicRangeCompression = 1.2f;
	
	@Override
	public IPalette getPalette() {
		return _palette;
	}
	
    @Override
    public boolean isColorFilter() {
    	return false;
    }

    @Override
	public void accept(ImageBuffer buffer) {
    	final int borderwidth = (int)((double)buffer.imagewidth * _bordersize);
    	final int bordercolor = 0xff000000;
    	
    	// Apply the PXL-2000 effect
		buffer.initScratchBuffer();
		Parallel.forRange(_body, buffer, borderwidth, buffer.imageheight - borderwidth);
		buffer.flipScratchBuffer();

		// Apply additional blur
		//Parallel.forRange(_blur, buffer, borderwidth, buffer.imageheight - borderwidth);
		//buffer.flipScratchBuffer();
		
    	// Black out the first and last lines
    	final int[] image = buffer.image.array();
    	Arrays.fill(image, 0, buffer.imagewidth * borderwidth, bordercolor);
    	Arrays.fill(image, buffer.imagewidth * (buffer.imageheight - borderwidth), buffer.imagewidth * buffer.imageheight, bordercolor);
    	
    	// Black out the sides
    	for (int i = borderwidth, last = buffer.imageheight - borderwidth, pos; i < last; i++) {
    		pos = buffer.imagewidth * i;
    		Arrays.fill(image, pos, pos + borderwidth, bordercolor);
    		
    		pos = pos + buffer.imagewidth - borderwidth;
    		Arrays.fill(image, pos, pos + borderwidth, bordercolor);
    	}
    }
    
    private class FilterBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
			final int[] image = buffer.image.array();
			final int[] scratch = buffer.scratch.array();
			
			final int borderwidth = (int)((double)buffer.imagewidth * _bordersize);
			final int width = buffer.imagewidth, xlast = width - borderwidth;
			final float[] kernel = _blurkernel;
			final float sharpen = _sharpenAmount;
			final float posterize = (255f / _posterizeLevels);
			final float compression = _dynamicRangeCompression;
			
			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = borderwidth; x < xlast; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					float lum = 0, origlum = pixel & 0xff;

					// Apply Gaussian blur
					lum += (float)(image[i - width - 1] & 0xff) * kernel[0];
					lum += (float)(image[i - width    ] & 0xff) * kernel[1];
					lum += (float)(image[i - width + 1] & 0xff) * kernel[2];
					lum += (float)(image[i         - 1] & 0xff) * kernel[3];
					lum += (float)(image[i            ] & 0xff) * kernel[4];
					lum += (float)(image[i         + 1] & 0xff) * kernel[5];
					lum += (float)(image[i + width - 1] & 0xff) * kernel[6];
					lum += (float)(image[i + width    ] & 0xff) * kernel[7];
					lum += (float)(image[i + width + 1] & 0xff) * kernel[8];
					
					// Apply unsharp mask
					final float contrast = Math.abs(origlum - lum) * sharpen;
					final float factor = (259f * ((float)contrast + 255f)) / (255f * (259f - (float)contrast));
					lum = factor * (lum - 128f) + 128f;
					
					// Clamp to 5% and 95% light levels
					lum = Math.max(12.75f, Math.min(lum, 242.25f));
					
					// Compress dynamic range
					lum = (lum - 128f) * compression + 128f;
					
					// Posterize to reduce color depth
					lum = Math.round(lum / posterize) * posterize;

					// Clamp to 5% and 95% light levels
					lum = Math.max(12.75f, Math.min(lum, 242.25f));
					
					// Output the pixel, but keep alpha channel intact
					final int color = (int)lum;
					scratch[i] = (pixel & 0xff000000) | (color << 16) | (color << 8) | color;
				}
			}
		}
    }

    private class BlurBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
			final int[] image = buffer.image.array();
			final int[] scratch = buffer.scratch.array();
			
			final int borderwidth = (int)((double)buffer.imagewidth * _bordersize);
			final int width = buffer.imagewidth, xlast = width - borderwidth;
			final float[] blurmatrix = _blurkernel;
			
			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = borderwidth; x < xlast; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					float lum = 0;

					// Apply Gaussian blur
					lum += (float)(image[i - width - 1] & 0xff) * blurmatrix[0];
					lum += (float)(image[i - width    ] & 0xff) * blurmatrix[1];
					lum += (float)(image[i - width + 1] & 0xff) * blurmatrix[2];
					lum += (float)(image[i         - 1] & 0xff) * blurmatrix[3];
					lum += (float)(image[i            ] & 0xff) * blurmatrix[4];
					lum += (float)(image[i         + 1] & 0xff) * blurmatrix[5];
					lum += (float)(image[i + width - 1] & 0xff) * blurmatrix[6];
					lum += (float)(image[i + width    ] & 0xff) * blurmatrix[7];
					lum += (float)(image[i + width + 1] & 0xff) * blurmatrix[8];
					
					// Output the pixel, but keep alpha channel intact
					int color = Math.max(0, Math.min((int)lum, 255));
					scratch[i] = (pixel & 0xff000000) | (color << 16) | (color << 8) | color;
				}
			}
		}
    }
}
