package se.embargo.retroboy.filter;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import se.embargo.core.concurrent.IMapReduceBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.core.graphic.Levels;
import se.embargo.retroboy.color.IPalette;
import android.util.Log;

/**
 * Decodes YUV frames into RGB images.
 */
public class YuvFilter implements IImageFilter {
	private static final String TAG = "YuvFilter";
	private final int _width, _height;
	private final float _factor;
	private final boolean _autoexposure;
	
	private final Queue<int[]> _bufferpool = new ArrayBlockingQueue<int[]>(256);
	private final IMapReduceBody<ImageBuffer, int[]> _body;
	
	public YuvFilter(int width, int height, int contrast, boolean color, boolean autoexposure) {
		_width = width;
		_height = height;
		_factor = (259.0f * ((float)contrast + 255.0f)) / (255.0f * (259.0f - (float)contrast));
		_autoexposure = autoexposure;
		
		if (color) {
			_body = new ColorBody();
		}
		else {
			_body = new MonochromeBody();
		}
	}
	
	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		final float stride = getStride(framewidth, frameheight); 
		return Math.min((int)(framewidth / stride), _width);
	}
	
	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		final float stride = getStride(framewidth, frameheight); 
		return Math.min((int)(frameheight / stride), _height);
	}
	
	@Override
	public boolean isColorFilter() {
		return _body instanceof ColorBody;
	}
	
	@Override
	public IPalette getPalette() {
		return null;
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
			Log.d(TAG, "Allocating image buffer for " + imagesize + " pixels (" + buffer.image + ")");
			buffer.image = IntBuffer.wrap(new int[imagesize]);
		}
		
		// Downsample and convert the YUV frame to RGB image in parallel
		int[] histogram = Parallel.mapReduce(
			_body, buffer, 0, Math.min((int)(buffer.imageheight * stride), buffer.frameheight));
	
		// Calculate the global Otsu threshold
		if (_autoexposure) {
			buffer.threshold = Levels.getThreshold(
				buffer.imagewidth, buffer.imageheight, buffer.image.array(), histogram);
		}
		
		// Release histogram back to pool
		_bufferpool.offer(histogram);
	}
	
	private abstract class FilterBody implements IMapReduceBody<ImageBuffer, int[]> {
		@Override
		public int[] reduce(int[] lhs, int[] rhs) {
			for (int i = 0; i < lhs.length; i++) {
				lhs[i] += rhs[i];
			}
			
			_bufferpool.offer(rhs);
			return lhs;
		}
	}
	
	private class ColorBody extends FilterBody {
		@Override
		public int[] map(ImageBuffer buffer, int it, int last) {
			final float framewidth = buffer.framewidth, 
					    frameheight = buffer.frameheight,
					    framesize = framewidth * frameheight;
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
			
			// Convert YUV chunk to color
			int yo = (int)((float)it / stride) * imagewidth;
			for (float y = it; y < last; y += stride, yo += imagewidth) {
				int xi = 0, 
					yi = (int)y * framewidthi;
				
				float uvp = framesize + ((int)y >> 1) * framewidthi; 
				int u = 0, v = 0;  
				
				for (float x = 0; x < framewidth && xi < imagewidth; x += stride, xi++) {
					final int xo = yo + xi;
					final int ii = (int)x + yi;
					
					// Convert from YUV luminance
					final float lum = ((int)data[ii] & 0xff) - 16.0f;
					
					// Apply the contrast adjustment
					final int lumi = Math.max(0, Math.min((int)(factor * (lum - 128.0f) + 128.0f), 255));
					
					// Build the histogram used to calculate the global threshold
					histogram[lumi]++;
					
					// Fetch new UV values every other iteration
					if ((xi & 0x01) == 0) {  
						final int uvpi = (int)uvp & 0xfffffffe;
						v = ((int)data[(int)uvpi] & 0xff) - 128;  
						u = ((int)data[(int)uvpi + 1] & 0xff) - 128;
						uvp += stride + stride;
					}
					
					// Convert to RGB
					int y1192 = 1192 * lumi;
					int r = Math.max(0, Math.min(y1192 + 1634 * v, 262143));
					int g = Math.max(0, Math.min(y1192 - 833 * v - 400 * u, 262143));  
					int b = Math.max(0, Math.min(y1192 + 2066 * u, 262143));
					
					// Output the pixel
					image[xo] = 0xff000000 | ((b << 6) & 0x00ff0000)  | ((g >> 2) & 0x0000ff00) |  ((r >> 10) & 0x000000ff);
				}
			}
			
			return histogram;
		}
	}
	
	private class MonochromeBody extends FilterBody {
		@Override
		public int[] map(ImageBuffer buffer, int it, int last) {
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
	}
	
	private float getStride(float framewidth, float frameheight) {
		if (framewidth >= frameheight) {
			return Math.max(Math.min(framewidth / _width, frameheight / _height), 1.0f);
		}

		return Math.max(Math.min(frameheight / _width, framewidth / _height), 1.0f);
	}
}
