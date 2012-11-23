package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IColorDistance;
import se.embargo.retroboy.graphic.DitherMatrixes;
import android.content.Context;

/**
 * 3-tone Yliluoma dithering.
 * @link	http://bisqwit.iki.fi/story/howto/dither/jy/
 */
public class YliluomaTriFilter extends AbstractColorFilter {
	/**
	 * Number of integers per bucket.
	 */
	private static final int COLOR_BUCKET_SIZE = 5;
	
	/**
	 * Version number for the cache files
	 */
	private static final int CACHE_VERSION_NUMBER = 0x04;

	/**
	 * Dither matrix.
	 */
	private static final int[] _matrix = DitherMatrixes.MATRIX_8x8;
	
	/**
	 * Length of matrix side.
	 */
	private static final int _patternsize = 8;

	/**
	 * Parallel functor used to process frames.
	 */
	private final ForBody<ImageBuffer> _body = new ColorBody();
	
	public YliluomaTriFilter(Context context, IColorDistance distance, int[] palette) {
		super("ytritone", context, distance, palette, COLOR_BUCKET_SIZE, CACHE_VERSION_NUMBER);
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
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					
					final int r1 = Math.min((int)((float)(pixel & 0x000000ff) * factor), 255);
					final int g1 = Math.min((int)((float)((pixel & 0x0000ff00) >> 8) * factor), 255);
					final int b1 = Math.min((int)((float)((pixel & 0x00ff0000) >> 16) * factor), 255);
					
					final int bucket = ((r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb)) * COLOR_BUCKET_SIZE;
					final int ratio = _buckets[bucket + 4];
					
					if (ratio == 256) {
						image[i] = (pixel & 0xff000000) | (_buckets[bucket + ((y & 0x01) * 2) + (x & 0x01)]);
					}
					else {
						final int threshold = _matrix[x % _patternsize + yt];
						image[i] = (pixel & 0xff000000) | (threshold < ratio ? _buckets[bucket + 1] : _buckets[bucket]);
					}
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

	            int ratio = 32;
	            if (color1 != color2) {
	                // Determine the ratio of mixing for each channel.
	                //   solve(r1 + ratio*(r2-r1)/64 = r, ratio)
	                // Take a weighed average of these three ratios according to the
	                // perceived luminosity of each channel (according to CCIR 601).
	                ratio = ((r2 != r1 ? 299*64 * (r - r1) / (r2-r1) : 0)
	                      +  (g2 != g1 ? 587*64 * (g - g1) / (g2-g1) : 0)
	                      +  (b2 != b1 ? 114*64 * (b - b1) / (b2-b1) : 0))
	                      / ((r2 != r1 ? 299 : 0)
	                       + (g2 != g1 ? 587 : 0)
	                       + (b2 != b1 ? 114 : 0));
	                
	                ratio = Math.max(0, Math.min(ratio, 63));
	            }

	            // Determine what mixing them in this proportion will produce
	            int r0 = r1 + ratio * (r2-r1) / 64;
	            int g0 = g1 + ratio * (g2-g1) / 64;
	            int b0 = b1 + ratio * (b2-b1) / 64;
	            
	        	int rdist = _distance.get(r,g,b, r0,g0,b0),
	        	    r12dist = _distance.get(r1,g1,b1, r2,g2,b2);
	            
	            int penalty = rdist + r12dist / 10 * (Math.abs(ratio - 32) + 32) / 64;
	            if (penalty < minpenalty) {
	                minpenalty = penalty;
	                _buckets[bucket] = color1;
	                _buckets[bucket + 1] = color2;
	                _buckets[bucket + 4] = ratio;
	            }
	            
	            if (i != j) {
	            	for (int k = 0; k < _palette.length; k++) {
	                    if (k == i || k == j) {
	                    	continue;
	                    }
	                    
	                    // 50% index3, 25% index2, 25% index1
	                    final int color3 = _palette[k];
	                    final int r3 = color3 & 0xff, 
	    	            		  g3 = (color3 >> 8) & 0xff, 
	    	            		  b3 = (color3 >> 16) & 0xff;
	            	
	    	            r0 = (r1 + r2 + r3*2) / 4;
	    	            g0 = (g1 + g2 + g3*2) / 4;
	    	            b0 = (b1 + b2 + b3*2) / 4;
	    	            rdist = _distance.get(r,g,b, r0,g0,b0);
	    	            
	    	            penalty = rdist + r12dist / 40 + _distance.get((r1+g1)/2,(g1+g2)/2,(b1+b2)/2, r3,g3,b3) / 40;
	    	            if (penalty < minpenalty) {
	    	                minpenalty = penalty;
	    	                _buckets[bucket] = (color3 & 0xffffff);
	    	                _buckets[bucket + 1] = (color1 & 0xffffff);
	    	                _buckets[bucket + 2] = (color2 & 0xffffff);
	    	                _buckets[bucket + 3] = (color3 & 0xffffff);
	    	                _buckets[bucket + 4] = 256;
	    	            }
	            	}
	            }
	        }
        }
    }
}
