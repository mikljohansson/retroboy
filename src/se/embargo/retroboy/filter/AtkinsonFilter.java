package se.embargo.retroboy.filter;

public class AtkinsonFilter extends AbstractFilter {
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.imagewidth, 
				  height = buffer.imageheight, 
				  pixels = width * height;

		// Factor used to offset the threshold to compensate for too dark or bright images
		final int threshold = buffer.threshold;
		
		for (int i = 0; i < pixels; i++) {
			final int pixel = image[i];
			final int mono = pixel & 0xff;
			
			// Apply the threshold
			final int lum = mono < threshold ? 0 : 255;
			
			// Output the pixel
			image[i] = (pixel & 0xff000000) | (lum << 16) | (lum << 8) | lum;
			
			// Propagate the error
			final int err = (mono - lum) / 8;
			if (err != 0) {
				// No need to check bound, buffer has 2+ extra lines
				int oi = i + 1;
				int opixel = image[oi];
				image[oi] = (opixel & 0xff000000) | Math.min(Math.max(0, (opixel & 0xff) + err), 255);

				oi = i + 2;
				opixel = image[oi];
				image[oi] = (opixel & 0xff000000) | Math.min(Math.max(0, (opixel & 0xff) + err), 255);

				oi = i - 1 + width;
				opixel = image[oi];
				image[oi] = (opixel & 0xff000000) | Math.min(Math.max(0, (opixel & 0xff) + err), 255);

				oi = i + width;
				opixel = image[oi];
				image[oi] = (opixel & 0xff000000) | Math.min(Math.max(0, (opixel & 0xff) + err), 255);

				oi = i + 1 + width;
				opixel = image[oi];
				image[oi] = (opixel & 0xff000000) | Math.min(Math.max(0, (opixel & 0xff) + err), 255);

				oi = i + width + width;
				opixel = image[oi];
				image[oi] = (opixel & 0xff000000) | Math.min(Math.max(0, (opixel & 0xff) + err), 255);
			}
		}
	}
}
