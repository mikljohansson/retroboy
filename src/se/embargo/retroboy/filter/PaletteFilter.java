package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IPalette;
import se.embargo.retroboy.color.IPaletteSink;

public class PaletteFilter extends AbstractFilter implements IPaletteSink {
	private volatile IPalette _palette;
	private final FilterBody _body = new FilterBody();
	
	/**
	 * @param palette	Color palette in ABGR (Alpha, Blue, Green, Red)
	 */
	public PaletteFilter(IPalette palette) {
		_palette = palette;
	}
	
	@Override
	public void accept(IPalette palette) {
		_palette = palette;
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
    
    private class FilterBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final IPalette palette = _palette;
			final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;

			for (int y = it; y < last; y++) {
				final int yi = y * width;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];

					// Extract color components
					final int r1 = pixel & 0xff,
							  g1 = (pixel >> 8) & 0xff,
							  b1 = (pixel >> 16) & 0xff;

					// Output the pixel, but keep alpha channel intact
					image[i] = (pixel & 0xff000000) | (palette.getNearestColor(r1, g1, b1) & 0xffffff);
				}
			}
		}
    }
}
