package se.embargo.retroboy.filter;

import android.graphics.Color;

public class MonochromeFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		final int[] image = buffer.image.array();
		
		for (int i = 0, last = buffer.width * buffer.height; i != last; i++) {
			final int pix = image[i];
			final int lum = (int)(0.299 * Color.red(pix) + 0.587 * Color.green(pix) + 0.114 * Color.blue(pix));
			image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
		}
	}
}
