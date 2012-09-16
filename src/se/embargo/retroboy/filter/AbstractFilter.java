package se.embargo.retroboy.filter;

public abstract class AbstractFilter implements IImageFilter {
	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		return 0;
	}

	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		return 0;
	}

	@Override
	public boolean isColorFilter() {
		return false;
	}
}
