package se.embargo.retroboy.filter;

import java.nio.IntBuffer;
import java.util.Arrays;

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
		
		final int[] histogram = buffer.histogram;
		Arrays.fill(histogram, 0);
		
		for (float y = 0; y < frameheight; y += stride, yo++) {
			int xo = 0;

			for (float x = 0; x < framewidth; x += stride, xo++) {
				final int ii = (int)x + (int)y * framewidthi;
				final int io = xo + yo * imagewidth;
				
				// Convert from YUV luminance
				final float lum = Math.max((((int)data[ii]) & 0xff) - 16, 0);
				
				// Apply the contrast adjustment
				final int color = Math.min(Math.max(0, (int)(factor * (lum - 128.0f) + 128.0f)), 255);
				
				// Build the histogram used to calculate the global threshold
				histogram[color]++;
				
				// Output the pixel
				image[io] = 0xff000000 | (color << 16) | (color << 8) | color;
			}
		}

		// Calculate the global Otsu threshold
		buffer.threshold = getGlobalThreshold(
			imagewidth, imageheight, image, histogram);
	}
	
	public static int getGlobalThreshold(int imagewidth, int imageheight, final int[] image, final int[] histogram) {
		float sum = 0;
		int pixels = imagewidth * imageheight;
		
		for (int i = 0; i < histogram.length; i++) {
			sum += (float)(histogram[i] * i);
		}
		
		float csum = 0;
		int wB = 0;
		int wF = 0;
		
		float fmax = -1.0f;
		int threshold = 0;
		
		for (int i = 0; i < 255; i++) {
			// Weight background
			wB += histogram[i];
			if (wB == 0) { 
				continue;
			}
		
			// Weight foreground
			wF = pixels - wB;
			if (wF == 0) {
				break;
			}
		
			csum += (float)(histogram[i] * i);
		
			float mB = csum / wB;
			float mF = (sum - csum) / wF;
			float sb = (float)wB * (float)wF * (mF - mB);
		
			// Check if new maximum found
			if (sb > fmax) {
				fmax = sb;
				threshold = i + 1;
			}
		}
		
		return Math.max(2, Math.min(threshold, 254));
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
