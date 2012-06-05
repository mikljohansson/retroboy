package se.embargo.retroboy.filter;

import android.graphics.Bitmap;

public class ImageBitmapFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		// Change the bitmap dimensions
		if (buffer.bitmap == null || buffer.bitmap.getWidth() != buffer.imagewidth || buffer.bitmap.getHeight() != buffer.imageheight) {
			buffer.bitmap = Bitmap.createBitmap(buffer.imagewidth, buffer.imageheight, Bitmap.Config.ARGB_8888);
		}
		
		buffer.bitmap.copyPixelsFromBuffer(buffer.image);
	}
}
