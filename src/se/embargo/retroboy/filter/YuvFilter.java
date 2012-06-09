package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

import android.util.Log;

public class YuvFilter implements IImageFilter {
	private static final String TAG = "YuvFilter";
	private int _width, _height;
	private float _factor;
	
	public YuvFilter(int width, int height, int contrast) {
		_width = width;
		_height = height;
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
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
		final float factor = _factor;
		int yo = 0;
		
		for (float y = 0; y < frameheight; y += stride, yo++) {
			int xo = 0;

			for (float x = 0; x < framewidth; x += stride, xo++) {
				final int ii = (int)x + (int)y * framewidthi;
				final int io = xo + yo * imagewidth;
				
				// Convert from YUV luminance
				final float lum = Math.max((((int)data[ii]) & 0xff) - 16, 0);
				
				// Apply the contrast adjustment
				final int color = Math.min(Math.max(0, (int)(factor * (lum - 128.0f) + 128.0f)), 255);
				
				// Output the pixel
				image[io] = 0xff000000 | (color << 16) | (color << 8) | color;
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
