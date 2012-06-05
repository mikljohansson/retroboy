package se.embargo.retroboy.filter;

import java.nio.IntBuffer;


public class BitmapImageFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		final int imagewidth = buffer.bitmap.getWidth(),
				  imageheight = buffer.bitmap.getHeight(),
				  imagesize = imagewidth * imageheight + imagewidth * 4;
		
		// Change the buffer dimensions
		if (buffer.image == null || buffer.image.array().length < imagesize) {
			buffer.image = IntBuffer.wrap(new int[imagesize]);
		}
		
		buffer.imagewidth = imagewidth;
		buffer.imageheight = imageheight;
		buffer.bitmap.copyPixelsToBuffer(buffer.image);
	}
}
