package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IPalette;

public class BayerFilter extends AbstractFilter {
	private static final int[] _matrix64 = new int[] {
    	1, 49, 13, 61, 4, 52, 16, 63, 
    	33, 17, 45, 29, 36, 20, 48, 32, 
    	9, 57, 5, 53, 12, 60, 8, 56, 
    	41, 25, 37, 21, 44, 28, 40, 24, 
    	3, 51, 15, 63, 2, 50, 14, 62, 
    	35, 19, 47, 31, 34, 18, 46, 30, 
    	11, 59, 7, 55, 10, 58, 6, 54, 
    	43, 27, 39, 23, 42, 26, 38, 22};

	private static final int[] _matrix16 = new int[] {
    	1, 9, 3, 11, 
    	13, 5, 15, 7, 
    	4, 12, 2, 10, 
    	15, 8, 14, 6};

	private final int[] _matrix;
	private final int _patternsize;
	private final ForBody<ImageBuffer> _body;
	
	public BayerFilter(IPalette palette, boolean color) {
		switch (palette.getPaletteSize()) {
			case 16:
				_matrix = _matrix16;
				_patternsize = 4;
				break;

			case 4:
				_matrix = _matrix64;
				_patternsize = 8;
				break;
				
			default:
				throw new IllegalArgumentException("Invalid palette size");
		}
		
		if (color) {
			_body = new ColorBody(palette);
		}
		else {
			_body = new MonochromeBody(palette);
		}
	}
    
    @Override
	public void accept(ImageBuffer buffer) {
		Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private class ColorBody implements ForBody<ImageBuffer> {
    	private final IPalette _palette;
    	
		public ColorBody(IPalette palette) {
			_palette = palette;
		}

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
					final int threshold = _matrix[x % _patternsize + yt];
					final int r = Math.min((int)((float)(pixel & 0x000000ff) * factor) + threshold, 255);
					final int g = Math.min((int)((float)((pixel & 0x0000ff00) >> 8) * factor) + threshold, 255);
					final int b = Math.min((int)((float)((pixel & 0x00ff0000) >> 16) * factor) + threshold, 255);
					image[i] = _palette.getNearestColor(r, g, b);
				}
			}
		}
    }

    private class MonochromeBody implements ForBody<ImageBuffer> {
    	private final int[] _palette = new int[256];

    	public MonochromeBody(IPalette palette) {
			for (int i = 0; i < 256; i++) {
				_palette[i] = palette.getNearestColor(i, i, i);
			}
		}

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
					final float mono = image[i] & 0xff;
					final int threshold = _matrix[x % _patternsize + yt];
					final int lum = Math.min((int)(mono * factor) + threshold, 255);
					image[i] = _palette[lum];
				}
			}
		}
    }

    @Override
    public boolean isColorFilter() {
    	return _body instanceof ColorBody;
    }
}
