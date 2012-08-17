package se.embargo.retroboy.filter;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

public class HalftoneFilter implements IImageFilter {
    private static final int _patternsize = 6;
	private static final float[] _thresholds = new float[_patternsize * _patternsize];
	
	private static class FilterItem {
		public ImageBuffer buffer;
		final public int[] cells;

		public FilterItem(int cellwidth, int cellheight) {
			cells = new int[cellwidth * cellheight];
		}
	}
	
	private Queue<FilterItem> _bufferpool = new ArrayBlockingQueue<FilterItem>(16);
	private FilterBody _body = new FilterBody();
    
    @Override
	public void accept(ImageBuffer buffer) {
		// Allocate a buffer to hold the luminance
		final int width = buffer.imagewidth, height = buffer.imageheight;
		final int cellwidth = width / _patternsize + _patternsize, cellheight = height / _patternsize + _patternsize;
		FilterItem item = _bufferpool.poll();
		
		if (item == null || item.cells.length != cellwidth * cellheight) {
			item = new FilterItem(cellwidth, cellheight);
		}
		
		Arrays.fill(item.cells, 0);
		item.buffer = buffer;

		// Must process chunks of whole dithering cells
		int grainsize = height / Parallel.getNumberOfCores() / 4;
		grainsize -= grainsize % _patternsize;
		grainsize = Math.max(grainsize, _patternsize);
		
		// Process lines of dithering cells in parallel
		Parallel.forRange(_body, item, 0, height, grainsize);
		
		// Release work item back to pool
		_bufferpool.offer(item);
	}
    
    private class FilterBody implements ForBody<FilterItem> {
		@Override
		public void run(FilterItem item, int it, int last) {
	    	final int[] image = item.buffer.image.array(), cells = item.cells;
			final int width = item.buffer.imagewidth;
			final int cellwidth = width / _patternsize + _patternsize;

			// Summarize the luminance for each dithering cell
			for (int y = it; y < last; y++) {
				final int yi = y * width, 
						  yo = (y / _patternsize) * cellwidth;
				
				for (int x = 0; x < width; x++) {
					final int ii = x + yi;
					final int oi = (x / _patternsize) + yo;
					cells[oi] += image[ii] & 0xff;
				}
			}
			
			// Factor used to offset the threshold to compensate for too dark or bright images
			final float factor = (float)item.buffer.threshold / 128;
			
			// Apply the threshold for each dithering cell
			for (int y = it; y < last; y++) {
				final int yo = y * width, 
						  yi = (y / _patternsize) * cellwidth, 
						  yt = (y % _patternsize) * _patternsize;

				for (int x = 0; x < width; x++) {
					final int ii = (x / _patternsize) + yi;
					final int oi = x + yo;
					final int mono = (cells[ii] / _thresholds.length) & 0xff;
					
					// Apply the threshold
					final int threshold = (int)(_thresholds[x % _patternsize + yt] * factor);
					final int lum = mono <= threshold ? 0 : 255;
					image[oi] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
				}
			}
		}
    }
	
    /**
     * Static constructor to initialize the matrix
     */
    {
    	for (int y = 0; y < _patternsize; y++) {
    		for(int x = 0; x < _patternsize; x++){
    			final int i = x + y * _patternsize;
    			final double threshold = (cos1((double)x / _patternsize) + cos1((double)y / _patternsize) + 2.0) / 4.0 * 0xff;
    			_thresholds[i] = (int)Math.min(Math.max(1.0, Math.round(threshold)), 254.0);
    		}
    	}
    }

	private static double cos1(double v) { 
		return Math.cos(2 * Math.PI * v); 
	}

	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		return 0;
	}

	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		return 0;
	}
}
