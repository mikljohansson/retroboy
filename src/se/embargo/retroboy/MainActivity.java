package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.BitmapImageFilter;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.TransformFilter;
import se.embargo.retroboy.filter.YuvFilter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity {
	public static final String PREF_CAMERA = "camera";
	
	public static final String PREF_REVIEW = "review";
	public static final boolean PREF_REVIEW_DEFAULT = true;

	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private CameraPreview _preview;
	private Camera _camera;
	private Camera.CameraInfo _cameraInfo;
	
	/**
	 * Actual physical orientation of the device
	 */
	private WindowOrientationListener _rotationListener;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);
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
	
	@Override
	protected void onResume() {
		super.onResume();
		
		_rotationListener = new OrientationListener();
		_rotationListener.enable();
		
		initCamera();
	}

	@Override
	protected void onPause() {
		_rotationListener.disable();
		stopPreview();
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		_rotationListener.disable();
		stopPreview();
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		_rotationListener.disable();
		stopPreview();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
		
		// Set the correct icon for the filter button
		menu.getItem(1).setIcon(Pictures.getFilterDrawableResource(this));
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.switchFilterOption: {
				Pictures.toggleImageFilter(this);
				return true;
			}
			
			case R.id.switchCameraOption: {
				// Switch the active camera
            	int cameraid = _prefs.getInt(PREF_CAMERA, 0) + 1;
            	if (cameraid >= Camera.getNumberOfCameras()) {
            		cameraid = 0;
            	}
            	
            	SharedPreferences.Editor editor = _prefs.edit();
            	editor.putInt(PREF_CAMERA, cameraid);
            	editor.commit();
            	return true;
            }
            
            case R.id.selectImageOption: {
            	// Pick a gallery image to process
				Intent intent = new Intent(this, ReviewActivity.class);
				intent.putExtra(ReviewActivity.EXTRA_ACTION, "pick");
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
			_cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraid, _cameraInfo);

			// Configure the camera
			Camera.Parameters parameters = _camera.getParameters();
			parameters.setPreviewFormat(ImageFormat.NV21);
			
			// Select preview size that most closely matches the wanted size and dimensions
			Camera.Size previewSize = null;
			for (Camera.Size size : _camera.getParameters().getSupportedPreviewSizes()) {
				if (previewSize == null || 
					(previewSize.width < Pictures.IMAGE_WIDTH && previewSize.width < size.width ||
					 previewSize.width > size.width && size.width >= Pictures.IMAGE_WIDTH) &&
					ratioError(previewSize) >= ratioError(size)) {
					previewSize = size;
				}
			}
			parameters.setPreviewSize(previewSize.width, previewSize.height);
			
			// Apply the parameter changes
			_camera.setParameters(parameters);
			
			// Start the preview
			_preview.setCamera(_camera, _cameraInfo);
		}
	}
	
	private void initFilter() {
		CompositeFilter filters = new CompositeFilter();
		filters.add(new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT));
		filters.add(Pictures.createEffectFilter(this));
		filters.add(new ImageBitmapFilter());
		_preview.setFilter(filters);
	}
	
	private void stopPreview() {
		if (_camera != null) {
			_camera.stopPreview();
			_preview.setCamera(null, null);
			_camera.release();
			_camera = null;
			_cameraInfo = null;
		}
	}

	private static float ratioError(Camera.Size size) {
		return Math.round(Math.abs((float)Pictures.IMAGE_WIDTH / Pictures.IMAGE_HEIGHT - (float)size.width / size.height) * 10);
	}
	
	private class TakePhotoListener implements View.OnClickListener, PreviewCallback {
		@Override
		public void onClick(View v) {
			_preview.setOneShotPreviewCallback(this);
		}

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			Camera.Size size = camera.getParameters().getPreviewSize();

			// Get the current device orientation
			WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
			int rotation = _rotationListener.getCurrentRotation(windowManager.getDefaultDisplay().getRotation());
			
			if (_prefs.getBoolean(PREF_REVIEW, PREF_REVIEW_DEFAULT)) {
				// Parameterize the image review activity
				Intent intent = new Intent(MainActivity.this, ReviewActivity.class);
				ReviewActivity._inputdata = data;
				intent.putExtra(ReviewActivity.EXTRA_DATA_WIDTH, size.width);
				intent.putExtra(ReviewActivity.EXTRA_DATA_HEIGHT, size.height);
				intent.putExtra(ReviewActivity.EXTRA_DATA_FACING, _cameraInfo.facing);
				intent.putExtra(ReviewActivity.EXTRA_DATA_ORIENTATION, _cameraInfo.orientation);
				intent.putExtra(ReviewActivity.EXTRA_DATA_ROTATION, rotation);
				
				// Stop the running preview
				stopPreview();

				// Start image review
				startActivity(intent);
			}
			else {
				// Process and save the picture
				new ProcessFrameTask(camera, data, size.width, size.height, _cameraInfo.facing, _cameraInfo.orientation, rotation).execute();
			}
		}
	}
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (PREF_CAMERA.equals(key)) {
				initCamera();
			}
			else if (Pictures.PREF_FILTER.equals(key)) {
				// Update the action bar icon
				invalidateOptionsMenu();

				// Change the active image filter
				initFilter();
			}
		}
	}
	
	/**
	 * Process an camera preview frame
	 */
	private class ProcessFrameTask extends AsyncTask<Void, Void, File> {
		private Camera _camera;
		private IImageFilter _filter;
		private IImageFilter.ImageBuffer _buffer;
		private Bitmaps.Transform _transform;

		public ProcessFrameTask(Camera camera, byte[] data, int width, int height, int facing, int orientation, int rotation) {
			_camera = camera;
			_buffer = new IImageFilter.ImageBuffer(data, width, height);
			
			YuvFilter yuvFilter = new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);
			_transform = Pictures.createTransformMatrix(
				MainActivity.this, 
				yuvFilter.getEffectiveWidth(width, height), 
				yuvFilter.getEffectiveHeight(width, height), 
				facing, orientation, rotation);
			
			CompositeFilter filter = new CompositeFilter();
			filter.add(yuvFilter);
			filter.add(new ImageBitmapFilter());
			filter.add(new TransformFilter(_transform));
			filter.add(new BitmapImageFilter());
			filter.add(Pictures.createEffectFilter(MainActivity.this));
			filter.add(new ImageBitmapFilter());
			_filter = filter;
		}

		@Override
		protected File doInBackground(Void... params) {
			// Apply the image filter to the current image			
			_filter.accept(_buffer);
			
			// Write the image to disk
			return Pictures.compress(MainActivity.this, null, null, _buffer.bitmap);
		}
		
		@Override
		protected void onPostExecute(File result) {
			// Show confirmation that image was saved
			if (result != null) {
				Toast.makeText(MainActivity.this, getString(R.string.toast_saved_image, result.getName()), Toast.LENGTH_SHORT).show();
			}

			// Release buffer back to camera
			synchronized (_camera) {
				_camera.addCallbackBuffer(_buffer.frame);
			}
		}
	}
	
	private class OrientationListener extends WindowOrientationListener {
		public OrientationListener() {
			super(MainActivity.this);
			setAllow180Rotation(true);
		}

		@Override
		public void onOrientationChanged(int rotation) {}
	}
}
