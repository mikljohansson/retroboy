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
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity {
	private static final String TAG = "MainActivity";
	
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
	
	private int _cameraCount;
	private boolean _hasCameraFlash;
	
	/**
	 * Actual physical orientation of the device
	 */
	private WindowOrientationListener _rotationListener;
	
	/**
	 * Listener to receive taken photos
	 */
	private TakePhotoListener _takePhotoListener = new TakePhotoListener();

	/**
	 * Listener to handle auto-focus
	 */
	private AutoFocusListener _autoFocusListener = new AutoFocusListener();
	private ImageView _autoFocusMarker;
	private boolean _hasAutoFocus;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_cameraCount = Camera.getNumberOfCameras();
		_hasCameraFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
		_hasAutoFocus = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS);
		
		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);

		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		// Setup preview display surface
		setContentView(R.layout.main_activity);
		_preview = (CameraPreview)findViewById(R.id.preview);
		_autoFocusMarker = (ImageView)findViewById(R.id.autoFocusMarker);
		
		// Connect the take photo button
		{
			ImageButton button = (ImageButton)findViewById(R.id.takePhoto);
			button.setOnClickListener(_takePhotoListener);
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
		
		// Remove the switch camera button if the device doesn't have multiple cameras
		if (_cameraCount < 2) {
			menu.getItem(0).setVisible(false).setEnabled(false);
		}

		// Set the correct icon for the filter button
		menu.getItem(1).setIcon(Pictures.getFilterDrawableResource(this));
		
		// Remove the flash on/off button if the device doesn't support it
		if (!_hasCameraFlash) {
			menu.getItem(3).setVisible(false).setEnabled(false);
		}
		
		// Disable the flash on/off button if the current camera doesn't support it
		if (_camera != null && (_camera.getParameters().getFlashMode() == null || _cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)) {
			menu.getItem(3).setEnabled(false);
		}
		
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
            	if (cameraid >= _cameraCount) {
            		cameraid = 0;
            	}
            	
            	SharedPreferences.Editor editor = _prefs.edit();
            	editor.putInt(PREF_CAMERA, cameraid);
            	editor.commit();
            	return true;
            }
            
            case R.id.selectImageOption: {
            	// Pick a gallery image to process
				Intent intent = new Intent(this, ImageActivity.class);
				intent.putExtra(ImageActivity.EXTRA_ACTION, ImageActivity.EXTRA_ACTION_PICK);
				startActivity(intent);
	            return true;
            }
            
            case R.id.toggleCameraTorch: {
            	if (_camera != null) {
            		Camera.Parameters params = _camera.getParameters();
            		if (!Camera.Parameters.FLASH_MODE_TORCH.equals(params.getFlashMode())) {
            			params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            		}
            		else {
            			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            		}
            		
            		_camera.setParameters(params);
            	}
            	
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
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_CAMERA:
					_takePhotoListener.takePhoto();
					return true;
			}
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	private void initCamera() {
		stopPreview();
		
		// Check which camera to use
		if (_cameraCount > 0) {
			int cameraid = _prefs.getInt(PREF_CAMERA, 0);
			if (cameraid >= _cameraCount) {
				cameraid = 0;
			}

			// Lock the camera
			_camera = Camera.open(cameraid);
			_cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraid, _cameraInfo);

			// Check if camera supports auto-focus
			if (_hasAutoFocus) {
				if (_cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					_autoFocusMarker.setVisibility(View.INVISIBLE);
					_preview.setOnClickListener(null);
					Log.i(TAG, "Auto-focus disabled");
				}
				else {
					_autoFocusMarker.setVisibility(View.VISIBLE);
					_preview.setOnClickListener(_autoFocusListener);
					Log.i(TAG, "Auto-focus enabled");
				}
			}
			
			// Configure the camera
			Camera.Parameters params = _camera.getParameters();
			params.setPreviewFormat(ImageFormat.NV21);
			
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
			params.setPreviewSize(previewSize.width, previewSize.height);
			
			// Invalidate the menu to redraw flash button
			invalidateOptionsMenu();
			
			// Apply the parameter changes
			_camera.setParameters(params);
			
			// Start the preview
			_preview.setCamera(_camera, _cameraInfo);
		}
	}
	
	private void initFilter() {
		// Get the contrast adjustment
		int contrast = 0;
		try {
			contrast = Integer.parseInt(_prefs.getString(Pictures.PREF_CONTRAST, "0"));
		}
		catch (NumberFormatException e) {}
		
		// Create the image filter pipeline
		CompositeFilter filters = new CompositeFilter();
		filters.add(new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT, contrast));
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
		public void takePhoto() {
			_preview.setOneShotPreviewCallback(this);
		}
		
		@Override
		public void onClick(View v) {
			takePhoto();
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
	
	private class AutoFocusListener implements View.OnClickListener, Camera.AutoFocusCallback {
		@Override
		public void onClick(View v) {
			_autoFocusMarker.setImageResource(R.drawable.ic_menu_focus);

			if (_camera != null) {
				_camera.autoFocus(this);
			}
		}

		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			if (success) {
				_autoFocusMarker.setImageResource(R.drawable.ic_menu_focus_ok);
			}
			else {
				_autoFocusMarker.setImageResource(R.drawable.ic_menu_focus_fail);
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
			
			// Get the contrast adjustment
			int contrast = 0;
			try {
				contrast = Integer.parseInt(_prefs.getString(Pictures.PREF_CONTRAST, "0"));
			}
			catch (NumberFormatException e) {}

			// Create the image filter pipeline
			YuvFilter yuvFilter = new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT, contrast);
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
			else if (Pictures.PREF_CONTRAST.equals(key)) {
				// Change the active image filter
				initFilter();
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
