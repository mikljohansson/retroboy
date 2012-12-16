package se.embargo.retroboy;

import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

/**
 * Handles the Camera auto-focus states.
 */
class FocusManager implements IChangeListener<CameraHandle>, Camera.AutoFocusCallback {
	private static final String TAG = "FocusManager";
	
	private final IObservableValue<CameraHandle> _cameraHandle;
	private final ImageView _autoFocusMarker;
	private final boolean _hasAutoFocus;
	private boolean _visible = true;
	
	public FocusManager(Context context, IObservableValue<CameraHandle> cameraHandle, View parent) {
		_cameraHandle = cameraHandle;
		_cameraHandle.addChangeListener(this);
		_autoFocusMarker = (ImageView)parent.findViewById(R.id.autoFocusMarker);
		_hasAutoFocus = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS);
	}
	
	@Override
	public void handleChange(ChangeEvent<CameraHandle> event) {
		_visible = true;
		init();
	}

	public void setVisible(boolean visible) {
		_visible = visible;
		init();
	}
	
	private void init() {
		CameraHandle handle = _cameraHandle.getValue();
		
		if (handle != null && handle.info.facing == Camera.CameraInfo.CAMERA_FACING_BACK && _visible && _hasAutoFocus) {
			_autoFocusMarker.setVisibility(View.VISIBLE);
			Log.i(TAG, "Auto-focus enabled");
		}
		else {
			_autoFocusMarker.setVisibility(View.GONE);
			Log.i(TAG, "Auto-focus disabled");
		}
	}
	
	public void autoFocus() {
		_autoFocusMarker.setImageResource(R.drawable.ic_focus);

		CameraHandle handle = _cameraHandle.getValue();
		if (handle != null) {
			try {
				handle.camera.autoFocus(this);
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to start auto-focus", e);
			}
		}
	}
	
	public void resetFocus() {
		_autoFocusMarker.setImageResource(R.drawable.ic_focus);

		CameraHandle handle = _cameraHandle.getValue();
		if (handle != null) {
			try {
				handle.camera.cancelAutoFocus();
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to reset auto-focus", e);
			}
		}
	}

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		if (success) {
			_autoFocusMarker.setImageResource(R.drawable.ic_focus_ok);
		}
		else {
			_autoFocusMarker.setImageResource(R.drawable.ic_focus_fail);
		}
	}
}
