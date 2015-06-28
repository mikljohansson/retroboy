package se.embargo.retroboy.filter;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import se.embargo.core.concurrent.IMapReduceBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.core.graphic.Levels;
import android.util.Log;

/**
 * Input filter used when processing existing images with a monochrome filter.
 */
public class MonochromeFilter extends AbstractFilter {
	private static final String TAG = "MonochromeFilter";
	
	private final Queue<int[]> _bufferpool = new ArrayBlockingQueue<int[]>(256);
	private final IMapReduceBody<ImageBuffer, int[]> _body = new FilterBody();
	private final float _factor;
	private final boolean _autoexposure;
	
	public MonochromeFilter(int contrast, boolean autoexposure) {
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
		_autoexposure = autoexposure;
	}

	@Override
	public void accept(ImageBuffer buffer) {
		// Convert to monochrome and calculate the histogram in parallel
		int[] histogram = Parallel.mapReduce(_body, buffer, 0, buffer.imagewidth * buffer.imageheight);
	
		// Calculate the global Otsu threshold
		if (_autoexposure) {
			buffer.threshold = Levels.getThreshold(
				buffer.imagewidth, buffer.imageheight, buffer.image.array(), histogram);
			Log.d(TAG, "Threshold: " + buffer.threshold);
		}
		
		// Release histogram back to pool
		_bufferpool.offer(histogram);
	}

    private class FilterBody implements IMapReduceBody<ImageBuffer, int[]> {
		@Override
		public int[] reduce(int[] lhs, int[] rhs) {
			for (int i = 0; i < lhs.length; i++) {
				lhs[i] += rhs[i];
			}
			
			_bufferpool.offer(rhs);
			return lhs;
		}

		@Override
		public int[] map(ImageBuffer buffer, int it, int last) {
			final int[] image = buffer.image.array();
			final float factor = _factor;
			
			// Space to hold an image histogram
			int[] histogram = _bufferpool.poll();
			if (histogram == null) {
				histogram = new int[256];
			}
			
			Arrays.fill(histogram, 0);
			
			for (int i = it; i != last; i++) {
				final int pixel = image[i];
				
				// Convert to monochrome
				final float lum = (0.299f * (pixel & 0xff) + 0.587f * ((pixel & 0xff00) >> 8) + 0.114f * ((pixel & 0xff0000) >> 16));
				
				// Apply the contrast adjustment
				final int lumi = Math.min(Math.max(0, (int)(factor * (lum - 128.0f) + 128.0f)), 255);

				// Build the histogram used to calculate the global threshold
				histogram[lumi]++;
				
				// Output the pixel, but keep alpha channel intact
				image[i] = (pixel & 0xff000000) | (lumi << 16) | (lumi << 8) | lumi;
			}

			return histogram;
		}
    }
}
