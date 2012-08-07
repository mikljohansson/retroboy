package se.embargo.retroboy.filter;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HalftoneFilter implements IImageFilter {
    private static final int _patternsize = 6;
	private static final int[] _thresholds = new int[_patternsize * _patternsize];
	
	private Queue<int[]> _bufferpool = new ConcurrentLinkedQueue<int[]>();
    
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.imagewidth, height = buffer.imageheight;
		
		// Allocate a buffer to hold the luminance
		final int cellwidth = width / _patternsize + _patternsize, cellheight = height / _patternsize + _patternsize;
		int[] cells = _bufferpool.poll();
		
		if (cells == null || cells.length != cellwidth * cellheight) {
			cells = new int[cellwidth * cellheight];
		}
		
		Arrays.fill(cells, 0);
		
		// Summarize the luminance for each dithering cell
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int ii = x + y * width;
				final int oi = (x / _patternsize) + (y / _patternsize) * cellwidth;
				
				final int mono = image[ii] & 0xff;
				cells[oi] += mono;
			}
		}
		
		// Apply the threshold for each dithering cell
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int ii = (x / _patternsize) + (y / _patternsize) * cellwidth;
				final int oi = x + y * width;
				
				// Apply the threshold
				final int threshold = _thresholds[x % _patternsize + (y % _patternsize) * _patternsize];
				final int mono = (cells[ii] / _thresholds.length) & 0xff;
				final int lum = mono <= threshold ? 0 : 255;
				image[oi] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
			}
		}
		
		_bufferpool.offer(cells);
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
}
