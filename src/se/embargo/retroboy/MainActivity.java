package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.BitmapImageFilter;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.TransformFilter;
import se.embargo.retroboy.filter.YuvFilter;
import se.embargo.retroboy.widget.ListPreferenceDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.Window;

public class MainActivity extends SherlockActivity {
	private static final String TAG = "MainActivity";
	
	public static final String PREF_CAMERA = "camera";
	
	private SharedPreferences _prefs;
	
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
	private TakePhotoListener _takePhotoListener = new TakePhotoListener();

	/**
	 * Listener to handle auto-focus
	 */
	private AutoFocusListener _autoFocusListener = new AutoFocusListener();
	private ImageView _autoFocusMarker;
	private boolean _hasAutoFocus;
	
	/**
	 * Zoom support
	 */
	private IObservableValue<Integer> _zoomLevel = new WritableValue<Integer>(0);
	
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
		
		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		// Setup preview display surface
		setContentView(R.layout.main_activity);
		_preview = (CameraPreview)findViewById(R.id.cameraPreview);
		_autoFocusMarker = (ImageView)findViewById(R.id.autoFocusMarker);

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
		/*
		{
			final ImageButton button = (ImageButton)findViewById(R.id.editSettingsButton);
			button.setOnClickListener(new EditSettingsButtonListener());
		}
		*/

		// Connect the contrast button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.adjustContrastButton);
			button.setOnClickListener(new ListPreferenceDialogListener(
				Pictures.PREF_CONTRAST, Pictures.PREF_CONTRAST_DEFAULT,
				R.string.pref_title_contrast, R.array.pref_contrast_labels, R.array.pref_contrast_values));
		}

		// Connect the image filter button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.switchFilterButton);
			button.setOnClickListener(new ListPreferenceDialogListener(
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_DEFAULT,
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
		
		// Initialize the image filter
		initFilter();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		_rotationListener = new OrientationListener();
		_rotationListener.enable();
		
		// Update the last photo thumbnail
		new GetLastThumbnailTask(true).execute();
		
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
		return false;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_CAMERA:
					// Take photo when the dedicated photo button is pressed
					_takePhotoListener.takePhoto();
					return true;
					
				case KeyEvent.KEYCODE_VOLUME_UP: {
					// Zoom in when volume up is pressed
					CameraHandle handle = _cameraHandle.getValue();
					if (handle != null) {
						Camera.Parameters params = handle.camera.getParameters();
						if (params.isZoomSupported() || params.isSmoothZoomSupported()) {
							int max = params.getMaxZoom();
							int val = _zoomLevel.getValue();
							if (val < max) {
								_zoomLevel.setValue(val + 1);
							}
						}
					}
					
					return true;
				}

				case KeyEvent.KEYCODE_VOLUME_DOWN: {
					// Zoom out when volume down is pressed
					int val = _zoomLevel.getValue();	
					if (val > 0) {
						_zoomLevel.setValue(val - 1);
					}
					
					return true;
				}
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
			Camera camera = Camera.open(cameraid);
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraid, cameraInfo);
			_cameraHandle.setValue(new CameraHandle(camera, cameraInfo));

			// Check if camera supports auto-focus
			if (_hasAutoFocus) {
				if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
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
			Camera.Parameters params = camera.getParameters();
			params.setPreviewFormat(ImageFormat.NV21);
			
			// Select preview size that most closely matches the wanted size and dimensions
			Camera.Size previewSize = null;
			for (Camera.Size size : camera.getParameters().getSupportedPreviewSizes()) {
				if (previewSize == null || 
					(previewSize.width < Pictures.IMAGE_WIDTH && previewSize.width < size.width ||
					 previewSize.width > size.width && size.width >= Pictures.IMAGE_WIDTH) &&
					ratioError(previewSize) >= ratioError(size)) {
					previewSize = size;
				}
			}
			params.setPreviewSize(previewSize.width, previewSize.height);
			
			// Apply the parameter changes
			camera.setParameters(params);
			
			// Start the preview
			_preview.setCamera(camera, cameraInfo);
		}
	}
	
	private void initFilter() {
		// Get the contrast adjustment
		int contrast = 0;
		try {
			contrast = Integer.parseInt(_prefs.getString(Pictures.PREF_CONTRAST, Pictures.PREF_CONTRAST_DEFAULT));
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
		CameraHandle handle = _cameraHandle.getValue();
		if (handle != null) {
			handle.camera.stopPreview();
			_preview.setCamera(null, null);
			handle.camera.release();
			_cameraHandle.setValue(null);
		}
	}

	private static float ratioError(Camera.Size size) {
		return Math.round(Math.abs((float)Pictures.IMAGE_WIDTH / Pictures.IMAGE_HEIGHT - (float)size.width / size.height) * 10);
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
	
	private class GetLastThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
		private boolean _sleep;
		
		public GetLastThumbnailTask(boolean sleep) {
			_sleep = sleep;
		}
		
		public GetLastThumbnailTask() {
			this(false);
		}
		
		@Override
		protected Bitmap doInBackground(Void... params) {
	    	if (_sleep) {
				try {
					Thread.sleep(500);
				}
				catch (InterruptedException e) {}
	    	}
	    	
	        long id = getLastImageId();
	        if (id >= 0) {
				return MediaStore.Images.Thumbnails.getThumbnail(
					getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
	        }
	        
	        return null;
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			ImageButton button = (ImageButton)findViewById(R.id.openGalleryButton);
			View layout = (View)button.getParent();
			
			button.setImageBitmap(result);
			layout.setVisibility(result != null ? View.VISIBLE : View.INVISIBLE);
			button.setEnabled(result != null);
		}
	}
	
	private String getBucketId() {
		// Matches code in MediaProvider.computeBucketValues()
		return String.valueOf(Pictures.getStorageDirectory().toString().toLowerCase().hashCode());
	}
	
	private static class CameraHandle {
		public final Camera camera;
		public final Camera.CameraInfo info;
		
		public CameraHandle(Camera camera, Camera.CameraInfo info) {
			this.camera = camera;
			this.info = info;
		}
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
		public void onPreviewFrame(byte[] data, Camera camera_) {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				Camera.Size size = handle.camera.getParameters().getPreviewSize();
	
				// Get the current device orientation
				WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
				int rotation = _rotationListener.getCurrentRotation(windowManager.getDefaultDisplay().getRotation());
				
				// Process and save the picture
				new ProcessFrameTask(handle.camera, data, size.width, size.height, handle.info.facing, handle.info.orientation, rotation).execute();
			}
		}
	}
	
	private class AutoFocusListener implements View.OnClickListener, Camera.AutoFocusCallback {
		@Override
		public void onClick(View v) {
			_autoFocusMarker.setImageResource(R.drawable.ic_focus);

			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				handle.camera.autoFocus(this);
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
				contrast = Integer.parseInt(_prefs.getString(Pictures.PREF_CONTRAST, Pictures.PREF_CONTRAST_DEFAULT));
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
			if (result != null) {
				// Update the last photo thumbnail
				new GetLastThumbnailTask().execute();
			}

			// Release buffer back to camera
			synchronized (_camera) {
				_camera.addCallbackBuffer(_buffer.frame);
			}
		}
	}
	
	/**
	 * Listens for preference changes and applies updates
	 */
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (PREF_CAMERA.equals(key)) {
				initCamera();
			}
			else if (Pictures.PREF_FILTER.equals(key)) {
				// Change the active image filter
				initFilter();
			}
			else if (Pictures.PREF_CONTRAST.equals(key)) {
				// Change the active image filter
				initFilter();
			}
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

	/**
	 * Starts the preferences activity
	 */
	/*
	private class EditSettingsButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
			startActivity(intent);
		}
	}
	*/

	/**
	 * Shows the contrast preference dialog
	 */
	private class ListPreferenceDialogListener extends ListPreferenceDialog implements OnClickListener {
		public ListPreferenceDialogListener(String prefname, String prefdefault, int title, int labels, int values) {
			super(MainActivity.this, _prefs, prefname, prefdefault, title, labels, values);
		}
		
		@Override
		public void onClick(View v) {
			show();
		}
	}

	/**
	 * Toggles the active camera
	 */
	private class SwitchCameraButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
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
    		CameraHandle handle = _cameraHandle.getValue();
			Camera.Parameters params = handle.camera.getParameters();
			
    		if (!Camera.Parameters.FLASH_MODE_TORCH.equals(params.getFlashMode())) {
    			params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
    		}
    		else {
    			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    		}
    		
    		handle.camera.setParameters(params);
		}
	}
	
	private class ZoomLevelHandler implements IChangeListener<Integer> {
		@Override
		public void handleChange(ChangeEvent<Integer> event) {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				Camera.Parameters params = handle.camera.getParameters();
				params.setZoom(event.getValue());
				handle.camera.setParameters(params);
			}
		}
	}
	
	private class ZoomSmoothHandler implements IChangeListener<Integer> {
		@Override
		public void handleChange(ChangeEvent<Integer> event) {
			CameraHandle handle = _cameraHandle.getValue();
			if (handle != null) {
				handle.camera.startSmoothZoom(event.getValue());
			}
		}
	}
	
	private class ZoomCameraHandler implements IChangeListener<CameraHandle> {
		private ZoomSmoothHandler _zoomSmoothHandler = new ZoomSmoothHandler();
		private ZoomLevelHandler _zoomLevelHandler = new ZoomLevelHandler();
		
		@Override
		public void handleChange(ChangeEvent<CameraHandle> event) {
			_zoomLevel.removeChangeListener(_zoomSmoothHandler);
			_zoomLevel.removeChangeListener(_zoomLevelHandler);
			_zoomLevel.setValue(0);
			
			CameraHandle handle = event.getValue();
			if (handle != null) {
				Camera.Parameters params = handle.camera.getParameters();
				
				if (params.isSmoothZoomSupported()) {
					_zoomLevel.addChangeListener(_zoomSmoothHandler);
				}
				else if (params.isZoomSupported()) {
					_zoomLevel.addChangeListener(_zoomLevelHandler);
				}
			}
		}
	}
}
