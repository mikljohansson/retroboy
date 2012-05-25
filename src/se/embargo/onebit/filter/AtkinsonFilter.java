package se.embargo.onebit.filter;

public class AtkinsonFilter implements IImageFilter {
	private int _width, _height;
	
	public AtkinsonFilter(int width, int height) {
		_width = width;
		_height = height;
	}
	
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.width, height = buffer.height;
		final int stride = (int)Math.ceil(Math.max((float)width / _width, (float)height / _height));
		
		for (int y = 0; y < height; y += stride) {
			for (int x = 0; x < width; x += stride) {
				final int i = x + y * width;

				// Apply the threshold
				final int mono = image[i] & 0xff;
				final int lum = mono < 128 ? 0 : 255;
				final int err = (mono - lum) / 8;
				final int pixel = 0xff000000 | (lum << 16) | (lum << 8) | lum;
				
				// Output the pixel block 
				for (int ty = 0; ty < stride; ty++) {
					for (int tx = 0; tx < stride; tx++) {
						image[i + tx + ty * width] = pixel;
					}
				}
				
				// Propagate the error
				if (err != 0) {
					propagate(image, err, i + stride); 
					propagate(image, err, i + stride * 2);
					propagate(image, err, i - stride + stride * width);
					propagate(image, err, i + stride * width);
					propagate(image, err, i + stride + stride * width);
					propagate(image, err, i + stride * 2 * width);
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
