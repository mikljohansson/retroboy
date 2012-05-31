package se.embargo.onebit;

import se.embargo.onebit.filter.AtkinsonFilter;
import se.embargo.onebit.filter.BayerFilter;
import se.embargo.onebit.filter.CompositeFilter;
import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.ImageBitmapFilter;
import se.embargo.onebit.filter.YuvImageFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity {
	public static final String PREFS_NAMESPACE = "se.embargo.onebit";
	public static final String PREF_FILTER = "filter";
	public static final String PREF_CAMERA = "camera";
	
	public static final int IMAGE_WIDTH = 480, IMAGE_HEIGHT = 320;

	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private CameraPreview _preview;
	private Camera _camera;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_prefs = getSharedPreferences(PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		// Setup preview display surface
		setContentView(R.layout.main_activity);
		_preview = (CameraPreview)findViewById(R.id.preview);
		
		// Connect the take photo button
		{
			ImageButton button = (ImageButton)findViewById(R.id.takePhoto);
			button.setOnClickListener(new TakePhotoListener());
		}

		// Initialize the image filter
		initFilter();
	}
	
	public static IImageFilter createEffectFilter(SharedPreferences prefs) {
		String filtertype = prefs.getString(PREF_FILTER, "bayer");
		if ("atkinson".equals(filtertype)) {
			return new AtkinsonFilter(IMAGE_WIDTH, IMAGE_HEIGHT);
		}

		return new BayerFilter(IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void onResume() {
		super.onResume();
		initCamera();
	}

	@Override
	protected void onPause() {
		stopPreview();
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case R.id.switchCameraOption: {
            	int cameraid = _prefs.getInt(PREF_CAMERA, 0) + 1;
            	if (cameraid >= Camera.getNumberOfCameras()) {
            		cameraid = 0;
            	}
            	
            	SharedPreferences.Editor editor = _prefs.edit();
            	editor.putInt(PREF_CAMERA, cameraid);
            	editor.commit();
            	return true;
            }
            
            case R.id.attachImageOption: {
            	// Pick a gallery image to process
				Intent intent = new Intent(this, ImageActivity.class);
				intent.putExtra(ImageActivity.EXTRA_ACTION, "pick");
				startActivity(intent);
	            return true;
            }

            case R.id.editSettingsOption: {
				// Start preferences activity
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			}

			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private void stopPreview() {
		if (_camera != null) {
			_camera.stopPreview();
			_preview.setCamera(null, null);
			_camera.release();
			_camera = null;
		}
	}
	
	private void initCamera() {
		stopPreview();
		
		// Check which camera to use
		int cameracount = Camera.getNumberOfCameras(), cameraid = -1;
		if (cameracount > 0) {
			cameraid = _prefs.getInt(PREF_CAMERA, 0);
			if (cameraid >= cameracount) {
				cameraid = 0;
			}

			// Lock the camera
			_camera = Camera.open(cameraid);
			
			// Select the smallest available preview size for performance reasons
			Camera.Size previewSize = null;
			for (Camera.Size size : _camera.getParameters().getSupportedPreviewSizes()) {
				if (previewSize == null || size.width < previewSize.width) {
					previewSize = size;
				}
			}
			_camera.getParameters().setPreviewSize(previewSize.width, previewSize.height);
			
			// Start the preview
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraid, info);
			_preview.setCamera(_camera, info);
		}
	}
	
	private void initFilter() {
		CompositeFilter filters = new CompositeFilter();
		filters.add(new YuvImageFilter());
		filters.add(createEffectFilter(_prefs));
		filters.add(new ImageBitmapFilter());
		_preview.setFilter(filters);
	}
	
	private class TakePhotoListener implements View.OnClickListener, Camera.ShutterCallback, Camera.PictureCallback {
		@Override
		public void onClick(View v) {
			if (_camera != null) {
				_preview.setCamera(null, null);
				_camera.takePicture(this, null, this);
				_camera = null;
			}
		}

		@Override
		public void onShutter() {}

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			if (camera != null) {
				camera.release();
			}
			
			// Start image activity
			Intent intent = new Intent(MainActivity.this, ImageActivity.class);
			intent.putExtra(ImageActivity.EXTRA_DATA, data);
			startActivity(intent);
		}
	}
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (PREF_CAMERA.equals(key)) {
				initCamera();
			}
			else if (PREF_FILTER.equals(key)) {
				initFilter();
			}
		}
	}
}
