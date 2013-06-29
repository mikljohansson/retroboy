package se.embargo.retroboy.filter;

import java.util.Arrays;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;

/**
 * @link	http://fox-gieg.com/tutorials/2008/fake-pxl2000-effect/
 */
public class Pxl2000Filter extends AbstractFilter {
	private final double _bordersize = 0.125d;
	private final FilterBody _body = new FilterBody();
	
	/**
	 * @link	http://www.swageroo.com/wordpress/how-to-program-a-gaussian-blur-without-using-3rd-party-libraries/
	 */
	private final float[] _blurkernel = {
		0.0947416f, 0.118317f, 0.0947416f,
		0.1183180f, 0.147761f, 0.1183180f,
		0.0947416f, 0.118317f, 0.0947416f};
	
	/**
	 * @link	http://photo.net/bboard/q-and-a-fetch-msg.tcl?msg_id=000Qi5
	 */
	private final float _sharpenAmount = 0.3f;
	
	/**
	 * Number of levels of color depth
	 */
	private final float _posterizeLevels = 90;
	
	public Pxl2000Filter() {
	}
	
    @Override
    public boolean isColorFilter() {
    	return false;
    }

    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
    	final int borderwidth = (int)((double)buffer.imagewidth * _bordersize);
    	final int color = 0xff000000;
    	
    	// Apply the PXL-2000 effect
		buffer.initScratchBuffer();
		Parallel.forRange(_body, buffer, borderwidth, buffer.imageheight - borderwidth);
		buffer.flipScratchBuffer();

    	// Black out the first and last lines
    	Arrays.fill(image, 0, buffer.imagewidth * borderwidth, color);
    	Arrays.fill(image, buffer.imagewidth * (buffer.imageheight - borderwidth), buffer.imagewidth * buffer.imageheight, color);
    	
    	// Black out the sides
    	for (int i = borderwidth, last = buffer.imageheight - borderwidth, pos; i < last; i++) {
    		pos = buffer.imagewidth * i;
    		Arrays.fill(image, pos, pos + borderwidth, color);
    		
    		pos = pos + buffer.imagewidth - borderwidth;
    		Arrays.fill(image, pos, pos + borderwidth, color);
    	}
    }
    
    private class FilterBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
			final int[] image = buffer.image.array();
			final int[] scratch = buffer.scratch.array();
			
			final int borderwidth = (int)((double)buffer.imagewidth * _bordersize);
			final int width = buffer.imagewidth, xlast = width - borderwidth;
			final float[] blurmatrix = _blurkernel;
			final float sharpen = _sharpenAmount, sharpeninv = 1f - _sharpenAmount;
			final float levels = (255f / _posterizeLevels);
			
			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = borderwidth; x < xlast; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					float lum = 0, origlum = pixel & 0xff;

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
					
					// Apply unsharp mask
					lum = (lum - sharpen * Math.abs(origlum - lum)) / sharpeninv;
					
					// Clamp to 5% and 95%
					lum = Math.max(12.75f, Math.min(lum, 242.25f));
					
					// Decrease dynamic range 20%
					lum = Math.round(lum / 1.2f) * 1.2f;
					
					// Posterize to 90 levels
					lum = Math.round(lum / levels) * levels;

					// Clamp to 5% and 95%
					lum = Math.max(12.75f, Math.min(lum, 242.25f));
					
					// Output the pixel, but keep alpha channel intact
					int color = Math.max(0, Math.min((int)lum, 255));
					scratch[i] = (pixel & 0xff000000) | (color << 16) | (color << 8) | color;
				}
			}
		}
    }
}
