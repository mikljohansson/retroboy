package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IColorDistance;
import se.embargo.retroboy.graphic.DitherMatrixes;
import android.content.Context;

/**
 * Simple 2-tone raster dithering as used on most Amstrad CPC games.
 */
public class AmstradFilter extends AbstractColorFilter {
	/**
	 * Number of integers per bucket.
	 */
	private static final int COLOR_BUCKET_SIZE = 2;
	
	/**
	 * Version number for the cache files
	 */
	private static final int CACHE_VERSION_NUMBER = 0x03;

	/**
	 * Dither matrix.
	 */
	private static final int[] _matrix = DitherMatrixes.MATRIX_8x8;
	
	/**
	 * Length of matrix side.
	 */
	private static final int _patternsize = 8;

	/**
	 * Ratio of color mixing.
	 */
	private static final int _mixingRatio = 32;
	
	/**
	 * Parallel functor used to process frames.
	 */
	private final ForBody<ImageBuffer> _body = new ColorBody();
	
	public AmstradFilter(Context context, IColorDistance distance, int[] palette) {
		super("amstrad", context, distance, palette, COLOR_BUCKET_SIZE, CACHE_VERSION_NUMBER);
	}
    
    @Override
	public void process(ImageBuffer buffer) {
    	Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private class ColorBody implements ForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			
			// Factor used to compensate for too dark or bright images
			final float factor = 128f / buffer.threshold;

			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % _patternsize) * _patternsize;
				
				for (int x = 0; x < width; x += 2) {
					final int i = x + yi;
					final int pixel = image[i];
					final int threshold = _matrix[(x >> 1) % _patternsize + yt];
					
					final int r1 = Math.max(0, Math.min((int)((float)(pixel & 0x000000ff) * factor) + threshold - 32, 255));
					final int g1 = Math.max(0, Math.min((int)((float)((pixel & 0x0000ff00) >> 8) * factor) + threshold - 32, 255));
					final int b1 = Math.max(0, Math.min((int)((float)((pixel & 0x00ff0000) >> 16) * factor) + threshold - 32, 255));

					final int bucket = ((r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb)) * COLOR_BUCKET_SIZE;
					image[i + 1] = image[i] = (pixel & 0xff000000) | _buckets[bucket + (((x >> 1) & 0x01) ^ (y & 0x01))];
				}
			}
		}
    }

    protected void initBucket(final int bucket, final int r, final int g, final int b) {
        int minpenalty = Integer.MAX_VALUE;
        for (int i = 0; i < _palette.length; ++i) {
	        for (int j = i; j < _palette.length; ++j) {
	            // Determine the two component colors
	            final int color1 = _palette[i], color2 = _palette[j];
	            final int r1 = color1 & 0xff, 
	            		  g1 = (color1 >> 8) & 0xff, 
	            		  b1 = (color1 >> 16) & 0xff;
	        
	            final int r2 = color2 & 0xff, 
	            		  g2 = (color2 >> 8) & 0xff, 
	            		  b2 = (color2 >> 16) & 0xff;

	            // Determine what mixing them in this proportion will produce
	            int r0 = r1 + _mixingRatio * (r2-r1) / 64;
	            int g0 = g1 + _mixingRatio * (g2-g1) / 64;
	            int b0 = b1 + _mixingRatio * (b2-b1) / 64;
	            
	        	int rdist = _distance.get(r,g,b, r0,g0,b0),
	        	    r12dist = _distance.get(r1,g1,b1, r2,g2,b2);
	            
	            int penalty = rdist + r12dist / 20;
	            if (penalty < minpenalty) {
	                minpenalty = penalty;
	                _buckets[bucket] = (color1 & 0xffffff);
	                _buckets[bucket + 1] = (color2 & 0xffffff);
	            }
	        }
        }
    }
}
