package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

public class BitmapImageFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		buffer.bitmap.copyPixelsToBuffer(IntBuffer.wrap(buffer.image.array(), 0, buffer.width * buffer.height));
	}
}
