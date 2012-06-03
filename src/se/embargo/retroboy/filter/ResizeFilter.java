package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

import se.embargo.core.graphics.Bitmaps;

public class ResizeFilter implements IImageFilter {
	private int _width, _height;
	
	public ResizeFilter(int width, int height) {
		_width = width;
		_height = height;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		buffer.bitmap.copyPixelsFromBuffer(IntBuffer.wrap(buffer.image.array(), 0, buffer.width * buffer.height));
		buffer.bitmap = Bitmaps.resize(buffer.bitmap, _width, _height);
		buffer.width = buffer.bitmap.getWidth();
		buffer.height = buffer.bitmap.getHeight();
		buffer.bitmap.copyPixelsToBuffer(IntBuffer.wrap(buffer.image.array(), 0, buffer.width * buffer.height));
	}
}
