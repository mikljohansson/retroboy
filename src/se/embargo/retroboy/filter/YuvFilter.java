package se.embargo.retroboy.filter;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import se.embargo.core.concurrent.MapReduceBody;
import se.embargo.core.concurrent.Parallel;
import android.util.Log;

public class YuvFilter implements IImageFilter {
	private static final String TAG = "YuvFilter";
	private int _width, _height;
	private float _factor;
	
	private Queue<int[]> _bufferpool = new ArrayBlockingQueue<int[]>(256);
	private FilterBody _body = new FilterBody();
	
	public YuvFilter(int width, int height, int contrast) {
		_width = width;
		_height = height;
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		// Select the dimension that most closely matches the bounds
		final float framewidth = buffer.framewidth, frameheight = buffer.frameheight;
		final float stride = getStride(framewidth, frameheight);
		
		buffer.imagewidth = Math.min((int)(framewidth / stride), _width);
		buffer.imageheight = Math.min((int)(frameheight / stride), _height);
		final int imagesize = buffer.imagewidth * buffer.imageheight + buffer.imagewidth * 4;
		
		// Change the buffer dimensions
		if (buffer.image == null || buffer.image.array().length < imagesize) {
			Log.i(TAG, "Allocating image buffer for " + imagesize + " pixels (" + buffer.image + ")");
			buffer.image = IntBuffer.wrap(new int[imagesize]);
		}
		
		// Downsample and convert the YUV frame to RGB image in parallel
		int[] histogram = Parallel.mapReduce(
			_body, buffer, 0, Math.min((int)(buffer.imageheight * stride), buffer.frameheight));

		// Calculate the global Otsu threshold
		buffer.threshold = getGlobalThreshold(
			buffer.imagewidth, buffer.imageheight, buffer.image.array(), histogram);
		
		// Release histogram back to pool
		_bufferpool.offer(histogram);
	}
	
	private class FilterBody implements MapReduceBody<ImageBuffer, int[]> {
		@Override
		public int[] map(ImageBuffer buffer, int it, int last) {
			// Select the dimension that most closely matches the bounds
			final float framewidth = buffer.framewidth, frameheight = buffer.frameheight;
			final float stride = getStride(framewidth, frameheight);
			final byte[] data = buffer.frame;

			final int[] image = buffer.image.array();
			final int framewidthi = buffer.framewidth,
					  imagewidth = buffer.imagewidth;
			final float factor = _factor;

			// Space to hold an image histogram
			int[] histogram = _bufferpool.poll();
			if (histogram == null) {
				histogram = new int[256];
			}
			
			Arrays.fill(histogram, 0);
			
			// Convert YUV chunk to monochrome
			int yo = (int)((float)it / stride) * imagewidth;
			for (float y = it; y < last; y += stride, yo += imagewidth) {
				int xi = 0, 
					yi = (int)y * framewidthi;

				for (float x = 0; x < framewidth && xi < imagewidth; x += stride, xi++) {
					final int xo = yo + xi;
					final int ii = (int)x + yi;
					
					// Convert from YUV luminance
					final float lum = (((int)data[ii]) & 0xff) - 16.0f;
					
					// Apply the contrast adjustment
					final int color = Math.max(0, Math.min((int)(factor * (lum - 128.0f) + 128.0f), 255));
					
					// Build the histogram used to calculate the global threshold
					histogram[color]++;
					
					// Output the pixel
					image[xo] = 0xff000000 | (color << 16) | (color << 8) | color;
				}
			}
			
			return histogram;
		}

		@Override
		public int[] reduce(int[] lhs, int[] rhs) {
			for (int i = 0; i < lhs.length; i++) {
				lhs[i] += rhs[i];
			}
			
			_bufferpool.offer(rhs);
			return lhs;
		}
	}
	
	/**
	 * Calculates the global luminance threshold using Otsu's method 
	 * @param imagewidth	Width of input image
	 * @param imageheight	Height of input image
	 * @param image			Input image buffer
	 * @param histogram		Histogram of the input image, length must be 256 
	 * @return				The global threshold color
	 */
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
		
		for (int i = 0; i < histogram.length; i++) {
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
		return Math.min((int)(framewidth / stride), _width);
	}
	
	public int getEffectiveHeight(int framewidth, int frameheight) {
		final float stride = getStride(framewidth, frameheight); 
		return Math.min((int)(frameheight / stride), _height);
	}

	private float getStride(float framewidth, float frameheight) {
		if (framewidth >= frameheight) {
			return Math.max(Math.min(framewidth / _width, frameheight / _height), 1.0f);
		}

		return Math.max(Math.min(frameheight / _width, framewidth / _height), 1.0f);
	}
}
