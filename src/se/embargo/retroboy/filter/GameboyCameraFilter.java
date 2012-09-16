package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

public class GameboyCameraFilter implements IImageFilter {
	private static final int[] _matrix = new int[] {
    	1, 49, 13, 61, 4, 52, 16, 63, 
    	33, 17, 45, 29, 36, 20, 48, 32, 
    	9, 57, 5, 53, 12, 60, 8, 56, 
    	41, 25, 37, 21, 44, 28, 40, 24, 
    	3, 51, 15, 63, 2, 50, 14, 62, 
    	35, 19, 47, 31, 34, 18, 46, 30, 
    	11, 59, 7, 55, 10, 58, 6, 54, 
    	43, 27, 39, 23, 42, 26, 38, 22};

	private static final int[] _palette = new int[256];
	private static final FilterBody _body = new FilterBody();
    
    @Override
	public void accept(ImageBuffer buffer) {
		Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private static class FilterBody implements ForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			
			// Factor used to compensate for too dark or bright images
			final float factor = 128f / buffer.threshold;

			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % 8) * 8;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final float mono = image[i] & 0xff;
					final int lum = Math.min((int)(mono * factor) + _matrix[x % 8 + yt], 255);
					image[i] = _palette[lum];
				}
			}
		}
    }

	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		return 0;
	}

	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		return 0;
	}

    /**
     * Static constructor to initialize the palette
     */
	{
		final int[] palette = {0xff000000, 0xff858585, 0xffaaaaaa, 0xffffffff};
		for (int i = 0; i < 256; i++) {
			_palette[i] = palette[i / 64];
		}
	}
}
