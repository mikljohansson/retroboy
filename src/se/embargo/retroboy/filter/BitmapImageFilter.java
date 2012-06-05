package se.embargo.retroboy.filter;

import java.nio.IntBuffer;


public class BitmapImageFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		final int outputwidth = buffer.bitmap.getWidth(),
				  outputheight = buffer.bitmap.getHeight();
		
		// Change the buffer dimensions
		if (buffer.image == null || buffer.imagewidth != outputwidth || buffer.imageheight != outputheight) {
			buffer.image = IntBuffer.wrap(new int[outputwidth * outputheight + outputwidth * 4]);
		}
		
		buffer.imagewidth = outputwidth;
		buffer.imageheight = outputheight;
		buffer.bitmap.copyPixelsToBuffer(buffer.image);
	}
}
