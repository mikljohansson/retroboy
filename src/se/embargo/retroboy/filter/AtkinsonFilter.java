package se.embargo.retroboy.filter;

public class AtkinsonFilter implements IImageFilter {
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.imagewidth, height = buffer.imageheight;

		// Factor used to offset the threshold to compensate for too dark or bright images
		final int threshold = buffer.threshold;
		
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int i = x + y * width;
				final int mono = image[i] & 0xff;

				// Apply the threshold
				final int lum = mono < threshold ? 0 : 255;
				
				// Output the pixel
				image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
				
				// Propagate the error
				final int err = (mono - lum) / 8;
				if (err != 0) {
					propagate(image, err, i + 1); 
					propagate(image, err, i + 2);
					propagate(image, err, i - 1 + width);
					propagate(image, err, i + width);
					propagate(image, err, i + 1 + width);
					propagate(image, err, i + 2 * width);
				}
			}
		}
	}

	private static final void propagate(final int[] image, final int err, final int i) {
		// No need to check bound, buffer has 2+ extra lines
		final int lum = Math.min(Math.max(0, (image[i] & 0xff) + err), 255);
		image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
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
