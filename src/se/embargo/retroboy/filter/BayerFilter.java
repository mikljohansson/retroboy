package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IPalette;
import se.embargo.retroboy.graphic.DitherMatrixes;

public class BayerFilter extends AbstractFilter {
	public static final int[] _matrix = DitherMatrixes.MATRIX_8x8;
	private static final int _patternsize = 8;

	private final ForBody<ImageBuffer> _body;
	
	public BayerFilter(IPalette palette, boolean color) {
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
					final int r1 = Math.max(0, Math.min((int)((float)(pixel & 0x000000ff) * factor) + threshold - 32, 255));
					final int g1 = Math.max(0, Math.min((int)((float)((pixel & 0x0000ff00) >> 8) * factor) + threshold - 32, 255));
					final int b1 = Math.max(0, Math.min((int)((float)((pixel & 0x00ff0000) >> 16) * factor) + threshold - 32, 255));
					image[i] = (pixel & 0xff000000) | (_palette.getNearestColor(r1, g1, b1) & 0xffffff);
				}
			}
		}
    }

    private class MonochromeBody implements ForBody<ImageBuffer> {
    	private final int[] _palette = new int[256];

    	public MonochromeBody(IPalette palette) {
			for (int i = 0; i < 256; i++) {
				_palette[i] = (palette.getNearestColor(i, i, i) & 0xffffff);
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
					final int pixel = image[i];
					final float mono = pixel & 0xff;
					final int threshold = _matrix[x % _patternsize + yt];
					final int lum = Math.max(0,  Math.min((int)(mono * factor) + threshold - 32, 255));
					image[i] = (pixel & 0xff000000) | _palette[lum];
				}
			}
		}
    }

    @Override
    public boolean isColorFilter() {
    	return _body instanceof ColorBody;
    }
}
