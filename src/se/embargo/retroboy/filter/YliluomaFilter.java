package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

public class YliluomaFilter extends AbstractFilter {
	private static final int[] _matrix = new int[] {
    	1, 49, 13, 61, 4, 52, 16, 63, 
    	33, 17, 45, 29, 36, 20, 48, 32, 
    	9, 57, 5, 53, 12, 60, 8, 56, 
    	41, 25, 37, 21, 44, 28, 40, 24, 
    	3, 51, 15, 63, 2, 50, 14, 62, 
    	35, 19, 47, 31, 34, 18, 46, 30, 
    	11, 59, 7, 55, 10, 58, 6, 54, 
    	43, 27, 39, 23, 42, 26, 38, 22};
	
	private static final int _patternsize = 8;

	private static final int _bits = 4;
	private static final int _step = 8 - _bits;

	private final MixingPlan[] _buckets = new MixingPlan[1 << (_bits * 3)];
	
	private final int _gsb = _bits, 
					  _bsb = _bits * 2;
	
	private final int _rm = (1 << _bits) - 1, 
					  _gm = _rm << _gsb, 
					  _bm = _rm << _bsb;
	
	private final int[] _palette;
	private final ForBody<ImageBuffer> _body = new ColorBody();
	
	public YliluomaFilter(int[] palette) {
		_palette = palette;
		
		// Initialize the cached mixing plans
		Parallel.forRange(new ForBody<MixingPlan[]>() {
			@Override
			public void run(MixingPlan[] item, int it, int last) {
				for (int i = it; i < last; i++) {
					final int r = (i & _rm) << _step,
						  	  g = ((i & _gm) >> _gsb) << _step,
						  	  b = ((i & _bm) >> _bsb) << _step;
				
					item[i] = getMixingPlan(r, g, b);
				}
			}
		}, _buckets, 0, _buckets.length);
	}
    
    @Override
	public void accept(ImageBuffer buffer) {
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
					
					final int threshold = _matrix[x % _patternsize + yt];
					final int bucket = (r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb);
					
					final MixingPlan plan = _buckets[bucket];
					image[i] = threshold < plan.ratio ? plan.c1 : plan.c0;
				}
			}
		}
    }

    @Override
    public boolean isColorFilter() {
    	return true;
    }
    
    private static class MixingPlan {
    	public int c0, c1, ratio;
    }
    
    private static int getDistance(
	        int r1,int g1,int b1,
	        int r2,int g2,int b2) {
		final int l1 = r1 * 299 + g1 * 587 + b1 * 114;
		final int l2 = r2 * 299 + g2 * 587 + b2 * 114;
		final int dl = (l1 - l2) / 1000;
		
		final int dr = r1 - r2,
				  dg = g1 - g2,
				  db = b1 - b2;
		
		return (dr * dr * 299 + dg * dg * 587 + db * db * 114) / 4000 * 3 + dl * dl;
    }
    
    private int getMixingError(
	    	int r,int g,int b,
	    	int r0,int g0,int b0,
	        int r1,int g1,int b1,
	        int r2,int g2,int b2, 
	        int ratio) {
    	return getDistance(r,g,b, r0,g0,b0) + getDistance(r1,g1,b1, r2,g2,b2) / 10 * (Math.abs(ratio - 32) + 32) / 64;
    }
    
    private MixingPlan getMixingPlan(final int r, final int g, final int b) {
        MixingPlan result = new MixingPlan();
        
        int least_penalty = Integer.MAX_VALUE;
        for (int i = 0; i < _palette.length; ++i) {
	        for (int j = i; j < _palette.length; ++j) {
	            // Determine the two component colors
	            int color1 = _palette[i], color2 = _palette[j];
	            int r1 = color1 & 0xff, 
	            	g1 = (color1 >> 8) & 0xff, 
	            	b1 = (color1 >> 16) & 0xff;
	        
	            int r2 = color2 & 0xff, 
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
	            
	            int penalty = getMixingError(
	                r,g,b, r0,g0,b0, r1,g1,b1, r2,g2,b2,
	                ratio);
	            
	            if (penalty < least_penalty) {
	                least_penalty = penalty;
	                result.c0 = color1;
	                result.c1 = color2;
	                result.ratio = ratio;
	            }
	        }
        }
        
        return result;
    }
}
