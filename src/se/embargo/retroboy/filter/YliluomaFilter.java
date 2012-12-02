package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IColorDistance;
import se.embargo.retroboy.graphic.DitherMatrixes;
import android.content.Context;

/**
 * 2-tone Yliluoma dithering.
 * @link	http://bisqwit.iki.fi/story/howto/dither/jy/
 */
public class YliluomaFilter extends AbstractColorFilter {
	/**
	 * Number of integers per bucket.
	 */
	private static final int COLOR_BUCKET_SIZE = 3;
	
	/**
	 * Dither matrix.
	 */
	private static final int[] _matrix = DitherMatrixes.MATRIX_8x8;
	
	/**
	 * Length of matrix side.
	 */
	private static final int _patternsize = (int)Math.sqrt(_matrix.length);

	/**
	 * Ratio of color mixing.
	 */
	private static final int _mixingRatio = _matrix.length / 2;

	/**
	 * Version number for the cache files
	 */
	private static final int CACHE_VERSION_NUMBER = 0x05 | (_matrix.length << 16);

	/**
	 * Parallel functor used to process frames.
	 */
	private final ForBody<ImageBuffer> _body = new ColorBody();
	
	public YliluomaFilter(Context context, IColorDistance distance, int[] palette) {
		super("yduotone", context, distance, palette, COLOR_BUCKET_SIZE, CACHE_VERSION_NUMBER);
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
			
			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % _patternsize) * _patternsize;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					final int threshold = _matrix[x % _patternsize + yt];
					
					final int r1 = pixel & 0x000000ff;
					final int g1 = (pixel & 0x0000ff00) >> 8;
					final int b1 = (pixel & 0x00ff0000) >> 16;
					
					final int bucket = ((r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb)) * COLOR_BUCKET_SIZE;
					final int ratio = _buckets[bucket + 2];
					image[i] = (pixel & 0xff000000) | (threshold < ratio ? _buckets[bucket + 1] : _buckets[bucket]);
				}
			}
		}
    }
    
    protected void initBucket(final int bucket, final int r, final int g, final int b) {
    	double minpenalty = Double.MAX_VALUE;
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

	            int ratio = _mixingRatio;
	            if (color1 != color2) {
	                // Determine the ratio of mixing for each channel.
	                //   solve(r1 + ratio*(r2-r1)/_matrix.length = r, ratio)
	                // Take a weighed average of these three ratios according to the
	                // perceived luminosity of each channel (according to CCIR 601).
	                ratio = ((r2 != r1 ? 299*_matrix.length * (r - r1) / (r2-r1) : 0)
	                      +  (g2 != g1 ? 587*_matrix.length * (g - g1) / (g2-g1) : 0)
	                      +  (b2 != b1 ? 114*_matrix.length * (b - b1) / (b2-b1) : 0))
	                      / ((r2 != r1 ? 299 : 0)
	                       + (g2 != g1 ? 587 : 0)
	                       + (b2 != b1 ? 114 : 0));
	                
	                ratio = Math.max(0, Math.min(ratio, _matrix.length - 1));
	            }

	            // Determine what mixing them in this proportion will produce
	            int r0 = r1 + ratio * (r2-r1) / _matrix.length;
	            int g0 = g1 + ratio * (g2-g1) / _matrix.length;
	            int b0 = b1 + ratio * (b2-b1) / _matrix.length;
	            
	        	double rdist = _distance.get(r,g,b, r0,g0,b0),
	        	       r12dist = _distance.get(r1,g1,b1, r2,g2,b2);
	            
	        	double penalty = rdist + r12dist / 10 * (Math.abs(ratio - _mixingRatio) + _mixingRatio) / _matrix.length;
	            if (penalty < minpenalty) {
	                minpenalty = penalty;
	                _buckets[bucket] = (color1 & 0xffffff);
	                _buckets[bucket + 1] = (color2 & 0xffffff);
	                _buckets[bucket + 2] = ratio;
	            }
	        }
        }
    }
}
