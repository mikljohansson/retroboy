package se.embargo.retroboy.filter;

import java.nio.IntBuffer;

import android.graphics.Bitmap;
import android.util.Log;

public class ImageBitmapFilter extends AbstractFilter {
	private static final String TAG = "ImageBitmapFilter";

	@Override
	public void accept(ImageBuffer buffer) {
		// Change the bitmap dimensions
		if (buffer.bitmap == null || buffer.bitmap.getWidth() != buffer.imagewidth || buffer.bitmap.getHeight() != buffer.imageheight) {
			Log.d(TAG, "Allocating Bitmap for " + buffer.imagewidth + "x" + buffer.imageheight + " pixels (" + buffer.bitmap + ")");
			buffer.bitmap = Bitmap.createBitmap(buffer.imagewidth, buffer.imageheight, Bitmap.Config.ARGB_8888);
		}
		
		buffer.bitmap.copyPixelsFromBuffer(IntBuffer.wrap(buffer.image.array(), 0, buffer.imagewidth * buffer.imageheight));
	}
}
