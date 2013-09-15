package se.embargo.retroboy.filter;

public class NullFilter extends AbstractFilter {
	private final boolean _color;
	
	public NullFilter(boolean color) {
		_color = color;
	}
	
	@Override
	public boolean isColorFilter() {
		return _color;
	}
	
	@Override
	public void accept(ImageBuffer buffer) {}
}
