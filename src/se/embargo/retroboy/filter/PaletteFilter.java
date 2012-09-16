package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.Palettes;

public class PaletteFilter implements IImageFilter {
	private final FilterBody _body = new FilterBody();
	private final int[] _palette;
	
	/**
	 * @param palette	Color palette in ABGR (Alpha, Blue, Green, Red)
	 */
	public PaletteFilter(int[] palette) {
		_palette = palette;
	}
    
    @Override
	public void accept(ImageBuffer buffer) {
		Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private class FilterBody implements ForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;

			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					image[i] = Palettes.getNearestColor(image[i], _palette);
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
}
