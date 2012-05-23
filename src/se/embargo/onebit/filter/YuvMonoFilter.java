package se.embargo.onebit.filter;


public class YuvMonoFilter implements IImageFilter {
	@Override
	public void accept(PreviewBuffer buffer) {
		int bufsize = buffer.width * buffer.height;
		byte[] data = buffer.data;
		int[] image = buffer.image;
		
		for (int i = 0; i < bufsize; i++) {
			int lum = (((int)data[i]) & 0xff) - 16;
			lum = Math.min(Math.max(0, lum), 255);
			image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
		}
	}
}
