package se.embargo.retroboy.filter;

import java.util.ArrayList;
import java.util.List;

public class CompositeFilter implements IImageFilter {
	private List<IImageFilter> _filters = new ArrayList<IImageFilter>();
	
	public void add(IImageFilter filter) {
		_filters.add(filter);
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		for (IImageFilter filter : _filters) {
			filter.accept(buffer);
		}
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
}
