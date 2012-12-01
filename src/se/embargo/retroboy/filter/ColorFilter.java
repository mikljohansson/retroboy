package se.embargo.retroboy.filter;

import java.util.Arrays;

public class ColorFilter extends AbstractFilter {
	private float _factor;
	
	public ColorFilter(int contrast) {
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
	}

	@Override
	public void accept(ImageBuffer buffer) {
		final int[] image = buffer.image.array();
		final float factor = _factor;
		
		final int[] histogram = buffer.histogram;
		Arrays.fill(histogram, 0);
		
		for (int i = 0, last = buffer.imagewidth * buffer.imageheight; i != last; i++) {
			final int pixel = image[i];
			
			// Extract color components and apply the contrast adjustment
			final int r = Math.min(Math.max(0, (int)(factor * ((pixel & 0xff) - 128.0f) + 128.0f)), 255),
					  g = Math.min(Math.max(0, (int)(factor * (((pixel & 0xff00) >> 8) - 128.0f) + 128.0f)), 255),
					  b = Math.min(Math.max(0, (int)(factor * (((pixel & 0xff0000) >> 16) - 128.0f) + 128.0f)), 255);

			// Build the histogram used to calculate the global threshold
			final float lum = (int)(0.299 * (pixel & 0xff) + 0.587 * ((pixel & 0xff00) >> 8) + 0.114 * ((pixel & 0xff0000) >> 16));
			final int adjusted = Math.min(Math.max(0, (int)(factor * (lum - 128.0f) + 128.0f)), 255);
			histogram[adjusted]++;
			
			// Output the pixel, but keep alpha channel intact
			image[i] = (pixel & 0xff000000) | (b << 16) | (g << 8) | r;
		}

		// Calculate the global Otsu threshold
		buffer.threshold = YuvFilter.getGlobalThreshold(
			buffer.imagewidth, buffer.imageheight, image, histogram);
	}
}
