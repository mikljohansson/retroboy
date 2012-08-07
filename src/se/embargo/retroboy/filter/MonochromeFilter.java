package se.embargo.retroboy.filter;

import java.util.Arrays;

import android.graphics.Color;

public class MonochromeFilter implements IImageFilter {
	private float _factor;
	
	public MonochromeFilter(int contrast) {
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
	}

	@Override
	public void accept(ImageBuffer buffer) {
		final int[] image = buffer.image.array();
		final float factor = _factor;
		
		final int[] histogram = buffer.histogram;
		Arrays.fill(histogram, 0);
		
		for (int i = 0, last = buffer.imagewidth * buffer.imageheight; i != last; i++) {
			final int pix = image[i];
			
			// Convert to monochrome
			final float lum = (int)(0.299 * Color.red(pix) + 0.587 * Color.green(pix) + 0.114 * Color.blue(pix));
			
			// Apply the contrast adjustment
			final int color = Math.min(Math.max(0, (int)(factor * (lum - 128.0f) + 128.0f)), 255);

			// Build the histogram used to calculate the global threshold
			histogram[color]++;
			
			image[i] = 0xff000000 | (color << 16) | (color << 8) | color;
		}

		// Calculate the global Otsu threshold
		buffer.threshold = YuvFilter.getGlobalThreshold(
			buffer.imagewidth, buffer.imageheight, image, histogram);
	}
}
