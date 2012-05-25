package se.embargo.onebit.filter;

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
}
