package se.embargo.retroboy;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import se.embargo.core.concurrent.ProgressTask;
import se.embargo.core.databinding.PreferenceProperties;
import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.core.widget.ListPreferenceDialog;
import se.embargo.core.widget.SeekBarDialog;
import se.embargo.retroboy.color.IIndexedPalette;
import se.embargo.retroboy.color.IPalette;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.TransformFilter;
import se.embargo.retroboy.filter.YuvFilter;
import se.embargo.retroboy.widget.PreferenceListAdapter;
import se.embargo.retroboy.widget.PreferenceListAdapter.ArrayPreferenceItem;
import se.embargo.retroboy.widget.PreferenceListAdapter.PreferenceItem;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import android.app.Activity;
import android.view.Menu;
import android.view.Window;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	
	public static final String PREF_CAMERA = "camera";
	
	private static final String PREF_AUTOFOCUS = "autofocus";
	private static final String PREF_AUTOFOCUS_AUTO = "auto";
	
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
	
	/**
	 * Application wide preferences
	 */
	protected SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private CameraPreview _preview;
	private IObservableValue<CameraHandle> _cameraHandle = new WritableValue<CameraHandle>();
	
	private int _cameraCount;
	private boolean _hasCameraFlash;
	
	/**
	 * Actual physical orientation of the device
	 */
	private WindowOrientationListener _rotationListener;
	
	/**
	 * Listener to receive taken photos
	 */
	private View.OnTouchListener _captureListener = new TakePhotoListener();

	/**
	 * Listener to handle auto-focus
	 */
	private FocusManager _focusManager;
	
	private ListView _detailedPreferences;
	private PreferenceListAdapter _detailedPreferenceAdapter = new PreferenceListAdapter(this);
	
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
	 * Picture or video mode.
	 */
	private enum CameraState { Picture, Video, Recording }

	/**
	 * Current camera state. 
	 */
	private IObservableValue<CameraState> _cameraState = new WritableValue<CameraState>(CameraState.Picture);
	
	/**
	 * Filter tasked with encoding animated GIF's from the frame stream.
	 */
	private VideoRecorder _videoRecorder;
	
	/**
	 * Current effect filter.
	 * 
	 * Some filters maintain internal state (e.g. PXL-2000 filter) that correspond to 
	 * previous frames. When taking photos the filter instance used for preview must 
	 * be used to process the captured frame.
	 */
	private IImageFilter _effectFilter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_cameraCount = Camera.getNumberOfCameras();
		_hasCameraFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
		
		_sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		_gyroscope = _sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);

		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to portrait orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		// Setup preview display surface
		setContentView(R.layout.main_activity);

		View previewLayout = findViewById(R.id.cameraPreviewLayout);
		_focusManager = new FocusManager(this, _prefs, _cameraHandle, previewLayout);
		_videoRecorder = new VideoRecorder(this, previewLayout);
		_videoRecorder.setStateChangeListener(new VideoRecorder.StateChangeListener() {
			@Override
			public void onRecord() {
				_cameraState.setValue(CameraState.Recording);
			}

			@Override
			public void onStop() {
				_cameraState.setValue(CameraState.Video);

				// Stop preview while image is processed
				CameraHandle handle = _cameraHandle.getValue();
				if (handle != null) {
					handle.camera.setPreviewCallbackWithBuffer(null);
				}
			}
			
			@Override
			public void onFinish() {
				_cameraState.setValue(CameraState.Video);
			
				// Callback after captured media
				onMediaCaptured();
				
				// Update the last photo thumbnail
				new GetLastThumbnailTask().execute();

				// Restart preview
				_preview.initPreviewCallback();
			}
		});
		
		_preview = (CameraPreview)findViewById(R.id.cameraPreview);
		_detailedPreferences = (ListView)findViewById(R.id.detailedPreferences);
		_detailedPreferences.setOnItemClickListener(_detailedPreferenceAdapter);

		// Add the items of the details preference dialog
		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_FILTER, R.string.pref_filter_default, R.string.menu_option_filter, 
			R.array.pref_filter_labels, R.array.pref_filter_values));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_RESOLUTION, R.string.pref_resolution_default, R.string.menu_option_resolution, 
			R.array.pref_resolution_labels, R.array.pref_resolution_values));
		
		_detailedPreferenceAdapter.add(new SceneModePreferenceItem());
		
		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_CONTRAST, R.string.pref_contrast_default, R.string.menu_option_contrast, 
			R.array.pref_contrast_labels, R.array.pref_contrast_values));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_MATRIXSIZE, R.string.pref_matrixsize_default, R.string.menu_option_matrixsize, 
			R.array.pref_matrixsize_labels, R.array.pref_matrixsize_values,
			new PreferenceListAdapter.PreferencePredicate(_prefs, 
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
					Pictures.PREF_FILTER_GAMEBOY_CAMERA,
					Pictures.PREF_FILTER_AMSTRAD_CPC464,
					Pictures.PREF_FILTER_COMMODORE_64,
					Pictures.PREF_FILTER_AMIGA_500,
			})));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_RASTERLEVEL, R.string.pref_rasterlevel_default, R.string.menu_option_rasterlevel, 
			R.array.pref_rasterlevel_labels, R.array.pref_rasterlevel_values,
			new PreferenceListAdapter.PreferencePredicate(_prefs, 
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
					Pictures.PREF_FILTER_AMSTRAD_CPC464,
					Pictures.PREF_FILTER_COMMODORE_64,
			})));

		_detailedPreferenceAdapter.add(new Pictures.PalettePreferenceItem(this, _prefs));
		_detailedPreferenceAdapter.add(new ExposurePreferenceItem());

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_AUTOEXPOSURE, R.string.pref_autoexposure_default, R.string.menu_option_autoexposure, 
			R.array.pref_autoexposure_labels, R.array.pref_autoexposure_values,
			new PreferenceListAdapter.PreferencePredicate(_prefs, 
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
					Pictures.PREF_FILTER_GAMEBOY_CAMERA,
					Pictures.PREF_FILTER_ATKINSON,
			})));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			PREF_AUTOFOCUS, R.string.pref_autofocus_default, R.string.menu_option_autofocus, 
			R.array.pref_autofocus_labels, R.array.pref_autofocus_values));
		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
				Pictures.PREF_FOCUSMARKER, R.string.pref_focusmarker_default, R.string.menu_option_focusmarker, 
				R.array.pref_focusmarker_labels, R.array.pref_focusmarker_values));

		_detailedPreferenceAdapter.add(new OrientationPreferenceItem(
			Pictures.PREF_ORIENTATION, R.string.pref_orientation_default, R.string.menu_option_orientation, 
			R.array.pref_orientation_labels, R.array.pref_orientation_values));
		
		// Set the adapter after populating to ensure list height measure is done properly
		_detailedPreferences.setAdapter(_detailedPreferenceAdapter);

		// Connect the switch camera mode button
		{
			final ImageButton cameraModeButton = (ImageButton)findViewById(R.id.cameraModeButton);
			cameraModeButton.setOnClickListener(new CameraModeButtonListener());
			_cameraState.addChangeListener(new CameraStateListener());
			_cameraState.setValue(CameraState.Picture);
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
		}

		// Connect the pick image button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.pickImageButton);
			button.setOnClickListener(new PickImageListener());
		}
		
		// Connect the image filter button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.switchFilterButton);
			button.setOnClickListener(new ListPreferenceDialogListener(
				Pictures.PREF_FILTER, getResources().getString(R.string.pref_filter_default),
				R.string.menu_option_filter, R.array.pref_filter_labels, R.array.pref_filter_values));
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
				_cameraHandle.addChangeListener(new IChangeListener<CameraHandle>() {
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
		
		// Reinitialize the filter in case preferences have changed
		initFilter();
		
		// Update the last photo thumbnail
		new GetLastThumbnailTask().execute();
		
		// Start the preview
		initCamera();
		
		// Connect to the sensors
		_sensorManager.registerListener(_gyroListener, _gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
		_gyroListener.resetMovement();
	}

	@Override
	public void onBackPressed() {
		// Close the detail preferences if open
		if (_detailedPreferences.getVisibility() == View.VISIBLE) {
			reset();
		}
		else {
			super.onBackPressed();
		}
	}
	
	private void reset(boolean abort) {
		_detailedPreferences.setVisibility(View.GONE);
		_focusManager.setVisible(true);
		
		if (abort) {
			_videoRecorder.abort();
		}
	}
	
	private void reset() {
		reset(true);
	}

	private void stop() {
		_prefs.unregisterOnSharedPreferenceChangeListener(_prefsListener);
		_sensorManager.unregisterListener(_gyroListener);
		_rotationListener.disable();
		reset(true);
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
	public void onLowMemory() {
		_videoRecorder.stop();
		super.onLowMemory();
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
				_focusManager.resetFocus();
				return true;

			case KeyEvent.KEYCODE_CAMERA:
				if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP) {
					// Take photo when the dedicated photo button is pressed
					MotionEvent motevent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0);
					_captureListener.onTouch(null, motevent);
					motevent.recycle();
				}
				
				return true;
		}

		return super.onKeyUp(keyCode, event);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_FOCUS:
				reset(false);
				
				// Trigger auto focus when dedicated photo button is pressed half way
				if (event.getRepeatCount() == 0) {
					_focusManager.autoFocus();
				}
				
				return true;
		
			case KeyEvent.KEYCODE_CAMERA:
				reset(false);
				
				if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
					// Take photo when the dedicated photo button is pressed
					_captureListener.onTouch(null, MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0));
				}
				
				return true;
				
			case KeyEvent.KEYCODE_VOLUME_UP:
				reset(false);

				// Zoom in when volume up is pressed
				_zoomLevel.setValue(Math.min(_zoomLevel.getValue() + ZOOM_VOLUME_STEP, ZOOM_MAX));
				return true;

			case KeyEvent.KEYCODE_VOLUME_DOWN:
				reset(false);

				// Zoom out when volume down is pressed
				_zoomLevel.setValue(Math.max(0, _zoomLevel.getValue() - ZOOM_VOLUME_STEP));
				return true;
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
			CameraHandle handle;
			try {
				Camera camera = Camera.open(cameraid);
				Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
				Camera.getCameraInfo(cameraid, cameraInfo);
				handle = new CameraHandle(camera, cameraInfo, cameraid);
			}
			catch (RuntimeException e) {
				Log.e(TAG, e.getMessage(), e);
				Toast.makeText(this, R.string.error_open_camera, Toast.LENGTH_LONG).show();
				return;
			}
			
			// Configure the camera
			Camera.Parameters params = handle.camera.getParameters();
			params.setPreviewFormat(ImageFormat.NV21);
			
			// Select preview size that most closely matches the wanted size and dimensions
			Pictures.Resolution target = Pictures.getResolution(this, _prefs);
			Camera.Size optimal = null;
			Log.i(TAG, "Target output resolution: " + target);
			
			for (Camera.Size candidate : handle.camera.getParameters().getSupportedPreviewSizes()) {
				if (optimal == null || ratioError(candidate, target) < ratioError(optimal, target) ||
					((optimal.width < target.width && optimal.width < candidate.width ||
					  optimal.width > candidate.width && candidate.width >= target.width))) {
					optimal = candidate;
				}
			}
			params.setPreviewSize(optimal.width, optimal.height);
			Log.i(TAG, "Found preview resolution: " + optimal.width + "x" + optimal.height);
			
			// Initialize the scene mode
			if (params.getSupportedSceneModes() != null) {
				String scenemode = _prefs.getString(Pictures.PREF_SCENEMODE + "_" + cameraid, Camera.Parameters.SCENE_MODE_AUTO);
				params.setSceneMode(scenemode);
				Log.i(TAG, "Applied scene mode: " + scenemode);
			}
			
			// Initialize the exposure compensation
			int exposure = _prefs.getInt(Pictures.PREF_EXPOSURE + "_" + cameraid, 0);
			params.setExposureCompensation(exposure);
			
			// Apply the parameter changes
			handle.camera.setParameters(params);
			
			// Start the preview
			Log.i(TAG, "Starting preview");
			_cameraHandle.setValue(handle);
			_preview.setCamera(handle);
		}
	}
	
	private void initFilter() {
		// Get the resolution and contrast from preferences
		Pictures.Resolution resolution = Pictures.getResolution(this, _prefs);
		int contrast = Pictures.getContrast(this, _prefs);
		
		// Check the auto exposure setting
		String autoexposurevalue = _prefs.getString(Pictures.PREF_AUTOEXPOSURE, getResources().getString(R.string.pref_autoexposure_default));
		boolean autoexposure = "auto".equals(autoexposurevalue);

		// Create the image filter pipeline
		CompositeFilter filter = new CompositeFilter();
		_effectFilter = Pictures.createEffectFilter(this);
		filter.add(new YuvFilter(resolution.width, resolution.height, contrast, _effectFilter.isColorFilter(), autoexposure));
		filter.add(_effectFilter);
		filter.add(new ImageBitmapFilter());
		filter.add(_videoRecorder);
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
			_preview.setCamera(null);
			_cameraHandle.setValue(null);
			handle.camera.release();
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
        	MediaStore.Images.ImageColumns.MIME_TYPE + " IN ('image/png', 'image/gif') AND " +
        	MediaStore.Images.ImageColumns.BUCKET_ID + " = " + getBucketId();
        
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
	
	protected void onMediaCaptured() {}
	
	@SuppressLint("DefaultLocale")
	private static String getBucketId() {
		// Matches code in MediaProvider.computeBucketValues()
		return String.valueOf(Pictures.getStorageDirectory().toString().toLowerCase().hashCode());
	}

	private class GetLastThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
		@Override
		protected Bitmap doInBackground(Void... params) {
	        long id = getLastImageId();
	        if (id >= 0) {
				return MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
	        }
	        
	        return null;
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			ImageButton button = (ImageButton)findViewById(R.id.openGalleryButton);
			View layout = (View)button.getParent();
			
			button.setImageBitmap(result);
			button.setEnabled(result != null);
			layout.setVisibility(result != null ? View.VISIBLE : View.GONE);
		}
	}
	
	private class PickImageListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(MainActivity.this, ImageActivity.class);
			intent.putExtra(ImageActivity.EXTRA_ACTION, ImageActivity.EXTRA_ACTION_PICK);
			startActivity(intent);
		}
	}
	
	private class CameraStateListener implements IChangeListener<CameraState> {
		@Override
		public void handleChange(ChangeEvent<CameraState> event) {
			final ImageButton takePhotoButton = (ImageButton)findViewById(R.id.takePhotoButton);
			final ImageButton cameraModeButton = (ImageButton)findViewById(R.id.cameraModeButton);
			final View videoProgressLayout = findViewById(R.id.videoProgressLayout);

			switch (event.getValue()) {
				case Picture: {
					// Abort any ongoing video capture							
					_videoRecorder.abort();
					
					// Prepare to capture still images
					_captureListener = new TakePhotoListener();
					takePhotoButton.setImageResource(R.drawable.ic_button_camera);
					takePhotoButton.setOnTouchListener(_captureListener);
					cameraModeButton.setImageResource(R.drawable.ic_button_video);
					videoProgressLayout.setVisibility(View.GONE);
					
					// Enable auto-focus
					_focusManager.setVisible(true);
					break;
				}
				
				case Video: {
					// Abort any ongoing video capture							
					_videoRecorder.abort();
					
					// Prepare to record video
					_captureListener = new CaptureVideoListener();
					takePhotoButton.setImageResource(R.drawable.ic_button_video);
					takePhotoButton.setOnTouchListener(_captureListener);
					cameraModeButton.setImageResource(R.drawable.ic_button_camera);
					videoProgressLayout.setVisibility(View.VISIBLE);

					// Enable auto-focus
					_focusManager.setVisible(true);
					break;
				}
				
				case Recording: {
					// Hide auto-focus marker
					_focusManager.setVisible(false);

					// Recording video
					takePhotoButton.setImageResource(R.drawable.ic_button_playback_stop);
					cameraModeButton.setImageResource(R.drawable.ic_button_camera);
					videoProgressLayout.setVisibility(View.VISIBLE);
				}
			}
		}
	}
	
	private class CameraModeButtonListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			reset();
			_cameraState.setValue(_cameraState.getValue() != CameraState.Picture ? CameraState.Picture : CameraState.Video);
		}
	}
	
	private class TakePhotoListener implements View.OnTouchListener, PreviewCallback {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			reset();

			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_POINTER_DOWN: {
					CameraHandle handle = _cameraHandle.getValue();
					if (handle != null) {
						handle.camera.setPreviewCallbackWithBuffer(this);
					}
					
					return true;
				}
			}
			
			return false;
		}

		@Override
		public void onPreviewFrame(byte[] data, Camera camera_) {
			CameraHandle handle = _cameraHandle.getValue();

			// data may be null if buffer was too small
			if (handle != null && data != null) {
				Log.d(TAG, "Captured frame to save");
				
				// Stop receiving camera frames
				handle.camera.setPreviewCallbackWithBuffer(null);
				
				// Process and save the picture
				new ProcessFrameTask(handle, data).execute();
			}
		}
	}
	
	private class CaptureVideoListener implements View.OnTouchListener {
		private static final long PRESS_DELAY = 350;
		private long _prevEvent = 0;
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			_detailedPreferences.setVisibility(View.GONE);
			
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_POINTER_DOWN: {
					// Start recording if we're not currently doing so
					CameraHandle handle = _cameraHandle.getValue();
					if (handle != null && !_videoRecorder.isRecording()) {
						IPalette palette = _preview.getFilter().getPalette();
						IIndexedPalette indexed = (palette instanceof IIndexedPalette) ? (IIndexedPalette)palette : null;
						_videoRecorder.record(getTransform(handle), indexed);
						_prevEvent = System.currentTimeMillis();
					}
					else {
						// Stop recording when button is released from long press
						_videoRecorder.stop();
					}
					
					return true;
				}

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
					// Don't stop if the record button was just tapped quickly
					if ((System.currentTimeMillis() - _prevEvent) > PRESS_DELAY) {
						_videoRecorder.stop();
					}
					
					return true;
				
				case MotionEvent.ACTION_CANCEL:
					_videoRecorder.abort();
					return true;
			}
			
			return false;
		}
	}
	
	private class GyroscopeListener implements SensorEventListener {
		private boolean _movement = true;
		private long _timestamp = 0;
		
		public GyroscopeListener() {
			resetMovement();
		}
		
		public void resetMovement() {
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
					autoFocus();
				}
			}
			else if (rotation >= GYROSCOPE_MOVEMENT_THRESHOLD) {
				// Movement detected
				_movement = true;
				_timestamp = event.timestamp;
				_focusManager.resetFocus();
			}
		}
		
		private void autoFocus() {
			if (PREF_AUTOFOCUS_AUTO.equals(_prefs.getString(PREF_AUTOFOCUS, PREF_AUTOFOCUS_AUTO))) {
				_focusManager.autoFocus();
			}
		}
	}
	
	private Bitmaps.Transform getTransform(CameraHandle handle) {
		Camera.Size size = handle.camera.getParameters().getPreviewSize();
		
		// Get the current device orientation
		WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
		int rotation = _rotationListener.getCurrentRotation(windowManager.getDefaultDisplay().getRotation());
		
		// Get the resolution and contrast from preferences
		Pictures.Resolution resolution = Pictures.getResolution(MainActivity.this, _prefs);
		int contrast = Pictures.getContrast(MainActivity.this, _prefs);

		// Check for orientation override
		int orientation = Pictures.getCameraOrientation(_prefs, handle.info, handle.id);
		
		// Create the image filter pipeline
		YuvFilter yuvFilter = new YuvFilter(resolution.width, resolution.height, contrast, false, false);
		Bitmaps.Transform transform = Pictures.createTransformMatrix(
			yuvFilter.getEffectiveWidth(size.width, size.height), 
			yuvFilter.getEffectiveHeight(size.width, size.height), 
			handle.info.facing, orientation, rotation,
			resolution);
		
		return transform;
	}
	
	/**
	 * Process an camera preview frame
	 */
	private class ProcessFrameTask extends ProgressTask<Void, Void, Void> {
		private IImageFilter _filter;
		private IImageFilter.ImageBuffer _buffer;

		public ProcessFrameTask(CameraHandle handle, byte[] data) {
			super(MainActivity.this, R.string.title_saving_image, R.string.msg_saving_image);
			Camera.Size size = handle.camera.getParameters().getPreviewSize();
			_buffer = new IImageFilter.ImageBuffer(data, size.width, size.height);
			_task = this;
			
			// Get the resolution and contrast from preferences
			Pictures.Resolution resolution = Pictures.getResolution(MainActivity.this, _prefs);
			int contrast = Pictures.getContrast(MainActivity.this, _prefs);
			
			// Check the auto exposure setting
			String autoexposurevalue = _prefs.getString(Pictures.PREF_AUTOEXPOSURE, getResources().getString(R.string.pref_autoexposure_default));
			boolean autoexposure = "auto".equals(autoexposurevalue);
			
			// Create the image filter pipeline
			IImageFilter effect = _effectFilter;
			YuvFilter yuvFilter = new YuvFilter(resolution.width, resolution.height, contrast, effect.isColorFilter(), autoexposure);
			Bitmaps.Transform transform = getTransform(handle);
			
			CompositeFilter filter = new CompositeFilter();
			filter.add(yuvFilter);
			filter.add(effect);
			filter.add(new ImageBitmapFilter());
			filter.add(new TransformFilter(transform));
			_filter = filter;
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			Log.d(TAG, "Processing captured image");
			
			// Apply the image filter to the current image			
			_filter.accept(_buffer);
			
			// Write the image to disk
			File file = Pictures.compress(MainActivity.this, null, null, _buffer.bitmap);
			Log.i(TAG, "Wrote image to disk: " + file);
			
			return null;
		}
		
		@Override
		protected void onCancelled() {
			Log.w(TAG, "Image processing was cancelled");
			
			if (_task == this) {
				_task = null;
			}
			
			super.onCancelled();
		}
		
		@Override
		protected void onPostExecute(Void result) {
			Log.i(TAG, "Successfully captured image");

			// Callback after captured media
			onMediaCaptured();

			// Update the last photo thumbnail
			new GetLastThumbnailTask().execute();
			
			// Restart preview
			_preview.initPreviewCallback();

			if (_task == this) {
				_task = null;
			}
			
			super.onPostExecute(result);
		}
	}
	
	/**
	 * Listens for preference changes and applies updates
	 */
	private class PreferencesListener implements SharedPreferences.OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				if (PREF_CAMERA.equals(key)) {
					Log.i(TAG, "Camera changed through preferences");
					
					// Reinitialize the camera
					initCamera();
				}
				else if (Pictures.PREF_FILTER.equals(key) || 
						 Pictures.PREF_CONTRAST.equals(key) ||
						 Pictures.PREF_MATRIXSIZE.equals(key) ||
						 Pictures.PREF_RASTERLEVEL.equals(key) ||
						 Pictures.PREF_AUTOEXPOSURE.equals(key) ||
						 Pictures.PREF_PALETTE.equals(key) ||
						 key.startsWith(Pictures.PREF_ORIENTATION)) {
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
				else if (key.equals(Pictures.PREF_EXPOSURE + "_" + handle.id)) {
					int exposure = _prefs.getInt(Pictures.PREF_EXPOSURE + "_" + handle.id, 0);
					Camera.Parameters params = handle.camera.getParameters();
					params.setExposureCompensation(exposure);
					handle.camera.setParameters(params);
				}
				else if (key.startsWith(Pictures.PREF_SCENEMODE)) {
					Camera.Parameters params = handle.camera.getParameters();
					
					// Initialize the scene mode
					if (params.getSupportedSceneModes() != null) {
						String scenemode = _prefs.getString(Pictures.PREF_SCENEMODE + "_" + handle.id, Camera.Parameters.SCENE_MODE_AUTO);
						params.setSceneMode(scenemode);
						handle.camera.setParameters(params);
						Log.i(TAG, "Applied scene mode: " + scenemode);
					}
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
			if (_detailedPreferences.getVisibility() != View.VISIBLE) {
				_detailedPreferences.setVisibility(View.VISIBLE);
				_focusManager.setVisible(false);
				_videoRecorder.abort();
			}
			else {
				reset();
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
			reset();
			super.onClick(v);
		}
	}

	/**
	 * Toggles the active camera
	 */
	private class SwitchCameraButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			reset();

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
			reset(false);

			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
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
	}
	
	private class GestureDetector extends ScaleGestureDetector implements View.OnTouchListener {
		public GestureDetector() {
			super(MainActivity.this, new ScaleListener());
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			reset(false);

			boolean result = super.onTouchEvent(event);
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_CANCEL:
					_singleTouch = false;
					break;

				case MotionEvent.ACTION_UP:
					if (_singleTouch) {
						Log.i(TAG, "Detected single touch, triggering auto-focus");
						_focusManager.autoFocus();
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
			int value = (int)Math.floor(((float)_max) * (event.getValue() / ZOOM_MAX));
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
			int value = (int)Math.floor(((float)_max) * (event.getValue() / ZOOM_MAX));
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
	
	public class OrientationPreferenceItem extends ArrayPreferenceItem {
		public OrientationPreferenceItem(String key, int defvalue, int title, int labels, int values) {
			super(MainActivity.this, _prefs, key, defvalue, title, labels, values);
		}
		
		@Override
		protected String getPreferenceKey() {
			String key = super.getPreferenceKey();
			CameraHandle handle = _cameraHandle.getValue();
			
			if (handle != null) {
				return key + "_" + handle.id;
			}
			
			return key;
		}
	}
	
	private class SceneModePreferenceItem extends PreferenceItem {
		public SceneModePreferenceItem() {
			super(R.string.menu_option_scenemode);
		}

		@Override
		public String getValueLabel() {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				return getValueLabel(handle.camera.getParameters().getSceneMode());
			}
			
			return getValueLabel(null);
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
					
					// Remove modes without a label
					int count = 0;
					for (int i = 0; i < values.length; i++) {
						if (labels[i] != null) {
							labels[count] = labels[i];
							values[count] = values[i];
							count++;
						}
					}
					
					values = Arrays.copyOf(values, count);
					labels = Arrays.copyOf(labels, count);
					
					// Show preference dialog
					ListPreferenceDialog dialog = new ListPreferenceDialog(
						MainActivity.this, _prefs, Pictures.PREF_SCENEMODE + "_" + handle.id, 
						"", _title, labels, values);
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
			
			return null;
		}
	}
	
	private class ExposurePreferenceItem extends PreferenceItem implements DialogInterface.OnDismissListener {
		public ExposurePreferenceItem() {
			super(R.string.menu_option_exposure);
		}

		@Override
		public String getValueLabel() {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				Camera.Parameters params = handle.camera.getParameters();
				float value = (float)params.getExposureCompensation() * params.getExposureCompensationStep();
				return format(value);
			}
			
			return "0";
		}
		
		@Override
		public void onClick() {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				Camera.Parameters params = handle.camera.getParameters();
				SeekBarDialog dialog = new SeekBarDialog(MainActivity.this, 
					PreferenceProperties.integer(Pictures.PREF_EXPOSURE + "_" + handle.id, 0).observe(_prefs), 
					params.getExposureCompensationStep(),
					params.getMinExposureCompensation(),
					params.getMaxExposureCompensation());
				
				_detailedPreferences.setVisibility(View.GONE);
				dialog.setOnDismissListener(this);
				dialog.show();
			}
		}
		
		@Override
		public void onDismiss(DialogInterface dialog) {
			reset();
		}
		
		private String format(float value) {
			if (value == (int)value) {
				return String.format(Locale.ENGLISH, "%d", (int)value);
			}
			
			return String.format(Locale.ENGLISH, "%s", value);
		}
	}
}
