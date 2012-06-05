package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

import android.util.Log;

public class YuvFilter implements IImageFilter {
	private static final String TAG = "YuvFilter";
	private int _width, _height;
	
	public YuvFilter(int width, int height) {
		_width = width;
		_height = height;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		final byte[] data = buffer.frame;
		final float framewidth = buffer.framewidth, frameheight = buffer.frameheight;
		
		// Select the dimension that most closely matches the bounds
		final float stride = getStride(framewidth, frameheight);
		final int imagewidth = (int)(framewidth / stride), 
				  imageheight = (int)(frameheight / stride),
				  imagesize = imagewidth * imageheight + imagewidth * 4;
		
		// Change the buffer dimensions
		if (buffer.image == null || buffer.image.array().length < imagesize) {
			Log.i(TAG, "Allocating image buffer for " + imagesize + " pixels (" + buffer.image + ")");
			buffer.image = IntBuffer.wrap(new int[imagesize]);
		}
		
		buffer.imagewidth = imagewidth;
		buffer.imageheight = imageheight;

		// Downsample and convert the YUV frame to RGB image
		final int[] image = buffer.image.array();
		final int framewidthi = buffer.framewidth;
		int yo = 0;
		
		for (float y = 0; y < frameheight; y += stride, yo++) {
			int xo = 0;

			for (float x = 0; x < framewidth; x += stride, xo++) {
				final int ii = (int)x + (int)y * framewidthi;
				final int io = xo + yo * imagewidth;
				final int lum = Math.max((((int)data[ii]) & 0xff) - 16, 0);
				image[io] = 0xff000000 | (lum << 16) | (lum << 8) | lum;	
			}
		}
	}

	public int getEffectiveWidth(int framewidth, int frameheight) {
		final float stride = getStride(framewidth, frameheight); 
		return (int)(framewidth / stride);
	}
	
	public int getEffectiveHeight(int framewidth, int frameheight) {
		final float stride = getStride(framewidth, frameheight); 
		return (int)(frameheight / stride);
	}

	private float getStride(float framewidth, float frameheight) {
		if (framewidth >= frameheight) {
			return Math.max(Math.max(framewidth / _width, frameheight / _height), 1.0f);
		}

		return Math.max(Math.max(frameheight / _width, framewidth / _height), 1.0f);
	}
}
