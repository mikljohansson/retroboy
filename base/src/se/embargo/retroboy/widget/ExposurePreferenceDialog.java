package se.embargo.retroboy.widget;

import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.retroboy.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class ExposurePreferenceDialog implements DialogInterface.OnDismissListener, SeekBar.OnSeekBarChangeListener {
	private final Activity _context;
	private final IObservableValue<Integer> _value;	
	private final float _step;
	private final int _max, _min;
	private DialogInterface.OnDismissListener _dismissListener = null;

	public ExposurePreferenceDialog(Activity context, IObservableValue<Integer> value, float step, int max, int min) {
		_context = context;
		_value = value;
		_step = step;
		_max = max;
		_min = min;
	}
	
	public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
		_dismissListener = listener;
	}
	
	public void show() {
		final FrameLayout frameview = new FrameLayout(_context);
		LayoutInflater inflater = _context.getLayoutInflater();
		inflater.inflate(R.layout.camera_exposure_preference, frameview);
		
		SeekBar exposureview = (SeekBar)frameview.findViewById(R.id.exposureValue);
		exposureview.setMax(Math.abs(_min) + Math.abs(_max));
		exposureview.setProgress(_value.getValue() + Math.abs(_min));
		exposureview.setOnSeekBarChangeListener(this);
		
		TextView minview = (TextView)frameview.findViewById(R.id.minExposureValue);
		minview.setText(Float.toString(_step * _min));
		
		TextView maxview = (TextView)frameview.findViewById(R.id.maxExposureValue);
		maxview.setText(Float.toString(_step * _max));

		AlertDialog.Builder builder = new AlertDialog.Builder(_context);
		builder.setView(frameview);

		AlertDialog dialog = builder.create();
		dialog.setOnDismissListener(this);
		dialog.setCanceledOnTouchOutside(true);

		// Avoid dimming the background when the dialog is shown
		dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		
		// Show the dialog
		dialog.show();
	}
	
	@Override
	public void onDismiss(DialogInterface dialog) {
		if (_dismissListener != null) {
			_dismissListener.onDismiss(dialog);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			_value.setValue(progress - Math.abs(_min));
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}
}
