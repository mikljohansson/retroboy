package se.embargo.retroboy.filter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.core.concurrent.ProgressTask;
import se.embargo.retroboy.R;
import android.content.Context;
import android.util.Log;

public class YliluomaTriFilter extends AbstractFilter {
	private static final String TAG = "YliluomaTriFilter";
	
	/**
	 * Version number for the cache files
	 */
	private static final int CACHE_VERSION_NUMBER = 0x02;

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

	private final int[] _buckets = new int[(1 << (_bits * 3)) * 5];
	private final CountDownLatch _init = new CountDownLatch(1);
	
	private final int _gsb = _bits, 
					  _bsb = _bits * 2;
	
	private final int _rm = (1 << _bits) - 1, 
					  _gm = _rm << _gsb, 
					  _bm = _rm << _bsb;
	
	private final Context _context;
	private final int[] _palette;
	private final ForBody<ImageBuffer> _body = new ColorBody();
	
	public YliluomaTriFilter(Context context, int[] palette) {
		_context = context;
		_palette = palette;
		
		// Check for cached mixing plans
    	long ts = System.nanoTime();
		int hash = 0;
		for (int color : _palette) {
			hash ^= color;
		}
		
		// Read cached mixing plan
		String filename = "ytritone-" + Integer.toHexString(hash) + ".bin";
		try {
			DataInputStream is = new DataInputStream(new BufferedInputStream(_context.openFileInput(filename)));
			int version = is.readInt();
			
			if (version == CACHE_VERSION_NUMBER) {
				for (int i = 0; i < _buckets.length; i++) {
					_buckets[i] = is.readInt();
				}

				is.close();
				_init.countDown();
				Log.i(TAG, "Cached init: " + (((double)System.nanoTime() - (double)ts) / 1000000000d) + "s");
				return;
			}

			is.close();
		}
		catch (IOException e) {}

		// Show a progress dialog while building the mixing plans
		if (Parallel.isGuiThread()) {
			new InitializeTask(_context, filename).execute();
		}
		else {
			init(filename);
		}
	}
    
    @Override
	public void accept(ImageBuffer buffer) {
    	try {
			_init.await();
		}
		catch (InterruptedException e) {}
		
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
					
					final int bucket = ((r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb)) * 5;
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

    @Override
    public boolean isColorFilter() {
    	return true;
    }
    
    private void init(String filename) {
    	long ts = System.nanoTime();

    	// Calculate mixing plans
		Parallel.forRange(new ForBody<int[]>() {
			@Override
			public void run(int[] item, int it, int last) {
				for (int i = it; i < last; i++) {
					final int r = (i & _rm) << _step,
						  	  g = ((i & _gm) >> _gsb) << _step,
						  	  b = ((i & _bm) >> _bsb) << _step;
				
					initBucket(i * 5, r, g, b);
				}
			}
		}, _buckets, 0, _buckets.length / 5);
		
		// Write mixing plans to cache
		try {
			DataOutputStream os = new DataOutputStream(new BufferedOutputStream(_context.openFileOutput(filename, Context.MODE_PRIVATE)));
			os.writeInt(CACHE_VERSION_NUMBER);
			
			for (int bucket : _buckets) {
				os.writeInt(bucket);
			}
			
			os.flush();
			os.close();
		}
		catch (IOException e) {}

		_init.countDown();
		Log.i(TAG, "Full init: " + (((double)System.nanoTime() - (double)ts) / 1000000000d) + "s");
    }
    
    private class InitializeTask extends ProgressTask<Void, Void, Void> {
		private final String _filename;
    	
    	public InitializeTask(Context context, String filename) {
			super(context, R.string.title_init_filter, R.string.msg_init_filter);
			_filename = filename;
		}

		@Override
		protected Void doInBackground(Void... params) {
			init(_filename);
			return null;
		}
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
    
    private void initBucket(final int bucket, final int r, final int g, final int b) {
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
	            
	        	int rdist = getDistance(r,g,b, r0,g0,b0),
	        	    r12dist = getDistance(r1,g1,b1, r2,g2,b2);
	            
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
	    	            rdist = getDistance(r,g,b, r0,g0,b0);
	    	            
	    	            penalty = rdist + r12dist / 40 + getDistance((r1+g1)/2,(g1+g2)/2,(b1+b2)/2, r3,g3,b3) / 40;
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
