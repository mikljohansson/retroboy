package se.embargo.onebit.filter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;

public class ResizeFilter implements IImageFilter {
	private final int _width;
	private final int _height;
	
	public ResizeFilter(int width, int height) {
		_width = width;
		_height = height;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		// Select the constraining dimension
		int width, height;
		if ((buffer.width - _width) >= (buffer.height - _height)) {
			width = _width;
			height = (int)((float)_width / buffer.width * buffer.height);
		}
		else {
			width = (int)((float)_height / buffer.height * buffer.width);
			height = _height;
		}
		
		// Create input and output bitmaps
		Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(target);
		
		// Scale the image when drawing
		Matrix matrix = new Matrix();
		matrix.setRectToRect(
			new RectF(0, 0, buffer.width, buffer.height), 
			new RectF(0, 0, width, height), 
			Matrix.ScaleToFit.START);
		canvas.drawBitmap(buffer.bitmap, matrix, null);
		
		// Extract the bitmap
		target.copyPixelsToBuffer(buffer.image);
		buffer.width = width;
		buffer.height = height;
	}
}
