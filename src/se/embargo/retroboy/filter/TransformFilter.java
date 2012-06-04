package se.embargo.retroboy.filter;

import se.embargo.core.graphics.Bitmaps;

public class TransformFilter implements IImageFilter {
	private Bitmaps.Transform _transform;
	
	public TransformFilter(Bitmaps.Transform transform) {
		_transform = transform;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		buffer.bitmap = Bitmaps.transform(buffer.bitmap, _transform);
		buffer.width = buffer.bitmap.getWidth();
		buffer.height = buffer.bitmap.getHeight();
	}
}
