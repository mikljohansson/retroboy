package se.embargo.onebit.filter;

import se.embargo.core.graphics.Bitmaps;
import android.graphics.Bitmap;

public class JpegImageFilter implements IImageFilter {
	private int _width, _height;
	
	public JpegImageFilter(int width, int height) {
		_width = width;
		_height = height;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		Bitmap bm = Bitmaps.decodeByteArray(buffer.data, _width, _height);
		
		if (bm != null) {
			buffer.width = bm.getWidth();
			buffer.height = bm.getHeight();
			bm.copyPixelsToBuffer(buffer.image);
			bm.recycle();
		}
	}
}
