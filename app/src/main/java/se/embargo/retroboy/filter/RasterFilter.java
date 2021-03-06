package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IColorDistance;
import android.content.Context;

/**
 * 2-tone raster dithering as used on most Amstrad CPC games.
 */
public class RasterFilter extends AbstractColorFilter {
	/**
	 * Version number for the cache files
	 */
	private static final int CACHE_VERSION_NUMBER = 10;

	/**
	 * Number of integers per bucket.
	 */
	private static final int COLOR_BUCKET_SIZE = 2;

	/**
	 * Dither matrix.
	 */
	private final int[] _matrix;
	
	/**
	 * Length of matrix side.
	 */
	private final int _patternsize;

	/**
	 * Ratio of color mixing.
	 */
	private final int _mixingratio;

	/**
	 * Divisor for the too-far-apart color penalty.
	 */
	private final double _penaltyDivisor;
	
	/**
	 * Parallel functor used to process frames.
	 */
	private final IForBody<ImageBuffer> _body = new ColorBody();
	
	/**
	 * @param context		Context running the filter
	 * @param distance		Measure for color distance
	 * @param colors		Palette of available colors
	 * @param matrix		Dithering matrix to use
	 * @param rasterlevel	Level of rastering to apply
	 */
	public RasterFilter(Context context, IColorDistance distance, int[] colors, int[] matrix, int rasterlevel) {
		super("raster-" + rasterlevel, context, distance, colors, COLOR_BUCKET_SIZE, CACHE_VERSION_NUMBER);
		_matrix = matrix;
		_patternsize = (int)Math.sqrt(_matrix.length);
		_mixingratio = _matrix.length / 2;
		_penaltyDivisor = (double)rasterlevel / 10d;
		
		// Initialize buckets after members are initialized
		init();
	}
    
    @Override
	public void process(ImageBuffer buffer) {
    	Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private class ColorBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			
			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % _patternsize) * _patternsize;
				
				for (int x = 0; x < width; x += 2) {
					final int i = x + yi;
					final int pixel = image[i];
					final int threshold = _matrix[(x >> 1) % _patternsize + yt];
					
					final int r1 = Math.max(0, Math.min((pixel & 0x000000ff) + threshold - _mixingratio, 255));
					final int g1 = Math.max(0, Math.min(((pixel & 0x0000ff00) >> 8) + threshold - _mixingratio, 255));
					final int b1 = Math.max(0, Math.min(((pixel & 0x00ff0000) >> 16) + threshold - _mixingratio, 255));

					final int bucket = ((r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb)) * COLOR_BUCKET_SIZE;
					image[i + 1] = image[i] = (pixel & 0xff000000) | _buckets[bucket + (((x >> 1) & 0x01) ^ (y & 0x01))];
				}
			}
		}
    }

    protected final void initBucket(final int bucket, final int r, final int g, final int b) {
    	double minpenalty = Double.MAX_VALUE;
        for (int i = 0; i < _colors.length; ++i) {
	        for (int j = i; j < _colors.length; ++j) {
	            // Determine the two component colors
	            final int color1 = _colors[i], color2 = _colors[j];
	            final int r1 = color1 & 0xff, 
	            		  g1 = (color1 >> 8) & 0xff, 
	            		  b1 = (color1 >> 16) & 0xff;
	        
	            final int r2 = color2 & 0xff, 
	            		  g2 = (color2 >> 8) & 0xff, 
	            		  b2 = (color2 >> 16) & 0xff;

	            // Determine what mixing them in this proportion will produce
	            int r0 = r1 + ((r2-r1) >> 1);
	            int g0 = g1 + ((g2-g1) >> 1);
	            int b0 = b1 + ((b2-b1) >> 1);
	            
	            double penalty = _distance.get(r,g,b, r0,g0,b0),
	        	       r12dist = _distance.get(r1,g1,b1, r2,g2,b2);
	            
	            // Penalize color combinations too far apart
	            penalty += r12dist / _penaltyDivisor;
	            
	            if (penalty < minpenalty) {
	                minpenalty = penalty;
	                _buckets[bucket] = (color1 & 0xffffff);
	                _buckets[bucket + 1] = (color2 & 0xffffff);
	            }
	        }
        }
    }
}
