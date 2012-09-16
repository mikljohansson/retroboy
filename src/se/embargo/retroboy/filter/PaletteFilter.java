package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

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
					image[i] = getNearestColor(image[i]);
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
	
	private int getNearestColor(int pixel) {
		int color = 0, mindistance = 0xffffff;
		
		// Find palette color with minimum Euclidean distance to pixel 
		for (int i = 0; i < _palette.length; i++) {
			final int d1 = ((pixel & 0x00ff0000) - (_palette[i] & 0x00ff0000)) >> 16,
					  d2 = ((pixel & 0x0000ff00) - (_palette[i] & 0x0000ff00)) >> 8,
					  d3 = ((pixel & 0x000000ff) - (_palette[i] & 0x000000ff));
			final int distance = d1 * d1 + d2 * d2 + d3 * d3;
			
			if (distance < mindistance) {
				color = _palette[i];
				mindistance = distance;
			}
		}
		
		return color;
	}
}
