package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

import se.embargo.retroboy.color.IPalette;

import android.graphics.Bitmap;

public interface IImageFilter {
	/**
	 * Returns the output image width
	 * @param framewidth	Raw input frame width
	 * @param frameheight	Raw input frame height
	 * @return				Image width
	 */
	public int getEffectiveWidth(int framewidth, int frameheight);
	
	/**
	 * Returns the output image height
	 * @param framewidth	Raw input frame width
	 * @param frameheight	Raw input frame height
	 * @return				Image height
	 */
	public int getEffectiveHeight(int framewidth, int frameheight);

	/**
	 * @return	Returns true if this is a color filter. 
	 */
	public boolean isColorFilter();	

	/**
	 * @return	The palette used by this filter.
	 */
	public IPalette getPalette();
	
	/**
	 * Filter one frame
	 * @param buffer	Frame to process
	 */
	public void accept(ImageBuffer buffer);

	/**
	 * Camera frame buffer
	 */
	public class ImageBuffer {
		/**
		 * Raw image data, typically YUV format
		 */
		public byte[] frame;
		
		/**
		 * Width and height of the raw data
		 */
		public final int framewidth, frameheight;
		
		/**
		 * Pixel buffer for filters to read/write to.
		 */
		public IntBuffer image;
		
		/**
		 * Scratch buffer for image processing, same dimensions as image.
		 */
		public IntBuffer scratch;
		
		/**
		 * Size of output image.
		 */
		public int imagewidth, imageheight;

		/**
		 * Finished bitmap of output image.
		 */
		public Bitmap bitmap;
		
		/**
		 * Timestamp when frame was captured in nanoseconds.
		 */
		public long timestamp;
		
		/**
		 * Global lighting threshold.
		 */
		public int threshold = 128;
		
		public ImageBuffer(byte[] frame, int framewidth, int frameheight) {
			this.frame = frame;
			this.framewidth = framewidth;
			this.frameheight = frameheight;
		}

		public ImageBuffer(int framewidth, int frameheight) {
			this(null, framewidth, frameheight);
		}
		
		public ImageBuffer(Bitmap input) {
			this(null, input.getWidth(), input.getHeight());
			imagewidth = framewidth;
			imageheight = frameheight;
			image = IntBuffer.wrap(new int[imagewidth * imageheight + imagewidth * 4]);
			bitmap = input;
			bitmap.copyPixelsToBuffer(image);
		}
		
		public void reset(byte[] data) {
			frame = data;
			timestamp = System.nanoTime();
			threshold = 128;
		}
		
		public void initScratchBuffer() {
			if (scratch == null || scratch.array().length != image.array().length) {
				scratch = IntBuffer.wrap(new int[image.array().length]);
			}
		}
		
		public void flipScratchBuffer() {
			IntBuffer tmp = scratch;
			scratch = image;
			image = tmp;
		}
	}
}
