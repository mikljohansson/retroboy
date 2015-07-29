package se.embargo.retroboy.filter;

import java.util.ArrayList;
import java.util.List;

import se.embargo.core.graphic.color.IPalette;

public class CompositeFilter extends AbstractFilter {
	private List<IImageFilter> _filters = new ArrayList<IImageFilter>();
	
	public void add(IImageFilter filter) {
		_filters.add(filter);
	}
	
	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		for (IImageFilter filter : _filters) {
			int result = filter.getEffectiveWidth(framewidth, frameheight);
			if (result > 0) {
				return result;
			}
		}
		
		return 0;
	}

	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		for (IImageFilter filter : _filters) {
			int result = filter.getEffectiveHeight(framewidth, frameheight);
			if (result > 0) {
				return result;
			}
		}
		
		return 0;
	}

	@Override
	public boolean isColorFilter() {
		for (IImageFilter filter : _filters) {
			if (filter.isColorFilter()) {
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public IPalette getPalette() {
		for (IImageFilter filter : _filters) {
			IPalette palette = filter.getPalette();
			if (palette != null) {
				return palette;
			}
		}
		
		return null;
	}

	@Override
	public void accept(ImageBuffer buffer) {
		for (IImageFilter filter : _filters) {
			filter.accept(buffer);
		}
	}
}
