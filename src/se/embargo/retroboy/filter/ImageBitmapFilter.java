package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

public class ImageBitmapFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		buffer.bitmap.copyPixelsFromBuffer(IntBuffer.wrap(buffer.image.array(), 0, buffer.width * buffer.height));
	}
}
