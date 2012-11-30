package se.embargo.retroboy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import se.embargo.core.Strings;
import se.embargo.core.databinding.DataBindingContext;
import se.embargo.core.databinding.IPropertyDescriptor;
import se.embargo.core.databinding.PojoProperties;
import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.TransformFilter;
import se.embargo.retroboy.filter.YuvFilter;
import se.embargo.retroboy.widget.ListPreferenceDialog;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.FloatMath;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.Window;

public class MainActivity extends SherlockActivity {
	private static final String TAG = "MainActivity";
	
	public static final String PREF_CAMERA = "camera";
	
	/**
	 * Radians per second required to trigger movement detection
	 */
	private static final float GYROSCOPE_MOVEMENT_THRESHOLD = 0.25f;
	
	/**
	 * Radians per second required to detect non-movement
	 */
	private static final float GYROSCOPE_FOCUSING_THRESHOLD = 0.15f;
	
	/**
	 * Milliseconds of non-movement required to trigger auto focus 
	 */
	private static final long GYROSCOPE_FOCUSING_TIMEOUT = 300;

	/**
	 * How many virtual zoom steps there are
	 */
	private static final float ZOOM_MAX = 300.0f;

	/**
	 * How much each volume up/down step should affect the zoom level
	 */
	private static final float ZOOM_VOLUME_STEP = 10.0f;
	
	/**
	 * How much the scale touch event should affect the zoom level
	 */
	private static final float ZOOM_SCALE_FACTOR = 250.0f;
	
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private CameraPreview _preview;
	private IObservableValue<CameraHandle> _cameraHandle = new WritableValue<CameraHandle>();
	private IObservableValue<String> _sceneMode = new WritableValue<String>(Camera.Parameters.SCENE_MODE_AUTO);
	
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
	
	private View _detailedPreferences;
	private ListView _detailedPreferencesList;
	private PreferenceAdapter _detailedPreferenceAdapter = new PreferenceAdapter();
	
	/**
	 * Connect to the gyroscope to detect when auto focus need to trigger
	 */
	private SensorManager _sensorManager;
	private Sensor _gyroscope;
	private GyroscopeListener _gyroListener = new GyroscopeListener();
	
	/**
	 * Current zoom level in range [0, ZOOM_MAX)
	 */
	private IObservableValue<Float> _zoomLevel = new WritableValue<Float>(0.0f);
	
	/**
	 * Currently running image processing task
	 */
	private ProcessFrameTask _task = null;

	/**
	 * Tracks if a single finger is touching the screen.
	 */
	private boolean _singleTouch = false;
	
	/**
	 * Context for all data bindings. 
	 */
	private DataBindingContext _binding = new DataBindingContext();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_cameraCount = Camera.getNumberOfCameras();
		_hasCameraFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
		_hasAutoFocus = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS);
		
		_sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		_gyroscope = _sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);

		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		// Setup preview display surface
		setContentView(R.layout.main_activity);
		_preview = (CameraPreview)findViewById(R.id.cameraPreview);
		_autoFocusMarker = (ImageView)findViewById(R.id.autoFocusMarker);
		_detailedPreferences = findViewById(R.id.detailedPreferences);
		_detailedPreferencesList = (ListView)findViewById(R.id.detailedPreferencesList);
		_detailedPreferencesList.setAdapter(_detailedPreferenceAdapter);
		_detailedPreferencesList.setOnItemClickListener(_detailedPreferenceAdapter);

		// Connect the take photo button
		{
			ImageButton button = (ImageButton)findViewById(R.id.takePhotoButton);
			button.setOnClickListener(_takePhotoListener);
		}
		
		// Connect the open gallery button
		{
			ImageButton button = (ImageButton)findViewById(R.id.openGalleryButton);
			button.setOnClickListener(new OpenGalleryButtonListener());
		}

		// Connect the settings button
		{
			EditSettingsButtonListener listener = new EditSettingsButtonListener();
			ImageButton button = (ImageButton)findViewById(R.id.editSettingsButton);
			button.setOnClickListener(listener);
			
			Button cancelButton = (Button)findViewById(R.id.cancelDetailedPreferences);
			cancelButton.setOnClickListener(listener);
		}

		// Connect the contrast button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.adjustContrastButton);
			button.setOnClickListener(new ListPreferenceDialogListener(
				Pictures.PREF_CONTRAST, getResources().getString(R.string.pref_contrast_default),
				R.string.pref_title_contrast, R.array.pref_contrast_labels, R.array.pref_contrast_values));
		}

		// Connect the image filter button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.switchFilterButton);
			button.setOnClickListener(new ListPreferenceDialogListener(
				Pictures.PREF_FILTER, getResources().getString(R.string.pref_filter_default),
				R.string.pref_title_filter, R.array.pref_filter_labels, R.array.pref_filter_values));
		}

		// Connect the switch camera button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.switchCameraButton);
			button.setOnClickListener(new SwitchCameraButtonListener());

			// Disable the switch camera button if the device doesn't have multiple cameras
			if (_cameraCount < 2) {
				button.setEnabled(false);
			}
		}
		
		// Connect the flash button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.adjustFlashButton);
			button.setOnClickListener(new FlashButtonListener());
			
			if (!_hasCameraFlash) {
				// Disable the flash on/off button if the device doesn't support it
				button.setEnabled(false);
			}
			else {
				// Disable the flash on/off button if the current camera doesn't support it
				_cameraHandle.addChangeListener(new IChangeListener<MainActivity.CameraHandle>() {
					@Override
					public void handleChange(ChangeEvent<CameraHandle> event) {
						CameraHandle handle = event.getValue();
						if (handle != null && (handle.camera.getParameters().getFlashMode() == null || handle.info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)) {
							button.setEnabled(false);
						}
						else {
							button.setEnabled(true);
						}
					}
				});
			}
		}
		
		// Connect the zoom support
		_cameraHandle.addChangeListener(new ZoomCameraHandler());
		_preview.setOnTouchListener(new GestureDetector());
		
		// Connect the scene mode parameter
		_binding.bindValue(
			PojoProperties.value(new SceneModeDescriptor()).observe(_cameraHandle), 
			_sceneMode);
		
		// Initialize the image filter
		initFilter();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		// Listen to preference changes
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
		// Listen to device tilt sensor
		_rotationListener = new OrientationListener();
		_rotationListener.enable();
		
		// Update the last photo thumbnail
		new GetLastThumbnailTask().execute();
		
		// Start the preview
		initCamera();
		
		// Connect to the sensors
		_sensorManager.registerListener(_gyroListener, _gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
		_gyroListener.reset();
	}

	private void stop() {
		_prefs.unregisterOnSharedPreferenceChangeListener(_prefsListener);
		_sensorManager.unregisterListener(_gyroListener);
		_rotationListener.disable();
		resetFocus();
		stopPreview();
	}
	
	@Override
	protected void onPause() {
		stop();
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		stop();
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		stop();
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_FOCUS:
				// Reset auto focus when dedicated photo button is completely released
				_autoFocusListener.resetFocus();
				return true;
		}

		return super.onKeyUp(keyCode, event);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		resetFocus();

		switch (keyCode) {
			case KeyEvent.KEYCODE_FOCUS:
				// Trigger auto focus when dedicated photo button is pressed half way
				if (event.getRepeatCount() == 0) {
					_autoFocusListener.autoFocus();
				}
				
				return true;
		
			case KeyEvent.KEYCODE_CAMERA:
				if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
					// Take photo when the dedicated photo button is pressed
					_takePhotoListener.takePhoto();
				}
				
				return true;
				
			case KeyEvent.KEYCODE_VOLUME_UP: {
				// Zoom in when volume up is pressed
				_zoomLevel.setValue(Math.min(_zoomLevel.getValue() + ZOOM_VOLUME_STEP, ZOOM_MAX));
				return true;
			}

			case KeyEvent.KEYCODE_VOLUME_DOWN: {
				// Zoom out when volume down is pressed
				_zoomLevel.setValue(Math.max(0, _zoomLevel.getValue() - ZOOM_VOLUME_STEP));
				return true;
			}
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	private void initCamera() {
		stopPreview();
		
		// Check which camera to use
		if (_cameraCount > 0) {
			Log.i(TAG, "Acquiring camera");
			int cameraid = _prefs.getInt(PREF_CAMERA, 0);
			if (cameraid >= _cameraCount) {
				cameraid = 0;
			}

			// Lock the camera
			Camera camera = Camera.open(cameraid);
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraid, cameraInfo);
			_cameraHandle.setValue(new CameraHandle(camera, cameraInfo, cameraid));

			// Check if camera supports auto-focus
			initAutoFocusMarker();
			
			// Configure the camera
			Camera.Parameters params = camera.getParameters();
			params.setPreviewFormat(ImageFormat.NV21);
			
			// Select preview size that most closely matches the wanted size and dimensions
			Pictures.Resolution target = Pictures.getResolution(this, _prefs);
			Camera.Size optimal = null;
			Log.i(TAG, "Target output resolution: " + target);
			
			for (Camera.Size candidate : camera.getParameters().getSupportedPreviewSizes()) {
				if (optimal == null || ratioError(candidate, target) < ratioError(optimal, target) ||
					((optimal.width < target.width && optimal.width < candidate.width ||
					  optimal.width > candidate.width && candidate.width >= target.width))) {
					optimal = candidate;
				}
			}
			params.setPreviewSize(optimal.width, optimal.height);
			Log.i(TAG, "Found preview resolution: " + optimal.width + "x" + optimal.height);
			
			// Apply the parameter changes
			camera.setParameters(params);
			
			// Start the preview
			Log.i(TAG, "Starting preview");
			_preview.setCamera(camera, cameraInfo);
		}
	}
	
	private void initFilter() {
		// Get the resolution and contrast from preferences
		Pictures.Resolution resolution = Pictures.getResolution(this, _prefs);
		int contrast = Pictures.getContrast(this, _prefs);
		
		// Create the image filter pipeline
		CompositeFilter filter = new CompositeFilter();
		IImageFilter effect = Pictures.createEffectFilter(this);
		filter.add(new YuvFilter(resolution.width, resolution.height, contrast, effect.isColorFilter()));
		filter.add(effect);
		filter.add(new ImageBitmapFilter());
		_preview.setFilter(filter);
	}
	
	private void stopPreview() {
		// Cancel any image processing tasks
		if (_task != null) {
			_task.cancel(false);
			_task = null;
		}
		
		// Stop the preview and release the camera
		CameraHandle handle = _cameraHandle.getValue();
		if (handle != null) {
			Log.i(TAG, "Releasing camera");
			_preview.setCamera(null, null);
			handle.camera.release();
			_cameraHandle.setValue(null);
		}
	}

	private static float ratioError(Camera.Size size, Pictures.Resolution resolution) {
		return Math.round(Math.abs((float)resolution.width / resolution.height - (float)size.width / size.height) * 10);
	}

	private long getLastImageId() {
		ContentResolver resolver = getContentResolver();
		Uri baseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[] {
        	MediaStore.Images.ImageColumns._ID};
        
        String selection = 
        	MediaStore.Images.ImageColumns.MIME_TYPE + "='image/png' AND " +
        	MediaStore.Images.ImageColumns.BUCKET_ID + '=' + getBucketId();
        
        String order = 
        	MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC," + 
        	MediaStore.Images.ImageColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } 
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return -1;
	}
	
	@SuppressLint("DefaultLocale")
	private static String getBucketId() {
		// Matches code in MediaProvider.computeBucketValues()
		return String.valueOf(Pictures.getStorageDirectory().toString().toLowerCase().hashCode());
	}

	private Bitmap getPreviousThumbnail() {
        long id = getLastImageId();
        if (id >= 0) {
			return MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        }
        
        return null;
	}
	
	private void setPreviousThumbnail(Bitmap result) {
		ImageButton button = (ImageButton)findViewById(R.id.openGalleryButton);
		View layout = (View)button.getParent();
		
		button.setImageBitmap(result);
		layout.setVisibility(result != null ? View.VISIBLE : View.GONE);
		button.setEnabled(result != null);
	}
	
	private void initAutoFocusMarker() {
		if (_hasAutoFocus) {
			CameraHandle handle = _cameraHandle.getValue();
			
			if (handle != null && handle.info.facing == Camera.CameraInfo.CAMERA_FACING_BACK && _detailedPreferences.getVisibility() != View.VISIBLE) {
				_autoFocusMarker.setVisibility(View.VISIBLE);
				Log.i(TAG, "Auto-focus enabled");
			}
			else {
				_autoFocusMarker.setVisibility(View.GONE);
				Log.i(TAG, "Auto-focus disabled");
			}
		}
	}
	
	private void resetFocus() {
		if (_detailedPreferences.getVisibility() == View.VISIBLE) {
			_detailedPreferences.setVisibility(View.GONE);
			initAutoFocusMarker();
		}
	}
	
	private class GetLastThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
		@Override
		protected Bitmap doInBackground(Void... params) {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException e) {}
	    	
	    	return getPreviousThumbnail();
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			setPreviousThumbnail(result);
		}
	}
	
	private static class CameraHandle {
		public final Camera camera;
		public final Camera.CameraInfo info;
		public final int id;
		
		public CameraHandle(Camera camera, Camera.CameraInfo info, int id) {
			this.camera = camera;
			this.info = info;
			this.id = id;
		}
	}
	
	private class TakePhotoListener implements View.OnClickListener, PreviewCallback {
		public void takePhoto() {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				handle.camera.setPreviewCallbackWithBuffer(this);
			}
		}
		
		@Override
		public void onClick(View v) {
			resetFocus();
			takePhoto();
		}

		@Override
		public void onPreviewFrame(byte[] data, Camera camera_) {
			CameraHandle handle = _cameraHandle.getValue();

			// data may be null if buffer was too small
			if (handle != null && data != null) {
				Log.d(TAG, "Captured frame to save");
				
				// Stop receiving camera frames
				handle.camera.setPreviewCallbackWithBuffer(null);
	
				// Get the current device orientation
				WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
				int rotation = _rotationListener.getCurrentRotation(windowManager.getDefaultDisplay().getRotation());
				
				// Process and save the picture
				new ProcessFrameTask(handle, data, rotation).execute();
			}
		}
	}
	
	private class AutoFocusListener implements Camera.AutoFocusCallback {
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
	
	private class GyroscopeListener implements SensorEventListener {
		private boolean _movement = true;
		private long _timestamp = 0;
		
		public GyroscopeListener() {
			reset();
		}
		
		public void reset() {
			_movement = true;
			_timestamp = System.nanoTime();
		}
		
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}

		@Override
		public void onSensorChanged(SensorEvent event) {
			float rotation = (Math.abs(event.values[0]) + Math.abs(event.values[1]) + Math.abs(event.values[2]));
			if (_movement) {
				if (rotation >= GYROSCOPE_FOCUSING_THRESHOLD) {
					// Still moving
					_timestamp = event.timestamp;
				}
				else if (((event.timestamp - _timestamp) / 1000000) > GYROSCOPE_FOCUSING_TIMEOUT) {
					// Movement stopped
					_movement = false;
					_autoFocusListener.autoFocus();
				}
			}
			else if (rotation >= GYROSCOPE_MOVEMENT_THRESHOLD) {
				// Movement detected
				_movement = true;
				_timestamp = event.timestamp;
				_autoFocusListener.resetFocus();
			}
		}
	}
	
	/**
	 * Process an camera preview frame
	 */
	private class ProcessFrameTask extends AsyncTask<Void, Void, Bitmap> {
		private IImageFilter _filter;
		private IImageFilter.ImageBuffer _buffer;
		private ProgressDialog _progress;

		public ProcessFrameTask(CameraHandle handle, byte[] data, int rotation) {
			Camera.Size size = handle.camera.getParameters().getPreviewSize();
			_buffer = new IImageFilter.ImageBuffer(data, size.width, size.height);
			_task = this;

			// Get the resolution and contrast from preferences
			Pictures.Resolution resolution = Pictures.getResolution(MainActivity.this, _prefs);
			int contrast = Pictures.getContrast(MainActivity.this, _prefs);

			// Create the image filter pipeline
			IImageFilter effect = Pictures.createEffectFilter(MainActivity.this);
			YuvFilter yuvFilter = new YuvFilter(resolution.width, resolution.height, contrast, effect.isColorFilter());
			Bitmaps.Transform transform = Pictures.createTransformMatrix(
				yuvFilter.getEffectiveWidth(size.width, size.height), 
				yuvFilter.getEffectiveHeight(size.width, size.height), 
				handle.info.facing, handle.info.orientation, rotation,
				resolution);
			
			CompositeFilter filter = new CompositeFilter();
			filter.add(yuvFilter);
			filter.add(effect);
			filter.add(new ImageBitmapFilter());
			filter.add(new TransformFilter(transform));
			_filter = filter;
		}
		
		@Override
		protected void onPreExecute() {
			// Show a progress dialog
			_progress = new ProgressDialog(MainActivity.this);
			_progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			_progress.setIndeterminate(true);
			_progress.setMessage(getResources().getString(R.string.msg_saving_image));
			_progress.show();
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			Log.d(TAG, "Processing captured image");
			
			// Apply the image filter to the current image			
			_filter.accept(_buffer);
			
			// Write the image to disk
			File file = Pictures.compress(MainActivity.this, null, null, _buffer.bitmap);
			Log.i(TAG, "Wrote image to disk: " + file);
		
			// Update the last photo thumbnail
			return getPreviousThumbnail();
		}
		
		@Override
		protected void onCancelled() {
			Log.w(TAG, "Image processing was cancelled");
			
			// Close the progress dialog and restart preview
			dismiss();
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			// Update the last image thumbnail
			setPreviousThumbnail(result);
			
			// Close the progress dialog and restart preview
			dismiss();

			Log.i(TAG, "Successfully captured image");
		}
			
		private void dismiss() {
			if (_task == this) {
				_task = null;
			}

			_progress.dismiss();
			
			// Continue preview
			if (!isCancelled()) {
				_preview.initPreviewCallback();
			}
		}
	}
	
	/**
	 * Listens for preference changes and applies updates
	 */
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				if (PREF_CAMERA.equals(key)) {
					Log.i(TAG, "Camera changed through preferences");
					
					// Reinitialize the camera
					initCamera();
				}
				else if (Pictures.PREF_FILTER.equals(key) || Pictures.PREF_CONTRAST.equals(key) || Pictures.PREF_ORIENTATION.equals(key)) {
					// Change the active image filter
					initFilter();
				}
				else if (Pictures.PREF_RESOLUTION.equals(key)) {
					Log.i(TAG, "Resolution changed through preferences");

					// Reinitialize the camera
					initCamera();
					
					// Change the active image filter
					initFilter();
				}
			}
			
			_detailedPreferenceAdapter.notifyDataSetChanged();
		}
	}

	/**
	 * Listens for device orientation changes and stores the current orientation
	 */
	private class OrientationListener extends WindowOrientationListener {
		public OrientationListener() {
			super(MainActivity.this);
			setAllow180Rotation(true);
		}

		@Override
		public void onOrientationChanged(int rotation) {}
	}

	/**
	 * Starts the gallery in the Retroboy folder
	 */
	private class OpenGalleryButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			String bucketid = getBucketId();
			String imageid = String.valueOf(getLastImageId());
			Uri.Builder builder = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(imageid);
			
			// Android 3.0+ requires the buckedId in order to show additional images in the folder
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				builder.appendQueryParameter("bucketId", bucketid);
			}
			
			Uri target = builder.build();
			Intent intent = new Intent(Intent.ACTION_VIEW, target);

			try {
				startActivity(intent);
			}
			catch (ActivityNotFoundException e) {
				Log.e(TAG, "Could not start gallery activity", e);
			}
		}
	}

	private class EditSettingsButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			if (_detailedPreferences.getVisibility() == View.VISIBLE) {
				resetFocus();
			}
			else {
				_autoFocusMarker.setVisibility(View.GONE);
				_detailedPreferences.setVisibility(View.VISIBLE);
			}
		}
	}

	/**
	 * Shows the preference dialog for a specific preference.
	 */
	private class ListPreferenceDialogListener extends ListPreferenceDialog implements OnClickListener {
		public ListPreferenceDialogListener(String prefname, String prefdefault, int title, int labels, int values) {
			super(MainActivity.this, _prefs, prefname, prefdefault, title, labels, values);
		}
		
		@Override
		public void onClick(View v) {
			resetFocus();
			show();
		}
	}

	/**
	 * Toggles the active camera
	 */
	private class SwitchCameraButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			resetFocus();

			int cameraid = _prefs.getInt(PREF_CAMERA, 0) + 1;
        	if (cameraid >= _cameraCount) {
        		cameraid = 0;
        	}
        	
        	SharedPreferences.Editor editor = _prefs.edit();
        	editor.putInt(PREF_CAMERA, cameraid);
        	editor.commit();
		}
	}

	/**
	 * Toggles the flash mode
	 */
	private class FlashButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			resetFocus();

			CameraHandle handle = _cameraHandle.getValue();
			Camera.Parameters params = handle.camera.getParameters();
			
    		if (!Camera.Parameters.FLASH_MODE_TORCH.equals(params.getFlashMode())) {
    			params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
    		}
    		else {
    			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    		}
    		
    		try {
    			handle.camera.setParameters(params);
    		}
    		catch (Exception e) {
    			Log.e(TAG, "Failed to toggle flash", e);
    		}
		}
	}
	
	private class GestureDetector extends ScaleGestureDetector implements OnTouchListener {
		public GestureDetector() {
			super(MainActivity.this, new ScaleListener());
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			resetFocus();

			boolean result = super.onTouchEvent(event);
			switch (event.getAction()) {
				case MotionEvent.ACTION_CANCEL:
					_singleTouch = false;
					break;

				case MotionEvent.ACTION_UP:
					if (_singleTouch) {
						Log.i(TAG, "Detected single touch, triggering auto-focus");
						_autoFocusListener.autoFocus();
					}
					
					_singleTouch = false;
					break;

				case MotionEvent.ACTION_DOWN:
					_singleTouch = true;
					break;
			}
			
			return result;
		}
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			_singleTouch = false;

			// Zoom in when volume up is pressed
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				Camera.Parameters params = handle.camera.getParameters();
				if (params.isZoomSupported() || params.isSmoothZoomSupported()) {
					float value = _zoomLevel.getValue();
					value += detector.getScaleFactor() * ZOOM_SCALE_FACTOR - ZOOM_SCALE_FACTOR;
					_zoomLevel.setValue(Math.max(0, Math.min(value, ZOOM_MAX)));
				}
			}
			
			return true;
		}
	}
	
	private class ZoomLevelHandler implements IChangeListener<Float> {
		private final int _max;
		private int _prev = 0;
		
		public ZoomLevelHandler(int max) {
			_max = max;
		}
		
		@Override
		public void handleChange(ChangeEvent<Float> event) {
			int value = (int)FloatMath.floor(((float)_max) * (event.getValue() / ZOOM_MAX));
			if (value != _prev) {
				CameraHandle handle = _cameraHandle.getValue();
				if (handle != null) {
					Camera.Parameters params = handle.camera.getParameters();
					params.setZoom(value);
	
		    		try {
		    			Log.i(TAG, "Set zoom level: " + value);
		    			handle.camera.setParameters(params);
		    			_prev = value;
		    		}
		    		catch (Exception e) {
		    			Log.e(TAG, "Failed to zoom", e);
		    		}
				}
			}
		}
	}
	
	private class ZoomSmoothHandler implements IChangeListener<Float> {
		private final int _max;
		private int _prev = 0;

		public ZoomSmoothHandler(int max) {
			_max = max;
		}
		
		@Override
		public void handleChange(ChangeEvent<Float> event) {
			int value = (int)FloatMath.floor(((float)_max) * (event.getValue() / ZOOM_MAX));
			if (value != _prev) {
				CameraHandle handle = _cameraHandle.getValue();
				if (handle != null) {
		    		try {
		    			Log.i(TAG, "Smooth zoom to level: " + value);
						handle.camera.startSmoothZoom(value);
						_prev = value;
		    		}
		    		catch (Exception e) {
		    			Log.e(TAG, "Failed to start smooth zoom", e);
		    		}
				}
			}
		}
	}
	
	private class ZoomCameraHandler implements IChangeListener<CameraHandle> {
		private ZoomSmoothHandler _zoomSmoothHandler = null;
		private ZoomLevelHandler _zoomLevelHandler = null;
		
		@Override
		public void handleChange(ChangeEvent<CameraHandle> event) {
			_zoomLevel.removeChangeListener(_zoomSmoothHandler);
			_zoomLevel.removeChangeListener(_zoomLevelHandler);
			_zoomLevel.setValue(0.0f);
			
			CameraHandle handle = event.getValue();
			if (handle != null) {
				Camera.Parameters params = handle.camera.getParameters();
				
				if (params.isSmoothZoomSupported()) {
					_zoomSmoothHandler = new ZoomSmoothHandler(params.getMaxZoom());
					_zoomLevel.addChangeListener(_zoomSmoothHandler);
				}
				else if (params.isZoomSupported()) {
					_zoomLevelHandler = new ZoomLevelHandler(params.getMaxZoom());
					_zoomLevel.addChangeListener(_zoomLevelHandler);
				}
			}
		}
	}
	
	private abstract class PreferenceItem {
		public final int _title;
		
		public PreferenceItem(int title) {
			this._title = title;
		}

		public abstract String getValueLabel();
		public abstract void onClick();
	}
	
	private class ArrayPreferenceItem extends PreferenceItem {
		public final String _key;
		public final int _defvalue, _labels, _values;
		
		public ArrayPreferenceItem(String key, int defvalue, int title, int labels, int values) {
			super(title);
			_defvalue = defvalue;
			_key = key;
			_labels = labels;
			_values = values;
		}

		@Override
		public String getValueLabel() {
			String[] labels = getResources().getStringArray(_labels);
			String[] values = getResources().getStringArray(_values);
			String defvalue = getResources().getString(_defvalue);
			String value = _prefs.getString(_key, defvalue);

			for (int i = 0; i < labels.length && i < values.length; i++) {
				if (value.equals(values[i])) {
					return labels[i];
				}
			}
			
			return "";
		}
		
		@Override
		public void onClick() {
			String defvalue = getResources().getString(_defvalue);
			ListPreferenceDialog dialog = new ListPreferenceDialog(
				MainActivity.this, _prefs, _key, defvalue,
				_title, _labels, _values);
			dialog.show();
		}
	}

	private class SceneModePreferenceItem extends PreferenceItem {
		public SceneModePreferenceItem() {
			super(R.string.menu_option_scenemode);
		}

		@Override
		public String getValueLabel() {
			return getValueLabel(_sceneMode.getValue());
		}
		
		@Override
		public void onClick() {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				List<String> modes = handle.camera.getParameters().getSupportedSceneModes();
				if (modes != null) {
					// Sort scene mode ids and keep "auto" first
					modes.remove(Camera.Parameters.SCENE_MODE_AUTO);
					
					String[] values = new String[modes.size() + 1];
					for (int i = 1; i < values.length; i++) {
						values[i] = modes.get(i - 1);
					}
					
					Arrays.sort(values, 1, values.length);
					values[0] = Camera.Parameters.SCENE_MODE_AUTO;
					
					// Create uppercased labels
					String[] labels = new String[values.length];
					for (int i = 0; i < values.length; i++) {
						labels[i] = getValueLabel(values[i]);
					}
					
					// Show preference dialog
					ListPreferenceDialog dialog = new ListPreferenceDialog(
						MainActivity.this, _sceneMode, _title, labels, values);
					dialog.show();
				}
			}
		}
		
		@SuppressLint("DefaultLocale")
		private String getValueLabel(String value) {
			if (value == null) {
				value = Camera.Parameters.SCENE_MODE_AUTO;
			}
			
			Resources resources = getResources();
			int id = resources.getIdentifier("label_scenemode_" + value.toLowerCase().replaceAll("[^a-z]", "_"), "string", getPackageName());
			if (id != 0) {
				return resources.getString(id);
			}
			
			return Strings.upperCaseWords(value);
		}
	}
	
	private class SceneModeDescriptor implements IPropertyDescriptor<CameraHandle, String> {
		@Override
		public String getValue(CameraHandle object) {
			if (object != null) {
				Camera.Parameters params = object.camera.getParameters();
				if (params.getSupportedSceneModes() != null) {
					return params.getSceneMode();
				}
			}
			
			return Camera.Parameters.SCENE_MODE_AUTO;
		}

		@Override
		public void setValue(CameraHandle object, String value) {
			if (object != null) {
				try {
					Camera.Parameters params = object.camera.getParameters();
					if (params.getSupportedSceneModes() != null) {
						params.setSceneMode(value);
						object.camera.setParameters(params);
						Log.i(TAG, "Applied scene mode: " + value);
					}
				}
	    		catch (Exception e) {
	    			Log.e(TAG, "Failed to set scene mode", e);
	    		}
			}
		}
	}
	
	private class PreferenceAdapter extends BaseAdapter implements AdapterView.OnItemClickListener, IChangeListener<String> {
		private List<PreferenceItem> _items = new ArrayList<PreferenceItem>();
		
		public PreferenceAdapter() {
			_items.add(new ArrayPreferenceItem(
				Pictures.PREF_RESOLUTION, R.string.pref_resolution_default, R.string.menu_option_resolution, 
				R.array.pref_resolution_labels, R.array.pref_resolution_values));
			
			_items.add(new SceneModePreferenceItem());
			
			_items.add(new ArrayPreferenceItem(
				Pictures.PREF_CONTRAST, R.string.pref_contrast_default, R.string.menu_option_contrast, 
				R.array.pref_contrast_labels, R.array.pref_contrast_values));
			
			_items.add(new ArrayPreferenceItem(
				Pictures.PREF_FILTER, R.string.pref_filter_default, R.string.menu_option_filter, 
				R.array.pref_filter_labels, R.array.pref_filter_values));

			_items.add(new ArrayPreferenceItem(
				Pictures.PREF_ORIENTATION, R.string.pref_orientation_default, R.string.menu_option_orientation, 
				R.array.pref_orientation_labels, R.array.pref_orientation_values));
			
			_sceneMode.addChangeListener(this);
		}
		
		@Override
		public int getCount() {
			return _items.size();
		}

		@Override
		public Object getItem(int position) {
			return _items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
			    LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			    convertView = inflater.inflate(R.layout.camera_preference_item, parent, false);
			}
			
		    TextView titleView = (TextView)convertView.findViewById(R.id.prefItemTitle);
		    TextView valueView = (TextView)convertView.findViewById(R.id.prefItemValue);

		    PreferenceItem item = _items.get(position);
		    titleView.setText(item._title);
		    valueView.setText(item.getValueLabel());
		    return convertView;
		}
		
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			PreferenceItem item = _items.get(position);
			item.onClick();
		}

		@Override
		public void handleChange(ChangeEvent<String> event) {
			notifyDataSetChanged();
		}
	}
}
