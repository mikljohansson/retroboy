package se.embargo.retroboy;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

public class ExposurePreference extends DialogPreference {
	private SeekBar _progress;
	private View _widget;
	private int _value;
	
	public ExposurePreference(Context context) {
		this(context, null);
	}

	public ExposurePreference(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ExposurePreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		setWidgetLayoutResource(R.layout.exposure_preference);
		setDialogLayoutResource(R.layout.exposure_preference_dialog);
	
		setPositiveButtonText(R.string.btn_exposure_set);
		setNegativeButtonText(R.string.btn_exposure_cancel);
	}
	
	protected View onCreateView(ViewGroup parent) {
		_widget = super.onCreateView(parent);
		initExposureValue();
		return _widget;
	}
	
	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		_progress = (SeekBar)view.findViewById(R.id.exposureValue);
		_progress.setProgress(_value);
	}
	
	private void initExposureValue() {
		if (_widget != null) {
			TextView value = (TextView)_widget.findViewById(R.id.exposureValue);
			value.setText(getContext().getString(R.string.pref_format_exposure, (float)(_value - 50) / 25));
		}
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (positiveResult) {
			try {
				_value = _progress.getProgress();
				String value = Integer.toString(_value);
				
				if (callChangeListener(value)) {
					persistString(value);
					initExposureValue();
				}
			}
			catch (NumberFormatException e) {}
		}
	}
	
	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getString(index);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		String value = null;

		if (restoreValue) {
			if (defaultValue != null) {
				value = getPersistedString(defaultValue.toString());
			}
			else {
				value = getPersistedString("50");
			}
		}
		else {
			value = defaultValue.toString();
		}

		try {
			_value = Integer.parseInt(value);
		}
		catch (NumberFormatException e) {}
	}
}
