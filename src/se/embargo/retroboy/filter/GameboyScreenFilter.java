package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

public class GameboyScreenFilter implements IImageFilter {
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
				final int yi = y * width;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final float mono = image[i] & 0xff;
					final int lum = Math.max(0, Math.min((int)(mono * factor), 255));
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
     * @link	http://en.wikipedia.org/wiki/List_of_video_game_console_palettes#Original_Game_Boy
     */
	{
		final int[] palette = {0xff0f380f, 0xff306230, 0xff0fac8b, 0xff0fbc9b};
		for (int i = 0; i < 256; i++) {
			_palette[i] = palette[i / 64];
		}
	}
}
