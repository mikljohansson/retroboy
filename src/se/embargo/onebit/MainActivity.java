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
import com.actionbarsherlock.view.Window;

public class MainActivity extends SherlockActivity {
	public static final String PREFS_NAMESPACE = "se.embargo.onebit";
	public static final int IMAGE_WIDTH = 480, IMAGE_HEIGHT = 320;

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
		setContentView(R.layout.main_activity);
		_preview = (CameraPreview)findViewById(R.id.preview);
		_prefsListener.onSharedPreferenceChanged(_prefs, "filter");
		
		// Connect the take photo button
		{
			ImageButton button = (ImageButton)findViewById(R.id.takePhoto);
			button.setOnClickListener(new TakePhotoListener());
		}
		
		// Check which camera to use
		for (int i = 0, cameras = Camera.getNumberOfCameras(); i < cameras; i++) {
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(i, info);
			
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				_cameraid = i;
				break;
			}
		}
	}

	public static IImageFilter createEffectFilter(SharedPreferences prefs) {
		String filtertype = prefs.getString("filter", "bayer");
		if ("atkinson".equals(filtertype)) {
			return new AtkinsonFilter(IMAGE_WIDTH, IMAGE_HEIGHT);
		}

		return new BayerFilter(IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	private IImageFilter createFilter() {
		CompositeFilter filters = new CompositeFilter();
		filters.add(new YuvImageFilter());
		filters.add(createEffectFilter(_prefs));
		filters.add(new ImageBitmapFilter());
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
		stopPreview();
		super.onPause();
	}
	
	private void stopPreview() {
		if (_camera != null) {
			_camera.stopPreview();  
			_camera.release();
			_camera = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		stopPreview();
		
		switch (item.getItemId()) {
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
	
	private class TakePhotoListener implements View.OnClickListener, Camera.ShutterCallback, Camera.PictureCallback {
		@Override
		public void onClick(View v) {
			if (_camera != null) {
				_camera.takePicture(this, null, this);
			}
		}

		@Override
		public void onShutter() {}

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			if (_camera != null) {
				_camera.release();
				_camera = null;
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
			if ("filter".equals(key)) {
				_preview.setFilter(createFilter());
			}
		}
	}
}
