package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

/**
 * Input filter used when processing existing images with a monochrome filter.
 */
public class MonochromeFilter extends AbstractFilter {
	private final FilterBody _body = new FilterBody();
	private float _factor;
	
	public MonochromeFilter(int contrast) {
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
	}

	@Override
	public void accept(ImageBuffer buffer) {
		Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}

    private class FilterBody implements ForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
			final int[] image = buffer.image.array();
			final float factor = _factor;
			
			for (int i = it; i != last; i++) {
				final int pixel = image[i];
				
				// Convert to monochrome
				final float lum = (0.299f * (pixel & 0xff) + 0.587f * ((pixel & 0xff00) >> 8) + 0.114f * ((pixel & 0xff0000) >> 16));
				
				// Apply the contrast adjustment
				final int adjusted = Math.min(Math.max(0, (int)(factor * (lum - 128.0f) + 128.0f)), 255);
	
				// Output the pixel, but keep alpha channel intact
				image[i] = (pixel & 0xff000000) | (adjusted << 16) | (adjusted << 8) | adjusted;
			}
		}
    }
}
