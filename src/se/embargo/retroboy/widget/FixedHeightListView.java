package se.embargo.retroboy.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

public class FixedHeightListView extends ListView {
	public FixedHeightListView(Context context) {
		super(context);
	}

	public FixedHeightListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public FixedHeightListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override
	public void setAdapter(ListAdapter adapter) {
		super.setAdapter(adapter);
		
		if (adapter != null) {
	        int totalHeight = 0;
	        for (int i = 0; i < adapter.getCount(); i++) {
	            View listItem = adapter.getView(i, null, this);
	            listItem.measure(0, 0);
	            totalHeight += listItem.getMeasuredHeight();
	        }
	
	        ViewGroup.LayoutParams params = getLayoutParams();
	        params.height = totalHeight + (getDividerHeight() * (adapter.getCount() - 1));
	        setLayoutParams(params);
	        requestLayout();
		}
	}
}
