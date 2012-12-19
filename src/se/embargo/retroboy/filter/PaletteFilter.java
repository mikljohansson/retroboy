package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IPalette;

public class PaletteFilter extends AbstractFilter {
	private final FilterBody _body = new FilterBody();
	private final IPalette _palette;
	private final float _factor;
	
	/**
	 * @param palette	Color palette in ABGR (Alpha, Blue, Green, Red)
	 */
	public PaletteFilter(IPalette palette, int contrast) {
		_palette = palette;
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
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
	public void accept(ImageBuffer buffer) {
		Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private class FilterBody implements ForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			final float factor = _factor;

			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];

					// Extract color components and apply the contrast adjustment
					final int r = Math.min(Math.max(0, (int)(factor * ((pixel & 0xff) - 128.0f) + 128.0f)), 255),
							  g = Math.min(Math.max(0, (int)(factor * (((pixel & 0xff00) >> 8) - 128.0f) + 128.0f)), 255),
							  b = Math.min(Math.max(0, (int)(factor * (((pixel & 0xff0000) >> 16) - 128.0f) + 128.0f)), 255);

					// Output the pixel, but keep alpha channel intact
					image[i] = (pixel & 0xff000000) | (_palette.getNearestColor(r, g, b) & 0xffffff);
				}
			}
		}
    }
}
