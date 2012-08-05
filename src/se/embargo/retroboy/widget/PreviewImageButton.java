package se.embargo.retroboy.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageButton;

public class PreviewImageButton extends ImageButton {
	public PreviewImageButton(Context context) {
		super(context);
	}

	public PreviewImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public PreviewImageButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			setColorFilter(Color.argb(255, 51, 181, 229));
		} 
		else if (event.getAction() == MotionEvent.ACTION_UP) {
			resetColorFilter();
		}

		return super.onTouchEvent(event);
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		resetColorFilter();
	}
	
	private void resetColorFilter() {
		if (isEnabled()) {
			setColorFilter(Color.argb(0, 0, 0, 0));
		}
		else {
			setColorFilter(Color.argb(128, 0, 0, 0));
		}
	}
}
