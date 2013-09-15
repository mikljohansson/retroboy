package se.embargo.retroboy.filter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.core.concurrent.ProgressTask;
import se.embargo.retroboy.R;
import se.embargo.retroboy.color.BucketPalette;
import se.embargo.retroboy.color.DistancePalette;
import se.embargo.retroboy.color.IColorDistance;
import se.embargo.retroboy.color.IPalette;
import android.content.Context;
import android.util.Log;

public abstract class AbstractColorFilter extends AbstractFilter {
	private static final String TAG = "AbstractColorFilter";
	
	/**
	 * Number of most significant bits to store per color channel.
	 */
	private static final int _bits = 4;
	
	/**
	 * Number of bits to shift a color.
	 */
	protected static final int _step = 8 - _bits;
	
	/**
	 * Color bucket entries.
	 */
	protected final int[] _buckets;
	
	/**
	 * Latch to guard bucket initialization.
	 */
	private final CountDownLatch _init = new CountDownLatch(1);
	
	/**
	 * Number of bits to shift green and blue colors. 
	 */
	protected final int _gsb = _bits, 
					  _bsb = _bits * 2;
	
	/**
	 * Red, green and blue bit masks.
	 */
	private final int _rm = (1 << _bits) - 1, 
					  _gm = _rm << _gsb, 
					  _bm = _rm << _bsb;

	/**
	 * Name of filter.
	 */
	private final String _filtername;

	/**
	 * Handle on running Activity.
	 */
	private final Context _context;
	
	/**
	 * Palette instance.
	 */
	private final IPalette _palette;
	
	/**
	 * Colors in palette.
	 */
	protected final int[] _colors;
	
	/**
	 * Number of ints per bucket.
	 */
	private final int _bucketSize;
	
	/**
	 * Version of cache file.
	 */
	private final int _version;
	
	/**
	 * Function to measure the distance between colors.
	 */
	protected final IColorDistance _distance;
	
    public AbstractColorFilter(String filtername, Context context, IColorDistance distance, int[] colors, int bucketSize, int version) {
		_filtername = filtername;
    	_context = context;
		_distance = distance;
		_palette = new BucketPalette(new DistancePalette(distance, colors));
		_colors = colors;
		_bucketSize = bucketSize;
		_version = version;
		_buckets = new int[(1 << (_bits * 3)) * bucketSize];
	}

    /**
     * Apply this filter to a frame.
     * @param buffer	Frame to process
     */
    protected abstract void process(ImageBuffer buffer);
    
    /**
     * Initialize a bucket.
     * @param bucket	Index of bucket.
     * @param r			Red value to select a color for.
     * @param g			Green value to select a color for.
     * @param b			Blue value to select a color for.
     */
    protected abstract void initBucket(final int bucket, final int r, final int g, final int b);
    
    /**
     * Initialize the buckets.
     * @remark	This must be done after the child class is done initializing since virtual methods are called.
     */
    protected void init() {
    	long ts = System.nanoTime();
		
		// Check for cached mixing plans
		int hash = 0;
		for (int color : _colors) {
			hash ^= color;
		}
		
		// Read cached mixing plan
		String filename = _filtername + _distance + "-" + Integer.toHexString(hash) + ".bin";
		try {
			DataInputStream is = new DataInputStream(new BufferedInputStream(_context.openFileInput(filename)));
			int cachedversion = is.readInt();
			
			if (cachedversion == _version) {
				for (int i = 0; i < _buckets.length; i++) {
					_buckets[i] = is.readInt();
				}

				is.close();
				Log.i(TAG, "Cached init: " + (((double)System.nanoTime() - (double)ts) / 1000000000d) + "s");
				_init.countDown();
				return;
			}

			is.close();
		}
		catch (IOException e) {}
		
		// Show a progress dialog while building the mixing plans
		if (Parallel.isGuiThread()) {
			new InitializeTask(filename).execute();
		}
		else {
			init(filename);
		}
    }
    
    @Override
    public boolean isColorFilter() {
    	return true;
    }
    
    @Override
    public IPalette getPalette() {
    	return _palette;
    }
    
	@Override
	public final void accept(ImageBuffer buffer) {
    	try {
			_init.await();
		}
		catch (InterruptedException e) {}
    	
    	process(buffer);
	}
    
    private void init(String filename) {
    	long ts = System.nanoTime();
		
		// Calculate mixing plans
		Parallel.forRange(new IForBody<int[]>() {
			@Override
			public void run(int[] item, int it, int last) {
				for (int i = it; i < last; i++) {
					final int r = (i & _rm) << _step,
						  	  g = ((i & _gm) >> _gsb) << _step,
						  	  b = ((i & _bm) >> _bsb) << _step;
				
					initBucket(i * _bucketSize, r, g, b);
				}
			}
		}, _buckets, 0, _buckets.length / _bucketSize);

		// Write mixing plans to cache
		try {
			DataOutputStream os = new DataOutputStream(new BufferedOutputStream(_context.openFileOutput(filename, Context.MODE_PRIVATE)));
			os.writeInt(_version);
			
			for (int bucket : _buckets) {
				os.writeInt(bucket);
			}
			
			os.flush();
			os.close();
		}
		catch (IOException e) {}

		Log.i(TAG, "Full init: " + (((double)System.nanoTime() - (double)ts) / 1000000000d) + "s");
		_init.countDown();
    }
    
    private class InitializeTask extends ProgressTask<Void, Void, Void> {
		private final String _filename;
    	
    	public InitializeTask(String filename) {
			super(_context, R.string.title_init_filter, R.string.msg_init_filter);
			_filename = filename;
		}

		@Override
		protected Void doInBackground(Void... params) {
			init(_filename);
			return null;
		}
    }
}
