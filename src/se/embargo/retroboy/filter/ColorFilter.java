package se.embargo.retroboy.filter;


public class ColorFilter extends AbstractFilter {
	private float _factor;
	
	public ColorFilter(int contrast) {
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
	}

	@Override
	public void accept(ImageBuffer buffer) {
		final int[] image = buffer.image.array();
		final float factor = _factor;
		
		for (int i = 0, last = buffer.imagewidth * buffer.imageheight; i != last; i++) {
			final int pixel = image[i];
			
			// Extract color components and apply the contrast adjustment
			final int r = Math.min(Math.max(0, (int)(factor * ((pixel & 0xff) - 128.0f) + 128.0f)), 255),
					  g = Math.min(Math.max(0, (int)(factor * (((pixel & 0xff00) >> 8) - 128.0f) + 128.0f)), 255),
					  b = Math.min(Math.max(0, (int)(factor * (((pixel & 0xff0000) >> 16) - 128.0f) + 128.0f)), 255);

			// Output the pixel, but keep alpha channel intact
			image[i] = (pixel & 0xff000000) | (b << 16) | (g << 8) | r;
		}
	}
}
