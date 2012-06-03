package se.embargo.retroboy.filter;

public class YuvFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		final byte[] data = buffer.data;
		final int[] image = buffer.image.array();
		
		for (int i = 0, last = buffer.width * buffer.height; i != last; i++) {
			final int lum = Math.max(0, (((int)data[i]) & 0xff) - 16);
			image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
		}
	}
}
