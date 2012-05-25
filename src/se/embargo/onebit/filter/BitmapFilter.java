package se.embargo.onebit.filter;


public class BitmapFilter implements IImageFilter {
	@Override
	public void accept(ImageBuffer buffer) {
		buffer.bitmap.copyPixelsFromBuffer(buffer.image);
	}
}
