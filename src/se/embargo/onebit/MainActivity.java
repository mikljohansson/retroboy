package se.embargo.onebit;

import se.embargo.onebit.filter.AtkinsonFilter;
import se.embargo.onebit.filter.BayerFilter;
import se.embargo.onebit.filter.BitmapFilter;
import se.embargo.onebit.filter.CompositeFilter;
import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.YuvMonoFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

public class MainActivity extends SherlockActivity {
	public static final String PREFS_NAMESPACE = "se.embargo.onebit";
	
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private CameraPreview _preview;
	private Camera _camera;
	private int _cameraid = -1;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_prefs = getSharedPreferences(PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
		// Request full screen window
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		// Setup preview display surface
		setContentView(R.layout.main);
		_preview = (CameraPreview)findViewById(R.id.preview);
		_prefsListener.onSharedPreferenceChanged(_prefs, "filter");
		
		// Check which camera to use
		for (int i = 0, cameras = Camera.getNumberOfCameras(); i < cameras; i++) {
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(i, info);
			
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				_cameraid = i;
				break;
			}
		}
	}
	
	private IImageFilter createImageFilter(IImageFilter filter) {
		CompositeFilter filters = new CompositeFilter();
		filters.add(new YuvMonoFilter());
		//filters.add(new ResizeFilter(480, 320));
		filters.add(filter);
		filters.add(new BitmapFilter());
		return filters;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (_cameraid >= 0) {
			_camera = Camera.open(_cameraid);
			
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
			Camera.getCameraInfo(_cameraid, info);
			_preview.setCamera(_camera, info);
		}
	}

	@Override
	protected void onPause() {
		if (_camera != null) {
			_camera.stopPreview();  
			_camera.release();
			_camera = null;
		}

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
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if ("filter".equals(key)) {
				// Switch the active filter
				String value = prefs.getString(key, "bayer");
				IImageFilter filter;
				
				if ("atkinson".equals(value)) {
					filter = new AtkinsonFilter(480, 320);
				}
				else {
					filter = new BayerFilter(480, 320);
				}
				
				_preview.setFilter(createImageFilter(filter));
			}
		}
	}
}
