package se.embargo.retroboy.filter;

import se.embargo.core.graphic.Bitmaps;

public class TransformFilter extends AbstractFilter {
	private Bitmaps.Transform _transform;
	
	public TransformFilter(Bitmaps.Transform transform) {
		_transform = transform;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		buffer.bitmap = Bitmaps.transform(buffer.bitmap, _transform);
	}

	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		return _transform.width;
	}

	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		return _transform.height;
	}
}
