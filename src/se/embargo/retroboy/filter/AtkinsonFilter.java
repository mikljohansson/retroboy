package se.embargo.retroboy.filter;

public class AtkinsonFilter implements IImageFilter {
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.imagewidth, height = buffer.imageheight;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int i = x + y * width;

				// Apply the threshold
				final int mono = image[i] & 0xff;
				final int lum = mono < 128 ? 0 : 255;
				final int err = (mono - lum) / 8;
				image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
				
				// Propagate the error
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
}
