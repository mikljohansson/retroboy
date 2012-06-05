package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

public class YuvFilter implements IImageFilter {
	private int _width, _height;
	
	public YuvFilter(int width, int height) {
		_width = width;
		_height = height;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		final byte[] data = buffer.frame;
		final float width = buffer.framewidth, height = buffer.frameheight;
		
		// Select the dimension that most closely matches the bounds
		final float stride;
		if (width >= height) {
			stride = Math.max(Math.max(width / _width, height / _height), 1.0f);
		}
		else {
			stride = Math.max(Math.max(height / _width, width / _height), 1.0f);
		}
		
		final int imagewidth = (int)(width / stride), 
				  imageheight = (int)(height / stride);
		
		// Change the buffer dimensions
		if (buffer.image == null || buffer.image.array().length < (imagewidth * imageheight)) {
			buffer.image = IntBuffer.wrap(new int[imagewidth * imageheight + imagewidth * 4]);
		}
		
		buffer.imagewidth = imagewidth;
		buffer.imageheight = imageheight;

		// Downsample and convert the YUV frame to RGB image
		final int[] image = buffer.image.array();
		final int framewidth = buffer.framewidth;
		int yo = 0;
		
		for (float y = 0; y < height; y += stride, yo++) {
			int xo = 0;

			for (float x = 0; x < width; x += stride, xo++) {
				final int ii = (int)x + (int)y * framewidth;
				final int io = xo + yo * imagewidth;
				final int lum = Math.max((((int)data[ii]) & 0xff) - 16, 0);
				image[io] = 0xff000000 | (lum << 16) | (lum << 8) | lum;	
			}
		}
	}
}
