package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

import android.graphics.Bitmap;

public interface IImageFilter {
	/**
	 * Filter one frame
	 * @param buffer	Frame to process
	 */
	public void accept(ImageBuffer buffer);
	
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
	 * @return	Returns true if this is a color filter 
	 */
	public boolean isColorFilter();	
	
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
		
		public IntBuffer image;
		public int imagewidth, imageheight;

		public Bitmap bitmap;
		
		/**
		 * Timestamp when frame was captured in nanoseconds.
		 */
		public long timestamp;
		
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
	}
}
